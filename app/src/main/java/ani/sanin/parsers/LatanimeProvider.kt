package ani.sanin.parsers

import android.util.Base64
import ani.sanin.FileUrl
import ani.sanin.Mapper
import ani.sanin.okHttpClient
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

class LatanimeProvider : NativeAnimeParser() {

    override val name = "Latanime"
    override val saveName = "latanime"
    override val defaultBaseUrl = "https://latanime.org"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val knownServers = listOf(
        "DoodStream", "MixDrop", "Voe", "FileMoon", "Mp4Upload", "Hexload",
        "SaveFiles", "StreamWish", "VEmbed"
    )

    override suspend fun search(query: String): List<ShowResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val html = latGet("$baseUrl/buscar?q=${encode(query)}", "$baseUrl/")
                Regex(
                    """<a\b[^>]*href="https://latanime\.org/anime/([^"/]+)"[^>]*>([\s\S]*?)</a>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).findAll(html).mapNotNull { match ->
                    val slug = match.groupValues[1]
                    val block = match.groupValues[2]
                    val title = Regex("""<h3[^>]*>([\s\S]*?)</h3>""", RegexOption.IGNORE_CASE)
                        .find(block)?.groupValues?.get(1)?.let(::stripTags)
                        ?.takeIf { it.isNotBlank() }
                        ?: slug.replace('-', ' ')
                    val cover = Regex("""<img\b[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
                        .find(block)?.groupValues?.get(1)?.takeIf { it.startsWith("http") } ?: defaultImage
                    ShowResponse(
                        name = title,
                        link = slug,
                        coverUrl = cover,
                        extra = mutableMapOf("slug" to slug)
                    )
                }.distinctBy { it.extra?.get("slug") }.toList()
            } catch (e: Exception) {
                Logger.log("Latanime search error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val slug = extra?.get("slug") ?: animeLink.substringAfterLast('/')
        return withContext(Dispatchers.IO) {
            try {
                val html = latGet("$baseUrl/anime/$slug", "$baseUrl/")
                Regex(
                    """href="https://latanime\.org/ver/([^"]+?)-episodio-(\d+)"""",
                    RegexOption.IGNORE_CASE
                ).findAll(html).mapNotNull { match ->
                    val number = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    Episode(
                        number = number.toString(),
                        link = "$baseUrl/ver/${match.groupValues[1]}-episodio-${match.groupValues[2]}",
                        extra = extra
                    )
                }.sortedBy { it.number.toIntOrNull() ?: 0 }.toList()
            } catch (e: Exception) {
                Logger.log("Latanime loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        return withContext(Dispatchers.IO) {
            try {
                val html = latGet(episodeLink, "$baseUrl/")
                val audio = if (
                    episodeLink.contains("castellano", ignoreCase = true) ||
                    episodeLink.contains("latino", ignoreCase = true) ||
                    episodeLink.contains("redoblaje", ignoreCase = true) ||
                    episodeLink.contains("-dub", ignoreCase = true)
                ) "DUB" else "SUB"

                val seen = mutableSetOf<String>()
                Regex("""data-player="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .findAll(html).mapNotNull { match ->
                        val embed = b64Decode(match.groupValues[1])?.trim() ?: return@mapNotNull null
                        if (!embed.startsWith("http")) return@mapNotNull null
                        val family = hostFamily(embed) ?: return@mapNotNull null
                        if (!seen.add(family)) return@mapNotNull null
                        VideoServer(
                            family,
                            embed,
                            mutableMapOf(
                                "referer" to "$baseUrl/",
                                "host" to family,
                                "audio" to audio
                            )
                        )
                    }.toList()
            } catch (e: Exception) {
                Logger.log("Latanime loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return when (server.extraData?.get("host") ?: hostFamily(server.embed.url)) {
            "Mp4Upload" -> Mp4UploadExtractor(server)
            "Hexload" -> HexloadExtractor(server)
            "MixDrop" -> MixDropExtractor(server)
            "DoodStream" -> DoodStreamExtractor(server)
            "Voe" -> VoeExtractor(server)
            "FileMoon", "StreamWish", "VEmbed" -> SourceApiExtractor(server)
            "SaveFiles" -> SaveFilesExtractor(server)
            else -> EmptyExtractor(server)
        }
    }

    companion object {
        fun hostFamily(embed: String): String? {
            val host = runCatching { URI(embed).host }
                .getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
            return when {
                host == "doodstream.com" || host.endsWith(".doodstream.com") ||
                    host == "dsvplay.com" || host.endsWith(".dsvplay.com") ||
                    host == "bysekoze.com" || host.endsWith(".bysekoze.com") ||
                    host == "playmogo.com" || host.endsWith(".playmogo.com") -> "DoodStream"
                host.contains("mixdrop") -> "MixDrop"
                host == "voe.sx" || host.endsWith(".voe.sx") -> "Voe"
                host == "filemoon.sx" || host.endsWith(".filemoon.sx") ||
                    host == "filemoon.to" || host.endsWith(".filemoon.to") ||
                    host == "fviplions.com" || host.endsWith(".fviplions.com") ||
                    host == "luluvdo.com" || host.endsWith(".luluvdo.com") -> "FileMoon"
                host == "mp4upload.com" || host.endsWith(".mp4upload.com") -> "Mp4Upload"
                host == "hexload.com" || host.endsWith(".hexload.com") ||
                    host == "hexupload.net" || host.endsWith(".hexupload.net") -> "Hexload"
                host == "savefiles.com" || host.endsWith(".savefiles.com") -> "SaveFiles"
                host == "streamwish.to" || host.endsWith(".streamwish.to") ||
                    host == "streamwish.com" || host.endsWith(".streamwish.com") ||
                    host == "wish.cloud" || host.endsWith(".wish.cloud") -> "StreamWish"
                host == "vembed.net" || host.endsWith(".vembed.net") -> "VEmbed"
                else -> null
            }
        }
    }
}

class Mp4UploadExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val referer = server.extraData?.get("referer")
            val page = latGet(server.embed.url, referer)
            val mp4 = Regex("""src:\s*"([^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE)
                .findAll(page).map { it.groupValues[1].trim() }
                .firstOrNull { it.contains("mp4upload", ignoreCase = true) }
            if (mp4.isNullOrBlank()) {
                Logger.log("Latanime Mp4Upload: no mp4 found in ${server.embed.url}")
                VideoContainer(emptyList())
            } else {
                val direct = if (mp4.startsWith("//")) "https:$mp4" else mp4
                Logger.log("Latanime Mp4Upload: got ${direct.take(120)}")
                VideoContainer(
                    listOf(Video(null, VideoType.CONTAINER, FileUrl(direct, mapOf("Referer" to originOf(direct)))))
                )
            }
        } catch (e: Exception) {
            Logger.log("Latanime Mp4Upload extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class HexloadExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val id = server.embed.url.substringAfterLast('/').removePrefix("embed-").substringBefore('?')
            val origin = originOf(server.embed.url)
            val res = latPostForm(
                "$origin/download",
                mapOf(
                    "op" to "download3",
                    "id" to id,
                    "ajax" to "1",
                    "method_free" to "1",
                    "dataType" to "json"
                ),
                referer = server.embed.url,
                xhr = true
            )
            val obj = Mapper.json.parseToJsonElement(res) as? JsonObject
            val result = obj?.get("result") as? JsonObject
            val url = (result?.get("url") as? JsonPrimitive)?.contentOrNull
            val size = (result?.get("size") as? JsonPrimitive)?.doubleOrNull
            if (url.isNullOrBlank()) {
                Logger.log("Latanime Hexload: no url in response")
                VideoContainer(emptyList())
            } else {
                Logger.log("Latanime Hexload: got ${url.take(120)} size=$size")
                VideoContainer(
                    listOf(Video(null, VideoType.CONTAINER, FileUrl(url, mapOf("Referer" to origin)), size))
                )
            }
        } catch (e: Exception) {
            Logger.log("Latanime Hexload extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class MixDropExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val referer = server.extraData?.get("referer")
            val page = latGet(server.embed.url, referer)
            val decoded = unpackMixdropPacker(page)
            if (decoded == null) {
                Logger.log("Latanime MixDrop: no packed config in ${server.embed.url}")
                VideoContainer(emptyList())
            } else {
                val wurl = Regex("""MDCore\.wurl\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(decoded)?.groupValues?.get(1)?.trim()
                if (wurl.isNullOrBlank()) {
                    Logger.log("Latanime MixDrop: no wurl in decoded config")
                    VideoContainer(emptyList())
                } else {
                    val direct = if (wurl.startsWith("//")) "https:$wurl" else wurl
                    Logger.log("Latanime MixDrop: got ${direct.take(120)}")
                    VideoContainer(
                        listOf(Video(null, VideoType.CONTAINER, FileUrl(direct, mapOf("Referer" to originOf(direct)))))
                    )
                }
            }
        } catch (e: Exception) {
            Logger.log("Latanime MixDrop extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class DoodStreamExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val referer = server.extraData?.get("referer")
            val embed = server.embed.url
            val page = latGet(embed, referer)
            Logger.log(
                "Latanime DoodStream: ${embed} pageSize=${page.length} hasPassMd5=${page.contains("pass_md5", ignoreCase = true)}"
            )
            val token = Regex("""pass_md5/([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
                .find(page)?.groupValues?.get(1)
            if (token.isNullOrBlank()) {
                Logger.log("Latanime DoodStream: no pass_md5 token (likely Cloudflare challenge)")
                VideoContainer(emptyList())
            } else {
                val origin = originOf(embed)
                val pass = latGet("$origin/pass_md5/$token", embed)
                Logger.log("Latanime DoodStream: pass_md5 response=${pass.take(200)}")
                val direct = Regex("""https?://[^"'\s<>]+?\.mp4[^"'\s<>]*""", RegexOption.IGNORE_CASE)
                    .find(pass)?.value
                    ?: runCatching {
                        val obj = Mapper.json.parseToJsonElement(pass) as? JsonObject
                        (obj?.get("result") as? JsonPrimitive)?.contentOrNull
                            ?.takeIf { it.contains(".mp4", ignoreCase = true) }
                    }.getOrNull()
                if (direct.isNullOrBlank()) {
                    Logger.log("Latanime DoodStream: no direct url in pass_md5 response")
                    VideoContainer(emptyList())
                } else {
                    VideoContainer(
                        listOf(Video(null, VideoType.CONTAINER, FileUrl(direct, mapOf("Referer" to origin))))
                    )
                }
            }
        } catch (e: Exception) {
            Logger.log("Latanime DoodStream extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class VoeExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val referer = server.extraData?.get("referer")
            val embed = server.embed.url
            val id = embed.substringAfterLast('/').substringBefore('?')
            val page = latGet(embed, referer)
            val mirror = Regex("""window\.location\.href\s*=\s*'([^']+)'""")
                .find(page)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
            val target = mirror ?: embed
            val page2 = if (mirror == null) page else latGet(target, referer)
            val heleket = Regex(
                """<meta[^>]*name=["']heleket["'][^>]*content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(page2)?.groupValues?.get(1)
            Logger.log("Latanime Voe: mirror=${mirror ?: "none"} heleket=${heleket ?: "none"}")
            if (heleket.isNullOrBlank()) {
                Logger.log("Latanime Voe: no heleket token in $target")
                VideoContainer(emptyList())
            } else {
                val api = "${originOf(target)}/api/player/$id"
                val res = latPostJson(api, """{"heleket":"$heleket"}""", target)
                Logger.log("Latanime Voe: api response=${res.take(300)}")
                val obj = runCatching { Mapper.json.parseToJsonElement(res) as? JsonObject }.getOrNull()
                val sources = obj?.get("sources") as? JsonArray
                val videos = sources?.mapNotNull { s ->
                    val so = s as? JsonObject ?: return@mapNotNull null
                    val url = (so["file"] as? JsonPrimitive)?.contentOrNull
                        ?: (so["src"] as? JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                    val label = (so["label"] as? JsonPrimitive)?.contentOrNull
                    val quality = label?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                    Video(quality, VideoType.M3U8, FileUrl(url, mapOf("Referer" to target)))
                }
                if (videos.isNullOrEmpty()) {
                    Logger.log("Latanime Voe: no sources parsed from api")
                    VideoContainer(emptyList())
                } else {
                    VideoContainer(videos)
                }
            }
        } catch (e: Exception) {
            Logger.log("Latanime Voe extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class SourceApiExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        val host = server.extraData?.get("host") ?: "FileMoon"
        try {
            val referer = server.extraData?.get("referer")
            val embed = server.embed.url
            val id = embed.substringAfterLast('/').substringBefore('?')
            val origin = originOf(embed)
            val api = "$origin/api/source/$id"
            val res = latPostForm(api, mapOf("r" to embed), referer, xhr = true)
            Logger.log("Latanime $host: api response=${res.take(200)}")
            val obj = runCatching { Mapper.json.parseToJsonElement(res) as? JsonObject }.getOrNull()
            val data = obj?.get("data") as? JsonArray
            val videos = data?.mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                val url = (so["file"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val label = (so["label"] as? JsonPrimitive)?.contentOrNull
                val type = (so["type"] as? JsonPrimitive)?.contentOrNull
                val quality = label?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                val format = if (url.contains(".m3u8", ignoreCase = true) || type?.contains("hls", ignoreCase = true) == true) {
                    VideoType.M3U8
                } else {
                    VideoType.CONTAINER
                }
                Video(quality, format, FileUrl(url, mapOf("Referer" to origin)))
            }
            if (videos.isNullOrEmpty()) {
                Logger.log("Latanime $host: no api sources, scanning embed page for direct links")
                val page = latGet(embed, referer)
                val urls = latanimeM3u8s(page) + latanimeMp4s(page)
                if (urls.isEmpty()) {
                    VideoContainer(emptyList())
                } else {
                    VideoContainer(urls.map {
                        Video(null, if (it.contains(".m3u8", ignoreCase = true)) VideoType.M3U8 else VideoType.CONTAINER, FileUrl(it, mapOf("Referer" to origin)))
                    })
                }
            } else {
                VideoContainer(videos)
            }
        } catch (e: Exception) {
            Logger.log("Latanime $host extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class SaveFilesExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer = withContext(Dispatchers.IO) {
        try {
            val referer = server.extraData?.get("referer")
            val embed = server.embed.url
            val id = embed.substringAfterLast('/').substringBefore('?')
            val origin = originOf(embed)
            val page = latGet("$origin/d/$id", referer)
            Logger.log("Latanime SaveFiles: pageSize=${page.length}")
            val direct = Regex("""href="([^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE)
                .findAll(page).map { it.groupValues[1] }.firstOrNull()
                ?: Regex("""https?://[^"'\s<>]+\.mp4[^"'\s<>]*""", RegexOption.IGNORE_CASE)
                    .find(page)?.value
            if (direct.isNullOrBlank()) {
                Logger.log("Latanime SaveFiles: no direct mp4 (likely Cloudflare challenge)")
                VideoContainer(emptyList())
            } else {
                val url = if (direct.startsWith("/")) "$origin$direct" else direct
                VideoContainer(
                    listOf(Video(null, VideoType.CONTAINER, FileUrl(url, mapOf("Referer" to origin))))
                )
            }
        } catch (e: Exception) {
            Logger.log("Latanime SaveFiles extract error: ${e.message}")
            VideoContainer(emptyList())
        }
    }
}

class EmptyExtractor(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        Logger.log("Latanime: no extractor for host '${server.extraData?.get("host")}' (${server.embed.url})")
        return VideoContainer(emptyList())
    }
}

private suspend fun latGet(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
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

private suspend fun latPostForm(
    url: String,
    form: Map<String, String>,
    referer: String?,
    xhr: Boolean = false
): String = withContext(Dispatchers.IO) {
    val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
    val request = Request.Builder().url(url)
        .header("User-Agent", NativeAnimeParser.USER_AGENT)
        .apply { referer?.let { header("Referer", it) } }
        .apply { if (xhr) header("X-Requested-With", "XMLHttpRequest") }
        .post(body).build()
    okHttpClient.newCall(request).execute().use { response ->
        val bodyStr = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        bodyStr
    }
}

private suspend fun latPostJson(url: String, json: String, referer: String?): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url)
        .header("User-Agent", NativeAnimeParser.USER_AGENT)
        .apply { referer?.let { header("Referer", it) } }
        .header("Content-Type", "application/json")
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()
    okHttpClient.newCall(request).execute().use { response ->
        val bodyStr = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        bodyStr
    }
}

private fun b64Decode(value: String): String? = try {
    String(Base64.decode(value.trim(), Base64.DEFAULT), Charsets.UTF_8)
} catch (e: Exception) {
    null
}

private fun originOf(url: String): String = runCatching {
    URI(url).let { "${it.scheme}://${it.authority}" }
}.getOrDefault(url.substringBefore('/', ""))

private fun latanimeM3u8s(html: String): List<String> = Regex(
    """(?:https?:)?(?:\\/|/)[^"'\s<>]+?\.m3u8[^"'\s<>]*""",
    RegexOption.IGNORE_CASE
).findAll(html)
    .map { latanimeDecodeEntities(it.value) }
    .map { if (it.startsWith("//")) "https:$it" else it }
    .distinct()
    .toList()

private fun latanimeMp4s(html: String): List<String> = Regex(
    """https?://[^"'\s<>]+?\.mp4[^"'\s<>]*""",
    RegexOption.IGNORE_CASE
).findAll(html)
    .map { latanimeDecodeEntities(it.value) }
    .distinct()
    .toList()

private fun latanimeDecodeEntities(value: String): String {
    var decoded = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
    decoded = Regex("&#(\\d+);").replace(decoded) { m ->
        m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
    }
    decoded = Regex("&#x([0-9a-fA-F]+);", RegexOption.IGNORE_CASE).replace(decoded) { m ->
        m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
    }
    return decoded.replace("\\/", "/")
}

private fun unpackMixdropPacker(html: String): String? {
    val match = Regex(
        """\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:[^'\\]|\\.)*)'\s*\.split\('\|'\)\s*,\s*0\s*,\s*\{\}\s*\)""",
        RegexOption.IGNORE_CASE
    ).find(html) ?: return null
    val payload = jsUnescape(match.groupValues[1])
    val count = match.groupValues[3].toIntOrNull() ?: return null
    val tokens = jsUnescape(match.groupValues[4]).split("|")
    var out = payload
    for (i in 0 until count) {
        val token = tokens.getOrNull(i) ?: continue
        if (token.isEmpty()) continue
        out = out.replace(Regex("\\b$i\\b")) { token }
    }
    return out
}

private fun jsUnescape(value: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            when (val n = value[i + 1]) {
                '\\' -> { sb.append('\\'); i += 2 }
                '\'' -> { sb.append('\''); i += 2 }
                '"' -> { sb.append('"'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                'x' -> {
                    if (i + 3 < value.length) {
                        val code = value.substring(i + 2, i + 4).toIntOrNull(16)
                        if (code != null) { sb.append(code.toChar()); i += 4 } else { sb.append('x'); i += 2 }
                    } else { sb.append('x'); i += 2 }
                }
                'u' -> {
                    if (i + 5 < value.length) {
                        val code = value.substring(i + 2, i + 6).toIntOrNull(16)
                        if (code != null) { sb.append(code.toChar()); i += 6 } else { sb.append('u'); i += 2 }
                    } else { sb.append('u'); i += 2 }
                }
                else -> { sb.append(n); i += 2 }
            }
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}
