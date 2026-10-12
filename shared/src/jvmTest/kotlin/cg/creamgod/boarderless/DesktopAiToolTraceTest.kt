package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.DesktopAiToolTrace
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*

class DesktopAiToolTraceTest {
    @Test fun writesOwnerOnlyJsonLinesAndRotatesWithoutFollowingSymlinks() {
        val root = Files.createTempDirectory("trace")
        val dir = root.resolve("logs")
        val trace = DesktopAiToolTrace(dir, maxBytes = 2048, submit = { it() })
        trace.record("tool", "req", 2, mapOf("tool" to "create_nodes", "arguments" to "{\"nodes\":[]}", "result" to "{\"placed\":[]}"))
        val file = dir.resolve("ai-trace.jsonl")
        val text = Files.readString(file)
        assertTrue(text.contains("\"tool\":\"create_nodes\"") && text.endsWith("\n"))
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
        repeat(40) { trace.record("tool", "req", it, mapOf("arguments" to "x".repeat(200))) }
        assertTrue(Files.exists(dir.resolve("ai-trace.jsonl.1")))
        assertTrue(Files.size(file) <= 2048)

        val target = Files.createTempFile("victim", ".txt")
        Files.delete(file)
        Files.createSymbolicLink(file, target)
        trace.record("tool", "req", 1, mapOf("arguments" to "secret"))
        assertEquals("", Files.readString(target), "a symlinked trace file must never be written through")
    }

    @Test fun oversizedValuesAreTruncated() {
        val dir = Files.createTempDirectory("trace2")
        val trace = DesktopAiToolTrace(dir, submit = { it() })
        trace.record("tool", "req", 1, mapOf("result" to "y".repeat(100_000)))
        assertTrue(Files.size(dir.resolve("ai-trace.jsonl")) < 20_000)
    }
}
