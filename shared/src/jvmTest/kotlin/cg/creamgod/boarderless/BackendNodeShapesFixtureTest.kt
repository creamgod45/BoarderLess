package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.NodeShape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** APP and backend must change the fixed shape list in the same commit; this test enforces it. */
class BackendNodeShapesFixtureTest {
    @Test fun backendShapeListMatchesTheAppEnumInContentAndOrder() {
        val root = System.getenv("BOARDERLESS_TEST_REPO_ROOT") ?: System.getProperty("user.dir")
        val file = listOf(File(root), File(root).parentFile)
            .map { File(it, "backend/tests/fixtures/contract/node-shapes.json") }.first { it.isFile }
        val fixture = Json.parseToJsonElement(file.readText()).jsonObject
        val backend = fixture.getValue("shapes").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(fixture.getValue("count").jsonPrimitive.content.toInt(), backend.size)
        assertEquals(backend, NodeShape.entries.map { it.token })
    }
}
