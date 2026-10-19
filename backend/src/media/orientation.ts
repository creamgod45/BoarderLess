import { open, type FileHandle } from 'node:fs/promises'
import type { AssetMediaType } from '../models/asset.ts'

/** EXIF orientation 5–8 代表影像需轉 90° 顯示（寬高互換） */
export const isQuarterTurnOrientation = (orientation: number | null) => orientation !== null && orientation >= 5 && orientation <= 8

/** 影片 display matrix rotation（±90、±270）代表寬高互換 */
export const isQuarterTurnRotation = (degrees: number | null) => degrees !== null && Math.abs(Math.round(degrees)) % 180 === 90

const MAX_EXIF_BYTES = 256 * 1024

/**
 * 讀取影像內嵌的 EXIF orientation（JPEG APP1、PNG eXIf、WebP EXIF chunk）；沒有或無法解析時回傳 null。
 * 只走 segment / chunk header，不把整個檔案讀進記憶體。
 */
export async function readExifOrientation(path: string, mediaType: AssetMediaType): Promise<number | null> {
  if (mediaType !== 'image/jpeg' && mediaType !== 'image/png' && mediaType !== 'image/webp') return null
  const file = await open(path, 'r')
  try {
    const size = (await file.stat()).size
    const tiff =
      mediaType === 'image/jpeg' ? await jpegExif(file, size) : mediaType === 'image/png' ? await pngExif(file, size) : await webpExif(file, size)
    return tiff ? tiffOrientation(tiff) : null
  } catch {
    return null
  } finally {
    await file.close()
  }
}

async function read(file: FileHandle, position: number, length: number): Promise<Buffer> {
  const buffer = Buffer.alloc(length)
  const { bytesRead } = await file.read(buffer, 0, length, position)
  return buffer.subarray(0, bytesRead)
}

const EXIF_HEADER = Buffer.from('Exif\0\0', 'latin1')
const stripExifHeader = (data: Buffer) => (data.subarray(0, 6).equals(EXIF_HEADER) ? data.subarray(6) : data)

async function jpegExif(file: FileHandle, size: number): Promise<Buffer | null> {
  let pos = 2 // SOI
  while (pos + 4 <= size) {
    const head = await read(file, pos, 4)
    if (head.length < 4 || head[0] !== 0xff) return null
    const marker = head[1]!
    if (marker === 0xd8 || (marker >= 0xd0 && marker <= 0xd7) || marker === 0x01) {
      pos += 2
      continue
    }
    if (marker === 0xda || marker === 0xd9) return null // 影像資料開始：metadata 段已結束
    const length = head.readUInt16BE(2)
    if (marker === 0xe1 && length > 8 && length - 2 <= MAX_EXIF_BYTES) {
      const data = await read(file, pos + 4, length - 2)
      if (data.subarray(0, 6).equals(EXIF_HEADER)) return data.subarray(6)
    }
    pos += 2 + length
  }
  return null
}

async function pngExif(file: FileHandle, size: number): Promise<Buffer | null> {
  let pos = 8 // signature
  while (pos + 8 <= size) {
    const head = await read(file, pos, 8)
    const length = head.readUInt32BE(0)
    const type = head.toString('latin1', 4, 8)
    if (type === 'eXIf' && length <= MAX_EXIF_BYTES) return stripExifHeader(await read(file, pos + 8, length))
    if (type === 'IEND') return null
    pos += 12 + length
  }
  return null
}

async function webpExif(file: FileHandle, size: number): Promise<Buffer | null> {
  let pos = 12 // RIFF....WEBP
  while (pos + 8 <= size) {
    const head = await read(file, pos, 8)
    const type = head.toString('latin1', 0, 4)
    const length = head.readUInt32LE(4)
    if (type === 'EXIF' && length <= MAX_EXIF_BYTES) return stripExifHeader(await read(file, pos + 8, length))
    pos += 8 + length + (length % 2)
  }
  return null
}

/** TIFF IFD0 的 Orientation（tag 0x0112，SHORT） */
function tiffOrientation(tiff: Buffer): number | null {
  if (tiff.length < 8) return null
  const order = tiff.toString('latin1', 0, 2)
  if (order !== 'II' && order !== 'MM') return null
  const le = order === 'II'
  const u16 = (o: number) => (le ? tiff.readUInt16LE(o) : tiff.readUInt16BE(o))
  const u32 = (o: number) => (le ? tiff.readUInt32LE(o) : tiff.readUInt32BE(o))
  if (u16(2) !== 42) return null
  const ifd = u32(4)
  if (ifd + 2 > tiff.length) return null
  const count = u16(ifd)
  for (let i = 0; i < count; i++) {
    const entry = ifd + 2 + i * 12
    if (entry + 12 > tiff.length) return null
    if (u16(entry) === 0x0112 && u16(entry + 2) === 3) {
      const value = u16(entry + 8)
      return value >= 1 && value <= 8 ? value : null
    }
  }
  return null
}
