# 節點、連線與圖型後端契約 v2

2026-10-06 Desktop本機自循環與geometry接線：FileRepo支持RelationGeometry；選取一個text/media可明確建立自循環，重複loop逐次增加有界extent，不把普通node當時序participant。geometry採boarderless.relation-geometry.v1＋mode union（auto／manual≤32 world points／loop side+extent16..4096；label auto／manual fraction+有界world offsets），constructor及strict decoder拒絕錯schema／type／token／nonfinite／錯endpoint route。CreateRelations只在明確geometry時允許self-loop；零尺寸端點拒絕建立，已有loop的端點不可transform至零尺寸。RelationAttributes包含完整geometry，before-state／version guard與inverse保留路徑，delete/cascade只一份relation，native文件atomic保存與reopen無損。

renderer／hit test／labels共用relationWorldRoute：loop沿真shape外側不同ports並跟隨旋轉／移動／縮放，direction只改箭頭；manual world waypoints不隨node移動，邊界接段重算。Inspector提供四側loop、extent增減、輸入world waypoint list→小預覽→明確Apply、重設auto、manual label offset增減／重設；變更走既有history／queue，未Apply的文字不入文件。manual label按實際source→target弧長與world offsets重算，manual bounds加入auto label避讓。普通parallel edge在本機放寬duplicate guard，不推測時序；自動路徑可能重疊，可明確設定manual路徑，不宣稱parallel自動分流／完全避障。直接拖曳線條waypoint或label尚未實作。

選取／clipboard升v5，geometry與self-loop保存／複製，manual world points隨整體paste平移且越界不套用；v1..4仍可讀，舊版本不可帶新geometry／self-loop。方案簿native與transfer codec讀v1..5，geometry-aware縮圖包含loop外側路徑；ClipboardJson改strict unknown fields防抹除未識別資料。插入節點以同一route midpoint放置，explicit loop可拆A→N→A，新段重設auto、原loop snapshot／Undo geometry保留。Backend支持旗標維持false：UI關閉新入口／拒絕新clipboard幾何，draft append和DTO conversion前rejectcreate/update/delete/cascade（含nested transaction）；projection帶geometry（包括null）明確contract error，不忽略後繼續同步。AI工具capabilities未開loop／geometry，不能把前端能力當工具已驗。

56839最終隔離build成功62s：JVM907（6既有skip）、JS819、Wasm819、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過並更新desktopApp/build/verified/BoarderLess.app，核含geometry model／controls／routing classes。Common geometry7cases驗create／delete／cascade UndoRedo、world route／direction／node move／manual anchors、geometry update inverse／stale state、strict bounds/schema/union、v5＋scheme與loop插線、zero-size拒絕、Backend零Settings／零HTTP；projection新增unnegotiated geometry不丟棄。Native新增loop→geometry edit→reopen→node cascade→Undo→reopen。最初fixturebuild方法／HistoryResult undo／projection objects漏參數與舊v4 fixture被constructor升v5影響均修正，沒有放寬production guards。未做GUI／真server v2／OS kill驗收；鋼筆仍preview editor未正式canvas object，sequence participant/step/block metadata／Mermaid wizard未實作。實際AI401仍未排除，完整六主線goal active，不把本機geometry當正式跨端同步完成。


日期：2026-10-05。狀態：**APP 提出的交付草案，待後端 agent 確認及實作**。本文不代表運行服務已支援，不修改後端，也不要求啟動或遷移資料庫。

適用：自循環、平行連線、路徑／標籤拖曳、線上插入節點、明確時序圖語意。沿用既有 operation transaction、ACL、版本、receipt／fence；不新增第二套 canvas 儲存系統。

## 1. 現況與增量

本次唯讀核對 `backend/src/domain/operations.ts`、`backend/src/services/canvas-mutations.ts`：

