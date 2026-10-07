package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Exact frozen body, including every previous tool result, reviewed separately each round. */
internal data class AiDiagramRequestReview(
    val server: AiServerProfile,
    val requestId: String,
    val sessionId: String,
    val round: Int,
    val body: String,
)

internal enum class AiDiagramLoopFailure { Configuration, ConsentDenied, ScopeChanged, Preparation, Stream, Limit, ToolProtocol }

internal sealed interface AiDiagramLoopResult {
    /** Still a proposal: formal authority/version check and explicit batch review remain required. */
    data class Ready(
        val text: String,
        val proposal: AiProposal?,
        val localReferences: Map<String, CanvasObjectId> = emptyMap(),
    ) : AiDiagramLoopResult

    data class Failed(
        val code: AiDiagramLoopFailure,
    ) : AiDiagramLoopResult
}

internal sealed interface AiDiagramLoopProgress {
    data class Text(
        val round: Int,
        val text: String,
    ) : AiDiagramLoopProgress

    data class Tools(
        val round: Int,
        val results: List<AiDiagramToolResult>,
    ) : AiDiagramLoopProgress
}

private class DiagramLoopStopped(
    val code: AiDiagramLoopFailure,
) : IllegalStateException()

/** Dedicated client ownership transfers to this invocation, including preflight failures.
 * Eight HTTP requests maximum, no retry/fallback; every request awaits exact-body approval before
 * current-scope validation and fresh credential acquisition. Cheap session guards run on each
 * frame and between tools; fresh validation runs before each request and after complete replies.
 * Any failure/cancel discards the entire isolated draft. No WorkspaceRepository/prefs callbacks.
 */
