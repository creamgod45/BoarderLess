# BoarderLess Backend

Workspace 儲存與同步服務。依 [`docs/BACKEND_ARCHITECTURE.md`](../docs/BACKEND_ARCHITECTURE.md) 規劃，目前進度與待辦見 [`PROGRESS.md`](PROGRESS.md)。

- Runtime / 套件管理：**Bun**（程式碼維持 Node.js 相容，不依賴 Bun 專屬 DB API）
- HTTP：Fastify 5 + TypeBox（JSON Schema 驗證、OpenAPI 自動產生）
- 資料庫：PostgreSQL（`postgres` driver）
- 素材：S3-compatible private object storage + 背景 worker（ffmpeg / ffprobe）

## 快速開始

```bash
brew install ffmpeg       # worker 需要 ffmpeg / ffprobe（第一次）
bun install
cp .env.example .env      # 第一次
bun run db:up             # docker compose 啟動 PostgreSQL（5433）與 RustFS S3 storage（9000）
bun run migrate
bun run dev               # API：http://localhost:3000
bun run worker:dev        # 另一個 terminal：素材驗證 / 縮圖 / 清理
```

不想啟動 storage 容器時，可在 `.env` 改用 `STORAGE_DRIVER=local`（由 API 自己處理簽名 URL，只限開發）。

| 位置 | 內容 |
|---|---|
| `GET /` | 服務狀態頁（page view） |
| `GET /docs` | OpenAPI / Swagger UI |
| `GET /health`、`/health/ready` | liveness / readiness |
| `/api/v1/*` | 儲存服務 API |

### 指令

| 指令 | 說明 |
|---|---|
| `bun run dev` / `bun run start` | API（watch / 正式） |
| `bun run worker:dev` / `bun run worker` | 背景 worker（watch / 正式） |
| `bun run migrate` | 套用 `migrations/*.sql` |
| `bun test` | 單元 + 整合 + 端到端測試（使用 `boarderless_test` DB，自動建立；DB / ffmpeg / S3 不可用時略過對應測試） |
| `bun run fixtures:media` | 重新產生六種 MIME 的 binary fixture 與 manifest |
| `bun run fixtures:contract` | 以真實流程重新錄製 Media v1 request / response fixtures |
| `bun run fixtures:canvas` | 重新錄製 canvas（型別 / 階層 / restore / receipt）fixtures |
| `bun run fixtures:preparation` | 重新錄製 asset preparation v2 fixtures |
| `bun run fixtures:realtime` | 重新錄製即時協作（REST + WebSocket）fixtures 與訊息 JSON Schema |
| `bun run acceptance:multi-process` | 驗收 CORE-04 / RT-04：兩個真實 API process + kill -9，結果寫入 `acceptance/results/` |
| `bun run acceptance:db-restart` | 驗收 CORE-08 / ASSET-03：一次性 PostgreSQL 容器真重啟（restart、kill），結果寫入 `acceptance/results/` |
| `bun run typecheck` | `tsc --noEmit` |

### Docker

`Dockerfile` 有兩個 target：`api`（預設，listen 3000）與 `worker`（含 ffmpeg）。容器啟動時 API 會自動套用 migration（`MIGRATE_ON_START=true`；worker 預設不跑）。

```bash
# 以容器跑完整服務（DB / storage / API / worker）；API 對外 port 預設 3000，可用 API_PORT 調整
docker compose --profile app up -d --build
```

```bash
# 單獨執行 CI 打包好的映像
docker run -p 3000:3000 -e DATABASE_URL=postgres://… -e STORAGE_DRIVER=s3 -e S3_ENDPOINT=… ghcr.io/creamgod45/boarderless-backend:main
```

GitHub Actions（[`.github/workflows/backend-image.yml`](../.github/workflows/backend-image.yml)）在 `backend/` 有變更時：型別檢查 + 測試（含 PostgreSQL、RustFS、ffmpeg）→ 映像啟動檢查 → 打包 amd64 / arm64 映像並推送到 GHCR：

| 映像 | 內容 |
|---|---|
| `ghcr.io/creamgod45/boarderless-backend` | REST API（port 3000） |
| `ghcr.io/creamgod45/boarderless-worker` | 背景 worker |

