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

---

## 批次 C：共用背景（2026-10-11）

### 1. 分支與 commit
- 分支 `backend/media-v1`（基底 `89a85a3`），**尚未 commit**。狀態：源碼完成 ✅；服務已部署 ⬜；正式驗收 ⬜。

### 2. Projection、operation 與 capability
- `GET …/state` 與新 snapshot（**snapshot schema 2**）新增 `canvasStyle`、`canvasStyleVersion`，與 objects / relations 同一 `throughServerSeq`（同一 repeatable-read 讀取點）。
- style schema 1：`{schemaVersion: 1, backgroundToken: default|warm|cool|#rrggbb（小寫）, gridStyle: {pattern: none|lines|dots, colorToken: auto|#rrggbb}}`；未知欄位、大寫 hex、alpha、URL 一律拒絕，不靜默修正。
- Operation `update_canvas_style`（沿用 `POST …/operations` 與 WS `submit_operations`）：payload `{expectedCanvasStyleVersion, canvasStyle}`。
  - Workspace 鎖內 CAS；成功 `canvasStyleVersion + 1`，與同交易其他 operation 共用 workspaceVersion，任一失敗全部 rollback。
  - 正式（committed）payload 為 `{expectedCanvasStyleVersion, canvasStyle（canonical）, canvasStyleVersion（新版本）}`；replay / fan-out 依此設定。
  - 同 style 的提交仍是一次正式接受與 +1；duplicate 只依原 transaction / operation ID 判定，不再 +1。
  - 不接受 `expectedObjectVersions`（422 `invalid_expected_versions`）；owner / editor 可寫，viewer / commenter 403。
- 錯誤：409 conflict envelope 擴充 `code: "canvas_style_version_conflict"`、`canvasStyle: {expectedVersion, actualVersion, canvasStyle, canvasStyleVersion}`（`conflicts: []`，workspaceVersion / lastServerSeq 為權威 checkpoint）；422 `invalid_canvas_style`（details.field）、`unsupported_canvas_style_schema`、`canvas_style_version_exhausted`。物件版本衝突的 409 形狀不變。
- Capability：`canvasStyle: {supported: true, operationKinds: ["update_canvas_style"], styleSchemaVersions: [1], snapshotSchemaVersion: 2, defaultStyle}`。
- Undo / Redo：APP 送新的 `update_canvas_style`（after = 原 before），版本單調增加，不退回歷史值。

### 3. Migration、部署與相容性
- Migration `008_canvas_style.sql`：`workspaces.canvas_style`（default）、`canvas_style_version`（0，CHECK 0..2^53-1）。既有 Workspace 全部為 default / 0，不讀任何裝置偏好。
- **相容性影響**：
  - `GET …/state` 與新 snapshot 內容多了 `canvasStyle` / `canvasStyleVersion`，嚴格 decoder 需更新。
  - 新建 snapshot 的 `schemaVersion` 改為固定的 snapshot schema 版本（2），不再沿用 workspace.schemaVersion。
  - schema 1 的舊 snapshot 原樣回傳（不補欄位、不改 checksum）；APP 依規則視為 default / 0。
  - 409 conflict 新增兩個 optional 欄位（只在 style 衝突時出現）。

### 4. Fixtures
- `tests/fixtures/contract/canvas-style-v1/`（14 個）：state default、accepted、state after、CAS conflict、大寫 hex、未知 schema、未知欄位、混合 rollback、duplicate、Undo 新版本、混合 accepted、viewer 403、snapshot schema 2、capabilities。
- 同時更新：`canvas-v1`（state 含 style）、`asset-preparation-v2/00`、`realtime-v1`（capabilities / schemas）。

### 5. 尚待集中執行
- STYLE-01 / 02。證據：8 個整合測試（CAS、並行只一個成功、rollback、duplicate、Undo、log replay = state、舊 snapshot）。
- 未涵蓋：APP picker / history 接線、grid world-unit / density / snap 雙端確認（本批不新增 spacing 欄位）、雙端視覺。

