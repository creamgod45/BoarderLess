# Asset preparation v2：固定身份與未知回應恢復

狀態：2026-10-11 新交付需求，後端尚未實作；不得將以下路由當成現存 API。不修改既有 Media v1。

## 現存缺口與範圍

唯讀核對 `backend/src/services/asset.service.ts`：prepare 每次呼叫 `randomUUID()`，沒有 request identity／冪等記錄。`backend/src/schemas/asset.schema.ts` 的 create body 使用 additionalProperties=false，未接受 requestId；現存 routes 未提供 upload-ticket renew。complete 可冪等，不能彌補 prepare 回應遺失時 APP 還不知道 asset ID 的情形。

本規格用於本機原素材跨裝置同步及明確恢復，不允許 APP 對 v1 重複 POST 猜測同一資產，也不以 checksum 查到同內容就冒認本人的原請求。素材完成不會自動新增 Node；canvas 交易仍走獨立審閱、版本、receipt／fence。

## Prepare 固定身份

新增 `POST /api/v1/workspaces/:workspaceId/assets/preparations`，body：

```json
{
  "schema": "boarderless.asset-prepare.v1",
  "requestId": "00112233-4455-6677-8899-aabbccddeeff",
  "metadata": {
    "byteSize": 123,
    "checksum": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
    "durationMs": null,
    "height": 24,
    "mediaType": "image/png",
    "width": 32
  }
}
```

requestId 是 APP 先 durable 保存的 lowercase UUID；認證 user 來自既有 authentication，不信 body user／owner。metadata 採 Media v1 六種 MIME、1..200MiB、SHA-256 與有限正尺寸／非負 duration（≤JSON safe integer 9007199254740991）規則；未知欄位、缺欄位、非法 token 拒絕。null 表示尚未提供尺寸／duration，不補別人的值。

requestDigest 為 metadata 的 compact UTF-8 JSON（欄位固定按 byteSize, checksum, durationMs, height, mediaType, width；所有 null 明確保留，checksum lowercase、MIME canonical）之 SHA-256 lowercase hex，無 sha256: 前綴。server 自行計算，不信任 client 提供的 digest。APP 的 PreparedAssetUploadRequest 使用同序列化規則；local ID／檔案路徑／owner／review／原檔 bytes 不進這份 body。

跨端 golden vector：byteSize=3、checksum=sha256:加64個小寫b、durationMs=null、height=1、mediaType=image/png、width=1，依上述序列化的digest為`32d5f0d51e3a30dc0c7057664dc28914132cf09fe6e109098725c05d26f602ee`，已用獨立Ruby JSON.generate＋Digest::SHA256核對並加入APP測試；不要把上方byteSize=123示例誤用成此向量。

唯一鍵為 authenticated user＋workspace＋requestId。原子保存固定 asset UUID、request digest／metadata、prepare receipt 與 pending asset；多 worker 同請求競爭只建立一份。首次 201，相同重試 200，回同 identity；同 key 不同 metadata 回 409 preparation_mismatch，不另建資產。server生成獨立UUID，不借用local ID。若與APP local UUID碰撞，APP仍保存已知receipt identity但停止graph projection，重新規劃需產品確認，不能自動換requestId或再prepare。

回覆 envelope：schema `boarderless.asset-preparation.v1`、requestId、requestDigest、asset（沿 Media v1 metadata）、phase，以及可選 upload directive。phase 至少 prepared／completionAccepted／ready／rejected／missing／abandoned。directive 仍只在當次記憶體，method=PUT／headers／expiresAt／leaseId；receipt 或 log 不保存 signed URL／headers。ready 重試不提供可覆寫的 PUT。worker accepted 的 Pending 與未完成 prepare 必須由 phase 區分。

## 原 prepare 查詢與 retention

新增 `GET .../assets/preparations/:requestId`，只能查自己這個 workspace 的 request；每次重新授權。同 identity 回 200 envelope，真 absent 回 404 preparation_not_found；不可洩漏他人的 request。404 不證明仍在途的原請求永遠不會 commit，因此只可在此 v2 冪等契約下明確重送同 requestId／body，不生成新 requestId。

receipt retention 必須覆蓋 APP 的 offline retry，不能 TTL 消失後允許舊 requestId 重用成另一 asset。abandoned／deleted／missing 保留 tombstone identity；相同請求不能復活或重建。若只能有限 retention，必須提供明確 terminal fence／過期拒絕規則，不能 silent eviction。回應遺失、DB restart、presign 失敗均須可依 requestId 查既有 identity；未簽到票券不刪掉 reservation。

## 票券 lease 與 complete

