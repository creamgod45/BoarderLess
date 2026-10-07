import type { JobHandler } from './context.ts'

/** 回收 storage 物件（abandon / GC / 已驗證後的暫存上傳）；delete 本身冪等 */
export const deleteObject: JobHandler = async (job, ctx) => {
  const key = job.payload.key
  if (typeof key !== 'string' || key === '') return
  await ctx.storage.delete(key)
}
