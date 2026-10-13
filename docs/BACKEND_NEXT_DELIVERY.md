# 後端下一批交付依賴（2026-10-11）

2026-10-13 唯讀核對：`backend/DELIVERY.md` 仍記 A–F 功能基準 `ebd05fd`／文件 `167086a1`；`backend/src` 最近提交 `64b0cb75` 主要清理未使用宣告，並非下列補充契約交付。現存 `auth.ts` 仍用開發 header、materialization／endpointRewire 仍未啟用、REST／WS 仍 200 operations、media provenance 仍缺 mediaKind。未查到新部署或正式驗收證據。本輪未修改／啟動後端，也不要求使用者先自行判別契約。

大型時序圖原子容量新增缺口：見 [BACKEND_SEQUENCE_ATOMIC_CAPACITY_SPEC.md](BACKEND_SEQUENCE_ATOMIC_CAPACITY_SPEC.md)。現有200 operations無法覆蓋最大模型；需正式REST／WS容量協商與舊版相容策略，不允許APP自行放寬或分批假裝原子。此為提案、尚未交付。

## 2026-10-12 更新：A–F已交付，F需補mediaKind歷史語意

功能源碼已交付至ebd05fd，文件基準167086a；下方「尚未出現的路由」屬交付前歷史，不再當作APP停止接線的原因。正式auth／429／跨workspace materialization／endpoint rewire仍未交付。

F新增缺口（程式碼核對，未真HTTP重現）：APP MediaReference包含assetId、mediaKind、thumbnailAssetId，UpdateMediaReferenceOperation可跨種類替換；backend mediaReferenceOf／sameReference與provenance只保存assetId、thumbnailAssetId，restore_media_references只還原這兩欄、保留目前mediaKind。Image→Video後Undo可能形成舊Image資產＋Video種類，與原APP history不一致。

請擴充F provenance before／after及restoredReference為完整三欄引用，同時驗相反側mediaKind與target原素材MIME對應；沒有舊kind provenance時明確unsupported，不根據目前asset狀態猜測歷史。migration、capability／committed schema與fixtures需一併交付，含Image→Video→原圖missing→Undo／Redo、縮圖null與種類／引用被並行改寫的原子拒絕。普通替換仍Ready，歷史還原不復活素材、不授予下載。APP目前只建立same-kind證據模型，cross-kind不硬送；不將這個子路徑冒充完整F完成。

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
