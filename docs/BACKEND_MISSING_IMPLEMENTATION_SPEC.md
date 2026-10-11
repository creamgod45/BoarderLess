# 尚缺後端能力：實作交付規格

日期：2026-10-11。用途：直接交給後端 agent 實作，不是單純驗收計畫。

下列是要求交付的目標契約，不是現存 API。後端交付精確 schema／capability／fixtures 後，APP 才接入；如需調整本規格的 wire 命名或部署方式，必須記錄差異，不能靜默丟欄位。詳細契約與本文件增量一併閱讀；衝突時以下「目前 APP 對齊事項」優先於舊文件歷史進度敘述。

## 1. 範圍與實作順序

| 批次 | 缺少能力 | 必要交付 |
| --- | --- | --- |
| A | 正式即時協作 | ticket／join／完整交易 fan-out／catch-up／Presence／撤權 |
| B | 使用者層級方案簿 | UUID 資源 CRUD／ETag／mutation receipt／tombstone／穩定分頁 |
| C | 共用背景 | canvasStyle projection、CAS operation、snapshot／replay／Undo |
| D | 連線與鋼筆 | 自循環／geometry／插線原子交易／typed vectorPath |
| E | 時序圖 | 語意 metadata、bindings、versioned operation、原子 graph 驗證 |
| F | 換素材歷史還原 | 有原交易證據的引用還原，不放寬一般 update 的 Ready 規則 |

素材 preparation v2 的 declared／verified 已交付，不要求重做。AI Router 是 APP 對接 OpenAI Chat／Claude Messages 相容服務，本批不要求 BoarderLess backend 保存 AI Key 或代理 AI 請求。APP 測試／建置與正式驗收仍等所有业务功能完成後集中執行，效能最後；先交付業務源碼與契約，不以反覆跑全套測試取代功能實作。

## 2. 所有批次共同規則

- 認證 actor 由 middleware 決定，body 不能自選 owner。x-user-id 僅明確 dev 替身；production auth／Origin／CORS 的部署方式需交付配置說明，不能宣称dev模式已具正式安全能力。
- 畫布寫入沿現有 POST `/api/v1/workspaces/:workspaceId/operations`、transactionId／operationId、ACL、版本衝突、receipt／fence；不另建無版本 PATCH 畫布通道。
- state／snapshot／operation replay／REST ACK／WS 廣播必須同一 canonical schema，完整已提交 transaction 全範圍發布，不能只發部分 operation。
- 新 schema 嚴格拒絕未知欄位、錯 primitive、非法 null／token、非有限值及超界；不得先存任意 JSON 後交 APP 猜。limits 同時公布與 enforcement。
- 增量 capability 須保留既有 assetPreparation 宣告；沒有交付的能力為 false／不宣告。後端回報最小可寫 APP／protocol 版本，舊 APP 不可覆寫不認得的內容。
- DB 變更走 migration，不搬移測試內容、不擅自清 DB。來源碼、部署與正式驗收是三個分開狀態。
- 每批提供 OpenAPI／消息 JSON Schema、正常與錯誤 fixtures、migration、錯誤碼、部署依賴和可用 QA scope。fixtures 不含真 UUID、Key、ticket、signed URL 或私人內容；UUID map keys 也要匿名化。

## 3. A：即時協作

完整消息與 barrier 契約：[BACKEND_REALTIME_API_SPEC.md](BACKEND_REALTIME_API_SPEC.md)。

目標路由：

- GET `/api/v1/workspaces/:workspaceId/realtime-capabilities`：需 read membership，回 protocol/schema versions、同部署可驗證的 webSocketUrl、fan-out／Presence 支援、metadataRevision、大小／backpressure limits、heartbeat／TTL。
- POST `/api/v1/workspaces/:workspaceId/realtime-ticket`：已認證 user＋client＋workspace；短效一次性 ticket，目標 TTL 60 秒、原子 consume、no-store。
- WS `/api/v1/realtime`：subprotocol `boarderless.v1`，第一個 join_workspace frame 送 ticket，不放 URL query；一條連線一個 workspace，3 秒未驗證關閉，驗證前不送任何內容。

Join 綁定 connectionEpoch；roomEpoch 區分房間世代。所有後續事件包含 protocolVersion／workspaceId／connectionEpoch。catch-up 從正式 serverSeq 恢復，完整交易不切半；snapshot／catch-up 到 live 必須有一致 barrier，不能漏掉 join 途中提交。

多 Node.js process 共用 durable operation log 與 transaction outbox；commit 同 transaction 建 outbox，發布失敗可重送，subscriber 用正式身份去重。Redis 等 broker 只能傳播／喚醒，不能作唯一歷史；crash 後未發布的 commit 必須可恢復。後端自行選 broker／dispatcher，但交付一致性與部署說明。

