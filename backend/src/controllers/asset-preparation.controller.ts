import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import { ASSET_MEDIA_TYPES, MAX_ASSET_BYTES } from '../models/asset.ts'
import { PREPARATION_RESPONSE_SCHEMA, PREPARE_REQUEST_SCHEMA } from '../models/asset-preparation.ts'
import type {
  completePreparationSchema,
  createPreparationSchema,
  getPreparationSchema,
  issueTicketSchema,
} from '../schemas/asset-preparation.schema.ts'
import type { AssetPreparationService } from '../services/asset-preparation.service.ts'
import type { RequestFor } from './types.ts'

export class AssetPreparationController {
  constructor(private readonly preparations: AssetPreparationService) {}

  prepare = async (request: RequestFor<typeof createPreparationSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const { requestId, metadata } = request.body
    const result = await this.preparations.prepare(request.params.workspaceId, user.id, { requestId, metadata })
    return reply.status(result.created ? 201 : 200).send(result.envelope)
  }

  get = async (request: RequestFor<typeof getPreparationSchema>) => {
    const user = requireUser(request)
    return this.preparations.get(request.params.workspaceId, user.id, request.params.requestId)
  }

  issueTicket = async (request: RequestFor<typeof issueTicketSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const { workspaceId, requestId } = request.params
    const result = await this.preparations.issueTicket(workspaceId, user.id, requestId, request.body.ticketRequestId)
    return reply.status(result.created ? 201 : 200).send(result.envelope)
  }

  complete = async (request: RequestFor<typeof completePreparationSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const { workspaceId, requestId } = request.params
    const envelope = await this.preparations.complete(workspaceId, user.id, requestId, request.body)
    return reply.status(envelope.phase === 'completionAccepted' ? 202 : 200).send(envelope)
  }

  /** 公開的能力宣告：不需要身分 */
  capabilities = async () => ({
    assetPreparation: {
      requestSchema: PREPARE_REQUEST_SCHEMA,
      responseSchema: PREPARATION_RESPONSE_SCHEMA,
      maxByteSize: MAX_ASSET_BYTES,
      mediaTypes: Object.keys(ASSET_MEDIA_TYPES),
    },
  })
}
