package cg.creamgod.boarderless.data.ai

/** Typed request/response summaries only; never raw bodies, URLs, headers or exception text. */
internal enum class AiProbeStage { Preparing, CheckingWorkspace, Connecting, HttpResponse, Receiving, Completed, Cancelled, Failed }

internal enum class AiProbeFailure {
    Configuration,
    ConsentDenied,
    WorkspaceChanged,
    WorkspaceBackend,
    WorkspaceScopeChanged,
    WorkspaceVersionChanged,
    WorkspaceSnapshotChanged,
    Preparation,
    Network,
    Timeout,
    HttpRejected,
    ResponseFormat,
    StreamFormat,
    ProviderError,
    Incomplete,
    UnsupportedMode,
    OutputLimit,
    Unknown,
}

internal enum class AiProbeResponseType { Sse, Json, Html, Other, Missing }

internal data class AiProbeDiagnostic(
    val stage: AiProbeStage,
    val failure: AiProbeFailure? = null,
    val httpStatus: Int? = null,
    val responseType: AiProbeResponseType? = null,
    val preparedAuthentication: AiPreparedAuthentication? = null,
    val authenticationChallenge: AiAuthenticationChallenge? = null,
    val request: AiWireRequestDiagnostic? = null,
    val providerError: AiWireErrorDiagnostic? = null,
    val responseRequestId: AiWireRequestId? = null,
    val responseHeadersMillis: Long? = null,
) {
    fun reportLine(): String =
        buildString {
            append(stage.name)
            failure?.let { append(" code=").append(it.name) }
            httpStatus?.let { append(" HTTP=").append(it) }
            responseType?.let { append(" contentType=").append(it.name) }
            preparedAuthentication?.let { append(" preparedAuth=").append(it.name) }
            authenticationChallenge?.let { append(" authChallenge=").append(it.name) }
            request?.let { append(" ").append(it.report()) }
            responseRequestId?.let { append(" responseRequestId=").append(it) }
            responseHeadersMillis?.let { append(" responseHeadersMs=").append(it.coerceAtLeast(0)) }
            providerError?.let { append(" ").append(it.report()) }
        }
}

internal class AiProbePreflightException(
    val failure: AiProbeFailure,
    val status: Int? = null,
) : IllegalStateException("AI workspace preflight failed")
