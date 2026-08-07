package ani.sanin.parsers

import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AniZoneProvider : NativeAnimeParser() {

    override val name = "AniZone"
    override val saveName = "AniZone"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val defaultBaseUrl = "https://anizone.to"
    override val knownServers = listOf("AniZone")

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val html = get("$baseUrl/anime?search=${encode(query)}", baseUrl, "text/html,application/json,*/*")

                val seen = mutableSetOf<String>()
                val cardPattern = Regex(
                    """<div\s+x-data="([^"]*)"[^>]*wire:key="a-([a-z0-9-]+)"[^>]*>(.*?)(?=<div\s+x-data=|\z)""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
                val cardResults = cardPattern.findAll(html).mapNotNull { match ->
                    val xData = match.groupValues[1]
                    val slug = match.groupValues[2]
                    if (!seen.add(slug)) return@mapNotNull null
                    val name = Regex("""getTitle\(\s*this\.anmTitles\s*,\s*'([^']*)'\s*\)""", RegexOption.IGNORE_CASE)
                        .find(xData)?.groupValues?.get(1)?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val fullUrl = "$baseUrl/anime/$slug"
                    val coverUrl = Regex("""<img\b[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(match.groupValues[3])?.groupValues?.get(1) ?: defaultImage
                    ShowResponse(
                        name = name, link = fullUrl, coverUrl = coverUrl,
                        extra = mutableMapOf("slug" to slug)
                    )
                }.toList()
                if (cardResults.isNotEmpty()) return@withContext cardResults

                Regex(
                    """<a\b[^>]*href=["'](?:https://anizone\.to)?/anime/([a-z0-9-]+)(?:[/?#][^"']*)?["'][^>]*>(.*?)</a>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).findAll(html).mapNotNull { match ->
                    val slug = match.groupValues[1]
                    if (!seen.add(slug)) return@mapNotNull null
                    val anchorContent = match.groupValues[2]
                    val name = Regex("""<[^>]+>([^<]+)</""", RegexOption.IGNORE_CASE)
                        .findAll(anchorContent).map { it.groupValues[1].trim() }
                        .firstOrNull { it.length > 1 && it.any { c -> c.isLetter() } }
                        ?: Regex("""(?:^|>)\s*([A-Za-z0-9 .,:!?'-]{3,})\s*(?:<|$)""", RegexOption.IGNORE_CASE)
                            .find(anchorContent)?.groupValues?.get(1)?.trim()
                        ?: return@mapNotNull null
                    val fullUrl = "$baseUrl/anime/$slug"
                    var coverUrl = defaultImage
                    Regex("""<img\b[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach { img ->
                        val src = img.groupValues[1]
                        if (src.contains(slug, ignoreCase = true)) coverUrl = src
                    }
                    ShowResponse(
                        name = name, link = fullUrl, coverUrl = coverUrl,
                        extra = mutableMapOf("slug" to slug)
                    )
                }.toList()
            } catch (e: Exception) {
                Logger.log("AniZone search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        return withContext(Dispatchers.IO) {
            try {
                val slug = animeLink.substringAfter("/anime/").substringBefore("?")
                val html = get("$baseUrl/anime/$slug", "$baseUrl/", "text/html,application/json,*/*")

                if (html.isBlank()) {
                    Logger.log("AniZone loadEpisodes: empty response for slug='$slug'")
                    return@withContext emptyList()
                }

                val count = Regex("""\b(\d+)\s+Episodes?\b""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""Episodes?\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(html)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""\b(\d+)\s+EP\b""", RegexOption.IGNORE_CASE)
                        .find(html)?.groupValues?.get(1)?.toIntOrNull()
                    ?: run {
                        Logger.log("AniZone loadEpisodes: no episode count found in HTML for slug='$slug' (html size=${html.length})")
                        return@withContext emptyList()
                    }

                Logger.log("AniZone loadEpisodes: found $count episodes for slug='$slug'")
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

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
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
