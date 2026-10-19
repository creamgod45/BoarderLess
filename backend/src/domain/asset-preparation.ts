import { createHash } from 'node:crypto'
import type { PreparationMetadata, VerifiedMetadata } from '../models/asset-preparation.ts'

/**
 * Canonical metadata：固定欄位順序（byteSize, checksum, durationMs, height, mediaType, width），
 * null 明確保留。body 已通過嚴格 schema（lowercase checksum、allowlist MIME），這裡只重建順序。
 */
export function canonicalMetadata(input: PreparationMetadata): PreparationMetadata {
  return {
    byteSize: input.byteSize,
    checksum: input.checksum,
    durationMs: input.durationMs,
    height: input.height,
    mediaType: input.mediaType,
    width: input.width,
  }
}

/** requestDigest：canonical compact UTF-8 JSON 的 SHA-256 lowercase hex（無 sha256: 前綴） */
export function preparationDigest(metadata: PreparationMetadata): string {
  return createHash('sha256').update(JSON.stringify(canonicalMetadata(metadata)), 'utf8').digest('hex')
}

/** 產品容差（不是格式標準）：abs(declared − verified) ≤ max(100ms, verified × 1%) */
export function durationWithinTolerance(declared: number, verified: number): boolean {
  return Math.abs(declared - verified) <= Math.max(100, verified * 0.01)
}

/**
 * declared 與 verified 比對：null 表示未宣告，不比較該欄位；寬高必須完全相同；
 * duration 依產品容差。宣告了 duration 但實際內容沒有（靜態影像）視為不符。
 */
export function declaredMatchesVerified(declared: PreparationMetadata, verified: VerifiedMetadata): boolean {
  if (declared.width !== null && declared.width !== verified.width) return false
  if (declared.height !== null && declared.height !== verified.height) return false
  if (declared.durationMs !== null) {
    if (verified.durationMs === null) return false
    if (!durationWithinTolerance(declared.durationMs, verified.durationMs)) return false
  }
  return true
}
