import type { AssetMediaType } from '../models/asset.ts'

/** 讀取用於判斷格式的檔頭長度 */
export const SNIFF_BYTES = 4096

const ascii = (bytes: Uint8Array, start: number, end: number) => String.fromCharCode(...bytes.subarray(start, end))
const startsWith = (bytes: Uint8Array, signature: number[]) => signature.every((b, i) => bytes[i] === b)

// ISO-BMFF 中屬於靜態影像（HEIC / AVIF）或非 MP4 的 brand
const NON_MP4_BRANDS = new Set(['qt  ', 'heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'mif1', 'msf1', 'avif', 'avis', 'crx '])

/**
 * 以檔案內容（magic bytes）判斷實際格式；宣告的副檔名 / Content-Type 不是驗證依據（§4.2）。
 * 只辨識 allowlist 內的六種格式，其他回傳 null。
 */
export function sniffMediaType(bytes: Uint8Array): AssetMediaType | null {
  if (startsWith(bytes, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return 'image/png'
  if (startsWith(bytes, [0xff, 0xd8, 0xff])) return 'image/jpeg'
  if (bytes.length >= 6 && (ascii(bytes, 0, 6) === 'GIF87a' || ascii(bytes, 0, 6) === 'GIF89a')) return 'image/gif'
  if (bytes.length >= 12 && ascii(bytes, 0, 4) === 'RIFF' && ascii(bytes, 8, 12) === 'WEBP') return 'image/webp'
  if (bytes.length >= 12 && ascii(bytes, 4, 8) === 'ftyp') {
    return NON_MP4_BRANDS.has(ascii(bytes, 8, 12)) ? null : 'video/mp4'
  }
  if (startsWith(bytes, [0x1a, 0x45, 0xdf, 0xa3])) {
    // EBML header 的 DocType 必須是 webm（一般 Matroska 不在 allowlist）
    const header = ascii(bytes, 0, Math.min(bytes.length, 64))
    return header.includes('webm') ? 'video/webm' : null
  }
  return null
}
