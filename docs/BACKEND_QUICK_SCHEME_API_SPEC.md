# 快速方案簿跨裝置 APP／Backend 合約草案

日期：2026-10-02。對應 Priority 3／BAI-006。

狀態：**Proposal／待後端 agent 確認與實作**。本文所有新增路由、envelope、同步與素材 materialization 能力均尚未存在。APP 不得猜測 endpoint 或把本機方案標示為已同步。Priority 1 素材與 Priority 2 即時協作仍先於本項；效能驗收維持最後。本輪未修改或啟動後端。

## 1. 已確認基線與版本分層

唯讀對照：`backend/src/routes/api.routes.ts`、`schemas/asset.schema.ts`；APP 的 `QuickSchemeStore.kt`、`QuickSchemePayload.kt`、`WorkspaceScreen.kt` 與 `AssetRepository.kt`。

- 本機方案使用遞增 Int ID 與 Settings，沒有遠端穩定 ID、時間、revision 或 ETag；不能直接把 Int ID 當跨裝置主鍵。
- 新保存的素材方案另外保留本機 sourceWorkspaceId，rename／重啟保留、delete 一併移除；legacy 仍為 null，不假造來源。這是方案層級來源提示，不是正式逐 assetBindings 或授權證明。APP 目前只允許重用已確認 ready、MIME／ID／scope 相符的目前畫布素材，已知來源不同則等待正式 materialization；純文字方案仍可跨畫布插入。
- 本機 payload 是 JSON string；目前內嵌 `boarderless/selection` v4，包含 nodes、groups、media、relations。可讀 v1–v4；v4 保存 Image／GIF／Video asset reference。
- 本機預覽／插入共用驗證，包含 transform、ID、parent、cycle、shape、media、relation invariant；此不等於後端已驗證相同 schema。
- 素材 metadata 與 content route 是 workspace-scoped。持有 asset UUID 或方案所有權**不等於**持有素材讀取／複製權限。
- 現有 API 沒有 user-scoped Quick Scheme CRUD 或跨 workspace 素材轉移操作。

版本必須分開：`resourceVersion: 2` 是本規格的新同步 envelope；`schemaVersion: 4` 是 `payload.version`。不能把兩者混成單一 version，也不能將素材方案降成 selection v2。v1／v2 純文字／群組 fixture 與 v4 素材 fixture 都必須交付。

## 2. 身分、資源與欄位

建議 collection：`/api/v1/users/me/quick-schemes`。user 由已驗證身分決定，body、query、path 不接受自選 ownerId。現有 `x-user-id` 只可作明確 dev 替身，不是 production authentication。

| 欄位 | 語意 |
| --- | --- |
| id | client 在首次 create 前產生並保存的 UUID；server 仍強制 owner scope |
| ownerId | server 注入；不可修改 |
| resourceVersion | 固定 2；不支援時 fail closed |
| name | 使用者名稱；建議 trim 後 1–80 Unicode scalar values，TS／Kotlin 共用 fixture |
| schemaVersion | payload.version；目前支援 1–4，必須明示且一致 |
| payload | JSON object，不是雙重編碼字串；保留物件、群組、素材、關聯與所有已支援屬性 |
| assetBindings | payload 中每個素材原始／縮圖 ID 的 source workspace 綁定；無素材時空陣列 |
| revision | server 單調遞增整數，1..9007199254740991；不是 workspaceVersion 或 serverSeq |
| createdAt／updatedAt | server UTC RFC3339 時間；不可用裝置時鐘決定衝突勝負 |
| etag | 與 HTTP ETag 相同的 opaque strong validator，包含雙引號；不可由 client 從 revision 組裝 |

資源示例（合成資料；ETag 只是示例，不是固定演算法）：

```json
{
  "id": "00000000-0000-0000-0000-000000000101",
  "ownerId": "00000000-0000-0000-0000-000000000001",
  "resourceVersion": 2,
  "name": "Planning card",
  "schemaVersion": 4,
  "payload": {
    "format": "boarderless/selection",
    "version": 4,
    "nodes": [{
      "originalId": "template-node-1", "x": 0, "y": 0,
      "width": 240, "height": 120, "rotationDegrees": 0,
      "text": "Plan", "colorToken": "paper", "shapeToken": "rounded",
      "locked": false, "zOffset": 0
    }],
    "groups": [], "media": [], "relations": []
  },
  "assetBindings": [],
  "revision": 1,
  "createdAt": "2026-10-02T00:00:00Z",
  "updatedAt": "2026-10-02T00:00:00Z",
  "etag": "\"fixture-etag-1\""
}
```

