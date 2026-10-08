package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** Closed diagnostic vocabulary: provider strings and canvas content never reach the log. */
internal enum class AiWireParameter {
    Model, Messages, Tools, Stream, MaxCompletionTokens, MaxTokens, ReasoningEffort,
    Temperature, ResponseFormat, ToolChoice, ParallelToolCalls, Other, Missing,
}
internal enum class AiWireModel { Gpt56Luna, Gpt56Terra, Gpt56Sol, Other }
internal enum class AiWireReasoning { None, Minimal, Low, Medium, High, Xhigh, Max, Default, Other }
internal enum class AiWireEndpoint { ChatCompletions, Responses, Messages, Other }
internal enum class AiWireErrorBody { Parsed, Empty, TooLarge, InvalidUtf8, InvalidJson, ReadFailed, NonJson }
internal enum class AiWireErrorType { InvalidRequest, Authentication, Permission, RateLimit, NotFound, Other, Missing }
internal enum class AiWireErrorCode { UnsupportedParameter, UnsupportedValue, InvalidParameter, InvalidSchema, ModelNotFound, InvalidApiKey, InsufficientQuota, RateLimit, Other, Missing }
internal enum class AiWireRejection { ReasoningToolsInChat, UnsupportedParameter, InvalidSchema, Other }

internal data class AiWireRequestDiagnostic(
    val endpoint: AiWireEndpoint,
    val model: AiWireModel,
    val bytes: Int,
    val messages: Int,
    val tools: Int,
    val stream: Boolean?,
    val reasoning: AiWireReasoning,
    val maxCompletionTokens: Int?,
    val maxTokens: Int?,
    val parameters: List<AiWireParameter>,
) {
    fun report() = "requestEndpoint=$endpoint requestModel=$model requestBytes=$bytes messages=$messages tools=$tools stream=${stream ?: "Unknown"} reasoning=$reasoning max_completion_tokens=${maxCompletionTokens ?: "Absent"} max_tokens=${maxTokens ?: "Absent"} parameters=${parameters.joinToString(",")}"
}
internal data class AiWireErrorDiagnostic(
    val body: AiWireErrorBody,
    val bytes: Int,
    val type: AiWireErrorType = AiWireErrorType.Missing,
    val code: AiWireErrorCode = AiWireErrorCode.Missing,
    val parameter: AiWireParameter = AiWireParameter.Missing,
    val rejection: AiWireRejection = AiWireRejection.Other,
) {
    fun report() = "errorBody=$body errorBytes=$bytes providerType=$type providerCode=$code providerParam=$parameter rejection=$rejection"
}

private fun parameter(raw: String?): AiWireParameter = when (raw?.substringBefore('[')?.substringBefore('.')) {
    null -> AiWireParameter.Missing
    "model" -> AiWireParameter.Model
    "messages" -> AiWireParameter.Messages
    "tools" -> AiWireParameter.Tools
    "stream" -> AiWireParameter.Stream
    "max_completion_tokens" -> AiWireParameter.MaxCompletionTokens
    "max_tokens" -> AiWireParameter.MaxTokens
    "reasoning_effort", "reasoning" -> AiWireParameter.ReasoningEffort
    "temperature" -> AiWireParameter.Temperature
    "response_format" -> AiWireParameter.ResponseFormat
    "tool_choice" -> AiWireParameter.ToolChoice
    "parallel_tool_calls" -> AiWireParameter.ParallelToolCalls
    else -> AiWireParameter.Other
}
private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun aiWireRequestDiagnostic(body: String, path: String): AiWireRequestDiagnostic? = try {
    require(body.length <= 1024 * 1024)
    requireBoundedDraftJsonDepth(body)
    val obj = Json.parseToJsonElement(body) as JsonObject
    AiWireRequestDiagnostic(
        when (path) {
            "/v1/chat/completions" -> AiWireEndpoint.ChatCompletions
            "/v1/responses" -> AiWireEndpoint.Responses
            "/v1/messages" -> AiWireEndpoint.Messages
            else -> AiWireEndpoint.Other
        },
        when (obj.string("model")) {
            "gpt-5.6-luna" -> AiWireModel.Gpt56Luna
            "gpt-5.6-terra" -> AiWireModel.Gpt56Terra
            "gpt-5.6-sol", "gpt-5.6" -> AiWireModel.Gpt56Sol
            else -> AiWireModel.Other
        },
        body.encodeToByteArray().size,
        (obj["messages"] as? JsonArray)?.size ?: 0,
        (obj["tools"] as? JsonArray)?.size ?: 0,
        (obj["stream"] as? JsonPrimitive)?.booleanOrNull,
        when (obj.string("reasoning_effort")) {
            null -> AiWireReasoning.Default
            "none" -> AiWireReasoning.None
            "minimal" -> AiWireReasoning.Minimal
            "low" -> AiWireReasoning.Low
            "medium" -> AiWireReasoning.Medium
            "high" -> AiWireReasoning.High
            "xhigh" -> AiWireReasoning.Xhigh
            "max" -> AiWireReasoning.Max
            else -> AiWireReasoning.Other
        },
        (obj["max_completion_tokens"] as? JsonPrimitive)?.intOrNull,
        (obj["max_tokens"] as? JsonPrimitive)?.intOrNull,
        obj.keys.map(::parameter).distinct().sortedBy { it.ordinal },
    )
} catch (_: Exception) { null }

