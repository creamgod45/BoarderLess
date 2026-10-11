# 後端驗證與交付驗收規格

日期：2026-10-11。狀態：驗收計畫，本文不代表案例已執行或通過。

用途：提供後端 agent 與 APP 共用的驗收入口。功能 schema、endpoint、錯誤碼以各正式契約為準；尚未定案的草案不得直接當成現存能力。本文件不新增 API，也不替代原契約。

依產品要求：先完成所有業務功能，再集中執行契約、整合、故障恢復與雙端驗收；效能測試排最後。既有後端測試結果僅證明當時版本，不代表最新 APP 對接已通過。

## 1. 驗收版本與證據

每次驗收必須記錄 backend／APP commit、未提交差異、schema／capability 版本、migration、部署版本與測試環境。使用隔離 QA workspace、至少兩個不同權限帳號及兩個 client；不得清空使用者 DB 代替遷移驗證。

每個案例交付：案例 ID、前置資料、操作／故障注入、預期與實際結果、匿名化 request／response 或事件 fixtures、執行時間與結果檔。涉及交易須附完整 transaction 範圍及 DB／projection 檢查；單一 HTTP 成功碼不能證明整體通過。

狀態只能是：待實作、待執行、通過、失敗、受阻、不適用（需理由）。mock、重新開連線池、真服務、真 DB 重啟及人工雙端驗收必須分開標示。不得保存 API Key、Bearer token、signed URL、私有原圖或其他使用者內容於公開證據。

## 2. 共同資料正確性與安全門檻

| ID | 驗證情境 | 通過條件 |
| --- | --- | --- |
| CORE-01 | capability／schema 協商及舊版 APP | 未支援能力明確拒絕；不靜默忽略欄位或啟用草案 endpoint。 |
| CORE-02 | 未知型別／欄位／token、錯誤 primitive、null、尺寸與數字邊界 | 按正式 schema 拒絕；不得回 500 或產生部分資料。 |
| CORE-03 | 同 actor／request 重送與不同 payload | 相同請求確認原結果，不重建 identity；不同內容按契約衝突。 |
| CORE-04 | 多 process 同時寫、版本衝突、交易中途失敗 | 一致 CAS 與原子交易；完整成功或完整回滾，沒有孤立物件。 |
| CORE-05 | 跨 user／workspace、viewer 寫入、操作途中撤權 | 每步重新授權，無越權讀寫或 identity 洩漏；HTTP／socket 一致。 |
| CORE-06 | commit 後丟回覆、receipt 查詢與 fence 競爭 | 查到原完整交易；commit／terminal fence 不可同時成立，晚到原提交不重建。 |
| CORE-07 | 刪除 Undo／新增 Redo、group hierarchy、relations | 用 restore_* 保持 ID、版本單調；循環／非法 parent／無效端點拒絕，cascade 原子。 |
| CORE-08 | 真 DB 重啟、migration、舊資料與 replay／snapshot | receipt／tombstone／交易仍在；projection 一致，非法舊資料有掃描與遷移計畫。 |

參照：[原提交 receipt／fence 驗收](ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE.md)、[Undo／Restore 驗收](UNDO_RESTORE_ACCEPTANCE.md)、[Local-first 同步契約](BACKEND_LOCAL_FIRST_SYNC_SPEC.md)。

## 3. 素材 preparation v2

契約：[BACKEND_ASSET_PREPARATION_V2_SPEC.md](BACKEND_ASSET_PREPARATION_V2_SPEC.md)、[Media v1](BACKEND_MEDIA_API_SPEC.md)。後端已回報 v2 源碼交付；正式 APP 端到端仍待集中驗收。原規格頂部「尚未實作」屬先前狀態，不能據此否定交付或據測試數推定雙端通過。

| ID | 驗證情境 | 通過條件 |
| --- | --- | --- |
| ASSET-01 | 首次 prepare、同 key 重送、改 metadata | 分別 201／200／409 preparation_mismatch；固定同 asset ID／digest，多 worker 僅一份。 |
| ASSET-02 | explicit null、canonical MIME／checksum、golden vector | 跨端 digest 一致；不做 primitive 型別轉換，欄位完整。 |
| ASSET-03 | prepare commit 後丟回覆、presign 失敗、重啟 | 原 requestId 可查回原 identity；reservation 不因簽名失敗消失。 |
| ASSET-04 | ticket 冪等、過期、renew／complete 競爭 | 同 ticketRequestId 同 lease；新 lease 的替代與過期寬限按正式規則，舊 lease 不冒充目前 lease。 |
| ASSET-05 | complete 重送、wrong lease／hash／size、v1 繞過 | accepted＋verify job 原子；不重建工作；錯誤明確拒絕，v1 不繞過 v2。 |
| ASSET-06 | accepted／ready／rejected／missing／abandoned、晚到請求 | phase 明確，receipt／tombstone 保留；terminal 請求不復活，Ready 不提供可覆寫 PUT。 |
| ASSET-07 | 舊 signed PUT 仍有效、原檔改變、GET／hash 驗證 | staging 隔離，不能改已接受／Ready 原檔；錯檔不得授予映射或建 Node。 |
| ASSET-08 | declared 尺寸為 null、verified 補尺寸、影片 duration 差異 | **先定案 metadata 邊界再驗收**：固定 declared identity 不被覆寫，或正式交付分離 declared／verified 的新契約與 APP fixtures；禁止 APP 靜默放寬。 |
| ASSET-09 | 雙端離線匯入→明確上傳→Ready→canvas 審阅→重開 | 本機／遠端 ID 映射正確；素材準備不自動改畫布；未知 ACK 可恢復，Undo／Redo 不丟資料。 |

