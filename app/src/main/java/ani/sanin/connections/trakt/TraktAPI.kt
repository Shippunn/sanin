package ani.sanin.connections.trakt

import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

object TraktAPI {
    private const val BASE_URL = "https://api.trakt.tv"
    private const val HARDCODED_CLIENT_ID = "w5-QcQAhtlujinZFPFddAnY5B_huGoWModzSoMM6VjM"
    private val json = Json { ignoreUnknownKeys = true }

    private val clientId: String
        get() = PrefManager.getNullableVal(PrefName.TraktClientId, null)
            ?.takeIf { it.isNotBlank() }
            ?: HARDCODED_CLIENT_ID

    private val client: OkHttpClient by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun headers(): Map<String, String> = mapOf(
        "trakt-api-key" to clientId,
        "trakt-api-version" to "2",
        "Content-Type" to "application/json"
    )

    private fun isEnabled(): Boolean =
        PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1

    suspend fun searchByImdb(imdbId: String): TraktSearchResult? = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext null
        try {
            val request = Request.Builder()
                .url("$BASE_URL/search/imdb/$imdbId?type=show,movie")
                .apply { headers().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val results = json.decodeFromString<List<TraktSearchResult>>(body)
            results.firstOrNull()
        } catch (e: Exception) {
            Logger.log("Trakt search error: ${e.message}")
            null
        }
    }

    suspend fun getComments(
        type: String,
        traktId: Int,
        page: Int = 1,
        sort: String = "newest"
    ): List<TraktComment> = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url("$BASE_URL/$type/$traktId/comments?sort=$sort&page=$page&limit=25")
                .apply { headers().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            json.decodeFromString<List<TraktComment>>(body)
        } catch (e: Exception) {
            Logger.log("Trakt comments error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getReplies(commentId: Int): List<TraktComment> = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/$commentId/replies?limit=25")
                .apply { headers().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            json.decodeFromString<List<TraktComment>>(body)
        } catch (e: Exception) {
            Logger.log("Trakt replies error: ${e.message}")
            emptyList()
        }
    }

    private fun authHeaders(): Map<String, String> {
        val token = TraktAuth.accessToken ?: return headers()
        return headers() + ("Authorization" to "Bearer $token")
    }

    suspend fun likeComment(commentId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!TraktAuth.isLoggedIn()) return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/$commentId/like")
                .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .post("".toRequestBody(null))
                .build()
            val response = client.newCall(request).execute()
            response.code == 204
        } catch (e: Exception) {
            Logger.log("Trakt like error: ${e.message}")
            false
        }
    }

    suspend fun unlikeComment(commentId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!TraktAuth.isLoggedIn()) return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/$commentId/like")
                .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .delete()
                .build()
            val response = client.newCall(request).execute()
            response.code == 204
        } catch (e: Exception) {
            Logger.log("Trakt unlike error: ${e.message}")
            false
        }
    }

    suspend fun replyToComment(commentId: Int, text: String): TraktComment? = withContext(Dispatchers.IO) {
        if (!TraktAuth.isLoggedIn()) return@withContext null
        try {
            val body = json.encodeToString(PostCommentBody(comment = text))
            val request = Request.Builder()
                .url("$BASE_URL/comments/$commentId/replies")
                .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            if (response.code !in 200..299) return@withContext null
            json.decodeFromString<TraktComment>(responseBody)
        } catch (e: Exception) {
            Logger.log("Trakt reply error: ${e.message}")
            null
        }
    }

    suspend fun postComment(type: String, id: Int, text: String, spoiler: Boolean = false): TraktComment? = withContext(Dispatchers.IO) {
        if (!TraktAuth.isLoggedIn()) return@withContext null
        try {
            val body = json.encodeToString(PostCommentBody(comment = text, spoiler = spoiler))
            val request = Request.Builder()
                .url("$BASE_URL/$type/$id/comments")
                .apply { authHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            if (response.code !in 200..299) return@withContext null
            json.decodeFromString<TraktComment>(responseBody)
        } catch (e: Exception) {
            Logger.log("Trakt post comment error: ${e.message}")
            null
        }
    }
}

@Serializable
data class PostCommentBody(
    val comment: String,
    val spoiler: Boolean = false
)

@Serializable
data class TraktSearchResult(
    @SerialName("type") val type: String,
    @SerialName("score") val score: Double? = null,
    @SerialName("show") val show: TraktShow? = null,
    @SerialName("movie") val movie: TraktMovie? = null
) {
    val traktId: Int?
        get() = show?.ids?.trakt ?: movie?.ids?.trakt
    val mediaType: String?
        get() = if (show != null) "shows" else if (movie != null) "movies" else null
    val title: String?
        get() = show?.title ?: movie?.title
}

@Serializable
data class TraktShow(
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("ids") val ids: TraktIds
)

@Serializable
data class TraktMovie(
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("ids") val ids: TraktIds
)

@Serializable
data class TraktIds(
    @SerialName("trakt") val trakt: Int,
    @SerialName("slug") val slug: String? = null,
    @SerialName("imdb") val imdb: String? = null,
    @SerialName("tmdb") val tmdb: Int? = null
)

@Serializable
data class TraktComment(
    @SerialName("id") val id: Int,
    @SerialName("parent_id") val parentId: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("comment") val comment: String,
    @SerialName("spoiler") val spoiler: Boolean = false,
    @SerialName("review") val review: Boolean = false,
    @SerialName("replies") val replies: Int = 0,
    @SerialName("likes") val likes: Int = 0,
    @SerialName("user_stats") val userStats: TraktUserStats? = null,
    @SerialName("user") val user: TraktUser,
    @SerialName("user_liked") val userLiked: Boolean = false
)

@Serializable
data class TraktUserStats(
    @SerialName("rating") val rating: Int? = null,
    @SerialName("play_count") val playCount: Int? = null,
    @SerialName("completed_count") val completedCount: Int? = null
)

@Serializable
data class TraktUser(
    @SerialName("username") val username: String,
    @SerialName("name") val name: String? = null,
    @SerialName("vip") val vip: Boolean = false,
    @SerialName("vip_ep") val vipEp: Boolean = false,
    @SerialName("images") val images: TraktUserImages? = null
)

@Serializable
data class TraktUserImages(
    @SerialName("avatar") val avatar: TraktAvatar? = null
)

@Serializable
data class TraktAvatar(
    @SerialName("full") val full: String? = null
)
