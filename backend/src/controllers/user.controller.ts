import type { FastifyReply, FastifyRequest } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type { createUserSchema } from '../schemas/user.schema.ts'
import type { UserService } from '../services/user.service.ts'
import type { RequestFor } from './types.ts'

export class UserController {
  constructor(private readonly users: UserService) {}

  create = async (request: RequestFor<typeof createUserSchema>, reply: FastifyReply) => {
    const user = await this.users.create(request.body.displayName)
    return reply.status(201).send(user)
  }

  me = async (request: FastifyRequest) => requireUser(request)
}
