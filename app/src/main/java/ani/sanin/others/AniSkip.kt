package ani.sanin.others

import ani.sanin.Mapper
import ani.sanin.client
import ani.sanin.tryWithSuspend
import ani.sanin.util.Logger
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
        // ExoPlayer reports C.TIME_UNSET (Long.MIN_VALUE) when the stream duration
        // isn't known yet; the AniSkip API rejects negative lengths with a 400, so
        // fall back to a standard 24-minute episode in that case.
        val safeEpisodeLength = if (episodeLength > 0) episodeLength else 1440L
        Logger.log(
            "AniSkip: getResult start malId=$malId episode=$episodeNumber " +
                "episodeLengthIn=$episodeLength safeEpisodeLength=$safeEpisodeLength " +
                "useProxy=$useProxyForTimeStamps"
        )
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$safeEpisodeLength"
        val proxies = listOf(
            "https://corsproxy.io/?${URLEncoder.encode(url, "utf-8").replace("+", "%20")}",
            "https://api.allorigins.win/raw?url=${URLEncoder.encode(url, "utf-8").replace("+", "%20")}",
            "https://r.jina.ai/$url"
        )
        // When the proxy option is on, try the CORS relays first: some devices
        // (e.g. Fire TV) can't reach api.aniskip.com directly and the direct call
        // would otherwise burn a full timeout before the relays get a chance.
        // The relays are also kept as an automatic fallback when the option is off,
        // so the skip button works on those devices out of the box.
        val candidates = if (useProxyForTimeStamps) proxies + url else listOf(url) + proxies
        return tryWithSuspend {
            for (candidate in candidates) {
                val started = java.lang.System.currentTimeMillis()
                val a = withTimeoutOrNull(8_000L) { client.get(candidate) }
                val elapsed = java.lang.System.currentTimeMillis() - started
                if (a == null) {
                    Logger.log("AniSkip: TIMEOUT after ${elapsed}ms for ${candidate.take(100)}")
                    continue
                }
                Logger.log(
                    "AniSkip: response code=${a.code} in ${elapsed}ms for " +
                        "${candidate.take(100)} headers=${a.headers["content-type"]}"
                )
                if (a.code != 200) {
                    continue
                }
                val text = a.text
                Logger.log("AniSkip: body head=${text.trim().take(160)}")
                if (!text.trimStart().startsWith("{")) {
                    Logger.log("AniSkip: non-JSON response from ${candidate.take(100)}")
                    continue
                }
                val res = try {
                    Mapper.json.decodeFromString<AniSkipResponse>(text)
                } catch (e: Exception) {
                    Logger.log("AniSkip: parse error from ${candidate.take(100)}: ${e.message}")
                    continue
                }
                if (res.found) {
                    Logger.log(
                        "AniSkip: FOUND ${res.results?.size} stamps via ${candidate.take(100)} " +
                            "statusCode=${res.statusCode}"
                    )
                    return@tryWithSuspend res.results
                }
                Logger.log(
                    "AniSkip: found=false via ${candidate.take(100)} message=${res.message} " +
                        "statusCode=${res.statusCode}"
                )
            }
            Logger.log("AniSkip: ALL candidates exhausted -> null for malId=$malId episode=$episodeNumber")
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
