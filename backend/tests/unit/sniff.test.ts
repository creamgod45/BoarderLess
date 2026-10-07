import { describe, expect, test } from 'bun:test'
import { sniffMediaType } from '../../src/media/sniff.ts'
import { mediaFixtures, readFixture } from '../helpers.ts'

describe('sniffMediaType', () => {
  for (const fixture of mediaFixtures) {
    test(`detects ${fixture.mediaType} from ${fixture.file}`, async () => {
      expect(sniffMediaType(await readFixture(fixture.file))).toBe(fixture.mediaType as never)
    })
  }

  test('ignores formats outside the allowlist', () => {
    const bytes = (s: string) => new TextEncoder().encode(s)
    expect(sniffMediaType(bytes('<svg xmlns="http://www.w3.org/2000/svg"/>'))).toBeNull()
    expect(sniffMediaType(bytes('\x00\x00\x00\x18ftypheic\x00\x00\x00\x00'))).toBeNull() // HEIC
    expect(sniffMediaType(bytes('\x00\x00\x00\x14ftypqt  \x00\x00\x00\x00'))).toBeNull() // QuickTime
    expect(sniffMediaType(new Uint8Array([0x1a, 0x45, 0xdf, 0xa3, ...bytes('\x42\x82\x88matroska')]))).toBeNull()
    expect(sniffMediaType(new Uint8Array())).toBeNull()
  })
})
