package ani.sanin.parsers

import ani.sanin.FileUrl
import ani.sanin.Mapper
import ani.sanin.okHttpClient
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.io.IOException
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AnimeAV1Provider : NativeAnimeParser() {

    override val name = "AnimeAV1"
    override val saveName = "animeav1"
    override val defaultBaseUrl = "https://animeav1.com"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val knownServers = listOf("HLS", "UPNShare", "Mp4Upload")

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val json = av1Get("$baseUrl/catalogo/__data.json?search=${encode(query)}", "$baseUrl/")
                val root = av1PageRoot(json) ?: return@withContext emptyList()
                val results = root["results"] as? JsonArray ?: return@withContext emptyList()
                results.mapNotNull { it as? JsonObject }.mapNotNull { r ->
                    val id = (r["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val slug = (r["slug"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val title = (r["title"] as? JsonPrimitive)?.contentOrNull ?: slug.replace('-', ' ')
                    ShowResponse(
                        name = title,
                        link = slug,
                        coverUrl = "https://cdn.animeav1.com/covers/$id.jpg",
                        extra = mutableMapOf("slug" to slug)
                    )
                }.distinctBy { it.extra?.get("slug") }.toList()
            } catch (e: Exception) {
                Logger.log("AnimeAV1 search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val slug = extra?.get("slug") ?: animeLink.trimEnd('/').substringAfterLast('/')
        return withContext(Dispatchers.IO) {
            try {
                val json = av1Get("$baseUrl/media/$slug/__data.json", "$baseUrl/")
                val root = av1PageRoot(json) ?: return@withContext emptyList()
                val media = root["media"] as? JsonObject ?: return@withContext emptyList()
                val episodes = media["episodes"] as? JsonArray ?: return@withContext emptyList()
                episodes.mapNotNull { it as? JsonObject }.mapNotNull { ep ->
                    val number = (ep["number"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                        ?: return@mapNotNull null
                    Episode(
                        number = number.toString(),
                        link = "$baseUrl/media/$slug/$number",
                        extra = extra
                    )
                }.sortedBy { it.number.toIntOrNull() ?: 0 }.toList()
            } catch (e: Exception) {
                Logger.log("AnimeAV1 loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        return withContext(Dispatchers.IO) {
            try {
                val json = av1Get("$episodeLink/__data.json", "$baseUrl/")
                val root = av1PageRoot(json) ?: return@withContext emptyList()
                val embeds = root["embeds"] as? JsonObject ?: return@withContext emptyList()
                val servers = mutableListOf<VideoServer>()
                val seen = mutableSetOf<String>()
                embeds.forEach { (audio, group) ->
                    val list = group as? JsonArray ?: return@forEach
                    list.forEach { item ->
                        val obj = item as? JsonObject ?: return@forEach
                        val name = (obj["server"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        val url = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        val family = hostFamily(url) ?: return@forEach
                        val key = "$family|$audio"
                        if (!seen.add(key)) return@forEach
                        servers.add(
                            VideoServer(
                                family,
                                url,
                                mutableMapOf(
                                    "referer" to refererFor(family),
                                    "host" to family,
                                    "audio" to audio
                                )
                            )
                        )
                    }
                }
                servers.toList()
            } catch (e: Exception) {
                Logger.log("AnimeAV1 loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return when (server.extraData?.get("host") ?: hostFamily(server.embed.url)) {
            "UPNShare" -> UpnShareExtractor(server)
            "Mp4Upload" -> Mp4UploadExtractor(server)
            "HLS" -> ZillaHlsExtractor(server)
            else -> Av1EmptyExtractor(server)
        }
    }

    companion object {
        fun hostFamily(embed: String): String? {
            val host = runCatching { URI(embed).host }
                .getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
            return when {
                host.contains("zilla-networks") -> "HLS"
                host.contains("uns.bio") -> "UPNShare"
                host == "mp4upload.com" || host.endsWith(".mp4upload.com") -> "Mp4Upload"
                host == "mega.nz" || host.endsWith(".mega.nz") -> "Mega"
                else -> null
            }
        }

        private fun refererFor(family: String): String = when (family) {
            "UPNShare" -> "https://animeav1.uns.bio/"
            else -> "https://animeav1.com/"
        }
    }
}

class UpnShareExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val id = server.embed.url.substringAfter('#', "").trim()
            if (id.isBlank()) {
                Logger.log("AnimeAV1 UPNShare: no id in ${server.embed.url}")
                return@withContext VideoContainer(emptyList())
            }
            val referer = server.extraData?.get("referer") ?: "https://animeav1.uns.bio/"
            val host = runCatching { URI(referer).host }.getOrNull()?.removePrefix("www.")
                ?: "animeav1.com"
            val api = "https://animeav1.uns.bio/api/v1/video?id=$id&w=1920&h=1080&r=$host"
            val res = av1Get(api, referer)
            val decrypted = av1Decrypt(res)
            val obj = decrypted?.let {
                runCatching { Mapper.json.parseToJsonElement(it) as? JsonObject }.getOrNull()
            }
            val source = (obj?.get("source") as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: (obj?.get("cfNative") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            if (source.isNullOrBlank()) {
                Logger.log("AnimeAV1 UPNShare: no source in api response")
                return@withContext VideoContainer(emptyList())
            }
            val master = runCatching { av1Get(source, referer) }.getOrNull().orEmpty()
            val videos = av1MasterVideos(master, source, referer)
            if (videos.isEmpty()) {
                VideoContainer(
                    listOf(Video(null, VideoType.M3U8, FileUrl(source, mapOf("Referer" to referer))))
                )
            } else {
                VideoContainer(videos)
            }
        } catch (e: Exception) {
            Logger.log("AnimeAV1 UPNShare extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class ZillaHlsExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val referer = server.extraData?.get("referer")
            val page = av1Get(server.embed.url, referer)
            val urls = Regex("""https?://[^"'\s<>]+?\.m3u8[^"'\s<>]*""", RegexOption.IGNORE_CASE)
                .findAll(page).map { it.value.trim() }.toList()
            val url = urls.firstOrNull {
                it.contains("master", ignoreCase = true) || it.contains("index", ignoreCase = true)
            } ?: urls.firstOrNull()
            if (url.isNullOrBlank()) {
                Logger.log("AnimeAV1 HLS: no m3u8 in ${server.embed.url}")
                return@withContext VideoContainer(emptyList())
            }
            VideoContainer(
                listOf(
                    Video(
                        null,
                        VideoType.M3U8,
                        FileUrl(url, mapOf("Referer" to av1OriginOf(server.embed.url)))
                    )
                )
            )
        } catch (e: Exception) {
            Logger.log("AnimeAV1 HLS extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class Av1EmptyExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        Logger.log("AnimeAV1: no extractor for host '${server.extraData?.get("host")}' (${server.embed.url})")
        return VideoContainer(emptyList())
    }
}

private suspend fun av1Get(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url)
        .header("User-Agent", NativeAnimeParser.USER_AGENT)
        .apply { referer?.let { header("Referer", it) } }
        .get().build()
    okHttpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        body
    }
}

private fun av1PageRoot(json: String): JsonObject? = runCatching {
    val doc = Mapper.json.parseToJsonElement(json) as? JsonObject ?: return null
    val nodes = doc["nodes"] as? JsonArray ?: return null
    nodes.mapNotNull { it as? JsonObject }
        .mapNotNull { node ->
            val data = node["data"] as? JsonArray ?: return@mapNotNull null
            val root = data.firstOrNull() ?: return@mapNotNull null
            root.devalueDeref(data) as? JsonObject
        }
        .firstOrNull { it.containsKey("media") || it.containsKey("results") }
}.getOrNull()

private fun JsonElement.devalueDeref(data: JsonArray): JsonElement = when (this) {
    is JsonPrimitive -> {
        val n = if (isString) null else intOrNull
        if (n != null && n >= 0 && n < data.size) data[n].devalueResolve(data) else this
    }
    is JsonArray -> JsonArray(map { it.devalueDeref(data) })
    is JsonObject -> JsonObject(mapValues { it.value.devalueDeref(data) })
    else -> this
}

private fun JsonElement.devalueResolve(data: JsonArray): JsonElement = when (this) {
    is JsonArray -> JsonArray(map { it.devalueDeref(data) })
    is JsonObject -> JsonObject(mapValues { it.value.devalueDeref(data) })
    else -> this
}

private fun av1MasterVideos(master: String, masterUrl: String, referer: String): List<Video> {
    val base = runCatching { URI(masterUrl) }.getOrNull() ?: return emptyList()
    return Regex(
        """#EXT-X-STREAM-INF:[^\r\n]*?RESOLUTION=(\d+)x(\d+)[^\r\n]*\r?\n([^\r\n]+)""",
        RegexOption.IGNORE_CASE
    ).findAll(master).mapNotNull { m ->
        val height = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val child = m.groupValues[3].trim()
        val url = if (child.startsWith("http")) child
        else base.resolve(child).toString()
        Video(height, VideoType.M3U8, FileUrl(url, mapOf("Referer" to referer)))
    }.distinctBy { it.file.url }.toList()
}

private fun av1Decrypt(hex: String): String? = try {
    val bytes = hex.trim().chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
    val key = SecretKeySpec("kiemtienmua911ca".toByteArray(Charsets.UTF_8), "AES")
    val iv = IvParameterSpec("1234567890oiuytr".toByteArray(Charsets.UTF_8))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, key, iv)
    String(cipher.doFinal(bytes), Charsets.UTF_8)
} catch (e: Exception) {
    Logger.log("AnimeAV1 UPNShare decrypt error: ${e.message}")
    null
}

private fun av1OriginOf(url: String): String = runCatching {
    URI(url).let { "${it.scheme}://${it.authority}" }
}.getOrDefault(url.substringBefore('/', ""))
