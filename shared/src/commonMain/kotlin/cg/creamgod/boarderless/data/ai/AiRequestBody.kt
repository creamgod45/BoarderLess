package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.domain.ai.*
import kotlinx.serialization.json.*

/** Local servers must explicitly select one of these dialects; never auto-detect/fallback. */
internal enum class AiRequestDialect { OpenAiChat, AnthropicMessages }

/** Not a credential/endpoint profile. Model and output budget are explicit, not guessed defaults. */
internal data class AiRequestBodyProfile(
    val dialect: AiRequestDialect,
    val model: String,
    val maxOutputTokens: Int,
)

internal class AiRequestValidationException : IllegalArgumentException("AI request is invalid")

/** Pure wire body construction. Caller still owes context review/consent and approved transport.
 * Limits are APP safety policy, not provider context-window claims. Never silently truncate.
 * User text stays user content; no tools, media bytes, workspace credentials or automatic edits.
 */
internal fun aiRequestBody(
    profile: AiRequestBodyProfile,
    request: AiCoworkRequest,
): String {
    fun checkRequest(valid: Boolean) {
        if (!valid) throw AiRequestValidationException()
    }

    fun bounded(
        text: String,
        bytes: Int,
    ) {
        checkRequest(text.length <= bytes && text.encodeToByteArray().size <= bytes)
    }

    fun identifier(text: String) {
        checkRequest(text.isNotBlank())
        bounded(text, 256)
    }
    identifier(profile.model)
    checkRequest(profile.maxOutputTokens in 1..65536)
    checkRequest(request.prompt.isNotBlank())
    bounded(request.prompt, 65536)
    val context = request.context
    checkRequest(context.objects.size <= 128 && context.relations.size <= 256)
    val messages =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", request.prompt)
                },
            )
            when (context.scope) {
                AiContextScope.PromptOnly -> {
                    // Reject contradictory scope instead of accidentally leaking objects.
                    checkRequest(context.objects.isEmpty() && context.relations.isEmpty())
                    // Deliberately omit workspace identity/version and request ID in this scope.
                }

                AiContextScope.Selection -> {
                    identifier(context.workspaceId)
                    checkRequest(context.workspaceVersion in 0..9007199254740991L)
                    checkRequest(context.objects.isNotEmpty())
                    val ids = context.objects.map { it.objectId }.toSet()
                    checkRequest(ids.size == context.objects.size)
                    checkRequest(
                        context.relations
                            .map { it.relationId }
                            .toSet()
                            .size == context.relations.size,
                    )
                    val snapshot =
                        buildJsonObject {
                            put("scope", "selection")
                            put("workspaceId", context.workspaceId)
                            put("workspaceVersion", context.workspaceVersion)
                            putJsonArray("objects") {
                                context.objects.forEach { item ->
                                    identifier(item.objectId)
                                    checkRequest(item.objectType in setOf("text", "group", "media"))
                                    checkRequest(item.version in 0..9007199254740991L)
                                    bounded(item.content, 65536)
                                    add(
                                        buildJsonObject {
                                            put("objectId", item.objectId)
                                            put("objectType", item.objectType)
                                            put("version", item.version)
                                            put("content", item.content)
                                        },
                                    )
                                }
                            }
                            putJsonArray("relations") {
                                context.relations.forEach { edge ->
                                    identifier(edge.relationId)
                                    checkRequest(edge.sourceObjectId in ids && edge.targetObjectId in ids)
                                    bounded(edge.direction, 128)
                                    edge.intent?.let { bounded(it, 4096) }
                                    edge.label?.let { bounded(it, 4096) }
                                    add(
                                        buildJsonObject {
                                            put("relationId", edge.relationId)
                                            put("sourceObjectId", edge.sourceObjectId)
                                            put("targetObjectId", edge.targetObjectId)
                                            put("direction", edge.direction)
                                            edge.intent?.let { put("intent", it) }
                                            edge.label?.let { put("label", it) }
                                        },
                                    )
                                }
                            }
                        }.toString()
                    bounded(snapshot, 262144)
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", snapshot)
                        },
                    )
                }
            }
        }
    val body =
        buildJsonObject {
            put("model", profile.model)
            put("stream", true)
            put("messages", messages)
            put(
                when (profile.dialect) {
                    AiRequestDialect.OpenAiChat -> "max_completion_tokens"
                    AiRequestDialect.AnthropicMessages -> "max_tokens"
                },
                profile.maxOutputTokens,
            )
        }.toString()
    bounded(body, 1048576)
    return body
}
