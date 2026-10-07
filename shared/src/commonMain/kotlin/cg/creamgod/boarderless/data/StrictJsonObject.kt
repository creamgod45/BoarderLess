package cg.creamgod.boarderless.data

import kotlinx.serialization.json.*

/** Bounded JSON object parser, rejecting duplicate keys and invalid Unicode before use. */
internal fun parseStrictJsonObject(raw: String): JsonObject {
    requireBoundedDraftJsonDepth(raw)
    val value = Json.parseToJsonElement(raw)
    require(value is JsonObject)

    data class Frame(
        val keys: MutableSet<String>?,
        var key: Boolean,
    )
    val stack = mutableListOf<Frame>()
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '{' -> {
                stack.add(Frame(mutableSetOf(), true))
            }

            '[' -> {
                stack.add(Frame(null, false))
            }

            '}', ']' -> {
                stack.removeAt(stack.lastIndex)
            }

            ',' -> {
                stack.last().let { if (it.keys != null) it.key = true }
            }

            '"' -> {
                val start = i++
                while (i < raw.length && raw[i] != '"') {
                    if (raw[i] == '\\') i++
                    i++
                }
                val frame = stack.last()
                if (frame.keys != null && frame.key) {
                    require(frame.keys.add(Json.decodeFromString<String>(raw.substring(start, i + 1))))
                    frame.key = false
                }
            }
        }
        i++
    }

    // Reject dangling surrogate escapes in decoded strings, rather than transmit replacements.
    fun unicode(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, field) ->
                    key.encodeToByteArray(throwOnInvalidSequence = true)
                    unicode(field)
                }
            }

            is JsonArray -> {
                element.forEach(::unicode)
            }

            is JsonPrimitive -> {
                if (element.isString) element.content.encodeToByteArray(throwOnInvalidSequence = true)
            }
        }
    }
    unicode(value)
    return value as JsonObject
}
