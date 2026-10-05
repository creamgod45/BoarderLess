import { randomUUID } from 'node:crypto'
import { join } from 'node:path'
import { hashFile, streamToFile, withTempDir } from '../media/files.ts'
import { enqueueDeleteObject, storageKeyFor } from '../services/asset.service.ts'
import type { JobHandler } from './context.ts'
import { THUMBNAIL_VERSION } from './verify-original.job.ts'

const THUMBNAIL_MAX_EDGE = 512
const DERIVATIVE_TYPE = 'thumbnail'

/**
 * GIF / 影片的靜態縮圖：另一筆 ready asset（自己的 ID、key、MIME、size、checksum、尺寸），
 * 由原檔 thumbnailAssetId 指向（§4.3）。失敗只重試此 job，不影響原檔 ready。
 */
export const generateThumbnail: JobHandler = async (job, ctx) => {
  const source = job.assetId ? await ctx.db.repos.assets.findById(job.assetId) : null
  if (!source || source.phase !== 'ready' || source.thumbnailAssetId || source.deletedAt) return

  const existing = await ctx.db.repos.assets.findDerived(source.id, DERIVATIVE_TYPE, THUMBNAIL_VERSION)
  if (existing) {
    await ctx.db.repos.assets.linkThumbnail(source.id, existing.id)
    return
  }

  await withTempDir(ctx.media.tmpDir, async (dir) => {
    const stream = await ctx.storage.getStream(source.storageKey)
    if (!stream) {
      await ctx.db.repos.assets.markMissing(source.id)
      ctx.log.warn({ assetId: source.id }, 'source content missing while generating thumbnail')
      return
    }
    const input = await streamToFile(stream, join(dir, 'source'), source.byteSize)

    const isVideo = source.mediaType.startsWith('video/')
    const format = isVideo ? 'jpeg' : 'png'
    const output = join(dir, `thumbnail.${format === 'jpeg' ? 'jpg' : 'png'}`)
    const seekMs = isVideo && source.durationMs ? Math.min(1000, Math.floor(source.durationMs / 2)) : 0
    const ok = await ctx.tools.extractFrame(input.path, output, { seekMs, maxEdge: THUMBNAIL_MAX_EDGE, format })
    if (!ok) throw new Error('thumbnail extraction failed')

    const probe = await ctx.tools.probe(output)
    if (!probe?.video) throw new Error('generated thumbnail could not be probed')
    const { byteCount, checksum } = await hashFile(output)
    const mediaType = format === 'jpeg' ? 'image/jpeg' : 'image/png'
    const id = randomUUID()
    const storageKey = storageKeyFor(source.workspaceId, id, mediaType)
    await ctx.storage.putFile(storageKey, output, { contentType: mediaType, ifNoneMatch: true })

    await ctx.db.transaction(async (repos) => {
      const derived = await repos.assets.insertDerived({
        id,
        workspaceId: source.workspaceId,
        ownerId: source.ownerId,
        storageKey,
        mediaType,
        byteSize: byteCount,
        checksum,
        width: probe.video!.width,
        height: probe.video!.height,
        sourceAssetId: source.id,
        derivativeType: DERIVATIVE_TYPE,
        derivativeVersion: THUMBNAIL_VERSION,
      })
      if (derived) {
        await repos.assets.linkThumbnail(source.id, derived.id)
        return
      }
      // 另一個 worker 已發布：連結既有縮圖，回收本次產生的物件
      const winner = await repos.assets.findDerived(source.id, DERIVATIVE_TYPE, THUMBNAIL_VERSION)
      if (winner) await repos.assets.linkThumbnail(source.id, winner.id)
      await enqueueDeleteObject(repos, storageKey, ctx.jobMaxAttempts)
    })
    ctx.log.info({ assetId: source.id }, 'thumbnail generated')
  })
}
