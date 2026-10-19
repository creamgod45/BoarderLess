# Backend 驗收追蹤

對照：[`docs/BACKEND_ACCEPTANCE_SPEC.md`](../docs/BACKEND_ACCEPTANCE_SPEC.md)（案例 ID 與通過條件以該文件為準）
最後更新：2026-10-12（冪等修正；批次 A–F 預檢）

> **整體交付未完成**：正式認證、429 限流、跨 workspace 素材搬移仍未實作（見 [`DELIVERY.md`](DELIVERY.md)）。

依規格，正式驗收要等所有業務功能完成、驗收版本封存後才集中執行。下表的**正式狀態**只用規格的六種值；**後端預檢**是目前未封存版本上的自動化證據，不能代替正式驗收，也不代表 APP 對接或雙端通過。

證據類型標示：
- **整合**：`bun test`，真 PostgreSQL + 本機 storage driver，同一 process 內並行請求
- **ffmpeg**：整合測試且使用真 ffmpeg / ffprobe
- **S3**：對 RustFS 的真 HTTP（無法連線時略過）
- **新 pool**：以新連線池 + 新 app instance 模擬重啟（**不算**真 DB 重啟）
- **真 DB 重啟**：`bun run acceptance:db-restart`，一次性 PostgreSQL 容器的 `docker restart` 與 `docker kill → start`，同一 app 與連線池
- **fixtures**：`tests/fixtures/contract/` 錄製的 request / response

## 共同資料正確性與安全

| ID | 正式狀態 | 後端預檢 | 缺口 / 備註 |
| --- | --- | --- | --- |
| CORE-01 | 待執行 | 整合：`protocolVersion` 不符回 `unsupported_protocol_version`；`GET /capabilities` 宣告 `assetPreparation`；所有 body 拒絕未知欄位 | capability 目前只涵蓋 preparation v2；即時協作等草案 endpoint 不存在（回 404 `route_not_found`） |
| CORE-02 | 待執行 | 整合 `request-validation.test.ts`、`asset-preparation.test.ts`；fixtures canvas-v1 / preparation-v2 | 2026-10-11 起全域 body 嚴格驗證（不做型別轉換）；querystring / params 仍依 schema 轉型 |
| CORE-03 | 待執行 | 整合：相同內容重送回原結果；同 transaction / operation ID 不同內容（payload、clientSeq、baseVersion、expectedObjectVersions、換 transaction、transaction ID 重用）409 `idempotency_mismatch`（REST 與 WS）；preparation 同 key 200 / 不同 metadata 409；方案簿 Idempotency-Key | — |
| CORE-04 | 待執行 | 整合：20 個並行寫入 serverSeq 連續、版本衝突、rollback；**真多 process**預檢（`acceptance:multi-process`）：兩個 `src/server.ts` process 同時提交 40 筆，serverSeq / version 連續無重複 | 多 process 預檢已通過；正式驗收待集中執行 |
| CORE-05 | 待執行 | 整合：非成員 404、viewer 403、撤權後 404（REST）；WS：viewer submit rejected forbidden、撤權 access_revoked + 4403、撤權後不再送內容 | — |
| CORE-06 | 待執行 | 整合 + fixtures：receipt partial / 跨 actor、fence 晚到原請求、fence 並行競爭；真 DB 重啟後 fence 仍成立 | — |
| CORE-07 | 待執行 | 整合 + fixtures：restore_objects / restore_relations、group 循環 / 非法 parent、cascade | — |
| CORE-08 | 待執行 | **真 DB 重啟**預檢通過（restart、kill 各 9 項 + durable job）：projection、receipt、operation replay、fence、tombstone、preparation identity | 已定案：現有測試資料不做內容搬移，schema 一律以 migration 交付；清除資料需另行明確授權；**正式上線前仍須資料檢查與處置計畫**（不得以「目前皆為測試資料」永久免驗）。snapshot 尚無 compaction |

## 素材 preparation v2

