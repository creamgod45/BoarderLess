package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.model.NodeShape
import kotlinx.serialization.json.*
import kotlin.test.*

class AiRequestParametersTest {
    private val request = AiCoworkRequest("request", "Draw a diagram",
        AiContextSnapshot("workspace", 0, AiContextScope.PromptOnly, emptyList(), emptyList()))
    private val caps = AiDiagramCapabilities(setOf(NodeShape.Rectangle), setOf("blue"), setOf("group"))
    private val turn = AiDiagramToolTurn("Planning",
        listOf(AiDiagramToolCall("request", "session", "call", "read_approved_graph", "{}")),
        listOf(AiDiagramToolResult("call", AiDiagramToolStatus.Read, buildJsonObject { put("text", "approved graph") })))

    @Test fun blankAndExplicitFieldsReachBothProtocolsAndEveryToolRound() {
        for (dialect in AiRequestDialect.entries) {
            for ((tokens, temperature) in listOf("" to "", "  " to "\t", "65537" to "", "" to "0", "8192" to "0.7")) {
                val options = parseAiRequestParameters(tokens, temperature)
                val profile = AiRequestBodyProfile(dialect, "model", options.maxOutputTokens, options.temperature)
                val bodies = listOf(aiRequestBody(profile, request),
                    aiDiagramToolRequestBody(profile, "request", "session", request.prompt, caps),
                    aiDiagramToolRequestBody(profile, "request", "session", request.prompt, caps, listOf(turn)))
                for (raw in bodies) {
                    val body = Json.parseToJsonElement(raw).jsonObject
                    val key = if (dialect == AiRequestDialect.OpenAiChat) "max_completion_tokens" else "max_tokens"
                    val other = if (key == "max_tokens") "max_completion_tokens" else "max_tokens"
                    assertFalse(other in body)
                    if (options.maxOutputTokens == null) assertFalse(key in body)
                    else assertEquals(options.maxOutputTokens, body.getValue(key).jsonPrimitive.int)
                    if (options.temperature == null) assertFalse("temperature" in body)
                    else assertEquals(options.temperature, body.getValue("temperature").jsonPrimitive.double)
                }
            }
        }
    }

    @Test fun invalidInputCannotBecomeAnOmittedParameterOrGuessedDefault() {
        for (tokens in listOf("0", "-1", "1.5", "abc", "2147483648")) {
            assertFailsWith<AiRequestValidationException> { parseAiRequestParameters(tokens, "") }
        }
        for (temperature in listOf("abc", "NaN", "Infinity", "1e999")) {
            assertFailsWith<AiRequestValidationException> { parseAiRequestParameters("", temperature) }
        }
        assertEquals(AiRequestParameters(2000, 0.5), parseAiRequestParameters(" 2000 ", " 0.5 "))
    }

    @Test fun directProfilesAlsoRejectNonFiniteTemperatureAndInvalidTokenCount() {
        for (profile in listOf(AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "model", 0),
            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "model", temperature = Double.NaN))) {
            assertFailsWith<AiRequestValidationException> { aiRequestBody(profile, request) }
            assertFailsWith<AiRequestValidationException> {
                aiDiagramToolRequestBody(profile, "request", "session", request.prompt, caps)
            }
        }
    }
}
