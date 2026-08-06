package ani.sanin.connections.auth

import ani.sanin.client
import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val expiresIn: Int,
    val qrUrl: String
)

@Serializable
data class SessionStatusResponse(
    val status: String
)

@Serializable
data class ConsumeSessionResponse(
    val accessToken: String
)

object QrLoginApi {
    private const val BASE_URL = "https://sanin-auth.shemaus58.workers.dev"
    private const val TIMEOUT_MS = 10_000L // 10 seconds
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 1_000L

    suspend fun createSession(): CreateSessionResponse = withContext(Dispatchers.IO) {
        executeWithRetry {
            val response = client.post("$BASE_URL/api/session/create")
            response.parsed()
        }
    }

    suspend fun getSessionStatus(sessionId: String): SessionStatusResponse = withContext(Dispatchers.IO) {
        // Validate UUID format before making request
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
        if (!uuidRegex.matches(sessionId)) {
            throw IllegalArgumentException("Invalid session ID format")
        }

        executeWithRetry {
            val response = client.get("$BASE_URL/api/session/$sessionId")
            response.parsed()
        }
    }

    suspend fun consumeSession(sessionId: String): ConsumeSessionResponse = withContext(Dispatchers.IO) {
        executeWithRetry {
            val response = client.post(
                "$BASE_URL/api/session/consume",
                data = mapOf("sessionId" to sessionId)
            )
            response.parsed()
        }
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                return withTimeoutOrNull(TIMEOUT_MS) {
                    block()
                } ?: throw java.net.SocketTimeoutException("Request timed out")
            } catch (e: Exception) {
                lastException = e
                Logger.log("QrLoginApi attempt ${attempt + 1} failed: ${e.message}")

                // Don't retry on client errors (4xx) - check if it's an HTTP error
                val errorMessage = e.message ?: ""
                if (errorMessage.contains("400") || errorMessage.contains("401") ||
                    errorMessage.contains("403") || errorMessage.contains("404")) {
                    throw e
                }

                // Don't retry on last attempt
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(10_000L) // Exponential backoff, max 10s
                }
            }
        }

        throw lastException ?: Exception("Unknown error")
    }
}
