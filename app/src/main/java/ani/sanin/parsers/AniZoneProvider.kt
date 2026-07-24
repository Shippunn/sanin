package ani.sanin.parsers

import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AniZoneProvider : NativeAnimeParser() {

    override val name = "AniZone"
    override val saveName = "AniZone"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    private val baseUrl = "https://anizone.to"

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val html = get("$baseUrl/anime?search=${encode(query)}", baseUrl, "text/html,application/json,*/*")
                val slugs = Regex(
                    """href=["'](?:https://anizone\.to)?/anime/([a-z0-9-]+)(?:[/?#][^"']*)?["']""",
                    RegexOption.IGNORE_CASE
                ).findAll(html)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()

                val seen = mutableSetOf<String>()
                slugs.mapNotNull { slug ->
                    if (!seen.add(slug)) return@mapNotNull null
                    val name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
                    val fullUrl = "$baseUrl/anime/$slug"
                    var coverUrl = defaultImage
                    Regex("""<img\b[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
                        val src = match.groupValues[1]
                        if (src.contains(slug, ignoreCase = true)) coverUrl = src
                    }
                    ShowResponse(
                        name = name, link = fullUrl, coverUrl = coverUrl,
                        extra = mutableMapOf("slug" to slug)
                    )
                }
            } catch (e: Exception) {
                Logger.log("AniZone search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime?): List<Episode> {
        return withContext(Dispatchers.IO) {
            try {
                val slug = animeLink.substringAfter("/anime/").substringBefore("?")
                val html = get("$baseUrl/anime/$slug", "$baseUrl/", "text/html,application/json,*/*")
                val count = Regex("""\b(\d+)\s+Episodes\b""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext emptyList()

                (1..count).map { number ->
                    Episode(
                        number = number.toString(),
                        link = number.toString(),
                        extra = extra
                    )
                }
            } catch (e: Exception) {
                Logger.log("AniZone loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode?): List<VideoServer> {
        val episodeNum = episodeLink.toIntOrNull() ?: return emptyList()
        val slug = extra?.get("slug") ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val pageUrl = "$baseUrl/anime/$slug/$episodeNum"
                val html = get(pageUrl, "$baseUrl/anime/$slug", "text/html,application/json,*/*")

                val hls = Regex("""<media-player[^>]+src=["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.let(::decodeEntities)
                    ?: hlsUrls(html).firstOrNull()
                    ?: return@withContext emptyList()

                val subtitlesStr = parseAniZoneSubtitles(html, pageUrl)
                val extraData = mutableMapOf("referer" to "$baseUrl/")
                if (subtitlesStr.isNotEmpty()) extraData["subtitles"] = subtitlesStr

                listOf(VideoServer("AniZone", hls, extraData))
            } catch (e: Exception) {
                Logger.log("AniZone loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseAniZoneSubtitles(html: String, pageUrl: String): String {
        val subs = Regex("""<track\b([^>]*)>""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { match ->
            val tag = match.value
            if (!attr(tag, "kind").equals("subtitles", true)) return@mapNotNull null
            val url = attr(tag, "src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fullUrl = absoluteUrl(pageUrl, url)
            val label = attr(tag, "label").ifBlank { "Subtitle" }
            val lang = attr(tag, "srclang").ifBlank { "und" }
            "{\"url\":\"${fullUrl.replace("\"", "\\\"")}\",\"language\":\"$lang\",\"type\":\"vtt\"}"
        }
        return if (subs.toList().isNotEmpty()) "[${subs.toList().joinToString(",")}]" else ""
    }


}