ASSET-08 是目前程式碼核對發現的契約風險，尚未真服務重現。真 PostgreSQL 重啟不得用新 pool／新 app 模擬取代。

## 4. 即時協作

契約：[BACKEND_REALTIME_API_SPEC.md](BACKEND_REALTIME_API_SPEC.md)。REST 輪詢不算 WebSocket 驗收通過。

| ID | 驗證情境 | 通過條件 |
| --- | --- | --- |
| RT-01 | authenticated ticket／join、Origin／scope／epoch | 只接受合法目前連線；過期或錯 scope 拒絕，正式環境 HTTPS／WSS。 |
| RT-02 | join 時並行提交、分頁 catch-up、live barrier | 無缺段；完整 transaction 才發布，checkpoint 不越過未完整交易。 |
| RT-03 | 重複／亂序／gap／未知 kind、REST 與 WS 交錯 | 正確去重與權威 resync；不跳過未知事件或用舊 snapshot 覆蓋新狀態。 |
| RT-04 | 多 process fan-out、掉線／重連／服務重啟、背壓 | 已提交交易可恢復；不是只送 wakeup 或部分 operation；慢 client 不無限積壓。 |
| RT-05 | Presence／游標／selection、離開／kill／撤權 | 暫態資料不進持久畫布；TTL 收斂，舊 epoch 不污染新 room，撤權立即停止。 |

## 5. 快速方案簿與共用背景

契約：[快速方案簿](BACKEND_QUICK_SCHEME_API_SPEC.md)、[Canvas Style](BACKEND_CANVAS_STYLE_API_SPEC.md)。

| ID | 驗證情境 | 通過條件 |
| --- | --- | --- |
| SCHEME-01 | user-scoped CRUD、不同裝置同帳號、不同帳號 | 正式遠端 identity／revision，跨裝置可讀；不洩漏別人方案，本機 Int ID 不當雲端 ID。 |
| SCHEME-02 | mutation 冪等、並行改名／刪除、掉回覆／重啟 | 原結果可恢復、衝突明確；tombstone 防舊請求復活，不以內容相同冒認原提交。 |
| SCHEME-03 | 方案含素材、來源撤權、匯入確認 | 素材按契約驗 ACL／可搬移性；讀取或預覽不自動存入／插入畫布。 |
| STYLE-01 | versioned style Apply／Cancel／Undo／Redo、同時修改 | Apply 一筆正式 operation；取消零提交，CAS 衝突明確，版本單調。 |
| STYLE-02 | state／snapshot／replay／重開／兩端 | 共用 style 一致；theme、viewport、snap 等個人偏好不被誤同步。 |

## 6. 節點、連線、鋼筆與時序語意

契約：[Diagram／Relation v2](BACKEND_DIAGRAM_RELATION_V2_SPEC.md)、[Vector Path](BACKEND_VECTOR_PATH_API_SPEC.md)。

| ID | 驗證情境 | 通過條件 |
| --- | --- | --- |
| GRAPH-01 | 自循環／平行線、geometry／label anchor、非法 schema／數字 | 保存完整正式 geometry，不降級丟欄位；合法自循環可 replay，非法內容按契約拒絕。 |
| GRAPH-02 | 線上插入節點、並行刪線／端點、撤權／未知 ACK | 刪原線＋建節點＋兩條新線全成或全敗；receipt／fence 恢復不另造 ID。 |
| GRAPH-03 | vectorPath 邊界、create／update／delete／restore | 嚴格限制與版本控制；DB／snapshot／replay／Undo 不丟 path。 |
| GRAPH-04 | sequence participants／messages／blocks／bindings／巢狀分支 | semantic ID 與 graph UUID 分離；完整 bindings 與 versioned metadata 同交易驗證，非法引用原子拒絕。 |

## 7. 最終驗收順序與完成門檻

1. 所有計畫內業務功能完成、待定契約定案，封存驗收版本與共用 fixtures。
2. 集中執行 schema／契約／migration／後端整合及並行故障案例。
3. 真服務＋APP 雙端執行，包含未知 ACK、撤權、離線、重開與 Undo／Redo。
4. 產品負責人依[產品 QA 品質檢驗 SOP](產品%20QA%20品質檢驗%20SOP.md)簽核視覺、遮擋、觸控與 VoiceOver；自動化不能代簽。
5. 最後執行效能／容量驗收：先確認硬體、資料量、連線數與目標值，再記錄吞吐、延遲、記憶體及恢復時間；未約定數字不得自稱效能達標。

完成條件：適用案例全部有可追溯證據；失敗／受阻項不可算通過。backend 源碼完成、服務部署完成、APP 對接完成、端到端验收通過需分別報告。尚未提供的正式服務能力保持待實作，不以 mock 替代。
