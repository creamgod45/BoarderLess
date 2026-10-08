package cg.creamgod.boarderless.data.ai

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

internal data class AiRequestParameters(val maxOutputTokens: Int?, val temperature: Double?)

/** Blank inputs mean absent wire keys, including whitespace-only input. */
internal fun parseAiRequestParameters(maxTokens: String, temperature: String): AiRequestParameters {
    val tokens = maxTokens.trim().takeIf { it.isNotEmpty() }?.let {
        it.toIntOrNull()?.takeIf { value -> value > 0 } ?: throw AiRequestValidationException()
    }
    val sampling = temperature.trim().takeIf { it.isNotEmpty() }?.let {
        it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: throw AiRequestValidationException()
    }
    return AiRequestParameters(tokens, sampling)
}

internal fun aiRequestParametersValid(profile: AiRequestBodyProfile): Boolean =
    (profile.maxOutputTokens == null || profile.maxOutputTokens > 0) &&
        (profile.temperature == null || profile.temperature.isFinite())

internal fun JsonObjectBuilder.putAiRequestParameters(profile: AiRequestBodyProfile) {
    if (!aiRequestParametersValid(profile)) throw AiRequestValidationException()
    profile.maxOutputTokens?.let {
        put(if (profile.dialect == AiRequestDialect.OpenAiChat) "max_completion_tokens" else "max_tokens", it)
    }
    profile.temperature?.let { put("temperature", it) }
}
