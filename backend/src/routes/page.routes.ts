import type { FastifyInstance } from 'fastify'
import { HealthController } from '../controllers/health.controller.ts'
import { PageController } from '../controllers/page.controller.ts'
import type { Services } from '../services/index.ts'
import type { EndpointInfo } from '../views/home.view.ts'

export function pageRoutes(services: Services, endpoints: () => EndpointInfo[]) {
  return async (app: FastifyInstance) => {
    const health = new HealthController(services.status)
    app.get('/health', { schema: { hide: true } }, health.live)
    app.get('/health/ready', { schema: { hide: true } }, health.ready)

    const pages = new PageController(services.status, endpoints)
    app.get('/', { schema: { hide: true } }, pages.home)
  }
}
