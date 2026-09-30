# BoarderLess Node.js 後端、儲存與即時協作架構

版本：0.1
狀態：規劃草案；先定協定與資料邊界，不代表目前已實作
對應文件：`docs/PRODUCT_PLAN.md`、`docs/USER_STORIES.md`

## 1. 目標與非目標

後端的第一目標是可靠保存 Workspace，第二目標才是即時多人協作。所有正式變更沿用用戶端與 AI 共用的 `WorkspaceOperation`，讓單機、本機離線、伺服器同步、undo/redo 和 AI proposal 不產生五套不同資料語意。

架構必須：

- 先以單一 Node.js 實例交付，再不改協定地擴充到多程序、多實例。
- 對同一 Workspace 的正式 operations 建立唯一且可恢復的順序。
- 即使 WebSocket、Redis 或某個 Node.js process 中斷，也不能遺失已回覆成功的正式變更。
- 支援 Desktop-first KMP client，協定不綁定 JavaScript 或瀏覽器專屬資料型別。
- 圖片、GIF、影片不直接存入 PostgreSQL row；資料庫只保存 metadata 與 object-storage key。
- 暫態 presence 和正式 Workspace 狀態分離。

第一版不追求：

- 一開始就導入微服務、Kafka 或跨區多主寫入。
- 讓多個 process 共享可變的記憶體 Workspace。
- 在沒有真實衝突需求前，為所有物件導入完整 CRDT。
- 由 Redis 取代正式 operation log 或資料庫備份。

## 2. 建議技術組合

| 責任 | 建議 | 原因 |
|---|---|---|
| Runtime | Node.js LTS + TypeScript | 適合 I/O、WebSocket 與共用協定工具；以 LTS 線固定部署版本 |
| HTTP API | Fastify 或同級低開銷框架 | REST、schema validation、auth hook 與健康檢查足夠；暫不需要大型 framework runtime |
| Realtime | 標準 WebSocket + 版本化應用協定 | KMP、Web 與未來其他 client 都可實作，不綁定 JavaScript 專屬 client |
| 正式資料 | PostgreSQL | transaction、唯一約束、row/advisory lock、JSONB 與可靠備份 |
| 暫態協調 | Redis | presence TTL、跨 process 即時 fan-out、rate limit 與短期 cache |
| 耐久背景工作 | Redis Streams，後續依規模再評估專用 broker | 支援 consumer group、ack、重送；工作 handler 必須 idempotent |
| 素材 | S3-compatible object storage | 大檔、分段上傳、signed URL、生命週期與 CDN 邊界清楚 |
| Schema contract | JSON Schema / OpenAPI + contract tests | TypeScript 與 Kotlin 共享協定來源，避免手動模型逐漸分歧 |
| 可觀測性 | OpenTelemetry-compatible traces、structured logs、metrics | 能跨 API、WebSocket、DB transaction 與 worker 追蹤同一 operation |

資料庫與 Redis 的角色不可互換：PostgreSQL 是正式內容的 source of truth；Redis 可以清除並重建。Redis Pub/Sub 只用於在線 fan-out，斷線補資料一律從 PostgreSQL operation log 依 `serverSeq` 追回。

## 3. 邏輯拓撲

```text
KMP / Web clients
        │ HTTPS + WebSocket
        ▼
Load balancer / TLS termination
        │
        ├──────────────┐
        ▼              ▼
API + Realtime A   API + Realtime B     stateless I/O processes
        │              │
        ├──── Redis ────┤                presence, live fan-out, rate limits
        │              │
        └──────┬───────┘
               ▼
          PostgreSQL                    workspace state, operations, ACL, outbox
               │
               ├──── Object storage     images, GIF, video, exports
               │
               └──── Redis Streams ───► background workers
                                       snapshots, thumbnails, scan, transcode, export
```

API 與 realtime process 不持有唯一真相。process 重啟後，連線中的 client 以最後確認的 `serverSeq` 重新加入並補齊 operations。

## 4. 多核心與多實例策略

### 4.1 I/O 工作

- 開發環境或單台主機可以使用 Node.js `cluster`，依可用核心啟動多個獨立 process 並共享 listen port。
- 正式容器環境優先採「一個 Node.js process／一個 container」，由 orchestrator 水平增加 replicas；這比在每個 container 內再建立 cluster 更容易設定 CPU limit、健康檢查與滾動更新。
- WebSocket 連線不要求永久 sticky session。若 load balancer 提供 connection affinity 可降低重連成本，但正確性必須來自外部資料層與 reconnect protocol。
- session、Workspace 狀態、room membership 的正式依據不得只存在單一 process memory。

### 4.2 CPU 工作

