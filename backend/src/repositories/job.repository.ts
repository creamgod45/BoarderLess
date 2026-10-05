import { json, type Db } from '../db/client.ts'
import type { AssetJob, JobStatus, NewJob } from '../models/job.ts'

export class JobRepository {
  constructor(private readonly db: Db) {}

  /** 以 job_key 去重；已存在時不重複建立 */
  async enqueue(job: NewJob, maxAttempts: number): Promise<void> {
    await this.db`
      INSERT INTO asset_jobs (job_key, job_type, asset_id, payload, max_attempts)
      VALUES (${job.jobKey}, ${job.jobType}, ${job.assetId}, ${json(this.db, job.payload ?? {})}, ${maxAttempts})
      ON CONFLICT (job_key) DO NOTHING
    `
  }

  /**
   * 取得一個可執行的 job 並設定 lease。lease 過期的 running job（worker 當機）會被重新領取。
   * SKIP LOCKED 讓多個 worker / 核心同時領取不同 job。
   */
  async claim(workerId: string, leaseSeconds: number): Promise<AssetJob | null> {
    const [row] = await this.db<AssetJob[]>`
      UPDATE asset_jobs SET
        status = 'running',
        attempts = attempts + 1,
        lease_owner = ${workerId},
        lease_expires_at = now() + make_interval(secs => ${leaseSeconds}),
        updated_at = now()
      WHERE id = (
        SELECT id FROM asset_jobs
        WHERE (status = 'queued' AND run_after <= now())
           OR (status = 'running' AND lease_expires_at < now())
        ORDER BY run_after, id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
      )
      RETURNING *
    `
    return row ?? null
  }

  async extendLease(id: number, workerId: string, leaseSeconds: number): Promise<boolean> {
    const result = await this.db`
      UPDATE asset_jobs SET lease_expires_at = now() + make_interval(secs => ${leaseSeconds}), updated_at = now()
      WHERE id = ${id} AND lease_owner = ${workerId} AND status = 'running'
    `
    return result.count > 0
  }

  async complete(id: number, workerId: string): Promise<void> {
    await this.db`
      UPDATE asset_jobs SET status = 'done', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL,
        last_error = NULL, updated_at = now()
      WHERE id = ${id} AND lease_owner = ${workerId}
    `
  }

  /** 失敗：未達上限則延後重試（指數退避），否則進入 dead-letter */
  async fail(id: number, workerId: string, error: string, retryDelaySeconds: number): Promise<JobStatus | null> {
    const [row] = await this.db<{ status: JobStatus }[]>`
      UPDATE asset_jobs SET
        status = CASE WHEN attempts >= max_attempts THEN 'dead' ELSE 'queued' END,
        run_after = now() + make_interval(secs => ${retryDelaySeconds}),
        last_error = ${error.slice(0, 2000)},
        lease_owner = NULL,
        lease_expires_at = NULL,
        updated_at = now()
      WHERE id = ${id} AND lease_owner = ${workerId}
      RETURNING status
    `
    return row?.status ?? null
  }

  async findByKey(jobKey: string): Promise<AssetJob | null> {
    const [row] = await this.db<AssetJob[]>`SELECT * FROM asset_jobs WHERE job_key = ${jobKey}`
    return row ?? null
  }

  async listByAsset(assetId: string): Promise<AssetJob[]> {
    return this.db<AssetJob[]>`SELECT * FROM asset_jobs WHERE asset_id = ${assetId} ORDER BY id`
  }

  async countByStatus(): Promise<Record<JobStatus, number>> {
    const rows = await this.db<{ status: JobStatus; count: number }[]>`
      SELECT status, count(*)::int AS count FROM asset_jobs GROUP BY status
    `
    const counts: Record<JobStatus, number> = { queued: 0, running: 0, done: 0, dead: 0 }
    for (const row of rows) counts[row.status] = row.count
    return counts
  }
}
