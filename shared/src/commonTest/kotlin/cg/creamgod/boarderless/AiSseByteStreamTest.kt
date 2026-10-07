package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AiSseByteStreamTest {
    private val request = AiCoworkRequest("request", "prompt",
        AiContextSnapshot("workspace", 1, AiContextScope.PromptOnly, emptyList(), emptyList()))

    @Test fun everySingleByteSplitPreservesUtf8BomCrLfMultilineAndNamedEvents() = runTest {
        val bytes = "\uFEFF: comment\r\nevent: text\r\ndata: 素材🙂\r\ndata:  tail\r\n\r\ndata\n\n".encodeToByteArray()
        val channel = ByteChannel()
        val writer = launch {
            bytes.forEach { channel.writeFully(byteArrayOf(it)); channel.flush(); yield() }
            channel.close()
        }
        assertEquals(listOf(AiSseDataEvent("text", "素材🙂\n tail"), AiSseDataEvent("message", "")),
            channel.aiSseEvents().toList())
        writer.join()
        assertTrue(channel.isClosedForRead)
    }

    @Test fun bareCrCommentsCaseSensitiveFieldsAndEventResetAreHandled() = runTest {
        val channel = ByteReadChannel(": ignore\rid: private\rretry: 10\rDATA: not data\revent: ignored\r\r" +
            "data:a\rdata:b\r\revent: first\revent: second\rdata:c\r\revent:\rdata:d\r\r")
        assertEquals(listOf(AiSseDataEvent("message", "a\nb"), AiSseDataEvent("second", "c"),
            AiSseDataEvent("message", "d")), channel.aiSseEvents().toList())
    }

    @Test fun eofNeverDispatchesIncompleteEventsAndInvalidUtf8FailsClosed() = runTest {
        for (text in listOf("data: unfinished", "data: unfinished\n", "event: pending\ndata: unfinished\n")) {
            assertTrue(ByteReadChannel(text).aiSseEvents().toList().isEmpty())
        }
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28, 10), byteArrayOf(0xf0.toByte(), 0x9f.toByte()))) {
            val channel = ByteReadChannel(bytes)
            val failure = assertFailsWith<AiSseFormatException> { channel.aiSseEvents().toList() }
            assertEquals("AI event stream format or size is invalid", failure.message)
            assertTrue(channel.isClosedForRead)
        }
    }

    @Test fun linePayloadNameAndEventCountLimitsCloseOwnedChannel() = runTest {
        val inputs = listOf("x".repeat(262145), "data:" + "x".repeat(140000) + "\ndata:" + "x".repeat(140000) + "\n\n",
            "event:" + "x".repeat(129) + "\n\n", "data:x\n\n".repeat(8193), ":" + "x".repeat(262144))
        inputs.forEach { input ->
            val channel = ByteReadChannel(input)
            assertFailsWith<AiSseFormatException> { channel.aiSseEvents().collect {} }
            assertTrue(channel.isClosedForRead)
        }
    }

    @Test fun providerTerminalStopsParsingLaterBytesInTheSameReadAndClosesChannel() = runTest {
        val stop = """{"id":"completion","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":"stop"}]}"""
        val channel = ByteReadChannel(("data:$stop\n\ndata:[DONE]\n\n").encodeToByteArray() + byteArrayOf(0xff.toByte()))
        val provider = OpenAiChatEventAdapter("test") { channel.aiSseEvents().map { it.data } }
        assertEquals(listOf(AiCoworkEvent.TextDelta("ok"), AiCoworkEvent.Completed), provider.stream(request).toList())
        assertTrue(channel.isClosedForRead)
        val incomplete = OpenAiChatEventAdapter("test") { ByteReadChannel("data:[DONE]").aiSseEvents().map { it.data } }
        assertEquals(listOf(AiCoworkEvent.Failed("AI stream ended before completion", true)), incomplete.stream(request).toList())
    }

    @Test fun cancellationAndConsumerErrorReleaseActualWaitingChannel() = runTest {
        val channel = ByteChannel()
        val observed = CompletableDeferred<Unit>()
        val collector = launch { channel.aiSseEvents().collect { observed.complete(Unit) } }
        channel.writeFully("data:partial\n\n".encodeToByteArray()); channel.flush()
        observed.await(); collector.cancelAndJoin()
        assertTrue(channel.isClosedForRead)
        assertTrue(channel.isClosedForWrite)
        val other = ByteReadChannel("data:first\n\ndata:second\n\n")
        val failure = IllegalStateException("consumer")
        assertSame(failure, assertFailsWith<IllegalStateException> { other.aiSseEvents().collect { throw failure } })
        assertTrue(other.isClosedForRead)
    }

    @Test fun ignoredCommentsStillConsumeTotalWireBudget() = runTest {
        // 8192-byte comment lines; no event payload or event-count budget is consumed.
        val line = (":" + "x".repeat(8190) + "\n").encodeToByteArray()
        for (lines in listOf(1024, 1025)) {
            val channel = ByteChannel()
            val writer = launch {
                try {
                    repeat(lines) { channel.writeFully(line); channel.flush() }
                } finally { channel.close() }
            }
            if (lines == 1024) {
                assertTrue(channel.aiSseEvents().toList().isEmpty())
                writer.join()
            } else {
                assertFailsWith<AiSseFormatException> { channel.aiSseEvents().collect {} }
                writer.cancelAndJoin()
            }
            assertTrue(channel.isClosedForRead)
            assertTrue(channel.isClosedForWrite)
        }
    }
}
