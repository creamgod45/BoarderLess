import type { FastifySchemaCompiler } from 'fastify'
import type { TSchema } from 'typebox'
import { Compile } from 'typebox/compile'

/**
 * 所有路由的 request 驗證（CORE-02）：
 * - body：嚴格驗證，不做型別轉換、不移除未知欄位、不補預設值（"42" 不是數字）。
 * - querystring / params：URL 上的值都是字串，先套預設值再依 schema 轉型後驗證。
 */
export const requestValidatorCompiler: FastifySchemaCompiler<TSchema> = ({ schema, httpPart }) => {
  const validator = Compile(schema)
  const coerce = httpPart !== 'body'
  return (data) => {
    const value = coerce ? validator.Convert(validator.Default(structuredClone(data ?? {}))) : data
    if (validator.Check(value)) return { value }
    return { error: [...validator.Errors(value)] as never }
  }
}
