package ani.sanin.parsers

import ani.sanin.Mapper
import ani.sanin.media.Media
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class AniBdProvider : NativeAnimeParser() {

    override val name = "AniBD"
    override val saveName = "AniBD"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val defaultBaseUrl = "https://epeng.animeapps.top"
    private val catalogCache = mutableMapOf<Int, String>()

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
        return withContext(Dispatchers.IO) {
            try {
                val jsonStr = getCatalog(anilistId)
                val groups = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return@withContext emptyList()

                val numbers = mutableSetOf<Int>()
                groups.mapNotNull { it as? JsonObject }.forEach { group ->
                    val serverData = group["server_data"] as? JsonArray ?: return@forEach
                    serverData.mapNotNull { it as? JsonObject }.forEach { ep ->
                        val name = (ep["name"] as? JsonPrimitive)?.contentOrNull
                        val number = name?.toIntOrNull() ?: return@forEach
                        numbers.add(number)
                    }
                }

                numbers.sorted().map { number ->
                    Episode(
                        number = number.toString(),
                        link = number.toString(),
                        extra = extra
                    )
                }
            } catch (e: Exception) {
                Logger.log("AniBD loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val anilistId = extra?.get("anilist_id")?.toIntOrNull()
        val episodeNum = episodeLink.toIntOrNull()
        if (anilistId == null || episodeNum == null) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val jsonStr = getCatalog(anilistId)
                val groups = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return@withContext emptyList()

                val dubPreferred = selectDub
                val audio = if (dubPreferred) "dub" else "sub"

                val link = groups.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { group ->
                    val name = (group["server_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val groupAudio = if (name.contains("dub", ignoreCase = true)) "dub" else "sub"
                    if (groupAudio != audio) return@firstNotNullOfOrNull null
                    val serverData = group["server_data"] as? JsonArray ?: return@firstNotNullOfOrNull null
                    serverData.mapNotNull { it as? JsonObject }.firstOrNull { ep ->
                        val epName = (ep["name"] as? JsonPrimitive)?.contentOrNull
                        epName?.toIntOrNull() == episodeNum
                    }?.let { (it["link"] as? JsonPrimitive)?.contentOrNull }
                } ?: return@withContext emptyList()

                val playersJson = get("$baseUrl/apilink.php?data=$link", "$baseUrl/")
                val players = Mapper.json.parseToJsonElement(playersJson) as? JsonArray ?: return@withContext emptyList()

                val servers = mutableListOf<VideoServer>()
                players.mapNotNull { it as? JsonObject }.take(3).forEach { player ->
                    val playerUrl = (player["link"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    val origin = originOf(playerUrl)
                    val html = try {
                        get(playerUrl, origin, "text/html,application/json,*/*")
                    } catch (_: Exception) { return@forEach }

                    val hls = AniBdParser.videoUrl(html, origin)
                    if (hls != null) {
                        val serverName = (player["server"] as? JsonPrimitive)?.contentOrNull ?: "AniBD"
                        val extraData = mutableMapOf("referer" to "$origin/")
                        extraData["audio"] = audio
                        val subs = AniBdParser.trackSubtitles(html)
                        if (subs.isNotEmpty()) extraData["subtitles"] = subs
                        servers.add(VideoServer("AniBD $serverName", hls, extraData))
                    }
                }

                if (servers.isEmpty()) {
                    players.mapNotNull { it as? JsonObject }.firstOrNull()?.let { player ->
                        val playerUrl = (player["link"] as? JsonPrimitive)?.contentOrNull
                        if (!playerUrl.isNullOrBlank()) {
                            servers.add(VideoServer("AniBD embed", playerUrl, mapOf("audio" to audio)))
                        }
                    }
                }

                servers
            } catch (e: Exception) {
                Logger.log("AniBD loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun getCatalog(anilistId: Int): String {
        catalogCache[anilistId]?.let { return it }
        val json = get("$baseUrl/api2.php?epid=$anilistId", "$baseUrl/")
        catalogCache[anilistId] = json
        return json
    }

    private fun originOf(url: String): String = runCatching {
        java.net.URI(url).let { "${it.scheme}://${it.authority}" }
    }.getOrDefault(baseUrl)
}

object AniBdParser {
    fun videoUrl(html: String, origin: String): String? {
        val raw = Regex("""videoUrl\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/") -> "$origin$raw"
            else -> "$origin/$raw"
        }
    }

    fun trackSubtitles(html: String): String {
        val block = Regex("""tracks\s*:\s*\[([\s\S]*?)\]""").find(html)?.groupValues?.get(1) ?: return ""
        val subs = Regex("""\{[^{}]*\}""").findAll(block).mapNotNull { entry ->
            val text = entry.value
            fun field(name: String): String? =
                Regex(""""$name"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1)
            val file = field("file")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val kind = field("kind")
            if (kind != null && kind != "captions" && kind != "subtitles") return@mapNotNull null
            val label = field("label")?.takeIf(String::isNotBlank) ?: "Subtitle"
            val lang = when {
                label.contains("eng", ignoreCase = true) -> "en"
                label.contains("spa", ignoreCase = true) -> "es"
                label.contains("por", ignoreCase = true) -> "pt"
                else -> "und"
            }
            "{\"url\":\"${file.replace("\"", "\\\"")}\",\"language\":\"$lang\",\"type\":\"vtt\"}"
        }
        return if (subs.toList().isNotEmpty()) "[${subs.toList().joinToString(",")}]" else ""
    }
}
