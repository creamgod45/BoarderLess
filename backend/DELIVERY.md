# 後端交付紀錄（BACKEND_MISSING_IMPLEMENTATION_SPEC）

依 [`docs/BACKEND_MISSING_IMPLEMENTATION_SPEC.md`](../docs/BACKEND_MISSING_IMPLEMENTATION_SPEC.md) §9 模板，每批一節。
狀態分三種，彼此獨立：**源碼完成**（本 repo）／**服務已部署**／**正式驗收通過**。本文件只記錄源碼與預檢，部署與正式驗收另行標示。

---

## 批次 A：即時協作（2026-10-11）

### 1. 分支與 commit
- 分支 `backend/media-v1`，基底 `89a85a3`。**尚未 commit**；未提交範圍包含 preparation v2、ASSET-07 / 08、驗收追蹤與批次 A（`git status -- backend`）。
- 狀態：源碼完成 ✅；服務已部署 ⬜；正式驗收 ⬜（依規格待全部功能完成後集中執行）。
- 未完成：正式 HTTP auth（目前仍是 `x-user-id` 開發替身，ticket endpoint 已使用同一 middleware，換成正式 auth 時不需改 gateway）。

### 2. 路由、訊息與 capability
| 介面 | 說明 |
| --- | --- |
| `GET /api/v1/workspaces/:workspaceId/realtime-capabilities` | read membership；版本、`webSocketUrl`、`subprotocol`、`metadataRevision`、全部 limits |
| `POST /api/v1/workspaces/:workspaceId/realtime-ticket` | body `{protocolVersion, clientId}`；201 `{ticket, expiresAt, webSocketUrl, protocolVersion}`、`Cache-Control: no-store`；60 秒、一次性、原子 consume |
| `WS /api/v1/realtime` | subprotocol `boarderless.v1`；第一個 frame `join_workspace`（ticket 不放 URL）；3 秒未驗證關閉 |
| `GET /api/v1/capabilities` | 新增 `canvas.minWritableProtocolVersion`、`realtime`；保留 `assetPreparation`；未交付能力（quickSchemes、canvasStyle、relationGeometry、vectorPath、sequenceDiagrams、mediaReferenceRestore）為 `supported: false` |

- Client → server：`join_workspace`、`catch_up_applied`、`heartbeat`、`presence_update`、`submit_operations`。
- Server → client：`join_accepted`、`snapshot_required`、`catch_up_transaction`、`committed_transaction`、`live_ready`、`heartbeat_ack`、`presence_snapshot`、`presence_delta`、`workspace_metadata`、`access_revoked`、`resync_required`、`protocol_error`、`operation_ack`。
- 每則 server 事件都帶 `type`、`protocolVersion`、`workspaceId`、`connectionEpoch`（驗證前的 `protocol_error` 這兩個欄位為 null）。
- 正式 JSON Schema：`tests/fixtures/contract/realtime-v1/schemas.json`（由 `src/realtime/messages.ts` 的 TypeBox 產生，server 驗證使用同一份）。
- 錯誤碼：`authentication_required`、`ticket_expired`、`ticket_used`、`scope_mismatch`、`unsupported_protocol_version`、`unsupported_schema_version`、`access_revoked`、`invalid_message`、`message_too_large`、`rate_limited`、`sequence_gap`、`resync_required`，另加 `not_live`、`heartbeat_timeout`。
- Close codes：4400 invalid、4401 ticket / auth、4403 撤權、4408 驗證逾時、4409 resync、4413 過大、4426 不支援版本、4000 heartbeat 逾時、4429 未驗證連線過多、1001 關機。
- Snapshot reasons：`checkpoint_not_at_transaction_boundary`、`checkpoint_ahead_of_server`、`catch_up_too_large`。Resync reasons：`too_many_pending_transactions`、`transaction_too_large`、`slow_consumer`、`sequence_gap`。
- 待確認語意（已採用）：同 ID 但內容不同的重送沿現有 service，以 operationId 去重回 `duplicate`（原結果），不比對 payload；部分重複回 `rejected partial_duplicate`。