Presence／cursor／selection 是暫態，預設不分享，不進畫布 snapshot／Undo。離開、kill、背景與撤權按詳細文件 TTL 收斂；慢 client 有界排隊，缺段要求 resync，不靜默跳事件。REST polling 不是這批完成條件。

## 4. B：雲端快速方案簿

完整 CRUD、pagination、素材 ACL 與 conflict：[BACKEND_QUICK_SCHEME_API_SPEC.md](BACKEND_QUICK_SCHEME_API_SPEC.md)。

- collection `/api/v1/users/me/quick-schemes`；capabilities `/api/v1/users/me/quick-scheme-capabilities`。owner 由認證注入。
- GET collection／resource；PUT `/:id` create 用 If-None-Match: *，update 用具體 strong If-Match；DELETE 用 If-Match。所有 mutation 有固定 UUID Idempotency-Key；資源 UUID 不使用 APP local Int ID。
- resourceVersion=2；name、schemaVersion、payload object、assetBindings 可寫；revision／UTC timestamps／opaque ETag 由 server 生成。revision 與 collectionRevision 單調，資源／receipt／資產 pin／collection event 同交易保存。
- receipt 同 actor＋method＋resource＋key；fingerprint 含 payload 與 precondition。重送同請求回原結果，不受後來 ETag 改變影響；不同內容 409。刪除保留 tombstone，不允許過期請求復活。有限 receipt retention 必須公開期限及到期拒絕／衝突恢復規則。
- list 使用穩定 snapshot cursor，不能只用 updatedAt offset；cursor 過期明確回錯。最多100／頁、預設50、單份request 1MiB、1000 objects／2000 relations、1000 schemes／user，限制與詳細文件一致。
- assetBindings 精確覆蓋全部原檔／縮圖引用，來源 workspace read ACL 與可用狀態由 server 核對；持有 UUID 或方案所有權不授予原素材權限。未交付跨 workspace materialization 時 capability=false、引用插入阻擋，不偷偷跨 scope 共用資產。

目前 APP 對齊事項：舊文件只列 selection v1–v4，APP payload 已可讀 v1–v9（含後續屬性）。後端必須以當前 `QuickSchemeBundle.kt`／`ClipboardPayload.kt` 的 serializer 與 validators 錄製跨端 fixtures，明確公布 selectionSchemaVersions；不得把 v9 改標 v4 或丟 geometry／vector／其他欄位。可分批支援版本，但未支援完整 payload 的方案必須拒絕，不算完整六主線交付。

## 5. C：共用背景

完整欄位與錯誤：[BACKEND_CANVAS_STYLE_API_SPEC.md](BACKEND_CANVAS_STYLE_API_SPEC.md)。

state／snapshot 新增同 checkpoint 的 canvasStyle 與 canvasStyleVersion（初始0）。style schemaVersion=1：backgroundToken=default／warm／cool／lowercase #rrggbb；gridStyle.pattern=none／lines／dots；gridStyle.colorToken=auto／lowercase #rrggbb。

新增正式 `update_canvas_style` operation：payload 含 expectedCanvasStyleVersion 及完整 canvasStyle。workspace 鎖內 CAS，成功 style version+1；與圖形 operation 同交易全成或全敗。409 canvas_style_version_conflict 提供 expected／actual 與權威 checkpoint；duplicate 不加版本。history inverse 按新版本送回舊 style，不能倒退 version。

capability 宣告此 operation 與 style schema；replay／snapshot／fan-out 不丟 style。showGrid、snap、theme、viewport 是裝置偏好，不寫這個 operation。

## 6. D：連線與自訂鋼筆

完整 geometry：[BACKEND_DIAGRAM_RELATION_V2_SPEC.md](BACKEND_DIAGRAM_RELATION_V2_SPEC.md)。

- 正式支援 source=target 自循環與平行線；刪 node 的 cascade 只刪每個 relation 一次。
- style.geometry schema=`boarderless.relation-geometry.v1`；route 為 auto／manual(1..32 world waypoints)／loop(side,extent16..4096)，labelPlacement 為 auto／manual(pathFraction0..1,tangentOffset,normalOffset)。offset絕對值≤4096，world座標絕對值≤10,000,000；普通線不接受loop、自循環不接受manual。
- geometry 整體替換，不 deep merge；缺欄位update=不改，reset用完整auto。create／update／restore／log／snapshot完整保留。方向backward只改箭頭，不反轉source→target的geometry。
- 插線沿既有交易recipe：刪原relation R＋建立N＋建立R1/R2；同交易新node可供後續relations參照。保留方向／意圖／標籤／geometry的拆分規則按詳細文件，不自行猜。衝突、撤權、無效parent、endpoint刪除皆原子失敗；未知ACK查原receipt／fence，不另造ID。

