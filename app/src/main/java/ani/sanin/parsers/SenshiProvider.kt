package ani.sanin.parsers

import ani.sanin.Mapper
import ani.sanin.media.Media
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class SenshiProvider : NativeAnimeParser() {

    override val name = "Senshi"
    override val saveName = "Senshi"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val defaultBaseUrl = "https://senshi.live"
    override val knownServers = listOf("Senshi", "StreamNin", "FileMoon")

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val saved = loadSavedShowResponse(mediaObj.id)
        if (saved != null) return saved
        val malId = mediaObj.idMAL
        if (malId == null || malId <= 0) {
            Logger.log("Senshi autoSearch: no MAL id for '${mediaObj.mainName()}' (anilist=${mediaObj.id}) — cannot query MAL-based API")
            return null
        }
        val response = ShowResponse(
            name = mediaObj.mainName(),
            link = saveName,
            coverUrl = mediaObj.cover ?: defaultImage,
            extra = mutableMapOf(
                "anilist_id" to mediaObj.id.toString(),
                "mal_id" to malId.toString()
            )
        )
        saveShowResponse(mediaObj.id, response)
        return response
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val malId = extra?.get("mal_id")?.toIntOrNull()
        if (malId == null || malId <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val jsonStr = getCatalog(malId)
                val array = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return@withContext emptyList()
                array.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val number = (obj["ep_id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                    val title = (obj["ep_title"] as? JsonPrimitive)?.contentOrNull
                    val filler = (obj["ep_filler"] as? JsonPrimitive)?.booleanOrNull == true
                    Episode(
                        number = number.toString(),
                        link = number.toString(),
                        title = title?.takeIf { it.isNotBlank() },
                        isFiller = filler,
                        extra = extra
                    )
                }
            } catch (e: Exception) {
                Logger.log("Senshi loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val malId = extra?.get("mal_id")?.toIntOrNull()
        val episodeNum = episodeLink.toIntOrNull()
        if (malId == null || malId <= 0 || episodeNum == null) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val jsonStr = get("$baseUrl/episode-embeds/$malId/$episodeNum", "$baseUrl/")
                val array = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return@withContext emptyList()

                val dubPreferred = selectDub
                val embed = array.firstOrNull { element ->
                    val obj = element as? JsonObject
                    val status = (obj?.get("status") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (dubPreferred) status.equals("dub", ignoreCase = true)
                    else !status.equals("dub", ignoreCase = true)
                } as? JsonObject ?: return@withContext emptyList()

                val hlsUrl = (embed["url"] as? JsonPrimitive)?.contentOrNull
                if (hlsUrl.isNullOrBlank()) return@withContext emptyList()

                val servers = mutableListOf<VideoServer>()
                val extraData = mutableMapOf("referer" to "$baseUrl/")
                extraData["audio"] = if (dubPreferred) "dub" else "sub"

                val maskedBase = (embed["masked_base_url"] as? JsonPrimitive)?.contentOrNull
                if (!maskedBase.isNullOrBlank()) {
                    try {
                        val prefix = if (dubPreferred) "dub" else "sub"
                        val sidecarUrl = "$maskedBase/${prefix}_filemoon.json"
                        val sidecarStr = get(sidecarUrl, "$baseUrl/")
                        val subs = parseFilemoonSidecar(sidecarStr)
                        if (subs.isNotEmpty()) {
                            extraData["subtitles"] = subs
                        }
                    } catch (_: Exception) {
                        try {
                            val prefix = if (dubPreferred) "dub" else "sub"
                            val sidecarUrl = "$maskedBase/${prefix}_artplayer.json"
                            val sidecarStr = get(sidecarUrl, "$baseUrl/")
                            val subs = parseArtplayerSidecar(sidecarStr, maskedBase)
                            if (subs.isNotEmpty()) {
                                extraData["subtitles"] = subs
                            }
                        } catch (_: Exception) { }
                    }
                }

                servers.add(VideoServer("Senshi", hlsUrl, extraData))

                val audioTag = if (dubPreferred) "dub" else "sub"
                (embed["server2"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { embedUrl ->
                    servers.add(VideoServer("StreamNin", embedUrl, mapOf("audio" to audioTag)))
                }
                (embed["serverFM"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { embedUrl ->
                    servers.add(VideoServer("FileMoon", embedUrl, mapOf("audio" to audioTag)))
                }

                servers
            } catch (e: Exception) {
                Logger.log("Senshi loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    private var catalogCache = mutableMapOf<Int, String>()

    private fun getCatalog(malId: Int): String {
        catalogCache[malId]?.let { return it }
        val json = get("$baseUrl/episodes/$malId", "$baseUrl/")
        catalogCache[malId] = json
        return json
    }

    private fun parseFilemoonSidecar(jsonStr: String): String {
        val array = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return ""
        val subs = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = (obj["src"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: "Subtitle"
            val lang = languageCode(label)
            "{\"url\":\"${url.replace("\"", "\\\"")}\",\"language\":\"$lang\",\"type\":\"vtt\"}"
        }
        return if (subs.isNotEmpty()) "[${subs.joinToString(",")}]" else ""
    }

    private fun parseArtplayerSidecar(jsonStr: String, base: String): String {
        val array = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return ""
        val subs = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val raw = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (raw.isBlank() || raw.equals("none", ignoreCase = true)) return@mapNotNull null
            val url = if (raw.startsWith("http")) raw else "$base/$raw"
            val label = (obj["html"] as? JsonPrimitive)?.contentOrNull ?: "Subtitle"
            val lang = languageCode(label)
            "{\"url\":\"${url.replace("\"", "\\\"")}\",\"language\":\"$lang\",\"type\":\"vtt\"}"
        }
        return if (subs.isNotEmpty()) "[${subs.joinToString(",")}]" else ""
    }

    private fun languageCode(label: String): String = when {
        label.contains("english", ignoreCase = true) || label.contains("eng", ignoreCase = true) -> "en"
        label.contains("spanish", ignoreCase = true) || label.contains("spa", ignoreCase = true) -> "es"
        label.contains("portuguese", ignoreCase = true) || label.contains("por", ignoreCase = true) -> "pt"
        label.contains("french", ignoreCase = true) || label.contains("fra", ignoreCase = true) -> "fr"
        label.contains("german", ignoreCase = true) || label.contains("deu", ignoreCase = true) -> "de"
        label.contains("arabic", ignoreCase = true) || label.contains("ara", ignoreCase = true) -> "ar"
        label.contains("japanese", ignoreCase = true) || label.contains("jpn", ignoreCase = true) -> "ja"
        else -> "und"
    }

    private val JsonPrimitive.intOrNull: Int? get() = contentOrNull?.toIntOrNull()
}
