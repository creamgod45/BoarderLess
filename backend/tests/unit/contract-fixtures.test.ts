import { describe, expect, test } from 'bun:test'
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { Type } from 'typebox'
import { Value } from 'typebox/value'
import { preparationDigest } from '../../src/domain/asset-preparation.ts'
import { PreparationEnvelopeSchema } from '../../src/schemas/asset-preparation.schema.ts'
import { capabilitiesSchema } from '../../src/schemas/capabilities.schema.ts'
import { RealtimeCapabilitiesSchema, RealtimeTicketSchema } from '../../src/schemas/realtime.schema.ts'
import { QuickSchemeCapabilitiesSchema, QuickSchemeListSchema, QuickSchemeResourceSchema } from '../../src/schemas/quick-scheme.schema.ts'
import { validateSelectionPayload } from '../../src/domain/selection.ts'
import { CLIENT_MESSAGE_SCHEMAS, SERVER_EVENT_SCHEMAS } from '../../src/realtime/messages.ts'
import { AssetSchema, TransferDirectiveSchema } from '../../src/schemas/asset.schema.ts'
import { validateObjectState } from '../../src/domain/canvas-objects.ts'
import { ErrorEnvelope } from '../../src/schemas/common.schema.ts'
import { CommittedResponse, ConflictResponse, ReceiptResponse } from '../../src/schemas/operation.schema.ts'
import { SnapshotContentSchema } from '../../src/schemas/snapshot.schema.ts'
import { mediaFixtures } from '../helpers.ts'

/** 錄製的 contract fixtures 必須符合目前的 response schema，避免文件與實作分歧 */
const DIR = join(import.meta.dirname, '../fixtures/contract/media-v1')
const files = (await readdir(DIR)).filter((f) => /^\d+-.*\.json$/.test(f))

const Enveloped = Type.Object({ asset: AssetSchema })
const Prepared = Type.Object({ asset: AssetSchema, upload: TransferDirectiveSchema })
const Content = Type.Object({ asset: AssetSchema, download: TransferDirectiveSchema })
const List = Type.Object({ assets: Type.Array(AssetSchema) })

function schemaFor(status: number, body: Record<string, unknown>) {
  if (status >= 400) return ErrorEnvelope
  if ('upload' in body) return Prepared
  if ('download' in body) return Content
  if ('assets' in body) return List
  if ('asset' in body) return Enveloped
  return AssetSchema
}

describe('Media v1 contract fixtures', () => {
  test('fixtures exist', () => {
    expect(files.length).toBeGreaterThanOrEqual(20)
  })

  for (const file of files) {
    test(file, async () => {
      const doc = JSON.parse(await readFile(join(DIR, file), 'utf8'))
      const { status, body } = doc.response
      if (body === undefined) return expect([200, 204]).toContain(status)
      const schema = schemaFor(status, body)
      expect([...Value.Errors(schema, body)].map((e) => `${e.instancePath} ${e.message}`)).toEqual([])
      // 簽名 URL 一律已正規化，不得洩漏真實簽名
      expect(JSON.stringify(doc)).not.toMatch(/sig=|X-Amz-Signature|localhost|storage\.test/)
    })
  }

  test('original fixtures carry the real binary SHA-256 from the media manifest', async () => {
    const ready = JSON.parse(await readFile(join(DIR, '06-get-ready.json'), 'utf8')).response.body
    const png = mediaFixtures.find((f) => f.file === 'image.png')!
    expect(ready).toMatchObject({ checksum: png.checksum, byteSize: png.byteSize, width: png.width, height: png.height })
  })
})

// ---- Canvas v1（BAI-001 / 007 / 008 / 011 / 012）----
const CANVAS_DIR = join(import.meta.dirname, '../fixtures/contract/canvas-v1')
const canvasFiles = (await readdir(CANVAS_DIR)).filter((f) => /^\d+-.*\.json$/.test(f))

function canvasSchemaFor(path: string, status: number, body: Record<string, unknown>) {
  if (status === 409 && body.status === 'conflict') return ConflictResponse
  if (status >= 400) return ErrorEnvelope
  if (path.endsWith('/state')) return SnapshotContentSchema
  if (path.endsWith('/receipts') || path.endsWith('/fences')) return ReceiptResponse
  return CommittedResponse
}

