# BoarderLess User Story 與 Backlog 樣式

版本：0.1  
對應產品計畫：`docs/PRODUCT_PLAN.md`

## 1. Story 撰寫格式

每一則可進入開發的 Story 使用以下格式：

```markdown
### US-<EPIC>-<流水號>：<成果導向標題>

**身為** <角色>  
**我想要** <可觀察的行為或能力>  
**以便** <使用者價值>

優先級：Must / Should / Could  
目標里程碑：M0 / M1 / M2 / M3 / M4

前置條件：
- ...

驗收條件：
- Given ... When ... Then ...

UX 狀態：
- Empty / Loading / Active / Success / Error / Disabled

非目標：
- ...

依賴：
- ...
```

Story 描述使用者成果，不指定類別名稱或實作方式；技術工作以 Task 掛在 Story 下方。每則 Story 應能獨立展示，並具備可重複驗證的驗收條件。

## 2. Definition of Ready

進入開發前，Story 必須：

- 有單一、清楚的使用者成果。
- 已列出主要成功、空白、載入和錯誤狀態。
- 驗收條件可由測試或人工操作確認。
- 已確認是否影響 Workspace schema 或 undo history。
- AI Story 已定義 context、取消、失敗及使用者確認方式。
- UI Story 已確認屬於 Shell 還是 Content 視覺語言。

## 3. Definition of Done

- 驗收條件全部通過。
- 具備適當的單元、狀態或互動測試。
- 支援鍵盤與基本語意標註。
- Light、Dark、Reduce Transparency 可正常使用。
- 不支援 blur 的平台具有可讀 fallback。
- 可撤銷的 Workspace 操作已進入統一 history。
- 錯誤不造成正式內容遺失。

## 4. Epics

| Epic | 名稱 | 成果 |
|---|---|---|
| DS | Design System | 建立不依賴 Material 3 的共同介面基礎 |
| WS | Workspace | 建立、開啟及恢復工作空間 |
| CV | Canvas | 在無邊際空間中導航與定位 |
| CT | Content | 建立與組織思想內容 |
| HX | History | 安全地撤銷、重做與保存變更 |
| AI | AI Cowork | 讓 AI 以 proposal 方式共同工作 |
| FD | Find & Navigate | 快速找到內容與執行命令 |
| AX | Accessibility | 確保不同能力與裝置環境可操作 |

## 5. MVP User Stories

### US-DS-001：辨識工具與內容

**身為** Workspace 使用者  
**我想要** 一眼辨識哪些是軟體工具、哪些是我的正式內容  
**以便** 專注思考且不誤把控制介面當成內容

優先級：Must  
目標里程碑：M0

驗收條件：

- Given 畫面同時有工具列與思想節點，When 使用者檢視畫面，Then 工具列使用 Shell 毛玻璃語言，思想節點使用清晰的 Content 語言。
- Given AI 建議尚未接受，When 建議出現在 Canvas，Then 它具有明確的暫態 AI 標記。
- Given AI 建議被接受，When operation commit 完成，Then 它不再顯示毛玻璃或 AI 暫態樣式。

UX 狀態：Light、Dark、Reduce Transparency。

非目標：每個平台呈現完全相同的像素效果。

### US-DS-002：在不支援模糊時維持介面層級

**身為** 使用低效能或不支援背景模糊裝置的使用者  
**我想要** 仍然清楚辨識浮動工具  
**以便** 不因視覺效果降級而失去可用性

優先級：Must  
目標里程碑：M0

驗收條件：

- Given 平台 renderer 不支援 backdrop blur，When 顯示 GlassSurface，Then 使用不透明或半透明 tint、邊框與陰影提供相同語意層級。
- Given 使用者啟用 Reduce Transparency，When 任何玻璃介面出現，Then 文字對比符合產品設定且不依賴背景模糊。

### US-WS-001：建立空白 Workspace

**身為** 第一次使用者  
**我想要** 快速建立空白 Workspace  
**以便** 不需先設定分類就能記錄想法

優先級：Must  
目標里程碑：M2

驗收條件：

- Given 使用者位於起始頁，When 選擇新增 Workspace，Then 在可操作 Canvas 中建立本機 Workspace。
- Given 建立成功，When Canvas 顯示，Then 焦點允許立即建立第一個思想節點。
- Given 儲存失敗，When Workspace 無法建立，Then 顯示可重試錯誤且不呈現已成功保存的假象。

### US-WS-002：重新開啟既有 Workspace

**身為** 回訪使用者  
**我想要** 回到上次工作的內容與位置  
**以便** 延續思考而不必重新定位

優先級：Must  
目標里程碑：M2

驗收條件：

- Given Workspace 已保存，When 重新開啟，Then 恢復內容、關係、群組與最後有效 viewport。
- Given Workspace 資料無法完整解析，When 開啟失敗，Then 保留原檔並提供可理解的錯誤，不覆寫資料。

