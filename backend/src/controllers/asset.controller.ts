import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type {
  abandonAssetSchema,
  assetContentSchema,
  completeAssetSchema,
  createAssetSchema,
  getAssetSchema,
  listAssetsSchema,
} from '../schemas/asset.schema.ts'
import type { AssetService } from '../services/asset.service.ts'
import type { RequestFor } from './types.ts'

export class AssetController {
  constructor(private readonly assets: AssetService) {}

  prepare = async (request: RequestFor<typeof createAssetSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    return reply.status(201).send(await this.assets.prepare(request.params.workspaceId, user.id, request.body))
  }

  complete = async (request: RequestFor<typeof completeAssetSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const { workspaceId, assetId } = request.params
    const result = await this.assets.complete(workspaceId, user.id, assetId, request.body)
    return reply.status(result.asset.status === 'pending' ? 202 : 200).send(result)
  }

  list = async (request: RequestFor<typeof listAssetsSchema>) => {
    const user = requireUser(request)
    return { assets: await this.assets.list(request.params.workspaceId, user.id) }
  }

  get = async (request: RequestFor<typeof getAssetSchema>) => {
    const user = requireUser(request)
    return this.assets.get(request.params.workspaceId, user.id, request.params.assetId)
  }

  content = async (request: RequestFor<typeof assetContentSchema>) => {
    const user = requireUser(request)
    return this.assets.content(request.params.workspaceId, user.id, request.params.assetId)
  }

  abandon = async (request: RequestFor<typeof abandonAssetSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    await this.assets.abandon(request.params.workspaceId, user.id, request.params.assetId)
    return reply.status(204).send()
  }
}
