package ani.sanin.parsers

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import okhttp3.Request

class AnimeKaiProvider : NativeAnimeParser() {

    override val name = "AnimeKai"
    override val saveName = "animekai"
    override val baseUrl = "https://www3.anikai.cc"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    private val mirrors = listOf(
        "https://www3.anikai.cc", "https://www1.anikai.cc",
        "https://www2.anikai.cc", "https://www4.anikai.cc", "https://anikai.cc",
    )

    override suspend fun search(query: String): List<ShowResponse> {
        for (base in mirrors) {
            try {
                val html = get("$base/browser?keyword=${encode(query)}", "$base/", "text/html,*/*")
                val results = parseCards(html).map { card ->
                    ShowResponse(name = card.title, link = card.slug, coverUrl = defaultImage, extra = mutableMapOf("slug" to card.slug, "base" to base))
                }
                if (results.isNotEmpty()) return results
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val slug = extra?.get("slug") ?: return emptyList()
        val base = extra?.get("base") ?: mirrors.first()
        for (b in listOf(base) + mirrors) {
            try {
                val html = get("$b/watch/$slug", "$b/", "text/html,*/*")
                val avail = parseAudioEpisodes(html)
                if (avail.sub.isEmpty() && avail.dub.isEmpty()) continue
                val episodes = if (selectDub) avail.dub else avail.sub
                extra?.let { it["base"] = b }
                return episodes.map { Episode(number = it.toString(), link = "", extra = extra) }
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val slug = extra?.get("slug") ?: return emptyList()
        val episodeNum = sEpisode.episode_number.toInt()
        val audio = if (selectDub) "dub" else "sub"
        val bases = listOfNotNull(extra?.get("base")) + mirrors

        for (base in bases.distinct()) {
            try {
                val pageUrl = "$base/watch/$slug/ep-$episodeNum"
                val html = get(pageUrl, "$base/watch/$slug", "text/html,*/*")
                val servers = parseServers(html)
                if (servers.isEmpty()) continue

                val preferred = if (selectDub) listOf("dub") else listOf("hsub", "sub", "softsub")
                val pool = servers.filter { it.language in preferred }
                    .sortedByDescending { if (it.videoUrl.contains("vivibebe", true) || it.videoUrl.contains("bibiemb", true) || it.videoUrl.contains("vibeplayer", true)) 3 else if (it.videoUrl.contains("megaup") || it.videoUrl.contains("4spromax")) 1 else 2 }

                val resultServers = mutableListOf<VideoServer>()
                pool.take(4).forEach { server ->
                    val embedUrl = absoluteUrl(base, server.videoUrl)
                    val embedHtml = runCatching { get(embedUrl, mapOf("Referer" to "$base/", "Accept" to "text/html,*/*")) }.getOrDefault("")
                    val hlsUrl = findHls(embedHtml)
                    if (hlsUrl != null) {
                        val ref = origin(embedUrl)
                        val subs = parsePageSubtitles(embedHtml)
                        val extraData = mutableMapOf("referer" to "$ref/", "type" to "hls")
                        if (subs.isNotEmpty()) extraData["subtitles"] = "[${subs.joinToString(",")}]"
                        resultServers += VideoServer("AnimeKai ${server.name}", hlsUrl, extraData)
                    }
                }
                if (resultServers.isNotEmpty()) return resultServers
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    private data class Card(val slug: String, val title: String)
    private data class Server(val language: String, val name: String, val videoUrl: String)
    private data class EpisodeAudio(val sub: Set<Int>, val dub: Set<Int>)

    private fun parseCards(html: String): List<Card> {
        val starts = Regex("""<div\b[^>]*class=["'][^"']*\baitem\b[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).toList()
        return starts.mapNotNull { match ->
            val end = starts.firstOrNull { it.range.first > match.range.first }?.range?.first
                ?: (match.range.first + 5_000).coerceAtMost(html.length)
            val block = html.substring(match.range.first, end)
            val href = Regex("""href=["'](/watch/[^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val slug = Regex("""/watch/([a-z0-9][a-z0-9-]*)""", RegexOption.IGNORE_CASE)
                .find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val titleTag = Regex("""<a\b[^>]*class=["'][^"']*\btitle\b[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
                .find(block)?.value.orEmpty()
            val title = attr(titleTag, "data-en")
                .ifBlank { attr(titleTag, "title") }
                .ifBlank {
                    Regex("""<a\b[^>]*class=["'][^"']*\btitle\b[^"']*["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
                        .find(block)?.groupValues?.get(1)?.let(::stripTags).orEmpty()
                }
                .ifBlank { slug.replace('-', ' ') }
            Card(slug, title)
        }.distinctBy(Card::slug)
    }

    private fun parseAudioEpisodes(html: String): EpisodeAudio {
        val sub = linkedSetOf<Int>()
        val dub = linkedSetOf<Int>()
        Regex("""<a\b[^>]*\bdata-(?:num|slug)\s*=\s*(["'])\d+\1[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { match ->
                val tag = match.value
                val number = attr(tag, "data-num").toIntOrNull()
                    ?: attr(tag, "data-slug").toIntOrNull()
                    ?: return@forEach
                if (attr(tag, "data-sub") == "1" || attr(tag, "data-hsub") == "1") sub += number
                if (attr(tag, "data-dub") == "1") dub += number
            }
        return EpisodeAudio(sub, dub)
    }

    private fun parseServers(html: String): List<Server> {
        val groupTags = Regex("""<[a-z0-9]+\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)
            .filter { tag ->
                val classes = attr(tag.value, "class")
                Regex("""(?:^|\s)server-items(?:\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(classes) &&
                    attr(tag.value, "data-id").isNotBlank()
            }.toList()
        val servers = mutableListOf<Server>()
        groupTags.forEachIndexed { index, group ->
            val language = attr(group.value, "data-id").lowercase()
            val end = groupTags.getOrNull(index + 1)?.range?.first ?: html.length
            val block = html.substring(group.range.last + 1, end)
            Regex("""<(?:span|div|li|a)\b[^>]*\bdata-video\s*=\s*(["']).*?\1[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(block).forEach { tag ->
                    val video = attr(tag.value, "data-video")
                    if (video.isBlank()) return@forEach
                    val classes = attr(tag.value, "class")
                    if (!classes.contains("server", true)) return@forEach
                    val name = attr(tag.value, "data-name")
                        .ifBlank { attr(tag.value, "title") }
                        .ifBlank { "Server" }
                    servers += Server(language, name, video)
                }
        }
        return servers.distinctBy { "${it.language}:${it.videoUrl}" }
    }

    private fun findHls(html: String): String? {
        val patterns = listOf(
            Regex("""const\s+src\s*=\s*["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:["']file["']|file)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { regex -> regex.find(html)?.groupValues?.get(1) }
            ?.let { decodeEntities(it).replace("\\u0026", "&", ignoreCase = true).replace("\\/", "/") }
            ?: hlsUrls(html).firstOrNull()
    }

    private fun parsePageSubtitles(html: String): List<String> = Regex(
        """file\s*:\s*["']([^"']+\.vtt[^"']*)["'][\s\S]{0,100}?label\s*:\s*["']([^"']*)["']""",
        RegexOption.IGNORE_CASE,
    ).findAll(html).map { match ->
        val url = decodeEntities(match.groupValues[1]).replace("\\/", "/")
        val label = decodeEntities(match.groupValues[2]).ifBlank { "Subtitle" }
        """{"url":"$url","language":"${language(label)}","type":"vtt","label":"${label.replace("\"", "\\\"")}"}"""
    }.distinctBy { it }.toList()

    override var selectDub: Boolean = false
}
