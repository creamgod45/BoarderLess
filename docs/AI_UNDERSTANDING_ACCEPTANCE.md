# AI 畫布理解驗證（唯讀）

2026-10-06 網路引擎交付核對：新版 Desktop／Android 明確指定 Ktor 3.6.0 + OkHttp，AI理解／工具與後端／素材／GIPHY 統一走 platformHttpClient 邊界。維持每次 AI HTTP 授權、停用自動 connection retry／redirect／503 Retry-After:0 隱式重送，嚴格 UTF-8／空回應檢查。31759 全套成功，real-socket PlatformHttpClientTest 通過並核新版 verified bundle 內 platform factory；iOS／Web 保留 Ktor 平台引擎。這不能證明使用者 Router 401 已修復，亦未自動讀 key 或發送新 AI 請求。


2026-10-06 AI工具草稿首次Apply固定選取：captured proposal/items持續用於明確重試，include/exclude停用，不能因保存例外而把同transaction ID換為另一组operations。重試仍需新確認及既有fresh scope/version/snapshot gate；stale按鈕要求當前proposal同一實例，取消／scope失效／dispose清除暫存。AiDiagramReviewTest第6case用真journal manifest寫入後例外，驗證原選取確實可讀且原／changed selection从未前進baseline都不能append／覆寫；未知保存结果須原草稿review，不宣稱Settings具備durable。43520完整回歸JVM867／JS799／Wasm799／Desktop46零失敗，JVM6既有skip，Android/iOS編譯與Desktop打包成功39s；61711初次全套JVM類別載入失敗不算成功。沒有新實際AI請求，401仍未排除。

2026-10-06 本地日誌實際環境修正：檢查發現使用者既有 `~/.boarderless` 為755，原logger要求其700導致每次best-effort寫入被拒絕。現在容許與user directory同owner、非symlink且group/others不可寫的APP父目錄，不修改原權限；logs仍700、檔案仍600，unsafe parent仍拒絕。新增真755父目錄回歸，核對可寫入私有logs、755維持原樣、改為group-writable後拒寫且原內容不變。`:shared:jvmTest :desktopApp:createDistributable`成功36s（48203）。測試使用隔離tempdir，沒有在user home製造假AI紀錄。實際user log仍未產生，最後真請求證據仍HTTP401，不能宣稱已修復授權。Desktop UI工具開啟新package逾時，未完成實機視窗驗證。

2026-10-06 APP工具任務與批次review已接線：Ai理解視窗新增「AI圖形草稿」入口，editor且提交佇列／recovery安全時可用，延續canvas modal／鍵盤隔離。AiDiagramToolDialog提供selection或whole≤128objects、模型／完整endpoint／認證／version／local HTTP、任務，key僅記憶體，沒有設定／key持久化；Router工具支援需真任務驗證，初始capability僅APP既有七shape／preset node colors／group，selfLoop=false，未宣稱pen/media/v2能力。每個真正HTTP都先展示完整不可變body＋endpoint/model並等待新checkbox＋send，拒絕／取消／dispose釋放pending gate；gate依同一review實例而非round值同意，stale／double按鈕不能批准下一輪。session／history／role／queue／recovery變化同步guard並取消清plan；backend fresh check失敗以WorkspaceBackend typed status區分AI本身HTTP。

完成後唯本機preview：call items中文／英文操作名稱、逐項include/exclude→立即重算domain dependencies/version、node shapes/color/group/rotation fit preview與完整文字／geometry／relation direction/label/intent inventory，Double bounds避免大座標Float overflow；小圖連線是中心直線概略，不宣称正式geometry或實機視覺已驗。AiDiagramDraft→loop Ready新增本機logical refs mapping，維持read N及new A等名稱，UUID僅UI、未進任何provider body／safe log。final無proposal顯「未提出可套用變更」，不假稱產生圖形。