新增 `POST .../assets/preparations/:requestId/upload-tickets`，body 包含先 durable 保存的 ticketRequestId UUID。fresh upload 權限且仍 prepared 才簽發；同 ticketRequestId 冪等返回同 lease identity，不因未知 ACK 增加 lease。票券過期需另一明確批准、另一先保存的 ticketRequestId，不自動 retry／renew。完成已 accepted 或 ready 不可再次取得 PUT。

新 lease 不得讓仍有效舊 PUT 改寫 accepted／ready 原檔。server 應固定租約 generation／staging key，完整 body 與 worker 只接受對應 lease，過期 staging 不可變成當前 completed original。若要換租約必須明確處理並行 upload／complete；不能只覆寫同 key。可重新完整 PUT，不要求 HTTP Range；APP 不猜測續傳。

新增或明確擴充 preparation complete endpoint，body：requestId 對應 path、leaseId、byteSize、checksum；重新檢查 ACL，原子 accepted＋durable verify job。同 lease／同 metadata 重複回當前 phase，不重建工作；wrong lease／不同 checksum 回 409。舊 v1 complete 保持原相容行為，但不能藉此繞過 v2 lease。ready original 再簽 GET 並校验 hash；縮圖是獨立 server identity，需正式 metadata／ACL，不取代原檔。

## APP 保存與批准順序

1. 審閱來源 local 原檔、目的 binding、大小及可能網路費用。
2. durable 保存 PreparedAssetUploadRequest 固定 requestId＋scope＋原 metadata；保存未知不准 HTTP。
3. 使用者另批准 prepare；僅在 backend 宣告／fixture 證明 v2 capability 時可用，不偷偷塞 requestId 到 v1 body。
4. 回覆未知時停住；明確查原 receipt／同身份 retry。原 asset identity 保存未知亦不准 PUT，確認同 generation 後才走票券。
5. 另批准 upload／complete；未知 complete 先查原 phase，不 abandon、不重新 prepare。
6. ready original 及 checksum／size／fresh ACL驗證後，明確新審閱把 local ID 投影為 server ID；canonical canvas request 與本機 Undo分開保存。素材準備不改 Node／baseline。

APP 已新增 request／identity 模型及 deterministic wire／digest；durable reservation、v2 HTTP adapter、ticket lease、上傳串流、映射發布與產品入口仍待接。不把 identity receipt 当 Ready／授權證明。

## 交付 fixtures／測試

需提供 request／response fixtures：首次201、同 key200、改 metadata409、receipt200／404／403、expired lease、lease retry、accepted pending／ready／rejected／missing／abandoned、wrong workspace／actor、wrong lease complete、ready禁止PUT。所有 primitive metadata 保持完整 null／lowercase／canonical digest。

多 core／worker 競爭、prepare durable commit後丟回覆、presign失敗後查原identity、DB重開、舊請求延遲抵達、receipt tombstone保留、complete／renew競爭、有效舊PUT不能改ready、撤權与source內容變更需有整合測試。APP最後以真兩端：offline local import→明確prepare/upload→ready驗證→審閱canvas→receipt／fence→重開Undo／Redo；visual／遮擋／VoiceOver由使用者簽核，效能最後。

## 後端交付補充：declared／verified（ASSET-08 定案，2026-10-11）

後端已實作本規格（`backend/`，fixtures：`backend/tests/fixtures/contract/asset-preparation-v2/`）。envelope 新增：

- `declared`：prepare 的 canonical metadata，等於 requestDigest 涵蓋的內容，永不改寫。APP 以此綁定 receipt identity。
- `verified`：worker 實際解析值 `{width, height, durationMs}`；尚未解析或解析失敗為 null。因不符被 rejected 時仍保存，供診斷。
- `asset`：沿用 Media v1 metadata；ready 後寬高／duration 為 verified 值，因此不能拿來和 declared 比對 identity。

比對規則：byteSize／checksum 完全相符；寬高為顯示尺寸（已套用 JPEG／PNG／WebP EXIF orientation 與影片 display matrix rotation），declared 非 null 時必須完全相同；duration 容差 `abs(declared − verified) ≤ max(100ms, verified × 1%)`（產品容差，不是格式標準）；declared 宣告 duration 但內容沒有 duration 視為不符。declared 為 null 不比較，但格式、解碼與大小限制照常驗證，必要資訊解析失敗不會 ready。不符時 phase=rejected，`asset.rejectionReason=declared_metadata_mismatch`。

## 後端交付補充：MediaNode 引用（ASSET-07 定案，2026-10-11）

新建 MediaNode 與替換素材（`update_object` 改 `assetId`／`thumbnailAssetId`）必須引用同 workspace、具權限且 Ready 的素材，否則 422 `asset_not_ready`（details 列出 assetId 與 status）。`restore_objects` 還原原本的引用不要求 Ready；素材已 missing／rejected 時由 APP 顯示不可用狀態，還原不是重新授權，也不讓素材復活。相容性影響見 `backend/ACCEPTANCE.md`。
