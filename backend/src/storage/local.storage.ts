import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { copyFile, link, mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import type { Readable } from 'node:stream'
import type { TransferDirective } from '../models/asset.ts'
import {
  assertValidKey,
  ObjectExistsError,
  type ObjectInfo,
  type ObjectStorage,
  type PresignGetOptions,
  type PresignPutOptions,
  type PutObjectOptions,
} from './types.ts'

export const LOCAL_STORAGE_ROUTE = '/storage/objects'

export interface LocalSignature {
  method: 'PUT' | 'GET'
  key: string
  expires: number
  contentType: string
  maxBytes: number
}

interface ObjectMeta {
  contentType: string
}

/**
 * 開發 / 測試用的本機 object storage。簽名 URL 由本服務的 /storage/objects 路由處理，
 * 語意比照 S3 presigned URL：限定 key、method、到期時間與 Content-Type。
 * 不可用於正式環境（單機磁碟、無 replication）。
 */
export class LocalObjectStorage implements ObjectStorage {
  readonly driver = 'local'

  constructor(
    private readonly directory: string,
    private readonly publicBaseUrl: string,
    private readonly signingSecret: string,
  ) {}

  async presignPut(key: string, options: PresignPutOptions): Promise<TransferDirective> {
    const expiresAt = new Date(Date.now() + options.expiresInSeconds * 1000)
    const url = this.signUrl({
      method: 'PUT',
      key,
      expires: Math.floor(expiresAt.getTime() / 1000),
      contentType: options.contentType,
      maxBytes: options.maxBytes,
    })
    return { method: 'PUT', url, headers: { 'Content-Type': options.contentType }, expiresAt }
  }

  async presignGet(key: string, options: PresignGetOptions): Promise<TransferDirective> {
    const expiresAt = new Date(Date.now() + options.expiresInSeconds * 1000)
    const url = this.signUrl({
      method: 'GET',
      key,
      expires: Math.floor(expiresAt.getTime() / 1000),
      contentType: '',
      maxBytes: 0,
    })
    return { method: 'GET', url, headers: {}, expiresAt }
  }

  async head(key: string): Promise<ObjectInfo | null> {
    const path = this.pathOf(key)
    try {
      const info = await stat(path)
      const meta = await this.readMeta(key)
      return { size: info.size, contentType: meta?.contentType ?? null }
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null
      throw error
    }
  }

  async getStream(key: string): Promise<Readable | null> {
    if (!(await this.head(key))) return null
    return createReadStream(this.pathOf(key))
  }

  async putFile(key: string, filePath: string, options: PutObjectOptions): Promise<void> {
    const target = this.pathOf(key)
    await mkdir(dirname(target), { recursive: true })
    const temp = `${target}.${randomUUID()}.tmp`
    await copyFile(filePath, temp)
    await this.commit(temp, key, options)
  }

  /** 由 /storage/objects PUT 路由呼叫：已寫好的暫存檔原子發布到 key */
  async commit(tempPath: string, key: string, options: PutObjectOptions): Promise<void> {
    const target = this.pathOf(key)
    await mkdir(dirname(target), { recursive: true })
    try {
      if (options.ifNoneMatch) {
        // link() 在目標存在時失敗，等同 If-None-Match: *
        await link(tempPath, target)
        await rm(tempPath, { force: true })
      } else {
        await rename(tempPath, target)
      }
    } catch (error) {
      await rm(tempPath, { force: true })
      if ((error as NodeJS.ErrnoException).code === 'EEXIST') throw new ObjectExistsError(key)
      throw error
    }
    await writeFile(`${target}.meta.json`, JSON.stringify({ contentType: options.contentType } satisfies ObjectMeta))
  }

  async delete(key: string): Promise<void> {
    const path = this.pathOf(key)
    await rm(path, { force: true })
    await rm(`${path}.meta.json`, { force: true })
  }

  /** 暫存檔位置（與目標同一檔案系統，以便 rename / link） */
  tempPathFor(key: string): string {
    return `${this.pathOf(key)}.${randomUUID()}.upload`
  }

  verify(key: string, query: Record<string, string | undefined>, method: 'PUT' | 'GET'): LocalSignature | null {
    const expires = Number(query.e)
    const maxBytes = Number(query.n ?? 0)
    const contentType = query.ct ?? ''
    const sig = query.sig ?? ''
    if (!Number.isFinite(expires) || !Number.isFinite(maxBytes)) return null
    const signature: LocalSignature = { method, key, expires, contentType, maxBytes }
    const expected = Buffer.from(this.sign(signature))
    const actual = Buffer.from(sig)
    if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) return null
    if (expires * 1000 < Date.now()) return null
    return signature
  }

  private signUrl(signature: LocalSignature): string {
    const params = new URLSearchParams({ e: String(signature.expires) })
    if (signature.method === 'PUT') {
      params.set('ct', signature.contentType)
      params.set('n', String(signature.maxBytes))
    }
    params.set('sig', this.sign(signature))
    return `${this.publicBaseUrl}${LOCAL_STORAGE_ROUTE}/${signature.key}?${params}`
  }

  private sign(s: LocalSignature): string {
    return createHmac('sha256', this.signingSecret)
      .update([s.method, s.key, s.expires, s.contentType, s.maxBytes].join('\n'))
      .digest('base64url')
  }

  private pathOf(key: string): string {
    assertValidKey(key)
    return join(this.directory, key)
  }

  private async readMeta(key: string): Promise<ObjectMeta | null> {
    try {
      return JSON.parse(await readFile(`${this.pathOf(key)}.meta.json`, 'utf8')) as ObjectMeta
    } catch {
      return null
    }
  }
}
