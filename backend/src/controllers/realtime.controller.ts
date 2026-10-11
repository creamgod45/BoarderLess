import type { FastifyReply, FastifyRequest } from 'fastify'
import type { RealtimeConfig } from '../config/env.ts'
import { requireUser } from '../middlewares/auth.ts'
import type { realtimeCapabilitiesSchema, realtimeTicketSchema } from '../schemas/realtime.schema.ts'
import type { RealtimeService } from '../services/realtime.service.ts'
import type { RequestFor } from './types.ts'

export const REALTIME_PATH = '/api/v1/realtime'

/** 對外 WebSocket URL：優先使用部署設定（正式環境必須是 wss://），否則依 request 推導（開發用） */
export function webSocketUrlFor(config: RealtimeConfig, request: FastifyRequest): string {
  if (config.publicUrl) return config.publicUrl
  const forwardedProto = String(request.headers['x-forwarded-proto'] ?? '').split(',')[0]?.trim()
  const secure = (forwardedProto || request.protocol) === 'https'
  const host = String(request.headers['x-forwarded-host'] ?? request.headers.host ?? 'localhost')
  return `${secure ? 'wss' : 'ws'}://${host}${REALTIME_PATH}`
}

export class RealtimeController {
  constructor(
    private readonly realtime: RealtimeService,
    private readonly config: RealtimeConfig,
  ) {}

  capabilities = async (request: RequestFor<typeof realtimeCapabilitiesSchema>) => {
    const user = requireUser(request)
    return this.realtime.capabilities(request.params.workspaceId, user.id, webSocketUrlFor(this.config, request))
  }

  ticket = async (request: RequestFor<typeof realtimeTicketSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const result = await this.realtime.issueTicket(request.params.workspaceId, user.id, request.body, webSocketUrlFor(this.config, request))
    return reply.status(201).header('cache-control', 'no-store').send(result)
  }
}