完整 vectorPath：[BACKEND_VECTOR_PATH_API_SPEC.md](BACKEND_VECTOR_PATH_API_SPEC.md)。目前 APP 是 TextNode 的可選 vectorPath，目標採正式 text properties 增量，不新增相似 object kind；需明確 capability 與舊client拒寫策略。path commands 只允許 move／line／quadratic／cubic／close，schemaVersion=1，2..4096 commands／最多128 subpaths，首段move，close後只能move；viewBox兩邊0.001..100000，點絕對值≤1000000，style及command order按詳細文件。拒絕SVG／script／URL。

vectorPath 的create／update／restore／projection／clipboard／scheme無損；objectVersion、locked、parent／relation invariants沿既有transaction。沒有平台Path或gesture資料入DB。

## 7. E：時序圖

完整語意：[BACKEND_DIAGRAM_RELATION_V2_SPEC.md](BACKEND_DIAGRAM_RELATION_V2_SPEC.md)第6節；正式field以APP `SequenceCanvasDiagram.kt`／`SequenceDiagramDraft.kt`為準。

目標operation `update_sequence_diagrams`，payload含 expectedSequenceDiagramsVersion、before、after；state／snapshot包含sequenceDiagrams與version（初始0）。server同workspace鎖驗before完整一致＋CAS、after graph bindings，成功version+1。無版本全檔PATCH或只存Mermaid文字不接受。

schema=`boarderless.sequence-canvas.v1`；containerId、participants(participant＋objectId)、messages(message＋relationId＋position)、blocks(id／kind／branchLabels／objectId／position)。position.blockId=null代表root，另有branchIndex／order。semantic ID是受限ASCII識別字，不是UUID；graph IDs沿正式UUID。

每workspace最多64份；每份1..32 participants／1..512 messages／最多128 blocks／depth16；loop1branch，alt／parallel2..16且每branch非空。label≤4096 UTF-8 bytes、總draft text≤262144 bytes。Call／Return／Async／Signal與dashed依APP規則；self-message由相同participant ID決定，不從座標推論。

同transaction建立／刪除graph bindings與metadata；驗同workspace、unique ordering、所有引用存在、block無cycle。刪relation／node後不能留下懸空sequence引用，否則全交易拒絕。Undo／Redo以新版本還原metadata＋graph，不局部成功。

## 8. F：素材引用的歷史還原（新增目標契約）

普通create／替換assetId或thumbnailAssetId仍必須同workspace、具權限、Ready。restore_objects仍沿已交付刪除還原規則。本節處理普通update不能Undo回missing舊素材的缺口，不開放任意引用繞過Ready。

目標operation `restore_media_references`，payload：objectId、expectedObjectVersion、originalTransactionId、originalOperationId、targetSide（before／after）。不接受client自行提交舊assetId。原交易必須是同workspace且由目前authenticated actor提交、已commit的媒體引用更新；由server原交易provenance取before／after兩份引用，不憑APP說明或當前asset狀態推測。

server同鎖內驗owner/editor、目前object是MediaNode、版本一致、目前引用恰為原交易另一側，才還原targetSide。原檔與縮圖成對還原，其他properties不變；原資料跨scope／缺provenance／陌生actor／目前引用已被改寫皆拒絕。missing／rejected允許保持歷史引用，不使asset復活、重驗或授予下載。

DB需保存原引用before／after provenance，不能只存update patch；既有資料無provenance時明確unsupported，不回推假資料。接受後objectVersion+1、workspace／serverSeq＋receipt／replay／fan-out原子更新。APP需獨立capability；正式request／committed schema和錯誤codes在交付時一併提供。本規格不自動取得其他使用者的Undo權限。

## 9. 交付回覆模板

每批交付請附：

1. 分支＋commit（若尚未commit，明列未提交範圍）；實作批次及未完成項。
2. 精確路由／operation kind／capability＋request、response、error、committed event schemas。
3. migration、broker／worker／auth配置需求、相容性影響及部署版本。
4. 共用fixture路徑與schema versions，含CAS／ACL／duplicate／unknown ACK／rollback／舊client。
5. 尚待集中執行案例，不以預檢或mock宣告真服務／多process／雙端通過。

驗收案例ID與證據要求見[BACKEND_ACCEPTANCE_SPEC.md](BACKEND_ACCEPTANCE_SPEC.md)。產品視覺／觸控／遮擋／VoiceOver由使用者簽核；負載與效能最後。後端agent可直接依A→B→C→D→E→F交付，不需等待APP猜測endpoint；任何契約差異先列出，APP依正式交付接線。
