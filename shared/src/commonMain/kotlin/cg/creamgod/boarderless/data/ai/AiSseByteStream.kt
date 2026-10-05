package cg.creamgod.boarderless.data.ai

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class AiSseFormatException : IllegalStateException("AI event stream format or size is invalid")

/** Single-use channel owned by the collector. No reconnect, retry or Last-Event-ID persistence.
 * HTTP status/content type/auth/timeout remain the request transport's responsibility.
 */
internal fun ByteReadChannel.aiSseEvents(): Flow<AiSseDataEvent> = flow {
    val parser = AiSseByteParser()
    val buffer = ByteArray(16384)
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = readAvailable(buffer, 0, buffer.size)
            if (size < 0) break
            for (index in 0 until size) {
                parser.accept(buffer[index])?.let { emit(it) }
            }
        }
        parser.finish() // EOF never dispatches an incomplete event.
    } finally {
        cancel(null) // Includes downstream terminal/take, cancellation, malformed input and consumer error.
    }
}

/** UTF-8 is decoded only after a complete line, preserving split multibyte scalars.
 * Strict invalid UTF-8 rejection is an APP safety policy, not browser replacement decoding.
 */
private class AiSseByteParser {
    private var line = ByteArray(4096)
    private var length = 0
    private var skipLf = false
    private var firstLine = true
    private var wireBytes = 0L
    private var eventCount = 0
    private var eventName = ""
    private val data = StringBuilder()
    private var dataBytes = 0
    private var hasData = false

    fun accept(byte: Byte): AiSseDataEvent? {
        if (++wireBytes > 8L * 1024 * 1024) throw AiSseFormatException()
        val value = byte.toInt() and 255
        if (skipLf) {
            skipLf = false
            if (value == 10) return null
        }
        if (value == 13 || value == 10) {
            skipLf = value == 13
            return processLine()
        }
        if (length == 262144) throw AiSseFormatException()
        if (length == line.size) line = line.copyOf((line.size * 2).coerceAtMost(262144))
        line[length++] = byte
        return null
    }

    fun finish() {
        if (length > 0) decodeLine() // Validate trailing UTF-8, but do not dispatch/complete it.
        length = 0
        data.clear()
        hasData = false
        eventName = ""
    }

    private fun decodeLine(): String {
        var text = try { line.decodeToString(0, length, throwOnInvalidSequence = true) }
        catch (_: Exception) { throw AiSseFormatException() }
        if (firstLine) {
            firstLine = false
            if (text.startsWith('\uFEFF')) text = text.substring(1)
        }
        return text
    }

    private fun processLine(): AiSseDataEvent? {
        val text = decodeLine()
        length = 0
        if (text.isEmpty()) {
            val event = if (!hasData) null else {
                if (++eventCount > 8192) throw AiSseFormatException()
                AiSseDataEvent(eventName.ifEmpty { "message" }, data.toString().dropLast(1))
            }
            data.clear(); dataBytes = 0; hasData = false; eventName = ""
            return event
        }
        if (text.startsWith(':')) return null
        val colon = text.indexOf(':')
        val field = if (colon < 0) text else text.substring(0, colon)
        var value = if (colon < 0) "" else text.substring(colon + 1)
        if (value.startsWith(' ')) value = value.substring(1)
        when (field) {
            "event" -> {
                if (value.length > 128) throw AiSseFormatException()
                eventName = value
            }
            "data" -> {
                dataBytes += value.encodeToByteArray().size + 1
                if (dataBytes > 262144) throw AiSseFormatException()
                data.append(value).append('\n')
                hasData = true
            }
            // id/retry/unknown fields do not initiate reconnection or publish private identifiers.
        }
        return null
    }
}
