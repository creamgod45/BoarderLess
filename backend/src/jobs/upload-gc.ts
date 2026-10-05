import { enqueueDeleteObject } from '../services/asset.service.ts'
import type { JobContext } from './context.ts'

/**
 * 只回收「upload ticket 過期且從未接受 complete」的上傳。
 * 已接受的 processing 素材由 job lease / retry / dead-letter 處理，不依 createdAt 清除（§5）。
 */
export async function collectExpiredUploads(ctx: JobContext, batchSize = 100): Promise<number> {
  return ctx.db.transaction(async (repos) => {
    const expired = await repos.assets.expireStaleUploads(ctx.media.uploadGcGraceSeconds, batchSize)
    for (const row of expired) await enqueueDeleteObject(repos, row.uploadKey, ctx.jobMaxAttempts)
    if (expired.length > 0) ctx.log.info({ count: expired.length }, 'expired stale uploads')
    return expired.length
  })
}
