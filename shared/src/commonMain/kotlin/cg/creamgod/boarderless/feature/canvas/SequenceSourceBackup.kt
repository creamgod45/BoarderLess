package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.parseStrictJsonObject
import kotlinx.serialization.json.*

/** Authoring source only, never an operation, receipt or already-published diagram. */
internal object SequenceSourceBackupCodec {
    const val MaximumSourceBytes = 64 * 1024
    private const val MaximumFileBytes = 512 * 1024
    fun acceptsSource(source: String): Boolean = source.length <= MaximumSourceBytes && runCatching {
        source.encodeToByteArray(throwOnInvalidSequence = true).size <= MaximumSourceBytes
    }.getOrDefault(false)
    fun encode(source: String): String {
        require(acceptsSource(source))
        return buildJsonObject {
            put("schema", "boarderless.sequence-source.v1")
            put("version", 1)
            put("source", source)
        }.toString().also { require(it.encodeToByteArray(throwOnInvalidSequence = true).size <= MaximumFileBytes) }
    }
    fun decode(raw: String): String {
        require(raw.length <= MaximumFileBytes && raw.encodeToByteArray(throwOnInvalidSequence = true).size <= MaximumFileBytes)
        val json = parseStrictJsonObject(raw)
        require(json.keys == setOf("schema", "version", "source"))
        require(json["schema"] == JsonPrimitive("boarderless.sequence-source.v1") && json["version"] == JsonPrimitive(1))
        val value = requireNotNull(json["source"] as? JsonPrimitive).also { require(it.isString) }.content
        require(acceptsSource(value))
        return value
    }
}
