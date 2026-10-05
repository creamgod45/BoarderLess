import type { Readable } from 'node:stream'
import type { TransferDirective } from '../models/asset.ts'

export interface ObjectInfo {
  size: number
  contentType: string | null
}

export interface PresignPutOptions {
  contentType: string
  expiresInSeconds: number
  /** 允許的最大位元組數；S3 presigned PUT 無法強制，由 worker 驗證 */
  maxBytes: number
}

export interface PresignGetOptions {
  expiresInSeconds: number
}

export interface PutObjectOptions {
  contentType: string
  /** true：物件已存在時丟出 ObjectExistsError（防止覆寫已驗證內容） */
  ifNoneMatch?: boolean
}

/**
 * Private object storage 抽象。實作：local（開發 / 測試）、s3（S3-compatible）。
 * storage key 是 opaque handle，不是下載地址；下載一律經 presignGet 簽發短效 URL。
 */
export interface ObjectStorage {
  readonly driver: string
  presignPut(key: string, options: PresignPutOptions): Promise<TransferDirective>
  presignGet(key: string, options: PresignGetOptions): Promise<TransferDirective>
  head(key: string): Promise<ObjectInfo | null>
  /** 物件不存在時回傳 null */
  getStream(key: string): Promise<Readable | null>
  putFile(key: string, filePath: string, options: PutObjectOptions): Promise<void>
  /** 冪等；不存在也算成功 */
  delete(key: string): Promise<void>
  /** 啟動時準備（建立 bucket、CORS 等） */
  init?(corsOrigins: string[]): Promise<void>
}

/** 暫時性失敗（網路、5xx）；API 回 503，worker 重試 */
export class StorageUnavailableError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options)
    this.name = 'StorageUnavailableError'
  }
}

export class ObjectExistsError extends Error {
  constructor(readonly key: string) {
    super('Object already exists')
    this.name = 'ObjectExistsError'
  }
}

const KEY_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._\-/]*$/

export function assertValidKey(key: string): void {
  if (!KEY_PATTERN.test(key) || key.includes('..') || key.includes('//') || key.length > 512) {
    throw new Error(`Invalid storage key: ${key}`)
  }
}