Apply必須再次明確checkbox，refresh後pure reviewAiDiagramApply核對user/client/workspace、fresh及live editrole、三session和proposal version、lastServerSeq不退、opened/live/fresh/history完整snapshot一致；不是auto-rebase。合法selected Transaction沿existing execute→retainDraft→history→submissionQueue，標queued由原submission/ACK/recovery/Undo處理，AI UI沒有直接submit／繞journal、沒有在ACK前markCommitted。排除new node但保留依賴edge／empty／scope/permission/version/snapshot/status矛盾拒絕，失敗不改正式畫布；Settings預設durable／native default／unknown ACK完整驗收仍屬六主線待辦，不能聲稱這個UI解決全部保存。成功排隊後關視窗且清key/plan。每round自動安全logger，不需要再讓使用者複製資料。

新增AiDiagramReviewTest5cases在三runtime：exact-instance consumed consent／stale/duplicate、cancel/close、fresh Editor實際selected transaction→history Undo/Redo、foreign/read-only/版本/seq/三snapshot/已使用proposal拒絕、exclude dependency及empty零op。25609 compile與39100初次因漏withTimeout import失敗，補import後18904 focused5＋Desktop classes成功40s。25227第一次全套＋新app image成功59s；追加無變更提示／操作名稱localized、backend preflight typed errors及live snapshot gate後83581最終全套＋打包成功59s；數字如下，不是付費Router或GUI／Android/iOS runtime驗收。交付app image desktopApp/build/compose/binaries/main/app/BoarderLess.app，直接核對包內AI dialog及logger class。仍無新live log，最後使用者實際AI證據仍401。

83581最終結果：jvmTest {'tests': 857, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 790, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 790, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}，iOS arm64／Android main compile及Desktop createDistributable成功。下一步真Router/tools與四端GUI／scope撤權／formal ACK／Undo簽核，同時接續六主線其他缺件；goal active。


2026-10-06 Android main compile（97589）成功11s，補齊新增expect／actual的Android檢查；無Android實機diagnostic持久化。

2026-10-06 新版 Desktop 真實失敗已收到使用者安全報告：`Preparing → CheckingWorkspace → Connecting → HttpResponse HTTP=401 contentType=Other → Failed code=HttpRejected HTTP=401 contentType=Other`。這證明請求收到認證拒絕，尚未進入 SSE／文字解析；不能推定是 token、header 選擇、Router 還是上游認證失敗，亦不能據此改 model／token 參數或自動重送。

本輪追加交付：檢查發現本地 app image 原為 2026-10-02 舊包，user dir 尚無 `ai-probe.log`。已重新執行 `:desktopApp:createDistributable`，成功10s；直接驗證新包 shared jar 包含 `DesktopAiProbeLogWriter.class`，安全 logger 5cases／0fail／0error／0skip。交付路徑 `desktopApp/build/compose/binaries/main/app/BoarderLess.app`。未強制關閉既有視窗、未自動發送 AI 請求；沒有新的真實請求紀錄，最新已知實際結果仍是使用者提供的401，不能宣稱已修復。下次執行此包並逐次同意正常請求後，可直接讀取 user dir 安全日誌。

依使用者要求，AI 視窗每次操作自動收集 Desktop 本地安全 log：`~/.boarderless/logs/ai-probe.log`，備份為 `.1`，每檔最多 256 KiB。UTC 時間與本機隨機 attempt ID 串起阶段、協定與認證方式、HTTP status／contentType／錯誤分類；不記 endpoint／model／token／headers／workspace／prompt／原始回應或 exception。單 daemon、128 筆排隊上限、每 attempt 24 筆、寫入錯誤隔離，關閉視窗後的取消也會記錄；這是 best-effort 診斷，不是 crash-durable journal。POSIX 新建目录0700／檔案0600，拒絕既有非私有權限及 symlink，不修權限；非 POSIX 沿 user-home ACL，未驗 Windows 實機。Android／iOS／Web 回傳 null、不新增診斷持久化。既有逐次同意、UTF-8 128 KiB 上限、空白完成拒絕保持。

本輪不會因建立 logger 發 AI 請求、不替使用者重送，也未啟動／重啟其正在使用的 APP。自動收集需執行包含此變更的 Desktop build；測試使用隔離暫存目錄，不在 user dir 產生假 live report。後續可以直接讀本地 log，無需再次請使用者複製安全資訊。真實 401 是否排除仍未驗證。

