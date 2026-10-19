import { describe, expect, test } from 'bun:test'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { declaredMatchesVerified, durationWithinTolerance } from '../../src/domain/asset-preparation.ts'
import { isQuarterTurnOrientation, isQuarterTurnRotation, readExifOrientation } from '../../src/media/orientation.ts'
import { jpegWithOrientation, pngWithOrientation, webpWithOrientation } from '../support/exif.ts'
import { readFixture } from '../helpers.ts'

async function withFile<T>(bytes: Uint8Array, fn: (path: string) => Promise<T>): Promise<T> {
  const dir = await mkdtemp(join(tmpdir(), 'orientation-'))
  try {
    const path = join(dir, 'f')
    await writeFile(path, bytes)
    return await fn(path)
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
}

describe('EXIF orientation', () => {
  test('JPEG APP1, both byte orders', async () => {
    const jpg = await readFixture('image.jpg')
    expect(await withFile(jpegWithOrientation(jpg, 6), (p) => readExifOrientation(p, 'image/jpeg'))).toBe(6)
    expect(await withFile(jpegWithOrientation(jpg, 8, false), (p) => readExifOrientation(p, 'image/jpeg'))).toBe(8)
    expect(await withFile(jpg, (p) => readExifOrientation(p, 'image/jpeg'))).toBeNull()
  })

  test('PNG eXIf and WebP EXIF chunk', async () => {
    expect(await withFile(pngWithOrientation(await readFixture('image.png'), 5), (p) => readExifOrientation(p, 'image/png'))).toBe(5)
    expect(await withFile(webpWithOrientation(await readFixture('image.webp'), 7), (p) => readExifOrientation(p, 'image/webp'))).toBe(7)
    expect(await withFile(await readFixture('image.webp'), (p) => readExifOrientation(p, 'image/webp'))).toBeNull()
  })

  test('formats without EXIF and truncated input return null', async () => {
    expect(await withFile(await readFixture('animation.gif'), (p) => readExifOrientation(p, 'image/gif'))).toBeNull()
    const truncated = jpegWithOrientation(await readFixture('image.jpg'), 6).subarray(0, 20)
    expect(await withFile(truncated, (p) => readExifOrientation(p, 'image/jpeg'))).toBeNull()
  })

  test('quarter turns swap width / height', () => {
    expect([1, 2, 3, 4].map(isQuarterTurnOrientation)).toEqual([false, false, false, false])
    expect([5, 6, 7, 8].map(isQuarterTurnOrientation)).toEqual([true, true, true, true])
    expect([90, -90, 270, 180, 0, null].map(isQuarterTurnRotation)).toEqual([true, true, true, false, false, false])
  })
})

describe('declared vs verified (ASSET-08)', () => {
  const declared = { byteSize: 1, checksum: `sha256:${'0'.repeat(64)}`, mediaType: 'video/mp4' }

  test('duration tolerance: max(100ms, 1%)', () => {
    expect(durationWithinTolerance(1100, 1000)).toBe(true)
    expect(durationWithinTolerance(1101, 1000)).toBe(false)
    expect(durationWithinTolerance(99_000, 100_000)).toBe(true) // 1% = 1000ms
    expect(durationWithinTolerance(98_999, 100_000)).toBe(false)
  })

  test('null is not compared; non-null must match', () => {
    const verified = { width: 64, height: 48, durationMs: 1000 }
    expect(declaredMatchesVerified({ ...declared, width: null, height: null, durationMs: null }, verified)).toBe(true)
    expect(declaredMatchesVerified({ ...declared, width: 64, height: 48, durationMs: 1050 }, verified)).toBe(true)
    expect(declaredMatchesVerified({ ...declared, width: 48, height: null, durationMs: null }, verified)).toBe(false)
    expect(declaredMatchesVerified({ ...declared, width: null, height: 47, durationMs: null }, verified)).toBe(false)
    expect(declaredMatchesVerified({ ...declared, width: null, height: null, durationMs: 1200 }, verified)).toBe(false)
    // 靜態影像宣告了 duration
    expect(declaredMatchesVerified({ ...declared, width: null, height: null, durationMs: 0 }, { ...verified, durationMs: null })).toBe(false)
  })
})
