package cg.creamgod.boarderless.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** Explicit platform transport; serializers, request timeout and consent remain with callers. */
internal expect fun platformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient
