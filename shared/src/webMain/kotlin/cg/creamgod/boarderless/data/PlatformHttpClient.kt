package cg.creamgod.boarderless.data
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal actual fun platformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient { configure(this) }
