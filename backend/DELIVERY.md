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