---

## 批次 D：連線 geometry、自循環、插線與 vectorPath（2026-10-11）

### 1. 分支與 commit
- 分支 `backend/media-v1`（基底 `89a85a3`），**尚未 commit**。狀態：源碼完成 ✅；服務已部署 ⬜；正式驗收 ⬜。
- 未完成：端點改接（`endpointRewire: false`，update_relation 改端點仍 `unsupported_change`）；新形狀 / Mermaid shape 對應不在本批。

### 2. 格式、規則與 capability
- `relation.style.geometry`（`boarderless.relation-geometry.v1`）。server 寫入格式為完整 canonical 形狀，所有欄位必填：
  - `schema`、`route`、`labelPlacement`；route 為 `{mode:auto}`、`{mode:manual, waypoints[1..32]}` 或 `{mode:loop, side, extent}`；label 為 `{mode:auto}` 或 `{mode:manual, pathFraction, tangentOffset, normalOffset}`。
  - 判別分支不接受不相干欄位；座標絕對值 ≤ 10,000,000；extent 16..4096；pathFraction 0..1；offset 絕對值 ≤ 4096。
- create 缺 geometry 合法（自動路徑）。update 帶 geometry 時整個子物件替換（style 淺層 merge），只改 colorToken 的舊 client 不會抹除 geometry；重設送完整 auto，explicit null 拒絕。驗證的是 merge 後的完整 relation。
- 自循環：source = target 只在明確 geometry 且 route 為 auto / loop 時允許；普通線不接受 loop，自循環不接受 manual。刪除端點時自循環只 cascade 一次，還原時版本只 +1。
- 平行線：不同 relationId 可有相同 source / target / intent。
- 鎖定：server 不禁止在鎖定節點上建立 / 刪除 relation（鎖定只限制節點本身的移動 / 修改 / 刪除）。
- 插入節點：不新增 operation，沿既有單一 transaction：`delete_relations`（R 版本 CAS）+ `create_object`（N）+ 兩個 `create_relation`。同交易建立的 N 可供後續 relation 引用；任一失敗全部 rollback，並行 CAS 只有一方成功；Undo / Redo 用 delete + restore_*（同 ID）。
- vectorPath / textPlacement：TextNode 的 `properties.vectorPath` / `properties.textPlacement`，不是新 objectType。
  - 寫入格式完整（含 style、schemaVersion）；commands 只允許 move / line / quadratic / cubic / close，2..4096 commands，≤128 subpath，第一段必須是 move，close 後只能接 move；viewBox 0.001..100000；點絕對值 ≤ 1,000,000（控制點可超出 viewBox）；style token `[A-Za-z0-9#_-]{1,64}`、strokeWidth (0, 128]；帶 path 的節點尺寸必須 > 0。
  - null 表示清除。delete / restore / log / state 都完整保留。
- 錯誤碼：`invalid_relation_geometry`、`unsupported_geometry_schema`（422）；vectorPath / textPlacement 不合法沿用 `invalid_object`（details 有 path）。
- Capability：`relationGeometry {supported, schema, selfLoop, parallelEdges, insertNodeTransaction, endpointRewire:false, limits}`、`vectorPath {supported, objectType:"text", property:"vectorPath", schemaVersions:[1], commandTypes, limits}`、`textPlacement {supported}`。
- 最小可寫版本 / 舊 client：canvas protocol 仍是 1。不認識 geometry 的舊 client 更新 style 時 geometry 會保留；不認識自循環 / vectorPath 的 client 應依 capability 唯讀降級（APP 端責任），server 不會改寫或丟棄這些欄位。

### 3. Migration、部署與相容性
- 不需要 migration（資料在既有 `style` / `properties` JSONB）。
- 相容性：原本 source = target 一律拒絕，現在帶 geometry 時接受；text properties 多了兩個可選 key。

