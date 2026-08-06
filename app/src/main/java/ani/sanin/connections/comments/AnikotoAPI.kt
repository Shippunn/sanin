package ani.sanin.connections.comments

import android.util.Base64
import android.util.Log
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AnikotoAPI {
    private const val BASE_URL = "https://anikoto.cz"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    private const val MAX_EPISODES = 20
    private const val MAX_PAGES_PER_EPISODE = 10
    private const val EPISODE_TIMEOUT_MS = 20_000L

    private val client: OkHttpClient get() = Injekt.get<NetworkHelper>().client
    private val searchCache = mutableMapOf<String, AnikotoAnime>()

    private data class AnikotoAnime(val animeId: String, val slug: String, val title: String)
    private data class AnikotoEpisode(val num: Int, val episodeId: String)
    private data class AnikotoWidget(val html: String?, val nextPage: Int?)

    suspend fun getCommentsForMedia(
        mediaId: Int,
        title: String,
        episodeProgress: Int?,
        onBatch: suspend (List<Comment>) -> Unit
    ) {
        val anime = findAnime(title) ?: run {
            Logger.log(Log.ERROR, "Anikoto: no anime match for '$title'")
            return
        }
        val episodes = fetchEpisodes(anime.slug, anime.animeId) ?: run {
            Logger.log(Log.ERROR, "Anikoto: no episodes for '${anime.slug}'")
            return
        }
        val ordered = orderEpisodes(episodes, episodeProgress)
        Logger.log("Anikoto: ${ordered.size} episodes queued for '$title' (current=$episodeProgress)")
        var total = 0
        for (episode in ordered) {
            val comments = withTimeoutOrNull(EPISODE_TIMEOUT_MS) {
                fetchEpisodeComments(anime, episode, mediaId, onBatch)
            } ?: run {
                Logger.log(Log.ERROR, "Anikoto: timed out fetching ep ${episode.num}")
                emptyList()
            }
            total += comments.size
        }
        Logger.log("Anikoto: $total comments across ${ordered.size} episodes")
    }

    /** Fetches replies for one Anikoto comment (same .cw_l-line format). */
    suspend fun getReplies(commentId: Int, episode: Int, mediaId: Int): List<Comment> {
        val body = httpGet(
            "$BASE_URL/ajax/comment/replies/$commentId",
            referer = "$BASE_URL/",
            xhr = true
        ) ?: return emptyList()
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            val html = obj["html"]?.jsonPrimitive?.contentOrNull
            val status = obj["status"]?.jsonPrimitive?.booleanOrNull
            if (status == false || html.isNullOrBlank()) emptyList()
            else parseComments(html, mediaId, episode)
        } catch (e: Exception) {
            Logger.log(e)
            emptyList()
        }
    }

    /** Display order: current episode down to 1, then current+1 up to the last. */
    private fun orderEpisodes(episodes: List<AnikotoEpisode>, progress: Int?): List<AnikotoEpisode> {
        val current = progress ?: 0
        val below = episodes.filter { it.num <= current }.sortedByDescending { it.num }
        val above = episodes.filter { it.num > current }.sortedBy { it.num }
        return (below + above).take(MAX_EPISODES)
    }

    private suspend fun fetchEpisodeComments(
        anime: AnikotoAnime,
        episode: AnikotoEpisode,
        mediaId: Int,
        onBatch: suspend (List<Comment>) -> Unit
    ): List<Comment> {
        val comments = mutableListOf<Comment>()
        var page = 1
        while (page <= MAX_PAGES_PER_EPISODE) {
            val widget = fetchWidgetHtml(anime.animeId, episode.episodeId, resolveSort(), anime.slug, page)
            val html = widget?.html ?: break
            val parsed = parseComments(html, mediaId, episode.num)
            comments.addAll(parsed)
            onBatch(parsed)
            val nextPage = widget.nextPage ?: break
            if (nextPage <= page || parsed.isEmpty()) break
            page = nextPage
        }
        if (page > MAX_PAGES_PER_EPISODE) {
            Logger.log(Log.ERROR, "Anikoto: capped at $MAX_PAGES_PER_EPISODE pages for ep ${episode.num}")
        }
        Logger.log("Anikoto: ep ${episode.num} -> ${comments.size} comments")
        return comments
    }

    private suspend fun findAnime(title: String): AnikotoAnime? {
        val normalized = normalize(title)
        if (normalized.isEmpty()) return null
        searchCache[normalized]?.let { return it }

        val html = httpGet("$BASE_URL/filter?keyword=${URLEncoder.encode(title, "UTF-8")}")
            ?: return null
        val doc = Jsoup.parse(html)
        var best: AnikotoAnime? = null
        var bestScore = Int.MAX_VALUE
        doc.select("div.item").forEach { item ->
            val poster = item.selectFirst("div.poster")
            val link = poster?.selectFirst("a[href*=/watch/]")
                ?: item.selectFirst("a[href*=/watch/]")
                ?: return@forEach
            val slug = link.attr("href")
                .substringAfter("/watch/")
                .substringBefore("/ep-")
                .substringBefore("?")
            val animeId = poster?.attr("data-tip") ?: return@forEach
            val titleElement = item.selectFirst(".name.d-title") ?: item.selectFirst("a.d-title")
            val displayTitle = titleElement?.text()
                ?: link.selectFirst("img")?.attr("alt")
                ?: ""
            val romajiTitle = titleElement?.attr("data-jp") ?: ""
            if (slug.isEmpty() || animeId.isEmpty()) return@forEach
            val score = minOf(
                matchScore(normalized, normalize(displayTitle)),
                matchScore(normalized, normalize(romajiTitle)),
            )
            if (score < bestScore) {
                bestScore = score
                best = AnikotoAnime(animeId, slug, displayTitle)
            }
        }
        if (best != null && bestScore < Int.MAX_VALUE) {
            searchCache[normalized] = best!!
            Logger.log("Anikoto: matched '${best.title}' -> slug=${best.slug} id=${best.animeId} (score $bestScore)")
        } else {
            Logger.log(Log.ERROR, "Anikoto: no match for '$title' among ${doc.select("div.item").size} results")
        }
        return best
    }

    private suspend fun fetchEpisodes(slug: String, animeId: String): List<AnikotoEpisode>? {
        // Episode list is loaded via AJAX into #w-episodes; the watch page only
        // contains a placeholder div.
        val body = httpPost("$BASE_URL/ajax/episode/list/$animeId", referer = "$BASE_URL/watch/$slug/ep-1")
            ?: return null
        val html = try {
            Json.parseToJsonElement(body).jsonObject["result"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            Logger.log(e)
            null
        } ?: return null
        val doc = Jsoup.parse(html)
        val episodes = doc.select("ul.ep-range li a[data-id]").mapNotNull { link ->
            val episodeId = link.attr("data-id")
            val num = link.attr("data-num").toIntOrNull()
            if (episodeId.isEmpty() || num == null) null else AnikotoEpisode(num, episodeId)
        }
        if (episodes.isEmpty()) Logger.log(Log.ERROR, "Anikoto: no episodes parsed for slug=$slug")
        return episodes.ifEmpty { null }
    }

    private suspend fun fetchWidgetHtml(
        animeId: String,
        episodeId: String,
        sort: String,
        slug: String,
        page: Int = 1
    ): AnikotoWidget? {
        val pageParam = if (page > 1) "&page=$page" else ""
        val url = "$BASE_URL/ajax/comment/widget/$animeId?episodeId=$episodeId&sort=$sort&type=episode$pageParam"
        val body = httpGet(url, referer = "$BASE_URL/watch/$slug/ep-1", xhr = true)
            ?: return null
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            val html = obj["html"]?.jsonPrimitive?.contentOrNull
            val status = obj["status"]?.jsonPrimitive?.booleanOrNull
            val nextPage = obj["nextPage"]?.jsonPrimitive?.intOrNull
            if (status == false || html.isNullOrBlank()) {
                Logger.log(Log.ERROR, "Anikoto: widget returned no comments (status=$status)")
                null
            } else {
                AnikotoWidget(html, nextPage)
            }
        } catch (e: Exception) {
            Logger.log(e)
            null
        }
    }

    private suspend fun httpPost(url: String, referer: String? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Cache-Control", "no-cache")
                    .header("X-Requested-With", "XMLHttpRequest")
                if (referer != null) builder.header("Referer", referer)
                client.newCall(builder.post(ByteArray(0).toRequestBody()).build()).execute().use { response ->
                    if (response.code == 200) {
                        response.body?.string()
                    } else {
                        Logger.log(Log.ERROR, "Anikoto: HTTP ${response.code} for $url")
                        null
                    }
                }
            } catch (e: Exception) {
                Logger.log(e)
                null
            }
        }

    private suspend fun httpGet(
        url: String,
        referer: String? = null,
        xhr: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
            if (referer != null) builder.header("Referer", referer)
            if (xhr) builder.header("X-Requested-With", "XMLHttpRequest")
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 200) {
                    response.body?.string()
                } else {
                    Logger.log(Log.ERROR, "Anikoto: HTTP ${response.code} for $url")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
            null
        }
    }

    private fun parseComments(html: String, mediaId: Int, episode: Int): List<Comment> {
        val doc = Jsoup.parse(html)
        return doc.select(".cw_l-line").mapNotNull { parseComment(it, mediaId, episode) }
    }

    private fun parseComment(element: Element, mediaId: Int, episode: Int): Comment? {
        val commentId = element.attr("data-comment-id").toLongOrNull()?.toInt() ?: return null
        val userId = element.attr("data-user-id")
        val username = element.selectFirst("a.user-name")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Anime Fan"
        val avatar = element.selectFirst("img.user-avatar-img")?.attr("src")
        val timestamp = relativeToIso(element.selectFirst(".time")?.text() ?: "")
        val body = element.selectFirst(".cm-body")
        val raw = body?.attr("data-cm-raw-b64")
        val content = markdownifyGifs(
            raw?.let { decodeBase64(it) }?.takeIf { it.isNotBlank() }
                ?: body?.text()?.trim()
                ?: return null
        )
        val upvotes = element.selectFirst(".cm-btn-vote[data-type=\"1\"] .value")
            ?.text()?.toIntOrNull() ?: 0
        val downvotes = element.selectFirst(".cm-btn-vote[data-type=\"0\"] .value")
            ?.text()?.toIntOrNull() ?: 0
        val deleted = element.attr("data-comment-hidden") == "1"
        val replyText = element.selectFirst(".cm-btn-show-rep span")?.text() ?: ""
        val replyCount = Regex("(\\d+)").find(replyText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return Comment(
            commentId = commentId,
            userId = userId,
            mediaId = mediaId,
            parentCommentId = null,
            content = content,
            timestamp = timestamp,
            deleted = deleted,
            tag = null,
            upvotes = upvotes,
            downvotes = downvotes,
            userVoteType = null,
            username = username,
            profilePictureUrl = avatar,
            totalVotes = upvotes - downvotes,
            isAnikoto = true,
            anikotoEpisode = episode,
            replyCount = replyCount,
        )
    }

    private fun resolveSort(): String = when (PrefManager.getVal(PrefName.CommentSortOrder, "newest")) {
        "oldest" -> "oldest"
        "likes" -> "top"
        else -> "newest"
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase(Locale.US)) {
            if (ch in 'a'..'z' || ch in '0'..'9') sb.append(ch)
        }
        return sb.toString()
    }

    private fun matchScore(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return Int.MAX_VALUE
        if (a == b) return 0
        if (a.contains(b) || b.contains(a)) return kotlin.math.abs(a.length - b.length)
        val ratio = FuzzySearch.ratio(a, b)
        return if (ratio >= 75) 100 - ratio else Int.MAX_VALUE
    }

    private fun decodeBase64(input: String): String? {
        return try {
            String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** Convert bare GIF URLs (Anikoto stores them as plain text) into markdown images. */
    private fun markdownifyGifs(text: String): String {
        return GIF_URL_REGEX.replace(text) { "![gif](${it.value})" }
    }

    private val GIF_URL_REGEX = Regex("""https?://[^\s)"']+\.gif(?:\?[^\s)"']*)?""", RegexOption.IGNORE_CASE)

    private fun relativeToIso(relative: String): String {
        val now = System.currentTimeMillis()
        val text = relative.substringBefore("—").trim()
        if (text.isEmpty()) return formatIso(now)
        val lower = text.lowercase(Locale.US)
        if (lower == "just now" || lower == "now") return formatIso(now)
        if (lower == "yesterday") return formatIso(now - 86_400_000L)
        val units = mapOf(
            "second" to 1_000L,
            "seconds" to 1_000L,
            "minute" to 60_000L,
            "minutes" to 60_000L,
            "hour" to 3_600_000L,
            "hours" to 3_600_000L,
            "day" to 86_400_000L,
            "days" to 86_400_000L,
            "week" to 604_800_000L,
            "weeks" to 604_800_000L,
            "month" to 2_592_000_000L,
            "months" to 2_592_000_000L,
            "year" to 31_536_000_000L,
            "years" to 31_536_000_000L,
        )
        var offset = 0L
        var matched = false
        for (m in Regex("(\\d+)\\s+([a-zA-Z]+)").findAll(text)) {
            val amount = m.groupValues[1].toLongOrNull() ?: continue
            val multiplier = units[m.groupValues[2].lowercase(Locale.US)] ?: continue
            offset += amount * multiplier
            matched = true
        }
        return formatIso(if (matched) now - offset else now)
    }

    private fun formatIso(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(millis))
    }
}
