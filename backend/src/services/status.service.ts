import type { Database } from '../repositories/index.ts'

export interface ServiceStatus {
  name: string
  version: string
  uptimeSeconds: number
  database: 'up' | 'down'
  stats: { workspaces: number; operations: number; outboxBacklog: number } | null
}

const startedAt = Date.now()

export class StatusService {
  constructor(
    private readonly db: Database,
    private readonly version: string,
  ) {}

  async status(): Promise<ServiceStatus> {
    const up = await this.db.ping()
    const { workspaces, operations, outbox } = this.db.repos
    return {
      name: 'boarderless-backend',
      version: this.version,
      uptimeSeconds: Math.round((Date.now() - startedAt) / 1000),
      database: up ? 'up' : 'down',
      stats: up
        ? {
            workspaces: await workspaces.count(),
            operations: await operations.count(),
            outboxBacklog: await outbox.countUnpublished(),
          }
        : null,
    }
  }
}
