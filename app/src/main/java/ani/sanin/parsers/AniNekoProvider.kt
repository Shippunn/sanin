package ani.sanin.parsers

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

class AniNekoProvider : NativeAnimeParser() {

    override val name = "AniNeko"
    override val saveName = "anineko"
    override val baseUrl = "https://anineko.to"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override suspend fun search(query: String): List<ShowResponse> {
        val html = get("$baseUrl/browser?keyword=${encode(query)}", "$baseUrl/")
        return Regex("""<a\b([^>]*class=["'][^"']*nv-anime-thumb[^"']*["'][^>]*)>([\s\S]*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html).mapNotNull { match ->
                val href = attr(match.groupValues[1], "href")
                val id = Regex("""/watch/([^/?#]+)""").find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                val title = stripTags(match.groupValues[2]).ifBlank { id.replace('-', ' ') }
                ShowResponse(name = title, link = id, coverUrl = defaultImage, extra = mutableMapOf("slug" to id))
            }.distinctBy { it.extra?.get("slug") }.toList()
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime?): List<Episode> {
        val slug = extra?.get("slug") ?: return emptyList()
        val html = get("$baseUrl/watch/$slug", "$baseUrl/")
        val sub = linkedSetOf<Int>()
        val dub = linkedSetOf<Int>()
        Regex("""<article\b[^>]*class=["'][^"']*nv-info-episode-item[^"']*["'][^>]*>([\s\S]*?)</article>""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { match ->
                val block = match.groupValues[1]
                val number = Regex("""/ep-(\d+)""", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                if (Regex(""">\s*(?:SUB|HSUB)\s*<""", RegexOption.IGNORE_CASE).containsMatchIn(block)) sub += number
                if (Regex(""">\s*DUB\s*<""", RegexOption.IGNORE_CASE).containsMatchIn(block)) dub += number
            }
        val episodes = if (selectDub) dub else sub
        return episodes.map { Episode(number = it.toString(), link = "$baseUrl/watch/$slug", extra = extra) }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode?): List<VideoServer> {
        val slug = extra?.get("slug") ?: return emptyList()
        val episodeNum = sEpisode?.number?.toInt() ?: return emptyList()
        val watchUrl = "$baseUrl/watch/$slug/ep-$episodeNum"
        val html = get(watchUrl, mapOf("Referer" to "$baseUrl/watch/$slug"))

        val audio = if (selectDub) "dub" else "sub"
        val embeds = mutableListOf<String>()
        Regex("""<div\b[^>]*class=["'][^"']*nv-server-grid[^"']*["'][^>]*data-id=["']([^"']+)["'][^>]*>([\s\S]*?)(?=<div\b[^>]*class=["'][^"']*nv-server-grid|$)""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { panel ->
                val panelAudio = if (panel.groupValues[1].contains("dub", true)) "dub" else "sub"
                if (panelAudio == audio) {
                    Regex("""data-video=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .findAll(panel.groupValues[2]).forEach { embeds += decodeEntities(it.groupValues[1]) }
                }
            }

        val servers = mutableListOf<VideoServer>()
        val subtitles = mutableListOf<String>()
        embeds.distinct().take(4).forEachIndexed { index, embed ->
            val embedHtml = runCatching { get(embed, mapOf("Referer" to "$baseUrl/")) }.getOrDefault("")
            val hls = hlsUrls(embedHtml)
            hls.forEach { url ->
                servers += VideoServer("AniNeko (${servers.size + 1})", url, mapOf("referer" to embed, "type" to "hls"))
            }
            subtitles += embedQuerySubtitles(embed)
            servers += VideoServer("AniNeko embed", embed, mapOf("referer" to embed, "type" to "embed"))
        }
        return servers
    }

    private fun embedQuerySubtitles(embedUrl: String): List<String> {
        val query = embedUrl.substringAfter('?', "")
        if (query.isBlank()) return emptyList()
        val params = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }
        val byName = params.toMap()
        return params.mapNotNull { (key, value) ->
            if (!value.startsWith("http")) return@mapNotNull null
            val label = when {
                key == "sub" -> "English"
                key.startsWith("caption") -> byName["sub${key.removePrefix("caption")}"] ?: "Subtitle"
                else -> return@mapNotNull null
            }
            """{"url":"${value.replace("\"", "\\\"")}","language":"${language(label)}","type":"vtt"}"""
        }
    }

    override var selectDub: Boolean = false
}
