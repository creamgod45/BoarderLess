import pino from 'pino'
import { loadConfig } from './config/env.ts'
import { createSql } from './db/client.ts'
import { JobRunner } from './jobs/runner.ts'
import { MediaTools } from './media/ffmpeg.ts'
import { Database } from './repositories/index.ts'
import { createStorage } from './storage/index.ts'

/**
 * Background worker process：檔案驗證、縮圖 / poster、storage 清理與上傳 GC。
 * 與 API process 分開部署，媒體工具失敗或耗時不影響 API event loop（§6）。
 */
const config = loadConfig()
const log = pino({ level: config.logLevel, name: 'worker' })
const sql = createSql(config.databaseUrl, config.worker.concurrency + 2)
const storage = createStorage(config.storage)
await storage.init?.(config.corsOrigins)

const runner = new JobRunner(
  {
    db: new Database(sql),
    storage,
    tools: new MediaTools(config.media.ffmpegPath, config.media.ffprobePath),
    media: config.media,
    jobMaxAttempts: config.worker.maxAttempts,
    log,
  },
  config.worker,
)

const shutdown = async (signal: string) => {
  log.info({ signal }, 'worker shutting down; waiting for running jobs')
  await runner.stop()
  await sql.end({ timeout: 5 })
  process.exit(0)
}
process.on('SIGINT', () => void shutdown('SIGINT'))
process.on('SIGTERM', () => void shutdown('SIGTERM'))

runner.start()
log.info({ workerId: runner.workerId, concurrency: config.worker.concurrency, storage: storage.driver }, 'worker started')
