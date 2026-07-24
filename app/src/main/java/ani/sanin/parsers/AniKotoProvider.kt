package ani.sanin.parsers

import ani.sanin.Mapper
import ani.sanin.media.Media
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class AniKotoProvider : NativeAnimeParser() {

    override val name = "AniKoto"
    override val saveName = "AniKoto"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val baseUrl = "https://megaplay.buzz"

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val saved = loadSavedShowResponse(mediaObj.id)
        if (saved != null) return saved
        val response = ShowResponse(
            name = mediaObj.mainName(),
            link = saveName,
            coverUrl = mediaObj.cover ?: defaultImage,
            extra = mutableMapOf(
                "anilist_id" to mediaObj.id.toString(),
                "mal_id" to (mediaObj.idMAL?.toString() ?: "")
            )
        )
        saveShowResponse(mediaObj.id, response)
        return response
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val anilistId = extra?.get("anilist_id")?.toIntOrNull() ?: return emptyList()
        val malId = extra?.get("mal_id")?.toIntOrNull()
        return withContext(Dispatchers.IO) {
            try {
                val ids = listOfNotNull(anilistId, malId).distinct()
                val highestSub = ids.maxOfOrNull { id ->
                    val kind = if (id == anilistId) "ani" else "mal"
                    countEpisodes(kind, id, "sub")
                } ?: 0
                val highestDub = ids.maxOfOrNull { id ->
                    val kind = if (id == anilistId) "ani" else "mal"
                    countEpisodes(kind, id, "dub")
                } ?: 0

                val max = maxOf(highestSub, highestDub)
                if (max == 0) return@withContext emptyList()

                (1..max).map { number ->
                    Episode(
                        number = number.toString(),
                        link = number.toString(),
                        extra = extra
                    )
                }
            } catch (e: Exception) {
                Logger.log("AniKoto loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val anilistId = extra?.get("anilist_id")?.toIntOrNull()
        val malId = extra?.get("mal_id")?.toIntOrNull()
        val episodeNum = episodeLink.toIntOrNull()
        if ((anilistId == null && malId == null) || episodeNum == null) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val dubPreferred = selectDub
                val audio = if (dubPreferred) "dub" else "sub"
                val referer = "https://hianimes.re/"

                val streams = mutableListOf<VideoServer>()
                val ids = listOfNotNull(anilistId?.let { "ani" to it }, malId?.let { "mal" to it })

                for ((kind, id) in ids) {
                    val url = "$baseUrl/stream/$kind/$id/$episodeNum/$audio"
                    val page = try {
                        get(url, referer, "text/html,application/json,*/*")
                    } catch (_: Exception) { continue }

                    val hls = extractHlsFromMegaPlay(page, url)
                    if (hls != null) {
                        val extraData = mutableMapOf("referer" to referer)
                        val subs = extractMegaPlaySubtitles(page, url)
                        if (subs.isNotEmpty()) extraData["subtitles"] = subs
                        streams.add(VideoServer("AniKoto", hls, extraData))
                        return@withContext streams
                    }
                }

                streams
            } catch (e: Exception) {
                Logger.log("AniKoto loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun countEpisodes(kind: String, id: Int, audio: String): Int {
        return try {
            for (ep in (1..200).toList()) {
                val url = "$baseUrl/stream/$kind/$id/$ep/$audio"
                val page = try {
                    get(url, "https://hianimes.re/", "text/html,application/json,*/*")
                } catch (_: Exception) { break }
                if (page.isBlank() || page.contains("Error", ignoreCase = true)) {
                    return ep - 1
                }
            }
            0
        } catch (_: Exception) { 0 }
    }

    private fun extractHlsFromMegaPlay(page: String, pageUrl: String): String? {
        val iframe = Regex("""<iframe\b[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(page)?.groupValues?.get(1)
        val targetPage = if (iframe != null) {
            try {
                get(iframe, "https://hianimes.re/", "text/html,application/json,*/*")
            } catch (_: Exception) { return null }
        } else page

        val fileId = Regex("""data-id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(targetPage)?.groupValues?.get(1) ?: return null

        val origin = originOf(targetPage)
        val sourceJsonStr = try {
            get("$origin/stream/getSources?id=$fileId&id=$fileId", origin, "application/json, */*")
        } catch (_: Exception) { return null }

        val sourceJson = Mapper.json.parseToJsonElement(sourceJsonStr) as? JsonObject ?: return null
        val sources = sourceJson["sources"] as? JsonObject
        return (sources?.get("file") as? JsonPrimitive)?.contentOrNull
    }

    private fun extractMegaPlaySubtitles(page: String, pageUrl: String): String {
        val iframe = Regex("""<iframe\b[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(page)?.groupValues?.get(1)
        val targetPage = if (iframe != null) {
            try {
                get(iframe, "https://hianimes.re/", "text/html,application/json,*/*")
            } catch (_: Exception) { return "" }
        } else page

        val fileId = Regex("""data-id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(targetPage)?.groupValues?.get(1) ?: return ""

        val origin = originOf(targetPage)
        val sourceJsonStr = try {
            get("$origin/stream/getSources?id=$fileId&id=$fileId", origin, "application/json, */*")
        } catch (_: Exception) { return "" }

        val sourceJson = Mapper.json.parseToJsonElement(sourceJsonStr) as? JsonObject ?: return ""
        val tracks = sourceJson["tracks"] as? JsonArray ?: return ""

        val subs = tracks.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull?.lowercase()
            if (kind != null && kind != "captions" && kind != "subtitles") return@mapNotNull null
            val file = (obj["file"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: "Subtitle"
            val type = if (file.contains(".vtt", ignoreCase = true)) "vtt" else "srt"
            val lang = when {
                label.contains("eng", ignoreCase = true) -> "en"
                label.contains("spa", ignoreCase = true) -> "es"
                label.contains("por", ignoreCase = true) -> "pt"
                label.contains("fre", ignoreCase = true) || label.contains("fra", ignoreCase = true) -> "fr"
                else -> "und"
            }
            "{\"url\":\"${file.replace("\"", "\\\"")}\",\"language\":\"$lang\",\"type\":\"$type\"}"
        }
        return if (subs.isNotEmpty()) "[${subs.joinToString(",")}]" else ""
    }

    private fun originOf(url: String): String = runCatching {
        java.net.URI(url).let { "${it.scheme}://${it.authority}" }
    }.getOrDefault(baseUrl)
}
