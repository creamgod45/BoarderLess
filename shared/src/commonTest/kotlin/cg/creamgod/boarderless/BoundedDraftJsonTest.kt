package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class BoundedDraftJsonTest {
    @Test fun quotedBracketsEscapedQuotesAndBackslashesAreNotStructure() {
        requireBoundedDraftJsonDepth("{\"text\":" + JsonPrimitive("[]{}\\\"".repeat(1000)).toString() + "}")
        requireBoundedDraftJsonDepth("[\"\\\\\",{\"text\":\"\\\" } ]\"}]")
    }
    @Test fun boundaryDepthIsAllowedAndExcessIsRejectedBeforeDecoding() {
        requireBoundedDraftJsonDepth("[".repeat(64) + "0" + "]".repeat(64))
        assertFailsWith<IllegalArgumentException> { requireBoundedDraftJsonDepth("[".repeat(65) + "0" + "]".repeat(65)) }
    }
    @Test fun brokenFramingAndUnfinishedStringsFailClosed() {
        listOf("{]", "}", "[", "\"unfinished", "\"escape\\").forEach {
            assertFailsWith<IllegalArgumentException> { requireBoundedDraftJsonDepth(it) }
        }
        requireBoundedDraftJsonDepth("{}")
    }
}
