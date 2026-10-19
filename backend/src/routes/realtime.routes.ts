import websocket from '@fastify/websocket'
import type { FastifyInstance } from 'fastify'
import type { RealtimeConfig } from '../config/env.ts'
import { REALTIME_PATH, RealtimeController } from '../controllers/realtime.controller.ts'
import type { RealtimeHub } from '../realtime/hub.ts'
import { REALTIME_SUBPROTOCOL } from '../realtime/messages.ts'
import * as schema from '../schemas/realtime.schema.ts'
import type { RealtimeService } from '../services/realtime.service.ts'

/** 檢查 Origin：有 Origin header（瀏覽器）時必須在允許清單；原生 client 沒有 Origin 時允許 */
export function originAllowed(config: RealtimeConfig, origin: string | undefined): boolean {
  if (!origin || config.allowedOrigins.includes('*')) return true
  return config.allowedOrigins.includes(origin)
}

/** REST 路由（掛在 /api/v1 之下） */
export function realtimeRestRoutes(service: RealtimeService, config: RealtimeConfig) {
  return async (app: FastifyInstance) => {
    const controller = new RealtimeController(service, config)
    app.get('/workspaces/:workspaceId/realtime-capabilities', { schema: schema.realtimeCapabilitiesSchema }, controller.capabilities)
    app.post('/workspaces/:workspaceId/realtime-ticket', { schema: schema.realtimeTicketSchema }, controller.ticket)
  }
}

/** WebSocket gateway：subprotocol boarderless.v1；ticket 只在第一個 join_workspace frame，不放 URL query */
export function realtimeSocketRoutes(hub: RealtimeHub) {
  return async (app: FastifyInstance) => {
    await app.register(websocket, {
      options: {
        maxPayload: hub.config.maxMessageBytes,
        handleProtocols: (protocols: Set<string>) => (protocols.has(REALTIME_SUBPROTOCOL) ? REALTIME_SUBPROTOCOL : false),
      },
    })
    app.get(
      REALTIME_PATH,
      {
        websocket: true,
        schema: { hide: true },
        // upgrade 前拒絕：不允許的 Origin、缺少 subprotocol
        preValidation: async (request, reply) => {
          if (!originAllowed(hub.config, request.headers.origin)) return reply.code(403).send({ error: { code: 'origin_not_allowed' } })
          const protocols = String(request.headers['sec-websocket-protocol'] ?? '').split(',').map((p) => p.trim())
          if (!protocols.includes(REALTIME_SUBPROTOCOL)) return reply.code(426).send({ error: { code: 'subprotocol_required' } })
        },
      },
      (socket) => hub.accept(socket),
    )
  }
}
