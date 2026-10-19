# 大型時序圖原子容量需求（提案，尚未交付）

2026-10-12。本文件供後端 agent 規劃，不是已上線 API。现行 operation.schema 上限200 operations，只有 singular create_object／create_relation，不能覆蓋 APP 合法最大32 participants／512 messages／128 blocks。APP目前先預檢，不能拆transaction假裝原子或以小圖通過宣告完成。

## 建議正式契約

提議協商後擴大至2048 operations／4 MiB UTF-8 request，最終數值由後端確認，提供正式 maxOperations／maxRequestBytes capability、schema、OpenAPI與fixtures；不能只改validator。未知能力維持200。保留原ID、clientSeq、operation順序、整筆idempotency digest、CAS／ACL、單workspace commit、receipt／fence、restore語意；单物件與文字／深度驗證不放寬。

時序編輯會 temporary metadata clear→graph mutations→final metadata bind，两次CAS仍必須同transaction；任意失敗不得留下中間态。端點變更採delete＋create，不能自行使用未交付endpointRewire或重新配整圖ID。

## REST、WS與舊版相容性

同步交付 WS frame／catch-up transaction／ACK／body limits與APP decoder。若需新版protocol，必須正式ticket／join協商，不猜新token。不能只放大REST，把超量廣播給只支援200的舊client。舊版應沿正式resync／完整state恢復策略，不能裁剪或拆成假transaction；具體版本與原因token由後端定案。

## 必須交付的 fixtures 與驗收範圍

- 200／201／2048／2049及byte邊界，包含完整操作順序、两次metadata CAS／version、ACK／receipt／fence。
- 最大模型新建／修改、端點替換、Undo／Redo restore；末尾binding錯誤、撤權、版本race整筆拒絕。
- duplicate全文一致、same-ID不同內容、response-lost、crash、跨process fan-out／legacy resync与backpressure。
- 正式capability／OpenAPI／JSON Schema、source commit與部署狀態，現有契約相容紀錄。APP收到正式能力才改採更大容量。

未執行後端變更／migration／HTTP；正式驗收待全部業務功能完成後集中進行，效能最後。
