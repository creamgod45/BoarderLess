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
    EmptyResponse,
    UnsupportedMode,
    OutputLimit,
    Unknown,
}

internal enum class AiProbeResponseType { Sse, Json, Html, Other, Missing }

/** Completed wire reply counts only, never tool names, arguments or canvas text.
 * A complete round is not evidence that a proposal was applied to the workspace. */
internal data class AiReplyDiagnostic(val textBytes: Int, val toolCalls: Int, val argumentBytes: Int) {
    init {
        require(textBytes in 0..128 * 1024 && toolCalls in 0..64 && argumentBytes in 0..1024 * 1024)
    }
    fun report(): String = "replyTextBytes=$textBytes replyToolCalls=$toolCalls replyArgumentBytes=$argumentBytes"
}

/** Guidance, not a claim about which proxy/provider rejected the request. */
internal enum class AiAuthenticationGuidance { MissingCredentials, BasicChallenge, Unauthorized, Forbidden }

internal fun AiProbeDiagnostic.authenticationGuidance(): AiAuthenticationGuidance? {
    if (stage != AiProbeStage.Failed || failure != AiProbeFailure.HttpRejected) return null
    return when (httpStatus) {
        403 -> AiAuthenticationGuidance.Forbidden
        401 -> when {
            preparedAuthentication == AiPreparedAuthentication.None -> AiAuthenticationGuidance.MissingCredentials
            authenticationChallenge == AiAuthenticationChallenge.Basic -> AiAuthenticationGuidance.BasicChallenge
            else -> AiAuthenticationGuidance.Unauthorized
        }
        else -> null
    }
}

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
    val elapsedMillis: Long? = null,
    val reply: AiReplyDiagnostic? = null,
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
            elapsedMillis?.let { append(" elapsedMs=").append(it.coerceAtLeast(0)) }
            providerError?.let { append(" ").append(it.report()) }
            reply?.let { append(" ").append(it.report()) }
        }
}

/** Per-attempt typed metadata only. Never merge another attempt's HTTP outcome;
 * terminal stages retain their response summary instead of erasing it. */
internal class AiProbeDiagnosticTimeline {
    private val started = kotlin.time.TimeSource.Monotonic.markNow()
    private var lastRaw: AiProbeDiagnostic? = null
    private var last: AiProbeDiagnostic? = null

    fun record(event: AiProbeDiagnostic): AiProbeDiagnostic? {
        if (lastRaw == event) return null
        lastRaw = event
        val previous = last
        return event.copy(
            httpStatus = event.httpStatus ?: previous?.httpStatus,
            responseType = event.responseType ?: previous?.responseType,
            preparedAuthentication = event.preparedAuthentication ?: previous?.preparedAuthentication,
            authenticationChallenge = event.authenticationChallenge ?: previous?.authenticationChallenge,
            request = event.request ?: previous?.request,
            providerError = event.providerError ?: previous?.providerError,
            responseRequestId = event.responseRequestId ?: previous?.responseRequestId,
            responseHeadersMillis = event.responseHeadersMillis ?: previous?.responseHeadersMillis,
            elapsedMillis = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0),
            reply = event.reply ?: previous?.reply,
        ).also { last = it }
    }
}

internal class AiProbePreflightException(
    val failure: AiProbeFailure,
    val status: Int? = null,
) : IllegalStateException("AI workspace preflight failed")
