# Backend 開發進度

對照：[`docs/BACKEND_ARCHITECTURE.md`](../docs/BACKEND_ARCHITECTURE.md) §11 交付階段、[`docs/BACKEND_MEDIA_API_SPEC.md`](../docs/BACKEND_MEDIA_API_SPEC.md)（Media v1）
最後更新：2026-10-05（併入 2026-10-05 同步評估）

圖例：✅ 完成 · 🟡 部分完成／暫定 · ⬜ 未開始

## 階段總覽

| 階段 | 狀態 | 摘要 |
|---|---|---|
| B0 Protocol 與單機資料基線 | 🟡 | 資料表、transaction、冪等、測試基礎完成；protocol 尚未與 KMP 共用定稿 |
| B1 單實例保存與同步 | 🟡 | REST 版本的 Workspace CRUD / membership / submit / catch-up / state 完成；WebSocket 與正式 auth 未做 |
| B2 多程序與即時 fan-out | ⬜ | outbox 已同 transaction 寫入，但尚無 publisher、Redis |
| B3 資產與背景工作 | 🟡 | Media v1 後端完成（signed PUT/GET、complete、worker 驗證、縮圖、abandon、GC）；APP 真實端到端與 QA 簽核待做；snapshot compaction 未做 |
| B4 協作產品化 | ⬜ | — |

## 已實作

### 專案基礎
- ✅ `backend/` Bun + TypeScript 專案，Fastify 5 + TypeBox，MVC + Repository / Service 分層
- ✅ 環境變數設定（`src/config/env.ts`）、`.env.example`
- ✅ `docker-compose.yml` 啟動 PostgreSQL 17（port 5433）與 RustFS S3-compatible storage（port 9000）
- ✅ SQL migration runner（advisory lock + 單一 transaction，`bun run migrate`）
- ✅ 統一 error envelope `{ error: { code, message, details }, requestId }`
- ✅ OpenAPI / Swagger UI（`/docs`），request / response 皆有 JSON Schema
- ✅ Structured log（pino），redact `authorization` 與 `x-user-id`；request log 不保留 query string（簽名 URL）
- ✅ `/health`（liveness）、`/health/ready`（檢查 DB）
- ✅ 服務狀態 page view（`GET /`：DB 狀態、Workspace / operation 數、outbox backlog、素材狀態與 job / dead-letter 數、API 列表）
- ✅ API CORS（`CORS_ORIGINS`，允許 `content-type`、`x-user-id`）

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
- ✅ Assets（Media v1）：見下節

