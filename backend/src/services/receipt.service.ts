import type { CommittedOperation } from '../models/operation.ts'
import type { FenceKey, FenceKind } from '../repositories/fence.repository.ts'
import type { Database, Repositories } from '../repositories/index.ts'
import { badRequest } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export const MAX_RECEIPT_IDS = 200

export interface ReceiptQuery {
  transactionIds?: string[]
  operationIds?: string[]
}

/** 一次 commit 的完整交易邊界；不含 payload */
export interface TransactionReceipt {
  transactionId: string
  actorId: string
  clientId: string
  workspaceVersion: number
  fromServerSeq: number
  toServerSeq: number
  committedAt: Date
  operations: { operationId: string; clientSeq: number; serverSeq: number; kind: string }[]
}

export type LookupStatus = 'committed' | 'fenced' | 'unknown'

export interface ReceiptLookup {
  type: FenceKind
  id: string
  status: LookupStatus
  /** committed 時所屬 commit */
  transactionId?: string
  workspaceVersion?: number
}

export interface ReceiptResponse {
  workspaceId: string
  /** 查詢當下的 head；unknown 只代表「截至此 head 未提交」，不排除晚到的原請求（需 fence） */
  headServerSeq: number
  headWorkspaceVersion: number
  receipts: TransactionReceipt[]
  lookups: ReceiptLookup[]
}

/**
 * 原提交的權威 reconciliation（BAI-012）。
 * - receipts：以目前使用者 + Workspace ACL 查自己的原 transaction / operation ID
 * - fence：對尚未提交的 ID 建立 fence，保證原請求之後永遠不會 commit
 */
export class ReceiptService {
  constructor(private readonly db: Database) {}

  lookup(workspaceId: string, userId: string, query: ReceiptQuery): Promise<ReceiptResponse> {
    const ids = normalize(query)
    return this.db.readConsistent(async (repos) => {
      const { workspace } = await requireWorkspaceAccess(repos, workspaceId, userId, 'workspace.read')
      return build(repos, workspaceId, userId, ids, workspace.lastServerSeq, workspace.currentVersion)
    })
  }

  /** 與 submit 取同一個 Workspace row lock：fence 與原請求的 commit 必有先後，不會兩者都成立 */
  fence(workspaceId: string, userId: string, query: ReceiptQuery): Promise<ReceiptResponse> {
    const ids = normalize(query)
    return this.db.transaction(async (repos) => {
      const { workspace } = await requireWorkspaceAccess(repos, workspaceId, userId, 'operations.submit', { lock: true })
      const before = await build(repos, workspaceId, userId, ids, workspace.lastServerSeq, workspace.currentVersion)
      const toFence: FenceKey[] = before.lookups.filter((l) => l.status === 'unknown').map((l) => ({ kind: l.type, id: l.id }))
      await repos.fences.insert(workspaceId, userId, toFence)
      return {
        ...before,
        lookups: before.lookups.map((l) => (l.status === 'unknown' ? { ...l, status: 'fenced' as const } : l)),
      }
    })
  }
}

function normalize(query: ReceiptQuery): { transactionIds: string[]; operationIds: string[] } {
  const transactionIds = [...new Set(query.transactionIds ?? [])]
  const operationIds = [...new Set(query.operationIds ?? [])]
  const total = transactionIds.length + operationIds.length
  if (total === 0 || total > MAX_RECEIPT_IDS) {
    throw badRequest('invalid_receipt_query', `Provide 1–${MAX_RECEIPT_IDS} transaction / operation IDs`)
  }
  return { transactionIds, operationIds }
}

async function build(
  repos: Repositories,
  workspaceId: string,
  actorId: string,
  ids: { transactionIds: string[]; operationIds: string[] },
  headServerSeq: number,
  headWorkspaceVersion: number,
): Promise<ReceiptResponse> {
  const rows = await repos.operations.findForReceipts(workspaceId, actorId, ids.transactionIds, ids.operationIds)
  const receipts = groupReceipts(rows)
  const fenced = await repos.fences.findFenced(workspaceId, actorId, ids.transactionIds, ids.operationIds)
  const isFenced = (type: FenceKind, id: string) => fenced.some((f) => f.kind === type && f.id === id)

  const lookup = (type: FenceKind, id: string): ReceiptLookup => {
    const receipt =
      type === 'transaction'
        ? receipts.find((r) => r.transactionId === id)
        : receipts.find((r) => r.operations.some((o) => o.operationId === id))
    if (receipt) return { type, id, status: 'committed', transactionId: receipt.transactionId, workspaceVersion: receipt.workspaceVersion }
    return { type, id, status: isFenced(type, id) ? 'fenced' : 'unknown' }
  }

  return {
    workspaceId,
    headServerSeq,
    headWorkspaceVersion,
    receipts,
    lookups: [
      ...ids.transactionIds.map((id) => lookup('transaction', id)),
      ...ids.operationIds.map((id) => lookup('operation', id)),
    ],
  }
}

/** 以 (transactionId, workspaceVersion) 分組：每個 commit 一張完整 receipt */
function groupReceipts(rows: CommittedOperation[]): TransactionReceipt[] {
  const map = new Map<string, TransactionReceipt>()
  for (const row of rows) {
    const key = `${row.transactionId}:${row.workspaceVersion}`
    let receipt = map.get(key)
    if (!receipt) {
      receipt = {
        transactionId: row.transactionId,
        actorId: row.actorId,
        clientId: row.clientId,
        workspaceVersion: row.workspaceVersion,
        fromServerSeq: row.serverSeq,
        toServerSeq: row.serverSeq,
        committedAt: row.committedAt,
        operations: [],
      }
      map.set(key, receipt)
    }
    receipt.toServerSeq = row.serverSeq
    receipt.operations.push({
      operationId: row.operationId,
      clientSeq: row.clientSeq,
      serverSeq: row.serverSeq,
      kind: row.operationType,
    })
  }
  return [...map.values()].sort((a, b) => a.fromServerSeq - b.fromServerSeq)
}
