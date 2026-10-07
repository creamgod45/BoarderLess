package cg.creamgod.boarderless.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

enum class GiphyIssue { InvalidQuery, MissingKey, Unauthorized, RateLimited, Unavailable, InvalidResponse }

class GiphyException(
    val issue: GiphyIssue,
) : IllegalStateException(issue.name)

@Serializable data class GiphyRendition(
    val url: String = "",
    val width: String = "",
    val height: String = "",
    val mp4: String = "",
    val webp: String = "",
    val size: String = "",
)

@Serializable data class GiphyItem(
    val id: String,
    val title: String = "",
    val alt_text: String = "",
    val url: String = "",
    val images: Map<String, GiphyRendition> = emptyMap(),
)

@Serializable data class GiphyPagination(
    val offset: Int = 0,
    val count: Int = 0,
    val total_count: Int? = null,
)

@Serializable private data class GiphyMeta(
    val status: Int,
)

@Serializable private data class GiphyResponse(
    val data: List<GiphyItem>,
    val pagination: GiphyPagination,
    val meta: GiphyMeta,
)

@Serializable private data class GiphyLookupResponse(
    val data: List<GiphyItem>,
    val meta: GiphyMeta,
)

data class GiphyPage(
    val items: List<GiphyItem>,
    val pagination: GiphyPagination,
    val nextOffset: Int?,
)

/** Advisory, process-local rolling-hour budget. Shared keys on other devices still need server enforcement. */
class GiphyRequestBudget(
    private val nowMillis: () -> Long,
    private val limit: Int = 100,
) {
    private val mutex = Mutex()
    private val calls = mutableMapOf<String, List<Long>>()
    private val blockedUntil = mutableMapOf<String, Long>()

    init {
        require(limit > 0)
    }

    suspend fun reserve(key: String) =
        mutex.withLock {
            val now = nowMillis()
            val recent = calls[key].orEmpty().filter { now - it < 3_600_000 }
            if (now < (blockedUntil[key] ?: 0) || recent.size >= limit) throw GiphyException(GiphyIssue.RateLimited)
            calls[key] = recent + now // Failed requests consume budget as well. No automatic retries.
        }

    suspend fun blocked(
        key: String,
        retrySeconds: Long?,
    ) = mutex.withLock {
        blockedUntil[key] = nowMillis() + (retrySeconds?.coerceIn(1, 3600) ?: 3600) * 1000
    }
}

private val started = TimeSource.Monotonic.markNow()
private val processBudget = GiphyRequestBudget({ started.elapsedNow().inWholeMilliseconds })

