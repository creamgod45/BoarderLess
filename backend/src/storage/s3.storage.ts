import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { Readable } from 'node:stream'
import type { ReadableStream as WebReadableStream } from 'node:stream/web'
import { AwsClient } from 'aws4fetch'
import type { S3StorageConfig } from '../config/env.ts'
import type { TransferDirective } from '../models/asset.ts'
import {
  assertValidKey,
  ObjectExistsError,
  StorageUnavailableError,
  type ObjectInfo,
  type ObjectStorage,
  type PresignGetOptions,
  type PresignPutOptions,
  type PutObjectOptions,
} from './types.ts'

/** S3-compatible private bucket（AWS S3、RustFS、MinIO、R2…）；使用 path-style URL */
export class S3ObjectStorage implements ObjectStorage {
  readonly driver = 's3'
  private readonly client: AwsClient

  constructor(private readonly config: S3StorageConfig) {
    this.client = new AwsClient({
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
      service: 's3',
      region: config.region,
      retries: 2,
    })
  }

  async presignPut(key: string, options: PresignPutOptions): Promise<TransferDirective> {
    const headers = { 'Content-Type': options.contentType }
    // allHeaders：把 Content-Type 納入簽名，client 必須送出相同值
    const url = await this.presign(this.config.publicEndpoint, key, 'PUT', options.expiresInSeconds, headers, true)
    return { method: 'PUT', url, headers, expiresAt: expiry(options.expiresInSeconds) }
  }

  async presignGet(key: string, options: PresignGetOptions): Promise<TransferDirective> {
    const url = await this.presign(this.config.publicEndpoint, key, 'GET', options.expiresInSeconds, {}, false)
    return { method: 'GET', url, headers: {}, expiresAt: expiry(options.expiresInSeconds) }
  }

  async head(key: string): Promise<ObjectInfo | null> {
    const res = await this.request(this.objectUrl(this.config.endpoint, key), { method: 'HEAD' })
    if (res.status === 404) return null
    await expectOk(res, 'HEAD')
    return {
      size: Number(res.headers.get('content-length') ?? 0),
      contentType: res.headers.get('content-type'),
    }
  }

  async getStream(key: string): Promise<Readable | null> {
    const res = await this.request(this.objectUrl(this.config.endpoint, key), { method: 'GET' })
    if (res.status === 404) {
      await discard(res)
      return null
    }
    await expectOk(res, 'GET')
    if (!res.body) return Readable.from([])
    return Readable.fromWeb(res.body as unknown as WebReadableStream)
  }

  async putFile(key: string, filePath: string, options: PutObjectOptions): Promise<void> {
    const { size } = await stat(filePath)
    // 以短效 presigned URL 串流上傳，避免把檔案讀進記憶體計算 payload hash
    const url = await this.presign(this.config.endpoint, key, 'PUT', 300, {}, false)
    const headers: Record<string, string> = {
      'Content-Type': options.contentType,
      'Content-Length': String(size),
    }
    if (options.ifNoneMatch) headers['If-None-Match'] = '*'
    let res: Response
    try {
      res = await fetch(url, {
        method: 'PUT',
        headers,
        body: Readable.toWeb(createReadStream(filePath)) as unknown as RequestInit['body'],
        duplex: 'half',
      } as RequestInit)
    } catch (error) {
      throw new StorageUnavailableError('Object storage PUT failed', { cause: error })
    }
    if (res.status === 412) {
      await discard(res)
      throw new ObjectExistsError(key)
    }
    await expectOk(res, 'PUT')
    await discard(res)
  }

  async delete(key: string): Promise<void> {
    const res = await this.request(this.objectUrl(this.config.endpoint, key), { method: 'DELETE' })
    await discard(res)
    if (res.status === 404) return
    await expectOk(res, 'DELETE')
  }

  /** 開發用：建立 bucket 並設定 CORS（正式環境由 IaC 管理） */
  async init(corsOrigins: string[]): Promise<void> {
    if (!this.config.ensureBucket) return
    const bucketUrl = `${this.config.endpoint}/${this.config.bucket}`
    const created = await this.request(bucketUrl, { method: 'PUT' })
    if (!created.ok && created.status !== 409) await expectOk(created, 'CreateBucket')
    await discard(created)

    const origins = corsOrigins.map((o) => `<AllowedOrigin>${escapeXml(o)}</AllowedOrigin>`).join('')
    const cors =
      '<CORSConfiguration><CORSRule>' +
      origins +
      '<AllowedMethod>PUT</AllowedMethod><AllowedMethod>GET</AllowedMethod>' +
      '<AllowedHeader>content-type</AllowedHeader>' +
      '<ExposeHeader>ETag</ExposeHeader><ExposeHeader>Content-Length</ExposeHeader>' +
      '<MaxAgeSeconds>600</MaxAgeSeconds></CORSRule></CORSConfiguration>'
    const md5 = createHash('md5').update(cors).digest('base64')
    const res = await this.request(`${bucketUrl}?cors`, { method: 'PUT', body: cors, headers: { 'Content-MD5': md5 } })
    await expectOk(res, 'PutBucketCors')
    await discard(res)
  }

  private async presign(
    endpoint: string,
    key: string,
    method: 'PUT' | 'GET',
    expiresInSeconds: number,
    headers: Record<string, string>,
    signHeaders: boolean,
  ): Promise<string> {
    const url = `${this.objectUrl(endpoint, key)}?X-Amz-Expires=${expiresInSeconds}`
    const signed = await this.client.sign(url, { method, headers, aws: { signQuery: true, allHeaders: signHeaders } })
    return signed.url
  }

  private objectUrl(endpoint: string, key: string): string {
    assertValidKey(key)
    return `${endpoint}/${this.config.bucket}/${key.split('/').map(encodeURIComponent).join('/')}`
  }

  private async request(url: string, init: RequestInit): Promise<Response> {
    try {
      return await this.client.fetch(url, init)
    } catch (error) {
      throw new StorageUnavailableError(`Object storage ${init.method} failed`, { cause: error })
    }
  }
}

/** 非 2xx 時丟出 StorageUnavailableError；成功時不消耗 body */
async function expectOk(res: Response, operation: string): Promise<void> {
  if (res.ok) return
  const text = await res.text().catch(() => '')
  // 不回傳簽名 / URL；只保留 provider 錯誤碼
  const code = /<Code>([^<]+)<\/Code>/.exec(text)?.[1] ?? 'unknown'
  throw new StorageUnavailableError(`Object storage ${operation} returned ${res.status} (${code})`)
}

async function discard(res: Response): Promise<void> {
  await res.body?.cancel().catch(() => {})
}

function expiry(seconds: number): Date {
  return new Date(Date.now() + seconds * 1000)
}

function escapeXml(value: string): string {
  return value.replace(/[<>&'"]/g, (c) => `&#${c.charCodeAt(0)};`)
}
