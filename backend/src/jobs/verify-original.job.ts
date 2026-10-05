import { join } from 'node:path'
import { inspectMedia, type RejectionReason } from '../media/inspect.ts'
import { hashFile, streamToFile, withTempDir } from '../media/files.ts'
import { isAssetMediaType, type Asset } from '../models/asset.ts'
import { jobKeys } from '../models/job.ts'
import { enqueueDeleteObject } from '../services/asset.service.ts'
import { ObjectExistsError } from '../storage/types.ts'
import type { JobContext, JobHandler } from './context.ts'

export const THUMBNAIL_VERSION = 1
const NEEDS_THUMBNAIL = new Set(['image/gif', 'video/mp4', 'video/webm'])

/**
 * 驗證原檔：object 存在、實際 byte count、SHA-256、sniffed MIME、可解碼、尺寸與 duration。
 * 通過後把「已驗證的本機副本」寫入不可變 storageKey，再以 CAS 發布 ready。
 */
export const verifyOriginal: JobHandler = async (job, ctx) => {
  const asset = job.assetId ? await ctx.db.repos.assets.findById(job.assetId) : null
  if (!asset || asset.phase !== 'accepted') return // 已處理或已不適用：冪等結束
  const mediaType = asset.mediaType
  if (!isAssetMediaType(mediaType)) return reject(ctx, asset, 'unsupported_format')

  const uploadKey = asset.uploadKey!
  const info = await ctx.storage.head(uploadKey)
  if (!info) return reject(ctx, asset, 'upload_missing')
  if (info.size !== asset.byteSize) return reject(ctx, asset, 'byte_size_mismatch')

  await withTempDir(ctx.media.tmpDir, async (dir) => {
    const stream = await ctx.storage.getStream(uploadKey)
    if (!stream) return reject(ctx, asset, 'upload_missing')
    const file = await streamToFile(stream, join(dir, 'original'), asset.byteSize)
    if (file.truncated || file.byteCount !== asset.byteSize) return reject(ctx, asset, 'byte_size_mismatch')
    if (file.checksum !== asset.checksum) return reject(ctx, asset, 'checksum_mismatch')

    const result = await inspectMedia(file.path, mediaType, ctx.tools)
    if (!result.ok) return reject(ctx, asset, result.reason)

    await promote(ctx, asset, file.path)

    await ctx.db.transaction(async (repos) => {
      const ready = await repos.assets.markReady(asset.id, result)
      if (!ready) return
      if (NEEDS_THUMBNAIL.has(mediaType)) {
        await repos.jobs.enqueue(
          { jobKey: jobKeys.thumbnail(asset.id, THUMBNAIL_VERSION), jobType: 'generate_thumbnail', assetId: asset.id },
          ctx.jobMaxAttempts,
        )
      }
      await enqueueDeleteObject(repos, uploadKey, ctx.jobMaxAttempts)
    })
    ctx.log.info({ assetId: asset.id }, 'asset verified')
  })
}

/** 寫入不可變 key；先前嘗試已寫入時確認內容一致（防止覆寫已驗證內容） */
async function promote(ctx: JobContext, asset: Asset, path: string): Promise<void> {
  try {
    await ctx.storage.putFile(asset.storageKey, path, { contentType: asset.mediaType, ifNoneMatch: true })
  } catch (error) {
    if (!(error instanceof ObjectExistsError)) throw error
    const stream = await ctx.storage.getStream(asset.storageKey)
    if (!stream) throw new Error('promoted object disappeared during verification', { cause: error })
    const existing = await withTempDir(ctx.media.tmpDir, async (dir) => {
      const copy = await streamToFile(stream, join(dir, 'existing'), asset.byteSize)
      return hashFile(copy.path)
    })
    if (existing.checksum !== asset.checksum) {
      throw new Error('immutable object exists with different content', { cause: error })
    }
  }
}

async function reject(ctx: JobContext, asset: Asset, reason: RejectionReason): Promise<void> {
  await ctx.db.transaction(async (repos) => {
    if (await repos.assets.markRejected(asset.id, reason)) {
      await enqueueDeleteObject(repos, asset.uploadKey, ctx.jobMaxAttempts)
    }
  })
  ctx.log.warn({ assetId: asset.id, reason }, 'asset rejected')
}
