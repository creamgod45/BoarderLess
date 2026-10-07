# 共用 Canvas Style APP／Backend 合約草案

2026-10-06 背景文件保存與Undo／Redo已接線：Workspace新增strict CanvasStyle／canvasStyleVersion；預設Desktop FileRepo宣告支持。背景Apply沿execute→retainDraft→既有history／queue，完整style隨atomic文件保存，重開讀回；render依history style即時顯示Undo／Redo結果。viewport／showGrid／snap仍個人Settings，本機document mode不再把background回寫Settings，亦不自動搬移舊偏好。server mode支持旗標維持false，背景仍裝置偏好；正式Backend缺state／operation／capability，沒有發明endpoint或宣稱跨裝置同步。

UpdateCanvasStyleOperation有before-state＋style CAS；Undo／Redo只rebase版本並保留before guard，styleVersion單調增加，安全整數上限拒絕。schema1的default／warm／cool／lowercase hex、grid none／lines／dots與auto／hex嚴格驗證；grid renderer可讀這些正式樣式，spacing沿現有規則，未宣稱跨density合約已驗。picker捕捉user/client/workspace/style/version；scope／撤權取消、style drift拒絕Apply、Preview／Cancel零operation；未變更零operation。首次保存嘗試凍結同一operation/after，換token不能沿用identity。草稿三方合併增加workspaceStyle row與完整style比較，明確KeepRemote／UseDraft後產生新guarded operation；Backend contract gap阻擋未支持的樣式提交，retainDraft亦在任何append前拒絕（包含nested transactions），DTO conversion拒絕而不reserve seq／HTTP。

驗證：CanvasStyleOperationTest6cases（history單調／新identity、remote Undo與整數上限、strict serialization與transaction rollback、picker scopes／no-op／個人preferences、backend零Settings寫入／零HTTP、三方合併style conflict與新operation），FileRepo新增真Apply／Undo／Redo檔案reopen。50802最終隔離build成功79s：JVM887（6既有skip）、JS805、Wasm805、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過，更新desktopApp/build/verified/BoarderLess.app並核class。首次fixture漏canEditContent import、使用不存在MapSettings與漏TextNode transform已修正；既有contract-gap test以entries全量集合推斷舊3種改為明確3種，新增style gap另驗。尚無GUI驗收／正式多人樣式同步或方案簿CRUD；沒有新AI安全log，實際Router401未排除。完整六主線goal active，測試資料遷移不作前置。


日期：2026-10-02。對應 Priority 4／BAI-005。

狀態：**Proposal／待後端確認及雙端實作**。目前 APP 背景仍為 per-device preference，後端 projection／snapshot／operation reducer 沒有 canvasStyle。本文不代表已共享或可 Undo；不修改／啟動其他 agent 維護的後端，也不改既定 Priority 1→2→3→4 順序。

## 1. 現有行為與範圍

已對照 `CanvasPreferences.kt`、`CanvasBackground.kt`、`Workspace.kt`、`WorkspaceOperation.kt`、`WorkspaceHistory.kt`、後端 `snapshot.schema.ts`、`operation.schema.ts`、`domain/operations.ts`、`operation.service.ts`。

- APP 的 backgroundToken、showGrid、snapToGrid 現在一起保存在 Settings。色票 hover／拖曳的 canvasBackgroundPreview 是暫態，Apply 才修改本機背景。
- viewport.pan、zoom、showGrid、snapToGrid、theme、Reduce Motion、面板收合與 color picker model **仍為裝置／使用者偏好**，不可送到 Canvas style operation、Presence 或共享 snapshot。
- backgroundToken 與正式 gridStyle 是 workspace 內容，需落盤、catch-up、snapshot、replay、跨裝置同步及 Undo。
- 本機 Theme-aware default／warm／cool 在不同 theme 可能呈現不同 RGB；共享的是語意 token，不保證深淺主題逐像素相同。自訂 hex 則為明確 sRGB。
- 現有 GridCanvas 使用 GridSize=32、density、zoom；物件座標與 grid renderer 尚需確認跨 density 的 world-unit 規則。正式 grid style 不可自行另創一套坐標／吸附規則。

## 2. Projection 與 canonical schema

GET `/api/v1/workspaces/:workspaceId/state` 與每份正式 snapshot 增加 canvasStyle、canvasStyleVersion；兩欄屬於同一 throughServerSeq 的一致 projection，不從另一個無 checkpoint 的 GET 拼接。

```json
{
  "canvasStyle": {
    "schemaVersion": 1,
    "backgroundToken": "default",
    "gridStyle": {"pattern": "lines", "colorToken": "auto"}
  },
  "canvasStyleVersion": 0
}
```

