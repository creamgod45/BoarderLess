import type { FastifyInstance } from 'fastify'
import type { TypeBoxTypeProvider } from '@fastify/type-provider-typebox'
import { AssetPreparationController } from '../controllers/asset-preparation.controller.ts'
import { AssetController } from '../controllers/asset.controller.ts'
import { MemberController } from '../controllers/member.controller.ts'
import { OperationController } from '../controllers/operation.controller.ts'
import { SnapshotController } from '../controllers/snapshot.controller.ts'
import { UserController } from '../controllers/user.controller.ts'
import { WorkspaceController } from '../controllers/workspace.controller.ts'
import * as preparation from '../schemas/asset-preparation.schema.ts'
import * as asset from '../schemas/asset.schema.ts'
import * as operation from '../schemas/operation.schema.ts'
import * as snapshot from '../schemas/snapshot.schema.ts'
import * as user from '../schemas/user.schema.ts'
import * as workspace from '../schemas/workspace.schema.ts'
import type { Services } from '../services/index.ts'

/** /api/v1：路由只負責 URL ↔ schema ↔ controller 對應 */
export function apiRoutes(services: Services) {
  return async (fastify: FastifyInstance) => {
    const app = fastify.withTypeProvider<TypeBoxTypeProvider>()

    const users = new UserController(services.users)
    app.post('/users', { schema: user.createUserSchema }, users.create)
    app.get('/users/me', { schema: user.getMeSchema }, users.me)

    const workspaces = new WorkspaceController(services.workspaces)
    app.get('/workspaces', { schema: workspace.listWorkspacesSchema }, workspaces.list)
    app.post('/workspaces', { schema: workspace.createWorkspaceSchema }, workspaces.create)
    app.get('/workspaces/:workspaceId', { schema: workspace.getWorkspaceSchema }, workspaces.get)
    app.patch('/workspaces/:workspaceId', { schema: workspace.updateWorkspaceSchema }, workspaces.update)
    app.delete('/workspaces/:workspaceId', { schema: workspace.deleteWorkspaceSchema }, workspaces.delete)

    const members = new MemberController(services.members)
    app.get('/workspaces/:workspaceId/members', { schema: workspace.listMembersSchema }, members.list)
    app.put('/workspaces/:workspaceId/members/:userId', { schema: workspace.setMemberSchema }, members.set)
    app.delete('/workspaces/:workspaceId/members/:userId', { schema: workspace.revokeMemberSchema }, members.revoke)

    const operations = new OperationController(services.operations, services.receipts)
    app.post('/workspaces/:workspaceId/operations', { schema: operation.submitOperationsSchema }, operations.submit)
    app.get('/workspaces/:workspaceId/operations', { schema: operation.listOperationsSchema }, operations.list)
    app.post('/workspaces/:workspaceId/operations/receipts', { schema: operation.lookupReceiptsSchema }, operations.lookupReceipts)
    app.post('/workspaces/:workspaceId/operations/fences', { schema: operation.fenceTransactionsSchema }, operations.fence)

    const snapshots = new SnapshotController(services.snapshots)
    app.get('/workspaces/:workspaceId/state', { schema: snapshot.getStateSchema }, snapshots.state)
    app.post('/workspaces/:workspaceId/snapshots', { schema: snapshot.createSnapshotSchema }, snapshots.create)
    app.get('/workspaces/:workspaceId/snapshots/latest', { schema: snapshot.latestSnapshotSchema }, snapshots.latest)

    const assets = new AssetController(services.assets)
    app.post('/workspaces/:workspaceId/assets', { schema: asset.createAssetSchema }, assets.prepare)
    app.get('/workspaces/:workspaceId/assets', { schema: asset.listAssetsSchema }, assets.list)
    app.get('/workspaces/:workspaceId/assets/:assetId', { schema: asset.getAssetSchema }, assets.get)
    app.post('/workspaces/:workspaceId/assets/:assetId/complete', { schema: asset.completeAssetSchema }, assets.complete)
    app.get('/workspaces/:workspaceId/assets/:assetId/content', { schema: asset.assetContentSchema }, assets.content)
    app.delete('/workspaces/:workspaceId/assets/:assetId', { schema: asset.abandonAssetSchema }, assets.abandon)

    const preparations = new AssetPreparationController(services.preparations)
    const prepBase = '/workspaces/:workspaceId/assets/preparations'
    app.get('/capabilities', { schema: preparation.capabilitiesSchema }, preparations.capabilities)
    app.post(prepBase, { schema: preparation.createPreparationSchema }, preparations.prepare)
    app.get(`${prepBase}/:requestId`, { schema: preparation.getPreparationSchema }, preparations.get)
    app.post(`${prepBase}/:requestId/upload-tickets`, { schema: preparation.issueTicketSchema }, preparations.issueTicket)
    app.post(`${prepBase}/:requestId/complete`, { schema: preparation.completePreparationSchema }, preparations.complete)
  }
}
