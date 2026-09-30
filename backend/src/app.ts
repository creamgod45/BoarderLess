import Fastify, { type FastifyInstance } from 'fastify'
import swagger from '@fastify/swagger'
import swaggerUi from '@fastify/swagger-ui'
import type postgres from 'postgres'
import pkg from '../package.json' with { type: 'json' }
import type { AppConfig } from './config/env.ts'
import { DEV_USER_HEADER, registerDevAuth } from './middlewares/auth.ts'
import { registerErrorHandler } from './middlewares/error-handler.ts'
import { Database } from './repositories/index.ts'
import { apiRoutes } from './routes/api.routes.ts'
import { pageRoutes } from './routes/page.routes.ts'
import { createServices } from './services/index.ts'
import type { EndpointInfo } from './views/home.view.ts'

export interface BuildAppOptions {
  config: Pick<AppConfig, 'logLevel'>
  sql: postgres.Sql
  logger?: boolean
}

export async function buildApp({ config, sql, logger = true }: BuildAppOptions): Promise<FastifyInstance> {
  const app = Fastify({
    logger: logger && {
      level: config.logLevel,
      // 不記錄 token / 身分 header（§9）
      redact: ['req.headers.authorization', `req.headers["${DEV_USER_HEADER}"]`],
    },
    genReqId: () => crypto.randomUUID(),
    ajv: { customOptions: { removeAdditional: false, coerceTypes: 'array', useDefaults: true } },
  })

  const db = new Database(sql)
  const services = createServices(db, pkg.version)

  const endpoints: EndpointInfo[] = []
  app.addHook('onRoute', (route) => {
    const schema = route.schema as { hide?: boolean; summary?: string } | undefined
    if (!route.url.startsWith('/api/') || schema?.hide) return
    for (const method of [route.method].flat()) {
      if (method === 'HEAD') continue
      endpoints.push({ method, url: route.url, summary: schema?.summary })
    }
  })

  registerErrorHandler(app)
  registerDevAuth(app, services.users)

  await app.register(swagger, {
    openapi: {
      info: { title: 'BoarderLess Storage API', version: pkg.version },
      components: {
        securitySchemes: { devUser: { type: 'apiKey', in: 'header', name: DEV_USER_HEADER } },
      },
      security: [{ devUser: [] }],
    },
  })
  await app.register(swaggerUi, { routePrefix: '/docs' })

  await app.register(pageRoutes(services, () => endpoints))
  await app.register(apiRoutes(services), { prefix: '/api/v1' })

  return app
}