`worker_threads` 只處理會阻塞 event loop 的 CPU 工作，例如大型 snapshot 壓縮、縮圖、部分資料 migration 或純 JavaScript 解析。必須使用固定大小的 worker pool，不能每個 request 建立一個 thread。

影片轉碼、病毒掃描或外部工具工作放在獨立 background-worker process/container，由 durable queue 觸發；失敗不得拖垮 WebSocket process。

### 4.3 同一 Workspace 的順序

多核心不代表同一 Workspace 的 operations 可以任意平行提交。第一版採資料庫序列化：

1. 開啟 PostgreSQL transaction。
2. 取得 Workspace row lock，或依 `workspaceId` 取得 transaction-level advisory lock。
3. 檢查 membership、client operation id、`baseVersion` 和目標 object versions。
4. 配發遞增的 `serverSeq`，寫入 operation log。
5. 更新目前 projection、Workspace version 與 transaction outbox。
6. Commit 後才回覆 accepted，並由 outbox/fan-out 發布事件。

這使不同 Node.js process 可以同時服務不同 Workspace；同一 Workspace 的短 transaction 則具有可重建的全域順序。負載證明資料庫鎖已成瓶頸後，再升級為依 `workspaceId` 分片的 workspace-owner/actor，不在第一版提前增加分散式複雜度。

## 5. 儲存模型

採「正規化 metadata + 可索引的目前 projection + append-only operation log + 週期 snapshot」混合模型。

### 5.1 主要資料表

#### `users`

- `id`
- `display_name`
- `created_at`、`disabled_at`

登入供應商與 credential 應與公開 profile 分表保存。

#### `workspaces`

- `id`
- `owner_id`
- `title`
- `current_version`
- `last_server_seq`
- `schema_version`
- `created_at`、`updated_at`、`deleted_at`

#### `workspace_members`

- `workspace_id`
- `user_id`
- `role`: owner / editor / commenter / viewer
- `joined_at`、`revoked_at`

唯一鍵為 `(workspace_id, user_id)`。所有讀寫路徑都必須檢查 membership；PostgreSQL Row-Level Security 可作為額外防線，但不能取代應用層授權測試。

#### `canvas_objects`

- `workspace_id`
- `object_id`
- `object_type`
- `object_version`
- `parent_id`
- `z_index`
- `locked`
- `transform JSONB`
- `properties JSONB`
- `created_by`、`updated_by`
- `created_at`、`updated_at`、`deleted_at`

穩定且常查詢的欄位獨立成 column；各 Node 型別差異較大的內容先放 `properties JSONB`，並以應用 schema validation 控制。不要把整個 Workspace 永遠塞在單一無限制 JSON row。

#### `relations`

- `workspace_id`
- `relation_id`
- `relation_version`
- `source_object_id`
- `target_object_id`
- `direction`
- `intent`
- `label`
- `style JSONB`
- `created_at`、`updated_at`、`deleted_at`

來源和目標必須屬於同一 Workspace。刪除 Node 時對 relation 的行為由同一 transaction 明確處理，不能留下無法解釋的半連線。

#### `workspace_operations`

- `workspace_id`
- `server_seq`
- `operation_id`
- `transaction_id`
- `actor_id`
- `client_id`
- `client_seq`
- `base_version`
- `operation_type`
- `payload JSONB`
- `schema_version`
- `committed_at`

主要鍵為 `(workspace_id, server_seq)`；另設 `(workspace_id, actor_id, operation_id)` unique constraint，讓 client retry 具有 idempotency。

#### `workspace_snapshots`

- `workspace_id`
- `through_server_seq`
- `schema_version`
- `snapshot JSONB` 或大型 snapshot 的 object-storage key
- `checksum`
- `created_at`

Snapshot 是加速載入的 checkpoint，不取代 operation log。建立完成且 checksum 驗證後，才能更新 Workspace 的 active snapshot reference。

#### `assets`

- `id`
- `workspace_id`
- `owner_id`
- `storage_key`
- `media_type`
- `byte_size`
- `checksum`
- `width`、`height`、`duration_ms`
- `status`: pending / ready / rejected / missing
- `created_at`、`deleted_at`

上傳先建立 pending metadata，client 使用短效 signed URL 直傳 object storage；完成後由 worker 驗證 checksum、實際格式、限制與必要衍生檔。

#### `transaction_outbox`

- `id`
- `workspace_id`
- `server_seq`
- `event_type`
- `payload JSONB`
- `published_at`
- `attempt_count`

Operation 與 outbox 在同一 PostgreSQL transaction 寫入，避免資料已提交但廣播永久遺失。重複發布是允許的，consumer 依 event/operation id 去重。

## 6. 即時協作協定

### 6.1 連線流程

