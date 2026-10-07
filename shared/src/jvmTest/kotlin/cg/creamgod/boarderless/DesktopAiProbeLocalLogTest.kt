package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.test.*

class DesktopAiProbeLocalLogTest {
    @Test fun existingReadableAppParentAllowsPrivateLogsWithoutChangingParentPermissions() =
        temporary { root ->
            if (Files.getFileStore(root.parent.parent).supportsFileAttributeView("posix")) {
                val parentPermissions = PosixFilePermissions.fromString("rwxr-xr-x")
                Files.createDirectory(root.parent, PosixFilePermissions.asFileAttribute(parentPermissions))
                val writer = DesktopAiProbeLogWriter(root)
                write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Preparing))
                assertEquals(parentPermissions, Files.getPosixFilePermissions(root.parent))
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(root.resolve("ai-probe.log")))
                Files.setPosixFilePermissions(root.parent, PosixFilePermissions.fromString("rwxrwxr-x"))
                val before = Files.readString(root.resolve("ai-probe.log"))
                assertFails { write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Failed)) }
                assertEquals(before, Files.readString(root.resolve("ai-probe.log")))
            }
        }

    private inline fun <T> temporary(block: (Path) -> T): T {
        val home = Files.createTempDirectory("boarderless-ai-log-test")
        try {
            return block(home.resolve(".boarderless").resolve("logs"))
        } finally {
            Files.walk(home).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun write(
        writer: DesktopAiProbeLogWriter,
        id: UUID,
        event: AiProbeDiagnostic,
    ) = writer.write(id, AiRequestDialect.OpenAiChat, AiProbeAuthentication.Bearer, event)

    @Test fun realProbe401IsCollectedWithCorrelationWithoutCredentialsEndpointModelPromptOrBody() =
        runTest {
            temporary { root ->
                val writer = DesktopAiProbeLogWriter(root)
                val id = UUID.randomUUID()
                val client =
                    HttpClient(
                        MockEngine {
                            respond(
                                "secret-provider-body",
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, "text/plain"),
                            )
                        },
                    ) {
                        followRedirects = false
                        install(HttpTimeout)
                    }
                assertFails {
                    runAiUnderstandingProbe(
                        AiServerProfile(
                            "https://secret-endpoint.invalid/chat",
                            AiServerLocation.Remote,
                            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "secret-model", 4096),
                        ),
                        headersOf(HttpHeaders.Authorization, "Bearer secret-key"),
                        "secret-prompt",
                        { true },
                        {},
                        client,
                        { write(writer, id, it) },
                    )
                }
                val content = Files.readString(root.resolve("ai-probe.log"))
                assertContains(content, "Failed code=HttpRejected HTTP=401 contentType=Other")
                assertContains(content, "dialect=OpenAiChat auth=Bearer")
                assertTrue(content.lines().filter { it.isNotEmpty() }.all { "attempt=$id" in it })
                for (secret in listOf("secret-provider-body", "secret-endpoint", "secret-model", "secret-key", "secret-prompt")) {
                    assertFalse(secret in content)
                }
                if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
                    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(root.resolve("ai-probe.log")))
                    assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
                }
            }
        }

    @Test fun runtimeSinkBoundsRecordsAndDoesNotLetFileOrSchedulingFailureAffectProbe() =
        runTest {
            temporary { root ->
                val sink = desktopAiProbeLocalLog(root, AiRequestDialect.AnthropicMessages, AiProbeAuthentication.ApiKey) { it() }
                repeat(100) { sink.record(AiProbeDiagnostic(AiProbeStage.Preparing)) }
                val lines = Files.readAllLines(root.resolve("ai-probe.log"))
                assertEquals(24, lines.size)
                assertTrue(lines.all { "dialect=AnthropicMessages auth=ApiKey" in it })
                val badRoot = root.resolve("ai-probe.log").resolve("logs")
                val failingSink = desktopAiProbeLocalLog(badRoot, AiRequestDialect.OpenAiChat, AiProbeAuthentication.None) { it() }
                val events = mutableListOf<AiProbeDiagnostic>()
                var calls = 0
                val client =
                    HttpClient(
                        MockEngine {
                            calls++
                            respond("secret-provider-body", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "text/plain"))
                        },
                    ) {
                        followRedirects = false
                        install(HttpTimeout)
                    }
                assertFails {
                    runAiUnderstandingProbe(
                        AiServerProfile(
                            "https://secret-endpoint.invalid/chat",
                            AiServerLocation.Remote,
                            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "secret-model", 4096),
                        ),
                        Headers.Empty,
                        "secret-prompt",
                        { true },
                        {},
                        client,
                        {
                            events.add(it)
                            failingSink.record(it)
                        },
                    )
                }
                assertFalse(Files.exists(badRoot))
                assertEquals(1, calls)
                assertEquals(AiProbeFailure.HttpRejected, events.last().failure)
                assertEquals(401, events.last().httpStatus)
                val rejected =
                    desktopAiProbeLocalLog(root, AiRequestDialect.OpenAiChat, AiProbeAuthentication.None) {
                        throw IllegalStateException("private exception")
                    }
                rejected.record(AiProbeDiagnostic(AiProbeStage.Cancelled)) // No exception escapes to UI.
            }
        }

    @Test fun boundedRotationKeepsOnlyOneBackupAndSeparateAttempts() =
        temporary { root ->
            val writer = DesktopAiProbeLogWriter(root, 1024)
            val old = UUID.randomUUID()
            repeat(25) { write(writer, old, AiProbeDiagnostic(AiProbeStage.Preparing)) }
            val latest = UUID.randomUUID()
            write(writer, latest, AiProbeDiagnostic(AiProbeStage.Cancelled))
            assertContains(Files.readString(root.resolve("ai-probe.log")), "attempt=$latest")
            assertTrue(Files.size(root.resolve("ai-probe.log")) <= 1024)
            assertTrue(Files.size(root.resolve("ai-probe.log.1")) <= 1024)
            Files.list(root).use { assertEquals(2, it.count()) }
        }

    @Test fun symlinkAndUnsafePermissionsAreRefusedWithoutTouchingTheirTarget() =
        temporary { root ->
            if (Files.getFileStore(root.parent.parent).supportsFileAttributeView("posix")) {
                val writer = DesktopAiProbeLogWriter(root)
                write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Preparing))
                val file = root.resolve("ai-probe.log")
                val target = root.parent.parent.resolve("untouched.txt")
                Files.writeString(target, "untouched")
                Files.delete(file)
                Files.createSymbolicLink(file, target)
                assertFails { write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Failed)) }
                assertEquals("untouched", Files.readString(target))
                Files.delete(file)
                Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"))
                assertFails { write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Preparing)) }
                assertFalse(Files.exists(file))
            }
        }

    @Test fun parentAndBackupSymlinksAreAlsoRefused() =
        temporary { root ->
            if (Files.getFileStore(root.parent.parent).supportsFileAttributeView("posix")) {
                val writer = DesktopAiProbeLogWriter(root)
                write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Preparing))
                val target = root.parent.parent.resolve("untouched.txt")
                Files.writeString(target, "untouched")
                Files.createSymbolicLink(root.resolve("ai-probe.log.1"), target)
                assertFails { write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Preparing)) }
                assertEquals("untouched", Files.readString(target))
                Files.delete(root.resolve("ai-probe.log.1"))
                Files.delete(root.resolve("ai-probe.log"))
                Files.delete(root)
                Files.createSymbolicLink(root, root.parent.parent)
                assertFails { write(writer, UUID.randomUUID(), AiProbeDiagnostic(AiProbeStage.Preparing)) }
                assertFalse(Files.exists(root.parent.parent.resolve("ai-probe.log")))
            }
        }
}
