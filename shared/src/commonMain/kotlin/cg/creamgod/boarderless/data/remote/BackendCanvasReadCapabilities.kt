package cg.creamgod.boarderless.data.remote

import kotlinx.serialization.json.*

/** Read negotiation plus explicit writer protocol gates. Repository getters enable only
 * writers whose DTO/ACK/replay paths have been wired, not every advertised feature. */
internal data class BackendCanvasReadCapabilities(
    val canvasStyle: Boolean = false,
    val geometry: Boolean = false,
    val vectorPath: Boolean = false,
    val textPlacement: Boolean = false,
    val sequenceDiagrams: Boolean = false,
    val canvasStyleWrite: Boolean = false,
    val canWriteCanvas: Boolean = false,
    val sequenceWrite: Boolean = false,
    val mediaReferenceRestore: Boolean = false,
    /** Shape tokens the backend accepts. Missing or empty means only the original 25 may be written. */
    val nodeShapes: Set<String> = emptySet(),
)

internal fun readCanvasCapabilities(raw: JsonObject): BackendCanvasReadCapabilities {
    fun supported(name: String): JsonObject? {
        val value = raw[name] ?: return null
        val objectValue = value as? JsonObject ?: throw BackendContractException("Invalid canvas capability")
        val flag = (objectValue["supported"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
            ?: throw BackendContractException("Invalid canvas capability support flag")
        return objectValue.takeIf { flag }
    }
    fun JsonObject.versions(key: String) = (get(key) as? JsonArray)?.map {
        val primitive = it as? JsonPrimitive ?: throw BackendContractException("Invalid schema versions")
        if (primitive.isString) throw BackendContractException("Invalid schema version type")
        primitive.intOrNull ?: throw BackendContractException("Invalid schema version")
    } ?: throw BackendContractException("Missing schema versions")
    fun JsonObject.token(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    val style = supported("canvasStyle")?.let { 1 in it.versions("styleSchemaVersions") } ?: false
    val geometry = supported("relationGeometry")?.let { it.token("schema") == "boarderless.relation-geometry.v1" } ?: false
    val vector = supported("vectorPath")?.let {
        it.token("objectType") == "text" && it.token("property") == "vectorPath" && 1 in it.versions("schemaVersions")
    } ?: false
    val placement = supported("textPlacement")?.let { it.token("objectType") == "text" && it.token("property") == "textPlacement" } ?: false
    val sequence = supported("sequenceDiagrams")?.let { it.token("schema") == "boarderless.sequence-canvas.v1" } ?: false
    val writeProtocol = (raw["canvas"] as? JsonObject)?.get("minWritableProtocolVersion") as? JsonPrimitive
    val styleKinds = supported("canvasStyle")?.get("operationKinds") as? JsonArray
    val styleWrite = style && writeProtocol?.isString == false && writeProtocol?.intOrNull == 1 &&
        styleKinds?.any { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content == "update_canvas_style" } == true
    val canWrite = writeProtocol?.isString == false && writeProtocol?.intOrNull == 1
    val sequenceKinds = supported("sequenceDiagrams")?.get("operationKinds") as? JsonArray
    val sequenceWrite = sequence && canWrite && sequenceKinds?.any {
        (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content == "update_sequence_diagrams"
    } == true
    val referenceRestore = supported("mediaReferenceRestore")?.let {
        (it["requiresProvenance"] as? JsonPrimitive)?.takeUnless { value -> value.isString }?.booleanOrNull == true &&
            (it["operationKinds"] as? JsonArray)?.any { value ->
                (value as? JsonPrimitive)?.takeIf { token -> token.isString }?.content == "restore_media_references"
            } == true
    } ?: false
    val shapes = (raw["nodeShapes"] as? JsonArray)?.map {
        (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: throw BackendContractException("Invalid node shape capability")
    }?.toSet().orEmpty()
    return BackendCanvasReadCapabilities(style, geometry, vector, placement, sequence, styleWrite, canWrite, sequenceWrite, referenceRestore, shapes)
}
