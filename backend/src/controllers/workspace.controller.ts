import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type {
  createWorkspaceSchema,
  deleteWorkspaceSchema,
  getWorkspaceSchema,
  listWorkspacesSchema,
  updateWorkspaceSchema,
} from '../schemas/workspace.schema.ts'
import type { WorkspaceService } from '../services/workspace.service.ts'
import type { RequestFor } from './types.ts'

export class WorkspaceController {
  constructor(private readonly workspaces: WorkspaceService) {}

  list = async (request: RequestFor<typeof listWorkspacesSchema>) => {
    const user = requireUser(request)
    return { workspaces: await this.workspaces.listForUser(user.id) }
  }

  create = async (request: RequestFor<typeof createWorkspaceSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const workspace = await this.workspaces.create(user.id, request.body.title)
    return reply.status(201).send(workspace)
  }

  get = async (request: RequestFor<typeof getWorkspaceSchema>) => {
    const user = requireUser(request)
    return this.workspaces.get(request.params.workspaceId, user.id)
  }

  update = async (request: RequestFor<typeof updateWorkspaceSchema>) => {
    const user = requireUser(request)
    return this.workspaces.rename(request.params.workspaceId, user.id, request.body.title)
  }

  delete = async (request: RequestFor<typeof deleteWorkspaceSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    await this.workspaces.delete(request.params.workspaceId, user.id)
    return reply.status(204).send()
  }
}
