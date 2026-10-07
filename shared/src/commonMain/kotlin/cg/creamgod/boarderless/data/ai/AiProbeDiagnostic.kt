package cg.creamgod.boarderless.data.ai

/** Only typed safe metadata. Never raw exception text, request/response body, URL or headers. */
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
) {
    fun reportLine(): String =
        buildString {
            append(stage.name)
            failure?.let { append(" code=").append(it.name) }
            httpStatus?.let { append(" HTTP=").append(it) }
            responseType?.let { append(" contentType=").append(it.name) }
        }
}

internal class AiProbePreflightException(
    val failure: AiProbeFailure,
    val status: Int? = null,
) : IllegalStateException("AI workspace preflight failed")
