# AI 圖形表達格式

2026-10-05，使用者決定：**Mermaid flowchart 為 BoarderLess 預設 AI 圖形表達式。只有建立時明確選擇的特殊圖型，才使用對應語法。**不能根據形狀、座標、文字或模型猜測自動改成 mindmap／sequenceDiagram，也不要求先引入 DOT／BPMN。

## 本輪實作

既有一般畫布的 AI 理解驗證改送 `flowchart LR`，不再送儲存用完整 scene JSON。節點為 packet-local N1…，關係為 E1…；原 UUID 對照由 APP 的 immutable snapshot 保留，不輸出。排序只提供穩定 identity、不代表時間。圖形預覽文字／複製驗證包／明確同意後網路送出共用同一內容。

群組轉為 nested subgraph，節點名稱／素材 altText、關係 label 與非泛用 intent 保留；forward／backward／both／none 均反映箭頭。每條線的標籤含 E ID，平行線不合併。backward 在圖上 target→source，精簡 inventory 保留原 source／target／direction 供核對。media 只標示類型及 altText，沒有像素或下載資料。

另外附 `boarderless.ai-flowchart.v1` 精簡 inventory：scope、短 node ID／kind／parentId／parentOutsideScope、短 relation ID／source／target／direction。這是核對附錄，不是另造流程 DSL；讓模型回 JSON 的 inventory／summary／uncertainties 仍便於自動核對，主圖形表達是 Mermaid。

UUID、工作區名稱、版本、zIndex、色彩、鎖定、viewport、自由座標、旋轉角點與交疊矩形不送 AI。布局分析與精確編輯若日後需要，另取得使用者批准並附必要資訊，不恢復整份存檔當默认提示詞。

基本形狀包含 diamond／rounded／rectangle，ellipse 暫用 round node 近似；其餘降為 rectangle。不承諾形狀、自由布局、鋼筆或素材動畫無損轉換；提示詞明示近似與未提供原布局。群組必須合法、不循環，不偷偷將非法 parent 展開或補回 scope 外節點。

文字置於 quoted labels，控制字元／可逃逸語法字元用 Mermaid numeric entity 編碼；Emoji 用完整 Unicode code point。轉換器不輸出 click、URL、HTML、配置 directives 或 animation，不遵循節點中的指令。文字仍可包含私人資料，使用者需審查。

## 特殊圖型的接續規則

目前 Workspace 合約尚無「建立時圖型」metadata，不能假稱特殊類型已保存或同步。本輪僅一般畫布預設 flowchart。後續建立精靈與後端需正式持久化 diagram kind、版本化語意及物件對照：

- 明確建立 sequence：有 participant/message order／branch block 後才輸出 sequenceDiagram。
- 明確建立 mindmap：有根與樹階層後才輸出 mindmap；交叉線無法表達時報轉換限制，不默默丟失。
- 其他明確選定類型：有對應 converter／parser adapter 才支援；缺 converter 需告知，不默默改類型。

UI 不提供臨時「猜圖型」覆寫作為正式保存方式；legacy／自由畫布均 flowchart。

## Parser／驗收

遵循 [Mermaid flowchart 官方語法](https://mermaid.js.org/syntax/flowchart.html)，不另寫語法 parser。Kotlin common exporter／inventory checks 與官方 JavaScript parser 是不同角色；`AiFlowchartParserTest` 提供 opt-in 官方 parser 整合，外部測試腳本位置用 BOARDERLESS_MERMAID_PARSE_SCRIPT 明確配置，不在一般 Gradle test 偷裝 npm 或請求網路。

common tests 覆蓋預設、短 ID、四方向／平行線、群組／素材、惡意字串與 Emoji；官方 parser 額外驗 nested／empty group、group endpoints 與 self-loop 語法（語法可表達不代表 APP／後端目前已支援建立自循環）。syntax pass 不代表流程完整、所有渲染布局正確或模型理解。

重現官方 parser QA：在獨立目錄安裝 mermaid@12.1.0／jsdom@26.1.0，設 BOARDERLESS_MERMAID_RUNTIME 為該目錄、BOARDERLESS_MERMAID_PARSE_SCRIPT 為本 repo scripts/ai-flowchart-parser.mjs 的絕對路徑，再執行 :shared:jvmTest --tests '*AiFlowchartParserTest*'。沒有 env 時該 opt-in test 明確 skip；APP 不因這個 helper 多一套 runtime 依賴。原本空白群組名稱會被官方 parser 拒絕，已改 blank entity 而非補虛構名稱。

本輪沒有加入 APP runtime Mermaid renderer/parser，也沒有 import／AST extractor／round-trip editor；不宣稱 JavaScript API 能直接跑在 native commonMain。後續各端 adapter 固定版本與受限語法，圖片与遮擋依人工 QA。