| 項目 | 現有能力 | 本文要求 |
| --- | --- | --- |
| 物件 | text／group／media，型別化 properties、transform、群組階層 | 保留，不為新形狀新增 objectType |
| 形狀 | APP 現有 25 個 token 已由後端合約接受 | 新形狀／鋼筆另列 schema 擴充，不視為任意 Mermaid shape 已支援 |
| 連線 | create/update/delete/restore、四種 direction、label／intent／style | 新增正式 geometry 子格式及自循環 |
| 自循環 | create 明確拒絕 source=target | capability 開啟後允許；所有讀寫／還原路徑一致 |
| 端點修改 | update 明確回 unsupported_change | 第一階段保持不支援，不偷改端點 |
| 插入步驟 | 既有多 operation 原子交易可作基礎 | 定義拆線、CAS、Undo／Redo 及標籤保留語意 |
| 時序圖 | 無正式持久化順序／區塊合約 | 第二階段新增明確 metadata，不從座標猜時序 |

REST 輪詢可先 round-trip 本文資料；真正 WebSocket／Presence 仍依 BACKEND_REALTIME_API_SPEC，不能把資料格式交付当成即時協作完成。AI 除錯 UI 本身不需要新的後端 API。

## 2. 能力發現與相容性

需交付版本化 capability response 及正式 fixtures。**路由由後端在交付文件中確認，APP 不猜 endpoint。** 至少聲明：

```json
{
  "canvasSchemaVersion": 2,
  "capabilities": {
    "relationSelfLoop": true,
    "relationParallelEdges": true,
    "relationGeometry": "boarderless.relation-geometry.v1",
    "diagramMetadata": false
  }
}
```

上述值是目標示例，不是目前 capability。功能未交付應回 false 或省略；APP 關閉相關寫入入口，不能在陌生 style 裡塞資料假稱同步。

無 geometry 的 v1 線條保持自動路徑／自動標籤。舊 APP 不認識 v2 geometry 時只能唯讀降級，不得用 update 抹除資料；不能處理自循環／新 operation 時應要求升級或安全重新載入，不得丟棄線條。後端交付需明確列最小可寫 APP 版本與拒絕策略。

## 3. Relation geometry v1

使用現有 `style.geometry` 作版本化保留欄位，其餘既有樣式格式不在本次重定義。下列為同一 relation 的 style 局部示例：

```json
{
  "geometry": {
    "schema": "boarderless.relation-geometry.v1",
    "route": {
      "mode": "manual",
      "waypoints": [{"x": 320, "y": 180}, {"x": 520, "y": 180}]
    },
    "labelPlacement": {
      "mode": "manual",
      "pathFraction": 0.5,
      "tangentOffset": 0,
      "normalOffset": 24
    }
  }
}
```

- route 判別聯集：`{"mode":"auto"}`；`{"mode":"manual","waypoints":[...]}`；`{"mode":"loop","side":"right","extent":80}`。各分支禁止不相干／未知欄位。
- 普通線只允許 auto／manual；自循環只允許 auto／loop，不用零長度直線冒充。自循環 auto 的預設 right／extent=80 由 renderer 契約固定。
- waypoint 是世界座標、依 source→target 的儲存順序，最多 32 個；manual 至少 1 個。點不含端點，renderer 依物件邊界補首尾；移動物件後點維持世界位置，端點連接段重算，不整條線跟著滑鼠錯誤旋轉。
- side 為 top／right／bottom／left，相對 source 物件未旋轉本地座標；物件旋轉後 loop 一起旋轉。extent 是向外延伸的世界單位，範圍 16–4096。首尾使用同側不同錨點，必須形成非零長度路徑。
- labelPlacement：`{"mode":"auto"}` 或完整 manual 四欄。pathFraction 在 [0,1]，沿 source→target 的實際路徑弧長；tangentOffset 沿局部切線、normalOffset 沿其順時針 90 度法線，皆為世界單位。最大絕對 offset=4096。
- 路徑在 world x 向右／y 向下座標系解析。direction backward 只改箭頭，不反轉 geometry 的 source→target 座標定義；both／none 亦不反轉。零長 segment 不參與長度及切線計算；無可用切線時不得保存 manual placement。
- 所有數值有限，座標絕對值 ≤10,000,000；NaN／Infinity／錯誤型別／explicit null／未知 schema 或 token 拒絕。上限是本草案的產品限制，後端需確認並交付一致 fixtures。
- create 缺 geometry 合法；update 缺 geometry 表示不修改。update 有 geometry **整個子物件替換**，不深層 merge；要重設就送完整 auto route／auto labelPlacement，不用 null。伺服器應驗證合併後的完整 relation。
- create/update/restore/state/operation log/catch-up 都需無損 round-trip；delete tombstone 保留原 geometry，restore 恢復該狀態並 version+1。不能只在 state 回傳但 event 丟欄位。
- 自動標籤避讓是 APP 衍生布局，不寫 operation；手動值才同步並進入 history。自動避讓不保證各平台字體下像素完全一致。

