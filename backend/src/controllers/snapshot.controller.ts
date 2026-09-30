import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type { createSnapshotSchema, getStateSchema, latestSnapshotSchema } from '../schemas/snapshot.schema.ts'
import type { SnapshotService } from '../services/snapshot.service.ts'
import type { RequestFor } from './types.ts'

export class SnapshotController {
  constructor(private readonly snapshots: SnapshotService) {}

  state = async (request: RequestFor<typeof getStateSchema>) => {
    const user = requireUser(request)
    return this.snapshots.getState(request.params.workspaceId, user.id)
  }

  create = async (request: RequestFor<typeof createSnapshotSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    return reply.status(201).send(await this.snapshots.create(request.params.workspaceId, user.id))
  }

  latest = async (request: RequestFor<typeof latestSnapshotSchema>) => {
    const user = requireUser(request)
    return this.snapshots.latest(request.params.workspaceId, user.id)
  }
}
