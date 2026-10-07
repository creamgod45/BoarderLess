import type { Database } from '../repositories/index.ts'
import type { ObjectStorage } from '../storage/types.ts'
import { AssetService, type AssetServiceOptions } from './asset.service.ts'
import { MemberService } from './member.service.ts'
import { OperationService } from './operation.service.ts'
import { ReceiptService } from './receipt.service.ts'
import { SnapshotService } from './snapshot.service.ts'
import { StatusService } from './status.service.ts'
import { UserService } from './user.service.ts'
import { WorkspaceService } from './workspace.service.ts'

export interface Services {
  users: UserService
  workspaces: WorkspaceService
  members: MemberService
  operations: OperationService
  receipts: ReceiptService
  snapshots: SnapshotService
  assets: AssetService
  status: StatusService
}

export interface ServiceDependencies {
  db: Database
  storage: ObjectStorage
  version: string
  assets: AssetServiceOptions
}

export function createServices({ db, storage, version, assets }: ServiceDependencies): Services {
  return {
    users: new UserService(db),
    workspaces: new WorkspaceService(db),
    members: new MemberService(db),
    operations: new OperationService(db),
    receipts: new ReceiptService(db),
    snapshots: new SnapshotService(db),
    assets: new AssetService(db, storage, assets),
    status: new StatusService(db, version, storage.driver),
  }
}
