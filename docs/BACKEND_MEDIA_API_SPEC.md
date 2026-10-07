# 素材系統後端 API 規格書

版本：Media v1／2026-10-02。狀態：APP 相容合約與後端待實作需求，不代表後端已交付。

本文件以目前 APP 的 `BackendAssetTransferGateway`、`BackendContract`、`BackendWorkspaceRepository` 與素材匯入／下載協調器為準，細化 [BAI-003](BACKEND_APP_INTEGRATION_ISSUES.md)。架構沿用 [後端架構](BACKEND_ARCHITECTURE.md)，驗收沿用 [產品 QA 品質檢驗 SOP](產品%20QA%20品質檢驗%20SOP.md) 5.23、5.24。後端由另一位 agent 負責；本次只更新文件，沒有修改或啟動後端。

## 1. 範圍與現況

第一階段完成圖片、GIF、影片的原檔上傳、驗證、授權下載、靜態縮圖與素材庫重用。GIF Browser 排在素材生命週期完成後；效能壓測安排在功能整合與產品 QA 最後階段。

初次整理規格時，後端只有 pending metadata、list、GET、soft-delete，建立固定回 `uploadUrl: null`。2026-10-02 後續唯讀檢查發現另一 agent 已加入 prepare／complete／content／pending-only abandon routes 與對應 schema；這只證明原始碼出現相容介面，尚未驗證服務啟動、migration、storage、worker 或衍生素材的端到端交付。APP 已有 signed PUT／GET、完成確認、狀態輪詢、SHA-256 驗證及工作區素材庫；mock 測試通過不等於真實 storage 已驗收。

## 2. 固定相容邊界

- API prefix：`/api/v1`。下列表格路徑省略 prefix，`{w}`、`{id}` 為 Workspace／asset UUID。
- 現行開發 API 身分：`x-user-id`。這不是正式 authentication；正式 token／session 改版須同步 APP，不能直接以此 header 部署公開服務。
- Signed PUT／GET 只帶 directive 的必要 headers；APP 不把 API 身分 header 轉送 object storage。
- MIME 精確 allowlist：`image/png`、`image/jpeg`、`image/webp`、`image/gif`、`video/mp4`、`video/webm`。
- 原檔大小：`1..209715200` bytes（200 MiB）。checksum 使用原始二進位內容的 `sha256:` 加 64 個小寫十六進位字元，不能用 object storage ETag 代替。
- 公開 status 只可為 `pending`、`ready`、`rejected`、`missing`。`processing`／`uploading` 可作伺服器內部 phase，不能直接成為現行 APP 的 status。
- 準備與完成回傳的 id、workspaceId、mediaType、byteSize、checksum 必須與準備票券一致；不可悄悄把原檔換成轉碼檔。尺寸與 duration 可由伺服器驗證後校正。
- 簽名 URL 不進 Node、operation、history、clipboard、Quick Scheme 或永久 Workspace projection。

## 3. Asset metadata

| 欄位 | 型別／規則 |
| --- | --- |
| id、workspaceId、ownerId | 必填非空字串；服務端使用 UUID，ownerId 由身分決定 |
| storageKey | 必填非空字串，現行 APP parser 仍需要；當作 opaque handle，不作下載地址，不含 credential |
| mediaType | 必填 allowlist MIME |
| byteSize | 必填正整數，原檔不得超過 200 MiB |
| checksum | 必填，原檔 SHA-256；衍生檔有自己的雜湊 |
| status | 必填，四種公開 status 之一 |
| createdAt | 必填非空 ISO-8601 時間字串，建議 UTC |
| width、height | 可省略或 null；有值必須為正整數 |
| durationMs | 可省略或 null；有值不得負數，影片 ready 時應有有效正值 |
| thumbnailAssetId | 可省略或 null；有值不得空白，且為同 Workspace 的另一個 asset |
| rejectionReason | 可省略或 null；建議穩定機器碼，不含私密 URL／內部路徑 |