新增能力探詢 GET `/api/v1/users/me/quick-scheme-capabilities`，需回傳 resourceVersions、selectionSchemaVersions、limits、materializationEnabled。404 或版本不支援只保留本機；401／403 不當成缺能力或自動建立新帳號。

建議功能限制（待確認，非效能測試結果）：單份 request UTF-8 body 1 MiB、每方案最多 1000 物件／2000 關聯、每使用者 1000 方案、list page 1–100／預設 50。server 在 parse 前限制 request body，回應也限制頁面總 bytes；不能只檢查 JS string.length。未知 required 屬性、未支援 shape／schema 不靜默刪除；client 保留原本機資料並顯示版本不可用。公開能力中的限制必須與 server enforcement 一致。

## 3. CRUD、ETag 與原子性

| 方法／相對 collection 路徑 | 必要條件與結果 |
| --- | --- |
| GET `?cursor=…&limit=…` | 200 items、nextCursor、collectionRevision；revision 與穩定快照 pagination，見下一節 |
| GET `/:id` | 200 resource＋ETag；If-None-Match 命中回 304／無 body |
| PUT `/:id` 建立 | If-None-Match: *、Idempotency-Key；成功 201 resource＋ETag |
| PUT `/:id` 更新 | If-Match: 最新 opaque ETag、Idempotency-Key；完整替換可寫欄位，200 resource＋新 ETag |
| DELETE `/:id` | If-Match、Idempotency-Key；成功 204，刪除事件前移 collectionRevision |

可寫 body 固定 resourceVersion、name、schemaVersion、payload、assetBindings，不接受 ownerId、revision、createdAt、updatedAt、etag。改名也採最新完整資源更新，不能拿過期本機 payload 搭配新 ETag 覆蓋 server 內容。

- 缺少 precondition：428。ETag 不符或 create 已存在：412。不可用素材／語意衝突：409。輸入 schema：422；過大：413；quota：409；auth：401；陌生 owner 的 ID：404。不以任意 error.message 判斷流程。
- server 在同一 DB transaction 中檢查 owner、precondition、schema／binding、quota，保存資源、revision、collectionRevision 與資產 pin 變更。兩台裝置以同一 ETag 更新只能一個成功。
- ETag 是資源所有可寫欄位的 validator，改名、payload、binding 任一變更都必須更新；不允許 weak validator／If-Match: * 取代具體 revision 的更新。
- Idempotency-Key 為 client 生成的 UUID，scope 至 authenticated owner＋method＋resource。保存 request fingerprint 和完成 response／ETag；同 key 相同 body／precondition 的重送回原結果，即使原成功後 ETag 已變。不同 body／target 回 409 idempotency_mismatch。
- 建議 key 保留至少 30 天；capabilities 公布 TTL。超過 TTL 的不確定 create，先 GET 原 UUID／比對原內容，不更換 UUID 重新建立；update／delete 先取最新狀態交由衝突流程，不能盲目重送並覆蓋。
- delete 與其他 device 的未送更新衝突時，不自動重建同 UUID。使用者可明確「另存新方案」；本規格不聲稱有 Soft Delete／Restore API，也不自動刪除本機 legacy 原件。
- `Cache-Control: private, no-store`；Browser CORS 明確 expose ETag、Retry-After，允許 If-Match、If-None-Match、Idempotency-Key。不得用 CORS * 配合 credentials。

固定錯誤碼至少：unsupported_resource_version、unsupported_selection_schema、invalid_payload、invalid_asset_binding、asset_unavailable、asset_access_denied、precondition_required、revision_conflict、idempotency_mismatch、quota_exceeded、payload_too_large、cursor_expired、rate_limited。採現有 ErrorEnvelope，不回傳敏感 payload／signed URL／key。

## 4. 同步、分頁與衝突 UX

