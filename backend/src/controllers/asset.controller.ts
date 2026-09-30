import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type { createAssetSchema, deleteAssetSchema, getAssetSchema, listAssetsSchema } from '../schemas/asset.schema.ts'
import type { AssetService } from '../services/asset.service.ts'
import type { RequestFor } from './types.ts'

export class AssetController {
  constructor(private readonly assets: AssetService) {}

  create = async (request: RequestFor<typeof createAssetSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    return reply.status(201).send(await this.assets.createPending(request.params.workspaceId, user.id, request.body))
  }

  list = async (request: RequestFor<typeof listAssetsSchema>) => {
    const user = requireUser(request)
    return { assets: await this.assets.list(request.params.workspaceId, user.id) }
  }

  get = async (request: RequestFor<typeof getAssetSchema>) => {
    const user = requireUser(request)
    return this.assets.get(request.params.workspaceId, user.id, request.params.assetId)
  }

  delete = async (request: RequestFor<typeof deleteAssetSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    await this.assets.delete(request.params.workspaceId, user.id, request.params.assetId)
    return reply.status(204).send()
  }
}
