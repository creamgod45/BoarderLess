package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PersistentLocalWorkspaceRepositoryTest {
    private class Records : DurableLocalRecordStore {
        var value: DurableLocalRecord? = null
        var failAfterPublication = false
        var confirmations = 0
        override fun read(key: String) = value?.let { it.copy(payload = it.payload.copyOf()) }
        override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation)
            value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
            if (failAfterPublication) { failAfterPublication = false; error("Unknown publication") }
        }
        override fun confirmDurable(key: String, generation: Long) {
            check(value?.generation == generation)
            confirmations++
        }
    }
    private val lock = object : LocalDocumentLock { override fun <T> withLock(block: () -> T): T = block() }
    private fun repository(records: Records) = PersistentLocalWorkspaceRepository({ records }, lock, { "2026-10-09T00:00:00Z" })
    private val node = TextNode(CanvasObjectId("n"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 60f)), text = "離線🙂")
    private val operation = CreateObjectsOperation("create", listOf(node))

    @Test fun unknownPublicationReopensAndReconcilesSameReceiptWithoutAnotherGeneration() = runTest {
        val records = Records()
        val first = repository(records)
        val opened = first.openOrCreateWorkspace()
        records.failAfterPublication = true
        assertFails { first.retainDraft(opened, opened.workspace, operation) }
        val published = assertNotNull(records.value).copy(payload = records.value!!.payload.copyOf())
        val reopened = repository(records)
        val restored = reopened.openOrCreateWorkspace()
        assertEquals("離線🙂", (restored.workspace.objects.getValue(node.id) as TextNode).text)
        reopened.retainDraft(opened, opened.workspace, operation)
        assertEquals(SubmitOutcome.Accepted(1, 0), reopened.submit(opened, operation))
        assertEquals(published.generation, records.value!!.generation)
        assertContentEquals(published.payload, records.value!!.payload)
        assertEquals(2, records.confirmations)
        val altered = operation.copy(objects = listOf(node.copy(text = "different")))
        assertFails { reopened.retainDraft(opened, opened.workspace, altered) }
        assertFails { reopened.submit(opened, altered) }
        assertContentEquals(published.payload, records.value!!.payload)
    }

    @Test fun staleWorkspaceAndCorruptUtf8CannotReplacePreviouslySavedDocument() = runTest {
        val records = Records()
        val repo = repository(records)
        val opened = repo.openOrCreateWorkspace()
        repo.retainDraft(opened, opened.workspace, operation)
        val published = records.value!!.copy(payload = records.value!!.payload.copyOf())
        assertFails { repo.retainDraft(opened, opened.workspace, operation.copy(operationId = "stale", objects = listOf(node.copy(id = CanvasObjectId("other"))))) }
        assertContentEquals(published.payload, records.value!!.payload)
        records.value = DurableLocalRecord(published.generation, byteArrayOf(0xc3.toByte(), 0x28))
        assertFails { repository(records).openOrCreateWorkspace() }
        assertContentEquals(byteArrayOf(0xc3.toByte(), 0x28), records.value!!.payload)
        records.value = published
        assertEquals("離線🙂", (repository(records).openOrCreateWorkspace().workspace.objects.getValue(node.id) as TextNode).text)
    }
}