| ID | 正式狀態 | 後端預檢 | 缺口 / 備註 |
| --- | --- | --- | --- |
| ASSET-01 | 待執行 | 整合 + fixtures 01–03：201 / 200 / 409、並行只建一份 | — |
| ASSET-02 | 待執行 | 整合 + fixtures 04–07：golden vector、explicit null、不轉型、canonical MIME / checksum | — |
| ASSET-03 | 待執行 | 新 pool：回覆遺失後查回；整合：presign 失敗保留 reservation 與 lease；**真 DB 重啟**預檢通過 | — |
| ASSET-04 | 待執行 | 整合 + fixtures 13–19：同 ticketRequestId 同 lease、過期 409、新 lease 取代、complete / renew 競爭只一方成功 | 過期寬限：未被 GC 回收前（`UPLOAD_GC_GRACE_SECONDS`，預設 600 秒）已上傳的當前 lease 仍可 complete |
| ASSET-05 | 待執行 | 整合 + fixtures 20–25、35：wrong lease / checksum / requestId、並行 complete 只一個 job、v1 complete 回 `preparation_v2_required` | — |
| ASSET-06 | 待執行 | 整合 + fixtures 24–34：各 phase、tombstone、晚到 prepare 不復活、ready / accepted 禁止 PUT | — |
| ASSET-07 | 待執行 | ffmpeg：有效舊 PUT 不能改 ready 原檔、內容變更 → rejected、下載 hash 一致；新建 / 替換引用 pending、rejected → 422 `asset_not_ready`；missing 後 restore 與不改引用的編輯照常 | 已定案：新建 MediaNode 與替換素材必須引用同 Workspace 且 ready 的素材；`restore_objects` 還原原引用不要求 ready，素材 missing / rejected 時由 APP 顯示不可用，還原不是重新授權也不讓素材復活。APP 端「錯檔不得授予映射」待對接驗收 |
| ASSET-07b（批次 F） | 待執行 | 整合 + fixtures：restore_media_references 依 provenance 還原到 missing 原素材、成對縮圖、一般 update 仍需 Ready、他人 / 改寫 / 無 provenance 拒絕 | 對應 UNDO_RESTORE_ACCEPTANCE；APP Undo 接線待對接 |
| ASSET-08 | 待執行 | 契約已定案並實作（見下節）；ffmpeg + fixtures 36–37 | APP 需改用 `declared` 綁定 identity、`verified` 取實際尺寸 |
| ASSET-09 | 待執行 | — | 需 APP 對接後雙端執行 |

### ASSET-08 定案（2026-10-11）
- `declared`：prepare 時的 canonical metadata，也是 `requestDigest` 涵蓋的內容，永不改寫。
- `verified`：worker 實際解析值，ready 時寫入；因 `declared_metadata_mismatch` 被 rejected 時也保存，供診斷。
- 尺寸定義為**顯示尺寸**：已套用 JPEG / PNG / WebP 的 EXIF orientation 與影片 display matrix rotation（Media v1 的 `asset.width/height` 同一定義）。
- byteSize / checksum 必須完全相符；declared 寬高非 null 時必須完全相同；duration 容差為 `abs(declared − verified) ≤ max(100ms, verified × 1%)`（產品容差）；宣告了 duration 但內容沒有 duration 視為不符。
- declared 為 null 表示未宣告，不比較；格式、解碼、大小限制照常驗證，必要資訊解析失敗不會 ready。

## 相容性影響（2026-10-11，影響既有 Media v1 / operation client）

以下變更讓原本可通過的請求或回應改變，**不是**對舊版完全無影響；APP 對接須依更新後的 schema / fixtures：

| 變更 | 影響範圍 | 舊版可能遇到的情況 |
| --- | --- | --- |
| 所有 request body 嚴格驗證（不做型別轉換） | 全部 v1 寫入 API（operations、workspaces、members、assets） | 以字串送數字（如 `"baseVersion": "0"`）原本被轉型接受，現在回 400 `validation_error`；錯誤訊息改為列出全部錯誤 |
| 寬高改為顯示尺寸 | Media v1 `asset.width/height`、preparation v2 `verified` | 帶 EXIF orientation 5–8 的影像、旋轉 ±90° 的影片，寬高與先前相反 |
| 寬高上限 2147483647、duration 上限 JSON safe integer | Media v1 prepare | 超過上限原本回 500，現在回 400 |
| 同 ID 不同內容的重送改為 409（冪等修正） | `POST …/operations`、WS `submit_operations` | 原本回 200 `duplicate`；現在 409 / rejected `idempotency_mismatch`。migration 011 之前提交的 operation 重送一律 `original_digest_unavailable` |
| `validation_error` details 改為 TypeBox 格式（CORE-02） | 所有 400 `validation_error` | details 陣列的欄位（`schemaPath`、順序）與 Ajv 不同；只比對 `error.code` 的 client 不受影響 |
| 自循環在帶 geometry 時被接受（批次 D） | `create_relation` | 原本 source = target 一律 422；現在帶 auto / loop geometry 時接受 |
| state / snapshot 新增 sequenceDiagrams（批次 E） | `GET …/state`、新 snapshot（schema 3） | 嚴格 decoder 需接受新欄位；有時序圖的 Workspace 中，破壞 binding 的 graph 變更會被拒絕 |
| state / snapshot 新增 canvasStyle（批次 C） | `GET …/state`、新 snapshot（schema 2） | 嚴格 decoder 需接受新欄位；新 snapshot 的 schemaVersion 改為 2 |
| MediaNode 新引用必須 ready（ASSET-07） | `create_object`、替換 `assetId` / `thumbnailAssetId` 的 `update_object` | 引用 pending / rejected / missing 素材原本接受，現在回 422 `asset_not_ready`；替換素材的 Undo 改用 `restore_media_references`（批次 F），刪除的 Undo 用 `restore_objects` |
| v1 complete 不能用於 v2 資產 | `POST /assets/:id/complete` | v2 建立的資產回 409 `preparation_v2_required` |

## 即時協作、快速方案簿、共用背景、圖形語意