1. Client 以短效 access token 建立 WebSocket。
2. 傳送 `join_workspace`，包含 `workspaceId`、`clientId`、client 支援的 protocol/schema version、`lastSeenServerSeq`。
3. Server 驗證 membership。
4. 若落後量小，回傳缺少的 operations；落後過多或 schema 不相容時，回傳最新 snapshot + 後續 operations。
5. 進入 live 狀態後才發布 presence。

### 6.2 Operation envelope

```json
{
  "type": "submit_operations",
  "protocolVersion": 1,
  "workspaceId": "uuid",
  "clientId": "uuid",
  "transactionId": "uuid",
  "baseVersion": 42,
  "operations": [
    {
      "operationId": "uuid",
      "clientSeq": 17,
      "kind": "move_objects",
      "expectedObjectVersions": { "node-a": 8 },
      "payload": {}
    }
  ]
}
```

Server 回覆至少區分：

- `accepted`：包含 `serverSeq` 範圍、新 Workspace version 與正式 operations。
- `duplicate`：此 operation 先前已提交，回覆原 accepted 結果。
- `conflict`：包含目前 object version 與可供 client rebase 的缺少 operations。
- `rejected`：權限、schema、quota 或 invariant 不成立，正式資料未改變。

只有收到 `accepted` 或已確認的 `duplicate`，client 才能把本機 pending operation 標記為伺服器已保存。

### 6.3 Presence

游標、viewport、selection、正在輸入等 presence：

- 不寫入正式 history。
- 在 Redis 使用 TTL，自動清除異常離線狀態。
- 可以合併、節流或丟失；下一個 presence update 可覆蓋上一個。
- 不可包含未授權 Workspace 的內容摘要。

### 6.4 衝突策略

第一版採 operation-based optimistic concurrency，而不是全面 CRDT：

- 不同物件的操作可依 server order 合併。
- 同物件樣式或 transform 以 `expectedObjectVersion` 偵測衝突；初期回覆 conflict 讓 client rebase 或選擇版本，不靜默 last-write-wins。
- 群組、刪除與 relation 等會影響多物件 invariant 的操作必須在單一 transaction 驗證。
- 文字 Node 初期採 object version 衝突。只有產品驗證出現多人同時編輯同一段文字的實際需求，再為文字內容導入獨立 CRDT；Canvas domain protocol 不綁死特定 JavaScript CRDT library。

## 7. Server 專案結構

建議在 repository 新增獨立 `server/` workspace：

```text
server/
├── apps/
│   ├── gateway/          # REST、WebSocket、auth、workspace join
│   └── worker/           # outbox、snapshot、thumbnail、scan、transcode
├── packages/
│   ├── protocol/         # JSON Schema/OpenAPI、message types、fixtures
│   ├── domain/           # operation validation、invariants、conflicts
│   ├── database/         # migrations、repositories、transaction boundary
│   ├── realtime/         # rooms、presence、catch-up、fan-out adapter
│   ├── object-storage/   # signed upload/download 與 asset lifecycle
│   ├── observability/    # logging、metrics、trace context
│   └── test-support/     # protocol fixtures、DB/Redis integration helpers
├── migrations/
├── tests/
│   ├── contract/
│   ├── integration/
│   └── load/
└── package.json
```

初期仍以 modular monolith 部署：`gateway` 與 `worker` 兩種 process 即可。packages 是程式邊界，不代表每個 package 都要變成獨立服務。

## 8. 寫入、重連與背景工作流程

### 8.1 正式寫入

```text
client optimistic apply
  -> submit operation
  -> authenticate + authorize + schema validate
  -> acquire workspace transaction lock
  -> idempotency + version + invariant checks
  -> append operation + update projection + write outbox
  -> database commit
  -> accepted ack
  -> fan-out to other live clients
```

Client 樂觀更新可以立即顯示，但 pending/confirmed 狀態必須分開。Server reject 時 client 以正式 operations 重建或執行明確 rollback。

### 8.2 斷線重連

- Client 本機保存尚未確認的 operations 與最後 `serverSeq`。
- 重連先補 server operations，再逐筆或逐 transaction 重送 pending operations。
- Server 依 operation ID 去重；不得靠連線 ID 判斷是否重複。
- 若 schema 已過期，server 要求 client 更新或下載 snapshot，不直接猜測舊 payload。

### 8.3 背景工作

- Snapshot/compaction 依 operation 數量、資料大小或時間觸發，門檻以量測調整。
- Thumbnail、媒體 metadata、病毒掃描與轉碼使用 durable queue。
- Worker handler 至少一次執行，必須能安全重跑；完成結果以 job id 或 asset derivative unique key 去重。
- Redis Streams 可以承載中等規模工作；若未來需要長期大量事件保留、跨區或更多 consumer topology，再根據量測評估 Kafka 類系統。

## 9. 安全、備份與資料生命週期

