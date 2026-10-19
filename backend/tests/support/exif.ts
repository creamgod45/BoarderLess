import { crc32 } from 'node:zlib'

/** 最小 TIFF：IFD0 只有 Orientation（tag 0x0112，SHORT） */
export function tiffWithOrientation(orientation: number, littleEndian = true): Buffer {
  const b = Buffer.alloc(26)
  const u16 = (v: number, o: number) => (littleEndian ? b.writeUInt16LE(v, o) : b.writeUInt16BE(v, o))
  const u32 = (v: number, o: number) => (littleEndian ? b.writeUInt32LE(v, o) : b.writeUInt32BE(v, o))
  b.write(littleEndian ? 'II' : 'MM', 0, 'latin1')
  u16(42, 2)
  u32(8, 4)
  u16(1, 8)
  u16(0x0112, 10)
  u16(3, 12)
  u32(1, 14)
  u16(orientation, 18)
  u32(0, 22)
  return b
}

const EXIF = Buffer.from('Exif\0\0', 'latin1')

/** 在 SOI 之後插入 APP1 Exif segment */
export function jpegWithOrientation(jpeg: Uint8Array, orientation: number, littleEndian = true): Buffer {
  const payload = Buffer.concat([EXIF, tiffWithOrientation(orientation, littleEndian)])
  const header = Buffer.from([0xff, 0xe1, 0, 0])
  header.writeUInt16BE(payload.length + 2, 2)
  return Buffer.concat([Buffer.from(jpeg).subarray(0, 2), header, payload, Buffer.from(jpeg).subarray(2)])
}

/** 在 IHDR 之後插入 eXIf chunk（含正確 CRC） */
export function pngWithOrientation(png: Uint8Array, orientation: number): Buffer {
  const src = Buffer.from(png)
  const ihdrEnd = 8 + 12 + src.readUInt32BE(8)
  const data = tiffWithOrientation(orientation)
  const chunk = Buffer.alloc(12 + data.length)
  chunk.writeUInt32BE(data.length, 0)
  chunk.write('eXIf', 4, 'latin1')
  data.copy(chunk, 8)
  chunk.writeUInt32BE(crc32(chunk.subarray(4, 8 + data.length)), 8 + data.length)
  return Buffer.concat([src.subarray(0, ihdrEnd), chunk, src.subarray(ihdrEnd)])
}

/** 在檔尾附加 EXIF chunk 並更新 RIFF 大小 */
export function webpWithOrientation(webp: Uint8Array, orientation: number): Buffer {
  const data = Buffer.concat([EXIF, tiffWithOrientation(orientation)])
  const header = Buffer.alloc(8)
  header.write('EXIF', 0, 'latin1')
  header.writeUInt32LE(data.length, 4)
  const pad = data.length % 2 ? Buffer.alloc(1) : Buffer.alloc(0)
  const out = Buffer.concat([Buffer.from(webp), header, data, pad])
  out.writeUInt32LE(out.length - 8, 4)
  return out
}