- 初始 list 取得 snapshot cursor；後續頁必須屬於同一 owner／snapshot／collectionRevision。server 可採保留快照或 keyset＋有界資料版本；不得在中途更新、刪除後漏資料而 client 還把缺少項目當已刪除。
- cursor opaque、綁定 owner、限制與快照、有期限；過期回 cursor_expired，APP 放棄暫存頁並重新列舉。每頁套用前驗證 owner／resourceVersion／revision；完成所有頁後才以完整 snapshot 替換乾淨遠端 cache。
- `collectionRevision` 包含 create／update／delete。可用 If-None-Match 的 collection validator 做未變更探詢；未完成分頁不能提交同步 checkpoint。增量 change feed 是後續擴充，若交付必須有 tombstone、cursor retention 與全量 reset 契約。
- APP cache／outbox 以 backend origin＋authenticated user 分區；換帳號取消舊請求，遲到 response 不寫入新帳號。local Int ID 只用於本機 legacy lookup。
- pending create／update／delete 本機先耐久保存 UUID、body、base ETag、idempotency key，再送出；restart 可恢復，但不能將 pending 標示 Synced。重試原請求，不重建 ID／key；429 尊重 Retry-After，不 auto retry 412。
- 412 UI 保留自己的 draft、base 與最新 server resource，提供「使用遠端」、「另存副本」、「檢查差異後用最新 ETag 更新」。沒有「timestamp 最新者勝出」或自動 force overwrite。若再次衝突則再次確認。
- 本項是 user-scoped 同步，不把方案 payload、名稱或草稿廣播給 workspace 協作者。可在登入／面板開啟／manual refresh 讀取，不依賴 Priority 2 workspace socket。

## 5. 素材綁定、保存與跨畫布插入

每個 binding 包含 assetId、sourceWorkspaceId、kind（original／thumbnail）；全部是 UUID。原始素材需 ready 且 MIME 與 mediaKind 一致，thumbnail 必須由原始 metadata 證明是該素材 derivative。重複引用只需一份 binding；任意多餘、缺少、跨素材假 thumbnail binding 拒絕。source workspace 不從目前開啟畫布猜測；APP 保存方案時記錄真實來源。

payload／binding 只保存 opaque IDs，不含 binary、storageKey、signed URL、API Key、file／Photos URL。GIPHY external provider 引用需 BAI-010 的獨立合約，不能偽裝為 workspace asset、下載重上傳或產生假 UUID。

### 5.1 保存與保留

- server 以 authenticated user 檢查每一 source workspace 與素材的讀取／複製政策，不只檢查 uploader ownerId。member read 權是否允許建立可複用方案，須由產品與後端明確確定；未定前不開啟跨畫布素材 materialization。
- 建立／更新成功的方案對引用的原始與 derivative 建立 server-side retention pins，防止 object storage GC 誤刪。pin 只是保留，不是永久授權；權限撤銷、workspace 刪除或政策不再允許時不可繼續下載／複製。
- 方案刪除／移除 binding 只移除自己的 pin，不直接 DELETE 素材，也不影響其他方案或已插入畫布。GC 只在全部有效引用／pin 消失且滿足保留政策後處理，與 DB transaction／worker failure 有可恢復順序。
- list／GET 可回 unavailable binding 的固定狀態供 UI 呈現；不交付素材 URL／秘密、不把不可用方案當文字-only 成功方案、不自動刪除引用。

### 5.2 materialization（建議必需新增能力）

POST `/:id/materializations` 使用 If-Match 方案 ETag、Idempotency-Key；body 為 destinationWorkspaceId。建立時再次確認方案 owner、全部 source read／copy policy、destination write 權及素材 ready；固定凍結方案 revision，不拿更新後的 payload 混用舊 mapping。

- 回 201 ready 或 202 pending，含 materializationId、schemeId、schemeRevision、destinationWorkspaceId、expiresAt、固定 status。GET `/:id/materializations/:materializationId` 綁定相同 owner，ready 提供完整 asset ID mapping（含衍生縮圖），不能回任意外站 content URL。
- 同工作區可驗證後 identity-map；跨工作區由 server 建立 destination-scoped metadata／blob reference 或 server-side copy，保留 checksum、MIME、尺寸、duration 與 derivative 關係。禁止 client 先從任意 URL 下載再上傳繞過權限。
- 只有所有原始／必要 derivative ready 才交付 mapping；任何失敗不部分插入。pending／失敗／取消 materialization 不建立畫布 Node，也不進 Workspace Undo。
- APP 驗證 mapping 完整、無多餘／重複／錯誤 kind、scope／scheme revision 一致，再替換 asset／thumbnail ID，產生新的物件 ID 並重新映射 parent／relation。以**單一**既有 CreateObjects transaction 插入；不能把新的 asset ID 當新 object ID。
- 正式 operation service 必須在提交時再次驗證 destination write、每個 asset scope／ready／使用權；materialization 成功不等於 operation 永遠有權限。過期或中途撤權拒絕全部 transaction，不能留下半個群組／懸空關聯。
- 未使用 mapping 所建資產暫以 materialization retention 保留，過期後由安全 GC 清理；取消不刪除已被 committed Node 引用的素材。Undo 只撤回插入物件／關聯，不直接刪 blob；Redo 仍需合法素材引用。

