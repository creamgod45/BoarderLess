package cg.creamgod.boarderless.data

/** Lexical depth/framing guard BEFORE recursive decoders. Not a JSON schema validator.
 * Quoted brackets, escaped quotes and escaped backslashes do not count as structure.
 */
internal fun requireBoundedDraftJsonDepth(content: String) {
    val stack = ArrayDeque<Char>()
    var quoted = false
    var escaped = false
    content.forEach { char ->
        if (quoted) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                quoted = false
            }
        } else {
            when (char) {
                '"' -> {
                    quoted = true
                }

                '{', '[' -> {
                    require(stack.size < 64)
                    stack.addLast(char)
                }

                '}', ']' -> {
                    require(stack.removeLastOrNull() == if (char == '}') '{' else '[')
                }
            }
        }
    }
    require(stack.isEmpty() && !quoted)
}
