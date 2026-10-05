import { describe, expect, test } from 'bun:test'
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { Type } from 'typebox'
import { Value } from 'typebox/value'
import { AssetSchema, TransferDirectiveSchema } from '../../src/schemas/asset.schema.ts'
import { ErrorEnvelope } from '../../src/schemas/common.schema.ts'
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