internal suspend fun runAiDiagramToolLoop(
    server: AiServerProfile,
    requestId: String,
    sessionId: String,
    prompt: String,
    workspace: Workspace,
    approvedIds: Set<CanvasObjectId>,
    capabilities: AiDiagramCapabilities,
    approve: suspend (AiDiagramRequestReview) -> Boolean,
    validateScope: suspend () -> Boolean,
    isSessionCurrent: () -> Boolean,
    credentialHeaders: suspend () -> Headers,
    client: HttpClient =
        cg.creamgod.boarderless.data.platformHttpClient {
            followRedirects = false
            install(HttpTimeout)
        },
    onProgress: (AiDiagramLoopProgress) -> Unit = {},
    onDiagnostic: (Int, AiProbeDiagnostic) -> Unit = { _, _ -> },
): AiDiagramLoopResult {
    var draft: AiDiagramDraft? = null
    var round = 1
    var last: AiProbeDiagnostic? = null
    var response: AiProbeDiagnostic? = null
    var observerFailure: Throwable? = null

    fun record(event: AiProbeDiagnostic) {
        if (last?.stage == AiProbeStage.Failed) return
        if (event.stage == AiProbeStage.HttpResponse) response = event
        val safe =
            if (event.stage ==
                AiProbeStage.Failed
            ) {
                event.copy(
                    httpStatus = event.httpStatus ?: response?.httpStatus,
                    responseType =
                        event.responseType ?: response?.responseType,
                )
            } else {
                event
            }
        last = safe
        try {
            onDiagnostic(round, safe)
        } catch (failure: Throwable) {
            observerFailure = failure
            throw failure
        }
    }

    fun progress(event: AiDiagramLoopProgress) {
        try {
            onProgress(event)
        } catch (failure: Throwable) {
            observerFailure = failure
            throw failure
        }
    }

    fun stop(
        code: AiDiagramLoopFailure,
        failure: AiProbeFailure,
    ): Nothing {
        record(AiProbeDiagnostic(AiProbeStage.Failed, failure))
        throw DiagramLoopStopped(code)
    }

    fun guard() {
        if (!isSessionCurrent()) stop(AiDiagramLoopFailure.ScopeChanged, AiProbeFailure.WorkspaceScopeChanged)
    }

    suspend fun currentScope() {
        currentCoroutineContext().ensureActive()
        guard()
        val valid =
            try {
                withTimeout(15_000) { validateScope() }
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                stop(AiDiagramLoopFailure.Preparation, AiProbeFailure.WorkspaceBackend)
            }
        currentCoroutineContext().ensureActive()
        guard()
        if (!valid) stop(AiDiagramLoopFailure.ScopeChanged, AiProbeFailure.WorkspaceChanged)
    }
    try {
        requireSafeAiStreamingClient(client)
        val endpoint = validatedAiServerEndpoint(server, "diagram-tool-loop")
        val caps =
            capabilities.copy(
                shapes = capabilities.shapes.toSet(),
                nodeColors = capabilities.nodeColors.toSet(),
                groupColors = capabilities.groupColors.toSet(),
            )
        val engine = AiDiagramDraft(requestId, sessionId, workspace, approvedIds.toSet(), caps)
        draft = engine
        val history = mutableListOf<AiDiagramToolTurn>()
        // Transport's legacy parameter carries no context; only the exact reviewed wire is used.
        val transportRequest =
            AiCoworkRequest(requestId, prompt, AiContextSnapshot("diagram", 0, AiContextScope.PromptOnly, emptyList(), emptyList()))
        var totalCalls = 0
        var totalArgumentBytes = 0L
        for (nextRound in 1..8) {
            round = nextRound
            last = null
            response = null
            record(AiProbeDiagnostic(AiProbeStage.Preparing))
            guard()
            val body = aiDiagramToolRequestBody(server.body, requestId, sessionId, prompt, caps, history)
            if (!approve(
                    AiDiagramRequestReview(server, requestId, sessionId, round, body),
                )
            ) {
                stop(AiDiagramLoopFailure.ConsentDenied, AiProbeFailure.ConsentDenied)
            }
            currentCoroutineContext().ensureActive()
            guard()
            record(AiProbeDiagnostic(AiProbeStage.CheckingWorkspace))
            currentScope()
            val suppliedCredentials = credentialHeaders()
            val credentials = Headers.build { suppliedCredentials.entries().forEach { (name, values) -> appendAll(name, values) } }
            validateAiCredentialHeaders(credentials)
            // Credentials may suspend (secret-store/user interaction). Revalidate afterward.
            currentScope()
            val transport =
                AiSseHttpTransport(client, onDiagnostic = ::record) {
                    url.takeFrom(endpoint)
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    credentials.entries().forEach { (name, values) -> values.forEach { headers.append(name, it) } }
                    server.anthropicVersion?.let { headers.append("anthropic-version", it) }
                    setBody(body)
                }
            var completed: AiDiagramStreamEvent.Completed? = null
            var streamFailure: AiDiagramStreamFailure? = null
            val frames =
                transport.events(transportRequest).onEach {
                    guard()
                    currentCoroutineContext().ensureActive()
                }
            diagramToolEvents(server.body.dialect, requestId, sessionId, frames).collect { event ->
                guard()
                when (event) {
                    is AiDiagramStreamEvent.TextDelta -> {
                        if (last?.stage != AiProbeStage.Receiving) record(AiProbeDiagnostic(AiProbeStage.Receiving))
                        progress(AiDiagramLoopProgress.Text(round, event.text))
                    }

                    is AiDiagramStreamEvent.Completed -> {
                        completed = event
                    }

                    is AiDiagramStreamEvent.Failed -> {
                        streamFailure = event.code
                    }
                }
            }
            // Session/preflight failure inside frame guards may be converted into a transport
            // failure by the decoder; preserve its typed failure and stop the whole task.
            streamFailure?.let { failure ->
                stop(
                    AiDiagramLoopFailure.Stream,
                    when (failure) {
                        AiDiagramStreamFailure.Limit -> AiProbeFailure.OutputLimit
                        AiDiagramStreamFailure.Format -> AiProbeFailure.StreamFormat
                        AiDiagramStreamFailure.Provider -> AiProbeFailure.ProviderError
                        AiDiagramStreamFailure.Unsupported -> AiProbeFailure.UnsupportedMode
                        AiDiagramStreamFailure.Incomplete, AiDiagramStreamFailure.EmptyResponse -> AiProbeFailure.Incomplete
                        AiDiagramStreamFailure.Transport -> AiProbeFailure.Network
                    },
                )
            }
            val reply = completed ?: stop(AiDiagramLoopFailure.Stream, AiProbeFailure.Incomplete)
            currentScope()
            if (reply.calls.isEmpty()) {
                guard()
                currentCoroutineContext().ensureActive()
                val result = AiDiagramLoopResult.Ready(reply.text, engine.proposal(), engine.localReferences())
                record(AiProbeDiagnostic(AiProbeStage.Completed))
                // Diagnostic callback can itself close/revoke the task. Never publish afterward.
                guard()
                currentCoroutineContext().ensureActive()
                return result
            }
            // Budget is checked before executing any call in this turn. No unfinished ninth
            // round or partially applied oversized turn survives a failed task.
            totalCalls += reply.calls.size
            totalArgumentBytes +=
                reply.calls.sumOf {
                    it.arguments
                        .encodeToByteArray()
                        .size
                        .toLong()
                }
            if (round == 8 || totalCalls > 64 || totalArgumentBytes > 1024 * 1024) {
                stop(AiDiagramLoopFailure.Limit, AiProbeFailure.OutputLimit)
            }
            val results =
                reply.calls.map { call ->
                    guard()
                    currentCoroutineContext().ensureActive()
                    engine.execute(call).also { result ->
                        if (result.failure in
                            setOf(
                                AiDiagramToolFailure.DuplicateCall,
                                AiDiagramToolFailure.Closed,
                                AiDiagramToolFailure.Scope,
                                AiDiagramToolFailure.UnknownTool,
                                AiDiagramToolFailure.Internal,
                            )
                        ) {
                            stop(AiDiagramLoopFailure.ToolProtocol, AiProbeFailure.UnsupportedMode)
                        }
                    }
                }
            guard()
            currentCoroutineContext().ensureActive()
            history += AiDiagramToolTurn(reply.text, reply.calls.toList(), results.toList())
            progress(AiDiagramLoopProgress.Tools(round, results.toList()))
            guard()
        }
        return AiDiagramLoopResult.Failed(AiDiagramLoopFailure.Limit)
    } catch (cancelled: CancellationException) {
        try {
            onDiagnostic(round, AiProbeDiagnostic(AiProbeStage.Cancelled))
        } catch (_: Exception) {
        }
        throw cancelled
    } catch (failure: Exception) {
        observerFailure?.let { throw it }
        val code =
            when (failure) {
                is DiagramLoopStopped -> failure.code
                is AiRequestValidationException, is IllegalArgumentException -> AiDiagramLoopFailure.Configuration
                else -> AiDiagramLoopFailure.Preparation
            }
        record(
            AiProbeDiagnostic(
                AiProbeStage.Failed,
                when {
                    failure is AiProbePreflightException -> failure.failure
                    code == AiDiagramLoopFailure.Configuration -> AiProbeFailure.Configuration
                    else -> AiProbeFailure.Preparation
                },
                httpStatus = (failure as? AiProbePreflightException)?.status,
            ),
        )
        return AiDiagramLoopResult.Failed(code)
    } finally {
        draft?.close()
        client.close()
    }
}