| 欄位 | v1 規則 |
| --- | --- |
| schemaVersion | 固定 1；未知版本 fail closed，不靜默套 default |
| backgroundToken | default／warm／cool，或 lowercase `#[0-9a-f]{6}` |
| gridStyle.pattern | none／lines／dots；完整替換，不接受任意 CSS／shader／SVG |
| gridStyle.colorToken | auto 或 lowercase `#[0-9a-f]{6}`；auto 依背景／theme 維持既有對比策略 |
| canvasStyleVersion | 0..9007199254740991 的單調整數；初始 0，非 workspaceVersion／serverSeq |

v1 不接受 alpha、gradient、image／video URL、binary、HTML、API Key 或任意未知欄位。UI 可接受大小寫 hex，但送出前 canonicalize，server 使用同一正式 schema，不靜默 trim／修正非法 token。grid spacing、origin、線寬與透明度本版不開放自訂，雙端 fixtures 須先固定 renderer 的 world-unit／density 規則；若後續加入欄位需新 schema 與 migration。

showGrid=false 只隱藏自己的正式 grid，不改 gridStyle；pattern=none 則正式內容無 grid，任何 viewer 的 showGrid=true 也不覆蓋。snapToGrid 維持本機，正式 grid 的更新不移動任何已存在物件、不改 viewport；若 snap 依正式 spacing，spacing 擴充前須共同驗證 world-unit，不能只修改 UI 視覺。

## 3. `update_canvas_style` 正式 operation

沿用既有 POST `/api/v1/workspaces/:workspaceId/operations`，以及 Priority 2 的同一 submit service／fan-out。不是另開 PATCH Workspace title endpoint，也不創建假 Canvas object ID。

```json
{
  "protocolVersion": 1,
  "clientId": "00000000-0000-0000-0000-000000000030",
  "transactionId": "00000000-0000-0000-0000-000000000040",
  "baseVersion": 8,
  "operations": [{
    "operationId": "00000000-0000-0000-0000-000000000050",
    "clientSeq": 9,
    "kind": "update_canvas_style",
    "payload": {
      "expectedCanvasStyleVersion": 2,
      "canvasStyle": {
        "schemaVersion": 1,
        "backgroundToken": "#204060",
        "gridStyle": {"pattern": "dots", "colorToken": "auto"}
      }
    }
  }]
}
```

上述 protocolVersion=1 是沿用現有 envelope 的**建議**，新增 kind 的正式支援版本與能力探詢須後端確認，不能因 schema.kind 是 String 就認定 server 支援。operation payload schemaVersion 與 committed schemaVersion 的命名需在共用 fixtures 固定。

- expectedCanvasStyleVersion 必填，不能塞入 expectedObjectVersions 或由缺省推測；server 在鎖定 workspace 的既有 transaction 內檢查 CAS、ACL 與完整 style schema。
- owner／editor 可修改；viewer／commenter 不可修改，與正式 canEditContent 一致。提交時再次檢查權限；client 控制項停用不代替 server ACL。
- CAS 成功原子更新 canvasStyle、canvasStyleVersion+1、既有 workspaceVersion／serverSeq、operation log 與 outbox；含物件／關聯的同批 transaction 任一失敗必須全部 rollback。
- 同 style 的明確提交仍作一次正式接受與 version+1，避免不同 client 對 no-op 的 seq 認定不同；APP Apply 前可避免發送未變內容，但 server 的 duplicate 只以原 transaction／operation identity 判定。
- CAS 失敗回既有 409 conflict envelope 的擴充固定碼 canvas_style_version_conflict，包含 expected／actual style version 與目前權威 checkpoint；不得將 style version 塞入假 objectVersions。具體 conflict schema 待後端交付。
- accepted／duplicate committed event 保留完整 canonical payload、operationId、transactionId、serverSeq、workspaceVersion 與 authoritative style version。duplicate 不再次增加 version／seq，不另建 outbox；重送必須使用原 IDs。
- replay 按正式 seq 執行相同 reducer，重複 seq 不重新執行；snapshot 的 style version 與 throughServerSeq 一致。未知 durable kind／style schema 要求升級或重同步，不略過後繼續宣稱已同步。

## 4. APP Apply、取消、Undo 與遠端競爭

