# Backend 開發進度

對照：[`docs/BACKEND_ARCHITECTURE.md`](../docs/BACKEND_ARCHITECTURE.md) §11 交付階段
最後更新：2026-09-24

圖例：✅ 完成 · 🟡 部分完成／暫定 · ⬜ 未開始

## 階段總覽

| 階段 | 狀態 | 摘要 |
|---|---|---|
| B0 Protocol 與單機資料基線 | 🟡 | 資料表、transaction、冪等、測試基礎完成；protocol 尚未與 KMP 共用定稿 |
| B1 單實例保存與同步 | 🟡 | REST 版本的 Workspace CRUD / membership / submit / catch-up / state 完成；WebSocket 與正式 auth 未做 |
| B2 多程序與即時 fan-out | ⬜ | outbox 已同 transaction 寫入，但尚無 publisher、Redis |
| B3 資產與背景工作 | 🟡 | 只有 pending asset metadata；object storage、signed URL、worker 未做 |
| B4 協作產品化 | ⬜ | — |

## 已實作

### 專案基礎
- ✅ `backend/` Bun + TypeScript 專案，Fastify 5 + TypeBox，MVC + Repository / Service 分層
- ✅ 環境變數設定（`src/config/env.ts`）、`.env.example`
- ✅ `docker-compose.yml` 啟動 PostgreSQL 17（port 5433）
- ✅ SQL migration runner（advisory lock + 單一 transaction，`bun run migrate`）
- ✅ 統一 error envelope `{ error: { code, message, details }, requestId }`
- ✅ OpenAPI / Swagger UI（`/docs`），request / response 皆有 JSON Schema
- ✅ Structured log（pino），redact `authorization` 與 `x-user-id`
- ✅ `/health`（liveness）、`/health/ready`（檢查 DB）
- ✅ 服務狀態 page view（`GET /`：DB 狀態、Workspace / operation 數、outbox backlog、API 列表）

### 儲存模型（§5）
- ✅ `users`、`workspaces`、`workspace_members`、`canvas_objects`、`relations`、`workspace_operations`、`workspace_snapshots`、`assets`、`transaction_outbox`
- ✅ `workspace_operations` PK `(workspace_id, server_seq)` + unique `(workspace_id, actor_id, operation_id)`
- ✅ relation 來源 / 目標以複合 FK 限制在同一 Workspace
- ➕ 新增 `workspace_operations.workspace_version` 欄位（文件未列），conflict 時用來找出 `baseVersion` 之後的 operations

### API（`/api/v1`）
- ✅ Users：`POST /users`（dev）、`GET /users/me`
- ✅ Workspaces：列表 / 建立（建立者自動為 owner）/ 取得 / 重新命名 / soft delete
- ✅ Members：列表 / 新增或變更角色 / 撤銷（owner 限定；owner 角色不可變更）
- ✅ Operations：`POST …/operations`（§6.2 envelope）、`GET …/operations?afterSeq=&limit=`（catch-up）
- ✅ State / Snapshots：`GET …/state`（一致讀取的目前 projection）、`POST …/snapshots`（checksum）、`GET …/snapshots/latest`
- 🟡 Assets：建立 pending metadata（media type allowlist、200MB 上限）/ 列表 / 取得 / soft delete；`uploadUrl` 目前回傳 `null`

### 寫入正確性（§4.3、§6.4、§8.1）
- ✅ Workspace row lock 序列化同一 Workspace 的寫入，`serverSeq` 唯一且連續
- ✅ 鎖內重新檢查 membership / 角色（撤銷後立即無法寫入）
- ✅ 依 operation ID 冪等：全部重複 → `duplicate` 回原結果；部分重複 → `rejected: partial_duplicate`
- ✅ `expectedObjectVersions` 衝突 → 整個 transaction rollback，回 `409 conflict` + 目前版本 + `missingOperations`
- ✅ invariant 失敗 → rollback，回 `422`，資料不變
- ✅ 刪除 Node 時同 transaction 刪除相連 relation，並把 `cascadedRelationIds` 寫入正式 payload
- ✅ locked 物件只能切換 `locked`，不可移動 / 修改 / 刪除
- ✅ operation、projection、outbox 在同一 transaction 提交

