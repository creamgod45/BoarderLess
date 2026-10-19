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

    @Test fun explicitChatTokenFieldReachesUnderstandingAndEveryReviewedToolRound() {
        for (field in AiChatTokenLimitField.entries) {
            val profile = AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "router-model", 2048, chatTokenLimitField = field)
            val bodies = listOf(aiRequestBody(profile, request),
                aiDiagramToolRequestBody(profile, "request", "session", request.prompt, caps),
                aiDiagramToolRequestBody(profile, "request", "session", request.prompt, caps, listOf(turn)))
            for (wire in bodies) {
                val body = Json.parseToJsonElement(wire).jsonObject
                assertEquals(2048, body.getValue(field.wireName).jsonPrimitive.int)
                val other = AiChatTokenLimitField.entries.single { it != field }
                assertFalse(other.wireName in body)
                val diagnostic = assertNotNull(aiWireRequestDiagnostic(wire, "/v1/chat/completions"))
                assertEquals(if (field == AiChatTokenLimitField.MaxTokens) 2048 else null, diagnostic.maxTokens)
                assertEquals(if (field == AiChatTokenLimitField.MaxCompletionTokens) 2048 else null, diagnostic.maxCompletionTokens)
            }
        }
    }

    @Test fun blankLimitOmitsBothKeysAndClaudeKeepsItsRequiredFieldRegardlessOfChatChoice() {
        for (field in AiChatTokenLimitField.entries) {
            val profile = AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "router-model", chatTokenLimitField = field)
            for (wire in listOf(aiRequestBody(profile, request), aiDiagramToolRequestBody(profile, "request", "session", request.prompt, caps))) {
                val body = Json.parseToJsonElement(wire).jsonObject
                assertFalse("max_tokens" in body)
                assertFalse("max_completion_tokens" in body)
            }
            val claude = profile.copy(dialect = AiRequestDialect.AnthropicMessages, maxOutputTokens = 2048)
            for (wire in listOf(aiRequestBody(claude, request), aiDiagramToolRequestBody(claude, "request", "session", request.prompt, caps, listOf(turn)))) {
                val body = Json.parseToJsonElement(wire).jsonObject
                assertEquals(2048, body.getValue("max_tokens").jsonPrimitive.int)
                assertFalse("max_completion_tokens" in body)
            }
        }
    }

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
