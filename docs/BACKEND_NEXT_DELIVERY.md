# 後端下一批交付依賴（2026-10-11）

實作入口：[尚缺後端能力實作規格](BACKEND_MISSING_IMPLEMENTATION_SPEC.md)。包含A–F交付批次、路由／operation、CAS／durability與APP版本對齊；不是只有驗收計畫。

素材 preparation v2 已交付；APP 接線已實作、尚待所有功能完成後集中驗證。本文件整理仍未出現在目前後端路由／projection 的依賴，不是新的服務宣告，也不授權 APP 猜 endpoint 或略過不支援欄位。

統一驗證入口：[後端驗證與交付驗收規格](BACKEND_ACCEPTANCE_SPEC.md)，列出案例 ID、通過條件、交付證據及最後集中驗收順序。這是待執行計畫，不代表新增測試已通過。

## 先確認素材 v2 的 declared／verified metadata 邊界

更新：後端已交付分離declared／verified及ASSET-07新建引用Ready規則；APP本輪已按最新源碼接線，尚未集中驗證。以下為發現問題的歷史原因，不再視為尚未實作的metadata blocker。剩餘依賴是正式fixtures／雙端驗收及「換素材Undo到missing原引用」具原交易證據的還原語意；不放寬一般update_object。

2026-10-11 唯讀檢查發現：`verify-original.job.ts`把`inspectMedia`結果交給`assets.markReady`；repository會覆寫width／height／durationMs，而preparation envelope直接回`toPublicAsset(asset)`。APP目前依v2 immutable descriptor規則要求這些欄位與prepare metadata完全一致；prepare為null而Ready填值、或不同平台解析影片時長不同時，可能造成Ready回覆被拒絕。這是程式碼證據，尚非真服務故障重現；原檔bytes／hash相同也不能擅自省略已約定欄位。

請後端agent確認並交付一致語意與fixtures：若declared metadata是固定identity，就不能在receipt中用verified值取代它；若允許verified補充／更正，需正式分開兩份metadata、定義哪些欄位可變及各自digest／phase規則，再一起更新APP local descriptor／mapping。尤其補null→verified尺寸、duration差異、rejected／missing回覆與重送prepare cases。APP已顯示專屬差異提示、保留原request，不自動重傳或授予映射；本輪沒有更動backend。

## 建議交付順序

1. 正式即時協作：[BACKEND_REALTIME_API_SPEC.md](BACKEND_REALTIME_API_SPEC.md)。交付 capability、authenticated ticket／join、committed transaction 全範圍廣播、完整 catch-up／snapshot、room／connection epoch、Presence／游標／selection TTL、撤權與重新連線。APP 已有 scope／lease／backpressure／receipt-fence 基礎，但目前 Backend adapter 仍只有 REST。
2. 使用者層級快速方案簿：[BACKEND_QUICK_SCHEME_API_SPEC.md](BACKEND_QUICK_SCHEME_API_SPEC.md)。交付 user-scoped CRUD／revision／tombstone／冪等 mutation／衝突及素材來源授權。APP 的 local Int ID、匯入 request receipt、方案檔搬移都不等於遠端資源身分或雲端同步。
3. 共用背景：[BACKEND_CANVAS_STYLE_API_SPEC.md](BACKEND_CANVAS_STYLE_API_SPEC.md)。交付正式 canvasStyle projection、versioned operation／CAS、history replay與Undo，不能用 device settings 或無版本 PATCH 代替。
4. 圖形與連線：[BACKEND_DIAGRAM_RELATION_V2_SPEC.md](BACKEND_DIAGRAM_RELATION_V2_SPEC.md)、[BACKEND_VECTOR_PATH_API_SPEC.md](BACKEND_VECTOR_PATH_API_SPEC.md)。交付 capability及正式自循環／geometry／插入節點交易／vectorPath；sequence metadata 的正式 schema、bindings及versioned operation也需按 relation v2 文件確認，不能丟欄位降級。

## 每批共同交付內容

- capability 與精確 protocol/schema version，未支援能力的明確錯誤；不要僅以文件存在推論支援。
- 嚴格 request／response schemas、fixtures、migration、receipt／idempotency及並行／權限／unknown ACK規則。
- production replay／snapshot／projection 使用同一已提交 transaction 範圍；禁止只推播部分 operation。
- 標示源碼完成、服務已部署與故障驗收三種不同狀態。依目前使用者要求，APP 所有業務功能完成後才集中驗證；正式雙端、真 DB restart及視覺簽核不能由 mock代替，效能最後。

目前後端由其他 agent 維護。本文件供使用者轉交，未修改／啟動後端、未代發其他聊天訊息。
