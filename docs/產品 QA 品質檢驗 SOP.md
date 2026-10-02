# BoarderLess 產品 QA 品質檢驗 SOP

更新日期：2026-10-02
文件狀態：執行基準 v1

## 1. 目的

本文件定義 BoarderLess 從功能完成到產品放行的品質檢驗流程，確保每次驗收都有一致的範圍、步驟、證據、責任人與結論。適用於 Desktop、Android、iOS、JS／Wasm，以及圖片／GIF／影片、多人協作、AI、物件庫和自訂形狀等功能。

## 2. 驗收責任

| 角色 | 必須負責 | 不得越權宣告 |
|---|---|---|
| 工程／Agent | 建置、測試、功能步驟、資料一致性、錯誤處理、無障礙語意、可重現證據與已知限制 | 不得自行宣告視覺美感、遮擋程度或產品體驗已獲最終接受 |
| 產品負責人／使用者 | 視覺辨識、資訊層級、操作是否被搶走、元件是否互相遮擋、動態島／Home Indicator 等實機觀感與最終放行 | 不以單張截圖取代工程功能、資料完整性或回歸測試 |

以下項目一律需要產品負責人親自簽核：

- 畫面是否清楚、美觀、現代且符合產品個性。
- 工具列、Modal、Bottom Sheet、狀態列、鍵盤、動態島與 Home Indicator 是否造成不可接受的遮擋。
- 操作選單是否過度強調、搶走畫布、選取、拖曳或文字編輯的控制權。
- 手機直向／橫向、分割視窗與不同安全區域下的最終視覺結果。

工程端可以提供截圖、錄影、尺寸、語意樹與操作結果，但上述項目在產品負責人簽核前只能標記為「待產品視覺驗收」，不得寫成「已通過」。

## 3. 驗收狀態

- `未開始`：尚未準備可驗收版本。
- `工程驗證中`：建置、測試或功能流程尚未完成。
- `待產品視覺驗收`：工程檢查通過，等待產品負責人判斷視覺、遮擋與操作感受。
- `受阻`：缺少 API、裝置、帳號、測試資料或產品決策；必須記錄解除條件。
- `不通過`：已有可重現缺陷；必須附嚴重度與重現步驟。
- `通過`：所有必要驗收完成，且需要產品簽核的項目已取得結論。
- `不適用`：本次版本不包含該範圍，需寫明理由。

不得只寫「看起來正常」「應該可以」或「測過了」。

## 4. 標準驗收流程

### 4.1 建立驗收批次

每次驗收先記錄：

- 版本、commit 或建置識別。
- 平台、OS、裝置與畫面方向。
- 後端版本、Workspace 與測試帳號角色。
- 本次變更範圍及明確排除範圍。
- 已知阻塞與測試資料復原方法。

驗收期間若程式或後端合約變更，受影響的舊證據不得直接沿用。

### 4.2 工程進入條件

交付產品視覺驗收前，工程端至少必須確認：

1. 目標平台成功編譯或建置。
2. 對應單元、契約與整合測試通過。
3. `git diff --check` 或等價格式檢查通過。
4. 測試 Workspace、角色、Node、Group、Relation 與素材狀態可重現。
5. 成功、失敗、取消、重試及重開後狀態均有明確預期。
6. 不得把 pending、離線或後端拒絕顯示成已儲存成功。

### 4.3 功能檢驗

每個功能至少覆蓋：

- 正常路徑：使用者能完成目標。
- 邊界路徑：空資料、最大／最小尺寸、重複及快速連續操作。
- 中斷路徑：取消、離線、逾時、重開 App、切換 Workspace。
- 權限路徑：owner、editor、commenter、viewer 的允許及拒絕結果。
- History 路徑：Undo／Redo、遠端同步後 history 是否安全重設。
- 持久化路徑：重新開啟後內容、版本與資產參照仍一致。

正式資料的刪除測試必須先精確核對 ID、名稱、所有權與內容數量；需要產品授權的測試資料，未取得授權前不得執行。

### 4.4 手機互動檢驗

手機版至少以直向及橫向各驗一次：

- 點選 Node 不得自動進入文字編輯或強制展開 Bottom Sheet。
- 多選模式可逐項加入／移除，點擊空白區的結果符合模式定義。
- Modal 與收合狀態的 Bottom Sheet 可以共存，彼此不得意外關閉。
- 文字編輯、刪除、複製、貼上、群組、縮放、旋轉與連線都有可發現且不搶操作權的入口。
- 視覺控制點與實際觸控命中區分開檢查；可觸控不等於視覺尺寸必須放大。
- 軟鍵盤顯示及收回後，焦點、選取、草稿與畫布位置不得意外遺失。

### 4.5 視覺與遮擋檢驗（產品負責人簽核）

工程端先提供下列場景，產品負責人決定是否接受：

1. 空白、少量及密集物件的畫布。
2. 未選取、單選、多選、群組及 Relation 選取。
3. Menu、中央 Modal、Command Palette、Bottom Sheet 收合／展開。
4. 軟鍵盤開啟、直向、橫向及旋轉前後。
5. iPhone 動態島、狀態列、Home Indicator 與 safe area。
6. 長標題、長錯誤訊息、同步中、離線及權限遭撤銷。
7. Light／Dark、Reduce Transparency 與 Reduce Motion。

遮擋判定需同時檢查：

- 核心按鈕是否完全可見且可觸發。
- 最後一列內容能否捲動到系統區域之外。
- 浮動元件是否遮住正在操作的 Node 或控制點。
- Modal 的背景阻擋是刻意設計，還是意外奪走操作。
- Bottom Sheet 收合後是否仍佔用過多畫布。
- 鍵盤、動態島與 Home Indicator 是否蓋住資訊或控制。
- 提示、狀態 pill 與錯誤訊息是否與主要面板重疊。

產品負責人簽核格式：

```text
視覺驗收：通過／不通過
遮擋驗收：通過／不通過
裝置與方向：
需要修改：
簽核日期：
```

### 4.6 VoiceOver 與無障礙檢驗

VoiceOver 是 Apple 在 iPhone、iPad 與 Mac 內建的螢幕閱讀器。它會朗讀目前聚焦元件的名稱、角色、狀態與可執行動作，讓看不見畫面或不便直接操作觸控介面的使用者以手勢、鍵盤或輔助操作使用 App。

VoiceOver 檢驗至少確認：

- 可操作元件都有可理解的名稱，而不是只朗讀「按鈕」。
- 展開／收合、選取、停用、鎖定等狀態會被朗讀。
- 閱讀順序符合任務順序，不會跳到背景 Canvas。
- Modal 開啟時焦點留在 Modal；關閉後回到合理位置。
- Bottom Sheet 收合時，隱藏內容不會繼續被逐項朗讀。
- Node、Group、Relation 與 resize／rotate／connector handle 可辨識及啟用。
- 不依賴顏色、動畫或拖曳作為唯一操作方式。
- Dynamic Type 或較大文字下仍能完成操作。

模擬器的 accessibility tree 可做工程預檢，但最終 VoiceOver 手勢與朗讀品質應以實機為準。

### 4.7 回歸檢驗

新功能通過自身檢驗後，至少回歸：

- 建立、編輯、移動、刪除及 Undo／Redo。
- Workspace 開啟、切換、重新命名、同步及重開。
- Node、Group、Relation 的選取與 Inspector。
- 剪貼簿、Quick Scheme、Command Palette。
- 手機 Menu、Bottom Sheet、多選與安全區域。
- 權限與遠端衝突處理。

## 5. 圖片、GIF、影片素材專項

素材功能進入驗收前，必須先有可用的上傳、完成確認、授權下載與刪除合約。不得只建立 metadata 就宣稱素材已保存。

最低測試矩陣：

- 圖片：JPEG、PNG、透明圖、方向資訊、極寬／極高及超出限制。
- GIF：播放、暫停／Reduce Motion、縮圖及重開後恢復。
- 影片：載入中、播放／暫停、靜音、時長、失敗及離線狀態。
- 共通：上傳取消、逾時、重試、重複檔案、錯誤 media type、大小及 checksum 不符。
- 權限：viewer 不得上傳或刪除；失去權限後既有 signed URL 不得永久有效。
- 生命週期：刪除 Node、刪除 Workspace、孤兒資產與縮圖／衍生檔清理規則。

