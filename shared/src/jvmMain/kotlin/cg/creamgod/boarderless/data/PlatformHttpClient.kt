package cg.creamgod.boarderless.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun platformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        configure(this)
        engine {
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
                // OkHttp may follow a 503 with Retry-After: 0 even when connection retries are off.
                // Preserve its status/body while suppressing that implicit second HTTP request.
                addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 503 &&
                        response.header("Retry-After")?.trim()?.toLongOrNull() == 0L
                    ) {
                        response.newBuilder().removeHeader("Retry-After").build()
                    } else {
                        response
                    }
                }
            }
        }
    }