### 4. Fixtures
- `tests/fixtures/contract/graph-v2/`（56 個）：
  - 四方向 / 平行、auto reset、只改 colorToken 保留 geometry、manual label。
  - 自循環缺 geometry / manual / 普通線 loop 422、自循環 cascade 一次與還原。
  - 6 種非法 geometry（不部分提交）。
  - forward / backward / both / none / self-loop 插入 + Undo + Redo、CAS race 409、receipt unknown → commit → committed。
  - vectorPath（多 subpath、孔洞、控制點超出、quadratic / cubic）、更新、textPlacement、null 清除、3 種非法 422、刪除 / 還原、最終 state 與 catch-up。

### 5. 尚待集中執行
- GRAPH-01 / 02 / 03。證據：8 個整合測試、fixtures 契約測試（每條 projection geometry 與 vectorPath 重新驗證）。
- 未涵蓋：APP renderer / hit test、端點改接、跨端拖曳 UI。

---

## 批次 E：時序圖（2026-10-11）

### 1. 分支與 commit
- 分支 `backend/media-v1`（基底 `89a85a3`），**尚未 commit**。狀態：源碼完成 ✅；服務已部署 ⬜；正式驗收 ⬜。

### 2. 格式、operation 與 capability
- `GET …/state` 與新 snapshot（**snapshot schema 3**）新增 `sequenceDiagrams`（依 containerId 排序的陣列）與 `sequenceDiagramsVersion`（初始 0）。
- 元素格式：`boarderless.sequence-canvas.v1`，欄位與 APP `SequenceCanvasDiagram` 的 kotlinx 輸出相同（graph ID 為 `{"value": "<uuid>"}`）；server 寫入格式所有欄位必填（role / kind / dashed / blockId / branchIndex / blocks / schema）。graph ID 必須是小寫 UUID，semantic ID 是 `[A-Za-z_][A-Za-z0-9_]{0,63}` 且拒保留字。
- **Wire 對應**：APP 的 `Map<CanvasObjectId, SequenceCanvasDiagram>` 在 wire 上是陣列，以每份的 `containerId` 為 key（順序無關，containerId 不可重複）。
- Operation `update_sequence_diagrams`：payload `{expectedSequenceDiagramsVersion, before: [...], after: [...]}`（各 ≤ 64）。
  - Workspace 鎖內：version CAS，且 before 必須與目前 metadata 完整一致（canonical 比較）；成功時 after 整份替換、version + 1。
  - 正式 payload：`{expectedSequenceDiagramsVersion, before, after（canonical）, sequenceDiagramsVersion（新版本）}`。
- **Graph bindings 在整個 transaction 結束時驗證**（APP `hasValidSequenceBindings` 規則）。只要 Workspace 有時序圖，每個 transaction 結束時都會檢查：
  - container / block 是 active group，祖先鏈無循環。
  - participant 是同 container 的 text，文字等於 label、沒有 vectorPath。
  - message relation 的端點、方向（Signal → none，其他 → forward）、label 與自循環（loop route）一致。
  - 跨 diagram 不共用物件或 relation；order 完整唯一、block 無循環、深度 ≤ 16、branch 非空、文字上限。
  - 因此刪除 / 修改被引用的節點或 relation 而不同交易更新 metadata 時，整個 transaction 拒絕。
- 錯誤：409 `code: "sequence_diagrams_conflict"` + `sequenceDiagrams: {expectedVersion, actualVersion, diagrams, sequenceDiagramsVersion}`；422 `invalid_sequence_binding`（details.containerId）、`invalid_sequence_diagram`（containerId 重複）、`sequence_diagrams_version_exhausted`；結構錯誤 `invalid_operation`。
- Capability：`sequenceDiagrams {supported, schema, operationKinds: ["update_sequence_diagrams"], snapshotSchemaVersion: 3, limits}`。
- Undo / Redo：新的 `update_sequence_diagrams`（版本單調），搭配 delete / restore_* 同交易還原 graph；只還原 metadata 而 graph 不存在時拒絕。