### 素材系統 Media v1（BACKEND_MEDIA_API_SPEC）
- ✅ `POST …/assets`：MIME 精確 allowlist（415）、1..200 MiB（413）、`sha256:` checksum 格式（400）→ 201 `{asset, upload}`；不再回傳 `uploadUrl`
- ✅ Signed PUT directive：`{method: PUT, url, headers: {Content-Type}, expiresAt}`，Content-Type 納入簽名；上傳到每張票券獨立的暫存 key
- ✅ `POST …/assets/:id/complete`：同一 transaction 寫入 accepted + durable verify job 才回 202；重送相同值回目前狀態（ready 時 200），不同值 409，尚未上傳 409 `upload_not_found`
- ✅ `GET …/assets/:id`（不包裝）、`GET …/assets`（完整列表，含衍生縮圖），新增 `thumbnailAssetId`、`rejectionReason`、`updatedAt`
- ✅ `GET …/assets/:id/content`：只對 ready 簽發 `{asset, download}`；簽發前 HEAD 確認內容，遺失則轉 missing 並回 409
- ✅ **`DELETE …/assets/:id` 改為 pending-only abandon**（§5 語意變更已由後端確認並實作）：只有未接受 complete 的上傳可放棄；已 abandon 冪等 204；已 complete 回 409；與 complete 鎖同一 row 序列化
- ✅ Worker（`bun run worker`，獨立 process）：object 存在、byte count、SHA-256、magic-byte MIME、ffprobe 尺寸 / duration、ffmpeg 實際解碼；通過後把已驗證的本機副本以 `If-None-Match: *` 寫入不可變 `storageKey`，CAS 發布 ready
- ✅ GIF / 影片縮圖：獨立 ready asset（PNG / JPEG，≤512px），`thumbnailAssetId` 指向；以 (source, type, version) 去重；失敗只重試縮圖 job，不影響原檔
- ✅ Durable job queue（PostgreSQL `asset_jobs`）：job_key 去重、lease + `SKIP LOCKED`、heartbeat、指數退避重試、dead-letter
- ✅ Upload GC：只回收 upload ticket 過期且從未 complete 的資產，不碰 accepted / processing
- ✅ Storage driver：`s3`（aws4fetch，S3 / RustFS / MinIO / R2）與 `local`（開發 / 測試，HMAC 簽名 URL 由 `/storage/objects` 處理）
- ✅ MediaNode 引用驗證：`create_object` / `update_object` 的 `assetId` / `thumbnailAssetId` 必須屬於同一 Workspace，`mediaKind` 必須符合 MIME
- ✅ Storage 暫時失敗回 503 `storage_unavailable`，不洩漏內部位址
- ✅ 六種 MIME 真實 binary fixture 與 manifest（`tests/fixtures/media/`，`bun run fixtures:media`）
- ✅ 26 份 request / response contract fixtures（`tests/fixtures/contract/media-v1/`，`bun run fixtures:contract`），並以 response schema 驗證

#### 規格 §8 交付清單
| # | 項目 | 狀態 |
|---|---|---|
| 1 | Storage adapter、signed PUT、必要 headers / CORS | ✅ |
| 2 | complete durable acceptance、worker 驗證、ready / rejected / missing | ✅ |
| 3 | `{asset, download}` signed GET、縮圖獨立 metadata / content | ✅ |
| 4 | pending-only abandon、complete 競爭、GC policy | ✅ |
| 5 | 六種 MIME binary fixture 與 request / response fixtures | ✅ |
| 6 | 自動整合測試（截斷 / 超量 / 偽 MIME / 錯 checksum、worker 重啟、重複 complete、多 worker、遺失回應、abandon 競爭、GC、ACL、不可覆寫） | ✅ |
| 7 | APP 真實端到端（upload → ready → Node → 重開下載 → 素材庫重用、GIF / 影片 / poster） | ⬜ 需 APP 對接 |
| 8 | QA SOP 人工簽核、效能測試（最後階段） | ⬜ |

#### 簽名 URL TTL 與撤銷
- upload 預設 15 分鐘（`UPLOAD_URL_TTL_SECONDS`）、download 預設 5 分鐘（`DOWNLOAD_URL_TTL_SECONDS`）
- 撤銷 membership 後立即停止簽發新票券；**已簽出的 S3 presigned URL 在到期前無法撤銷**，最大暴露時間即上述 TTL

### 寫入正確性（§4.3、§6.4、§8.1）
- ✅ Workspace row lock 序列化同一 Workspace 的寫入，`serverSeq` 唯一且連續
- ✅ 鎖內重新檢查 membership / 角色（撤銷後立即無法寫入）
- ✅ 依 operation ID 冪等：全部重複 → `duplicate` 回原結果；部分重複 → `rejected: partial_duplicate`
- ✅ `expectedObjectVersions` 衝突 → 整個 transaction rollback，回 `409 conflict` + 目前版本 + `missingOperations`
- ✅ invariant 失敗 → rollback，回 `422`，資料不變
- ✅ 刪除 Node 時同 transaction 刪除相連 relation，並把 `cascadedRelationIds` 寫入正式 payload
- ✅ locked 物件只能切換 `locked`，不可移動 / 修改 / 刪除
- ✅ operation、projection、outbox 在同一 transaction 提交