證據需包含 asset ID、狀態轉換、原檔／縮圖結果與重開 Workspace 後的結果；不得記錄 signed URL 或憑證。

### 5.1 素材匯入入口驗收（Desktop／Android／iOS／Web）

目前選擇器已接上 Desktop、Android、iOS 與 Web JS／Wasm。使用 owner／editor 的測試 Workspace，先結束草稿、拖曳及儲存。真實傳輸驗收須確認 `BAI-003` 完整合約已交付；後端關閉時只驗收離線提示，不把成功上傳標為 Pass。

1. 從工具列點擊「匯入素材」，確認原生檔案選擇器開啟。以 Command Palette 搜尋「匯入素材」重複一次；窄視窗從中央 Menu 重複一次。
2. 取消原生選擇器，確認 Canvas、history 與 operation log 沒有新增物件，且匯入按鈕恢復可用。
3. 分別選取有效 PNG／JPEG／WebP／GIF／MP4／WebM，記錄準備、上傳百分比、完成確認與 processing。ready 前不得出現正式 Media Node。
4. 匯入期間確認內容編輯及切換／建立／改名／刪除 Workspace 停用，平移與查看仍可使用；取消或失敗後操作恢復。尚有待儲存內容、文字草稿或手勢時，匯入入口應停用。
5. 點擊「取消匯入」或從 Command Palette 執行取消，確認不新增 Node；pending 資產清理結果須查後端，若 ready 已完成則保留原檔並記錄該結果，不能把保留原檔誤判為取消失效。
6. 後端仍回傳 `uploadUrl: null` 時，確認顯示「此伺服器尚未提供素材上傳」、沒有新增 Node，且剛建立的 pending metadata 已 soft-delete。
7. 後端合約完整時，確認 ready 素材於畫布中央建立單一物件，具有正確 media kind、穩定 asset ID 與縮圖引用；Undo／Redo 操作不重新上傳，重開 Workspace 仍有相同資產引用。操作儲存失敗時須回到權威狀態並保留失敗提示。
8. 另測空檔、錯誤類型、超過 200 MB、選取後變動的檔案與中途斷網；須顯示錯誤或取消且不新增正式 Node。

圖標辨識、原生選擇器焦點、進度／取消按鈕可見性與中央選單遮擋交由產品負責人簽核。此批入口驗收不等同於圖片預覽、GIF／影片播放或完整 storage 整合驗收；尚未實作的項目維持 Pending。

### 5.2 Android 文件 provider 與暫存檔驗收

Android 已完成 Debug APK 建置，以下裝置操作仍待驗收。從中央 Menu 的「匯入素材」進入系統文件選擇器，沿用 5.1 的成功、取消、錯誤與權限檢查，另補：

1. 分別選取本機文件及可用的雲端文件 provider；確認不要求整個相簿／媒體庫的讀取權限，雲端下載完成後可繼續匯入。
2. 取消系統選擇器後重新選取，確認沒有殘留等待狀態；背景返回與旋轉造成 Activity 重建時，不可錯誤新增 Node，必要時可重新開始選取。
3. provider 沒有宣告大小時，依實際讀取結果驗證限制；宣告大小與實際內容不同時不新增正式物件。來源 URI 或檔案名稱不可成為正式 asset ID。
4. 分別在複製、上傳及 processing 階段取消；以 Debug 裝置的 App 私有 cache 查核本次 `boarderless-import-*.part` 已清理。成功與失敗也應清理；雲端或原始文件不得被刪除。
5. 檢查 PNG／JPEG／WebP／GIF 尺寸及 MP4／WebM 可取得的時長／尺寸；系統 codec 缺乏 optional metadata 時仍可由伺服器完成正式處理，不能宣稱已具本機播放能力。

Android 的系統選擇器本身由系統管理；取消 App 工作不會強制關閉已開啟的系統畫面。若選擇器仍開著，先返回 App 再開始下一次匯入。以真實裝置的結果更新 QA Workbench，缺少裝置時維持 Pending。

### 5.3 iOS Files／iCloud 與取消驗收

iOS 原生來源的 checksum／分段讀取／暫存清理已有模擬器測試；以下 UIKit、文件 provider 與觸控流程仍待人工驗收，依 4.5 由產品負責人簽核視覺及遮擋：

1. 在 iPhone 中央 Menu 點擊「匯入素材」，確認系統 Files 文件選擇器開啟，單次只能選一份；來源可使用「我的 iPhone」或 iCloud Drive。確認允許的 PNG／JPEG／WebP／GIF／MP4／WebM 能依裝置 provider 的型別識別顯示與選取。
2. 點系統「取消」後回到畫布，確認不新增 Node 並恢復操作；立即再次開啟選擇器，確認沒有殘留已呈現的 UIKit 面板或等待狀態。
3. 選取文件時使用副本，不移動／刪除原始文件；iCloud 文件下載後確認可進入準備及上傳階段。URI／security scope／本機暫存路徑不得出現在 Workspace、剪貼簿或方案 payload。
4. 上傳前與上傳途中取消，確認只清理本次 App snapshot，不刪原始文件；empty／超過 200 MB／複製途中變更的內容不得建立正式 Node。
5. 背景返回、直橫向旋轉及 VoiceOver 分別執行選取／取消。檢查系統面板關閉後可回到中央選單，進度與取消入口不被動態島或 Home Indicator 遮擋。
6. 真實 ready、圖片預覽、GIF／影片播放與重開驗收仍依後端 `BAI-003` 交付狀態分別記錄，不能以 Xcode build 或原生來源測試替代。

### 5.4 Web JS／Wasm 檔案選擇與跨站傳輸驗收

工程驗證命令：`node --test webApp/tests/media-import.test.mjs` 及 `./gradlew :shared:jsBrowserTest :shared:wasmJsBrowserTest`；Karma 使用 Chrome Headless，需有 Chrome 可執行檔（非預設位置時設定 `CHROME_BIN`）。自動測試以 browser File／Blob 及測試 fixture 驗證實際 JS／Wasm callback；未代替瀏覽器原生文件對話框或後端 storage。

1. 分別開啟 JS 與 Wasm 版；確認 `media-import.js` 成功載入，點擊「匯入素材」能開啟系統對話框。Chrome、Firefox、Safari 及手機瀏覽器分別記錄結果，不能以 Chrome Headless 代替其他瀏覽器簽核。
2. 選取、按系統取消、關閉對話框及再次選取同一檔案，確認不留下卡住的等待狀態。舊瀏覽器的取消由 focus fallback 判斷，須查核選取較慢的來源不會被提早當作取消。
3. 檔案 MIME 為空時以支援的副檔名辨識；錯誤 MIME、空檔、超過 200 MB 或選取後無法再讀取的文件都不建立正式 Node。只用 file slice 讀取內容，不把全檔轉成 Kotlin ByteArray。
4. 後端契約完整時，查核 storage 網路請求為 native File 的 signed PUT，required headers 與 Content-Type 正確，upload progress 更新；CORS preflight 必須允許實際 Web origin、PUT 及所需 headers。拒絕、斷網或 5 分鐘 timeout 不可回報 ready，取消要中止 request 並嘗試清理 pending metadata。
5. 取消 hash／upload／processing，或匯入成功後，確認 File 參照已釋放，沒有晚到 callback 又新增 Node；重開頁面不應恢復本機 File handle。簽名 URL／headers 不得進 Workspace、history、clipboard 或方案資料。

視覺與遮擋依 4.5 交由產品負責人簽核；效能／記憶體基線仍依第 7 節放在最後階段。

### 5.5 Desktop 圖片／縮圖預覽

前提：後端目前由使用者關閉，不自行重啟。`./gradlew :desktopApp:test :shared:jvmTest` 可離線使用模擬 gateway 驗證傳輸／解碼安全，但不能據此簽核 object storage 整合；下列真實驗收須待使用者啟動具 BAI-003 download 契約的服務。

