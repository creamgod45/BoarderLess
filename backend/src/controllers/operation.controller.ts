import type { FastifyReply } from 'fastify'
import { requireUser } from '../middlewares/auth.ts'
import type {
  fenceTransactionsSchema,
  listOperationsSchema,
  lookupReceiptsSchema,
  submitOperationsSchema,
} from '../schemas/operation.schema.ts'
import type { OperationService } from '../services/operation.service.ts'
import type { ReceiptService } from '../services/receipt.service.ts'
import type { RequestFor } from './types.ts'

export class OperationController {
  constructor(
    private readonly operations: OperationService,
    private readonly receipts: ReceiptService,
  ) {}

  submit = async (request: RequestFor<typeof submitOperationsSchema>, reply: FastifyReply) => {
    const user = requireUser(request)
    const result = await this.operations.submit(user.id, {
      ...request.body,
      workspaceId: request.params.workspaceId,
    })
    return reply.status(result.status === 'conflict' ? 409 : 200).send(result)
  }

  list = async (request: RequestFor<typeof listOperationsSchema>) => {
    const user = requireUser(request)
    const { afterSeq, limit } = request.query
    return this.operations.listAfter(request.params.workspaceId, user.id, afterSeq, limit)
  }

  lookupReceipts = async (request: RequestFor<typeof lookupReceiptsSchema>) => {
    const user = requireUser(request)
    return this.receipts.lookup(request.params.workspaceId, user.id, request.body)
  }

  fence = async (request: RequestFor<typeof fenceTransactionsSchema>) => {
    const user = requireUser(request)
    return this.receipts.fence(request.params.workspaceId, user.id, request.body)
  }
}
