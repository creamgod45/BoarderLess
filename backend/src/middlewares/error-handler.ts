import type { FastifyError, FastifyInstance } from 'fastify'
import { StorageUnavailableError } from '../storage/types.ts'
import { AppError } from '../utils/errors.ts'

interface PostgresError {
  code?: string
  severity?: string
}

function isPostgresError(error: unknown): error is PostgresError & Error {
  return error instanceof Error && error.name === 'PostgresError'
}

/** 所有錯誤統一為 `{ error: { code, message, details? }, requestId }` */
export function registerErrorHandler(app: FastifyInstance): void {
  app.setErrorHandler((error: FastifyError | AppError | Error, request, reply) => {
    const send = (statusCode: number, code: string, message: string, details?: unknown) =>
      reply.status(statusCode).send({ error: { code, message, details }, requestId: request.id })

    if (error instanceof AppError) {
      return send(error.statusCode, error.code, error.message, error.details)
    }
    if ('validation' in error && error.validation) {
      return send(400, 'validation_error', error.message, error.validation)
    }
    if (error instanceof StorageUnavailableError) {
      request.log.error({ err: error }, 'object storage unavailable')
      return send(503, 'storage_unavailable', 'Object storage is temporarily unavailable')
    }
    if (isPostgresError(error)) {
      if (error.code === '23505') return send(409, 'unique_violation', 'Resource already exists')
      if (error.code === '22P02') return send(400, 'invalid_input', 'Invalid input syntax')
    }
    const fastifyError = error as Partial<FastifyError>
    if (typeof fastifyError.statusCode === 'number' && fastifyError.statusCode < 500) {
      return send(fastifyError.statusCode, fastifyError.code ?? 'bad_request', error.message)
    }

    request.log.error({ err: error }, 'unhandled error')
    return send(500, 'internal_error', 'Internal server error')
  })

  app.setNotFoundHandler((request, reply) => {
    reply.status(404).send({
      error: { code: 'route_not_found', message: `Route ${request.method} ${request.url} not found` },
      requestId: request.id,
    })
  })
}
