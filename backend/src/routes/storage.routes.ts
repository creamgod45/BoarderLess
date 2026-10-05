import { mkdir, rm } from 'node:fs/promises'
import { dirname } from 'node:path'
import type { FastifyInstance, FastifyReply } from 'fastify'
import { streamToFile } from '../media/files.ts'
import { LOCAL_STORAGE_ROUTE, type LocalObjectStorage } from '../storage/local.storage.ts'

type StorageRequest = { Params: { '*': string }; Querystring: Record<string, string | undefined> }

const storageError = (reply: FastifyReply, status: number, code: string) =>
  reply.status(status).send({ error: { code, message: code } })

/**
 * STORAGE_DRIVER=local 時模擬 S3 presigned URL：只接受簽名限定的 key / method / Content-Type / 到期時間。
 * 不需要也不讀取 x-user-id；授權由簽名本身決定。
 */
export function localStorageRoutes(storage: LocalObjectStorage) {
  return async (app: FastifyInstance) => {
    // 保留原始 request stream，由 handler 串流寫檔（不經 JSON parser / bodyLimit）
    app.removeAllContentTypeParsers()
    app.addContentTypeParser('*', (_request, _payload, done) => done(null))

    app.put<StorageRequest>(`${LOCAL_STORAGE_ROUTE}/*`, { schema: { hide: true } }, async (request, reply) => {
      const key = request.params['*']
      const signature = safeVerify(storage, key, request.query, 'PUT')
      if (!signature) return storageError(reply, 403, 'SignatureDoesNotMatch')
      if (request.headers['content-type'] !== signature.contentType) {
        return storageError(reply, 403, 'SignatureDoesNotMatch')
      }
      const declared = Number(request.headers['content-length'] ?? Number.NaN)
      if (Number.isFinite(declared) && declared > signature.maxBytes) {
        return storageError(reply, 413, 'EntityTooLarge')
      }

      const temp = storage.tempPathFor(key)
      await mkdir(dirname(temp), { recursive: true })
      const written = await streamToFile(request.raw, temp, signature.maxBytes)
      if (written.truncated) {
        await rm(temp, { force: true })
        return storageError(reply, 413, 'EntityTooLarge')
      }
      await storage.commit(temp, key, { contentType: signature.contentType })
      return reply.status(200).header('etag', `"${written.checksum.slice(7, 39)}"`).send()
    })

    app.get<StorageRequest>(`${LOCAL_STORAGE_ROUTE}/*`, { schema: { hide: true } }, async (request, reply) => {
      const key = request.params['*']
      if (!safeVerify(storage, key, request.query, 'GET')) return storageError(reply, 403, 'SignatureDoesNotMatch')
      const info = await storage.head(key)
      const stream = info ? await storage.getStream(key) : null
      if (!info || !stream) return storageError(reply, 404, 'NoSuchKey')
      return reply
        .header('content-type', info.contentType ?? 'application/octet-stream')
        .header('content-length', String(info.size))
        .header('cache-control', 'private, no-store')
        .send(stream)
    })
  }
}

function safeVerify(
  storage: LocalObjectStorage,
  key: string,
  query: Record<string, string | undefined>,
  method: 'PUT' | 'GET',
) {
  try {
    return storage.verify(key, query, method)
  } catch {
    return null // 非法 key
  }
}
