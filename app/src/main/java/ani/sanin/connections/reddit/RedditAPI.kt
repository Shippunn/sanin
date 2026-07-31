package ani.sanin.connections.reddit

import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object RedditAPI {
    private const val BASE_URL = "https://www.reddit.com"
    private const val USER_AGENT = "android:ani.sanin:v1.0 (by /u/sanin)"
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class RedditThread(
        val id: String,
        val title: String,
        val episode: Int?,
        val createdUtc: Long,
        val numComments: Int,
        val score: Int
    ) : java.io.Serializable

    data class RedditComment(
        val id: String,
        val author: String,
        val body: String,
        val createdUtc: Long,
        val score: Int
    ) : java.io.Serializable

    private val episodeRegex = Regex("""(?i)(?:episode|ep\.?)\s*(\d{1,4})""")

    fun parseEpisode(title: String): Int? {
        return episodeRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Search r/anime for episode discussion threads. Returns newest first. */
    suspend fun searchThreads(query: String): List<RedditThread> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/r/anime/search.json?q=$encoded&restrict_sr=on&sort=new&t=all&limit=100"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (response.code != 200) return@withContext emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val children = root["data"]?.jsonObject?.get("children")?.jsonArray ?: return@withContext emptyList()
            children.mapNotNull { child ->
                val d = child.jsonObject["data"]?.jsonObject ?: return@mapNotNull null
                val title = d["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                RedditThread(
                    id = d["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    title = title,
                    episode = parseEpisode(title),
                    createdUtc = (d["created_utc"]?.jsonPrimitive?.contentOrNull ?: "0").toLongOrNull() ?: 0L,
                    numComments = d["num_comments"]?.jsonPrimitive?.intOrNull ?: 0,
                    score = d["score"]?.jsonPrimitive?.intOrNull ?: 0
                )
            }
        } catch (e: Exception) {
            Logger.log("Reddit search error: ${e.message}")
            emptyList()
        }
    }

    /** Fetch a thread's comments, flattened and sorted by score (top first). */
    suspend fun getThreadComments(threadId: String, limit: Int = 40): List<RedditComment> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/comments/$threadId.json?limit=100&sort=top"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                if (response.code != 200) return@withContext emptyList()
                val root = json.parseToJsonElement(body)
                if (root !is JsonArray) return@withContext emptyList()
                val listing = root.getOrNull(1)?.jsonObject?.get("data")?.jsonObject ?: return@withContext emptyList()
                val flattened = mutableListOf<RedditComment>()
                listing["children"]?.jsonArray?.forEach { child ->
                    child.jsonObject["data"]?.jsonObject?.let { flatten(it, flattened) }
                }
                flattened.sortedByDescending { it.score }.take(limit)
            } catch (e: Exception) {
                Logger.log("Reddit comments error: ${e.message}")
                emptyList()
            }
        }

    private fun flatten(data: JsonObject, out: MutableList<RedditComment>) {
        val author = data["author"]?.jsonPrimitive?.contentOrNull ?: "[deleted]"
        val body = data["body"]?.jsonPrimitive?.contentOrNull
        if (author != "[deleted]" && body != null && body.isNotBlank() && body != "[removed]") {
            out.add(
                RedditComment(
                    id = data["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    author = author,
                    body = body,
                    createdUtc = (data["created_utc"]?.jsonPrimitive?.contentOrNull ?: "0").toLongOrNull() ?: 0L,
                    score = data["score"]?.jsonPrimitive?.intOrNull ?: 0
                )
            )
        }
        val replies = data["replies"]
        if (replies is JsonObject) {
            replies["data"]?.jsonObject?.get("children")?.jsonArray?.forEach { child ->
                child.jsonObject["data"]?.jsonObject?.let { flatten(it, out) }
            }
        }
    }
}