### 3. Migration、部署與相容性
- Migration `006_realtime.sql`：`workspaces.metadata_revision`、`realtime_tickets`、UNLOGGED `presence_rooms` / `presence_peers`、outbox 索引。
- Broker：**PostgreSQL LISTEN / NOTIFY**（不需要 Redis）。outbox 在 commit 同一 transaction 寫入；每個 API process 都執行 publisher（`SKIP LOCKED` + 同交易 `pg_notify`），crash 後由其他 process 補發。NOTIFY 只是喚醒，內容一律從 operation log 讀。
- 每個 process 需一條額外的 session-mode PostgreSQL 連線給 LISTEN（PgBouncer transaction mode 不支援）。
- 設定：`REALTIME_ENABLED`、`REALTIME_PUBLIC_URL`（正式必須 `wss://`）、`REALTIME_ALLOWED_ORIGINS`、`REALTIME_TICKET_TTL_SECONDS`、`REALTIME_HEARTBEAT_INTERVAL_MS`、`REALTIME_PRESENCE_TTL_MS`、`REALTIME_MAX_MESSAGE_BYTES`、`REALTIME_MAX_PENDING_TRANSACTIONS`。反向代理需支援 WebSocket upgrade 與 `Sec-WebSocket-Protocol`。
- 新依賴：`@fastify/websocket`（dev：`@types/ws`）。Bun 的 `ws` 相容層不強制 `maxPayload`，gateway 自行檢查 frame 大小。
- 相容性：Workspace REST 回應新增 `metadataRevision` 欄位（只增不改）；`/capabilities` 結構擴充（`assetPreparation` 不變）。

### 4. Fixtures 與 schema 版本
- `tests/fixtures/contract/realtime-v1/`（33 個 + `schemas.json` + `index.json`），protocol 1、schema 1、subprotocol `boarderless.v1`。
- 涵蓋：capabilities、ticket（成功 / 不支援 protocol / 非成員）、join 空 Workspace → live、ticket unknown / used / expired / scope mismatch、catch-up（單 op、多 op transaction）、snapshot_required（中段 / 超前）、snapshot ahead of barrier、catch_up_applied gap、WS accepted / 跨 gateway fan-out / duplicate / 200 operations / conflict / rejected / partial_duplicate、presence delta / rate limit / heartbeat、metadata、viewer forbidden、access_revoked、撤權移除 presence、unknown type、不支援 protocol、過大 frame、過大 transaction resync、client 端序列驗證（重複 / gap / 亂序 / 同 ID 不同內容）。
- 匿名化：UUID（含 map key）、時間、ticket、webSocketUrl、retryAfterMs 皆固定化；`tests/unit/contract-fixtures.test.ts` 檢查每個 frame 符合 schema 且不含真實 host / ticket。

### 5. 尚待集中執行
- RT-01 – RT-05（見 [`ACCEPTANCE.md`](ACCEPTANCE.md)）。目前證據：
  - 整合測試（15 個；同一 OS process 內兩個 gateway、各自連線池與 LISTEN）。
  - **真多 process 預檢**：`bun run acceptance:multi-process`，兩個 `src/server.ts` process、kill -9、checkpoint 重新 join，11 項通過。
- 未涵蓋：正式 auth / Origin / proxy 實際部署、外部日誌 redaction 驗證、APP 雙端（文字 / Group / 媒體 / relation 收斂）、視覺 overlay 簽核、負載與背壓數字（效能最後）。

---

## 批次 B：雲端快速方案簿（2026-10-11）

### 1. 分支與 commit
- 分支 `backend/media-v1`（基底 `89a85a3`），**尚未 commit**（與批次 A 同一批未提交變更）。
- 狀態：源碼完成 ✅；服務已部署 ⬜；正式驗收 ⬜。
- 未完成：跨 workspace 素材 materialization（`materializationEnabled: false`，引用其他 workspace 素材的方案由 APP 阻擋插入）；`rate_limited`（429 / Retry-After）尚未實作。

### 2. 路由與 capability
| 介面 | 說明 |
| --- | --- |
| `GET /api/v1/users/me/quick-scheme-capabilities` | `resourceVersions [2]`、`selectionSchemaVersions [1..9]`、limits、`idempotencyKeyTtlSeconds`（30 天）、`cursorTtlSeconds`（600）、`materializationEnabled: false` |
| `GET /api/v1/users/me/quick-schemes?cursor&limit` | `{items, nextCursor, collectionRevision}` + collection ETag；首頁 If-None-Match 命中 304；limit 1..100（預設 50）；單頁 ≤ 4 MiB |
| `GET /api/v1/users/me/quick-schemes/:id` | resource + ETag；If-None-Match 命中 304 |
| `PUT /api/v1/users/me/quick-schemes/:id` | create：`If-None-Match: *`；update：`If-Match: <最新強 ETag>`；必填 `Idempotency-Key`（小寫 UUID） |
| `DELETE /api/v1/users/me/quick-schemes/:id` | `If-Match` + `Idempotency-Key`；204；保留 tombstone |
| `GET /api/v1/capabilities` | `quickSchemes.supported: true`、版本、`materializationEnabled: false` |

