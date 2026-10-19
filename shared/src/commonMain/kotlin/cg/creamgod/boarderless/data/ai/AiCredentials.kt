package cg.creamgod.boarderless.data.ai

import io.ktor.http.*

/** Accept a key or a single pasted Bearer value. Never emit raw input in errors/diagnostics. */
internal fun aiCredentialHeaders(mode: AiProbeAuthentication, input: String): Headers {
    if (mode == AiProbeAuthentication.None) return Headers.Empty
    var key = input.trim()
    if (mode == AiProbeAuthentication.Bearer && key.startsWith("Bearer ", ignoreCase = true)) key = key.substring(7).trim()
    if (key.isEmpty() || (mode == AiProbeAuthentication.Bearer && key.equals("Bearer", true)) ||
        key.length > 8192 - (if (mode == AiProbeAuthentication.Bearer) 7 else 0) ||
        key.any { it.code !in 33..126 }) throw AiRequestValidationException()
    return if (mode == AiProbeAuthentication.Bearer) headersOf(HttpHeaders.Authorization, "Bearer $key") else headersOf("x-api-key", key)
}

internal enum class AiPreparedAuthentication { None, Bearer, ApiKey, BearerAndApiKey, Other }
internal enum class AiAuthenticationChallenge { Missing, Basic, Bearer, Other, Multiple }

internal fun aiPreparedAuthentication(headers: Headers): AiPreparedAuthentication {
    val auth = headers[HttpHeaders.Authorization]
    val key = headers["x-api-key"]
    return when {
        auth == null && key == null -> AiPreparedAuthentication.None
        auth == null -> AiPreparedAuthentication.ApiKey
        !auth.startsWith("Bearer ", true) -> AiPreparedAuthentication.Other
        key != null -> AiPreparedAuthentication.BearerAndApiKey
        else -> AiPreparedAuthentication.Bearer
    }
}

internal fun aiAuthenticationChallenge(headers: Headers): AiAuthenticationChallenge {
    val values = headers.getAll(HttpHeaders.WWWAuthenticate) ?: return AiAuthenticationChallenge.Missing
    if (values.size != 1) return AiAuthenticationChallenge.Multiple
    val value = values.single()
    if (value.length > 8192) return AiAuthenticationChallenge.Other
    return when (value.trimStart().takeWhile { it in 'A'..'Z' || it in 'a'..'z' }.lowercase()) {
        "basic" -> AiAuthenticationChallenge.Basic
        "bearer" -> AiAuthenticationChallenge.Bearer
        else -> AiAuthenticationChallenge.Other
    }
}
