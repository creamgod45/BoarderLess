import { validateOperationPayload, type PayloadValidationError, type TypedOperation } from '../domain/operations.ts'
import type { CommittedOperation } from '../models/operation.ts'
import type { Database, Repositories } from '../repositories/index.ts'
import { conflict, unprocessable } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'
import { applyOperation, checkExpectedVersions, VersionConflictSignal, type VersionConflict } from './canvas-mutations.ts'

export type { VersionConflict } from './canvas-mutations.ts'

export const PROTOCOL_VERSION = 1
export const OPERATION_SCHEMA_VERSION = 1
const MAX_CATCH_UP = 1000

export interface SubmitOperationInput {
  operationId: string
  clientSeq: number
  kind: string
  expectedObjectVersions?: Record<string, number>
  payload: unknown
}

export interface SubmitOperationsInput {
  protocolVersion: number
  workspaceId: string
  clientId: string
  transactionId: string
  baseVersion: number
  operations: SubmitOperationInput[]
}

export interface CommittedResult {
  status: 'accepted' | 'duplicate'
  fromServerSeq: number
  toServerSeq: number
  workspaceVersion: number
  operations: CommittedOperation[]
}

export interface ConflictResult {
  status: 'conflict'
  conflicts: VersionConflict[]
  workspaceVersion: number
  lastServerSeq: number
  /** baseVersion 之後的正式 operations，供 client rebase */
  missingOperations: CommittedOperation[]
}

export type SubmitResult = CommittedResult | ConflictResult

const rejected = (code: string, message: string, details?: unknown) => unprocessable(code, message, details)

export class OperationService {
  constructor(private readonly db: Database) {}

  async submit(actorId: string, input: SubmitOperationsInput): Promise<SubmitResult> {
    if (input.protocolVersion !== PROTOCOL_VERSION) {
      throw rejected('unsupported_protocol_version', `Supported protocolVersion is ${PROTOCOL_VERSION}`)
    }
    const operations = this.validate(input.operations)

    try {
      return await this.db.transaction((repos) => this.commit(repos, actorId, input, operations))
    } catch (error) {
      if (!(error instanceof VersionConflictSignal)) throw error
      return this.buildConflict(input.workspaceId, input.baseVersion, error.conflicts)
    }
  }

  /** client 斷線補資料：依 serverSeq 追回（§8.2） */
  async listAfter(workspaceId: string, userId: string, afterSeq: number, limit: number) {
    const { workspace } = await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const operations = await this.db.repos.operations.listAfterSeq(workspaceId, afterSeq, Math.min(limit, MAX_CATCH_UP))
    const last = operations.at(-1)?.serverSeq ?? afterSeq
    return {
      operations,
      lastServerSeq: workspace.lastServerSeq,
      hasMore: last < workspace.lastServerSeq,
    }
  }

  private validate(inputs: SubmitOperationInput[]): { input: SubmitOperationInput; op: TypedOperation }[] {
    const errors: (PayloadValidationError & { index: number })[] = []
    const seen = new Set<string>()
    const result: { input: SubmitOperationInput; op: TypedOperation }[] = []

    inputs.forEach((input, index) => {
      if (seen.has(input.operationId)) {
        errors.push({ index, path: '/operationId', message: 'duplicate operationId in transaction' })
      }
      seen.add(input.operationId)
      const validation = validateOperationPayload(input.kind, input.payload)
      if (validation.ok) result.push({ input, op: validation.operation })
      else errors.push(...validation.errors.map((e) => ({ index, ...e })))
    })

    if (errors.length > 0) throw rejected('invalid_operation', 'One or more operations are invalid', errors)
    return result
  }

  private async commit(
    repos: Repositories,
    actorId: string,
    input: SubmitOperationsInput,
    operations: { input: SubmitOperationInput; op: TypedOperation }[],
  ): Promise<CommittedResult> {
    // 1–2. transaction + Workspace row lock；在鎖內重新檢查權限
    const { workspace } = await requireWorkspaceAccess(repos, input.workspaceId, actorId, 'operations.submit', {
      lock: true,
    })

    // 3. idempotency：依 operation ID（不是連線 ID）去重
    const operationIds = operations.map((o) => o.input.operationId)
    const existing = await repos.operations.findByOperationIds(workspace.id, actorId, operationIds)
    if (existing.length === operations.length) return toResult('duplicate', existing)
    if (existing.length > 0) {
      throw rejected('partial_duplicate', 'Some operations in this transaction were already committed', {
        committedOperationIds: existing.map((o) => o.operationId),
      })
    }
    // BAI-012：已 fence 的原請求永遠不會 commit
    const fenced = await repos.fences.findFenced(workspace.id, actorId, [input.transactionId], operationIds)
    if (fenced.length > 0) {
      throw conflict('transaction_fenced', 'This transaction was fenced and can no longer be committed', { fenced })
    }
    if (input.baseVersion > workspace.currentVersion) {
      throw rejected('invalid_base_version', 'baseVersion is ahead of the server', {
        workspaceVersion: workspace.currentVersion,
      })
    }

    // 4–5. 配發 serverSeq、更新 projection、寫入 operation log 與 outbox
    const workspaceVersion = workspace.currentVersion + 1
    let serverSeq = workspace.lastServerSeq
    const committed: CommittedOperation[] = []

    for (const { input: opInput, op } of operations) {
      await checkExpectedVersions(repos, workspace.id, op, opInput.expectedObjectVersions)
      const storedPayload = await applyOperation(repos, workspace.id, actorId, op)
      serverSeq += 1
      committed.push(
        await repos.operations.append({
          workspaceId: workspace.id,
          serverSeq,
          operationId: opInput.operationId,
          transactionId: input.transactionId,
          actorId,
          clientId: input.clientId,
          clientSeq: opInput.clientSeq,
          baseVersion: input.baseVersion,
          workspaceVersion,
          operationType: op.kind,
          payload: storedPayload,
          schemaVersion: OPERATION_SCHEMA_VERSION,
        }),
      )
    }

    await repos.workspaces.advance(workspace.id, workspaceVersion, serverSeq)
    const result = toResult('accepted', committed)
    await repos.outbox.enqueue(workspace.id, serverSeq, 'operations_committed', {
      transactionId: input.transactionId,
      actorId,
      clientId: input.clientId,
      fromServerSeq: result.fromServerSeq,
      toServerSeq: result.toServerSeq,
      workspaceVersion,
    })
    // 6. commit 由 Database.transaction 完成後才回覆 accepted
    return result
  }

  private async buildConflict(
    workspaceId: string,
    baseVersion: number,
    conflicts: VersionConflict[],
  ): Promise<ConflictResult> {
    return this.db.readConsistent(async (repos) => {
      const workspace = await repos.workspaces.findById(workspaceId)
      const missingOperations = await repos.operations.listAfterVersion(workspaceId, baseVersion, MAX_CATCH_UP)
      return {
        status: 'conflict',
        conflicts,
        workspaceVersion: workspace?.currentVersion ?? 0,
        lastServerSeq: workspace?.lastServerSeq ?? 0,
        missingOperations,
      }
    })
  }
}

function toResult(status: CommittedResult['status'], operations: CommittedOperation[]): CommittedResult {
  return {
    status,
    fromServerSeq: operations[0]!.serverSeq,
    toServerSeq: operations.at(-1)!.serverSeq,
    workspaceVersion: operations.at(-1)!.workspaceVersion,
    operations,
  }
}