Tag：分支名稱、`sha-<短 sha>`、預設分支另加 `latest`；推送 `backend-v1.2.3` tag 另加 `1.2.3`。Pull request 只測試與 build，不推送。

### 身分驗證（開發用）

正式登入尚未實作。`AUTH_MODE=dev` 下以 `x-user-id` header 識別使用者：

```bash
curl -X POST localhost:3000/api/v1/users -H 'content-type: application/json' -d '{"displayName":"Ken"}'
curl localhost:3000/api/v1/workspaces -H 'x-user-id: <上一步回傳的 id>'
```

## 架構（MVC + Repository / Service）

```text
request
  → routes/        URL ↔ schema ↔ controller 對應
  → controllers/   解析 request、呼叫 service、決定 HTTP status / view
  → services/      商業邏輯、權限、transaction 邊界
  → domain/        純邏輯：operation 定義與驗證、角色權限（無 I/O，可單元測試）
  → repositories/  SQL 存取；可綁定一般連線或 transaction（Database.transaction）
  → PostgreSQL
jobs/      背景工作 handler（verify / thumbnail / delete）、runner、upload GC
media/     magic-byte sniff、ffprobe / ffmpeg 封裝、串流雜湊
storage/   object storage 抽象：s3、local
views/     伺服器端 HTML（自動跳脫的 tagged template）
models/    資料實體型別
schemas/   API request/response JSON Schema（驗證 + OpenAPI）
```

```text
src/
├── app.ts / server.ts     組裝 Fastify / API 入口
├── worker.ts              背景 worker 入口
├── config/                環境變數
├── db/                    連線、migration runner
├── models/  domain/  repositories/  services/
├── controllers/  routes/  schemas/  views/
├── middlewares/           dev auth、統一 error envelope
└── utils/
migrations/                SQL migration（依檔名排序執行）
tests/unit、tests/integration
```

## 正式寫入流程（§4.3 / §8.1）

`POST /api/v1/workspaces/:id/operations` 接受 §6.2 operation envelope：

1. 驗證 protocol version 與每個 operation payload（不合法 → `422 rejected`）
2. 開 transaction，`SELECT … FOR UPDATE` 鎖住 Workspace row，並在鎖內重新檢查 membership / 角色
3. 以 `(workspace_id, actor_id, operation_id)` 去重（全部已提交 → `200 duplicate`）
4. 逐筆檢查 `expectedObjectVersions`（不符 → rollback，回 `409 conflict` + `missingOperations`）與 invariant
5. 更新 projection（`canvas_objects` / `relations`）、配發連續 `serverSeq`、寫入 operation log 與 `transaction_outbox`
6. commit 後才回 `200 accepted`

版本語意：每個 operation 一個 `serverSeq`；每個成功 transaction 使 Workspace `currentVersion` +1。

### Operation kinds

`create_object`、`update_object`、`move_objects`、`delete_objects`（同 transaction 連帶刪除 relations，記錄於 payload 的 `cascadedRelationIds`）、`restore_objects`、`create_relation`、`update_relation`、`delete_relations`、`restore_relations`。

- 物件型別：`text`、`group`、`media`，各有型別化 schema（[`src/domain/canvas-objects.ts`](src/domain/canvas-objects.ts)）；update 驗證 merge 後的完整狀態。
- parent 必須是同 Workspace 的 active group 且不可形成循環；刪除 group 時 children 必須一起刪除或先 reparent。
- 刪除的 Undo / Redo 使用 `restore_*`（以穩定 ID 還原 tombstone），不是 `create_*`。
- 原提交 reconciliation：`POST …/operations/receipts` 查 receipt，`POST …/operations/fences` 保證晚到的原請求不會 commit。
- 範例見 [`tests/fixtures/contract/canvas-v1/`](tests/fixtures/contract/canvas-v1/)。

> 這是 server 端的暫定集合，須與 KMP client 共用的 `WorkspaceOperation` 定稿後以 contract fixtures 對齊（見 PROGRESS.md）。

## 素材上傳流程（Media v1）

完整合約見 [`docs/BACKEND_MEDIA_API_SPEC.md`](../docs/BACKEND_MEDIA_API_SPEC.md)；request / response 範例見 [`tests/fixtures/contract/media-v1/`](tests/fixtures/contract/media-v1/)。

