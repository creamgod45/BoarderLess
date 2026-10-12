package cg.creamgod.boarderless.data.ai

/** Opt-in local trace of tool names, arguments and results to help improve generation quality.
 * Unlike the safe probe log this DOES contain canvas content, so it is only created when the user
 * enables it. It never receives credentials, endpoints or request headers. */
internal interface AiToolTrace {
    fun record(
        type: String,
        request: String,
        round: Int,
        fields: Map<String, String>,
    )
}

/** Desktop only; other platforms return null and nothing is written. */
internal expect fun aiToolTrace(): AiToolTrace?
