package ani.sanin.others

import ani.sanin.Mapper
import ani.sanin.client
import ani.sanin.tryWithSuspend
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.net.URLEncoder

object AniSkip {

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getResult(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Long,
        useProxyForTimeStamps: Boolean
    ): List<Stamp>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"
        val candidates = buildList {
            add(url)
            if (useProxyForTimeStamps) {
                add("https://corsproxy.io/?${URLEncoder.encode(url, "utf-8").replace("+", "%20")}")
                add("https://api.allorigins.win/raw?url=${URLEncoder.encode(url, "utf-8").replace("+", "%20")}")
                add("https://r.jina.ai/$url")
            }
        }
        return tryWithSuspend {
            for (candidate in candidates) {
                val a = withTimeoutOrNull(12_000L) { client.get(candidate) } ?: continue
                if (a.code != 200) continue
                val text = a.text
                if (!text.trimStart().startsWith("{")) continue
                val res = try {
                    Mapper.json.decodeFromString<AniSkipResponse>(text)
                } catch (e: Exception) {
                    continue
                }
                if (res.found) return@tryWithSuspend res.results
            }
            null
        }
    }

    @Serializable
    data class AniSkipResponse(
        val found: Boolean,
        val results: List<Stamp>?,
        val message: String?,
        val statusCode: Int
    )

    @Serializable
    data class Stamp(
        val interval: AniSkipInterval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )


    fun String.getType(): String {
        return when (this) {
            "op" -> "Opening"
            "ed" -> "Ending"
            "recap" -> "Recap"
            "mixed-ed" -> "Mixed Ending"
            "mixed-op" -> "Mixed Opening"
            else -> this
        }
    }

    @Serializable
    data class AniSkipInterval(
        val startTime: Double,
        val endTime: Double
    )
}