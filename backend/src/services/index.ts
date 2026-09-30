import type { Database } from '../repositories/index.ts'
import { AssetService } from './asset.service.ts'
import { MemberService } from './member.service.ts'
import { OperationService } from './operation.service.ts'
import { SnapshotService } from './snapshot.service.ts'
import { StatusService } from './status.service.ts'
import { UserService } from './user.service.ts'
import { WorkspaceService } from './workspace.service.ts'

export interface Services {
  users: UserService
  workspaces: WorkspaceService
  members: MemberService
  operations: OperationService
  snapshots: SnapshotService
  assets: AssetService
  status: StatusService
}

export function createServices(db: Database, version: string): Services {
  return {
    users: new UserService(db),
    workspaces: new WorkspaceService(db),
    members: new MemberService(db),
    operations: new OperationService(db),
    snapshots: new SnapshotService(db),
    assets: new AssetService(db),
    status: new StatusService(db, version),
  }
}
