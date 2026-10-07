import type { FastifyReply } from 'fastify'
import type { StatusService } from '../services/status.service.ts'
import { homeView, type EndpointInfo } from '../views/home.view.ts'

export class PageController {
  constructor(
    private readonly status: StatusService,
    private readonly endpoints: () => EndpointInfo[],
  ) {}

  home = async (_request: unknown, reply: FastifyReply) => {
    const status = await this.status.status()
    return reply.type('text/html; charset=utf-8').send(homeView(status, this.endpoints()))
  }
}
