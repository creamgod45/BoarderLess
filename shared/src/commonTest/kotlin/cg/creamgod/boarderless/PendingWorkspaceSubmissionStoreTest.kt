package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.*

class PendingWorkspaceSubmissionStoreTest {
    private val scope = PendingSubmissionScope("https://qa.example.invalid/api/v1", "user", "client", "workspace")
    private fun entry(text: String = "Draft") = PendingWorkspaceSubmission(scope, "local-op",
        SubmitOperationsRequest(clientId = scope.clientId, transactionId = "transaction", baseVersion = 8,
            operations = listOf(OperationDto("wire-op", 10, "create_object", payload = buildJsonObject { put("text", text) }))))

    @Test fun oversizedManifestAndDeclaredByteMismatchKeepEvidenceAndStopEarly() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        val saved = entry("🙂".repeat(2000))
        store.save(saved)
        val key = memory.keys.single { it.startsWith("wsp1.") }
        val raw = memory.getStringOrNull(key)!!
        memory.putString(key, raw + " ".repeat(512))
        assertFailsWith<BackendContractException> { store.load(scope) }
        assertEquals(raw + " ".repeat(512), memory.getStringOrNull(key))
        memory.putString(key, JsonObject(Json.parseToJsonElement(raw).jsonObject + ("byteSize" to JsonPrimitive(1))).toString())
        var reads = 0
        val counted = object : Settings by memory {
            override fun getStringOrNull(key: String): String? {
                if (key.startsWith("wsc1.")) reads++
                return memory.getStringOrNull(key)
            }
        }
        assertFailsWith<BackendContractException> { PendingWorkspaceSubmissionStore(counted).load(scope) }
        assertEquals(1, reads)
        memory.putString(key, raw)
        assertEquals(saved, store.load(scope))
    }

    private fun deepEntry(): PendingWorkspaceSubmission {
        val saved = entry()
        val payload = buildJsonObject { put("deep", Json.parseToJsonElement("[".repeat(70) + "0" + "]".repeat(70))) }
        return saved.copy(request = saved.request.copy(operations = listOf(saved.request.operations.single().copy(payload = payload))))
    }

    @Test fun deeplyNestedWireContentCannotBePublishedAndValidIdentityStillWorks() {
        val memory = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(memory)
        assertFailsWith<IllegalArgumentException> { store.save(deepEntry()) }
        assertTrue(memory.keys.isEmpty())
        store.save(entry())
        assertEquals(entry(), store.load(scope))
    }

    @Test fun legacyDeepContentWithValidChecksumIsRetainedButNeverDecodedOrReplaced() {
        // Emulate the old v1 publisher, which did not enforce lexical depth.
        val memory = InMemorySettings()
        val json = Json { encodeDefaults = true }
        val saved = deepEntry()
        val content = json.encodeToString(PendingWorkspaceSubmission.serializer(), saved)
        fun digest(text: String) = SHA256().digest(text.encodeToByteArray()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val scopeJson = json.encodeToString(PendingSubmissionScope.serializer(), scope)
        val hash = digest(content)
        assertTrue(content.length <= 2048)
        memory.putString("wsc1." + digest("$scopeJson:$hash:0"), content)
        memory.putString("wsp1." + digest(scopeJson), buildJsonObject {
            put("version", 1); put("chunks", 1); put("byteSize", content.encodeToByteArray().size); put("digest", hash)
        }.toString())
        val original = memory.keys.associateWith { memory.getStringOrNull(it) }
        val store = PendingWorkspaceSubmissionStore(memory)
        val failure = assertFailsWith<BackendContractException> { store.load(scope) }
        assertTrue(failure.cause is IllegalArgumentException)
        assertFailsWith<BackendContractException> { store.save(entry()) }
        assertFailsWith<BackendContractException> { store.acknowledge(scope, "transaction") }
        assertEquals(original, memory.keys.associateWith { memory.getStringOrNull(it) })
    }

    @Test fun restartRetainsExactWireIdentitiesAndLargeUnicodePayload() {
        val memory = InMemorySettings()
        val limited = object : Settings by memory {
            override fun putString(key: String, value: String) {
                assertTrue(key.length <= 80)
                assertTrue(value.length <= 8192)
                assertFalse(value.lastOrNull()?.let { it in '\uD800'..'\uDBFF' } == true)
                memory.putString(key, value)
            }
        }
        val saved = entry("A".repeat(2039) + "🙂".repeat(8000) + "素材")
        PendingWorkspaceSubmissionStore(limited).save(saved)
        assertEquals(saved, PendingWorkspaceSubmissionStore(limited).load(scope))
        assertTrue(memory.keys.count { it.startsWith("wsc1.") } > 1)
    }

    @Test fun scopesAndUnresolvedTransactionsCannotBeReassigned() {
        val store = PendingWorkspaceSubmissionStore(InMemorySettings())
        val saved = entry()
        store.save(saved)
        store.save(saved)
        for (other in listOf(scope.copy(apiBase = "https://other.invalid/api/v1"), scope.copy(userId = "other"),
            scope.copy(clientId = "other"), scope.copy(workspaceId = "other"))) assertNull(store.load(other))
        assertFailsWith<IllegalStateException> { store.save(saved.copy(localOperationId = "other")) }
        assertFailsWith<IllegalStateException> { store.save(entry("Changed")) }
        assertFalse(store.acknowledge(scope, "other"))
        assertEquals(saved, store.load(scope))
        assertTrue(store.acknowledge(scope, saved.request.transactionId))
        assertNull(store.load(scope))
    }

    @Test fun incompleteWriteDoesNotPublishManifestAndExactRetryCanRecover() {
        val memory = InMemorySettings()
        var writes = 0
        val interrupted = object : Settings by memory {
            override fun putString(key: String, value: String) {
                if (++writes == 2) error("Simulated interrupted storage")
                memory.putString(key, value)
            }
        }
        val saved = entry("Large".repeat(2000))
        assertFailsWith<IllegalStateException> { PendingWorkspaceSubmissionStore(interrupted).save(saved) }
        assertNull(PendingWorkspaceSubmissionStore(memory).load(scope))
        PendingWorkspaceSubmissionStore(memory).save(saved)
        assertEquals(saved, PendingWorkspaceSubmissionStore(memory).load(scope))
    }

    @Test fun corruptedOrMissingChunkFailsClosedWithoutDiscardingEvidence() {
        for (remove in listOf(false, true)) {
            val settings = InMemorySettings()
            val store = PendingWorkspaceSubmissionStore(settings)
            store.save(entry())
            val chunk = settings.keys.single { it.startsWith("wsc1.") }
            if (remove) settings.remove(chunk) else settings.putString(chunk, "corrupted")
            assertFailsWith<BackendContractException> { store.load(scope) }
            assertFailsWith<BackendContractException> { store.save(entry()) }
            assertTrue(settings.keys.any { it.startsWith("wsp1.") })
        }
    }

    @Test fun invalidSequenceAndClientIdentityNeverWritePendingData() {
        val settings = InMemorySettings()
        val store = PendingWorkspaceSubmissionStore(settings)
        val saved = entry()
        for (request in listOf(saved.request.copy(clientId = "other"), saved.request.copy(baseVersion = Long.MAX_VALUE),
            saved.request.copy(operations = listOf(saved.request.operations.single().copy(clientSeq = Long.MAX_VALUE))),
            saved.request.copy(operations = saved.request.operations + saved.request.operations.single().copy(operationId = "next", clientSeq = 15)))) {
            assertFailsWith<IllegalArgumentException> { store.save(saved.copy(request = request)) }
            assertEquals(0, settings.size)
        }
    }
}
