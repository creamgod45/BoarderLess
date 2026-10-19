package cg.creamgod.boarderless

/**
 * A lone high surrogate built at run time. A literal (or a compiler-folded constant) is rewritten to U+FFFD
 * by some JS bundlers when they write the bundle as UTF-8, which silently turns "invalid text" tests into
 * valid-text tests depending on how the bundle happens to be laid out.
 */
internal fun loneSurrogate(): String = (0xD7FF + listOf(1).size).toChar().toString()
