package cg.creamgod.boarderless.feature.canvas

import kotlinx.serialization.json.*

/** Established diagram syntax is the AI-facing expression; original UUIDs stay in the APP. */
internal data class AiFlowchart(
    val source: String,
    val inventory: JsonObject,
)

internal fun aiFlowchart(scene: JsonObject): AiFlowchart {
    val nodes = scene.getValue("nodes").jsonArray.map { it.jsonObject }
    val edges = scene.getValue("relations").jsonArray.map { it.jsonObject }
    val names = nodes.mapIndexed { index, node -> node.getValue("id").jsonPrimitive.content to "N${index + 1}" }.toMap()

    fun mapped(id: JsonElement): JsonPrimitive = JsonPrimitive(names.getValue(id.jsonPrimitive.content))

    // Numeric entity encoding prevents node text from closing labels or injecting Mermaid statements.
    fun text(value: String): String =
        buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index++]
                if (char.code in 0xD800..0xDBFF && index < value.length && value[index].code in 0xDC00..0xDFFF) {
                    val codePoint = 0x10000 + ((char.code - 0xD800) shl 10) + (value[index++].code - 0xDC00)
                    append("#$codePoint;")
                } else if (char.isLetterOrDigit() || char in " :+/-.,()_=") {
                    append(char)
                } else {
                    append("#${char.code};")
                }
            }
        }
    val children = nodes.groupBy { it["parentId"] }
    val visited = mutableSetOf<String>()
    val source =
        buildString {
            appendLine("flowchart LR")

            fun emit(
                node: JsonObject,
                depth: Int,
            ) {
                val original = node.getValue("id").jsonPrimitive.content
                check(visited.add(original)) { "Invalid group hierarchy" }
                val id = names.getValue(original)
                val indent = "  ".repeat(depth)
                val kind = node.getValue("kind").jsonPrimitive.content
                // Mermaid rejects an empty quoted title; use a blank entity, not invented content.
                val label = text(node.getValue("text").jsonPrimitive.content).ifEmpty { "#160;" }
                if (kind == "group") {
                    appendLine("${indent}subgraph $id[\"$label\"]")
                    children[node.getValue("id")].orEmpty().forEach { emit(it, depth + 1) }
                    appendLine("${indent}end")
                } else {
                    val display = if (kind == "media") "${node.getValue("mediaKind").jsonPrimitive.content}: $label" else label
                    val shape = node["shape"]?.jsonPrimitive?.content
                    val expression =
                        when (shape) {
                            "diamond" -> "$id{\"$display\"}"
                            "ellipse" -> "$id((\"$display\"))"
                            "rounded" -> "$id(\"$display\")"
                            else -> "$id[\"$display\"]"
                        }
                    appendLine("$indent$expression")
                    check(children[node.getValue("id")].isNullOrEmpty()) { "Parent is not a group" }
                }
            }
            children[JsonNull].orEmpty().forEach { emit(it, 1) }
            check(visited.size == nodes.size) { "Invalid group hierarchy" }
            edges.forEachIndexed { index, edge ->
                val a = mapped(edge.getValue("source")).content
                val b = mapped(edge.getValue("target")).content
                val direction = edge.getValue("direction").jsonPrimitive.content
                val label =
                    edge["label"]
                        ?.takeUnless { it == JsonNull }
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                val intent =
                    edge["intent"]
                        ?.takeUnless { it == JsonNull }
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                val display =
                    text(
                        "E${index + 1}" + (if (label.isNotEmpty()) ": $label" else "") +
                            (if (intent.isNotEmpty() && intent != "relates") " (intent: $intent)" else ""),
                    )
                val arrow =
                    when (direction) {
                        "forward", "backward" -> "-->"
                        "both" -> "<-->"
                        "none" -> "---"
                        else -> error("Unknown direction")
                    }
                val start = if (direction == "backward") b else a
                val end = if (direction == "backward") a else b
                appendLine("  $start $arrow|\"$display\"| $end")
            }
        }
    val inventory =
        buildJsonObject {
            put("schema", "boarderless.ai-flowchart.v1")
            put("scope", scene.getValue("scope"))
            put(
                "nodes",
                JsonArray(
                    nodes.map { node ->
                        buildJsonObject {
                            put("id", mapped(node.getValue("id")))
                            put("kind", node.getValue("kind"))
                            put("parentId", node["parentId"]?.takeUnless { it == JsonNull }?.let(::mapped) ?: JsonNull)
                            put("parentOutsideScope", node.getValue("parentOutsideScope"))
                        }
                    },
                ),
            )
            put(
                "relations",
                JsonArray(
                    edges.mapIndexed { index, edge ->
                        buildJsonObject {
                            put("id", "E${index + 1}")
                            put("source", mapped(edge.getValue("source")))
                            put("target", mapped(edge.getValue("target")))
                            put("direction", edge.getValue("direction"))
                        }
                    },
                ),
            )
        }
    return AiFlowchart(source, inventory)
}