1. 使用 ready PNG／JPEG／WebP Image Node；有縮圖時優先顯示縮圖，沒有時顯示原檔。確認 Fit 保留比例，替代文字仍可由 VoiceOver／無障礙工具讀取，圖片本身不增加重複朗讀。
2. 本節驗收 GIF 靜態首幀／圖片縮圖，Desktop GIF 動畫另依 5.10 簽核；Video 目前只有圖片縮圖。不可將「圖片顯示」簽核成「GIF 動畫／影片播放完成」。沒有縮圖的 Video 保留占位。
3. 選取、拖曳、旋轉、縮放、群組與鎖定圖片；預覽不搶走卡片操作。比例、旋轉後遮擋及視覺品質由產品負責人在 QA Workbench 附截圖並簽核。
4. storage 403／斷網／截斷／checksum 不符／損毀圖片時不得顯示未驗證內容；卡片維持可操作的占位，點選可重試載入且仍選取物件。32 MiB 以上原檔或 1,200 萬像素以上圖片需要較小縮圖，不得無限制解碼。
5. 載入中切換工作區／移出視窗／關閉 App；舊圖片不可出現在另一工作區，取消要清理 `.part`。載入完成也清理短期圖片檔，目前沒有持久磁碟 cache；重新進入可見範圍可再次取授權下載。signed URL、本機 token 及 Bitmap 不得進 Workspace、history、clipboard 或方案。
6. 記錄平台限制：Desktop／Android／iOS／Web 均已提供靜態預覽 loader；仍須分平台簽核，不能以 Desktop 結果簽核所有平台。

### 5.6 Android 圖片／縮圖預覽

離線驗證：`./gradlew :shared:testAndroidHostTest :shared:jvmTest :androidApp:assembleDebug`。Host tests 只驗證共用規則與真實 java.io 檔案／雜湊流程，不包含 Android BitmapFactory 的實機解碼；不可把 APK 建置或 Host test 通過簽成手機視覺通過。後端關閉期間不自行啟動，真實下載須待使用者啟動符合 BAI-003 的服務。

1. 至少在最低支援版本 API 24 與較新版本裝置測 ready PNG／JPEG／WebP，確認原檔或優先縮圖出現。驗收圖片比例、方向、替代文字、選取與觸控操作；GIF／Video 此階段只簽核靜態預覽／圖片縮圖，不簽核播放。
2. 解碼前檢查實際尺寸，超過 1,200 萬像素或 32 MiB encoded 大小要保持占位並要求較小縮圖；允許的圖片以 power-of-two sampling 將顯示用最長邊限制為 1024px。縮圖清晰度與高倍 zoom 可接受程度由產品負責人判斷，原檔不能被預覽取樣覆寫。
3. 斷網、403、錯誤 checksum、截斷、內容損毀及缺少 Video 縮圖，確認不顯示未校驗檔案；點選失敗卡片重試載入並照常選取，不強制開啟屬性面板。
4. 載入中移出視窗、切換工作區、轉向或離開 App，再返回；取消後 `.part` 與本次 `asset-preview-*` 私有目錄應清理，成功顯示後也不留持久圖片檔。不得清除其他匯入 snapshot 或整個 cache 根目錄。
5. 手機底部屬性 sheet、多選／群組、旋轉／縮放及選單遮擋仍由產品負責人在 QA Workbench 附截圖簽核；不得以本輪 Host tests 代替視覺判斷。記錄裝置、Android 版本、檔案格式、原始尺寸、下載／解碼結果與重試情形。

### 5.7 iOS 圖片／縮圖預覽

離線原生測試：`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test`（Xcode 安裝位置不同時改為實際位置）。測試包含 Simulator 的真實 Foundation 檔案／CoreCrypto 校驗／PNG 解碼，但不等同於正式 storage、實機視覺或觸控驗收。後端由使用者關閉期間不自行啟動。

1. 服務提供 BAI-003 授權下載後，在 iPhone／iPad 驗收 ready PNG／JPEG／WebP、GIF 靜態首幀與 Video 圖片縮圖；有 thumbnailAssetId 優先使用縮圖。確認比例、方向、透明背景、替代文字與 Fit 顯示，紀錄原始尺寸及格式。iOS GIF 動畫／影片另依 5.11／5.15 簽核；本節圖片結果不等同於播放完成。
2. 查核 32 MiB encoded／1,200 萬像素的解碼前限制；超限、損毀、checksum／size 不符、403／斷網時維持可點選重試的占位，不顯示未校驗檔案。iOS 此輪不套用 Android 的 1024px 取樣規則。
3. 載入中切換工作區、移出視窗、取消或關閉畫布；舊圖片不可跑到新工作區。成功與失敗後本次 `boarderless-preview-*.part/.ready` 都須清理，原始 Files／iCloud 文件及其他匯入 snapshot 不受影響；沒有持久磁碟 cache。
4. 驗收卡片的觸控選取、多選／群組、鎖定、拖曳、旋轉與縮放，圖片不得強制打開 bottom sheet 或截走物件操作。視覺、動態島、四邊選單遮擋及 VoiceOver 由產品負責人在 QA Workbench 附截圖／結果簽核。

### 5.8 Web JS／Wasm 圖片／縮圖預覽

離線驗證：`./gradlew :shared:jsBrowserTest :shared:wasmJsBrowserTest :webApp:compileKotlinJs :webApp:compileKotlinWasmJs`；Chrome 非預設位置時設定 `CHROME_BIN`。測試包含 Chrome Headless 的真實 PNG／Skiko 解碼，但下載使用模擬 gateway；不能取代 signed GET／storage CORS 或其他瀏覽器驗收。後端關閉期間不自行啟動。

1. 服務提供 BAI-003 download 授權後，在 JS 與 Wasm 版各驗收 PNG／JPEG／WebP、GIF 靜態首幀與 Video 圖片縮圖。Chrome／Firefox／Safari／手機瀏覽器分別紀錄，確認 Skiko 初始化後顯示、比例、方向、透明背景與替代文字。GIF 動畫另依 5.12 簽核；本節圖片結果不能簽核為動畫／影片播放完成。
2. 查核 signed GET 的 CORS 允許實際 origin 及 required headers；403、斷網、checksum／size 不符、損毀圖片、超過 32 MiB encoded／1,200 萬像素時不得顯示未校驗內容。失敗卡片可點選重試並照常選取物件，不能強制開啟 modal／屬性 sheet。
3. 下載中切換工作區、移出視窗或關閉頁面；舊 Bitmap 不可出現在新 Workspace。短期 buffer 只可校驗後消費一次，完成、取消與錯誤後釋放 reference；本輪不持久儲存至 localStorage／IndexedDB，不把 signed URL／local token／bytes 寫入 history 或剪貼簿。
4. 驗收滑鼠與 touch 的選取、多選、拖曳、旋轉、縮放、鎖定、群組及縮圖視覺。截圖、遮擋及動態島結果仍由產品負責人在 QA Workbench 簽核；效能／記憶體基線測試仍排最後階段。

### 5.9 跨卡片共用預覽快取

本階段為四平台短期 memory LRU，不是持久磁碟／IndexedDB cache；後端關閉或授權失敗時不得藉 cache 繞過驗證。效能基線仍排最後，下列只查核正確性與資料隔離。

1. 在同工作區複製同一張 ready 圖片，或將其移出再移回視窗；檢查每次載入仍呼叫下載授權，快取命中後不重複 signed GET／解碼。短效 URL 更新但 asset metadata 不變時，應可重用同一份 Bitmap。這是共用內容，不是把本機 token 寫入同步 operation。
2. 已成功顯示後讓下載授權回覆 403／角色失去檢視權，下一次載入不得直接交付 cached Bitmap；保持失敗占位。此測項查核新載入，已在畫面上的內容如何隨權限撤銷即時關閉仍須與多人協作機制另行驗收。
3. 使用者／工作區／checksum 改變不得取到舊圖片；切換或清空 session 會清除 cache。讓舊授權或解碼較晚完成，確認不回填；取消的 decoder 也不能回填。重試須重新授權，壞內容不得快取。
4. cache 最多 64 entries、估算 decoded pixels 64 MiB；超過時 LRU eviction，oversize value 不保留。這個上限不包括可見卡片、解碼器、下載 buffer 或其他 App 記憶體，不得以此宣稱效能／記憶體基線完成。
5. 驅逐／清除 cache 時，可見卡片持有的 Bitmap 不可被 recycle 而變成黑圖；重新開啟 App 不恢復這份 memory cache。四平台各自驗收，Android 原生解碼不能用 Host tests 代替，視覺問題附 QA Workbench 截圖由產品負責人簽核。