describe('Canvas v1 contract fixtures', () => {
  test('fixtures exist', () => {
    expect(canvasFiles.length).toBeGreaterThanOrEqual(30)
  })

  for (const file of canvasFiles) {
    test(file, async () => {
      const doc = JSON.parse(await readFile(join(CANVAS_DIR, file), 'utf8'))
      const { status, body } = doc.response
      const schema = canvasSchemaFor(doc.request.path, status, body)
      expect([...Value.Errors(schema, body)].map((e) => `${e.instancePath} ${e.message}`)).toEqual([])
      // 檔名標示的狀態碼與錄製結果一致（避免行為變更後 fixture 名稱誤導）
      const expected = /-(\d{3})$/.exec(file.replace('.json', ''))?.[1]
      if (expected) expect(String(status)).toBe(expected)
      if (file.includes('-accepted')) expect(body.status).toBe('accepted')
    })
  }

  test('every object in the recorded projection satisfies the typed schema', async () => {
    const state = JSON.parse(await readFile(join(CANVAS_DIR, '22-state-after-restore.json'), 'utf8')).response.body
    expect(state.objects.length).toBeGreaterThan(0)
    for (const obj of state.objects) {
      expect(validateObjectState(obj.objectType, obj.transform, obj.properties)).toEqual({ ok: true })
    }
  })
})

// ---- Asset preparation v2 ----
const PREP_DIR = join(import.meta.dirname, '../fixtures/contract/asset-preparation-v2')
const prepFiles = (await readdir(PREP_DIR)).filter((f) => /^\d+-.*\.json$/.test(f))

describe('Asset preparation v2 contract fixtures', () => {
  test('fixtures exist', () => {
    expect(prepFiles.length).toBeGreaterThanOrEqual(30)
  })

  for (const file of prepFiles) {
    test(file, async () => {
      const doc = JSON.parse(await readFile(join(PREP_DIR, file), 'utf8'))
      expect(JSON.stringify(doc)).not.toMatch(/sig=|X-Amz-Signature|localhost|storage\.test/)
      if (!doc.request?.path) return // signed PUT 說明型 fixture
      const { status, body } = doc.response
      const schema = status >= 400 ? ErrorEnvelope : doc.request.path.endsWith('/capabilities') ? capabilitiesSchema.response[200] : PreparationEnvelopeSchema
      expect([...Value.Errors(schema, body)].map((e) => `${e.instancePath} ${e.message}`)).toEqual([])
      const expected = /-(\d{3})$/.exec(file.replace('.json', ''))?.[1]
      if (expected) expect(String(status)).toBe(expected)
      // 成功的 prepare：digest 由伺服器依 canonical metadata 計算，與 request body 一致
      if (status < 400 && doc.request.body?.metadata) {
        expect(body.requestDigest).toBe(preparationDigest(doc.request.body.metadata))
        expect(body.requestId).toBe(doc.request.body.requestId)
      }
      // declared 永遠等於 digest 涵蓋的 metadata（ASSET-08）
      if (body?.declared) expect(preparationDigest(body.declared)).toBe(body.requestDigest)
      // upload directive 只出現在 ticket 回應
      if (body?.upload) expect(doc.request.path).toEndWith('/upload-tickets')
    })
  }

  test('ASSET-08: declared stays null while verified carries parsed values; mismatch is rejected', async () => {
    const ready = JSON.parse(await readFile(join(PREP_DIR, '36-receipt-ready-declared-null.json'), 'utf8')).response.body
    expect(ready.declared).toMatchObject({ width: null, height: null, durationMs: null })
    expect(ready.verified).toEqual({ width: 64, height: 48, durationMs: null })
    const rejected = JSON.parse(await readFile(join(PREP_DIR, '37-receipt-rejected-declared-mismatch.json'), 'utf8')).response.body
    expect(rejected).toMatchObject({ phase: 'rejected', declared: { width: 32, height: 24 }, verified: { width: 64, height: 48 } })
    expect(rejected.asset.rejectionReason).toBe('declared_metadata_mismatch')
  })

  test('golden vector fixture carries the cross-platform digest', async () => {
    const doc = JSON.parse(await readFile(join(PREP_DIR, '04-prepare-golden-vector-201.json'), 'utf8'))
    expect(doc.response.body.requestDigest).toBe('32d5f0d51e3a30dc0c7057664dc28914132cf09fe6e109098725c05d26f602ee')
  })
})

// ---- Realtime v1（batch A）----
const RT_DIR = join(import.meta.dirname, '../fixtures/contract/realtime-v1')
const rtFiles = (await readdir(RT_DIR)).filter((f) => /^\d+-.*\.json$/.test(f))

