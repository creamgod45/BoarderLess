import type { FastifyRequest } from 'fastify'
import type { Static, TSchema } from 'typebox'

type Part<S, K extends string> = S extends { [P in K]: infer T extends TSchema } ? Static<T> : unknown

/** 由 route schema 推導 controller 的 request 型別 */
export type RequestFor<S extends object> = FastifyRequest<{
  Params: Part<S, 'params'>
  Body: Part<S, 'body'>
  Querystring: Part<S, 'querystring'>
}>
