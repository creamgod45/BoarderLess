package cg.creamgod.boarderless.data

import kotlinx.serialization.json.*

/** Preserve declared schema and structured-map uniqueness BEFORE Map decoding can overwrite IDs. */
internal fun requireStrictSequenceCanvasFields(element: JsonElement) {
    when (element) {
        is JsonArray -> {
            element.forEach(::requireStrictSequenceCanvasFields)
        }

        is JsonObject -> {
            if ("sequenceDiagrams" in element) {
                val entries = element.getValue("sequenceDiagrams") as? JsonArray ?: error("Invalid sequence map")
                require(entries.size % 2 == 0 && entries.size <= 128)
                val ids = mutableSetOf<String>()
                for (index in entries.indices step 2) {
                    val key = entries[index] as? JsonObject ?: error("Invalid sequence identity")
                    require(key.keys == setOf("value"))
                    val id =
                        (
                            key.getValue(
                                "value",
                            ) as? JsonPrimitive
                        )?.takeIf { it.isString }?.content ?: error("Invalid sequence identity")
                    require(id.isNotBlank() && ids.add(id))
                    val diagram = entries[index + 1] as? JsonObject ?: error("Invalid sequence metadata")
                    require(diagram["schema"] == JsonPrimitive("boarderless.sequence-canvas.v1"))
                }
            }
            element.values.forEach(::requireStrictSequenceCanvasFields)
        }

        else -> {
            Unit
        }
    }
}
