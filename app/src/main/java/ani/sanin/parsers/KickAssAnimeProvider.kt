package ani.sanin.parsers

import ani.sanin.Mapper
import ani.sanin.okHttpClient
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class KickAssAnimeProvider : NativeAnimeParser() {

    override val name = "KickAssAnime"
    override val saveName = "kaa"
    override val baseUrl = "https://kaa.lt"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    private val api = "$baseUrl/api"
    private val hlsBase = "https://hls.krussdomi.com/manifest"
    private val playerOrigin = "https://krussdomi.com"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun search(query: String): List<ShowResponse> {
        val body = """{"query":"$query"}""".toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$api/fsearch")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$baseUrl/")
            .header("Content-Type", "application/json")
            .post(body).build()
        val response = okHttpClient.newCall(request).execute().use { it.body?.string().orEmpty() }
        val json = Mapper.json.parseToJsonElement(response).jsonObject
        val results = (json["result"] as? List<*>) ?: return emptyList()
        return results.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val slug = map["slug"] as? String ?: return@mapNotNull null
            val title = (map["title_en"] as? String) ?: (map["title"] as? String) ?: slug
            ShowResponse(name = title, link = slug, coverUrl = defaultImage, extra = mutableMapOf("slug" to slug))
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val slug = extra?.get("slug") ?: return emptyList()
        val data = parseEpisodeNumbers(slug)
        if (selectDub && data.dub.isEmpty() && data.sub.isNotEmpty()) return emptyList()
        val episodes = if (selectDub) data.dub else data.sub
        return episodes.map { Episode(number = it.toString(), link = "", extra = extra) }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val slug = extra?.get("slug") ?: return emptyList()
        val episodeNum = sEpisode.episode_number.toInt()
        val locale = if (selectDub) "en-US" else "ja-JP"

        val epJson = get("$api/show/$slug/episodes?ep=$episodeNum&lang=$locale", "$baseUrl/")
        val epRoot = Mapper.json.parseToJsonElement(epJson).jsonObject
        val epResult = (epRoot["result"] as? List<Map<*, *>>).orEmpty()
        val epSlug = epResult.firstNotNullOfOrNull { row ->
            if ((row["episode_number"] as? Number)?.toInt() == episodeNum) row["slug"] as? String else null
        } ?: return emptyList()

        val detailUrl = "$api/show/$slug/episode/ep-$episodeNum-$epSlug"
        val detail = get(detailUrl, "$baseUrl/")
        val detailRoot = Mapper.json.parseToJsonElement(detail).jsonObject
        val servers = (detailRoot["servers"] as? List<Map<*, *>>).orEmpty()
        val vidStreaming = servers.firstNotNullOfOrNull { s ->
            val name = s["name"] as? String ?: ""
            val src = s["src"] as? String
            src?.takeIf { name.equals("VidStreaming", true) || it.contains("source=vidstream", true) }
        } ?: return emptyList()

        val playerId = vidStreaming.substringAfter("id=").substringBefore("&").takeIf { it.isNotBlank() }
            ?: return emptyList()

        val masterUrl = "$hlsBase/$playerId/master.m3u8"
        val playerHtml = runCatching { get(vidStreaming, "$baseUrl/", "text/html,*/*") }.getOrDefault("")
        val subtitlesList = parseSubtitles(playerHtml)

        val extraData = mutableMapOf("referer" to "$playerOrigin/", "type" to "hls")
        if (subtitlesList.isNotEmpty()) extraData["subtitles"] = "[${subtitlesList.joinToString(",")}]"

        val label = if (selectDub) "KickAssAnime English" else "KickAssAnime Japanese"
        return listOf(VideoServer(label, masterUrl, extraData))
    }

    private data class EpisodeData(val sub: Set<Int>, val dub: Set<Int>)

    private fun parseEpisodeNumbers(slug: String): EpisodeData {
        val allSub = mutableSetOf<Int>()
        val allDub = mutableSetOf<Int>()

        fun epsForLocale(locale: String): Set<Int> {
            val json = runCatching { get("$api/show/$slug/episodes?ep=1&lang=$locale", "$baseUrl/") }.getOrNull() ?: return emptySet()
            val root = Mapper.json.parseToJsonElement(json).jsonObject
            val pages = (root["pages"] as? List<Map<*, *>>).orEmpty()
            val fromPages = pages.flatMap { page ->
                ((page["eps"] as? List<*>).orEmpty()).mapNotNull { (it as? Number)?.toInt() }
            }.filter { it > 0 }
            if (fromPages.isNotEmpty()) return fromPages.toSet()
            val result = (root["result"] as? List<Map<*, *>>).orEmpty()
            return result.mapNotNull { (it["episode_number"] as? Number)?.toInt() }.filter { it > 0 }.toSet()
        }

        allSub += epsForLocale("ja-JP")
        allDub += epsForLocale("en-US")
        return EpisodeData(allSub, allDub)
    }

    private fun parseSubtitles(html: String): List<String> {
        val decoded = decodeEntities(html)
        val pattern = Regex(
            "\"language\":\\[0,\"([^\"]*)\"\\],\"name\":\\[0,\"([^\"]*)\"\\],\"src\":\\[0,\"([^\"]+\\.vtt)\"\\]",
            RegexOption.IGNORE_CASE,
        )
        return pattern.findAll(decoded).map { match ->
            val label = match.groupValues[2].ifBlank { match.groupValues[1].ifBlank { "und" } }
            val url = match.groupValues[3]
            val lang = language(label)
            """{"url":"$url","language":"$lang","type":"vtt","label":"${label.replace("\"", "\\\"")}"}"""
        }.distinctBy { it }.toList()
    }

    override var selectDub: Boolean = false
}
