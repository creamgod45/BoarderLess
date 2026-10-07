# BoarderLess User Story 與 Backlog 樣式

版本：0.2
對應產品計畫：`docs/PRODUCT_PLAN.md`

## 1. Story 撰寫格式

每一則可進入開發的 Story 使用以下格式：

```markdown
### US-<EPIC>-<流水號>：<成果導向標題>

**身為** <角色>  
**我想要** <可觀察的行為或能力>  
**以便** <使用者價值>

優先級：Must / Should / Could  
目標里程碑：M0 / M1A / M1B / M2 / M3 / M4 / M5 / M6 / M7 / M8

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
| LB | Library | 從物品庫建立內容並重用使用者方案 |
| MD | Media | 在 Workspace 中可靠管理圖片、GIF 與影片 |
| CL | Collaboration | 讓多人安全地共同編輯同一 Workspace |

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
目標里程碑：M1A

驗收條件：

- Given Canvas 有內容，When 使用滑鼠、觸控板或對應平台手勢，Then viewport 以穩定幀率平移或縮放。
- Given 游標位於某個世界座標，When 進行縮放，Then 該座標在畫面上的相對焦點保持合理穩定。
- Given 使用者迷失位置，When 執行 Fit Content，Then 所有正式內容進入可視範圍。

### US-CT-001：快速建立思想節點

**身為** 正在捕捉想法的使用者  
**我想要** 在 Canvas 指定位置建立並立即輸入文字  
**以便** 不讓操作打斷思考

優先級：Must  
目標里程碑：M1B

驗收條件：

- Given Canvas 可編輯，When 使用建立命令或雙擊空白處，Then 在目標世界座標建立文字節點並進入編輯。
- Given 新節點尚無內容，When 使用者取消編輯，Then 不留下無意義的空節點。
- Given 使用者完成輸入，When 提交編輯，Then 節點進入 Workspace history 並觸發自動儲存。

### US-CT-002：選取與移動內容

**身為** 正在整理想法的使用者  
**我想要** 單選、多選並移動內容  
**以便** 依照逐漸形成的結構重新排列思想

優先級：Must  
目標里程碑：M1B

驗收條件：

- Given Canvas 有多個節點，When 點擊、框選或使用修飾鍵選取，Then selection 清楚顯示但不改變正式內容樣式。
- Given 多個節點已選取，When 拖曳其中之一，Then 所有選取節點保持相對位置移動。
- Given 移動完成，When 使用 Undo，Then 所有節點回到移動前位置。

### US-CT-003：建立思想關係

**身為** 正在形成觀點的使用者  
**我想要** 連結兩個思想節點  
**以便** 表達因果、支持、衝突或一般關聯

優先級：Must  
目標里程碑：M3

驗收條件：

- Given 來源節點已選取，When 將 connector 連到目標節點，Then 建立可持續追蹤兩端位置的 relation。
- Given 任一端節點移動，When Canvas 重繪，Then relation 保持正確連接。
- Given 節點將被刪除，When 操作執行，Then 相關 relation 的處理結果在確認或 undo 模型中明確定義。

### US-CT-004：群組相關思想

**身為** 已累積多個想法的使用者  
**我想要** 將節點整理成具名群組  
**以便** 建立中階結構而不必轉成固定文件

優先級：Must  
目標里程碑：M3

驗收條件：

- Given 多個節點已選取，When 執行 Group，Then 建立包含這些節點的可命名 frame。
- Given 群組被移動，When 操作完成，Then 群組內節點維持相對位置。
- Given 群組被解除，When Ungroup 完成，Then 內容保留且可獨立操作。

### US-HX-001：撤銷與重做內容操作

**身為** 探索中的使用者  
**我想要** 撤銷及重做內容變更  
**以便** 大膽嘗試而不怕破壞工作成果

優先級：Must  
目標里程碑：M1B

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
目標里程碑：M6

驗收條件：

- Given 一個以上內容物件已選取，When 開啟 AI Cowork，Then 清楚顯示即將提供給 AI 的 context 範圍。
- Given 使用者送出任務，When AI 回應串流中，Then 使用者可繼續檢視 Workspace 並可停止生成。
- Given 沒有選取內容，When 開啟 AI Cowork，Then 介面明確說明將使用的替代 context，不暗中送出整個 Workspace。

### US-AI-002：預覽 AI 建議

**身為** 重視內容控制權的使用者  
**我想要** 在正式修改前預覽 AI 的建議  
**以便** 判斷結果是否符合意圖

優先級：Must  
目標里程碑：M6

驗收條件：

- Given AI 產生結構化 operations，When proposal 準備完成，Then Canvas 以暫態樣式呈現新增、修改和刪除影響。
- Given proposal 仍待確認，When Workspace 自動保存，Then proposal 不被保存為正式內容。
- Given 目標內容在生成期間已被修改，When proposal 完成，Then 系統標記版本衝突，不直接套用過期變更。

### US-AI-003：接受或拒絕 AI 建議

**身為** Workspace 擁有者  
**我想要** 全部或部分接受 AI 建議，也能拒絕其餘部分  
**以便** 最終內容仍由我決定

優先級：Must  
目標里程碑：M6

驗收條件：

- Given proposal 包含多個 operations，When 使用者選擇部分接受，Then 只提交所選 operations。
- Given operation 被接受，When commit 成功，Then 內容轉為一般 Content 樣式並進入 undo history。
- Given proposal 被拒絕，When 關閉 review，Then 正式 Workspace 沒有任何相關變更。

### US-AI-004：處理 AI 失敗與取消

**身為** 使用 AI 協作的使用者  
**我想要** 安全停止請求並從錯誤中恢復  
**以便** 網路或供應商問題不會破壞工作

優先級：Must  
目標里程碑：M6

驗收條件：

- Given 回應正在串流，When 使用者停止，Then 請求被取消且正式內容不變。
- Given provider 回傳錯誤，When 介面顯示失敗，Then 提供可重試資訊並保留使用者 prompt。
- Given 回應只有部分內容，When 發生失敗，Then 未驗證的文字不轉成 Workspace operations。

### US-FD-001：使用 Command Palette 執行操作

**身為** 鍵盤導向的使用者  
**我想要** 從單一入口搜尋並執行命令  
**以便** 不需在多個面板間移動

優先級：Should  
目標里程碑：M7

驗收條件：

- Given Workspace 已開啟，When 使用快捷鍵叫出 Command Palette，Then 焦點進入搜尋欄且 Canvas selection 保留。
- Given 使用者輸入命令關鍵字，When 結果更新，Then 顯示可執行命令及其快捷鍵。
- Given 命令目前不可用，When 結果出現，Then 顯示原因或不列出，不靜默失敗。

### US-FD-002：搜尋並定位內容

**身為** 擁有大型 Workspace 的使用者  
**我想要** 搜尋文字並定位到對應內容  
**以便** 快速回到需要處理的思想

優先級：Should  
目標里程碑：M7

驗收條件：

- Given Workspace 有多個文字節點，When 輸入查詢，Then 結果依內容匹配更新。
- Given 使用者選擇結果，When 導航執行，Then viewport 將目標帶入可視範圍並短暫標示。

### US-AX-001：只用鍵盤操作核心流程

**身為** 鍵盤使用者  
**我想要** 不依賴滑鼠完成主要操作  
**以便** 高效率工作或在無法使用指標裝置時仍可操作

優先級：Must  
目標里程碑：M7

驗收條件：

- Given Workspace 已開啟，When 只使用鍵盤，Then 可建立節點、完成編輯、移動焦點、開啟 AI Cowork、接受或拒絕 proposal。
- Given 焦點位於任何 Shell 控制項，When 使用 Tab 或方向鍵，Then 焦點順序可預測且具有可見指示。
- Given 使用螢幕閱讀器，When 聚焦節點或控制項，Then 能取得名稱、角色與主要狀態。

## 6. 擴充 Backlog

### US-CV-002：切換網格與吸附規則

**身為** 同時需要自由探索與精確排列的使用者
**我想要** 分別控制網格顯示與物件吸附
**以便** 依工作階段選擇自由移動或整齊排列

優先級：Should
目標里程碑：M4

驗收條件：

- Given 網格隱藏，When 啟用網格吸附並移動物件，Then 物件仍依指定間距吸附。
- Given 網格顯示，When 關閉所有吸附並移動物件，Then 物件保持自由座標。
- Given 吸附生效，When 使用暫時略過快捷鍵，Then 本次拖曳不套用吸附。

### US-CV-003：自訂 Canvas 背景

**身為** 希望區分不同工作空間的使用者
**我想要** 調整 Canvas 背景與網格樣式
**以便** 建立適合內容與閱讀環境的視覺基底

優先級：Could
目標里程碑：M4

驗收條件：

- Given Workspace 已開啟，When 使用者選擇背景顏色或支援的網格樣式，Then Canvas 立即預覽並以可撤銷操作保存設定。
- Given 背景可能降低內容對比，When 設定套用，Then selection、工具與主要文字仍維持可讀性。

### US-CT-005：表達連接線的方向與意圖

**身為** 正在形成觀點的使用者
**我想要** 指定關係方向、意圖與標籤
**以便** 區分因果、支持、衝突、延伸或一般關聯

優先級：Must
目標里程碑：M3

驗收條件：

- Given 兩個節點已連接，When 使用者設定方向和意圖，Then relation domain 同時保存來源、目標、意圖與可選標籤。
- Given 任一節點移動或調整大小，When Canvas 重繪，Then connector 仍連接正確端點且箭頭方向不變。
- Given relation 被複製、保存或撤銷，When 操作完成，Then 其語意資料不遺失。

### US-CT-006：鎖定物件並調整層級

**身為** 正在整理複雜 Canvas 的使用者
**我想要** 鎖定背景物件並控制前後層級
**以便** 編輯其他內容時不會誤移重要配置

優先級：Should
目標里程碑：M3

驗收條件：

- Given 物件已鎖定，When 一般選取或拖曳發生，Then 物件不被移動或調整大小。
- Given 多個物件重疊，When 執行前移、後移、置頂或置底，Then hit testing 與顯示順序依新 z-order 更新。
- Given 鎖定或層級改變已完成，When 使用 Undo，Then 恢復先前狀態。

### US-CT-007：調整物件尺寸與旋轉

**身為** 使用視覺方式整理內容的使用者
**我想要** 透過控制點調整物件大小與角度
**以便** 建立符合內容重點的版面

優先級：Should
目標里程碑：M4

驗收條件：

- Given 一個可變形物件已選取，When 拖曳縮放或旋轉控制點，Then 提供即時預覽並在提交後建立單一 history operation。
- Given 多個物件已選取，When 執行共同 transform，Then 維持彼此相對位置並可一次撤銷。
- Given 物件型別不支援某種 transform，When 使用者選取它，Then 不顯示無效控制點。

### US-CT-008：使用 Inspector 編輯外觀與進階屬性

**身為** 需要調整內容表達的使用者
**我想要** 從 Inspector 修改 Node 顏色及型別相關屬性
**以便** 不必離開 Canvas 就能強調與整理內容

優先級：Should
目標里程碑：M4

驗收條件：

- Given 一個或多個相容 Node 已選取，When 修改顏色或共同屬性，Then Canvas 即時預覽並以可撤銷 operation 提交。
- Given 多選內容的屬性值不同，When Inspector 顯示，Then 使用 mixed state，不以任一物件值覆蓋其他物件。
- Given 選取型別不支援某項屬性，When Inspector 顯示，Then 該控制項隱藏或明確 disabled。

### US-HX-003：使用系統剪貼簿重用內容

**身為** 正在快速排列思想的使用者
**我想要** 剪下、複製、貼上與 duplicate 物件
**以便** 快速重用既有結構

優先級：Must
目標里程碑：M3

驗收條件：

- Given 選取內容包含節點、群組或其內部關係，When 複製並貼上，Then 建立新 ID 且保留副本內部關係。
- Given 貼上位置未指定，When 操作完成，Then 副本相對原內容偏移並被選取。
- Given 貼上建立多個物件，When Undo，Then 整批副本以單一 transaction 移除。

### US-LB-001：從物品庫拖曳建立內容

**身為** 想快速開始編排的使用者
**我想要** 從物品庫拖曳內建元件到 Canvas
**以便** 不必逐一設定常用物件

優先級：Should
目標里程碑：M5

驗收條件：

- Given 物品庫已開啟，When 元件被拖到 Canvas，Then 在放置的世界座標建立正式物件並進入 selection。
- Given 拖曳在 Canvas 外取消，When 指標放開，Then Workspace 不新增物件或 history entry。
- Given 元件不適用目前平台，When 物品庫顯示，Then 它具有明確 disabled 狀態或不出現。

### US-LB-002：保存與插入快速方案

**身為** 經常重用自己配置的使用者
**我想要** 把選取物件保存成快速方案
**以便** 在目前或其他 Workspace 重建相同結構

優先級：Should
目標里程碑：M5

驗收條件：

- Given 一組物件已選取，When 保存為快速方案，Then 方案包含相對位置、支援樣式、內部關係、縮圖與 schema version。
- Given 使用者插入方案，When 放置完成，Then 所有插入物件取得新 ID，且不與來源物件共享可變狀態。
- Given 舊版方案可遷移，When 使用者插入它，Then 系統先完成 migration；無法遷移時不破壞原方案。
- Given 使用者在另一台裝置建立或修改方案，When 同一帳號同步，Then 方案依 revision 更新且並行修改不會靜默覆蓋。

### US-LB-003：搜尋並插入大型圖形庫

**身為** 使用圖表整理複雜概念的使用者

**我想要** 從分類完整的物件庫搜尋及插入多種圖形與文字框

**以便** 建立流程圖、組織圖、架構圖、關係圖、拓樸圖與自由註解

優先級：Should

目標里程碑：M5

驗收條件：

- Given 物件庫已開啟，When 依名稱、別名或分類搜尋，Then 可找到基本幾何、流程、箭頭、標註、容器、架構、網路與文字類型。
- Given 使用者插入文字輸入框，When 輸入完成，Then 文字可無卡片外框存在，並支援 selection、transform、clipboard、history 與 collaboration。
- Given 使用者收藏或使用圖形，When 再次開啟物件庫，Then 可從收藏或最近使用快速取得。
- Given 內建圖形版本升級，When 開啟舊 Workspace，Then 原有 shape token 仍能穩定渲染或明確遷移。

### US-LB-004：以鋼筆工具建立自訂形狀

**身為** 需要特殊視覺語彙的使用者

**我想要** 以鋼筆工具畫出並編輯自己的向量形狀

**以便** 不受內建圖形限制

優先級：Should

目標里程碑：M5

驗收條件：

- Given 鋼筆工具啟用，When 使用者建立直線或曲線節點並完成／關閉路徑，Then 產生可選取、移動、縮放、旋轉的正式物件。
- Given 自訂路徑已選取，When 編輯 anchor、control point、填色或描邊，Then 每次正式修改可 Undo／Redo。
- Given 自訂形狀被複製、加入群組、存成快速方案或多人同步，When 重新開啟，Then 路徑與樣式保持一致。
- Given 匯入或同步的路徑包含非有限座標、過多 commands 或可執行內容，When 驗證，Then 安全拒絕且不影響既有 Workspace。

### US-MD-001：加入並保存圖片

**身為** 使用視覺材料思考的使用者
**我想要** 將圖片放入 Canvas
**以便** 把參考素材與文字想法放在同一空間

優先級：Should
目標里程碑：M5

驗收條件：

- Given 使用者選擇或拖入支援圖片，When 匯入成功，Then 建立可移動與調整大小的 Image node。
- Given Workspace 重新開啟，When 原始匯入路徑不可用，Then 仍能從 Workspace asset storage 載入或顯示可恢復的遺失狀態。
- Given 圖片解碼失敗，When 錯誤顯示，Then 其他正式內容不受影響。

### US-MD-002：播放 GIF 與影片素材

**身為** 收集動態素材的使用者
**我想要** 在 Canvas 中加入 GIF 或影片
**以便** 保留動態參考內容與上下文

優先級：Could
目標里程碑：M5

驗收條件：

- Given 動態素材進入可視範圍，When 使用者啟動播放，Then 播放控制不攔截必要的 Canvas 選取與移動操作。
- Given 動態素材離開可視範圍或 Workspace 關閉，When 生命週期更新，Then 播放與解碼資源被暫停或釋放。
- Given GIF Browser 無網路或供應商失敗，When 搜尋失敗，Then 本機 Canvas 編輯仍可正常使用。

### US-CL-001：共同編輯 Workspace

**身為** 受邀的協作者
**我想要** 和其他人同時編輯同一 Workspace
**以便** 共同整理想法且不覆蓋彼此的已確認內容

優先級：Could
目標里程碑：M8

前置條件：本機 persistence、operation log、物件版本、權限與資產同步策略已穩定。

驗收條件：

- Given 兩位使用者同時在線，When 各自提交不衝突操作，Then 兩端最終得到一致 Workspace。
- Given 同一物件發生並行修改，When 合併執行，Then 依明確規則解決或要求使用者處理，不靜默遺失內容。
- Given 使用者暫時離線，When 重新連線，Then 本機已確認 operations 可安全同步並保留活動紀錄。

## 7. 建議的第一個 Sprint

Sprint 目標：建立可演進的 Workspace 操作基礎，並證明「玻璃工具層＋原始內容層」能在 Desktop Canvas 上形成最小垂直切片。

納入：

- US-DS-001 辨識工具與內容。
- US-DS-002 模糊降級。
- US-CV-001 的 viewport 技術 spike。
- US-CT-001 的最小建立流程。
- US-HX-001 的 operation 與反轉模型，不要求第一個 Sprint 完成所有 UI。

技術 Tasks：

- 移除 `App.kt` 對 Material 3 Theme、Button、Text 的使用。
- 建立最小 Shell 與 Content tokens，不先投入完整動畫與視覺打磨。
- 建立 `GlassRenderer` contract 與可讀的 fallback implementation。
- 建立不依賴 Compose 的 Workspace、TextNode、Transform、Viewport 與 WorkspaceOperation。
- 建立 create、move、delete operation 及 transaction/undo common tests。
- 建立示範 Workspace screen。
- 加入 Light、Dark、Reduce Transparency previews。
- 先確保 Desktop 行為可展示；Android、iOS、Web 維持編譯通過。

Sprint Review 應展示：

1. 使用者能平移、縮放 Canvas，並建立一個原始樣式文字節點。
2. 使用者能透過浮動玻璃工具列觸發建立操作。
3. Node 建立與移動透過統一 operation 執行，且 common test 能反轉操作。
4. 關閉透明效果後仍保有清楚層級。
5. 相同 domain 與產品語意可在各平台編譯，互動驗證以 Desktop 為主。
