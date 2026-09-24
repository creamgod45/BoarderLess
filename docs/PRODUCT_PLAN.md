# BoarderLess 產品與介面計畫書

版本：0.1  
狀態：產品假設草案，供設計與 MVP 開發使用

## 1. 產品摘要

BoarderLess 是一個以無邊際畫布為核心的思想工作空間。使用者能在同一個空間中記錄想法、組織關係、發展計畫，並邀請 AI 以可觀察、可撤銷、由使用者決定是否採用的方式共同工作。

產品的核心區分是：

- 毛玻璃代表「軟體提供的工具與控制介面」。
- 清晰、原始的內容代表「使用者擁有的思想與產出」。
- AI 尚未被接受的內容屬於暫態建議；接受後才轉為一般使用者內容。

## 2. 問題定義

現有筆記工具通常要求使用者先選擇文件、資料夾或固定結構；白板工具雖然自由，卻常缺乏長期知識管理及可控的 AI 協作。BoarderLess 要解決三個問題：

1. 想法尚未成形時，不應被固定文件結構限制。
2. AI 不應只是聊天視窗，而應理解目前工作空間並提出可操作的協作結果。
3. AI 的行動必須透明、可預覽、可接受或拒絕，不能模糊內容所有權。

## 3. 產品願景

讓使用者在一個不限制空間與結構的介面中思考，並把 AI 變成可共同觀察、整理和推進工作的夥伴，而不是替使用者接管內容的黑盒子。

### 3.1 核心價值

- 自由：先捕捉思想，再決定結構。
- 清晰：內容、工具與 AI 建議有明確的視覺邊界。
- 可控：所有 AI 變更都能預覽、接受、拒絕與撤銷。
- 連續：從零散想法到可執行計畫不必切換工具。
- 所有權：正式內容永遠屬於使用者，且不依賴單一 AI 供應商。

## 4. 首要使用者

### 4.1 創作者與研究者

需要收集零散材料、探索關係，並逐步形成文章、研究或作品。

### 4.2 產品與專案規劃者

需要從目標、問題、方案一路發展成任務與決策紀錄。

### 4.3 個人知識工作者

需要一個低摩擦空間記錄、回顧及重組長期思考。

## 5. Jobs to Be Done

- 當我突然有一個想法時，我想立即把它放進空間，而不必先決定分類。
- 當想法開始增加時，我想自由移動、群組及連結它們，以找出結構。
- 當我卡住時，我想讓 AI 根據我選取的內容提出下一步，而不是收到泛泛的聊天回答。
- 當 AI 建議修改內容時，我想先看見影響範圍，再決定是否採用。
- 當探索告一段落時，我想把現有思想整理成計畫、摘要或可執行事項。

## 6. 體驗與視覺原則

### 6.1 內容與介面分層

```text
Transient Layer
  AI 建議、選取框、拖曳提示、選單、通知

Application Shell
  工具列、側欄、Inspector、AI Cowork 控制面板
  使用毛玻璃語言

Workspace Content
  思想節點、文字、圖片、連線、群組、文件
  使用清晰且可攜的原始樣式

Canvas
  無邊際座標空間、網格、背景
```

### 6.2 毛玻璃使用規則

毛玻璃只用於軟體功能層，不用於正式內容本身。

適用：

- 全域工具列與導覽。
- Inspector 與 AI Cowork Panel。
- Command Palette、選單和暫態控制項。
- 浮動縮放器、Minimap 與狀態顯示。

不適用：

- 思想節點、文件頁面及使用者輸入文字。
- 圖片、附件、連接線和正式群組。
- AI 產生且已被使用者接受的內容。
- 大面積 Canvas 背景。

### 6.3 AI 狀態語言

- 建議中：帶 AI accent 的輪廓、低透明度預覽或虛線。
- 執行中：顯示作用範圍、進度與停止操作。
- 待確認：明確提供接受、拒絕及局部套用。
- 已接受：移除 AI 暫態樣式，轉成一般內容。
- 失敗：保留原始內容，顯示可重試且不阻塞畫布的訊息。

### 6.4 可及性與降級

- 支援 Reduce Transparency：毛玻璃改為高對比不透明表面。
- 文字和圖示不能只依靠背景模糊維持可讀性。
- 所有主要操作可由鍵盤完成。
- AI 狀態不能只以顏色區分。
- 平台不支援即時背景模糊時，使用 tint、border、highlight 與 shadow 保留層次。

## 7. 資訊架構

### 7.1 Workspace

- Workspace metadata
- Infinite canvas viewport
- Content objects
- Relations
- Groups
- Activity/history
- AI sessions and proposals

### 7.2 首版內容物件

- Text node
- Checklist node
- Image/attachment node
- Group/frame
- Relation/connector

MVP 優先完成 Text node、Group 和 Relation；其他物件在核心互動穩定後加入。

### 7.3 主要介面區域

- Center：無邊際 Canvas。
- Top/Center：Context Toolbar。
- Left：Workspace navigation，可收合。
- Right：Inspector / AI Cowork Panel，可切換。
- Bottom/Corner：Zoom、定位與同步狀態。
- Overlay：Command Palette、搜尋、AI Proposal Review。

## 8. MVP 範圍

### 8.1 必須具備