### 5.10 Desktop GIF 動畫播放

離線測試：`./gradlew :desktopApp:test :shared:jvmTest`；使用測試產生的真實雙幀 GIF 驗證 decoder、pixel／delay／loop、釋放與模擬下載，但不代表實際 storage／GUI 驗收。後端關閉期間不自行啟動；iOS／Web／Android 動畫另依 5.11／5.12／5.13，影片仍未播放。

1. 後端提供 BAI-003 binary lifecycle 後，匯入 ready GIF，初始只顯示縮圖／首幀，不自動播放。選取卡片後按「播放 GIF」，確認下載的是原始 assetId 而非 thumbnailAssetId，授權、size／checksum 正確後才開始動畫；播放狀態與 handle 不進正式 operation／history／clipboard／方案。
2. 測至少雙幀顏色變化、透明疊合／partial frame、Restore Background／Previous、不同 delay、無限／有限循環及單幀 GIF。檢查循環不把第一次播放算成 repetition，最後一幀停留其宣告時長；0／小於 20ms 的 delay 以 100ms 顯示。色彩、透明及時序觀感由產品負責人簽核，原生自動測試目前只有雙幀全畫面 fixture，不能代替所有 disposal 案例。
3. 播放中按「暫停 GIF」，目前畫面須凍結且不回首幀；再按播放繼續原有 cursor。有限循環結束後重播從首幀開始。按鈕不得觸發卡片拖曳／取消選取或強制打開屬性面板；鎖定物件與 viewer 可以控制本機播放但不能修改內容。
4. 播放中拖曳、旋轉、縮放、多選／群組；移出可見範圍、切換工作區／使用者、刪除卡片或關閉畫布時 decoder 必須釋放。返回畫布重新授權並從首幀開始；重開 App 恢復 Node／靜態預覽，不保存或自動恢復先前的播放時間點。
5. 開啟 Reduce Motion 立即停止並釋放動畫，保留靜態預覽且停用播放按鈕；關閉後可再次播放。驗收按鈕可辨識性、焦點、VoiceOver 名稱、選單／圖像遮擋並附 QA Workbench 截圖，由產品負責人判斷。
6. 403、斷網、錯誤 checksum、損毀／截斷、超過 32 MiB encoded／1,200 萬像素／10,000 frames 不可啟動未校驗內容。晚到／中途錯誤停止排程並顯示重試狀態；取消與失敗不留本次 `boarderless-gif-*` 暫存目錄。不把本輪正確性測試當成最後階段的效能基線完成。

### 5.11 iOS GIF 動畫播放

離線原生測試：`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test`（依實際 Xcode 位置調整）。Simulator 測試驗證真實 GIF89a 的逐幀 pixel／delay／loop、Foundation 檔案與 CoreCrypto 校驗，不代替 iPhone／iPad UI 或正式 storage。後端關閉期間不自行啟動。

1. ready GIF 初始只顯示縮圖／首幀；選取後才顯示「播放 GIF」，由使用者按下才取原始 assetId 授權下載。初始不自動播放，viewer／鎖定物件仍能控制本機播放但不能編輯正式內容。播放不得強制打開 modal／bottom sheet。
2. 依 5.10 第 2 項驗收透明、partial frame、Restore Background／Previous、不同 delay、有限／無限循環與單幀 GIF；目前自動 fixture 僅包含兩個全畫面紅／藍幀。暫停須凍結目前幀，恢復保持 cursor，結束後可重新播放；播放時間點不保存或同步。
3. 確認原檔 byte size／SHA-256 完整驗證，32 MiB encoded／1,200 萬像素／10,000 frames 上限前置檢查；403、斷網、checksum 不符、損毀與取消不可交付未校驗 decoder。本次 `boarderless-preview-*.part/.ready` 在播放 handle 交付前清理，不能刪除 Files／iCloud 原始文件或其他匯入 snapshot。
4. 播放中轉向、拖曳、縮放、旋轉、多選／群組；切換工作區／使用者、移出可見範圍、刪除物件或關閉畫布釋放 decoder。離屏返回會重新授權並從首幀開始，不保留離屏播放位置。Reduce Motion 開啟即轉為靜態預覽，停用播放並釋放原生資源。
5. 用實機 touch 與 VoiceOver 驗收播放／暫停命中區、焦點及名稱；按鈕不搶走卡片拖曳／選取，不與 bottom sheet 或動態島衝突。圖片、文字、透明度及遮擋截圖附至 QA Workbench，交由產品負責人簽核；效能與記憶體基線維持最後階段。

### 5.12 Web JS／Wasm GIF 動畫播放

離線驗證：`./gradlew :shared:jsBrowserTest :shared:wasmJsBrowserTest :webApp:compileKotlinJs :webApp:compileKotlinWasmJs`；Chrome 非預設位置時設定 `CHROME_BIN`。Chrome Headless 測試含真實 Skiko GIF 解碼，但 binary gateway 為模擬，不等同 storage／CORS 或各瀏覽器 GUI 驗收；後端關閉期間不自行啟動。

1. JS 與 Wasm 各自驗收 ready GIF：初始仍為縮圖／首幀，選取後按「播放 GIF」才授權下載原始 assetId。兩版都需確認 signed GET origin／headers 的 CORS 正確；驗證實際 size／SHA-256 後才開啟 decoder，不拿 PNG 或其他檔案假冒 GIF。
2. 依 5.10 第 2 項驗收透明疊合、partial frame、disposal、不同 delay、有限／無限循環；目前自動 fixture 僅為雙幀全畫面紅／藍。播放、暫停／恢復、重播及 Reduce Motion 的結果應與 Desktop／iOS 一致，狀態與時間點只屬於裝置，不同步至協作者。
3. Chrome／Firefox／Safari／手機瀏覽器分別檢查滑鼠與 touch 的按鈕命中、焦點及名稱，按鈕不觸發卡片拖曳／取消選取、不強制開啟 modal／sheet。播放期間正常旋轉、縮放、鎖定與群組；圖片品質、透明與遮擋附 QA Workbench 截圖由產品負責人簽核。
4. 切換使用者／工作區、離屏、刪除 Node、關閉頁面或開啟 Reduce Motion 釋放 Codec／Data；再進入重新授權並從首幀開始。403、checksum／size、截斷、損毀、pending、跨工作區或超限不得交付 decoder。release 後不能再讀 frame，memory sink 不保留 signed URL、bytes 或本機 token 至 localStorage／IndexedDB。
5. 本輪採瀏覽器主執行緒的 Skiko 解碼，不宣稱 Worker、無卡頓或效能／記憶體基線已完成；效能測試依第 7 節維持最後階段。Android GIF 另依 5.13 驗收，四平台影片不得用這些結果簽核。

### 5.13 Android GIF 動畫播放

離線檢查：`./gradlew :shared:testAndroidHostTest :shared:assembleAndroidDeviceTest :androidApp:assembleDebug`。Host tests 可檢驗格式、metadata、loop／delay 及模擬下載清理，不能驗證原生 Bitmap pixel。連接裝置後執行 `./gradlew :shared:connectedAndroidDeviceTest`，測試真實幀、隨機取幀、既有 Bitmap 不變與 release；目前無連接裝置，Device test 已編譯／封裝測試 APK，未執行。測試 gateway 不等同真實 storage；後端關閉時不自行啟動。