### US-CV-001：平移與縮放 Canvas

**身為** 正在探索大量想法的使用者  
**我想要** 自由平移及縮放工作空間  
**以便** 同時掌握局部內容與整體結構

優先級：Must  
目標里程碑：M1

驗收條件：

- Given Canvas 有內容，When 使用滑鼠、觸控板或對應平台手勢，Then viewport 以穩定幀率平移或縮放。
- Given 游標位於某個世界座標，When 進行縮放，Then 該座標在畫面上的相對焦點保持合理穩定。
- Given 使用者迷失位置，When 執行 Fit Content，Then 所有正式內容進入可視範圍。

### US-CT-001：快速建立思想節點

**身為** 正在捕捉想法的使用者  
**我想要** 在 Canvas 指定位置建立並立即輸入文字  
**以便** 不讓操作打斷思考

優先級：Must  
目標里程碑：M1

驗收條件：

- Given Canvas 可編輯，When 使用建立命令或雙擊空白處，Then 在目標世界座標建立文字節點並進入編輯。
- Given 新節點尚無內容，When 使用者取消編輯，Then 不留下無意義的空節點。
- Given 使用者完成輸入，When 提交編輯，Then 節點進入 Workspace history 並觸發自動儲存。

### US-CT-002：選取與移動內容

**身為** 正在整理想法的使用者  
**我想要** 單選、多選並移動內容  
**以便** 依照逐漸形成的結構重新排列思想

優先級：Must  
目標里程碑：M1

驗收條件：

- Given Canvas 有多個節點，When 點擊、框選或使用修飾鍵選取，Then selection 清楚顯示但不改變正式內容樣式。
- Given 多個節點已選取，When 拖曳其中之一，Then 所有選取節點保持相對位置移動。
- Given 移動完成，When 使用 Undo，Then 所有節點回到移動前位置。

### US-CT-003：建立思想關係

**身為** 正在形成觀點的使用者  
**我想要** 連結兩個思想節點  
**以便** 表達因果、支持、衝突或一般關聯

優先級：Must  
目標里程碑：M1

驗收條件：

- Given 來源節點已選取，When 將 connector 連到目標節點，Then 建立可持續追蹤兩端位置的 relation。
- Given 任一端節點移動，When Canvas 重繪，Then relation 保持正確連接。
- Given 節點將被刪除，When 操作執行，Then 相關 relation 的處理結果在確認或 undo 模型中明確定義。

### US-CT-004：群組相關思想

**身為** 已累積多個想法的使用者  
**我想要** 將節點整理成具名群組  
**以便** 建立中階結構而不必轉成固定文件

優先級：Must  
目標里程碑：M1

驗收條件：

- Given 多個節點已選取，When 執行 Group，Then 建立包含這些節點的可命名 frame。
- Given 群組被移動，When 操作完成，Then 群組內節點維持相對位置。
- Given 群組被解除，When Ungroup 完成，Then 內容保留且可獨立操作。

### US-HX-001：撤銷與重做內容操作

**身為** 探索中的使用者  
**我想要** 撤銷及重做內容變更  
**以便** 大膽嘗試而不怕破壞工作成果

優先級：Must  
目標里程碑：M1

驗收條件：

- Given 已完成可逆操作，When 執行 Undo，Then Workspace 回到操作前一致狀態。
- Given 已撤銷操作且沒有新分支操作，When 執行 Redo，Then 恢復該操作。
- Given AI proposal 已被接受，When 執行 Undo，Then AI 寫入的 operations 依其 transaction 邊界撤銷。

### US-HX-002：自動保存工作成果

**身為** 長時間思考的使用者  
**我想要** 系統可靠地自動保存  
**以便** 不必分心管理存檔

優先級：Must  
目標里程碑：M2

驗收條件：

- Given Workspace 發生正式變更，When debounce 時間到達或應用進入背景，Then 以一致 transaction 保存。
- Given 保存進行中，When 使用者繼續編輯，Then 新操作不被錯誤標記為已保存。
- Given 保存失敗，When 狀態更新，Then 使用者看見非阻塞警示及重試狀態。

### US-AI-001：以選取內容邀請 AI 協作

**身為** 遇到瓶頸的使用者  
**我想要** 對目前選取的內容提出 AI 任務  
**以便** 得到與當前思考直接相關的協助

優先級：Must  
目標里程碑：M3

驗收條件：

- Given 一個以上內容物件已選取，When 開啟 AI Cowork，Then 清楚顯示即將提供給 AI 的 context 範圍。
- Given 使用者送出任務，When AI 回應串流中，Then 使用者可繼續檢視 Workspace 並可停止生成。
- Given 沒有選取內容，When 開啟 AI Cowork，Then 介面明確說明將使用的替代 context，不暗中送出整個 Workspace。