- Access token 短效；refresh token、provider identity 與公開 profile 分離保存。
- 每個 REST request、WebSocket join 與正式 operation 都重新檢查 Workspace 權限，不能只信任 client UI。
- Upload 使用短效、限定 object key、大小和 content constraints 的 signed URL。
- 以檔案實際內容驗證 media type；對不可信素材設 size、pixel、duration、解碼與轉碼限制。
- PostgreSQL 啟用 point-in-time recovery 能力並定期驗證 restore；object storage 使用 versioning/lifecycle 規則。
- Soft delete 有明確保留期；真正清除 Workspace 時同步排程 assets、snapshots 與衍生檔清理。
- Log 不記錄 token、完整 Node 文字或 signed URL；以 workspace/operation correlation ID 除錯。

## 10. 測試與可觀測性

### 必要測試

- Domain property tests：每個可逆 operation 套用後再 inverse，回到一致狀態。
- Protocol contract tests：TypeScript fixtures 與 Kotlin serializer 讀取相同消息。
- Idempotency：相同 operation 重送不重複寫入。
- Ordering：多 process 同時寫同一 Workspace 時 `serverSeq` 唯一且連續。
- Reconnect：ack 遺失、WebSocket 中斷、Redis 重啟後仍能從 DB catch up。
- Transaction failure：operation、projection 和 outbox 不出現部分提交。
- Permission：撤銷 membership 後既有 socket 不能繼續寫入。
- Asset：中斷上傳、錯誤 MIME、重複 checksum、遺失 object 與清除流程。
- Load：大量 idle sockets、presence burst、熱點 Workspace、不同 Workspace 平行寫入與 snapshot worker 壓力。

### 核心指標

- WebSocket active connections、join/reconnect rate。
- Operation accepted/conflict/rejected/duplicate rate。
- Commit latency、workspace lock wait、DB pool saturation。
- Client `serverSeq` lag、fan-out delay、outbox backlog。
- Redis Stream pending count、job retry/dead-letter count。
- Snapshot age/size、asset processing latency。
- Event-loop lag、process RSS、CPU 與 worker-pool queue depth。

在取得實際 Workspace 大小與協作者行為前，不先承諾單機連線數。先建立可重複 load scenario，再以 p95/p99 latency、event-loop lag 和資料庫鎖等待決定擴容門檻。

## 11. 交付階段

### B0：Protocol 與單機資料基線

- 定義 UUID、版本、operation envelope、error envelope 與 JSON Schema。
- 建立 PostgreSQL migration、repository transaction 與 integration test 基礎。
- Kotlin/TypeScript contract fixtures 可互相讀取。

完成標準：create/move/delete Node 能由 client schema 表示，在 PostgreSQL transaction 中提交並冪等重送。

### B1：單實例保存與同步

- Workspace CRUD、membership 與 snapshot download。
- WebSocket join、submit、accepted/conflict、catch-up。
- 斷線 pending operations 重送。

完成標準：兩個 client 透過單一 server 編輯同一 Workspace，server 重啟後資料與順序仍正確。

### B2：多程序與即時 fan-out

- 多個 gateway process/replicas。
- Redis presence 與 live fan-out。
- PostgreSQL lock、server sequence 與 transaction outbox。
- kill process、Redis 暫時中斷與重連測試。

完成標準：兩個 client 即使連到不同 process，仍接收一致順序；已 ack operation 不因 process 故障遺失。

### B3：資產與背景工作

- Signed upload、asset metadata 與 object storage。
- Redis Streams worker、縮圖、檔案檢查、snapshot compaction。
- retry、idempotency 與 dead-letter 處理。

完成標準：圖片可上傳、分享、重新開啟與刪除；worker 重啟不會產生重複衍生檔或遺失工作。

### B4：協作產品化

- Presence、協作者 UI 所需資料、分享與權限。
- 衝突 review、活動紀錄、版本回復。
- 觀測 dashboard、backup restore drill、容量與故障測試。

完成標準：符合 `US-CL-001`，並有可重複的故障與負載驗證報告。

## 12. 現在要先決定的契約

以下項目應在開始 server code 前定稿到能寫 contract test：

1. `WorkspaceOperation` 種類、inverse 所需資料及 transaction 邊界。
2. Workspace version、object version、`serverSeq` 與 `clientSeq` 的語意。
3. Node、Relation、Group、Lock、Layer 和 Asset 的穩定 ID 規則。
4. Client optimistic state、pending state 與 confirmed state 的切換方式。
5. 刪除 Node 時 relation、group membership 與 asset reference 的規則。
6. Editor、viewer 等角色能提交哪些 operation。
7. Snapshot 格式、schema migration 與最低支援 client protocol version。

建議先完成 M0 的本機 domain/operation 測試，再平行啟動 B0。Server 不應先自行發明另一套 Node payload。
