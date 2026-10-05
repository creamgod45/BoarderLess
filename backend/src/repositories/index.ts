import type postgres from 'postgres'
import type { Db } from '../db/client.ts'
import { AssetRepository } from './asset.repository.ts'
import { CanvasRepository } from './canvas.repository.ts'
import { FenceRepository } from './fence.repository.ts'
import { JobRepository } from './job.repository.ts'
import { MemberRepository } from './member.repository.ts'
import { OperationRepository } from './operation.repository.ts'
import { OutboxRepository } from './outbox.repository.ts'
import { SnapshotRepository } from './snapshot.repository.ts'
import { UserRepository } from './user.repository.ts'
import { WorkspaceRepository } from './workspace.repository.ts'

export interface Repositories {
  users: UserRepository
  workspaces: WorkspaceRepository
  members: MemberRepository
  canvas: CanvasRepository
  operations: OperationRepository
  outbox: OutboxRepository
  snapshots: SnapshotRepository
  assets: AssetRepository
  jobs: JobRepository
  fences: FenceRepository
}

export function createRepositories(db: Db): Repositories {
  return {
    users: new UserRepository(db),
    workspaces: new WorkspaceRepository(db),
    members: new MemberRepository(db),
    canvas: new CanvasRepository(db),
    operations: new OperationRepository(db),
    outbox: new OutboxRepository(db),
    snapshots: new SnapshotRepository(db),
    assets: new AssetRepository(db),
    jobs: new JobRepository(db),
    fences: new FenceRepository(db),
  }
}

/** Unit of work：repositories 綁定一般連線，transaction() 內則綁定同一個 transaction。 */
export class Database {
  readonly repos: Repositories

  constructor(readonly sql: postgres.Sql) {
    this.repos = createRepositories(sql)
  }

  transaction<T>(fn: (repos: Repositories) => Promise<T>): Promise<T> {
    return this.sql.begin((tx) => fn(createRepositories(tx))) as Promise<T>
  }

  /** 唯讀、一致的讀取視圖（projection + version 必須來自同一時間點） */
  readConsistent<T>(fn: (repos: Repositories) => Promise<T>): Promise<T> {
    return this.sql.begin('read only isolation level repeatable read', (tx) => fn(createRepositories(tx))) as Promise<T>
  }

  async ping(): Promise<boolean> {
    try {
      await this.sql`SELECT 1`
      return true
    } catch {
      return false
    }
  }
}
