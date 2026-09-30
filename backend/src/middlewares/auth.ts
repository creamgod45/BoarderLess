import type { FastifyInstance, FastifyRequest } from 'fastify'
import type { User } from '../models/user.ts'
import type { UserService } from '../services/user.service.ts'
import { unauthorized } from '../utils/errors.ts'

declare module 'fastify' {
  interface FastifyRequest {
    user: User | null
  }
}

export const DEV_USER_HEADER = 'x-user-id'
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * AUTH_MODE=dev：以 `x-user-id` header 辨識使用者。
 * TODO(B1)：改為短效 access token（JWT / session）驗證，refresh token 與 provider identity 分表保存（§9）。
 */
export function registerDevAuth(app: FastifyInstance, users: UserService): void {
  app.decorateRequest('user', null)
  app.addHook('onRequest', async (request) => {
    const header = request.headers[DEV_USER_HEADER]
    const id = Array.isArray(header) ? header[0] : header
    if (id && UUID_RE.test(id)) {
      request.user = await users.findActive(id)
    }
  })
}

export function requireUser(request: FastifyRequest): User {
  if (!request.user) throw unauthorized()
  return request.user
}
