package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings

internal fun <T> selectionPropertySummary(
    values: Collection<T>,
    emptyLabel: String = Strings.common.notAvailable(),
    valueLabel: (T) -> String,
): String {
    val distinct = values.distinct()
    return when (distinct.size) {
        0 -> emptyLabel
        1 -> valueLabel(distinct.single())
        else -> Strings.common.mixed()
    }
}