internal fun aiWireErrorDiagnostic(bytes: ByteArray): AiWireErrorDiagnostic {
    if (bytes.isEmpty()) return AiWireErrorDiagnostic(AiWireErrorBody.Empty, 0)
    if (bytes.size > 16384) return AiWireErrorDiagnostic(AiWireErrorBody.TooLarge, bytes.size)
    val text = try { bytes.decodeToString(throwOnInvalidSequence = true) }
        catch (_: Exception) { return AiWireErrorDiagnostic(AiWireErrorBody.InvalidUtf8, bytes.size) }
    return try {
        requireBoundedDraftJsonDepth(text)
        val obj = Json.parseToJsonElement(text) as JsonObject
        val error = obj["error"] as JsonObject
        val message = error.string("message")?.lowercase().orEmpty()
        AiWireErrorDiagnostic(
            AiWireErrorBody.Parsed, bytes.size,
            when (error.string("type")) {
                null -> AiWireErrorType.Missing
                "invalid_request_error" -> AiWireErrorType.InvalidRequest
                "authentication_error" -> AiWireErrorType.Authentication
                "permission_error" -> AiWireErrorType.Permission
                "rate_limit_error" -> AiWireErrorType.RateLimit
                "not_found_error" -> AiWireErrorType.NotFound
                else -> AiWireErrorType.Other
            },
            when (error.string("code")) {
                null -> AiWireErrorCode.Missing
                "unsupported_parameter" -> AiWireErrorCode.UnsupportedParameter
                "unsupported_value" -> AiWireErrorCode.UnsupportedValue
                "invalid_parameter" -> AiWireErrorCode.InvalidParameter
                "invalid_function_parameters", "invalid_json_schema" -> AiWireErrorCode.InvalidSchema
                "model_not_found" -> AiWireErrorCode.ModelNotFound
                "invalid_api_key" -> AiWireErrorCode.InvalidApiKey
                "insufficient_quota" -> AiWireErrorCode.InsufficientQuota
                "rate_limit_exceeded" -> AiWireErrorCode.RateLimit
                else -> AiWireErrorCode.Other
            },
            parameter(error.string("param")),
            when {
                "function tools" in message && "reasoning" in message && "not supported" in message &&
                    ("chat/completions" in message || "chat completions" in message) -> AiWireRejection.ReasoningToolsInChat
                "unsupported parameter" in message -> AiWireRejection.UnsupportedParameter
                "invalid schema" in message -> AiWireRejection.InvalidSchema
                else -> AiWireRejection.Other
            },
        )
    } catch (_: Exception) { AiWireErrorDiagnostic(AiWireErrorBody.InvalidJson, bytes.size) }
}

/** Read at most 16 KiB + one byte; a bad diagnostic never erases the original HTTP failure. */
internal suspend fun ByteReadChannel.aiWireErrorDiagnostic(): AiWireErrorDiagnostic {
    val buffer = ByteArray(16385)
    var size = 0
    return try {
        withTimeoutOrNull(2000) {
            while (size < buffer.size) {
                val count = readAvailable(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            aiWireErrorDiagnostic(buffer.copyOf(size))
        } ?: AiWireErrorDiagnostic(AiWireErrorBody.ReadFailed, size)
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { AiWireErrorDiagnostic(AiWireErrorBody.ReadFailed, size) }
}