本節涉及素材 metadata／ACL／GC 擴充，不能僅靠新增 Quick Scheme CRUD 宣稱「包含素材的方案已完整跨裝置／跨畫布可用」。確切 worker、poll interval、TTL 與 failure code 待後端確認。

## 6. 本機 migration（不得丟失既有方案）

1. 使用者確認登入帳號與遷移範圍；舊 Settings 是裝置共用資料，不能在每次換帳號時自動匯入到新 owner。
2. 讀原始 JSON，保留原件與本機 Int ID；使用既有 decoder 讀 v1–v4。JSON 未明示 version 時依保存 metadata；legacy metadata 1＋明示新版本允許。未支援／非法資料標示需修復，原件不刪。
3. 新 resource envelope 明示實際 selection schemaVersion；read adaptation 不代表已將所有 selection 升級 v4。要升級需有明確、無損 transform 與 fixtures；現有字串改 JSON object 時保留所有已支援屬性。
4. migration journal 在網路前保存 backend origin、owner、legacy ID、remote UUID、原 payload fingerprint、request body、idempotency key、state。再次啟動／重送沿用 journal，不以裝置 Int ID 對遠端 deduplicate。
5. 舊素材缺 sourceWorkspaceId，不從目前 session 補值。只能由既有可信來源／經授權 server resolver 確認；找不到則保留 Local only／待使用者選來源，不靜默上傳不可用方案。
6. remote 成功後保存 UUID／ETag／revision 與對應關係才標示 Synced。timeout 不當成失敗重新 create；先查原 UUID，身分／原內容不吻合進衝突流程。
7. 不自動刪除 legacy 原件。後續選擇移除本機舊備份需獨立確認，並有可讀遠端與 migration evidence；本文未授權批量刪除。

## 7. 共同 fixtures 與交付門檻

後端需交付正式 JSON Schema／TypeBox、migration SQL、OpenAPI、共用 JSON fixtures、auth／CORS／limits 與測試部署；APP 再實作 scoped repository、durable outbox、sync state／衝突 UI、migration journal 及素材 mapping。本文 JSON 可解析不等於 schema 或 runtime 驗證。

功能驗收最低包含：

- v2 resource envelope 包含 selection v1／v2／v4 round-trip，不丟群組、shape、lock、rotation、zOffset、媒體／thumbnail、intent／label；unsupported resource／selection 拒絕且保留本機。
- 兩裝置 CRUD／改名；相同 ETag 競爭只有一個成功；draft 保留、另存副本、二次衝突、delete vs update 不自動復活。
- create／update／delete 成功但 response 遺失、app crash、restart、same-key retry、key mismatch、TTL 過期；不能重複建立或丟失 pending draft。
- list 途中新增／改名／刪除、cursor 過期、304、owner 切換、晚到 response、quota／UTF-8 Unicode 邊界與 CORS ETag 可讀。
- pure-media／混合方案從裝置 A 保存、B 讀取、同畫布插入；跨畫布 materialization 必須全部 ready 後單一 transaction 插入，parent／relation 新 ID 正確。
- source／destination read-write ACL、撤權／刪 workspace、假 asset／thumbnail、GC pin、worker 失敗／重試、mapping 過期、提交前撤權、不完整 mapping 皆不洩漏或部分插入。
- legacy 重啟 migration 不重複；帳號切換不誤匯入；未知來源素材與不支援資料原件仍保留。
- 真正兩裝置／正式 object storage／後端資料與 operation log 證據；MockEngine 與本機單元 tests 不代替整合驗收。視覺與遮擋交 QA Workbench 簽核；效能最後。

## 8. 待後端／產品確認的決策

路由與 v2 envelope 命名、正式 auth、Unicode 計數與 limits、ETag／idempotency 保存、snapshot cursor 策略、素材 read 與 copy 政策、retention／workspace 刪除政策、materialization 與 operation ACL／GC 的原子邊界。未確認前 APP 保留本機方案，不能寫入猜測的 user API 或宣稱跨裝置已完成。