/** Client-to-GIPHY only. No backend proxy, result/media cache, or asset rehosting. */
class GiphyClient(
    private val apiKey: String,
    private val client: HttpClient =
        HttpClient {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        },
    private val budget: GiphyRequestBudget = processBudget,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Resolve up to 100 visible references in one request. Missing IDs remain missing, not deleted nodes. */
    suspend fun resolve(ids: List<String>): List<GiphyItem> {
        if (apiKey.isBlank()) throw GiphyException(GiphyIssue.MissingKey)
        if (ids.size !in 1..100 || ids.any { id -> id.isBlank() || id.any { it == ',' || it.isWhitespace() || it.code < 32 } }) {
            throw GiphyException(GiphyIssue.InvalidQuery)
        }
        currentCoroutineContext().ensureActive()
        budget.reserve(apiKey)
        try {
            val response =
                client.get("https://api.giphy.com/v1/gifs") {
                    parameter("api_key", apiKey)
                    parameter("ids", ids.joinToString(","))
                    parameter("rating", "g")
                }
            currentCoroutineContext().ensureActive()
            if (response.status.value == 429) {
                budget.blocked(apiKey, response.headers["Retry-After"]?.toLongOrNull())
                throw GiphyException(GiphyIssue.RateLimited)
            }
            when (response.status.value) {
                401, 403 -> throw GiphyException(GiphyIssue.Unauthorized)
                200 -> Unit
                else -> throw GiphyException(GiphyIssue.Unavailable)
            }
            val payload =
                try {
                    json.decodeFromString<GiphyLookupResponse>(response.bodyAsText())
                } catch (
                    _: Exception,
                ) {
                    currentCoroutineContext().ensureActive()
                    throw GiphyException(GiphyIssue.InvalidResponse)
                }
            currentCoroutineContext().ensureActive()
            if (payload.meta.status != 200 || payload.data.size > ids.size ||
                payload.data.any { it.id !in ids } || payload.data
                    .map { it.id }
                    .toSet()
                    .size != payload.data.size
            ) {
                throw GiphyException(GiphyIssue.InvalidResponse)
            }
            return payload.data // Provider order; do not fill omissions with stale cached metadata.
        } catch (
            cancelled: kotlinx.coroutines.CancellationException,
        ) {
            throw cancelled
        } catch (
            failed: GiphyException,
        ) {
            throw failed
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            throw GiphyException(GiphyIssue.Unavailable)
        }
    }

    suspend fun browse(
        query: String?,
        offset: Int = 0,
        language: String = "en",
    ): GiphyPage {
        if (apiKey.isBlank()) throw GiphyException(GiphyIssue.MissingKey)
        val maximum = if (query == null) 499 else 4999
        if (query != null && (query.isBlank() || query.count { !it.isLowSurrogate() } > 50) || offset !in 0..maximum) {
            throw GiphyException(GiphyIssue.InvalidQuery)
        }
        currentCoroutineContext().ensureActive()
        budget.reserve(apiKey)
        try {
            val response =
                client.get("https://api.giphy.com/v1/gifs/${if (query == null) "trending" else "search"}") {
                    parameter("api_key", apiKey)
                    if (query != null) {
                        parameter("q", query)
                        parameter("lang", language)
                    }
                    parameter("limit", 20)
                    parameter("offset", offset)
                    parameter("rating", "g")
                }
            currentCoroutineContext().ensureActive()
            if (response.status.value == 429) {
                budget.blocked(apiKey, response.headers["Retry-After"]?.toLongOrNull())
                throw GiphyException(GiphyIssue.RateLimited)
            }
            when (response.status.value) {
                401, 403 -> throw GiphyException(GiphyIssue.Unauthorized)
                200 -> Unit
                else -> throw GiphyException(GiphyIssue.Unavailable)
            }
            val payload =
                try {
                    json.decodeFromString<GiphyResponse>(response.bodyAsText())
                } catch (
                    _: Exception,
                ) {
                    currentCoroutineContext().ensureActive()
                    throw GiphyException(GiphyIssue.InvalidResponse)
                }
            currentCoroutineContext().ensureActive()
            if (payload.meta.status != 200 || payload.pagination.offset != offset || payload.pagination.count != payload.data.size ||
                payload.data.size > 20
            ) {
                throw GiphyException(GiphyIssue.InvalidResponse)
            }
            val next = offset + payload.pagination.count
            return GiphyPage(
                payload.data,
                payload.pagination,
                next.takeIf {
                    payload.pagination.count > 0 && it <= maximum &&
                        (payload.pagination.total_count == null || it < payload.pagination.total_count)
                },
            )
        } catch (
            error: kotlinx.coroutines.CancellationException,
        ) {
            throw error
        } catch (
            error: GiphyException,
        ) {
            throw error
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            throw GiphyException(GiphyIssue.Unavailable)
        }
    }

    fun close() = client.close()
}

/** Reject non-provider URLs, but never strip or rewrite returned query parameters. */
fun isGiphyMediaUrl(url: String): Boolean =
    runCatching {
        val parsed = io.ktor.http.Url(url)
        parsed.protocol.name == "https" && parsed.host.let { it == "media.giphy.com" || it.matches(Regex("media[0-9]+\\.giphy\\.com")) } &&
            parsed.user == null && parsed.password == null && parsed.port == 443
    }.getOrDefault(false)