APP 忽略未知欄位，可擴充 `processingPhase`、`updatedAt`、`filename`，但目前不會使用它們。素材庫現會將 rejectionReason 的已知機器碼轉為本地化提示：`upload_missing`、`byte_size_mismatch`、`checksum_mismatch`、`media_type_mismatch`、`unsupported_format`、`undecodable_media`、`dimensions_exceeded`、`invalid_duration`、`duration_exceeded`。只有 rejected 卡片顯示原因；未知／空白機器碼使用一般訊息，不直接顯示任意 server 文字；null 可相容舊版。新增其他欄位 UI 仍需另排 APP 對接。

以下 metadata 範例只展示格式；測試 fixture 必須以真實 binary 計算大小與雜湊，不能直接拿此範例作完整性驗收。

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "workspaceId": "22222222-2222-4222-8222-222222222222",
  "ownerId": "33333333-3333-4333-8333-333333333333",
  "storageKey": "workspaces/22222222-2222-4222-8222-222222222222/assets/11111111-1111-4111-8111-111111111111.png",
  "mediaType": "image/png",
  "byteSize": 1024,
  "checksum": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "status": "pending",
  "createdAt": "2026-10-02T00:00:00Z",
  "width": 100,
  "height": 100
}
```

## 4. API 合約

| API | Request | Response |
| --- | --- | --- |
| POST `/workspaces/{w}/assets` | `{mediaType, byteSize, checksum, width?, height?, durationMs?}` | 201 `{asset, upload}`；asset.status=pending |
| PUT `upload.url` | 原始 binary，不是 multipart | 2xx；不等同 ready |
| POST `/workspaces/{w}/assets/{id}/complete` | `{byteSize, checksum}` | 202 `{asset}` pending 或 200 `{asset}` ready；必須有 JSON body |
| GET `/workspaces/{w}/assets/{id}` | 無 | 200 **裸 Asset metadata**，不可包成 `{asset}` |
| GET `/workspaces/{w}/assets` | 無 | 200 `{assets: [...]}` |
| GET `/workspaces/{w}/assets/{id}/content` | 無 | 200 `{asset, download}`，只能對 ready 素材簽發 |
| GET `download.url` | 無 | 200 原始 binary；長度、MIME、雜湊對應回傳 asset |
| DELETE `/workspaces/{w}/assets/{id}` | 無 | 建議改成下節 pending-only abandon；成功 204 |

Envelope 欄位名稱是固定合約，不能只回下載 URL，也不能對 complete 回 204。JSON request 可省略 optional null 欄位。

### 4.1 Transfer directive

```json
{
  "method": "PUT",
  "url": "https://storage.example.invalid/signed-upload",
  "headers": { "Content-Type": "image/png" },
  "expiresAt": "2026-10-02T00:15:00Z"
}
```

準備回應為 `{ "asset": <第 3 節 metadata>, "upload": <上述 directive> }`。尖括號為文件占位，不是可直接送出的 JSON。

APP 目前只支援 upload method `PUT`，不支援 POST multipart。舊版非空 `uploadUrl` 可相容為沒有必要 headers 的 PUT；新實作應使用 `upload`。不要同時回互相矛盾的兩個地址。

下載回應為 `{ "asset": <ready metadata>, "download": <directive> }`，directive.method 必須是 `GET`。簽名需要的 Content-Type、provider checksum 或其他 headers 必須完整提供；APP 不猜測。expiresAt 為 optional 字串，建議一定提供；目前 APP 不會自動刷新過期票券或續傳。

### 4.2 完成與輪詢

complete 由 DB transaction 原子記錄「已接受完成」與 durable job／outbox 後才回 202；不能只放進 process 記憶體 queue 就承諾成功。相同 id、size、checksum 的重送應返回同一資產目前狀態，不重複驗證工作或衍生檔；不同值回 409。

worker 驗證 object 存在、實際 byte count、SHA-256、sniffed MIME、可解碼格式、尺寸及影片 duration，通過後才 ready。宣告的檔名／副檔名／Content-Type 不是驗證依據。不合法資料變 rejected；已 ready 但正式內容遺失時可變 missing，不可簽發假下載票券。

APP 預設每 500 ms GET 一次，最多 120 次讀取，約 59.5 秒等待加網路時間。超時不是伺服器失敗證據，也不得因此刪素材。較慢工作可繼續處理，使用者之後到素材庫重新讀取。第一階段不要求 WS；日後可由正式 metadata-change 通知觸發 refresh。

### 4.3 縮圖／影片 poster

GIF 保留動畫原檔；GIF 與影片產生靜態 PNG／JPEG／WebP 縮圖。縮圖是另一筆 ready asset：有自己的 ID、storageKey、MIME、size、checksum、尺寸與相同 Workspace ACL。原檔 metadata 用 thumbnailAssetId 指向它。

APP 的實際流程是 GET `/assets/{thumbnailAssetId}`，再 GET `/assets/{thumbnailAssetId}/content`；不是 `/assets/{id}/thumbnail/content`。額外提供後者可以，但不能取代通用 metadata／content endpoint。單獨 poster URL 或把 thumbnailAssetId 指回影片自己都不符合現行需求。

縮圖應明顯小於 APP 靜態預覽上限：encoded 32 MiB、decoded 12,000,000 pixels。原檔可以 ready 但縮圖稍後完成；APP 沒有縮圖時顯示 placeholder，不讀整支影片當卡片預覽。縮圖 job 應能重試，不把成功原檔誤判成 rejected。

轉碼若另做 rendition，必須使用另一筆 asset／另版合約，不能讓原 asset 的下載內容與原始 hash 不一致。

### 4.4 列表與重用

目前 APP 不送 cursor，素材庫分頁是本機 UI 分頁。v1 列表須返回授權範圍的完整 assets；若需真正 server pagination，先改 APP，不可靜默只回第一頁。衍生素材可包含在列表；要隱藏或分類衍生素材需另加 client 使用的合約。

重用 ready 素材前 APP 重新 GET metadata，再經既有 create-object operation 插入 MediaNode；不重新上傳。不以 GET 的閱讀權限當作編輯權限，後端仍須在 operation 提交時檢查角色及 asset scope。

## 5. 取消、競爭與刪除邊界

**待後端確認的語意變更：現有 DELETE 改成原子 pending-only abandon。** APP 目前的素材 DELETE 只用於完成請求前的失敗清理，不用於使用者刪除 ready 素材。現在後端的 unconditional soft-delete 不安全。

- 只有仍為 uploading／未接受 complete 的 pending 資產可 abandon；同一 transaction 與 complete 比較並鎖定狀態。
- abandon 先成功：complete 不得再接受，回明確錯誤；complete 先接受：abandon 回 409，不刪 processing／ready／rejected／missing 資產。
- 已 abandon 的相同清理可冪等 204，但仍需身分與 scope 檢查。storage 刪除可用 durable cleanup job，不以不可靠跨系統 transaction 宣稱原子刪檔。
- 沿用 assets.delete 的 owner／editor 權限；不因清理用途讓 viewer／commenter 取得刪除權。
- 使用者真正刪 ready 素材須另定顯式合約、Node 引用與保留／復原政策，不能混入 abort cleanup。刪除 Node 不等於刪除原檔。

若必須保留原 DELETE 的廣義 soft-delete 語意，請提供獨立 pending-only abandon endpoint，並協調 APP 改路徑後才接入；不能讓現有清理直接呼叫不安全 DELETE。

APP 在 complete 開始後遇到取消、503、回應遺失或 timeout，保留 asset ID 並提示從素材庫確認，不 DELETE，不自動再 upload／complete。APP 現已在完成請求前將 asset ID 寫入本機、按 user／Workspace 隔離的提醒清單；一般重開可回查，移除提醒不刪除伺服器素材。這是手動 reconcile index，不是上傳續傳或自動重試 journal，也不保證突然斷電時的 fsync durability。完成前只對符合 source／scope 的 pending row best-effort 清理，截止約 5 秒；不能保證 native IO 被強制終止。

GC 必須區分「未完成上傳」與「已接受 processing」：前者可依 expiry 清理；後者依工作租約、重試／dead-letter policy 處理，不可單憑 createdAt 把慢 worker 的素材清掉。準備回應遺失造成 APP 不知道 ID 的孤兒由服務端 GC 收拾。

## 6. 儲存與多核心工作架構

沿用 modular monolith：API gateway 處理授權與 ticket；PostgreSQL 保存 asset 生命週期；private object storage 保存 binary；獨立 background-worker process 執行檔案驗證、poster、cleanup。

DB 除既有欄位，增加內部 upload／completion phase、completionAcceptedAt、uploadExpiresAt、verifiedAt、衍生來源／版本、失敗原因、job lease／retry 所需紀錄。thumbnailAssetId 要有同 Workspace 關聯檢查。具體 migration 命名由後端定，但不可只用公開 pending 判斷是否可清理。

- complete transaction 同時寫 durable job／outbox；dispatcher 可重啟補送。
- worker 至少一次執行，使用 asset ID＋derivative type＋version 的 unique key 去重，CAS／lease 防止多核心同時發布不同結果。
- CPU 型 JS 工作用固定大小 worker_threads pool；FFmpeg 等外部工作用獨立 process/container，限制同時工作數與記憶體，不阻塞 API event loop。第一階段不做負載壓測。
- 原檔完成後不可被仍有效的 upload ticket 覆寫：採不可變 object version／promotion 到 immutable key，或等價防覆寫設計；後續讀取指向已驗證版本。
- 部分衍生檔失敗要可回收／重試，發布 ready metadata 與 thumbnail 關聯須一致；不要留下 ID 指向不存在內容。
- private bucket 禁止永久公開 URL；storage provider／queue 選型保持可替換，不要求本次另拆微服務。

## 7. 授權、安全與錯誤

prepare／complete：owner、editor；list／metadata／content：具 workspace.read 的成員；abandon：具 assets.delete 的成員。每個 API 重新檢查 Workspace 與 asset scope，縮圖不得繞過原 Workspace ACL。Node mutation 亦須驗證引用資產。

API 與 storage 分別配置允許的 Web origin、PUT／GET／OPTIONS 與必要 headers；API 的 x-user-id CORS 允許不等於 storage 要接收它。下載實作須在各平台可讀取完整 binary；目前沒有 Range／resumable 合約。

Signed URL 短效、限定 key／method／必要 headers。撤銷權限後立即停止簽發新票券，但已簽出的 storage URL 未必能即刻撤銷；須記錄最大 TTL 與實際 provider 行為，不能宣稱瞬間全面撤銷。URL／token／credential 不進 log。

建議錯誤分類沿用後端統一 error envelope：400 invalid metadata、403 權限不足、404 不存在／不可見、409 lifecycle conflict／not-ready、413 超量、415 不支援格式、503 暫時不可用。回應不得洩漏 storage 內部路徑／簽名。語法與 machine code 由後端統一交付 fixture；APP 現在主要依 HTTP status 顯示一般錯誤。

## 8. 後端交付與 QA 清單

1. Storage adapter、prepare signed PUT、必要 headers／CORS 可用。
2. 完成 API durable acceptance、worker 驗證、ready／rejected／missing 查詢可用。
3. `{asset, download}` signed GET，原檔 size／hash 一致；縮圖獨立 metadata／content 可用。
4. pending-only abandon 合約確認、原子 complete 競爭及 GC policy 可驗證。
5. 提供六種 MIME 真實 binary fixture、對應 SHA／尺寸／duration，以及完整 request／response fixtures（pending、ready、rejected、missing、縮圖、403、409）。
6. 自動整合測試：截斷／超量／偽 MIME／錯 checksum；worker 重啟、重複 complete、不同 checksum conflict、多 worker 重複 job；complete durable commit 後故意丟失 HTTP 回應；abandon／complete 兩種競爭順序；GC 不刪 accepted processing；跨 Workspace／角色撤銷／縮圖 ACL；有效 upload URL 無法改寫 ready 內容。
7. APP 真實端到端：upload → complete → ready → Node → 重開下載 → 素材庫再次插入；GIF 動畫、影片播放、poster、取消／遺失回應後復查。後端關閉時標 Pending，不標 Pass。
8. 使用者依 QA SOP／QA form 簽核視覺、遮擋、手機互動與音訊等人工項目，保存截圖及 HTML／PDF 驗收紀錄；效能測試最後進行。

## 9. 尚未支援、不可假定已完成

正式 authentication、server pagination、續傳／URL 自動更新、跨重啟自動恢復／重試、素材真正刪除／restore、跨 Workspace 複製或 Quick Scheme 資產重綁、轉碼 rendition、GIF Browser 都需另排合約或 APP 工作。多人 WebSocket、Quick Scheme 使用者 API、canvasStyle、AI gateway 仍依 BAI-009／006／005／004，不由此素材規格取代。