- 可寫 body：`resourceVersion`（2）、`name`（trim 後 1..80 Unicode scalar，保存 trim 後的值）、`schemaVersion`、`payload`（JSON object，`payload.version` 必須明示且等於 schemaVersion）、`assetBindings`。其他欄位（ownerId、revision、etag…）一律 422。
- 回應另含讀取時計算的 `assetAvailability[]`（`available` / `unavailable` / `access_denied`），不是授權、不含 URL。
- 錯誤碼（HTTP）：`precondition_required`（428）、`invalid_idempotency_key`（400）、`revision_conflict`（412，details `revision` / `exists` / `deleted`）、`idempotency_mismatch`（409）、`quota_exceeded`（409）、`asset_unavailable`（409）、`asset_access_denied`（403）、`invalid_asset_binding`（422）、`invalid_payload`（422，details `issue` 沿用 APP `ClipboardPayloadIssue` 名稱，另有 `Malformed`、`InvalidVectorPath`、`InvalidTextPlacement`）、`unsupported_resource_version`（422）、`unsupported_selection_schema`（422）、`invalid_json`（400，details `reason`: duplicate_key / invalid_unicode / depth / syntax）、`payload_too_large`（413）、`cursor_expired`（410）、`invalid_cursor`（400）、`not_found`（404，含他人的方案 ID）。
- 規則：
  - 所有 mutation 在 owner 的 collection row lock 內完成 receipt → precondition → 驗證 → quota → 素材 ACL → 保存資源 / revision / collectionRevision / pin / receipt（同一 transaction）；同 ETag 並行更新只有一個成功。
  - Receipt：同 owner + key + 相同 fingerprint（method、資源、precondition、body）回原結果（原 status / body / ETag，header `Idempotent-Replayed: true`），即使之後 ETag 已改變；不同內容 409。只保存成功結果；保留 30 天。**到期規則**：到期後同 key 重送視為新請求，create 會因資源已存在得到 412、update / delete 會因 ETag 已變得到 412，client 依衝突流程先 GET，不會重複建立或盲目覆蓋。
  - 分頁快照：cursor 綁定 owner、collectionRevision 與期限；快照期間任何 create / update / delete 都讓下一頁回 410 `cursor_expired`（不會靜默漏資料）。
  - 嚴格 JSON：parse 前限制 1 MiB；重複 key、孤立 surrogate、深度 > 64、非有限數字一律 400。
  - assetBindings 精確覆蓋 payload 的原檔 / 縮圖引用；以 authenticated user 重新檢查來源 workspace membership；素材必須 ready、mediaKind 相符；縮圖必須由原檔 metadata（`thumbnailAssetId` / `sourceAssetId`）證明是其 derivative。
  - Pin：create / update 依 bindings 重建、delete 移除；目前沒有會刪除 ready 素材的 GC，pin 為日後 GC 的保留依據。
  - Header：`Cache-Control: private, no-store`；CORS 允許 `If-Match` / `If-None-Match` / `Idempotency-Key`，expose `ETag` / `Retry-After` / `Idempotent-Replayed`。

### 3. Migration、部署與相容性
- Migration `007_quick_schemes.sql`：`quick_scheme_collections`、`quick_schemes`（tombstone）、`quick_scheme_receipts`、`quick_scheme_asset_pins`。
- 無新外部依賴。TypeScript lib 提升到 ES2024（`String.prototype.isWellFormed`；Bun 1.4 / Node 20+ 皆支援）。
- 相容性：純新增路由；CORS 允許 / expose 的 header 增加。

### 4. Fixtures 與 schema 版本
- `tests/fixtures/contract/quick-scheme-v2/`（44 個）：resourceVersion 2、selection v1–v9（含 legacy 最小 v1、v4 真實素材 + 縮圖）。
- 涵蓋：capabilities、各版本 round-trip、create / get / 304 / update / stale 412 / replay / idempotency mismatch / existing 412 / 428 ×2 / 他人 404 / delete / delete replay / 刪除後 404 / tombstone 防復活、422 ×4、重複 key 400、缺 binding 422、非成員 403、quota 409、分頁兩頁 / 304 / 列舉途中變更 410。
- 樣本 payload：`tests/support/selection-samples.ts`，**依 APP `ClipboardPayload.kt` 原始碼手寫的 kotlinx 形狀**（encodeDefaults、explicitNulls、`type` / `mode` discriminator、`{value}` ID），不是 Kotlin serializer 實際輸出；APP 端需以 decode → validate → re-encode 核對，若有差異以 APP serializer 為準修正樣本。
- 後端 selection validator：`src/domain/selection.ts`（`validateClipboardPayload` 與各型別 init 規則的移植，8 個單元測試、涵蓋 APP 各 issue code）。

### 5. 尚待集中執行
- SCHEME-01 – 03（見 [`ACCEPTANCE.md`](ACCEPTANCE.md)）。目前證據：15 個整合測試（同一 process）、selection 單元測試、fixtures。
- 未涵蓋：APP 雙裝置 CRUD / 衝突 UI、APP 端以 Kotlin serializer 對 fixtures 的雙向驗證、跨畫布 materialization（未交付）、正式 auth。