### 測試（`bun test`，17 個）
- ✅ 單元：operation payload 驗證、touched ids、角色權限矩陣、HTML 跳脫
- ✅ 整合（真實 PostgreSQL）：create/move/delete + 冪等重送、20 個並行寫入的 `serverSeq` 連續性、版本衝突、transaction rollback、relation cascade、角色與撤銷、outbox、snapshot / asset

## 待辦事項

### 需先定稿的契約（§12，阻塞 B0 完成）
- [ ] 與 KMP `shared` 模組共用 `WorkspaceOperation` 定義（種類、inverse 所需資料、transaction 邊界）；目前 `src/domain/operations.ts` 為 server 暫定版
- [ ] 產生共用 JSON Schema 並建立 Kotlin / TypeScript contract fixtures 互讀測試（`tests/contract/`）
- [ ] 確認 Workspace version / object version / `serverSeq` / `clientSeq` 語意（目前：每 operation 一個 `serverSeq`，每 transaction version +1）
- [ ] Node / Relation / Group / Lock / Layer / Asset 的穩定 ID 規則（目前皆為 client 產生的 UUID）
- [ ] Group、Layer、Asset reference 相關 operation 與刪除規則
- [ ] commenter 可提交的 operation 範圍（目前與 viewer 相同，唯讀）
- [ ] Snapshot 格式、schema migration 與最低支援 client protocol version

### B1
- [ ] 正式 auth：短效 access token、refresh token、provider identity 分表（取代 `AUTH_MODE=dev`）
- [ ] WebSocket gateway：`join_workspace`、`submit_operations`、accepted / conflict 推播、catch-up（共用 `OperationService`）
- [ ] 落後過多時回傳 snapshot + 後續 operations 的 join 邏輯
- [ ] server 重啟後雙 client 編輯一致性測試

### B2
- [ ] Outbox publisher（標記 `published_at`、`attempt_count`、重試）
- [ ] Redis：presence TTL、跨 process live fan-out、rate limit
- [ ] 多 process / replicas 部署與 kill-process、Redis 中斷重連測試

### B3
- [ ] S3-compatible object storage（本機可用 MinIO）與短效 signed upload / download URL
- [ ] 上傳完成回報、worker 驗證 checksum / 實際 media type / 尺寸限制，狀態 `pending → ready / rejected`
- [ ] Redis Streams worker：縮圖、檔案檢查、snapshot compaction（依 operation 數量 / 時間自動觸發）
- [ ] Worker 冪等、retry、dead-letter
- [ ] Workspace 刪除後 assets / snapshots / 衍生檔清理排程

### 品質與維運
- [ ] Domain property tests（operation 套用後 inverse 回到一致狀態）
- [ ] OpenTelemetry traces、metrics（commit latency、lock wait、outbox backlog、event-loop lag）
- [ ] Load scenario（大量 idle socket、熱點 Workspace、平行寫入）
- [ ] 大型 snapshot 改存 object storage（`storage_key`）
- [ ] CI：typecheck + test（含 PostgreSQL service）
- [ ] Workspace 狀態 inspector page view（需先有正式 auth）

## 與架構文件的差異
- 目錄使用 `backend/`（文件 §7 建議 `server/`），目前為單一 package 的 MVC 結構；待 WebSocket gateway / worker 加入時再評估拆成 `apps/` + `packages/`。
- Runtime 使用 Bun（文件建議 Node.js LTS）；`src/` 未使用 Bun 專屬 API（測試使用 `bun:test`），保留日後改回 Node.js 執行的彈性。