### 3. Migration、部署與相容性
- Migration `009_sequence_diagrams.sql`：`workspaces.sequence_diagrams`（`[]`）、`sequence_diagrams_version`（0）。
- 相容性：state / 新 snapshot 多兩個欄位，snapshot schemaVersion 變為 3（schema 1 / 2 的舊 snapshot 原樣回傳）。有時序圖的 Workspace 每個 transaction 多一次 binding 驗證查詢。

### 4. Fixtures
- `tests/fixtures/contract/sequence-v1/`（14 個）：
  - state 空、只送 metadata 422、graph + metadata 同交易建立、stale version 409、before 不一致 409。
  - 刪訊息 relation 422、只改 participant 文字 422、改名 + metadata 同交易、Return 非 dashed 422、非 UUID graph ID 422。
  - Undo 建立、Redo 建立（版本 4）、state、snapshot schema 3。
- APP clipboard v7 的時序圖（originalId 版本）由 `src/domain/selection.ts` 驗證，與方案簿共用。

### 5. 尚待集中執行
- GRAPH-04。證據：5 個整合測試、fixtures。
- 未涵蓋：APP 時序圖 wizard / 引導式編輯的 server 模式接線、雙端視覺。

---

## 批次 F：素材引用的歷史還原（2026-10-11）

### 1. 分支與 commit
- 分支 `backend/media-v1`（基底 `89a85a3`），**尚未 commit**。狀態：源碼完成 ✅；服務已部署 ⬜；正式驗收 ⬜。

### 2. Operation 與 capability
- Provenance：每次 `update_object` 實際改變 MediaNode 的 `assetId` / `thumbnailAssetId` 時，在同一 transaction 保存完整 before / after 引用（含 transactionId / operationId / actor），不是只存 patch。`restore_media_references` 本身也會記錄。
- Operation `restore_media_references`：payload `{objectId, expectedObjectVersion, originalTransactionId, originalOperationId, targetSide: before|after}`，**不接受 client 提交 assetId**。
  - 原交易必須是同 workspace、目前 authenticated actor、已 commit 的引用變更，且 objectId 相同。
  - 目標物件必須是 active、未鎖定的 MediaNode，版本等於 expectedObjectVersion。
  - 目前引用必須恰為原交易的另一側；成功時原檔與縮圖成對還原，其他 properties 不變，objectVersion + 1。
  - missing / rejected 素材允許作為歷史引用：不要求 ready、不讓素材復活、不重驗、不授予下載；只確認素材仍屬同一 Workspace。
  - 正式 payload 加上 `restoredReference {assetId, thumbnailAssetId}` 與 `objectVersion`，replay / fan-out 依此套用。
  - 一般 `create_object` / `update_object` 仍需 Ready（ASSET-07 未放寬）。
- 錯誤：`provenance_not_found`（沒有 provenance、交易不符、他人的交易、migration 前的舊變更）、`provenance_mismatch`、`media_reference_changed`（目前引用已被改寫）、`asset_not_found`（跨 scope）、409 物件版本 conflict、403（viewer / commenter）、`invalid_operation`（含 assetId 等未知欄位）。
- Capability：`mediaReferenceRestore {supported, operationKinds: ["restore_media_references"], requiresProvenance: true}`。

### 3. Migration、部署與相容性
- Migration `010_media_reference_provenance.sql`。既有引用變更沒有 provenance → 明確 `provenance_not_found`，不回推假資料。
- 相容性：純新增 operation kind；`update_object` 換素材時多寫一列 provenance。

### 4. Fixtures
- `tests/fixtures/contract/media-restore-v1/`（11 個）：建立、換素材、一般 update 換回 missing 素材 422、Undo（before）、Redo（after）、交易不符、stale 409、非另一側、他人 actor、client assetId 拒絕、capabilities。

### 5. 尚待集中執行
- 對應 ASSET-07 的 Undo 情境與 UNDO_RESTORE_ACCEPTANCE。證據：3 個整合測試（含成對縮圖還原）、fixtures。
