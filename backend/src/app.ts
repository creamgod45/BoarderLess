import Fastify, { type FastifyInstance, type FastifyRequest } from 'fastify'
import cors from '@fastify/cors'
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
import { localStorageRoutes } from './routes/storage.routes.ts'
import { createServices } from './services/index.ts'
import { LocalObjectStorage } from './storage/local.storage.ts'
import type { ObjectStorage } from './storage/types.ts'
import type { EndpointInfo } from './views/home.view.ts'

export type BuildAppConfig = Pick<AppConfig, 'logLevel' | 'corsOrigins' | 'media'> & {
  worker: Pick<AppConfig['worker'], 'maxAttempts'>
}

export interface BuildAppOptions {
  config: BuildAppConfig
  sql: postgres.Sql
  storage: ObjectStorage
  logger?: boolean
}

/** log 中不保留 query string（可能含 signed URL 簽名或 token，§9） */
function serializeRequest(request: FastifyRequest) {
  return {
    method: request.method,
    url: request.url.split('?')[0],
    hostname: request.hostname,
    remoteAddress: request.ip,
  }
}

export async function buildApp({ config, sql, storage, logger = true }: BuildAppOptions): Promise<FastifyInstance> {
  const app = Fastify({
    logger: logger && {
      level: config.logLevel,
      redact: ['req.headers.authorization', `req.headers["${DEV_USER_HEADER}"]`],
      serializers: { req: serializeRequest },
    },
    genReqId: () => crypto.randomUUID(),
    ajv: { customOptions: { removeAdditional: false, coerceTypes: 'array', useDefaults: true } },
  })

  const db = new Database(sql)
  const services = createServices({
    db,
    storage,
    version: pkg.version,
    assets: { media: config.media, jobMaxAttempts: config.worker.maxAttempts },
  })

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

  await app.register(cors, {
    origin: config.corsOrigins.includes('*') ? true : config.corsOrigins,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['content-type', DEV_USER_HEADER],
    exposedHeaders: ['content-length', 'etag'],
    maxAge: 600,
  })

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
  if (storage instanceof LocalObjectStorage) await app.register(localStorageRoutes(storage))

  return app
}
