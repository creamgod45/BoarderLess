import { createHash, randomUUID } from 'node:crypto'
import { createReadStream, createWriteStream } from 'node:fs'
import { mkdtemp, open, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { Transform, type Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'

export interface StreamedFile {
  path: string
  byteCount: number
  /** `sha256:<hex>` */
  checksum: string
  /** 超過 maxBytes 時停止寫入 */
  truncated: boolean
}

/** 串流寫入檔案並同時計算 SHA-256 與位元組數；超過 maxBytes 即停止（避免無上限寫入）。 */
export async function streamToFile(source: Readable, path: string, maxBytes: number): Promise<StreamedFile> {
  const hash = createHash('sha256')
  let byteCount = 0
  let truncated = false
  const meter = new Transform({
    transform(chunk: Buffer, _encoding, callback) {
      byteCount += chunk.length
      if (byteCount > maxBytes) {
        truncated = true
        callback(new Error('size limit exceeded'))
        return
      }
      hash.update(chunk)
      callback(null, chunk)
    },
  })
  try {
    await pipeline(source, meter, createWriteStream(path))
  } catch (error) {
    if (!truncated) throw error
  }
  return { path, byteCount, checksum: `sha256:${hash.digest('hex')}`, truncated }
}

export async function hashFile(path: string): Promise<{ byteCount: number; checksum: string }> {
  const hash = createHash('sha256')
  let byteCount = 0
  for await (const chunk of createReadStream(path)) {
    byteCount += (chunk as Buffer).length
    hash.update(chunk as Buffer)
  }
  return { byteCount, checksum: `sha256:${hash.digest('hex')}` }
}

export async function readHead(path: string, length: number): Promise<Uint8Array> {
  const handle = await open(path, 'r')
  try {
    const buffer = Buffer.alloc(length)
    const { bytesRead } = await handle.read(buffer, 0, length, 0)
    return buffer.subarray(0, bytesRead)
  } finally {
    await handle.close()
  }
}

/** 在 tmpDir 下建立專屬工作目錄，fn 結束後一律清除 */
export async function withTempDir<T>(tmpDir: string, fn: (dir: string) => Promise<T>): Promise<T> {
  const dir = await mkdtemp(join(tmpDir, `boarderless-${randomUUID().slice(0, 8)}-`))
  try {
    return await fn(dir)
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
}