1. 在 API 24 與較新 Android 版本各驗收 ready GIF：初始縮圖／首幀不自動播放，按「播放 GIF」後重新授權原始 assetId，校驗 size／SHA-256 才開啟 decoder。記錄裝置／API、檔案、原始尺寸及回應狀態；Android 影片另依 5.14 驗收，不以 GIF 結果簽核影片。
2. 依 5.10 第 2 項準備透明、partial frame、disposal 2／3、interlaced、短／長 delay、finite／infinite／無 NETSCAPE loop 的 GIF。檢查從首幀、暫停／恢復、重播及循環邊界，背景／先前幀不可殘留。自動 fixture 只有全畫面雙幀紅／藍，不涵蓋這些複雜組合，不得據此替代完整格式驗收。
3. 使用 touch、TalkBack 驗收播放／暫停命中區、焦點／名稱與選取、拖曳、旋轉、縮放、多選／群組、鎖定。播放控制不得強制打開 bottom sheet、搶走卡片操作或被 sheet 遮擋；viewer 可播放但不能產生編輯 operation。視覺及遮擋結果附 QA Workbench 截圖，由產品負責人簽核。
4. 切換帳號／工作區、離屏、刪除、Reduce Motion 與頁面離開釋放 decoder；重新進入要重新授權並回首幀。Bitmap 已交付後不得因 decoder release 變黑。播放時間點及暫停狀態不分享給協作者、不存入方案簿／剪貼簿。
5. 403、pending、跨工作區、checksum／size 錯誤、截斷／假冒格式、超過 32 MiB／1,200 萬 screen 或單幀像素／10,000 frames 不得開始播放；部分幀解碼失敗顯示可重試錯誤。檢查 scoped `asset-gif-*` 目錄的 `.part/.ready` 成功、取消、失敗後清除，不動其他 cache 目錄；signed URL 不持久化。效能與記憶體基線依第 7 節維持最後階段。

### 5.14 Android 影片播放