| ID | 正式狀態 | 後端預檢 | 缺口 / 備註 |
| --- | --- | --- | --- |
| RT-01 | 待執行 | 整合：ticket 一次性 / 過期 / 重用 / 跨 scope、3 秒未驗證關閉、Origin 與 subprotocol 於 upgrade 前拒絕 | **正式 HTTP auth 未交付**（目前 `x-user-id` dev 替身）；WSS 需由部署（TLS 反向代理 + `REALTIME_PUBLIC_URL`）提供 |
| RT-02 | 待執行 | 整合：先註冊訂閱再讀 head、完整 transaction catch-up、snapshot ahead of barrier、非邊界 checkpoint → snapshot_required / sequence_gap | — |
| RT-03 | 待執行 | 整合：相同內容重送回 duplicate ack、同 ID 不同內容回 rejected `idempotency_mismatch`、原 cursor 去重、REST 與 WS 提交走同一 service；client 端 gap / 亂序 / 同 ID 不同內容案例見 fixture 70 | APP reducer 對接後雙端驗證 |
| RT-04 | 待執行 | 同 process 兩 gateway 整合測試；**真多 process**預檢：跨 process fan-out、kill -9 後以 checkpoint 重新 join 補齊、outbox 由存活 process 補發；slow consumer / 過大 transaction → resync | broker 為 PostgreSQL LISTEN/NOTIFY（無 Redis）；負載與背壓數字留待效能驗收 |
| RT-05 | 待執行 | 整合：presence snapshot / delta、world 座標、未知 selection 丟棄、rate limit、heartbeat TTL 移除、roomEpoch 重建、撤權移除 | 視覺 overlay 由 QA 人工簽核 |
| SCHEME-01 | 待執行 | 整合：owner 由認證注入、他人 ID 一律 404、UUID 資源 / revision / opaque ETag、selection v1–v9 round-trip；fixtures | APP 雙裝置讀寫待對接 |
| SCHEME-02 | 待執行 | 整合：Idempotency-Key replay（含之後 ETag 已變）/ mismatch 409 / 並行同 key 只建立一次、同 ETag 並行更新只一個成功、tombstone 防復活、快照 cursor 變更 410 | receipt 保留 30 天，到期後的恢復規則見 DELIVERY.md |
| SCHEME-03 | 待執行 | 整合：assetBindings 精確覆蓋、來源 workspace ACL 403、非 ready 409、假縮圖 / 錯 kind 422、撤權後 availability=access_denied、讀取不插入畫布 | 跨畫布 materialization 未交付（capability false） |
| STYLE-01 | 待執行 | 整合：update_canvas_style CAS、同 expected 並行只一個接受、與圖形同交易 rollback、duplicate 不 +1、Undo 為新版本、viewer 403 | APP picker / history 待對接 |
| STYLE-02 | 待執行 | 整合：state / snapshot 同一 checkpoint、log replay = state、schema 1 舊 snapshot 原樣；WS fan-out 走同一 operation log | grid world-unit / density 雙端確認、視覺簽核 |
| GRAPH-01 | 待執行 | 整合 + fixtures：自循環 / 平行線、完整 geometry round-trip（create / update 整體替換 / delete / restore / log）、非法 schema / 數字 / null 拒絕不部分提交 | APP renderer 待對接 |
| GRAPH-02 | 待執行 | 整合 + fixtures：插入節點單一 transaction（四方向 + 自循環）、Undo / Redo、CAS race、端點被刪全交易拒絕、receipt 查詢 | 撤權途中的雙端案例待集中執行 |
| GRAPH-03 | 待執行 | 整合 + fixtures：vectorPath 邊界、create / update / null 清除 / delete / restore、textPlacement | 鋼筆 gesture / 雙端視覺 |
| GRAPH-04 | 待執行 | 整合 + fixtures：update_sequence_diagrams CAS / before 一致、graph + metadata 同交易、懸空 / 不一致引用全交易拒絕、Undo / Redo 新版本、snapshot schema 3 | APP server 模式接線待對接 |

## 預檢紀錄

| 日期 | 項目 | 版本 | 結果 | 證據 |
| --- | --- | --- | --- | --- |
| 2026-10-11 | 真 DB 重啟（CORE-08、ASSET-03） | 與 `ebd05fd` 相同的源碼（提交前執行） | 預檢通過（restart 296ms、kill 291ms 恢復） | `acceptance/results/2026-10-11T16-08-23-336Z-db-restart.json`（批次 A–F 與冪等修正後重跑） |
| 2026-10-11 | `bun test` | `15ad83b`（preparation v2、ASSET-07 / 08） | 預檢通過（224 tests） | 本機執行 |
| 2026-10-11 | 真多 process（CORE-04、RT-04） | 與 `ebd05fd` 相同的源碼（提交前執行） | 預檢通過（11 項） | `acceptance/results/2026-10-11T16-08-28-381Z-multi-process.json`（批次 A–F 與冪等修正後重跑） |
| 2026-10-12 | `bun test`（含批次 A–F 與冪等修正） | `ebd05fd` | 預檢通過（464 tests） | 本機執行 |
