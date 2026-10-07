# GIPHY API 接入規格

## 2026-10-05 下一頁定位修正

成功的手動下一頁請求仍依官方 pagination offset 追加完整結果，保留供應商排序與重複 ID；網格現在定位至第一個新增結果，避免停在舊頁看起來無效。新搜尋（即使結果數相同）回到頂部；空追加頁、失敗與取消不觸發定位。不預抓、不自動重試、不增加搜尋請求，不改 key／預算／保存政策。官方語意見 [Pagination Object](https://developers.giphy.com/docs/api/#pagination-object)。

MockEngine／純狀態測試驗證 offset 0→2、重疊結果、尾頁、新搜尋與空頁；不代表真實 GIPHY key／服務或 Compose 捲動操作已驗。若使用者症狀是按鈕停用或 API 錯誤，仍需依畫面訊息追查，不能把定位問題當成所有分頁失敗的定因。

核對日期：2026-10-02。這是 APP 查詢原型與後續外部引用對接規格，不代表完整 GIF 插入、正式品牌審核或實際金鑰驗證已完成。

## 官方限制與修正

依 [官方 Best Practices](https://developers.giphy.com/docs/api/#prohibited-integration-practices)，一般整合由 client 直連 API 及媒體 URL；不得自行代理、改寫 URL、快取／另存媒體或混合其他供應商結果。例外媒體快取需要事先書面批准及指定重驗證。先前「後端代理並下載至工作區素材庫」方案撤回；現有 MediaNode／asset 原檔匯入不可直接套用 GIPHY。

記憶體不天然豁免：目前畫面必需的顯示／解碼資源保留與可跨畫面重用的 LRU／磁碟快取分開設計。官方文件未明確提供記憶體快取例外，跨畫面重用需另確認批准範圍；不得宣稱只要不落盤就一定合規。強制重新下載也不是目標：當前已開啟預覽不因重排版、平移或縮放再請求。

## 查詢介面

API base：`https://api.giphy.com`，全部 GET；client 以 `api_key` query parameter 驗證。[Endpoints](https://developers.giphy.com/docs/api/endpoint/)

| 功能 | 路徑 | 主要參數 |
| --- | --- | --- |
| 熱門 | `/v1/gifs/trending` | api_key、limit、offset、rating；offset 最大 499 |
| 搜尋 | `/v1/gifs/search` | 同上及 q、lang；q 最多 50 字元，offset 最大 4999 |
| 單筆重新取得 | `/v1/gifs/{id}` | api_key、rating；供未來外部引用重新開啟 |
| 多筆重新取得 | `/v1/gifs` | api_key、ids，最多 100 ID；供未來可見引用批次解析 |

APP 已提供 `GiphyClient.resolve(ids)` 批次直連：1–100 ID、rating=g、共用搜尋的限額與 429 gate；缺失 ID 回傳缺失，不用舊 metadata 填補，也不刪除畫布物件。拒絕包含逗號／空白／控制字元的 ID、陌生或重複回應 ID；不假設批次回應順序等於請求順序，不要求不存在的 pagination 欄位。

回應採 `data`、`pagination`、`meta`；搜尋／熱門 data 為 array，單筆為 object。Rendition 尺寸是字串、格式可能缺漏；`pagination.total_count` 非每個 endpoint 都有，不能強制要求。[Schema](https://developers.giphy.com/docs/api/schema/)

Beta key 每小時 100 API 呼叫，平台各自用 key；production／enterprise 需申請，以 Dashboard 實際方案為準。[Quickstart](https://developers.giphy.com/docs/api/quick-start-guide/)

## 已實作的 APP 查詢原型

- 入口：物件庫 → GIPHY 瀏覽器（預覽），不混入內建元件或 Workspace 素材。
- 結果為固定高度的兩欄 LazyVerticalGrid 縮圖網格；維持 API 順序、包含重複結果，不因媒體缺失移除卡片。使用 index＋provider ID 的位置 key 避免重疊頁 ID 撞 key；縮圖顯示 fixed_width_still／fixed_height_still，無符合 URL／大小的 rendition 時保留可選擇 metadata 卡片，不回退 original。
- 縮圖與選取預覽共用程序內最多 3 個 in-flight 載入／解碼 permit，不是內容快取。只在卡片經 clip 後可見才載入；移出視窗或離開 composable 會取消並釋放顯示 Bitmap，重新進入會重新請求，不宣稱未批准的跨畫面快取。取消排隊不開始 download；GIF loader 在 permit 外 finally 關閉 client，避免排隊取消漏清理，resource check 在 ownership transfer 前完成。
- 明確按搜尋／熱門才請求；typing 不請求，不預抓下一頁。已顯示的同一查詢不重複送出；新查詢取消舊 job，generation 與 cancellation gate 防止晚到結果覆寫。
- 每頁 20、rating=g，搜尋原詞保留且只由 HTTP client 編碼一次。目前 UI 固定 lang=zh-TW；完整語言偏好映射待補。結果保持供應商順序，不重排、不以去重等方式修改結果。
- 程序內按 key 的 rolling-hour gate 最多 100 次，失败请求也计入；429 遵守秒數 Retry-After，缺漏時保守暫停一小時。沒有自動 retry／polling，重啟及其他裝置的使用不在本機計數內，不能顯示其為帳號真實餘額。
- query／offset 不合法時在發送前拒絕；401／403、429、無網路、malformed response 分類，錯誤不帶 key／完整 request URL。取消不轉為一般錯誤。
- Desktop 從 `BOARDERLESS_GIPHY_API_KEY` environment 載入，或在面板輸入僅本次使用的 masked key；不讀取 backend `.env`、不提交金鑰、不將 key 傳給我們的 API。按官方 client-direct 模式，key 在 client 请求中可見，不是可保密的伺服器權限憑證。
- Desktop／Android／iOS／Web JS、Wasm 僅在明確選擇結果後載入 `fixed_width_still`，直接連原 HTTPS GIPHY media URL，保留 query；不跟隨 redirect、不寫檔、不走通用素材快取。共用 `GiphyMediaClient` 的應用程式 retained encoded 上限 2MiB，檢查 Content-Length／image MIME（若有）、未知長度逐 chunk 限制、空 body／取消／狀態碼；不添加 Workspace token 或 API key 到媒體請求。不能把此限制宣稱為底層 HTTP engine 或 OS 的總記憶體上限。
- 四平台使用既有安全 decoder，解碼前檢查像素尺寸；Android BitmapFactory 將輸出取樣至最大邊 1024，iOS／Web Skia 保留 12MP 安全上限，Web 先等待 Skiko 就緒。切換預覽／離開釋放 UI 顯示資源；沒有應用程式管理的跨預覽 LRU、磁碟／IndexedDB cache，不宣稱 native／browser HTTP stack 完全不保留內部資源。
- 選取預覽可明確按播放／暫停，四平台復用既有 GIF decoder；優先 `fixed_width.url`，已知超過 2MiB 時改用符合限額的 `downsized.url`，沒有合適 rendition 就不啟用播放，不隱式下載 original。URL／query 原樣使用，下載仍受 2MiB gate；decode 後取消必須釋放尚未交給 UI 的 decoder，清理失敗不覆蓋原始取消。一般 GIF 建議的 MP4／WebP rendition adapter 仍待補，目前是原生 GIF decoder 的明確播放預覽，非動畫結果網格或 Canvas renderer。
- 初始不播放；pause 保留當前預覽 decoder／幀供本畫面恢復，關閉、改選、更新預覽、畫面裁切不可見、生命週期 ON_STOP、runtime activity epoch／available 變更或 Reduce Motion 會取消並釋放，不自動續播。播放完畢／失敗可明確重試。動畫 bytes、decoder、播放狀態不進 Workspace／operation／Quick Scheme。
- 「更新預覽」明確重新查詢選取 ID，成功後載入最新 still；移除／限額／錯誤時不繼續顯示舊圖。切換選取、查詢、key 或關閉預覽取消更新，generation gate 防止晚到回應覆蓋。API 預設也停用 redirect，不將帶 key 的請求自動轉往其他 host。
- 已使用官方 ZIP 提供的原始 Powered By GIPHY PNG，固定於 GIPHY 面板的捲動區之外，不裁切／換色／重畫，從 Compose resource 載入（native 封裝／Web 同源靜態資源，不請求 GIPHY API／CDN）；[來源、hash 與取得紀錄](GIPHY_BRAND_ASSET_PROVENANCE.md)。品牌位置／清晰度與正式整合審核仍由產品負責人／GIPHY 驗收，不能稱 production 已批准。官方建議標準 GIF 動態播放採 MP4／WebP rendition，不應為 GIF 副檔名而自行轉碼或另存。[Rendition／語言規格](https://developers.giphy.com/docs/optional-settings/)

## 後端／畫布外部引用待對接

建議新的 `ExternalMediaNode`／`external_media`，不是 fake asset UUID：保留真實 Canvas object UUID、provider=giphy、providerId、transform、parentId、zIndex、locked；不保存 rendition URL、API URL／key 或 GIF binary。projection、create/update/delete、ACL、relation、剪貼簿、Undo／Redo、Quick Scheme 需正式支援這個 kind。此為建議合約，尚未修改後端或啟用插入。

client 重新開啟或引用進入 viewport 時，直接依 provider ID 查最新 metadata；多筆可見引用採官方批次 endpoint，並共用查詢額度 gate。removed／rating blocked／無網路時保留可恢復佔位，不刪 Node；外部引用不保證離線可播放。正式 renderer、analytics 的適用事件／隱私設定、官方品牌標示与外部引用缓存批准需完成後才放行。