### 測試（`bun test`，89 個）
- ✅ 單元：operation payload 驗證、touched ids、角色權限矩陣、HTML 跳脫、MIME sniff、本機簽名 URL、contract fixtures schema
- ✅ 整合（真實 PostgreSQL）：create/move/delete + 冪等重送、20 個並行寫入的 `serverSeq` 連續性、版本衝突、transaction rollback、relation cascade、角色與撤銷、outbox、snapshot
- ✅ 素材整合（真實 ffmpeg）：六種 MIME 完整流程、驗證失敗分類、complete 冪等 / 衝突、abandon 競爭、GC、lease 過期重領、重複縮圖 job、多 worker、不可覆寫、missing、ACL、MediaNode 引用、storage 503
- ✅ 端到端（真實 HTTP）：本機 driver 串流上傳約 4 MB 影片；S3 driver 對 RustFS 的 presigned PUT / GET、縮圖、`If-None-Match`（storage 無法連線時略過）

## 待辦事項

### 2026-10-05 同步評估：建議交付順序
現況：REST 保存與 Media v1 已交付；即時協作與多項產品合約未完成。下列規格多數仍是 Proposal／待後端確認，確認前不可當成 APP 可用的正式 API。

1. **資料正確性**：型別化 object 驗證（BAI-008）、Group 階層 invariant（BAI-007）、restore／刪除 Undo／Redo（BAI-001、BAI-011）、原提交 receipt／settlement（BAI-012）
2. **正式認證 → 即時協作**：access／refresh token，再交付 WebSocket、fan-out、Presence、撤權斷線（[`BACKEND_REALTIME_API_SPEC.md`](../docs/BACKEND_REALTIME_API_SPEC.md)）
3. **快速方案簿**與素材跨畫布授權（[`BACKEND_QUICK_SCHEME_API_SPEC.md`](../docs/BACKEND_QUICK_SCHEME_API_SPEC.md)）
4. **共用背景**（[`BACKEND_CANVAS_STYLE_API_SPEC.md`](../docs/BACKEND_CANVAS_STYLE_API_SPEC.md)）、**AI gateway**（BAI-004、[`AI_PROVIDER_STREAMING.md`](../docs/AI_PROVIDER_STREAMING.md)）、**向量 Node**（[`BACKEND_VECTOR_PATH_API_SPEC.md`](../docs/BACKEND_VECTOR_PATH_API_SPEC.md)）、**GIPHY 引用物件**（BAI-010、[`GIPHY_API_INTEGRATION_SPEC.md`](../docs/GIPHY_API_INTEGRATION_SPEC.md)）
5. **正式部署與故障驗收**；效能測試維持最後

整合問題細節見 [`BACKEND_APP_INTEGRATION_ISSUES.md`](../docs/BACKEND_APP_INTEGRATION_ISSUES.md)；素材實測紀錄見 [`MEDIA_LIVE_ACCEPTANCE.md`](../docs/MEDIA_LIVE_ACCEPTANCE.md)。

### 資料正確性（優先 1）
- [ ] BAI-008：依 objectType 型別化驗證 `create_object` / `update_object`（transform 尺寸／座標、properties schema、未知型別拒絕、更新後型別一致）
- [ ] BAI-007：parent 必須是 Group、禁止間接循環、刪除父群組時 children 的處理規則
- [ ] BAI-001：soft-delete 後以相同 ID 還原（`restore_objects` 或受檢查的 resurrection）
- [ ] BAI-011：草稿合併的結構替換（物件型別替換、relation endpoint 修改）與刪除 Undo／Redo 語意
- [ ] BAI-012：原 transaction 的權威 receipt／settlement／fence（一般 catch-up 查不到不代表未成功）