- 背景 picker Preview 只在本裝置暫態顯示，不送 operation、不建立 Undo entry；Cancel 清除 preview，恢復目前權威／optimistic style，而不是開啟 picker 當時的舊背景。
- Apply 捕捉來源 user／client／workspace、base style version、before／after style；一次正式 history operation 與 transaction，不把連續拖曳色票變成大量保存。未變更直接關閉，不送 no-op。
- remote update 在 picker 開啟時到達：保留 draft 但標示權威 style 已改變；Apply 必須比較 base，不能自動換最新 expected version 而覆寫。可提供重新載入最新背景或再次確認自己的 draft。離開 workspace／撤權取消 picker／preview 與舊提交。
- Undo 是新的 update_canvas_style，after 設為該操作的 before；style version **單調增加**，不得把 version 退回歷史值。Redo 同樣為新 operation／IDs，不重送舊 duplicate transaction。
- domain inverse 保留 before／after 值及狀態 guard；WorkspaceHistory rebasing 只可刷新 CAS version，不能移除「目前 style 仍等於預期 after」的 guard。remote 已改背景時拒絕舊 Undo，不能用最新版 CAS 靜默覆蓋別人的內容。
- REST refresh 現有策略會重建 history；正式 style 接入後須明確保留安全 stack 或清除失效 Undo，兩者皆不得覆蓋 remote style。operation 被 server 拒絕時回復 optimistic state／保留 draft，不將 rejection 放入已接受 Undo。
- accepted／duplicate ack 與 authoritative broadcast 以 transaction／operation ID 去重；不能把自己 echo 再套一次，不能把 Preview 當 pending operation。
- viewer 可調整自己的 showGrid／zoom／pan，但不能 Apply 共享背景。缺能力時清楚標示裝置本機背景，不同時顯示「共享／可 Undo」。

## 5. Database、舊資料與 capability rollout

後端建議新增 workspace projection 的 canvas_style JSONB、canvas_style_version BIGINT，固定 schema allowlist，schema migration 對既有工作區採上例穩定 default／0。不可從任何一台 client 的 Settings 選取背景作 server migration，更不能用 viewport blob 當共享樣式。

歷史 snapshot 缺新欄位只在已知舊 snapshot schema 下由 server migration／replay adapter 補 default／0，之後重播正式 style operations；新 schema 缺欄位／null／非法 token 是 contract error，不悄悄 default。snapshot version、checksum 與 migration 必須一併交付；不能修改已簽 checksum 的內容而沿用舊 checksum。

capability 至少宣告 update_canvas_style kind、styleSchemaVersions 與相容 snapshot schema；舊 APP 無 reducer 時應明確不支援／read-only／升級，不可以 ignoreUnknownKeys 假裝已套用。無 capability 的舊 server 繼續 per-device preference，不嘗試新 kind。上線順序需後端 migration／schemas→reducer／log／snapshot→能力宣告→APP adapter／UI；每階段保留可辨識降級。

首次啟用共用樣式時，以 server default 為準。使用者可以明確「套用本機背景到共享畫布」，視為單次一般 CAS operation；不自動從每台裝置各提交一次。舊本機背景保留作降級備份，viewport／showGrid／snapToGrid Settings 原樣保留且不回寫他人。

## 6. 交付 fixtures 與功能 QA

後端交付正式 TypeBox／OpenAPI、DB migration、state／snapshot／conflict／committed fixtures 與測試部署；APP 需新增 domain CanvasStyle／version、UpdateCanvasStyleOperation／inverse／state guard、history rebasing、DTO projection／mapper、capability gate、grid renderer 與 picker Apply。本文本身不完成任何 runtime adapter。

最低回歸：

- default migration、v1 all tokens／custom hex、非法 token／未知 schema／缺欄位、safe integer 邊界、exact checksum；state＝snapshot＋log replay。
- 同一 style Apply、Undo、Redo 的 value 正確且 version／seq 單調；舊 Undo 遇 remote 改動拒絕，不影響物件／關聯／viewport。
- 兩 client 同 expected version 競爭只有一個接受；response 遺失與重複 ack／fan-out 不重複套用；REST fallback／斷線 catch-up 保留背景。
- style＋object 同批部分失敗全部 rollback；viewer／commenter／撤權不能寫；capability 缺失不送新 kind。
- picker Preview／Cancel／Apply、remote-update-open、切畫布、不同 theme／density、不同裝置 showGrid／snap／zoom／pan 保持各自偏好。grid lines／dots 的 world unit 與 snap 規則須雙端共同確認，不能只看單一 mac screenshot。
- 舊裝置 Settings 不自動搶寫 server default；手動移轉只產生一個正式 transaction。未知 snapshot 版本不能冒充同步成功。

視覺、對比、動態島與遮擋由使用者在 QA Workbench 簽核；效能量測維持最後。Mock／單元 tests、JSON 格式檢查不代替真實雙端保存與 Undo。

## 7. 待確認決策

正式 operation／snapshot／capability 版本、conflict 欄位與 committed style version、DB default／checksum migration、舊 client rollout、grid world-unit／density／snap 規則、theme semantic token 的產品簽核。這些仍是實作前整合缺口，不因草案存在解除 BAI-005。
