package cg.creamgod.boarderless.data.ai

/** JSON deltas can split a UTF-16 surrogate pair. Hold a trailing high surrogate until its
 * partner arrives; never display a replacement character or silently repair malformed data. */
internal class AiUtf8TextBuffer(private val maximumBytes: Int = 128 * 1024) {
    private val value = StringBuilder()
    private var pendingHigh: Char? = null
    var reservedBytes: Int = 0
        private set

    init { require(maximumBytes in 1..1024 * 1024) }

    fun append(delta: String): String {
        appendDelta(delta)
        return value.toString()
    }

    /** Return only newly complete characters, without rescanning/copying previous text.
     * Validate the entire fragment before committing any state; rejected fragments leave
     * the original text, pending pair and reserved byte count unchanged. */
    fun appendDelta(delta: String): String {
        if (delta.isEmpty()) return ""
        val previousPending = pendingHigh
        if (delta.length > maximumBytes || value.length.toLong() + (if (previousPending == null) 0 else 1) + delta.length > maximumBytes) {
            throw AiTextLimitException()
        }
        var index = 0
        var bytes = reservedBytes
        var nextPending: Char? = null
        if (previousPending != null) {
            if (!delta[0].isLowSurrogate()) throw AiTextEncodingException()
            index = 1 // Its four bytes were already reserved in the previous fragment.
        }
        while (index < delta.length) {
            val character = delta[index]
            when {
                character.isHighSurrogate() -> {
                    bytes += 4 // Reserve the entire code point even while its pair is pending.
                    if (index + 1 == delta.length) { nextPending = character; index++ }
                    else {
                        if (!delta[index + 1].isLowSurrogate()) throw AiTextEncodingException()
                        index += 2
                    }
                }
                character.isLowSurrogate() -> throw AiTextEncodingException()
                else -> {
                    bytes += when {
                        character.code < 0x80 -> 1
                        character.code < 0x800 -> 2
                        else -> 3
                    }
                    index++
                }
            }
            if (bytes > maximumBytes) throw AiTextLimitException()
        }
        val published = buildString {
            previousPending?.let { append(it) }
            append(delta, 0, delta.length - (if (nextPending == null) 0 else 1))
        }
        value.append(published)
        pendingHigh = nextPending
        reservedBytes = bytes
        return published
    }

    fun finish(): String {
        if (pendingHigh != null) throw AiTextEncodingException()
        return value.toString()
    }

    fun clear() {
        value.clear()
        pendingHigh = null
        reservedBytes = 0
    }
}

internal class AiTextLimitException : IllegalStateException("AI text limit")
internal class AiTextEncodingException : IllegalStateException("Invalid AI text encoding")

internal fun aiTextWithinUtf8Limit(text: String, maximumBytes: Int): Boolean =
    text.length <= maximumBytes && runCatching {
        text.encodeToByteArray(throwOnInvalidSequence = true).size <= maximumBytes
    }.getOrDefault(false)