describe('Realtime v1 contract fixtures', () => {
  test('fixtures exist', () => {
    expect(rtFiles.length).toBeGreaterThanOrEqual(30)
  })

  for (const file of rtFiles) {
    test(file, async () => {
      const raw = await readFile(join(RT_DIR, file), 'utf8')
      // 不洩漏真實 host、ticket、簽名
      expect(raw).not.toMatch(/127\.0\.0\.1|localhost|sig=|X-Amz-Signature/)
      const doc = JSON.parse(raw)
      if (doc.request) {
        const { status, body } = doc.response
        const schema = status >= 400 ? ErrorEnvelope : doc.request.path.endsWith('/realtime-ticket') ? RealtimeTicketSchema : RealtimeCapabilitiesSchema
        expect([...Value.Errors(schema, body)].map((e) => `${e.instancePath} ${e.message}`)).toEqual([])
        if (body?.ticket) expect(body.ticket).toBe('fixture-only-not-a-real-ticket')
        return
      }
      for (const { direction, frame } of doc.exchange ?? []) {
        if (typeof frame === 'string') continue
        if (direction === 'server') {
          const schema = SERVER_EVENT_SCHEMAS[frame.type]
          expect(schema).toBeDefined()
          expect([...Value.Errors(schema!, frame)].map((e) => `${frame.type}${e.instancePath} ${e.message}`)).toEqual([])
        } else if (frame.type in CLIENT_MESSAGE_SCHEMAS) {
          const schema = CLIENT_MESSAGE_SCHEMAS[frame.type as keyof typeof CLIENT_MESSAGE_SCHEMAS]
          // 刻意送出的不合法 frame 由對應 protocol_error 標示
          if (file.includes('unsupported') || file.includes('unknown')) continue
          expect([...Value.Errors(schema, frame)].map((e) => `${frame.type}${e.instancePath} ${e.message}`)).toEqual([])
        }
      }
      for (const c of doc.cases ?? []) for (const f of c.frames) expect([...Value.Errors(SERVER_EVENT_SCHEMAS[f.type]!, f)]).toEqual([])
    })
  }

  test('committed transactions are complete and contiguous', async () => {
    const doc = JSON.parse(await readFile(join(RT_DIR, '33-fan-out-200-operations.json'), 'utf8'))
    const tx = doc.exchange.find((e: any) => e.frame.type === 'committed_transaction').frame
    expect(tx.operations).toHaveLength(200)
    expect(tx.toServerSeq - tx.fromServerSeq + 1).toBe(200)
    tx.operations.forEach((o: any, i: number) => {
      expect(o.serverSeq).toBe(tx.fromServerSeq + i)
      expect(o.transactionId).toBe(tx.transactionId)
    })
  })
})

// ---- Quick scheme v2（batch B）----
const QS_DIR = join(import.meta.dirname, '../fixtures/contract/quick-scheme-v2')
const qsFiles = (await readdir(QS_DIR)).filter((f) => /^\d+-.*\.json$/.test(f))

describe('Quick scheme v2 contract fixtures', () => {
  test('fixtures exist', () => {
    expect(qsFiles.length).toBeGreaterThanOrEqual(40)
  })

  for (const file of qsFiles) {
    test(file, async () => {
      const doc = JSON.parse(await readFile(join(QS_DIR, file), 'utf8'))
      const { status, body } = doc.response
      const expected = /-(\d{3})$/.exec(file.replace('.json', ''))?.[1]
      if (expected) expect(String(status)).toBe(expected)
      if (status === 204 || status === 304) return expect(body).toBeUndefined()
      const schema = status >= 400 ? ErrorEnvelope : doc.request.path.includes('capabilities') ? QuickSchemeCapabilitiesSchema : doc.request.method === 'GET' && !/quick-schemes\/[0-9a-f-]{36}$/.test(doc.request.path) ? QuickSchemeListSchema : QuickSchemeResourceSchema
      expect([...Value.Errors(schema, body)].map((e) => `${e.instancePath} ${e.message}`)).toEqual([])
      // 成功保存的 payload 都是合法 selection，ETag header 與 body 一致
      if (status < 300 && body.payload) {
        expect(validateSelectionPayload(body.payload)).toEqual({ ok: true })
        expect(doc.response.headers.etag).toBe(body.etag)
      }
      // 請求的 payload 原樣回傳（round-trip 不丟欄位）
      if ((status === 201 || status === 200) && doc.request.body?.payload) expect(body.payload).toEqual(doc.request.body.payload)
    })
  }
})