### 需先定稿的契約（§12，阻塞 B0 完成）
- [ ] 與 KMP `shared` 模組共用 `WorkspaceOperation` 定義（種類、inverse 所需資料、transaction 邊界）；目前 `src/domain/operations.ts` 為 server 暫定版
- [ ] 產生共用 JSON Schema 並建立 Kotlin / TypeScript contract fixtures 互讀測試（`tests/contract/`）
- [ ] 確認 Workspace version / object version / `serverSeq` / `clientSeq` 語意（目前：每 operation 一個 `serverSeq`，每 transaction version +1）
- [ ] Node / Relation / Group / Lock / Layer / Asset 的穩定 ID 規則（目前皆為 client 產生的 UUID）
- [ ] Group、Layer、Asset reference 相關 operation 與刪除規則
- [ ] commenter 可提交的 operation 範圍（目前與 viewer 相同，唯讀）
- [ ] Snapshot 格式、schema migration 與最低支援 client protocol version

### B1（優先 2）
- [ ] 正式 auth：短效 access token、refresh token、provider identity 分表（取代 `AUTH_MODE=dev`）；APP 需同步改版
- [ ] WebSocket gateway：`join_workspace`、`submit_operations`、accepted / conflict 推播、catch-up、live barrier（共用 `OperationService`；依 BACKEND_REALTIME_API_SPEC）
- [ ] Presence／游標、撤銷 membership 後斷開既有連線
- [ ] 落後過多時回傳 snapshot + 後續 operations 的 join 邏輯
- [ ] server 重啟後雙 client 編輯一致性測試

### B2
- [ ] Outbox publisher（標記 `published_at`、`attempt_count`、重試）
- [ ] Redis：presence TTL、跨 process live fan-out、rate limit
- [ ] 多 process / replicas 部署與 kill-process、Redis 中斷重連測試

### B3
- [ ] APP 真實端到端與 QA 簽核（§8-7、§8-8）：需 APP 端連真實後端執行
- [ ] Storage 孤兒物件回收：bucket lifecycle rule 清理 `uploads/` 前綴（ready 後仍以有效票券重傳、或 worker 在寫入縮圖與 DB commit 之間當機時會留下物件）
- [ ] verify job 進入 dead-letter 時資產維持 pending：需要告警與人工重試工具
- [ ] 驗證強度：目前解碼第一個影格；評估完整解碼 / 影片 codec allowlist / 音訊檢查
- [ ] Worker container image（含 ffmpeg）與資源限制（CPU / 記憶體 / 同時 job 數）
- [ ] 正式環境 storage：bucket、CORS、lifecycle 由 IaC 管理（`S3_ENSURE_BUCKET` 只限開發），`STORAGE_SIGNING_SECRET` 必填
- [ ] Snapshot compaction worker（依 operation 數量 / 時間自動觸發）
- [ ] Workspace 刪除後 assets / snapshots / 衍生檔清理排程
- [ ] 規格 §9 尚未支援：正式 auth、server pagination、續傳 / URL 自動更新、真正刪除 / restore ready 素材、跨 Workspace 複製、轉碼 rendition、GIF Browser

### 產品合約（優先 3–4，規格待後端確認）
- [ ] 快速方案簿：user-level CRUD、revision／ETag、同步衝突、素材保留與跨畫布 materialization
- [ ] 共用畫布背景：state／snapshot 內的 canvasStyle、更新 operation、版本衝突、Undo／Redo
- [ ] AI gateway：provider profile／secret reference、job／stream／cancel、proposal 合約（APP adapter 不等於後端完成）
- [ ] 鋼筆／自訂向量 Node：kind／capability、型別 schema、projection／operation／restore、跨端 fixtures
- [ ] GIPHY 外部引用物件合約（下載保存另有供應商授權前提）

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
- 背景工作佇列使用 PostgreSQL `asset_jobs`（lease + `SKIP LOCKED`），而非文件建議的 Redis Streams：目前尚未引入 Redis，PostgreSQL queue 同樣 durable 且能與 complete 同一 transaction 寫入；量測到瓶頸再評估。
- CPU 密集工作全部交給 ffmpeg / ffprobe child process，因此尚未建立 `worker_threads` pool。
- 本機 S3-compatible storage 使用 RustFS：MinIO 社群版映像已停止公開發佈。
