package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.data.platformHttpClient
import com.sun.net.httpserver.HttpServer
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PlatformHttpClientTest {
    @Test fun actualOkHttpDoesNotRetry401503RetryAfterZeroOrFollowRedirects() =
        runBlocking {
            for ((code, retryAfter) in listOf(401 to "0", 503 to "0", 503 to "00", 307 to "0")) {
                val requests = AtomicInteger()
                val redirects = AtomicInteger()
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                server.createContext("/target") { exchange ->
                    redirects.incrementAndGet()
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                }
                server.createContext("/") { exchange ->
                    requests.incrementAndGet()
                    exchange.requestBody.use { it.readBytes() }
                    exchange.responseHeaders.add("Retry-After", retryAfter)
                    exchange.responseHeaders.add("Location", "/target")
                    val bytes = "fixture".encodeToByteArray()
                    exchange.sendResponseHeaders(code, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                server.start()
                val client =
                    platformHttpClient {
                        followRedirects = false
                        install(HttpTimeout) { requestTimeoutMillis = 5000 }
                    }
                try {
                    assertContains(client.engine::class.java.name, "okhttp", ignoreCase = true)
                    val response = client.post("http://127.0.0.1:${server.address.port}/") { setBody("fixture") }
                    assertEquals(code, response.status.value)
                    assertEquals("fixture", response.bodyAsText())
                    assertEquals(1, requests.get())
                    assertEquals(0, redirects.get())
                } finally {
                    client.close()
                    server.stop(0)
                }
            }
        }

    @Test fun realApprovedAiProbeDecodesUtf8AcrossPhysicalSocketFragmentsUsingExplicitEngine() =
        runBlocking {
            val requests = AtomicInteger()
            var approvals = 0
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests.incrementAndGet()
                exchange.requestBody.use { it.readBytes() }
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                val body =
                    "data: {\"id\":\"fixture\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"你好🙂\"},\"finish_reason\":null}]}\n\n" +
                        "data: {\"id\":\"fixture\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                exchange.responseBody.use { output ->
                    body.encodeToByteArray().forEach {
                        output.write(it.toInt() and 255)
                        output.flush()
                    }
                }
            }
            server.start()
            try {
                val result =
                    runAiUnderstandingProbe(
                        AiServerProfile(
                            "http://127.0.0.1:${server.address.port}/",
                            AiServerLocation.Local,
                            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "fixture", 100),
                            allowInsecureLocalHttp = true,
                        ),
                        Headers.Empty,
                        "fixture",
                        {
                            approvals++
                            true
                        },
                        {},
                    )
                assertEquals("你好🙂", result)
                assertEquals(1, approvals)
                assertEquals(1, requests.get())
            } finally {
                server.stop(0)
            }
        }
}