- 建立及開啟本機 Workspace。
- Canvas 平移、縮放、重設視角。
- 建立、編輯、選取、移動及刪除文字節點。
- 多選、群組和節點連線。
- Undo / redo。
- 自動儲存與重新開啟。
- 根據使用者選取內容啟動 AI 任務。
- 串流顯示 AI 回應。
- AI 建議以 proposal 呈現，可接受、拒絕或撤銷。
- 自製 Shell Design System，不使用 Material 3 元件作為產品 UI。
- Glass fallback 與 Reduce Transparency。

### 8.2 明確不在 MVP

- 即時多人協作。
- 完整插件市場。
- 任意程式碼執行代理。
- 跨裝置雲端同步。
- 複雜簡報和排版工具。
- 自動執行不可撤銷的外部操作。

## 9. 技術與模組方向

### 9.1 平台策略

- Desktop JVM：MVP 主要平台，優先完成鍵盤、滑鼠與大型 Canvas 體驗。
- Android/iOS：共用領域模型與基礎 UI，第二階段調整觸控互動。
- Web：保留編譯目標；待 Canvas 與儲存抽象穩定後納入產品驗證。

### 9.2 建議模組邊界

```text
shared
├── domain
│   ├── workspace
│   ├── content
│   ├── history
│   └── ai
├── data
│   ├── persistence
│   └── ai-provider
├── design-system
│   ├── tokens
│   ├── shell
│   ├── content
│   └── glass-renderer
├── feature
│   ├── canvas
│   ├── editor
│   ├── inspector
│   └── ai-cowork
└── app
```

### 9.3 Design System 邊界

- `ShellTheme`：毛玻璃、工具控制、overlay。
- `ContentTheme`：Canvas 內容及正式資料。
- `GlassRenderer`：平台可替換渲染能力。
- `InteractionPrimitives`：focus、hover、press、selection、drag。
- 產品 feature 不直接存取平台 blur API。

### 9.4 AI 領域模型

AI 不直接修改 Workspace，而是產生 proposal：

```text
User Intent
  -> Context Snapshot
  -> AI Request
  -> Streaming Response
  -> Proposed Operations
  -> User Review
  -> Commit to Workspace History
```

每個 proposed operation 至少包含：

- 唯一識別碼。
- 目標物件及其版本。
- 建議動作與內容。
- 建議原因或摘要。
- 接受狀態。
- 可逆操作所需資料。

## 10. 里程碑

### M0：Foundation Spike

- 移除範例畫面對 Material 3 的依賴。
- 建立 tokens、ShellTheme、ContentTheme。
- 建立 GlassSurface 和 fallback renderer。
- 驗證 Desktop、Android、iOS、Web 均能編譯。

完成標準：同一個示範畫面能呈現 Canvas、原始內容節點和玻璃工具列，並可切換 Reduce Transparency。

### M1：Canvas Core

- Viewport transform。
- 建立、選取、移動及編輯文字節點。
- 多選、群組、連線。
- Undo / redo。

完成標準：使用者能在單一工作階段完成一張可編輯思想圖。

### M2：Persistence

- Workspace schema。
- 自動儲存、開啟與資料遷移基礎。
- 異常關閉後復原。

完成標準：重新啟動後能恢復內容及 viewport，且不遺失最後一次已確認操作。

### M3：AI Cowork

- Provider abstraction。
- 選取內容作為 context。
- 串流回應與取消。
- Proposal preview、接受、拒絕與撤銷。

完成標準：AI 能對選取節點提出結構化修改，且未經接受不會變更正式內容。

### M4：Product Validation

- Onboarding 和空狀態。
- 搜尋與 Command Palette。
- 效能、鍵盤及可及性整理。
- 封閉測試與回饋收集。

## 11. 成功指標

MVP 不以註冊數為主要指標，而觀察核心價值是否成立：

- Time to First Thought：進入 Workspace 到建立第一個節點的時間。
- Structure Completion：建立至少一個 relation 或 group 的工作階段比例。
- AI Proposal Acceptance：AI proposal 被全部或部分接受的比例。
- AI Reversal Rate：接受後立即撤銷的比例，用來識別建議品質問題。
- Return to Workspace：使用者重新開啟既有 Workspace 的比例。
- Crash-free sessions 與自動儲存成功率。

## 12. 主要風險與控制

| 風險 | 影響 | 控制方式 |
|---|---|---|
| 即時 blur 造成效能下降 | Canvas 卡頓 | 限制玻璃面積、快取、fallback、效能模式 |
| 平台外觀差異過大 | 品牌不一致 | 共用語意 tokens，允許 renderer 不同但層級一致 |
| Canvas 模型與 UI 強耦合 | 後續功能難擴充 | domain 使用世界座標，不保存 Compose 狀態 |
| AI 誤改正式內容 | 使用者失去信任 | proposal-first、版本檢查、明確確認、undo |
| AI context 過大或洩漏 | 成本與隱私風險 | 可視 context 範圍、送出前摘要、provider policy |
| 過早支援所有平台 | 延遲核心體驗 | Desktop-first，其他平台維持編譯與模型一致 |

## 13. 待驗證產品決策

- Workspace 是否預設完全離線，AI 功能才需要網路。
- AI provider 是使用者自帶金鑰、產品代管，或兩者並存。
- 使用者內容的「原始樣式」是否允許自訂主題與嵌入 HTML/Markdown。
- 第一版是否只支援自由節點，或同時提供文件視圖。
- 行動版定位為完整編輯器或快速捕捉與檢視工具。

