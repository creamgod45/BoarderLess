# BoarderLess Backend

Workspace 儲存與同步服務。依 [`docs/BACKEND_ARCHITECTURE.md`](../docs/BACKEND_ARCHITECTURE.md) 規劃，目前進度與待辦見 [`PROGRESS.md`](PROGRESS.md)。

- Runtime / 套件管理：**Bun**（程式碼維持 Node.js 相容，不依賴 Bun 專屬 DB API）
- HTTP：Fastify 5 + TypeBox（JSON Schema 驗證、OpenAPI 自動產生）
- 資料庫：PostgreSQL（`postgres` driver）

## 快速開始

```bash
bun install
cp .env.example .env      # 第一次
bun run db:up             # docker compose 啟動 PostgreSQL（port 5433）
bun run migrate
bun run dev               # http://localhost:3000
```

| 位置 | 內容 |
|---|---|
| `GET /` | 服務狀態頁（page view） |
| `GET /docs` | OpenAPI / Swagger UI |
| `GET /health`、`/health/ready` | liveness / readiness |
| `/api/v1/*` | 儲存服務 API |

### 指令

| 指令 | 說明 |
|---|---|
| `bun run dev` | watch 模式啟動 |
| `bun run start` | 正式啟動 |
| `bun run migrate` | 套用 `migrations/*.sql` |
| `bun test` | 單元 + 整合測試（整合測試使用 `boarderless_test` DB，自動建立；DB 不可用時略過） |
| `bun run typecheck` | `tsc --noEmit` |

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
views/     伺服器端 HTML（自動跳脫的 tagged template）
models/    資料實體型別
schemas/   API request/response JSON Schema（驗證 + OpenAPI）
```

```text
src/
├── app.ts / server.ts     組裝 Fastify / 啟動入口
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

### 暫定 operation kinds

`create_object`、`update_object`、`move_objects`、`delete_objects`（同 transaction 連帶刪除 relations，記錄於 payload 的 `cascadedRelationIds`）、`create_relation`、`update_relation`、`delete_relations`。

> 這是 server 端的暫定集合，須與 KMP client 共用的 `WorkspaceOperation` 定稿後以 contract fixtures 對齊（見 PROGRESS.md）。
