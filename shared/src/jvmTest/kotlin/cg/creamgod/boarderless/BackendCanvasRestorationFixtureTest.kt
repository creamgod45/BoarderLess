package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*

/** Reads backend-owned recordings verbatim; never repairs IDs to manufacture a green contract. */
class BackendCanvasRestorationFixtureTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): JsonObject {
        val root = System.getenv("BOARDERLESS_TEST_REPO_ROOT") ?: System.getProperty("user.dir")
        val roots = listOf(File(root), File(root).parentFile)
        val file = roots.map { File(it, "backend/tests/fixtures/contract/canvas-v1/$name.json") }.first { it.isFile }
        return json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test fun officialRestoredStateDecodesWithActiveVersionThree() {
        val body = fixture("22-state-after-restore").getValue("response").jsonObject.getValue("body")
        val state = json.decodeFromJsonElement<WorkspaceStateDto>(body)
        val workspace = state.toDomainWorkspace("Official restoration fixture")
        assertEquals(8L, state.workspaceVersion)
        assertEquals(13L, state.throughServerSeq)
        assertTrue(workspace.objects.values.any { it.version == 3L })
        assertTrue(workspace.relations.values.any { it.version == 3L })
    }

    @Test fun officialRestoreObjectsRequestIsAcceptedButRelationsAreRejectedForMissingCas() {
        val body = fixture("20-restore-objects-relations-accepted").getValue("request").jsonObject.getValue("body")
        val request = json.decodeFromJsonElement<SubmitOperationsRequest>(body)
        assertEquals(listOf("restore_objects", "restore_relations"), request.operations.map { it.kind })
        // restore_objects is normalized; the official restore_relations carries no expectedObjectVersions,
        // which the app validator still requires. Not repaired here: a backend contract question.
        validateRestorationWire(request.operations.single { it.kind == "restore_objects" })
        assertFailsWith<IllegalArgumentException> { validateRestorationWire(request.operations.single { it.kind == "restore_relations" }) }
    }

    @Test fun officialRestoreResponseRestoredVersionKeysMatchTheRestoredIds() {
        val body = fixture("20-restore-objects-relations-accepted").getValue("response").jsonObject.getValue("body")
        val accepted = json.decodeFromJsonElement<AcceptedOperationsDto>(body)
        for (record in accepted.operations) {
            val key = if (record.operationType == "restore_objects") "objectIds" else "relationIds"
            val ids =
                record.payload
                    .getValue(key)
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet()
            assertEquals(
                ids,
                record.payload
                    .getValue("restoredVersions")
                    .jsonObject.keys,
            )
        }
    }
}
