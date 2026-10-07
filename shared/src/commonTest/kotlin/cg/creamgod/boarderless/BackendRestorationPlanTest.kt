package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import kotlinx.serialization.json.*
import kotlin.test.*

class BackendRestorationPlanTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    private fun state() =
        WorkspaceStateDto(
            "workspace",
            3,
            9,
            listOf(
                CanvasObjectDto(
                    "a",
                    "text",
                    4,
                    zIndex = 0,
                    locked = false,
                    transform = json("""{"x":0,"y":0,"width":10,"height":10,"rotationDegrees":0}"""),
                    properties = json("""{"text":"A"}"""),
                ),
                CanvasObjectDto(
                    "b",
                    "text",
                    2,
                    zIndex = 0,
                    locked = false,
                    transform = json("""{"x":0,"y":0,"width":10,"height":10,"rotationDegrees":0}"""),
                    properties = json("""{"text":"B"}"""),
                ),
            ),
            listOf(RelationDto("r", 7, "a", "b", "forward", style = json("{}"))),
        )

    private fun deletion() =
        listOf(
            CommittedWorkspaceOperationDto(
                10,
                "op",
                "tx",
                "actor",
                "client",
                1,
                3,
                4,
                "delete_objects",
                json("""{"objectIds":["a"],"cascadedRelationIds":["r"]}"""),
                1,
                "2026-10-05",
            ),
        )

    @Test fun committedDeleteBuildsIdsOnlyRestoreWithTombstoneCasAndRelationsLast() {
        val plan = planCommittedDeletionRestoration(state(), deletion())
        val operations = plan.operations(20)
        assertEquals(listOf("restore_objects", "restore_relations"), operations.map { it.kind })
        assertEquals(listOf(21L, 22L), operations.map { it.clientSeq })
        assertEquals(mapOf("a" to 5L), operations[0].expectedObjectVersions)
        assertEquals(mapOf("r" to 8L), operations[1].expectedObjectVersions)
        assertEquals(json("""{"objectIds":["a"]}"""), operations[0].payload)
        assertEquals(json("""{"relationIds":["r"]}"""), operations[1].payload)
        assertEquals("tx", plan.deletionTransactionId)
        assertEquals(10L, plan.throughServerSeq)
        assertTrue(operations.map { it.operationId }.distinct().size == 2)
    }

    @Test fun explicitRelationDeleteNeedsNoObjectRestore() {
        val records =
            deletion().map {
                it.copy(
                    operationType = "delete_relations",
                    payload = json("""{"relationIds":["r"]}"""),
                )
            }
        assertEquals(listOf("restore_relations"), planCommittedDeletionRestoration(state(), records).operations(0).map { it.kind })
    }

    @Test fun unknownGapIncompleteCascadeAndUnsafeVersionsCannotCreateRestoration() {
        for (records in listOf(
            deletion().map { it.copy(serverSeq = 11) },
            deletion().map { it.copy(operationType = "create_object") },
            deletion().map { it.copy(payload = json("""{"objectIds":["a"],"cascadedRelationIds":[]}""")) },
            deletion().map { it.copy(payload = json("""{"objectIds":["missing"],"cascadedRelationIds":[]}""")) },
        )) {
            assertFailsWith<BackendContractException> { planCommittedDeletionRestoration(state(), records) }
        }
        val unsafe = state().copy(objects = state().objects.map { it.copy(objectVersion = 9_007_199_254_740_990L) })
        assertFailsWith<BackendContractException> { planCommittedDeletionRestoration(unsafe, deletion()) }
        assertFailsWith<IllegalArgumentException> { planCommittedDeletionRestoration(state(), deletion()).operations(Long.MAX_VALUE) }
    }
}
