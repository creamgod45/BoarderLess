import { hostname } from 'node:os'
import type { WorkerConfig } from '../config/env.ts'
import type { AssetJob, JobType } from '../models/job.ts'
import type { JobContext, JobHandler } from './context.ts'
import { deleteObject } from './delete-object.job.ts'
import { generateThumbnail } from './generate-thumbnail.job.ts'
import { collectExpiredUploads } from './upload-gc.ts'
import { verifyOriginal } from './verify-original.job.ts'

export const JOB_HANDLERS: Record<JobType, JobHandler> = {
  verify_original: verifyOriginal,
  generate_thumbnail: generateThumbnail,
  delete_object: deleteObject,
}

export type RunnerOptions = Pick<WorkerConfig, 'concurrency' | 'pollIntervalMs' | 'leaseSeconds' | 'gcIntervalMs'>

const retryDelaySeconds = (attempts: number) => Math.min(300, 5 * 2 ** Math.max(0, attempts - 1))

/**
 * 從 PostgreSQL job queue 領取並執行工作。concurrency 決定同時執行的 job 數，
 * 也就是同時運行的 ffmpeg / ffprobe process 上限。
 */
export class JobRunner {
  readonly workerId: string
  private running = false
  private readonly loops: Promise<void>[] = []
  private gcTimer: ReturnType<typeof setInterval> | null = null

  constructor(
    private readonly ctx: JobContext,
    private readonly options: RunnerOptions,
    private readonly handlers: Record<JobType, JobHandler> = JOB_HANDLERS,
    workerId?: string,
  ) {
    this.workerId = workerId ?? `${hostname()}:${process.pid}:${crypto.randomUUID().slice(0, 8)}`
  }

  start(): void {
    if (this.running) return
    this.running = true
    for (let i = 0; i < this.options.concurrency; i++) this.loops.push(this.loop())
    this.gcTimer = setInterval(() => {
      collectExpiredUploads(this.ctx).catch((err) => this.ctx.log.error({ err }, 'upload gc failed'))
    }, this.options.gcIntervalMs)
  }

  /** 停止領取新 job，等待執行中的 job 結束 */
  async stop(): Promise<void> {
    this.running = false
    if (this.gcTimer) clearInterval(this.gcTimer)
    await Promise.all(this.loops)
    this.loops.length = 0
  }

  /** 依序執行目前所有可執行的 job（測試 / 一次性批次用），回傳處理數 */
  async drain(limit = 1000): Promise<number> {
    let processed = 0
    while (processed < limit && (await this.runOnce())) processed++
    return processed
  }

  /** 領取並執行一個 job；沒有可執行 job 時回傳 false */
  async runOnce(): Promise<boolean> {
    const job = await this.ctx.db.repos.jobs.claim(this.workerId, this.options.leaseSeconds)
    if (!job) return false
    await this.execute(job)
    return true
  }

  private async loop(): Promise<void> {
    while (this.running) {
      try {
        if (!(await this.runOnce())) await sleep(this.options.pollIntervalMs)
      } catch (err) {
        this.ctx.log.error({ err }, 'job loop error')
        await sleep(this.options.pollIntervalMs)
      }
    }
  }

  private async execute(job: AssetJob): Promise<void> {
    const { jobs } = this.ctx.db.repos
    const heartbeat = setInterval(
      () => void jobs.extendLease(job.id, this.workerId, this.options.leaseSeconds).catch(() => {}),
      Math.max(1000, (this.options.leaseSeconds * 1000) / 3),
    )
    try {
      await this.handlers[job.jobType](job, this.ctx)
      await jobs.complete(job.id, this.workerId)
    } catch (err) {
      const message = err instanceof Error ? `${err.name}: ${err.message}` : String(err)
      const status = await jobs.fail(job.id, this.workerId, message, retryDelaySeconds(job.attempts))
      const log = status === 'dead' ? this.ctx.log.error : this.ctx.log.warn
      log.call(this.ctx.log, { err, jobId: job.id, jobType: job.jobType, attempts: job.attempts, status }, 'job failed')
    } finally {
      clearInterval(heartbeat)
    }
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
