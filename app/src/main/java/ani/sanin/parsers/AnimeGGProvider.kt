package ani.sanin.parsers

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

class AnimeGGProvider : NativeAnimeParser() {

    override val name = "AnimeGG"
    override val saveName = "animegg"
    override val baseUrl = "https://www.animegg.org"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override suspend fun search(query: String): List<ShowResponse> {
        val html = get("$baseUrl/search/?q=${encode(query)}", "$baseUrl/")
        return Regex("""<a\b([^>]*class=["'][^"']*\bmse\b[^"']*["'][^>]*)>([\s\S]*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html).mapNotNull { match ->
                val href = attr(match.groupValues[1], "href")
                val id = Regex("""^/series/([^/?#]+)""").find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                val strong = Regex("""<strong[^>]*>([\s\S]*?)</strong>""", RegexOption.IGNORE_CASE)
                    .find(match.groupValues[2])?.groupValues?.get(1)
                val title = strong?.let(::stripTags) ?: id.replace('-', ' ')
                ShowResponse(name = title, link = id, coverUrl = defaultImage, extra = mutableMapOf("slug" to id))
            }.distinctBy { it.extra?.get("slug") }.toList()
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime?): List<Episode> {
        val slug = extra?.get("slug") ?: return emptyList()
        val html = get("$baseUrl/series/$slug", "$baseUrl/")
        val sub = linkedSetOf<Int>()
        val dub = linkedSetOf<Int>()
        Regex("""<li\b[^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val block = match.groupValues[1]
            if (!block.contains("anm_det_pop", ignoreCase = true)) return@forEach
            val number = Regex("""<strong[^>]*>[\s\S]*?(\d+)\s*</strong>""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            if (block.contains("btn-subbed", ignoreCase = true)) sub += number
            if (block.contains("btn-dubbed", ignoreCase = true)) dub += number
        }
        val episodes = if (selectDub) dub else sub
        return episodes.map { Episode(number = it.toString(), link = "$baseUrl/series/$slug", extra = extra) }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode?): List<VideoServer> {
        val slug = extra?.get("slug") ?: return emptyList()
        val episodeNum = sEpisode?.number?.toInt() ?: return emptyList()
        val series = get("$baseUrl/series/$slug", "$baseUrl/")
        val episodeSlug = Regex("""<li\b[^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE).findAll(series).mapNotNull { match ->
            val block = match.groupValues[1]
            if (!block.contains("anm_det_pop")) return@mapNotNull null
            val number = Regex("""<strong[^>]*>[\s\S]*?(\d+)\s*</strong>""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            if (number != episodeNum) return@mapNotNull null
            val tag = Regex("""<a\b[^>]*class=["'][^"']*anm_det_pop[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
                .find(block)?.value ?: return@mapNotNull null
            attr(tag, "href").trimStart('/').substringBefore('#')
        }.firstOrNull() ?: return emptyList()

        val watchUrl = "$baseUrl/$episodeSlug"
        val watch = get(watchUrl, mapOf("Referer" to baseUrl))
        val audio = if (selectDub) "dub" else "sub"
        val tabs = Regex("""<a\b[^>]*data-toggle=["']tab["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(watch).mapNotNull { match ->
                val id = attr(match.value, "data-id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val version = attr(match.value, "data-version")
                val tabAudio = if (version.startsWith("dub", true)) "dub" else "sub"
                if (tabAudio != audio) return@mapNotNull null
                id to attr(match.value, "data-mirror").ifBlank { "AnimeGG" }
            }

        val servers = mutableListOf<VideoServer>()
        tabs.take(4).forEach { (id, serverName) ->
            val embed = "$baseUrl/embed/$id"
            val embedHtml = runCatching { get(embed, mapOf("Referer" to baseUrl)) }.getOrDefault("")
            val hls = hlsUrls(embedHtml)
            val mp4Urls = Regex("""file:\s*["']([^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
                .findAll(embedHtml).map { it.groupValues[1] }.toList()
            val allUrls = hls + mp4Urls.map { absoluteUrl(embed, it) }
            allUrls.forEach { url ->
                val type = if (url.contains(".m3u8", ignoreCase = true)) "hls" else "mp4"
                servers += VideoServer("$serverName (${servers.size + 1})", url, mapOf("referer" to embed, "type" to type))
            }
            if (animeGgEmbedCanPlay(embedHtml, allUrls.isNotEmpty())) {
                servers += VideoServer("$serverName embed", embed, mapOf("referer" to baseUrl, "type" to "embed"))
            }
        }
        return servers
    }

    override var selectDub: Boolean = false
}

private fun animeGgEmbedCanPlay(html: String, hasExtractedMedia: Boolean): Boolean {
    if (hasExtractedMedia) return true
    if (html.isBlank()) return false
    val explicitlyEmpty = Regex("""\b(?:var|let|const)\s+videoSources\s*=\s*\[\s*]\s*;?""", RegexOption.IGNORE_CASE)
        .containsMatchIn(html)
    if (explicitlyEmpty) return false
    return html.contains("jwplayer", ignoreCase = true) ||
        Regex("""<iframe\b[^>]*src=["'][^"']+["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)
}
