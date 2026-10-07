import type { AssetMediaType } from '../models/asset.ts'
import type { MediaTools } from './ffmpeg.ts'
import { readHead } from './files.ts'
import { SNIFF_BYTES, sniffMediaType } from './sniff.ts'

/** 不可信素材的解碼限制 */
export const MEDIA_LIMITS = {
  maxPixels: 100_000_000,
  maxEdge: 20_000,
  maxDurationMs: 3 * 60 * 60 * 1000,
}

/** 穩定的 rejectionReason 機器碼；不含路徑或 URL */
export type RejectionReason =
  | 'upload_missing'
  | 'byte_size_mismatch'
  | 'checksum_mismatch'
  | 'media_type_mismatch'
  | 'unsupported_format'
  | 'undecodable_media'
  | 'dimensions_exceeded'
  | 'invalid_duration'
  | 'duration_exceeded'

export type InspectionResult =
  | { ok: true; width: number; height: number; durationMs: number | null }
  | { ok: false; reason: RejectionReason }

const ANIMATED_OR_VIDEO = new Set<AssetMediaType>(['image/gif', 'video/mp4', 'video/webm'])

/**
 * 依實際內容驗證格式、可解碼性、尺寸與 duration（§4.2）。
 * MediaToolUnavailableError 會往外拋，讓 job 重試而不是誤判素材不合法。
 */
export async function inspectMedia(path: string, declared: AssetMediaType, tools: MediaTools): Promise<InspectionResult> {
  const sniffed = sniffMediaType(await readHead(path, SNIFF_BYTES))
  if (!sniffed) return { ok: false, reason: 'unsupported_format' }
  if (sniffed !== declared) return { ok: false, reason: 'media_type_mismatch' }

  const probe = await tools.probe(path)
  if (!probe?.video) return { ok: false, reason: 'undecodable_media' }
  const { width, height } = probe.video
  if (width > MEDIA_LIMITS.maxEdge || height > MEDIA_LIMITS.maxEdge || width * height > MEDIA_LIMITS.maxPixels) {
    return { ok: false, reason: 'dimensions_exceeded' }
  }

  const isVideo = declared.startsWith('video/')
  if (isVideo) {
    const expected = declared === 'video/mp4' ? 'mp4' : 'webm'
    if (!probe.formatName.split(',').includes(expected)) return { ok: false, reason: 'media_type_mismatch' }
    if (!probe.durationMs) return { ok: false, reason: 'invalid_duration' }
  }
  if (probe.durationMs && probe.durationMs > MEDIA_LIMITS.maxDurationMs) return { ok: false, reason: 'duration_exceeded' }

  // 尺寸檢查通過後才實際解碼，避免 decompression bomb
  if (!(await tools.canDecode(path))) return { ok: false, reason: 'undecodable_media' }

  return {
    ok: true,
    width,
    height,
    durationMs: ANIMATED_OR_VIDEO.has(declared) ? probe.durationMs : null,
  }
}