### US-AI-002：預覽 AI 建議

**身為** 重視內容控制權的使用者  
**我想要** 在正式修改前預覽 AI 的建議  
**以便** 判斷結果是否符合意圖

優先級：Must  
目標里程碑：M3

驗收條件：

- Given AI 產生結構化 operations，When proposal 準備完成，Then Canvas 以暫態樣式呈現新增、修改和刪除影響。
- Given proposal 仍待確認，When Workspace 自動保存，Then proposal 不被保存為正式內容。
- Given 目標內容在生成期間已被修改，When proposal 完成，Then 系統標記版本衝突，不直接套用過期變更。

### US-AI-003：接受或拒絕 AI 建議

**身為** Workspace 擁有者  
**我想要** 全部或部分接受 AI 建議，也能拒絕其餘部分  
**以便** 最終內容仍由我決定

優先級：Must  
目標里程碑：M3

驗收條件：

- Given proposal 包含多個 operations，When 使用者選擇部分接受，Then 只提交所選 operations。
- Given operation 被接受，When commit 成功，Then 內容轉為一般 Content 樣式並進入 undo history。
- Given proposal 被拒絕，When 關閉 review，Then 正式 Workspace 沒有任何相關變更。

### US-AI-004：處理 AI 失敗與取消

**身為** 使用 AI 協作的使用者  
**我想要** 安全停止請求並從錯誤中恢復  
**以便** 網路或供應商問題不會破壞工作

優先級：Must  
目標里程碑：M3

驗收條件：

- Given 回應正在串流，When 使用者停止，Then 請求被取消且正式內容不變。
- Given provider 回傳錯誤，When 介面顯示失敗，Then 提供可重試資訊並保留使用者 prompt。
- Given 回應只有部分內容，When 發生失敗，Then 未驗證的文字不轉成 Workspace operations。

### US-FD-001：使用 Command Palette 執行操作

**身為** 鍵盤導向的使用者  
**我想要** 從單一入口搜尋並執行命令  
**以便** 不需在多個面板間移動

優先級：Should  
目標里程碑：M4

驗收條件：

- Given Workspace 已開啟，When 使用快捷鍵叫出 Command Palette，Then 焦點進入搜尋欄且 Canvas selection 保留。
- Given 使用者輸入命令關鍵字，When 結果更新，Then 顯示可執行命令及其快捷鍵。
- Given 命令目前不可用，When 結果出現，Then 顯示原因或不列出，不靜默失敗。

### US-FD-002：搜尋並定位內容

**身為** 擁有大型 Workspace 的使用者  
**我想要** 搜尋文字並定位到對應內容  
**以便** 快速回到需要處理的思想

優先級：Should  
目標里程碑：M4

驗收條件：

- Given Workspace 有多個文字節點，When 輸入查詢，Then 結果依內容匹配更新。
- Given 使用者選擇結果，When 導航執行，Then viewport 將目標帶入可視範圍並短暫標示。

### US-AX-001：只用鍵盤操作核心流程

**身為** 鍵盤使用者  
**我想要** 不依賴滑鼠完成主要操作  
**以便** 高效率工作或在無法使用指標裝置時仍可操作

優先級：Must  
目標里程碑：M4

驗收條件：

- Given Workspace 已開啟，When 只使用鍵盤，Then 可建立節點、完成編輯、移動焦點、開啟 AI Cowork、接受或拒絕 proposal。
- Given 焦點位於任何 Shell 控制項，When 使用 Tab 或方向鍵，Then 焦點順序可預測且具有可見指示。
- Given 使用螢幕閱讀器，When 聚焦節點或控制項，Then 能取得名稱、角色與主要狀態。

## 6. 建議的第一個 Sprint

Sprint 目標：證明「玻璃工具層＋原始內容層」可以在 KMP 中形成穩定產品基礎。

納入：

- US-DS-001 辨識工具與內容。
- US-DS-002 模糊降級。
- US-CV-001 的 viewport 技術 spike。
- US-CT-001 的最小建立／編輯流程。

技術 Tasks：

- 移除 `App.kt` 對 Material 3 Theme、Button、Text 的使用。
- 建立 Shell 與 Content tokens。
- 建立 `GlassRenderer` contract、Skia/fallback implementation。
- 建立示範 Workspace screen。
- 加入 Light、Dark、Reduce Transparency previews。
- 在 Desktop、Android、iOS、Web 執行編譯驗證。

Sprint Review 應展示：

1. 使用者能在 Canvas 建立一個原始樣式文字節點。
2. 使用者能透過浮動玻璃工具列觸發建立操作。
3. 關閉透明效果後仍保有清楚層級。
4. 相同產品語意可在各平台 renderer 下成立。