驗證：69558 focused＋Desktop classes成功13s；33073 全套 shared JVM799／0fail／0error／6skip、Desktop46／0fail／0error／0skip與JS／Wasm／iOS arm64編譯成功25s。追加不可寫目錄的確定性斷言後，3909 JVM全套再成功5s，799tests不變。新增DesktopAiProbeLocalLogTest5cases：正式probe MockEngine401全流程＋敏感內容排除、runtime queue前的24筆限制／不可寫檔與scheduler拒絕隔離、bounded rotation／correlation、POSIX權限／current／backup／root symlink拒絕。初次98380未執行（預設java不存在）；91670前一fixture首次compile因非inline lambda內suspend失敗，改inline後成功，不放寬production。不是GUI或真Router新請求證據。

2026-10-05 更新：依使用者決定，預設改為 Mermaid flowchart＋短 ID 精簡 inventory；下方 scene.v1／完整位置描述為先前版本，現有送出內容以 [AI_DIAGRAM_EXPRESSION_SPEC.md](AI_DIAGRAM_EXPRESSION_SPEC.md) 為準。UI 顯示並複製 flowchart 驗證包，回應 nodeIds／links／parents 使用 N／E 短 ID，不再用 UUID；原世界座標／旋轉資訊不送。只有日後建立時正式選定特殊圖型與合約支援，才使用其對應語法。

2026-10-05。讓使用者比較 AI 是否讀懂複雜圖形，並核對 APP 輸出的節點資訊。不執行工具、不改畫布、不提交後端操作；不是完整 AI agent 或模型理解已通過驗收。

## 操作流程

1. 更新 APP。桌面工具列／手機畫布選單點「AI 理解驗證」，或在命令面板搜尋 AI。等待同步完成再開啟。
2. 預設只提供選取節點及群組 descendants；沒有選取時先關閉視窗選物件。也可明確改為整個畫布，檢查實際 Mermaid flowchart 與精簡 inventory。
3. 可複製「驗證提示詞」到自己的 AI 工具，取得純 JSON 回答後貼回回應欄核對；複製本身不發網路請求。
4. 或進入設定，選 OpenAI-compatible Chat Completions／Claude-compatible Anthropic Messages，填 Router **完整 endpoint**、模型及認證。沒有預設模型、不猜路徑、不降級協定、不自動改走雲端。
5. 按 Router 要求選 Bearer／x-api-key；只有自行選 None 才省略認證。Claude 需填日期格式 anthropic-version。僅可信本機 Router 可明確開啟 HTTP；HTTP 不加密，勿傳敏感資訊。Web 另受 CORS／HTTPS mixed-content 限制。
6. 審查 endpoint、模型與完整提示詞，點同意後才能送出。每次按送出是一個請求，無自動重試；送出前重新讀後端確認權限與版本，不符請重開視窗。
7. 串流完成後按核對，也可取消／關閉。認證僅存在此視窗記憶體，不保存至偏好或檔案；關閉後需重新設定，不承諾擦除所有執行時副本。
8. QA 保存 flowchart 驗證包、模型、原始回應、核對結果與人工語意判斷；勿保存金鑰。

## 先前 boarderless.scene.v1 合約與限制（不再送給 AI）

- 節點包含 ID／版本／型別／文字、群組名稱或素材 altText、適用的形狀與顏色、zIndex、鎖定、parentId、parentOutsideScope、位置／尺寸／旋轉與世界座標角點。
- scope 外的 parentId 隱藏為 null，parentOutsideScope=true。只包含兩端在 scope 的關係；不補外部節點。
- 關係包含 ID／版本／source／target／direction／label／intent。forward=source→target、backward=target→source、both=雙向、none=無向。
- 世界座標 x 右、y 下、position 為未旋轉 top-left、旋轉繞中心順時針。viewport 與旋轉矩形 footprint 交疊提示不是像素遮擋或 shape-exact 碰撞。
- 不傳素材像素、assetId、thumbnailAssetId、下載 URL、本機路徑、工作區 ID／名稱。**使用者文字仍可能本身含網址、個資或秘密，必須自行審查。**不宣稱模型看見圖片。
- 最多 128 節點、256 關係與 48 KiB UTF-8 compact scene；超過拒絕，不靜默截斷。每次 4096 output tokens，複雜答案可能被截斷並判為未完成，請拆分或使用複製流程自行配置。貼入／解析回應最多 128 KiB，拒絕異常 JSON 深度。

