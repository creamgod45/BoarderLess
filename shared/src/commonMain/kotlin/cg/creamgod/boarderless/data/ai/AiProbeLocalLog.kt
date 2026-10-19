package cg.creamgod.boarderless.data.ai

/** Closed metadata vocabulary: no free-form configuration, credentials or content enter the log. */
internal enum class AiProbeAuthentication { Bearer, ApiKey, None }

internal interface AiProbeLocalLog {
    fun record(event: AiProbeDiagnostic)
}

/** Desktop returns a bounded best-effort local sink; other platforms do not persist diagnostics. */
internal expect fun aiProbeLocalLog(
    dialect: AiRequestDialect,
    authentication: AiProbeAuthentication,
): AiProbeLocalLog?