離線檢查：`./gradlew :shared:testAndroidHostTest :shared:assembleAndroidDeviceTest :androidApp:assembleDebug`。4 個共用 policy tests 與 3 個 Android 下載／清理 tests 不使用真正的 MediaPlayer；Device test 另檢查損毀影片的原生 prepare 失敗／清理，已封裝但未執行。成功影片解碼、TextureView 畫面與聲音尚無自動化證據，必須透過本節實機 QA，不得以 APK 或 Host tests 通過宣稱已完成。採用 [Android MediaPlayer 生命週期規範](https://developer.android.com/media/platform/mediaplayer/state-resources)，玩家狀態及 native calls 在 Main、檔案工作在 IO。

1. 測試 API 24 與較新版本，ready MP4（H.264/AAC）及 WebM（VP8／VP9、Opus／Vorbis，依裝置 decoder 支援情形逐一記錄）。初始只顯示 poster／placeholder，明確按「播放影片」才重新授權原始 assetId；完整下載／size／SHA-256 驗證後開啟播放器，不串流未校驗內容。影片格式／codec 不支援時要可重試錯誤，不黑畫面假裝成功。
2. 預設靜音；驗收播放／暫停、聲音開關、前後 10 秒、0／duration 邊界、原生 keyframe seek 及結束後重播。不得自動循環或初始自動發聲。確認 paused 時保留目前畫面；播放時間只在裝置記憶體，不產生 Workspace operation，也不進方案簿／剪貼簿。聲音中斷、耳機／藍牙切換與系統音訊焦點另列實機問題，不以現有 Host tests 代替。
3. 橫／直／旋轉 metadata 的影片比例、Fit 留邊、群組／layer／透明度、縮放／旋轉、拖曳與選取須實際檢查 TextureView；播放器不得變成全螢幕、搶走物件拖曳或強制開啟屬性 sheet。窄 Node 的控制換行、文字、底部 sheet／選單遮擋與 TalkBack 由產品負責人在 QA Workbench 附截圖簽核。viewer／鎖定 Node 可播放但不能編輯。
4. 離屏、切換帳號／工作區／asset、刪除 Node、Reduce Motion、App 進背景時取消載入並釋放播放器、Surface 與這次 private file。回到前景不自動續播，需再按播放；新播放器重新授權、從頭開始。測試「下載中切背景」及「prepare 中離開」，不得晚到的 callback／dispatcher 回傳讓背景播放器復活。暫時 TextureView 重建不可釋放其他 Node 的 Surface。
5. 正確 checksum 但損毀內容、403、pending、跨工作區、缺失、超過 200 MiB、實際 size 不符、unsupported codec、非影片內容與 prepare timeout 都不得留下播放器或私有 `.part/.ready`。確認只有本次 `asset-video-*` 目錄被清除，其他圖片／GIF／影片不受影響。原生影片實際尺寸超過 1,200 萬像素或沒有正 duration 時拒絕；此限制不等同已量測 native decoder 記憶體上限。
6. 真實 storage 仍需 BAI-003 binary lifecycle；後端關閉期間不自行啟動。iOS／Web／Desktop 另依 5.15／5.16／5.17 簽核，不可用 Android 結果代簽。持久下載 cache、邊下載邊播、完整音訊焦點策略與效能基線也不屬於本輪已完成驗收項目；效能維持第 7 節最後階段。

### 5.15 iOS 影片播放

離線檢查：`./gradlew :shared:iosSimulatorArm64Test :shared:compileKotlinIosArm64`；Xcode 尚未全域選取時可為此命令設定 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`，不修改系統設定。7 個 native tests 以 AVAssetWriter 現場產生 64x64 無聲 H.264 MP4，測試真實 AVPlayer 時鐘／狀態及模擬下載清理；不是 GUI pixel、音軌／實機或 storage 驗收。採用 [AVPlayerItem ready 狀態](https://developer.apple.com/documentation/avfoundation/avplayeritem/status-swift.property?language=objc) 與 [UIKit interop 觸控分工](https://kotlinlang.org/docs/multiplatform/compose-ios-touch.html)；不以 native view 接管畫布手勢。

1. 在 iPhone／iPad 驗收 MP4 的 H.264／HEVC 與 AAC 音軌，橫向／直向／rotation metadata／有無 poster 分別記錄。WebM 或其他 codec 必須逐一記錄該 iOS 裝置的實際支援結果；native player 不支援要可重試錯誤，不能因通過 MIME policy 就宣稱能播放，也不能將轉碼需求隱藏為「已完成」。初始不播放，按播放後重新授權原始 asset，完整 size／CoreCrypto SHA-256 校驗，再開啟 `.ready.mp4/.ready.webm` 私有檔案。
2. 依 Android 5.14 第 2 項驗收播放／暫停、預設靜音／手動聲音、前後 10 秒、0／結尾邊界及結束後重播。不自動 loop，未 attach Layer 不得在背景發聲；時間、聲音設定不產生 operation、不存 clipboard／scheme。無聲測試 fixture 不能證明 AAC、耳機／藍牙、silent switch、系統音訊中斷或 AudioSession 策略已完成，這些另記 QA／後續實作。
3. AVPlayerLayer 的 Fit、裁切、alpha、旋轉／縮放、Node／group／layer 重疊及播放控制 z-order 都要以實機圖片判斷。驗收 touch 選取／拖曳／多選／鎖定、VoiceOver 焦點與控制名稱，native view 不搶走畫布權、不強制彈 modal 或 sheet。動態島、安全區及 bottom sheet 遮擋的截圖附 QA Workbench，交由產品負責人簽核；native clock tests 不能代簽視覺。
4. 下載中／prepare 中切換工作區、帳號、asset、刪除、離屏及背景，應取消／釋放未交付或已交付播放器；背景通知與共用 lifecycle 都要驗收。回前景仍需明確再按播放，重新授權並從頭開始。UIView 重建／舊 view 釋放不得 detach 新 view 的 player；確保其他 Node 正在播放者不被清除。
5. 權限／ready／workspace／MIME／200 MiB gate、checksum／size、已校驗但損毀、unsupported codec、prepare timeout、沒有正 finite duration 與超過 1,200 萬實際像素都要有可重試結果。釋放後 item、pending seek、asset loading、timer、notification observers、Layer player 與 `.part/.ready.*` 不得殘留；取消在 Main dispatcher 交付點也要清理。只刪本次 private file，不碰使用者來源或其他素材。
6. BAI-003 尚缺真實 binary lifecycle，後端關閉期間不自行啟動。Web／Desktop 另依 5.16／5.17 驗收，持久 cache、完整 AudioSession／跨平台 rendition、實機與效能尚未完成；效能維持第 7 節最後階段。

### 5.16 Web JS／Wasm 影片播放

離線檢查：`./gradlew :shared:jsBrowserTest :shared:wasmJsBrowserTest :webApp:compileKotlinJs :webApp:compileKotlinWasmJs`，必要時設定 `CHROME_BIN`。8 個新增 tests 以 MediaRecorder 現場產生 32x32 無聲 VP8 WebM，在 Chrome Headless 驗證實際 pixel、native clock 與 Blob URL／取消清理；mock gateway 不能代替真實 storage／CORS，WebM fixture 不能證明 MP4、音軌或所有瀏覽器已支援。依 [HTMLMediaElement play Promise 規範](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/play)，不能在播放被拒絕時仍顯示播放成功；依 [Blob URL 釋放規範](https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static) 回收每個已建立的短期 URL。

1. Chrome／Firefox／Safari／手機瀏覽器分別測 JS、Wasm，MP4 H.264/AAC、WebM VP8／VP9 與有／無音軌、包含／省略 Duration 的錄製檔案逐一記錄。初始 poster／placeholder 不 auto play，按播放才授權下載；實際 size／SHA-256 校驗後才產生 Blob URL，不把 signed URL 直接交給 video。格式、codec、容器與 autoplay policy 拒絕必須可重試，不能把 MIME 接受誤當為可解碼。
2. 驗收播放／暫停、靜音／聲音開關、前後 10 秒、seek 邊界與結束重播；未 attach 畫布／detach 不播放、不自動 loop。Native play Promise 拒絕後按鈕不可錯誤標示正在播放，再次明確點擊可重試。包含 Safari 手勢／靜音政策、耳機／藍牙、背景音訊及 MP4 音軌的測試結果獨立簽核，無聲 fixture 不代簽聲音。
3. 影格由 native video → canvas PNG snapshot → Skia Bitmap → Compose Image，沒有 HTML video overlay；實測旋轉／縮放、alpha、clip、重疊 Node／group／layer、框選、多選、鎖定與 canvas pan。確認控制、modal／bottom sheet 不被影片覆蓋，也不被影格搶走 pointer／focus。畫質、比例、顏色、安全區及遮擋由產品負責人在 QA Workbench 附截圖簽核，不用 Headless pixel tests 代替 GUI。
4. 離屏、session／asset／workspace／帳號改變、刪除、Reduce Motion、pagehide、visibility hidden 應取消下載／prepare／pending capture，pause、移除 source、cancel native frame callback／reader／listeners／timer 並 revoke Blob URL。回到頁面需明確再次播放、重新授權、回首幀；不能恢復其他使用者的 bytes／URL。frame／prepare 回呼晚到不能重新交付已釋放 player 或 Bitmap。
5. 403、pending、foreign workspace、錯誤 MIME、size／checksum／截斷、正確 hash 但損毀、超過 200 MiB、metadata 不是正 finite duration、超過 1,200 萬實際像素與 30 秒 prepare timeout 都要拒絕並清理。允許大 network chunk 但 bridge 逐 1 MiB 送入；驗證前不得建立 URL／play，驗證後 URL 釋放恰好一次，不寫 localStorage／IndexedDB、operation／clipboard／scheme。
6. 此 pipeline 有 PNG 編碼、Base64 與 JS／Wasm copy 成本，沒有宣稱 Worker／GPU 零拷貝或記憶體／FPS 達標；效能基線依第 7 節維持最後。Desktop 影片另依 5.17 驗收；完整音訊策略／rendition、持久下載 cache、真實 storage 仍未完成，後端關閉期間不自行啟動。

### 5.17 Desktop 影片播放

本機檢查：`./gradlew :desktopApp:test :desktopApp:compileKotlin`。Native fixture 為 MPEG-4 Part 2／AAC MP4 與 VP8 無聲 WebM，驗證實際影格、時鐘、seek、暫停／重播、attach／detach、下載校驗及釋放；音訊使用 fake sink，不證明喇叭、藍牙或音畫同步通過。此節補充前述 5.14–5.16 的 Desktop 待辦，播放器已接上，產品驗收仍 Pending。

1. macOS／Windows／Linux 各記錄 OS、CPU architecture、封裝版本與 native library 載入結果，開發機成功不代簽其他平台。記錄 FFmpeg／JavaCV 發布依賴、授權與 notice 審核結果，未審核不得宣稱已可正式散布。
2. Ready MP4 H.264／AAC、MPEG-4 Part 2、WebM VP8／VP9、有／無音軌及含旋轉 metadata 分別測試。完整 size／SHA-256 校驗後才開啟本機檔案，decoder 不取得 signed URL；MIME 接受不等於 codec 可解碼。已依 [FFmpeg Display Matrix 定義](https://github.com/FFmpeg/FFmpeg/blob/n8.1.2/libavutil/display.h) 接上 affine display transform，native fixture 驗證 90／-90／180 度方向／尺寸與 seek 後影格，raster test 驗證 mirror／alpha；不能用 Node 旋轉操作替代。收集 iPhone／Android 實際直式拍攝、旋轉、鏡像、非 90 度、scale／skew metadata 檔案，逐一記錄 first frame／播放／seek／replay 比例與方向。透視、singular、truncated、超像素限制矩陣應明確拒絕並清理。Sample aspect ratio 已依 [JavaCV sample aspect ratio getter](https://github.com/bytedeco/javacv/blob/1.5.14/src/main/java/org/bytedeco/javacv/FFmpegFrameGrabber.java) 校正原始橫軸，再套 display transform；native fixture 覆蓋 SAR 2:1／1:2 與 SAR 2:1 + 90 度。額外收集真實 anamorphic／fractional SAR 檔案，確認圓形不變橢圓、字幕／人物比例、旋轉後長寬、seek／replay 與 fractional scaling 畫質；非正、非 finite 或校正後超像素限制应拒絕並清理。HDR tone mapping／完整色彩轉換尚未交付，不以這批 fixture 代簽。
3. 初始 paused／muted，明確播放才載入；未 attach／detach 不播放。測試暫停、靜音、前後 10 秒、seek 邊界、結束與重播，實際喇叭／耳機／藍牙及音畫同步獨立簽核。音訊裝置不可用、切換／拔除或關閉失敗應顯示可重試結果並釋放本次資源，不可保留假播放狀態。
4. 影格由 FFmpeg RGBA 按 row stride 複製至 Compose Image，不以 Swing／native overlay 覆蓋畫布。產品負責人測試色彩、比例、透明度、旋轉／縮放、group／layer 重疊、滑鼠／觸控／鍵盤控制、選取／拖曳／鎖定與選單遮擋，附 QA Workbench 截圖簽核。Pixel tests 不能代簽視覺。
5. 切換 workspace／帳號／asset、刪除、離屏、Reduce Motion、視窗最小化／關閉與應用程式離開逐一測試下載取消與播放器釋放；回前景應需再次明確播放。視窗 activity／epoch gate 已接上，純狀態 tests 覆蓋快速 hide/show、重複事件及 terminal close；仍需驗證真正 OS event 與 Compose 取消／釋放，不能把 attach／狀態 tests 當作完整視窗驗收。分別在下載中、prepare 中、播放中、暫停中最小化，再快速還原；GIF／影片都不可自動續播。切換焦點、開 QA Workbench／選單或其他 App 視窗但未最小化時，不應被此 gate 意外停止。釋放後 worker、audio line、decoder 與 private video 目錄不得殘留，音訊 close 異常也應清理；只刪本次 private 檔案。
6. 測試 foreign workspace／pending／錯誤 MIME、200 MiB、checksum／截斷、正確 hash 但內容損毀、非正有限 duration、超過 1,200 萬實際像素。Native prepare 目前沒有超時保證，需記錄卡住案例並列缺陷，不宣稱已通過 timeout gate。真實 storage／BAI-003 仍待驗，後端關閉期間不自行啟動。持久 cache、跨平台 rendition、完整音訊策略與效能基線仍 Pending；效能依第 7 節最後執行。

### 5.18 共用影片操作錯誤與釋放

共用 VideoPlaybackLease tests 驗證命令序列化、命令失敗時關閉、native state 未報錯時的獨立 failure signal、清理錯誤保留、取消與關閉後拒絕操作。Desktop native fixture 注入 audio write／pause 雙重故障，確認仍發布 failed／停止狀態並清理 private file；不是實際拔除音訊裝置或 GUI 驗收。

1. 各平台播放中操作播放／暫停、靜音與 seek；以開發版錯誤注入或可重現的 native device 故障觸發失敗。UI 顯示可重試結果，立即結束本次 player，不能只改錯誤文字而繼續背景播放。重新按播放才重新授權、下載並建立新 player。
2. 確認 native state 未發布 failed 但 command 拋出例外時，UI 仍離開等待、卸載 surface／控制與釋放檔案。release 再失敗也不可執行後續排隊命令；記錄原始失敗與 cleanup error，不能以吞掉 close error 宣稱裝置正常。
3. 快速連按、seek 中刪除／切換工作區、取消下載與視窗最小化等分別回歸。owner release 應等待在途命令並僅關閉 lease 一次；正常取消不應顯示新 session 的錯誤。舊 player／舊回呼不可清除或控制新 player。
4. 操作錯誤與 close error 不得造成 Compose effect 未處理例外而關閉 APP。實際停聲、原生資源回收、控制焦點／遮擋與重試交互由產品負責人附證據簽核，common fake player tests 不代簽。

### 5.19 Desktop codec 與封裝 runtime

1. 執行 `./gradlew :desktopApp:test :desktopApp:createDistributable`。新增 fixture 現場產生真正 H.264／AAC MP4，驗證 frame、seek、clock、結束及 PCM fake output，不能代替實際喇叭、完整 H.264 profile／level／色深與真實檔案驗收。
2. macOS 建置後執行 `desktopApp/build/compose/binaries/main/app/cg.creamgod.boarderless.app/Contents/MacOS/cg.creamgod.boarderless --check-media-runtime`。預期退出碼 0 且輸出 `MEDIA_RUNTIME_OK decoders=h264,aac,mpeg4,vp8,vp9,opus,vorbis skia=rgba audio=pcm16-stereo`。其他 OS 應對該平台封裝的 launcher 傳同一旗標，不使用 Gradle/JDK 的 classpath 代替 packaged runtime。保留 OS／architecture、建置版本、命令與輸出作為 QA 證據；本輪僅 macOS ARM64 實際通過。
3. 診斷在一般 APP UI 建立前離線退出，只檢查 native 載入、decoder availability、Skia 真實 1px RGBA 與 audio format classes，不開視窗、讀工作區、連後端、要求音訊權限或播放聲音，也不接受 asset 路徑／server credentials。`MEDIA_RUNTIME_OK` 不表示每一個 decoder 都已解碼正式素材、喇叭可用或 GUI 放行。
4. 安裝包 DMG／MSI／DEB、簽章／公證／首次開啟、安全提示、離線安裝、依賴缺失、native notices／授權審核及 Windows／Linux runtime 仍需獨立記錄。從封裝 APP 實際依 5.17／5.18 回歸匯入、播放、圖層、重試與清理，再由產品負責人簽核視覺／遮擋；不能用 `createDistributable` 成功代簽安装及產品放行。

### 5.20 Android 影片音訊焦點

採用 [Android Audio Focus 規範](https://developer.android.com/media/optimize/audio-focus)：API 24–25 使用 legacy request／abandon，API 26+ 使用同一 acquisition 的 AudioFocusRequest／AudioAttributes；不接受 delayed gain，設定 pause-when-ducked。Android 15／target 35+ 的非前景 request 可能被系統拒絕，不能靠假的「正在播放」狀態繞過。Host tests 只驗證 fake port 的策略／生命週期，System AudioManager 的實際回呼仍需裝置驗收。

1. 分別以 API 24–25、26–30、31+、35+ 裝置測試：初始 muted、不要求焦點；有音軌且明確 unmute／play 才要求，GRANTED 後才開啟 volume／start。暫停時 unmute 不應搶焦點；無音軌素材也不要求。先播放其他音樂再開影片，確認符合系統焦點結果與中斷提示。
2. Focus DENIED／FAILED 時維持停止，顯示「音訊中斷・點擊播放重試」，不接受 delayed gain、不在背景晚到 GAIN 時自動播放。使用者再次按播放才重新要求；也可改回靜音再明確播放。驗收權限角色／鎖定不影響本機播放控制。
3. LOSS／LOSS_TRANSIENT／LOSS_TRANSIENT_CAN_DUCK 都暫停並將 wantsPlay 清除，放棄焦點；GAIN 不自動續播。靜音、暫停、surface detach、結束、fail／release 都應放棄，API 26+ 用原 request instance。測試兩個 Node、其他音樂、通知／導航及通話；舊 acquisition 回呼不得暫停新 acquisition／已釋放 player。
4. 下載／prepare／播放中切換帳號、工作區、刪除與背景，確認焦點／播放器／檔案釋放。focus abandon 或 MediaPlayer.release 失敗也必須執行其他清理，close 保持 terminal，不因 callback 例外關閉 APP。Android becoming-noisy 策略另依 5.21、iOS AudioSession 依 5.22 實機驗收，不以 audio focus tests 代簽。
5. `./gradlew :shared:testAndroidHostTest :shared:assembleAndroidDeviceTest :androidApp:assembleDebug` 檢查 Host／APK；本輪新增 6 個策略 tests，不是 AudioManager callback 或 native 聲音 tests。無連接裝置時維持 Pending，附實機／喇叭／耳機／藍牙、控制與遮擋證據至 Workbench；後端關閉期間不自行啟動，真實 storage 依 BAI-003 另驗，效能仍最後執行。

### 5.21 Android 耳機拔除與音訊路由

依 [ACTION_AUDIO_BECOMING_NOISY](https://developer.android.com/reference/android/media/AudioManager#ACTION_AUDIO_BECOMING_NOISY) 監聽可能轉向喇叭的事件。僅在有聲播放取得焦點時動態註冊 receiver，使用 application context；API 33+ 使用 RECEIVER_NOT_EXPORTED，舊版本使用 legacy 註冊。Host fake-source tests 驗證訂閱生命週期與晚到事件，不等於實際系統 broadcast／聲音／API 分支驗收。

1. 分別以 API 24–25、26–32、33+ 裝置，記錄型號、OS、素材音軌與有線／USB／藍牙耳機。明確開啟影片聲音後拔除或斷開耳機，確認收到系統 noisy 事件時立即暫停、音量歸零並顯示中斷提示，不繼續從喇叭發聲；錄影與聲音判斷由產品驗收者簽核。系統未發送事件的路由變更須另記錄，不能據此宣稱所有路由皆已覆蓋。
2. 重新接上耳機不得自動續播，使用者明確按播放才重試。靜音與無音軌播放不註冊 noisy receiver；不得因其他 Node 的舊訂閱事件打斷目前靜音播放器。
3. mute／pause／detach／ended／fail／release 與切換工作區、帳號、刪除、背景都應解除本次訂閱。反覆播放／停止與兩個 Node 切換，確認無重複 receiver；上一 acquisition 的晚到事件或已排入主執行緒的 callback 不得暫停新 acquisition。
4. 註冊或解除訂閱失敗須記錄，仍嘗試放棄焦點與執行播放器／私人檔案清理；不能以 fake-port 測試通過宣稱系統 receiver 已成功解除。驗收需檢查錯誤提示、重試與 APP 不崩潰。
5. 本輪新增 4 個 Host 策略 tests，Android Host 合計 303 個通過，APP／Device Test APK 建置通過；Device APK 尚未於裝置執行。將證據附在 Workbench 的影片／生命週期／錯誤題，註明 SOP 5.20–5.21；實機未驗維持 Pending。後端保持關閉，storage 與效能驗收分別依 BAI-003 與第 7 節排程。

### 5.22 iOS 影片 AudioSession／中斷與路由

依 [Apple 音訊中斷規範](https://developer.apple.com/documentation/AVFAudio/handling-audio-interruptions) 與 [路由變更規範](https://developer.apple.com/documentation/avfaudio/responding-to-audio-route-changes)，有聲播放才主動設定 Playback／MoviePlayback category 並啟用 session；其他狀態解除本 APP 的持有者。AVAudioSession 是 APP 共用，採單一有聲 Node 持有者，舊 Node 先 mute／pause 再釋放，不能讓舊 Node 的晚到 release 解除新持有者。去啟用使用 NotifyOthersOnDeactivation，不表示其他 APP 一定自動恢復。

1. 實機記錄 iPhone／iPad 型號、OS、素材音軌／codec、其他音樂 APP 與輸出裝置。預設靜音、paused unmute、無音軌影片不得主動啟用 session；實際其他音樂是否受 AVPlayer 的系統自動 session 行為影響，需另以實機確認，不能由 fake activation 推論。明確播放有聲素材才啟用，失敗維持暫停與中斷提示，使用者可改靜音或明確重試。
2. 通話／Siri／系統中斷 Began 必須暫停、靜音並清除 wantsPlay；Ended 即使含 ShouldResume 也不得自動續播。解除中斷後按播放才重試；測試 session 去啟用失敗、背景／鎖屏與返回畫布，不能偷偷恢復聲音。
3. 有線／USB／藍牙耳機拔除的 OldDeviceUnavailable 必須暫停，保護使用者不立即從喇叭播出；NewDeviceAvailable、Override 不以拔除事件處理，也不自動恢復。記錄實際系統 reason／輸出裝置與音訊證據，無 broadcast／notification 的情況另列問題，不能聲稱所有路由覆蓋。
4. 同時放置兩個有聲 Node，開啟第二個時第一個暫停；釋放第一個不得關閉第二個 session。靜音／pause／detach／ended／failed／release 解除本次持有者與 observers；切換帳號／工作區、刪除／背景仍清理 Layer、timer、asset loading、seek 與 verified file。cleanup 單項失敗仍執行其他清理，已 release 為 terminal。
5. 依 [Apple 媒體服務重置規範](https://developer.apple.com/documentation/avfaudio/avaudiosession/mediaserviceswereresetnotification)，MediaServicesWereReset 必須使本次 native player 失效（含靜音影片），交由共用 owner 清理，不得繼續重用舊 AVPlayer 或自動重新啟用；使用者重開才建立新播放器。實機可在 iOS「設定 → 開發者 → Reset Media Services」驗證，不能把手動 post notification 當成真正 daemon reset。Foundation notification handler 不得把例外拋回系統；晚到或錯誤 session 的路由／中斷通知不得中斷新持有者。
6. 本輪 6 個 fake-port 持有者 tests、3 個真實 Foundation observer／注入 activation tests，以及 2 個無音軌 H.264 原生 player tests（無音軌 gate、合成 reset 通知後 terminal／清理／明確重開）通過；iOS Simulator 合計 309 個通過，iOS ARM64 Main 編譯通過。這些不涵蓋實際 setActive／硬體音軌／通話／耳機／真實 media-server reset／GUI：簽核仍為 Pending，將 SOP 5.15／5.22 與證據寫入 Workbench 影片／生命週期／錯誤題。後端保持關閉，效能維持第 7 節最後階段。

## 6. 多人即時協作專項

APP 已接上 optional remote notification → 權威 REST refresh 入口；目前 BackendWorkspaceRepository 未提供 stream，正式執行仍是 3 秒 REST polling。離線 tests 可驗證通知範圍／sequence gate、join wakeup、通知合併、stream end／failure fallback 與 observer cancellation，但不證明 WebSocket、真正 operation fan-out、Presence／Cursor／Selection 或 reconnect transport 已交付；BAI-009 的 URL／auth／message fixtures 尚待後端合約，後端關閉期間不得自行啟動。

WebSocket／Presence／Cursor／即時廣播交付後，至少以兩個獨立使用者及兩個 client 驗收：

- Join／leave、在線名單與角色正確。
- 游標與選取屬於暫態資訊，不寫入正式 operation history。
- A 的正式操作在 B 即時出現，版本與 operation ID 一致。
- 同物件同時修改有可預期的接受、衝突或重試結果，不可靜默覆蓋。
- WebSocket 中斷、重連、遺失 ack、重複及亂序訊息可由 server sequence catch up。
- REST catch-up 與 WebSocket fan-out 產生相同權威狀態。
- 權限撤銷後立即停止 mutation 與 Workspace subscription。
- Presence 或 Redis 中斷不得造成已確認正式內容遺失。

## 7. 效能測試時程

效能測試安排在目前功能與整合進程的最後階段，不得插隊阻塞素材及多人即時協作的功能交付。開始條件為：

1. 本輪核心功能及後端合約穩定。
2. 產品視覺與遮擋完成一輪簽核。
3. 主要回歸通過，沒有 P0／P1 功能缺陷。
4. 大型 Workspace 測試資料與量測裝置固定。

屆時再建立啟動時間、載入時間、平移／縮放流暢度、記憶體、CPU、網路、同步延遲、素材處理與多人 fan-out 的正式基線。基線數字需以實測建立，不在功能尚未穩定時臆定門檻。

## 8. 缺陷嚴重度

| 等級 | 定義 | 放行規則 |
|---|---|---|
| P0 | 資料遺失、權限外洩、安全問題、無法啟動或大範圍不可用 | 立即停止放行 |
| P1 | 核心流程無法完成、同步狀態錯誤、主要操作被完全遮擋且無替代入口 | 修正並回歸後才能放行 |
| P2 | 有替代路徑但體驗明顯受阻、局部遮擋、主要無障礙缺陷 | 由產品負責人決定是否延後放行 |
| P3 | 文案、間距、非阻塞視覺或低頻一致性問題 | 可排入後續版本，但需記錄 |

## 9. 驗收紀錄範本

Desktop 開發版可設定 `BOARDERLESS_QA_MODE=true` 開啟內建 QA Workbench。工作台依本 SOP 提供問卷、產品簽核標記、BoarderLess 視窗截圖、外部手機截圖附件，以及自包含 HTML／PDF 產出；預設保存於使用者的 `Documents/BoarderLess QA`。未設定旗標時不顯示入口，表單與截圖不寫入 Workspace 或後端。

素材問卷操作流程：

1. 以 `BOARDERLESS_QA_MODE=true ./gradlew :desktopApp:run` 開啟開發版，進入 QA Workbench，先填批次、版本、平台／裝置／方向、測試人、範圍、後端及自動證據。
2. 問卷新增 8 題：真實 asset lifecycle、GIF、影片控制／聲音、display metadata、生命週期、錯誤／重試、封裝 runtime 與產品媒體視覺。每題顯示 SOP 章節與證據提示；預設一律 Pending，建置／fixture 通過不自動勾選。
3. 一題一題按該平台的 SOP 執行，在「項目備註／證據」記錄素材／codec／SAR、步驟、觀察、輸出／退出碼與相關 screenshot caption。後端關閉或合約缺失是 Pending，不是通過；真正不在本批範圍者才選 N/A 並說明排除原因。效能題僅確認延期安排，不代表效能實測通過。
4. 擷取 APP 視窗或匯入手機截圖，以 caption 指明題目／步驟／裝置。影片視覺與遮擋題由產品負責人在備註寫簽核人、日期、結論；pixel、clock、PCM fake output 與 runtime check 不代簽視覺／聲音。
5. 匯出 HTML／PDF 後確認各題的 SOP reference、evidence hint、選取結果、備註與附件都保留，長問卷沒有文字裁切或遺失。內容匯出有單元測試，新增問卷的實際 PDF 排版仍由產品負責人驗收。總結含 Failed 即 Failed，無 Failed 但仍有 Pending 即 Pending，不能以自動證據覆寫未驗收狀態。

```markdown
## QA 批次：<名稱>

- 版本／commit：
- 日期：
- 平台／OS／裝置／方向：
- 後端版本與 Workspace：
- 驗收範圍：
- 排除範圍：
- 自動測試結果：
- 手動功能結果：
- 待產品視覺驗收項目：
- 產品視覺／遮擋簽核：
- VoiceOver／無障礙結果：
- 缺陷與嚴重度：
- 證據位置：
- 最終結論：
```

截圖與錄影需標示裝置、方向與操作狀態；API 證據需保留狀態碼及去識別化結果，不得保存密碼、token、signed URL 或其他機密。

## 10. 放行條件

- 所有範圍內項目都有明確狀態。
- P0、P1 為零。
- 自動測試及必要平台建置通過。
- 功能、資料、權限、同步與重開流程通過。
- 視覺與遮擋已由產品負責人簽核，或明確標記為本次不適用。
- VoiceOver 等必要無障礙項目已有實機結論；未完成時不得誤寫為已驗收。
- 已知 P2／P3 有負責人、處置決策及後續追蹤位置。
