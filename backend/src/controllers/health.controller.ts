import type { FastifyReply } from 'fastify'
import type { StatusService } from '../services/status.service.ts'

export class HealthController {
  constructor(private readonly status: StatusService) {}

  /** liveness：process 活著即可 */
  live = async () => ({ status: 'ok' })

  /** readiness：依賴（PostgreSQL）可用才接流量 */
  ready = async (_request: unknown, reply: FastifyReply) => {
    const status = await this.status.status()
    return reply.status(status.database === 'up' ? 200 : 503).send({ status: status.database === 'up' ? 'ok' : 'unavailable', database: status.database })
  }
}