```text
APP ── POST /assets ─────────────► API     授權、建立 pending、簽發 signed PUT（暫存 key）
APP ── PUT upload.url ───────────► Storage  原始 binary 直傳，不經過 API
APP ── POST /assets/:id/complete ► API     同一 transaction：accepted + verify job → 202
                                   Worker   取檔 → size / SHA-256 / magic bytes / ffprobe / 解碼
                                            → 寫入不可變 storageKey → ready（GIF / 影片再產生縮圖）
APP ── GET /assets/:id（輪詢）───► API     pending → ready / rejected
APP ── GET /assets/:id/content ──► API     只對 ready 簽發 signed GET
APP ── GET download.url ─────────► Storage  下載原檔
```

- `width` / `height` 是顯示尺寸（已套用 EXIF orientation 與影片 rotation）。
- MediaNode 新建或替換素材時，引用的素材必須 ready；`restore_objects` 還原原引用不要求 ready。
- 2026-10-11 的相容性影響（body 嚴格驗證、顯示尺寸等）見 [`ACCEPTANCE.md`](ACCEPTANCE.md#相容性影響2026-10-11影響既有-media-v1--operation-client)。
- 公開 status 只有 `pending` / `ready` / `rejected` / `missing`；內部 phase（awaiting_upload、accepted、abandoned、expired）不對外。
- `DELETE /assets/:id` 是 pending-only abandon：只能放棄尚未 complete 的上傳。
- 簽名 URL 只出現在 API 回應中，不寫入 DB、Node、operation 或 log。

## 素材 preparation v2（固定身份與未知回應恢復）

合約見 [`docs/BACKEND_ASSET_PREPARATION_V2_SPEC.md`](../docs/BACKEND_ASSET_PREPARATION_V2_SPEC.md)；範例見 [`tests/fixtures/contract/asset-preparation-v2/`](tests/fixtures/contract/asset-preparation-v2/)。APP 先確認 `GET /api/v1/capabilities` 宣告 `assetPreparation` 才使用。

```text
APP ── POST /assets/preparations ──────────────► API  {schema, requestId, metadata}：201 首次 / 200 同 key 重送 / 409 metadata 不同
APP ── GET  /assets/preparations/:requestId ───► API  回應遺失時查原 identity（只限本人、此 Workspace）
APP ── POST …/:requestId/upload-tickets ───────► API  {ticketRequestId}：簽發 lease（generation + 獨立 staging key）
APP ── PUT  upload.url ────────────────────────► Storage
APP ── POST …/:requestId/complete ─────────────► API  {requestId, leaseId, byteSize, checksum}：202 completionAccepted
                                                  Worker 驗證 → ready / rejected
```

- Identity = 認證 user + workspace + requestId，永久保留（abandoned / rejected / missing 也保留 tombstone，不會復活或重建）。
- `requestDigest` = canonical metadata（`byteSize, checksum, durationMs, height, mediaType, width`，null 保留）compact JSON 的 SHA-256 hex，由伺服器計算。
- body 嚴格驗證：不做型別轉換、不接受未知 / 缺少欄位；requestId 必須是小寫 UUID。
- `phase`：`prepared` / `completionAccepted` / `ready` / `rejected` / `missing` / `abandoned`。
- `declared` 永不改寫（APP 以此綁定 identity）；`verified` 是 worker 解析的顯示尺寸 / duration。declared 非 null 的寬高必須與 verified 相同、duration 容差 max(100ms, 1%)，否則 rejected `declared_metadata_mismatch`。
- 同 `ticketRequestId` 冪等回同一 lease；過期回 409 `upload_lease_expired`，需另存新的 `ticketRequestId`；新 lease 取代舊 lease（舊 lease complete 回 409 `upload_lease_superseded`）。accepted / ready 後不再簽發 PUT。
- 過期 lease 的 staging object 由 GC 回收，reservation 本身不會過期。v1 `POST /assets/:id/complete` 對 v2 資產回 409 `preparation_v2_required`。

## 即時協作（WebSocket，批次 A）

合約：[`docs/BACKEND_REALTIME_API_SPEC.md`](../docs/BACKEND_REALTIME_API_SPEC.md)；fixtures 與訊息 JSON Schema：[`tests/fixtures/contract/realtime-v1/`](tests/fixtures/contract/realtime-v1/)（`schemas.json`）。

```text
APP ── GET  /workspaces/:id/realtime-capabilities ─► API   版本、webSocketUrl、limits（需 read membership）
APP ── POST /workspaces/:id/realtime-ticket ───────► API   {protocolVersion, clientId} → 一次性 ticket（60 秒、no-store）
APP ══ WS   /api/v1/realtime（subprotocol boarderless.v1）
       → join_workspace{ticket, lastSeenServerSeq}    ← join_accepted{barrierServerSeq} ← catch_up_transaction* 或 snapshot_required
       → catch_up_applied{appliedServerSeq}           ← （補齊後）live_ready ← presence_snapshot
       → submit_operations / presence_update / heartbeat ← operation_ack / committed_transaction / presence_delta / workspace_metadata
```

- 一條連線一個 Workspace；ticket 只在第一個 frame，3 秒未驗證關閉；Origin 與 subprotocol 在 upgrade 前檢查。
- 所有 durable 內容都從 operation log 依連線 cursor 讀出完整 transaction；`committed_transaction` 與 REST 的 committed operations 欄位相同。
- WS submit 與 REST 走同一個 `OperationService`（ACL、schema、冪等、fence 完全相同）。
- `metadataRevision`（Workspace REST 回應也有）在改名、成員 / 角色變更、刪除時 +1；撤權連線收到 `access_revoked` 後以 4403 關閉。
- Presence 是暫態（UNLOGGED 表），不進 snapshot / Undo；heartbeat 只延長 TTL。

### 多 process 部署

- 不需要 Redis：commit 同一 transaction 寫入 `transaction_outbox`；每個 API process 都跑 publisher（`SKIP LOCKED`），在同一 DB transaction 內標記 published 並 `pg_notify`。任何 process crash 後未發布的事件由其他 process 補發。
- 每個 process `LISTEN`，NOTIFY 只是喚醒；另有 `pollIntervalMs` 安全網輪詢。Presence delta 也走 NOTIFY（payload < 8000 bytes：selection 上限 128）。
- 每個 process 需要一條額外的 PostgreSQL 連線給 `LISTEN`；若前面有 PgBouncer，LISTEN 連線必須是 session mode。
- 環境變數：`REALTIME_ENABLED`、`REALTIME_PUBLIC_URL`（正式環境必須是 `wss://…/api/v1/realtime`）、`REALTIME_ALLOWED_ORIGINS`（預設同 `CORS_ORIGINS`）、`REALTIME_TICKET_TTL_SECONDS`、`REALTIME_HEARTBEAT_INTERVAL_MS`、`REALTIME_PRESENCE_TTL_MS`、`REALTIME_MAX_MESSAGE_BYTES`、`REALTIME_MAX_PENDING_TRANSACTIONS`。
- 反向代理需支援 WebSocket upgrade 並轉送 `Sec-WebSocket-Protocol`；TLS 由代理終結。
- **認證**：ticket endpoint 使用與 REST 相同的 auth middleware；目前只有 `x-user-id` 開發替身，正式 HTTP auth 尚未交付，不能視為正式安全能力。
- Bun 的 `ws` 相容層不強制 `maxPayload`，gateway 在收到 frame 後自行檢查大小並以 4413 關閉。

## 雲端快速方案簿（批次 B）

合約：[`docs/BACKEND_QUICK_SCHEME_API_SPEC.md`](../docs/BACKEND_QUICK_SCHEME_API_SPEC.md)；fixtures：[`tests/fixtures/contract/quick-scheme-v2/`](tests/fixtures/contract/quick-scheme-v2/)；交付細節：[`DELIVERY.md`](DELIVERY.md)。

- `/api/v1/users/me/quick-schemes`：owner 由認證注入；資源 ID 是 client 先保存的 UUID。
- 建立 `PUT /:id` + `If-None-Match: *`；更新 / 刪除帶 `If-Match: <ETag>`；每個 mutation 都要 `Idempotency-Key`。
- payload 是 `boarderless/selection` v1–v9 物件，伺服器以 `src/domain/selection.ts`（APP validator 移植）嚴格驗證。
- 列表 cursor 是穩定快照；列舉途中有變更回 410 `cursor_expired`，從第一頁重來。
