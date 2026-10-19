package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.TextNode

internal fun commitNewNodeDraft(
    draft: TextNode,
    text: String,
): TextNode? = text.takeIf(String::isNotBlank)?.let { draft.copy(text = it) }