正式 renderer／hit-testing 另需 APP 測試，不由後端存像素結果。手指或滑鼠拖曳只 preview，放開一次 update_relation；取消零提交。重設自動同樣是一筆可 Undo update。

## 4. 自循環與平行關係 invariant

source=target 在能力開啟後合法；仍要求同 workspace、active endpoint、合法 ACL 與 geometry。刪除該 node 時自循環只 cascade 一次；還原時 relation version 也只增加一次。group 的階層 cycle 仍禁止，不能因開放 relation cycle 而放寬 parent invariant。

relationId 是 identity；不同 ID 可有相同 source／target／intent。重送同 operation／transaction 保持原冪等規則，不把另一 ID 的線條吞成 duplicate。APP 既有 duplicate guard 亦須更新。

建線可連到 text、group、media（包含既有 GIF 素材物件），但不能指向 asset ID／外部網址。鎖定語意建議沿 APP：鎖定節點禁止經此工具移動或修改節點，不禁止另建合法 relation；角色 ACL 仍必須檢查。後端需在交付時明確確認鎖定是否為伺服器 invariant，不宣稱目前已有此保證。

## 5. 插線建立中間節點：第一階段不增加新 operation

單擊線條只選取。使用者按「插入步驟」後先預覽並確認 node kind／parent／位置／兩段標籤，才送以下同一 transaction：

1. `delete_relations` 刪除原線 R（expectedObjectVersions 使用 R 的權威 active version；relation CAS 沿既有欄位名稱）。
2. `create_object` 建立新 UUID 的 N，kind 限既有支援型別；parent 由使用者確認，預設 null，不從幾何位置猜群組。
3. `create_relation` 建 R1、R2，兩者新 UUID；不能以同 ID create 代替 restore。

forward A→B：A→N→B。backward 儲存 A/B 而箭頭 B→A：新線明確採 B→N→A 的 forward 結構。both：A↔N↔B；none：A—N—B，僅關係拆分，不假稱時序。self-loop A→A：A→N→A，形成兩條普通線而非新自循環。

原 label／intent 預設保留在按實際方向第一段，第二段 label／intent=null；both／none 以原 source 作第一段。預覽需說明，使用者可修改。不沿用原整條 manual route 到兩段；新段預設 auto，避免 geometry 跨錯端點。

操作全成或全敗，失敗不得留下 N、孤線或刪掉 R；同交易建立物件後能供後續建線參照。並行變更 R／endpoint 刪除／parent 無效／撤權都要阻止或明確 conflict，不靜默改成最新版。未知 ACK 先查 receipt／fence，不能再造一組 ID 重送。

Undo：刪 R1/R2、刪 N、`restore_relations` 原 R，使用各權威版本與 tombstone CAS；若 N 已被他人增加 children／relations，不擅自 cascade 刪他人資料，拒絕並要求重驗。Redo：刪 R，再 restore N，最後 restore R1/R2；保存 exact deletion provenance 與最新 ack 版本。刪除 cascade 只算一次，不臆造版本。

