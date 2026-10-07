import type { MediaConfig } from '../config/env.ts'
import type { MediaTools } from '../media/ffmpeg.ts'
import type { AssetJob } from '../models/job.ts'
import type { Database } from '../repositories/index.ts'
import type { ObjectStorage } from '../storage/types.ts'

export interface JobLogger {
  info(obj: object, msg?: string): void
  warn(obj: object, msg?: string): void
  error(obj: object, msg?: string): void
}

export interface JobContext {
  db: Database
  storage: ObjectStorage
  tools: MediaTools
  media: Pick<MediaConfig, 'tmpDir' | 'uploadGcGraceSeconds'>
  jobMaxAttempts: number
  log: JobLogger
}

/** handler 必須冪等：job 至少執行一次，lease 過期後可能由另一個 worker 重跑 */
export type JobHandler = (job: AssetJob, ctx: JobContext) => Promise<void>
