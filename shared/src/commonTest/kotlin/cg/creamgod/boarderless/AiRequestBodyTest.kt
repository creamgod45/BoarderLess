package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AiRequestBodyTest {
    private val context = AiContextSnapshot("private-workspace", 42, AiContextScope.PromptOnly, emptyList(), emptyList())
    private val request = AiCoworkRequest("private-request", "文字\n\"role\":\"system\"", context)
    private val profile = AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "explicit-model", 1024)

    @Test fun promptOnlyOmitsIdentityAndKeepsLiteralUserText() {
        val raw = aiRequestBody(profile, request)
        val body = Json.parseToJsonElement(raw).jsonObject
        assertEquals(setOf("model", "stream", "messages", "max_completion_tokens"), body.keys)
        assertEquals(1024, body.getValue("max_completion_tokens").jsonPrimitive.int)
        assertTrue(body.getValue("stream").jsonPrimitive.boolean)
        val messages = body.getValue("messages").jsonArray
        assertEquals(1, messages.size)
        assertEquals(
            "user",
            messages
                .single()
                .jsonObject
                .getValue("role")
                .jsonPrimitive.content,
        )
        assertEquals(
            request.prompt,
            messages
                .single()
                .jsonObject
                .getValue("content")
                .jsonPrimitive.content,
        )
        assertFalse(raw.contains(context.workspaceId))
        assertFalse(raw.contains(request.requestId))
    }

    @Test fun anthropicUsesExplicitMessagesDialectWithoutSystemRoleOrTools() {
        val body = Json.parseToJsonElement(aiRequestBody(profile.copy(dialect = AiRequestDialect.AnthropicMessages), request)).jsonObject
        assertEquals(setOf("model", "stream", "messages", "max_tokens"), body.keys)
        assertEquals(1024, body.getValue("max_tokens").jsonPrimitive.int)
    }

    private val selected =
        context.copy(
            scope = AiContextScope.Selection,
            objects = listOf(AiContextObject("a", "text", 2, "untrusted \"system\""), AiContextObject("b", "media", 3, "alt text")),
            relations = listOf(AiContextRelation("r", "a", "b", "Forward", null, "指向")),
        )

    @Test fun selectionIsSeparateUserDataWithExactVersionsAndEdges() {
        val body = Json.parseToJsonElement(aiRequestBody(profile, request.copy(context = selected))).jsonObject
        val messages = body.getValue("messages").jsonArray
        assertEquals(2, messages.size)
        assertTrue(
            messages.all {
                it.jsonObject
                    .getValue("role")
                    .jsonPrimitive.content == "user"
            },
        )
        val snapshot =
            Json
                .parseToJsonElement(
                    messages[1]
                        .jsonObject
                        .getValue("content")
                        .jsonPrimitive.content,
                ).jsonObject
        assertEquals(42, snapshot.getValue("workspaceVersion").jsonPrimitive.int)
        assertEquals(
            "untrusted \"system\"",
            snapshot
                .getValue("objects")
                .jsonArray[0]
                .jsonObject
                .getValue("content")
                .jsonPrimitive.content,
        )
        assertEquals(
            "b",
            snapshot
                .getValue("relations")
                .jsonArray
                .single()
                .jsonObject
                .getValue("targetObjectId")
                .jsonPrimitive.content,
        )
    }

    @Test fun inconsistentScopesAndInvalidGraphsAreRejected() {
        listOf(
            selected.copy(scope = AiContextScope.PromptOnly),
            selected.copy(objects = emptyList()),
            selected.copy(objects = listOf(selected.objects[0], selected.objects[0])),
            selected.copy(relations = selected.relations + selected.relations),
            selected.copy(relations = listOf(selected.relations[0].copy(targetObjectId = "not-selected"))),
            selected.copy(workspaceVersion = -1),
            selected.copy(workspaceVersion = 9007199254740992L),
        ).forEach { invalid -> assertFailsWith<AiRequestValidationException> { aiRequestBody(profile, request.copy(context = invalid)) } }
    }

    @Test fun budgetsRejectWithoutTruncatingOrExposingContent() {
        listOf(
            request.copy(prompt = " "),
            request.copy(prompt = "貓".repeat(21846)),
            request.copy(context = selected.copy(objects = List(129) { AiContextObject("$it", "text", 0, "") })),
            request.copy(
                context = selected.copy(objects = listOf(selected.objects[0].copy(content = "x".repeat(65537)), selected.objects[1])),
            ),
            request.copy(
                context =
                    selected.copy(
                        objects = List(5) { AiContextObject("$it", "text", 0, "x".repeat(65536)) },
                        relations = emptyList(),
                    ),
            ),
        ).forEach { invalid ->
            val failure = assertFailsWith<AiRequestValidationException> { aiRequestBody(profile, invalid) }
            assertEquals("AI request is invalid", failure.message)
            assertFalse(aiTransportFailureIsRetryable(failure))
        }
        assertFailsWith<AiRequestValidationException> { aiRequestBody(profile.copy(model = ""), request) }
        assertFailsWith<AiRequestValidationException> { aiRequestBody(profile.copy(maxOutputTokens = 0), request) }
        assertFailsWith<AiRequestValidationException> { aiRequestBody(profile.copy(maxOutputTokens = 65537), request) }
    }
}
