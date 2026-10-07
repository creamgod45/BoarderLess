import { Type, type TSchema } from 'typebox'

export const Uuid = Type.String({ format: 'uuid' })
export const JsonObject = Type.Record(Type.String(), Type.Unknown())
/** 回應中 Date 物件會被序列化成 ISO 字串 */
export const DateTime = Type.Unsafe<Date | string>({ type: 'string', format: 'date-time' })
export const Nullable = <T extends TSchema>(schema: T) => Type.Union([schema, Type.Null()])

export const ErrorEnvelope = Type.Object({
  error: Type.Object({
    code: Type.String(),
    message: Type.String(),
    details: Type.Optional(Type.Unknown()),
  }),
  requestId: Type.String(),
})

export const WorkspaceParams = Type.Object({ workspaceId: Uuid })

export const errorResponses = {
  400: ErrorEnvelope,
  401: ErrorEnvelope,
  403: ErrorEnvelope,
  404: ErrorEnvelope,
  422: ErrorEnvelope,
}
