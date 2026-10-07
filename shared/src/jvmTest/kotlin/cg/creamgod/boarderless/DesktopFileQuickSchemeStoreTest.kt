package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DesktopFileQuickSchemeStoreTest {
    private val payload =
        ClipboardJson.encodeToString(
            ClipboardPayload.serializer(),
            ClipboardPayload(
                version = 4,
                nodes =
                    listOf(ClipboardNode("source", 0f, 0f, 120f, 80f, 0f, "中文🙂", "paper")),
            ),
        )

    private fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-file-schemes")
        try {
            block(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun nativeRepositorySelectsAtomicLibraryBeforeDocumentOpenAndPreservesIdsAcrossRestart() =
        temporary { root ->
            runTest {
                val repo = DesktopFileWorkspaceRepository(root.resolve("local"))
                val library = assertNotNull(repo.quickSchemeRepository)
                assertTrue(library.list().isEmpty())
                val first = library.save(payload, "方案🙂", 4, "source-workspace")
                val second = library.save(payload, "Second", 4)
                assertEquals(listOf(first, second), DesktopFileWorkspaceRepository(root.resolve("local")).quickSchemeRepository!!.list())
                assertEquals(first.copy(name = "已改名"), library.rename(first.id, " 已改名 "))
                assertTrue(library.delete(first.id))
                assertFalse(library.delete(first.id))
                val reopened = DesktopFileWorkspaceRepository(root.resolve("local")).quickSchemeRepository!!
                val third = reopened.save(payload, schemaVersion = 4)
                assertEquals(3, third.id)
                assertEquals(listOf(second, third), reopened.list())
                assertEquals(third, reopened.latest())
                val session = repo.openOrCreateWorkspace()
                repo.createWorkspace(session, "Another canvas")
                assertEquals(listOf(second, third), repo.quickSchemeRepository!!.list())
            }
        }

    @Test fun saveRenameAndDeleteFaultsLeaveAnOldOrNewCompleteLibraryWithoutIdReuse() {
        for (stage in AtomicDraftStage.entries) {
            for (mutation in listOf("save", "rename", "delete")) {
                temporary { root ->
                    var armed = false
                    val files = DesktopAtomicDraftStore(root) { if (armed && it == stage) error("File checkpoint") }
                    val store = DesktopFileQuickSchemeStore(files)
                    val original = store.save(payload, "Original", 4)
                    armed = true
                    assertFails {
                        when (mutation) {
                            "save" -> store.save(payload, "New", 4)
                            "rename" -> store.rename(original.id, "Renamed")
                            else -> store.delete(original.id)
                        }
                    }
                    val reopened = DesktopFileQuickSchemeStore(root)
                    val expected =
                        if (stage == AtomicDraftStage.DataForced) {
                            listOf(original)
                        } else {
                            when (mutation) {
                                "save" -> listOf(original, original.copy(id = 2, name = "New"))
                                "rename" -> listOf(original.copy(name = "Renamed"))
                                else -> emptyList()
                            }
                        }
                    assertEquals(expected, reopened.list())
                    val next = reopened.save(payload, schemaVersion = 4)
                    assertEquals(if (stage != AtomicDraftStage.DataForced && mutation == "save") 3 else 2, next.id)
                }
            }
        }
    }

    @Test fun corruptUnknownOrIncompleteLibraryIsRejectedWithoutResettingItsFile() =
        temporary { root ->
            val files = DesktopAtomicDraftStore(root)
            val store = DesktopFileQuickSchemeStore(files)
            store.save(payload, "Keep", 4)
            val key = files.recordKeys().single()
            val original = assertNotNull(files.read(key)).payload!!
            val valid = Json.parseToJsonElement(original.decodeToString()).jsonObject
            val invalid =
                listOf(
                    JsonObject(valid + ("version" to JsonPrimitive(2))).toString(),
                    JsonObject(valid - "nextId").toString(),
                    JsonObject(valid + ("nextId" to JsonPrimitive(1))).toString(),
                    "{\"version\":1,\"version\":1,\"nextId\":2,\"items\":[]}",
                )
            for (text in invalid) {
                val before = assertNotNull(files.read(key))
                files.compareAndSet(key, before.generation, text.encodeToByteArray())
                val path = root.resolve("$key.record")
                val bytes = Files.readAllBytes(path)
                val reopened = DesktopFileQuickSchemeStore(root)
                assertFails { reopened.list() }
                assertFails { reopened.save(payload, schemaVersion = 4) }
                assertContentEquals(bytes, Files.readAllBytes(path))
            }
            val before = assertNotNull(files.read(key))
            files.compareAndSet(key, before.generation, original)
            val path = root.resolve("$key.record")
            val corrupt = Files.readAllBytes(path).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            Files.write(path, corrupt)
            assertFails { DesktopFileQuickSchemeStore(root).list() }
            assertContentEquals(corrupt, Files.readAllBytes(path))
        }

    @Test fun invalidOrOversizedDefinitionsNeverAlterPublishedLibrary() =
        temporary { root ->
            val store = DesktopFileQuickSchemeStore(root)
            store.save(payload, "Keep", 4)
            val file = Files.list(root).use { it.filter { path -> path.fileName.toString().endsWith(".record") }.findFirst().get() }
            val before = Files.readAllBytes(file)
            assertFails { store.save("", schemaVersion = 4) }
            assertFails { store.save(payload, schemaVersion = 6) }
            assertFails { store.save(payload, sourceWorkspaceId = "") }
            assertFails { store.save("x".repeat(1024 * 1024 + 1), schemaVersion = 4) }
            assertFails { store.rename(1, "中".repeat(2000)) }
            assertFails { store.save(payload, "\uD800", 4) }
            assertContentEquals(before, Files.readAllBytes(file))
        }

    @Test fun schemeFileDefinitionTransfersToAnotherLibraryWithFreshLocalIdAndNoCanvasMutation() =
        temporary { root ->
            runTest {
                val source = DesktopFileWorkspaceRepository(root.resolve("source"))
                val sourceSession = source.openOrCreateWorkspace()
                val selected = source.quickSchemeRepository!!.save(payload, "可交付🙂", 4)
                val file = root.resolve("scheme.json")
                Files.writeString(file, QuickSchemeTransferCodec.encode(selected))
                val target = DesktopFileWorkspaceRepository(root.resolve("target"))
                val targetSession = target.openOrCreateWorkspace()
                val library = target.quickSchemeRepository!!
                library.save(payload, "Existing", 4)
                val reviewed = QuickSchemeTransferCodec.decode(Files.readString(file))
                assertEquals(0, reviewed.id)
                assertEquals(1, library.list().size) // Reading/reviewing does not import or change the canvas.
                val imported = library.save(reviewed.payload, reviewed.name, reviewed.schemaVersion, reviewed.sourceWorkspaceId)
                assertEquals(2, imported.id)
                assertEquals(selected.name, imported.name)
                assertEquals(decodeQuickSchemePayload(selected), decodeQuickSchemePayload(imported))
                assertEquals(sourceSession, source.refresh(sourceSession))
                assertEquals(targetSession, target.refresh(targetSession))
                assertEquals(imported, DesktopFileWorkspaceRepository(root.resolve("target")).quickSchemeRepository!!.latest())
            }
        }
}
