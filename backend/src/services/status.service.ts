import type { AssetStatus } from '../models/asset.ts'
import type { JobStatus } from '../models/job.ts'
import type { Database } from '../repositories/index.ts'

export interface ServiceStatus {
  name: string
  version: string
  uptimeSeconds: number
  database: 'up' | 'down'
  storageDriver: string
  stats: {
    workspaces: number
    operations: number
    outboxBacklog: number
    assets: Record<AssetStatus, number>
    jobs: Record<JobStatus, number>
  } | null
}

const startedAt = Date.now()

export class StatusService {
  constructor(
    private readonly db: Database,
    private readonly version: string,
    private readonly storageDriver: string,
  ) {}

  async status(): Promise<ServiceStatus> {
    const up = await this.db.ping()
    const { workspaces, operations, outbox, assets, jobs } = this.db.repos
    return {
      name: 'boarderless-backend',
      version: this.version,
      uptimeSeconds: Math.round((Date.now() - startedAt) / 1000),
      database: up ? 'up' : 'down',
      storageDriver: this.storageDriver,
      stats: up
        ? {
            workspaces: await workspaces.count(),
            operations: await operations.count(),
            outboxBacklog: await outbox.countUnpublished(),
            assets: await assets.countByStatus(),
            jobs: await jobs.countByStatus(),
          }
        : null,
    }
  }
}
