import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type { listMembersSchema, revokeMemberSchema, setMemberSchema } from '../schemas/workspace.schema.ts'
import type { MemberService } from '../services/member.service.ts'
import type { RequestFor } from './types.ts'

export class MemberController {
  constructor(private readonly members: MemberService) {}

  list = async (request: RequestFor<typeof listMembersSchema>) => {
    const user = requireUser(request)
    return { members: await this.members.list(request.params.workspaceId, user.id) }
  }

  set = async (request: RequestFor<typeof setMemberSchema>) => {
    const user = requireUser(request)
    const { workspaceId, userId } = request.params
    return this.members.setRole(workspaceId, user.id, userId, request.body.role)
  }

  revoke = async (request: RequestFor<typeof revokeMemberSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const { workspaceId, userId } = request.params
    await this.members.revoke(workspaceId, user.id, userId)
    return reply.status(204).send()
  }
}
