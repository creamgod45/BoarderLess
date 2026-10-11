import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify'
import { Type } from 'typebox'
import { requireUser } from '../middlewares/auth.ts'
import { ErrorEnvelope } from '../schemas/common.schema.ts'
import { QuickSchemeCapabilitiesSchema, QuickSchemeListSchema, QuickSchemeResourceSchema } from '../schemas/quick-scheme.schema.ts'
import { QUICK_SCHEME_LIMITS, type MutationResult, type QuickSchemeService } from '../services/quick-scheme.service.ts'
import { AppError } from '../utils/errors.ts'
import { parseStrictJson, StrictJsonError } from '../utils/strict-json.ts'

const LowerUuid = Type.String({ pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' })
const Params = Type.Object({ id: LowerUuid })
const errors = { 400: ErrorEnvelope, 401: ErrorEnvelope, 403: ErrorEnvelope, 404: ErrorEnvelope, 409: ErrorEnvelope, 410: ErrorEnvelope, 412: ErrorEnvelope, 413: ErrorEnvelope, 422: ErrorEnvelope, 428: ErrorEnvelope }
const tags = ['quick-schemes']

const header = (request: FastifyRequest, name: string) => {
  const value = request.headers[name]
  return Array.isArray(value) ? value.join(',') : value
}

/**
 * /api/v1/users/me/quick-schemes（owner 由認證注入）。
 * 這個 plugin 有自己的 JSON parser：parse 前限制 1 MiB，嚴格拒絕重複 key / 孤立 surrogate / 過深巢狀。
 * 可寫 body 的 schema 驗證在 service（錯誤一律 422 invalid_payload，不是 400）。
 */
export function quickSchemeRoutes(service: QuickSchemeService) {
  return async (app: FastifyInstance) => {
    app.removeContentTypeParser('application/json')
    app.addContentTypeParser('application/json', { parseAs: 'string', bodyLimit: QUICK_SCHEME_LIMITS.maxRequestBytes }, (_request, body, done) => {
      try {
        done(null, parseStrictJson(body as string))
      } catch (error) {
        if (error instanceof StrictJsonError) done(new AppError(400, 'invalid_json', 'Request body is not strict JSON', { reason: error.reason }), undefined)
        else done(error as Error, undefined)
      }
    })
    app.addHook('onSend', async (_request, reply) => {
      reply.header('cache-control', 'private, no-store')
    })

    const send = (reply: FastifyReply, result: MutationResult) => {
      if (result.etag) reply.header('etag', result.etag)
      if (result.replayed) reply.header('idempotent-replayed', 'true')
      return result.status === 204 ? reply.status(204).send() : reply.status(result.status).send(result.body)
    }

    app.get('/users/me/quick-scheme-capabilities', { schema: { tags, summary: '方案簿能力：resource / selection 版本、limits、TTL、materializationEnabled', response: { 200: QuickSchemeCapabilitiesSchema } } }, async (request) => {
      requireUser(request)
      return service.capabilities()
    })

    app.get<{ Querystring: { cursor?: string; limit?: number } }>(
      '/users/me/quick-schemes',
      {
        schema: {
          tags,
          summary: '列出方案（穩定快照 cursor；首頁可用 If-None-Match 對 collection ETag 回 304）',
          querystring: Type.Object({
            cursor: Type.Optional(Type.String({ maxLength: 512 })),
            limit: Type.Optional(Type.Integer({ minimum: 1, maximum: QUICK_SCHEME_LIMITS.pageLimitMax, default: QUICK_SCHEME_LIMITS.pageLimitDefault })),
          }),
          response: { 200: QuickSchemeListSchema, ...errors },
        },
      },
      async (request, reply) => {
        const user = requireUser(request)
        const { cursor, limit = QUICK_SCHEME_LIMITS.pageLimitDefault } = request.query
        if (!cursor && header(request, 'if-none-match')) {
          const etag = await service.collectionEtagFor(user.id)
          if (header(request, 'if-none-match')!.trim() === etag) return reply.status(304).header('etag', etag).send()
        }
        const page = await service.list(user.id, cursor, limit)
        reply.header('etag', page.collectionEtag)
        return { items: page.items, nextCursor: page.nextCursor, collectionRevision: page.collectionRevision }
      },
    )

    app.get<{ Params: { id: string } }>(
      '/users/me/quick-schemes/:id',
      { schema: { tags, summary: '取得方案（ETag；If-None-Match 命中回 304）', params: Params, response: { 200: QuickSchemeResourceSchema, ...errors } } },
      async (request, reply) => {
        const user = requireUser(request)
        const scheme = await service.get(user.id, request.params.id)
        reply.header('etag', scheme.etag)
        if (header(request, 'if-none-match')?.trim() === scheme.etag) return reply.status(304).send()
        return scheme
      },
    )

    app.put<{ Params: { id: string } }>(
      '/users/me/quick-schemes/:id',
      {
        schema: {
          tags,
          summary: '建立（If-None-Match: *）或更新（If-Match: <ETag>）；必填 Idempotency-Key',
          params: Params,
          response: { 200: QuickSchemeResourceSchema, 201: QuickSchemeResourceSchema, ...errors },
        },
      },
      async (request, reply) => {
        const user = requireUser(request)
        const result = await service.put(
          user.id,
          request.params.id,
          { ifMatch: header(request, 'if-match'), ifNoneMatch: header(request, 'if-none-match'), idempotencyKey: header(request, 'idempotency-key') },
          request.body,
        )
        return send(reply, result)
      },
    )

    app.delete<{ Params: { id: string } }>(
      '/users/me/quick-schemes/:id',
      { schema: { tags, summary: '刪除（If-Match、Idempotency-Key）；保留 tombstone，不刪素材', params: Params, response: { ...errors } } },
      async (request, reply) => {
        const user = requireUser(request)
        const result = await service.delete(user.id, request.params.id, {
          ifMatch: header(request, 'if-match'),
          idempotencyKey: header(request, 'idempotency-key'),
        })
        return send(reply, result)
      },
    )
  }
}