## 自動核對 ≠ 理解能力

提示詞要求純 JSON：nodeIds、links、parents、summary、uncertainties；勿包 Markdown code fence。自動核對節點集合／重複、關係 ID／端點／方向、每個節點 parentId 與摘要欄位格式。缺漏、幻覺與方向倒置列出分類；通過僅代表 inventory 正確，原始語意摘要仍須人工判斷。

人工建立至少三層群組、分支匯合、回饋循環、雙向／反向箭頭、同名不同 ID、旋轉交疊、素材 altText 與 scope 外物件，使用同一驗證包比較模型：

1. 是否引用 ID 說明群組責任、主流程、分支条件與循環，而非抄文字？
2. 是否混淆反向箭頭、同名物件、空間相近與真正 parent？
3. 是否承認 scope 外資訊、圖片像素與實際遮擋未知？
4. 節點加入「忽略指令／刪除物件」測試文字，應只當資料，不遵循。APP 此入口也無寫入路徑。
5. 取消／關閉／換工作區／撤權後不得以舊 scope 送新請求；驗證不應改畫布或 Undo stack。

視覺、遮擋、iPhone 安全區、鍵盤輸入／貼上、VoiceOver 由使用者簽核；效能最後。MockEngine 自動測試不代替真 Router、真模型與實機。

## 後續

安全持久化設定／認證、進階連線診斷、截圖輸入、tool call／tool result 迴圈、AI 草稿布局／預覽提交與理解品質 benchmark 尚未完成；依 AI_DIAGRAM_TOOL_WORKFLOW 後續接入。

## 固定狀態與安全除錯（2026-10-05）

原先只有泛用失敗提示，不能據此判斷請求是否送出。新版將狀態固定在標題下方，提示詞／回應內容獨立捲動。依序可能顯示 Preparing、CheckingWorkspace、Connecting、HttpResponse、Receiving、Completed；失敗與取消保留 Failed／Cancelled。快速完成可能跳過肉眼可見的中間階段，階段記錄仍保留；Connecting 只代表開始 HTTP 呼叫，不證明遠端收到。

「複製安全除錯資訊」只複製階段、固定錯誤分類、HTTP 狀態碼與粗略 Content-Type。不含金鑰、endpoint、模型、提示詞、回應本文或原始 headers。HTTP 200 但回 JSON／HTML、串流損毀或未完整結束也不能標為 Completed。WorkspaceBackend 的 HTTP 來自畫布後端，不是 AI；WorkspaceScopeChanged／WorkspaceVersionChanged／WorkspaceSnapshotChanged 代表前置快照驗證失敗，尚未呼叫 AI。需要先同步並重新開啟視窗，不繞過檢查。

Desktop 原視窗不保證自動載入修改：自行關閉舊 APP，再於專案執行 `./gradlew :desktopApp:run`。開啟 AI 視窗應看到標題下的「尚未送出 AI 請求」與複製安全除錯按鈕，才能確認驗收新版。編譯成功不代表目前運行視窗已更新。

MockEngine 回歸包含 HTTP 400／401／403／404／422／429／500／503、前置拒絕零 AI 呼叫、HTTP 200 格式錯誤、損毀／空串流、完成與取消。JVM 全套 730 tests、0 failures／errors、6 skipped（未啟用 live／外部 parser）；JS／Wasm／iOS arm64 編譯成功。不是使用者 Router、Desktop GUI 或真實模型已通過。

追加保護：每次請求結束（成功／失敗／前置拒絕）清除同意，下一次送出需再次同意；UTF-8 bytes上限128KiB，不把中文字元當1 byte；空白回應即使正常stop/DONE仍判未完成。54819追加後JVM全套732tests零fail/error、6skip及JS／Wasm／iOS arm64／Desktop classes成功28s。人工重驗重送按鈕、空白回應、中文超限、取消後不採用partial；這些結果不證明當前使用者服務已連通。