端點拖曳改接不是以上「路徑拖曳」。第一階段仍 unsupported_change；如要保留 relationId 的正式 rewire，必須另定 operation、CAS、幾何重設及 Undo 語意，不能在此次 update_relation 偷開放。objectType 轉換也維持不支援；修改同 text 物件的合法 shapeToken 不等於改 kind。

## 6. 第二階段：明確圖型及時序 metadata

預設圖型 flowchart；只有使用者明確建立其他圖型才改 expression。需求為 workspace 的版本化 diagram metadata（提出 schema `boarderless.diagram.v1`），不是僅存 Mermaid 字串。正式 operation 名稱與 metadata revision／CAS envelope 由後端另行交付；本階段未交付前不開 sequence 建立入口。

sequence 至少需：ordered participants（participantId、nodeId、actor／participant）；ordered steps（stepId、relationId、call／return／self-message、blockId）；ordered blocks（blockId、parentBlockId、alt／else／loop／par、標題與子順序）。身分使用 UUID，N/E 只是 AI packet alias，不可存成正式 ID。

需明確決定完整文法：alt 的 else 分支、par 的分支、巢狀 block，以及 relation 刪除後引用處理。限制順序無重複、引用存在且同 workspace、block 無 cycle；self-message 必須是相同 participant，普通 call 不能被座標暗示成 self-message。訊息／block 排序與 graph geometry 分開。

metadata 更新與必要物件／relations 變更同一原子交易，使用 metadata revision CAS，不能全檔 last-write-wins。Undo 要還原 metadata 及 graph 的同一語意狀態；restore 原 relation 若無法恢復合法 step 引用，整筆失敗。只通過 Mermaid syntax parser 不代表上述語意完整。

鋼筆自訂形狀及完整 Mermaid shape 對應另需 `shape schema`／受限 path commands、bounds／長度上限／安全解析規格；本次不允許任意 SVG／HTML 塞 properties。時序圖、鋼筆與端點改接是後續，不阻擋第一階段自循環與 geometry 交付。

## 7. 後端交付與驗收清單

第一階段必交：正式 capability 路由與 schema；DB／投影／事件相容策略；geometry 判別 schema 與錯誤碼；原子 insert-step recipe；以下匿名化 fixtures 和執行結果：

1. 普通／四方向／平行／自循環 create→state→update→delete→restore→catch-up。
2. 一個 node 刪除自循環一次、version map 精確對齊 payload UUID；所有 map keys 與 values 一致匿名化。
3. manual route／manual label／auto reset 完整 round-trip；style.geometry 整體替換，不殘留旧 waypoints。
4. 未知欄位／schema／token、null、NaN／Infinity、超限座標、33 waypoints、錯誤 endpoint／workspace／ACL，無部分提交。
5. forward／backward／both／none／self-loop 插入、Undo／Redo、同 transaction endpoint visibility，CAS race 和撤權。
6. 原提交已 commit／未 commit／late commit 的 receipt/fence；重送不重建 node 或線條。
7. 舊 APP 降級／拒寫、新 APP 讀 v1 自動線；第二階段 metadata 未交付時能力仍 false。

建議安全錯誤分類：`unsupported_capability`、`unsupported_geometry_schema`、`invalid_relation_geometry`；現有 conflict／ACL／unsupported_change 沿用。新碼是草案，正式 status、details schema 由後端確認，不混作已上線錯誤。

不要求清掉使用者資料重建 DB。掃描既有非法資料、遷移／回滾／最小版本及 transaction size 上限必須提供執行計畫；fixtures／backend tests 不取代 APP 真實跨端保存、操作與人工驗收。視覺遮擋、觸控、VoiceOver 由產品負責人簽核；效能最後。

參考：SEQUENCE_AND_RELATION_INTERACTION_SPEC、BACKEND_APP_INTEGRATION_ISSUES（BAI-007／008／011／012）、AI_DIAGRAM_EXPRESSION_SPEC、UNDO_RESTORE_ACCEPTANCE、BACKEND_REALTIME_API_SPEC。
