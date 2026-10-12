# BoarderLess 產品 QA 品質檢驗 SOP

## App 原 writer 恢復視窗（最後集中驗收）

切換儲存模式／離開畫布、候選 media 構造失敗時驗原 repository 被保留；正常 owner drain 只能等待，不得自動重試釋放或送網路請求。close 當下成功、背景 operation 稍後 release 失敗也須保留 handle 並提供恢復入口。待確認時限制畫布編輯、Presence 分享及再次模式切換，不能反覆構造新 writer。

中央視窗可關閉後重開；關閉只取消等待，不刪資料或丟失原 owner。查原排空不寫入；僅 Failed 原 owner 允許明確 retry 原 release callback，await 成功才移除記錄。多個原 handle 必須分別確認，不能一次全部清除。無 capability 的關閉失敗不得空成功。這只是目前 App 生命週期的恢復，不是跨重啟持久化 handle 或全 writer 排他證據。手機安全區、文字可讀性與遮擋由使用者判定。尚未執行驗收。

## Repository 原 writer 排空與釋放恢復（最後集中驗收）

Android／iOS／Web 的原 repository 必須先 close，再 awaitOwnedWriterDrain；未關閉或沒有原 factory capability 應拒絕。關閉後新 prepareWriter 不可開始，await 中關閉也不可繼續採用 owner。已有背景操作完成前不得宣告 drain 或建立替代 owner。

Web 測 Kotlin cancellation 早於 IDB complete／abort：原實體 ticket 應保留直到終止，await 不可提早返回。釋放 callback 失敗後僅明確 retryOwnedWriterRelease 重試原 owner 原 callback，不能恢復寫入、重送交易或清空計數；重試後再 await。取消／timeout 保留原 owner，不自動 takeover。Desktop 缺 capability 須拒絕，不空成功。這些只證明已 accounting 的 writer，all-writer 排他與 activation／HTTP 全生命週期仍須分別驗收。目前未執行。

## Desktop 舊方案簿恢復（最後集中驗收）

Desktop server 方案簿開「恢復 Desktop 舊方案簿」：開窗不讀取、不 HTTP；手動讀取後每頁 8 筆。審閱原始／副本完整分頁 JSON、結構預覽及來源 workspace，確認後才保存一份定義副本。舊 Settings、素材及畫布都不得變更，雲端上傳另走獨立授權流程。

驗缺 payload 殘留 metadata、超 count／單筆或總 UTF-8 限額、非法字元、未知 schema、媒體缺來源；應拒絕，不猜目前 workspace、不自動修復。來源變更／消失須重新讀取審閱。stage／save 取消、scope 切換、保存後回應遺失時保留同 requestId、不再送新 ID；關窗後用待完成的本機匯入查原任務。新 namespace 空白、舊索引空白都不得宣稱沒有舊資料。此來源重讀仍不是同 process／跨 process all-writer 排他證明，完整遷移另驗。視覺可讀性與遮擋由使用者判斷；目前未執行驗收。

## Desktop 儲存模式與雲端方案簿宿主（最後集中驗收）

普通Desktop啟動Local，即使上次Server；--server明確啟動仍Server，external launch mode優先。模式UI切換準備repo／media後才交接，失敗保留原畫布，不遷移内容；同模式no-op不重建client／storage。Local素材缺gateway不得HTTP fallback；Server素材用已選repo endpoint，不回預設其他服務。

server-v1/schemes私有持久方案庫应提供cloud功能入口与identified save，Local仍local/schemes，不能混讀／自動合併。舊Settings library不刪／不自動搬移；需另外驗明確匯出／匯入與namespace說明，空新庫不證明舊資料沒有。activated工廠存在不等正式WS啟用；ownership／scope／cutover另驗。全部功能完成後集中Desktop與跨端驗收，效能最後。

## AI UTF-8 增量緩衝原狀保持（最後集中驗收）

測多delta ASCII／中文／emoji與pending high配對；下一delta low只消耗原4bytes預留，不再次加4。fragment含非法low／high後非low或超上限時，原完整文字、pending high与reservedBytes保持不變，不發布部分fragment。空delta不清pending，finish仍拒未配對；clear重置所有狀態。

tool progress只包含新完成字元，UI拼接不得重複前文；arguments仍按原raw JSON UTF-8 bytes並保持strict parser限制。這不是效能通過證據，最後集中correctness／跨協定驗收，benchmark安排在全部功能完成之後最後執行。

## 背景／網格保存不明的原交易審閱（最後集中驗收）

背景首次Apply後readOnly且非busy、網格attempted且非busy，應有固定區原交易審閱按钮；只關對應picker/dialog及preview，再核captured actor/client/workspace進中央原記錄流程。不存在重新Apply、換operationId／beforeStyle或清原durable記錄；Close不等於rollback。

其他color picker不提供onReviewPending時維持原UI與操作；busy不能觸發審阅交接。原style CAS、遠端另改grid/background與Undo／Redo仍須驗，不將preview或receipt查詢當正式還原。全部業務完成後集中驗收，視覺由使用者、效能最後。

## 鋼筆／時序圖保存不明的原交易入口（最後集中驗收）

首次commit後false／exception／取消，鋼筆與時序固定區須能轉入中央原交易審閱，關掉的僅當前editor；captured owner與當前actor/client/workspace不同不能顯示新scope資料。local reload仍原pending／draft流程，不發替代請求或resend。AI同一scopeguard也驗。

時序commitAttempted後Close不稱「丟棄」或rollback，不刪任何已retain紀錄；commit前未保存內容仍有原discard確認。原紀錄未能保存時應維持未確認／無證據，不用空清單聲稱原交易不存在。最後集中跨端fault驗收，視覺由使用者、效能最後。

## 原交易中央審阅與AI未確認交接（最後集中驗收）

AI Apply Unconfirmed後可從固定區進入原交易視窗，也能從pending狀態按鈕開啟；只關原AI視窗，不清durable journal／wire／receipt。原actor/client/workspace不匹配不可導向新scope紀錄。busy／queue未完成不能操作，fixed讀取狀態與Close可見，背景畫布輸入／status遮擋停用。

「讀本機原紀錄」只用既有pendingChange／pendingDraft；「查原收據」只receipt讀取、不Fence或resend；draft review走既有正式審核。所有操作使用原ID，不把無紀錄UI空值或receipt查到當完整ACK／授權替代請求。Close不刪紀錄／rollback。最後集中跨端／fault驗收，視覺由使用者、效能最後。

## 原 writer 釋放失敗恢復（最後集中驗收）

對原owner idempotent onDrained注入釋放失敗，awaitClosedAndDrained仍拒絕；另candidate不可claim。只有原owner已closed、實體accounted operations=0、settlement Failed且仍佔active，明確retryFailedRelease才重試原callback；不得重開writer／歸零操作數／換identity／自動retry，成功後再await確認。兩個並行retry只允許原狀態CAS勝者，其餘拒絕；取消或再失敗保持原owner供明確處置。

Web事實：目前同tab lease tokens共用一把底層WebLock，跨tab ifAvailable失敗而非等待／steal，不把「同tab必然同名鎖死鎖」當現有bug。仍須驗證oldtoken/新token切換、pagehide invalidation及完整Settings/physical排除，不以shared lock當同process writer排除。最後集中故障與跨端驗收，效能最後。

## Activated installation 生命週期與實體排他權（最後集中驗收）

真宿主接妥後，media runtime準備失敗須closecandidate並保留原activation/exclusion待處置，不切UI或fallback。adopt只一次，錯scope／closed／重複handoff拒絕。close先停止runtime新calls与repo，等logicalcalls後仍須all-store實體IDB/native I/O終止，再release排他權；取消／exception不當Released，保留原物件與階段明確續處。

所有physical drain／release callbacks須真實且release冪等，不能傳empty function。特別核WebLock／nativelease的migration→repository ownership交接：不得持原exclusive lock又等待同名新acquire造成deadlock，亦不得為避deadlock提早釋放而讓舊writer復活。現有工廠／lifecycle尚無完整宿主，不拿源碼存在當此項通過。最後集中故障／跨端驗收，效能最後。

## 已啟用 runtime 的平台 repository 交接（最後集中驗收）

宿主真正接線後，使用原活躍activation lease＋registered scope，確認platform factory交接同一runtime與原API base／user／client／workspace。任何錯配、closed lease／未registered scope或factory缺失要停止／關candidate，不fallback legacy、不自動重配actor或新workspace、不初始化新ledger。建構不代表取得writer，正常prepareServerWriter與真平台ownership需另外成立。

切換前排除舊Settings／repo writers並等logical與平台physical I/O drain；宿主持有activation至新repo關閉且drain完成後才release。Android／iOS／Web factory入口与Desktop參數皆需驗，普通啟動仍不autoactivate。當前入口沒有App宿主caller，不得將factory存在當正式多人協作已完成。全部業務完成後集中測試，效能最後。

## 跨端 MIME 格式與 typed 匯入錯誤（最後集中驗收）

Android／iOS Photos／iOS Files／Desktop／Web 分別匯入 PNG/JPEG/WebP/GIF/MP4/WebM，wire MIME 必須canonical且與來源checksum／sniff一致。HEIC／MOV無支援representation時提示格式／需轉表示，不能重命名副檔名當轉碼或將QuickTime提交為MP4。Photos compatible representation是否實際可提供支援格式須實機驗，不以configuration存在當通過。

素材過200MiB、空檔、錯checksum／尺寸／duration／truncated source要typed大小或檔案錯誤，不統一當source mismatch；原已預留／write可能進行仍保留request及lookup規則，不自动重送。舊非支援metadata不可自行修改digest或刪除receipt冒充修復。完整业务完成後集中跨端驗收，視覺由使用者，效能最後。

## 時序 Mermaid 格式變更與原 IDs（最後集中驗收）

原時序圖包含自訊息／nested alt／loop／par，guided→export→source只改合法縮排、空白／註解或等價supported箭頭語法，validate及回guided後participants／messages／blocks原semantic IDs與canvas binding應不變；未改draft套用仍no-op。

更改訊息文字／方向／角色／順序／branch或參與者別名，不得以相同位置猜原身分；按現有parser新draft規則完整審閱。Unsupported source仍顯示正式diagnostic，不因original匹配跳過parse。確認外部連線dependency與鎖保護仍阻擋不安全移除，無部分transaction。最後集中source／guided／Undo／Redo跨端驗收，視覺由使用者、效能最後。

## 連線 geometry 與祖先鎖（最後集中驗收）

分別鎖source／target／直接或間接Group祖先，route waypoint、線條新增waypoint、label拖曳、loop extent及geometry屬性面板不應修改，應停用或提示；自循環建立與插入中間節點用同規則。missing parent／non-Group parent／cycle不得當unlocked，unlock有效祖先後才允許。

手勢途中遠端更新锁／祖先／geometry應full baseline不匹配而取消，不送partial mutation；停止手勢不留preview。此規則不改relation文字／方向編輯語意或用UI鎖取代backend ACL。最後集中pointer／跨端與Undo／Redo驗收，視覺由使用者、效能最後。

## AI proposal 提交前拒絕與交易未確認（最後集中驗收）

提交前 scope／permission／version／snapshot／pure proposal review 被拒絕時 Rejected，無正式操作，可以改包含項再審、必須重新授權Apply。executeAwaited 開始後 false／exception／取消，不得當未寫入；UI Unconfirmed保存原proposal選取及ID，禁再Apply／discard／設定重置，固定header提示核原pending／draft。

Close 只清UI，不刪已retain的journal／wire／receipt；草稿恢復與receipt/fence仍用原identity。Queued僅表示隊列交接，不當server committed；server確認另驗。此節尚未執行，完整功能完成後集中雙協定／跨端驗收，視覺由使用者、效能最後。

## AI 所有輪次實際請求授權（最後集中驗收）

含至少兩輪工具回覆的 OpenAI Chat／Claude Messages 任務，每輪顯示自己的 exact body／endpoint／model，皆須明確 Approve 才送該 HTTP。不得出現後續自動同意捷徑；首次批准不批准第二輪含新工具結果的body。不同任务或輪次相同的歷史卡片、不同 body／target 或已回覆 review，都不能批准當前請求。

在等待同意時拒絕／取消／關閉／切scope，應釋放 gate、無後續 HTTP、無可 Apply 草稿；同意後仍核當前scope與身份。log只含typed摘要，不記body或金鑰；Apply是另外一個本機／畫布交易審核，不等於送出 AI 授權。全部业务完成後集中雙協定故障驗收，效能最後。

## 雲端方案結構預覽與本機素材來源（最後集中驗收）

文字／群組／geometry／vectorPath／sequence／media／GIPHY 混合方案，選取後顯示結構預覽及完整 JSON，media只placeholder、不進行下載或播放。超8筆 assetAvailability分頁不可遺漏，按asset＋kind＋sourceWorkspace顯示 available／access_denied／unavailable；任何狀態都不自動授予插入或搬移權限。

純圖形或單一素材來源可明確保存metadata copy，identified intent與save採相同payload／schemaVersion／sourceWorkspace。多來源／不支援payload禁匯入並說明，不保留部分來源冒充完整copy；未支援materialization不可自行搬素材。已保存本機仍非已插畫布，實際使用另核 destinationReady／權限。功能完成後集中跨端驗收，視覺由使用者、效能最後。

## v2 新原檔一般上傳流程（最後集中驗收）

新選檔並 durable reserve 後，明確按「授權並上傳」應依序 prepare／ticket／PUT／complete，期間各 intent 在 write 前保存、identity／lease在使用前保存。原 record 有任何 prepare／ticket／PUT／complete attempt 或 known identity 即不能用一般流程，須原 request lookup／分步恢復；即使 UI snapshot 空也不得放行。

逐步於 write 前／中／後注入取消、網路及 durable 失敗：後續步驟停止，原 request／ticket IDs 保留，不換 ID／自動重送。completionAccepted顯示處理中，不當 Ready；使用者 lookup 得 Ready 後另明確插入。成功完成後釋放原 native handle、cleanup fail保留待清理且提示，不假稱全部成功清理；沒有 polling、abandon或自动node。一般流程与分步來源 checksum／metadata／scope／lease驗證相同。全部功能完成後集中跨端驗收，視覺由使用者、效能最後。

## 鋼筆保存不明與原交易凍結（最後集中驗收）

新建／編輯 vectorPath、文字及 textPlacement，於首次 commit 前確認 scope／lock；Stale／Invalid 不提交，修正或重審後可重新操作。首次 commit 開始後模擬 retain／HTTP／ACK 失敗、取消與 exception，應凍結第一次 path／text／placement／operationId，不再 Apply 或編輯替代內容；固定區提示未確認及 Close。

Close 不能宣稱 rollback／discard server transaction，不刪原 durable journal／pending／receipt；重新進 workspace 先核原交易狀態而非建立新 node 再送。連點不能重複 callback；no-op 編輯仍不增 transaction。此節尚未驗收，全部功能完成後集中跨端執行，視覺／遮擋由使用者，效能最後。

## 雲端方案簿取消後原任務快照（最後集中驗收）

初開面板須明確讀本機已保存 tasks 後才進其他操作，空未載入清單不代表沒有待確認交易。Send／Recover 開始前舊 remote page／resource ETag／review／conflict 失效；於 Sending marker、HTTP、回應後或 outbox readback 取消／故障，不能繼續用舊 Prepared 按鈕或 ETag 寫入。

需要重讀時固定提示與本機 task 按鈕可見，Close／Stop 不受影響；明確重讀只有 local outbox read、不發 HTTP／重送／新建 idempotency key。成功讀取且 scope 當前有效才解除；讀取失敗保持阻擋，已提交／Unknown 不說 rollback。原 task recovery 仍核固定 retention deadline，不延長有效期。最後集中跨端／fault 驗收，視覺由使用者、效能最後。

## 密集線路的自動標籤避讓（最後集中驗收）

建立重合／平行／交叉多條線、不同長度中文標籤及自循環。在候選位置可避免 label 重疊時，不能因另一位置穿過較多線就選擇疊 label；其次避 node、再避線。同分與同一 stable IDs 下，map insertion order 改變不能改变布局。手動拖曳 label 保持原 geometry，auto labels 應避開它並保留間距，不替使用者重定位手動標籤。

檢查 pan／zoom／旋轉／drag preview／Undo 與 PNG／PDF 使用相同 label measure 原則，僅自動布局不應新增 transaction／dirty history。密集無可用位置允許 best effort，遮擋／可读性交由使用者簽核，不宣稱零碰撞保證。全部功能完成後集中驗收，效能最後。

## 遷移完整 scope 資料本機分頁（最後集中驗收）

唯讀來源 preview 讀取成功後可顯示完整 frozen scope packet，不只數量；多 scope 翻頁須保持相同 source／plan digest，切 scope 回第一頁，涵蓋完整 pending 原 wire／草稿／已停止交易／fence／刪除證據／sequence floor。相鄰資料頁不得切斷 emoji、跳字或遺漏末頁，沒有傳送／儲存／自動 recapture 或寫入日誌。重新讀來源須建立新 review 並重置分頁。

非法 Unicode 原資料不得透過 replacement 產生 migration digest、不得修補或移除來源。正式 publication／activation 須另有真實平台宿主與 all-writer 排除、雙確認及 receipt 核對；現有可看 preview 不代表正式 migration 已接通。功能完成後集中驗收，視覺／遮擋由使用者簽核、效能最後。

## 已儲存素材插入交易未確認（最後集中驗收）

fresh asset GET 失敗或 Ready metadata 無效時提示讀取／插入失敗，不能產生 node。正式 executeAwaited 開始後注入 retainDraft／HTTP／ACK 故障或取消，必須提示原交易可能已保存／提交，先核 pending／draft review；不能聲稱 rollback、刪素材、自動新 node ID 再送。恢復確認使用原交易 identity，v2 reservation 仍可發現。

等待 GET／publication 時切 account／client／workspace，舊 callback 不改新 selection 或覆蓋新 status。舊 job finally 不清新 import busy；同步本機 GET 完成也不能留下 busy。此節未執行驗收，業務功能全部完成後集中跨端測試，視覺由使用者簽核，效能最後。

## AI 工具字節累積與失敗草稿隔離（最後集中驗收）

以受控串流確認中文／emoji 文字 128 KiB、單 call arguments 64 KiB、跨 call 1 MiB 邊界；計數按 UTF-8，而非 UTF-16 長度或替代字元。累積器對尚未配對字元暫存不發布，finish 不完整拒絕；但 SSE envelope 原 strict JSON 驗證不放寬，非法 frame 可在入口即 Format。

回覆未完整前不能執行本輪工具；超量、非法參數、取消或 scope 改變後不得保留可 Apply proposal、不得修改 workspace。清除 decoder 後不留文字／參數緩衝；先前輪次的工具 trace 不代表正式寫入。任務 composer 超 64 KiB／非法 Unicode 應提示拒絕並保持原任務；send 不得使用被拒的新內容。仍逐輪 exact body 授權，無自動錯誤重試。

本節是待驗收規範，全部業務功能完成後集中執行；視覺由使用者判斷、效能最後。

## AI 理解文字 Unicode 與字節上限（最後集中驗收）

- 使用受控 Router 回應測中文／emoji：合法 surrogate pair 同 delta 或拆兩個 delta，畫面不能先顯示替代字元；拆分最後配對後顯示完整字元。
- 未配對 low surrogate、high 後接非 low、正常終止時 high 尚未配對：固定狀態 Failed／ResponseFormat，不可 Completed／可核對。正常終止只有空白回 EmptyResponse，非正常終止仍 Incomplete。
- 分別測 128 KiB 邊界、多字節跨界、巨型單 delta：合法邊界接受、超量 OutputLimit、不自動重試；貼非法或超量外部回覆顯示拒絕提示並保留原回覆。安全日誌不得包含回覆原文。
- 每次重新送出仍須明確授權；取消保留 Cancelled 而不是成功。歷史 HTTP 200 log 不代表本版驗收。全部功能完成後集中執行，視覺由使用者簽核，效能最後。

## GIPHY 新查詢失敗／取消保留原結果（最後集中驗收）

1. 取得搜尋 A 並載入下一頁、選擇預覽；記錄來源、筆數、下一頁 offset。輸入 B 並搜尋或改熱門，載入中仍顯示 A 原結果與來源，不能把 A 稱為 B。
2. 分別於新查詢／下一頁中取消、模擬 401／429／網路失敗。清單及原 offset 不變，取消訊息持續可見，錯誤明確，不自動重試。明確恢復 A 輸入後可繼續原分頁；冷卻中仍禁止請求。
3. 成功取得 B 才取代清單與來源、重置預覽並移到首筆。下一頁成功才附加且維持提供者原順序；空頁不跳動，晚到被取消回應不得替換新結果。
4. 舊預覽 refresh 與新搜尋交錯時，舊 refresh 不得覆蓋新查詢狀態；未知 refresh 錯誤顯示 unavailable，不造成無處理 exception。更換 Key／語言時清 session，不續用舊 offset。

以上是待驗收流程，非測試通過紀錄。待全部業務功能完成後集中跨端執行；視覺／遮擋由使用者簽核，效能最後。

## 背景選色器原操作凍結／面板內狀態（最後集中驗收）

首次Apply後selector須read-only、原operationId不变、不能再改preview或重送；busy與未確認訊息在面板內可見，不只全局status。retain failure或結果不明時非busy可close，原draft／pending照恢復規則保留，不拿close當rollback。stale CAS錯誤在面板顯示，重開清舊problem；背景／grid相關Undo／Redo仍正式transaction。其他選色器未給readOnly/statusText時行為不變。正式跨端／取消／鍵盤／focus／mobile遮擋待功能全部完成後集中驗收，視覺由使用者，效能最後。

## 時序鎖定、no-op與保存不明（最後集中驗收）

container／祖先鎖定不得編輯；變更participant文字／角色、block子樹、message文字／端點／順序與移除物件時核相關鎖，不能靠metadata clear繞過。未變locked項目不應擋不相關變更。空白title保持空白，完全未變draft不生成transaction／增加versions。首次commit後保存不明／取消保留原prepared並禁再Apply，改走明確draft恢復；不另造transactionId或聲稱已回復。scope precheck拒絕不送出。最後集中編譯／跨端故障驗收，效能最後。

## Migration 取消與真正 writer 排除（最後集中驗收）

實際宿主接妥後，在publication／activation CAS及confirm／readback途中stop／close，status不可留「正在保存」或稱rollback；原receipt與source保留、再嘗試仍核exact review／original receipt，不自動重新發布。固定status／stop／close不得被大量scope review捲走。普通repository claimAfterDrain不能代替排除direct Settings／舊build／平台physical未終止writer；無真guard不可啟用。宿主目前未接，不拿dialog存在當驗收通過；業務完成後集中跨端故障驗收，效能最後。

## WS 寫入實際平台缺項（最後集中驗收）

缺checkpoint、sequence ledger、atomic recovery任意組合，成員面板必須精確顯示各缺項且仍REST寫入，不靠reconnect自動初始化或繞過migration。三者齊備仍需Live／actor／role／pending／fence及physical writer guard，不以missing空集合當server ACK或部署驗收。實際migration dialog宿主與publication／activation／runtime swap需另驗，目前尚未接；不能拿preview代替。最後集中平台／故障測試，效能最後。

## v2 原檔 handle 交接與清理失敗（最後集中驗收）

在restore record／新預留／重新選檔的舊source.release前、中、後注入取消、exception、timeout：UI先移除舊handle，不得再次read／PUT它；新picked在metadata核對失敗或交接取消時也釋放。cleanup失敗清單需可明確重試，清理未完不再選檔／切handle或PUT，不清原request／lease／server資產。source valid交接完成前核當前scope／controller／coroutine，closed modal不能接新handle。

dispose等待正在readChunk／picker／HTTP／release的job結束，再釋放當前与detached pending handle。每次cleanup有界，不以coroutine取消當成native完成；最終release失敗仍是未確認资源，不可稱「已完全清理」。正式native temporary-file／security scope／browser handle故障驗收待全部業務功能完成後集中進行，效能最後。

## v2 恢復紀錄歸檔與重新顯示（最後集中驗收）

Active／archived清單、分頁、同record反覆archive／unarchive驗原request／digest／lease／意圖不變。歸檔前server查詢必須fresh，ready／rejected／missing／abandoned可歸檔，prepared／completionAccepted／404／unknown／401／撤權不得藏起未解決結果；unarchive只local visibility，不prepare／PUT／復活。全部檔案／素材仍存在，不拿archive當delete或serverreceipt。

在index CAS／confirm／readback取消、兩controller同時新增request或改歸檔時，不覆寫其他紀錄、不盲目retry；outcome不明要求重讀兩份list。舊index未含archived欄位可讀，非法／重複／跨scope archived ID拒絕；舊APP不懂新欄位時不可repair或覆寫。索引1024／512KiB限制仍保留，歸檔不釋放容量，不能宣告長期容量已完成。未編譯／測試；業務功能完成後集中跨端故障驗收，視覺／遮擋由使用者，效能最後。

## 共用原子操作 count guard（最後集中驗收）

從貼上、快速方案、AI proposal、一般節點交易、Undo／Redo各入口驗199／200／201實際展開操作。不得用top-level Transaction child數替代正式展開數；超限前後history／queue／draft／pending／sequence allocation都不變，訊息顯示actual count／limit，不部分發布或清undo stack。直接repository retainDraft／retainDraftAwaited／retainHistoryDraft入口也拒絕超限，不只UI擋。local-only不採遠端上限。

過大的舊draft仍應保留供明確恢復／處置，不透過换requestId／丟項／拆transaction自動修好。200以內也仍可能因byte、ACL、CAS、restore陣列或schema被拒，count guard不是完整server驗證。最大時序容量另按正式需求規格交付，不以小圖取代；最後集中編譯／跨端故障驗收，效能最後。

## 時序圖實際展開操作數與原子上限（最後集中驗收）

驗新建／編輯在199／200／201 wire operations的界線，count必須與正式flattenAndExpand相同（transform多物件1筆，create及部分attributes各項展開）。超限在draft／pending／sequence reserve／HTTP前停止，不凍結prepared、不偷刪項目，可回source修正；不可拆成部分可見交易。local-only不套remote上限。合併transform後每物件版本只增加一次，再取該版本做text／group更新。

核兩次metadata CAS與transaction末binding validation，以及完整Undo／Redo；最大512訊息／128區塊案例不得用小圖結果代替。目前後端200 operation限制仍是不完整交付的契約缺口，後續bulk或更大capacity要有正式schema／capability／fixtures，不可前端自行放寬。未編譯／測試；全部業務功能完成後集中驗收，效能最後。

## 既有時序圖編輯（最後集中驗收）

從container inspector／palette修改／新增／刪除／重排participant、message與alt／loop／par；preview不write，確認後graph＋metadata同交易。guided matching IDs要保留，改訊息端點以replacement relation處理；外部children／relations依賴被移除物件時整筆拒絕，不cascade。未改Mermaid的guided/source往返保留snapshot；真改source明示可能重配ID。scope／baseline／lock／上限／invalid semantics不得繞過。

正式驗temporary metadata clear、graph operations、final binding兩次CAS的原子性，backend flatten後batch上限、最大圖形案例、response-lost receipt／fence、delete/restore Undo／Redo與雙端收斂。container旋轉、layout、手機／遮擋由使用者簽核；目前未編譯或測試，所有業務功能完成後集中驗收，效能最後。

## 雲端方案完整更新／冲突替換／完整分頁審閱（最後集中驗收）

修改本機方案後，選既有雲端resource→review完整替換→durable stage→明確send，應更新同resourceID而非另建重複；name、schema、所有圖形／連線／素材bindings完整保存。準備review時若本機已变，拒絕原捕捉內容；remote另一端更新後原ETag應被拒，不覆盖新版本。Rejected後可以名稱only保留新內容、完整替換新版本（新key＋fresh ETag、非merge）、另存新方案；三者語意不可混淆。Sending／Unknown不得提供full replacement來逃避原請求結果不明。

大於4096字元的payload／正式mutation須可翻完全部JSON，含target、ETag、idempotencyKey與assetBindings；emoji跨頁不切surrogate pair，翻頁不得HTTP／write，新的review重置頁數，resource切換清舊conflict。full replacement即使內容相同也不是原request成功證明；仍按原收據／TTL恢復規範。多workspace素材仍不materialize／自動下載，原缺口保留。正式編譯、兩裝置收斂、CAS／取消／本機durable失敗最後集中驗收，視覺／遮擋由使用者簽核，效能最後。

## 共用背景／網格的 stale draft 與未確認保存（最後集中驗收）

兩端同時編背景／網格：Apply前或凍結operation後另一端改styleVersion／before，原編輯器須拒絕，不覆寫新背景或網格。Undo／Redo沿完整樣式before/CAS規則，若別人已改其他樣式也不能強制rollback；不得變成本機display preference或清history逃避冲突。个人網格顯示／snap／viewport不被共用樣式更新改掉。

派送在session缺失、斷線、switch、draft publication busy、sync busy、pending queue時，要得到failure回報，不能silent return。awaited retain失敗／可能已commit時，網格視窗保留原operation並顯示保存未確認，不能連點另造ID或稱已回復；关閉只撤preview，草稿／pending照原恢復流程。一般成功本機保存不等同server ACK。驗手機內部捲動、固定狀態／actions、取消preview回復最新文件色；視覺與遮擋由使用者簽核。正式跨端同步／Undo／Redo／斷電／CAS測試待全部業務功能完成後集中執行，效能最後。

## AI 回覆摘要與空回覆區分（最後集中驗收）

理解與工具視窗各驗正常非空文字、只有工具呼叫、文字＋工具、正常terminal但空／空白且無工具、沒有terminal即EOF。空回覆須顯示 EmptyResponse 與明確引導，無terminal為 Incomplete，不互相冒充；均不得接受／套用草稿或自動retry。replyTextBytes／replyToolCalls／replyArgumentBytes 應精確符合當輪嚴格 UTF-8，安全 log與固定狀態區都能看到，但不能包含 node文字、arguments或raw response。多輪不可把上一輪summary套到下一輪；當輪工具執行或scope驗證失敗要保存當輪summary，清isolated draft。

HTTP200／Completed／reply counts只證明該次回覆階段，不能標記formal canvas apply成功。需要最後實際驗OpenAI兼容Chat與Claude兼容Messages、每輪exact-body授權、取消／UTF-8／空回覆／串流故障、proposal選取與原子套用／Undo／Redo。2026-10-09舊Desktop安全紀錄不可代替新版本測試；目前未新增付費呼叫或執行驗收。所有業務功能完成後集中測試，視覺／遮擋由使用者，效能最後。

## v2 write-ahead 意圖與 unknown-result 閘門（最後集中驗收）

在 prepare／PUT／complete 意圖 CAS、confirm、讀回、實際 HTTP dispatch 前後、ACK 遺失各處中斷。紀錄永遠保留原 requestId／metadata digest／lease，意圖不是成功或已派送證明；意圖未確認不可 HTTP，成功回應保存須使用意圖後的 generation。舊未含新增欄位的紀錄可讀，新增 marker 不可倒退或任意引用未知 lease，不裁剪／清除歷史。

重啟後或寫入結果不明，UI 必須清舊 phase／signed ticket、禁直接 PUT／complete／Ready 插入，要求明確 lookup；成功lookup後才恢復相應操作。404不自動 allocate，也不自動 retry；只有無已知 identity 的原請求能以清楚「相同ID」授權重送 prepare。模擬收到 response 卻 local settlement 失敗、取消發生在marker保存但HTTP尚未送出、PUT部分 bytes後檔案不符，都不能顯示舊成功或自動 complete。選錯原檔而未寫網路時保留前 source，不把它當成已送出。所有故障／平台測試最後集中執行，視覺／遮擋由使用者簽核，效能最後。

## v2 恢復分頁、原票券與錯誤引導（最後集中驗收）

清單0／1／8／9筆與上限場景：新紀錄優先、每頁8筆、前後邊界禁用，翻頁不可網路請求／配置身分。重啟後 latestTicketRequestId 需沿用，一般取票不能換 ID；唯有明確 replacement 授權可取代舊 lease。已知 identity 的紀錄恢復後只先提供查詢，不再次 prepare；index-only 沿原 request 初始化本機記錄，不被當成 server absence。

重新選錯檔在交接 handle 時就拒絕，仍可用舊檔；原 metadata 相同但 bytes 變更在 PUT hash驗證拒絕，不自動 complete。401／403／404／expired／superseded／mismatch／generic unknown outcome 訊息持續可見且不含 key／URL／error body；失敗清 ephemeral ticket，不能沿舊 phase 錯插 ready。completionAccepted 只提示手動查詢，不輪詢／重傳；terminal 只核對、不復活。dispose在readChunk／picker／HTTP途中取消，必須等待operation coroutine退出才release持有source；關閉client失敗也不能跳過release。正式編譯與跨端故障測試待業務功能完成後集中執行，視覺／遮擋由使用者，效能最後。

## 普通 Server v2 匯入操作視窗（源碼已接，最後集中驗收）

由原匯入按鈕進 v2 視窗：新選檔先durable reservation，再逐按 prepare→ticket→PUT→complete→查詢 Ready→插入。必須能讀回 index-only／HTTP response-lost 原身分，重新選原檔續傳；沒有自動網路写入／輪詢／替換 ID。正常票券重取沿原 ticketRequestId；換新按鈕明確說明 supersede。close／cancel保留紀錄、釋放 picker／client；新預留只清 UI，不刪原 reservation。未配置 durable runtime 顯示專用不可用訊息、不得 fallback v1。本機匯入不切遠端。

驗換 workspace／actor／client／角色、斷線、撤權、取消／dispose各 await 邊界；Ready click不可把舊scope素材加到新scope，需重新GET verified descriptor且沿正式畫布交易。背景快捷鍵／手勢在視窗存在時被擋；關閉後解除 barrier才插入，錯誤／HTTP code／phase 持續可見，不洩露 key／signed URL／response body。手機 safe area、按鈕尺寸／步驟直覺、遮擋與視覺由使用者簽核。此視窗源碼未經編譯或執行；全部業務功能完成後集中跨端驗收，效能最後。

## 一般匯入 v2 discovery 與分步 coordinator（最後集中驗收，尚未接 UI）

在 index CAS、durability confirmation、request CAS 各階段中斷，重啟須發現原 request；index-only 不得被當成已送出／未送出的 server 證明，不換 ID。記錄存在但索引缺失、錯 actor/client/server/workspace、索引 identity 不一致、數量或大小超限要停止而不清理歷史。跨 instance CAS 競爭不得盲目 retry。平台 factory 有源碼不等於 UI 已啟用，須檢查實際宿主 wiring。

每一步明確授權；prepare 的 capabilities await 中切換 scope 不可寫。ticket identity 保存、lease confirmation 失敗不能 PUT；HTTP 回應遺失只能核對原身分。PUT 重新選錯原檔、假 checksum、短讀、超長、非順序、讀取中變檔或切 scope 必須停止，不自動 complete／abandon／重傳。驗完成後仍未自動插節點，只有 Ready 與正式畫布交易流程才允許插入；lookup 404 不能另造上傳。現階段 ordinary picker 仍 v1，coordinator／UI 端到端未交付；全部業務功能完成後集中測試，效能最後，視覺與遮擋由使用者簽核。

## 素材取消／abandon 不明與提醒（最後集中驗收，尚未執行）

validated ticket上傳途中取消：abandon成功才可顯示純取消；abandon丟exception／timeout／連線中斷須RecoveryRequired且原asset ID保存在原actor/workspace提醒清單，重啟後可明確查metadata，不能另造上傳或把清理不明當不存在。completion／beforeComplete checkpoint已開始不得abandon；提醒保存失敗不遮原error，不force binary retention、不重試server mutation。scope切換只存原scope不污染新scope，source handle終止仍釋放。提醒只是Settings書籤，不證明v2身分、Ready或斷電durability。普通picker仍v1，後續需正式v2 prepare response-lost／lookup／lease／reselect驗收，不能以LocalAssetUploadCoordinator的v2測試代替。全部業務功能完成後集中驗收。

## Awaited WebSocket 提交原身分檢查（最後集中驗收，尚未執行）

正式activated runtime下，stage成功且generation確認後才能WS提交；派送guard要從awaited pending／fence讀而不是legacy Settings。於每次guard await中改actor/client/workspace、owner、Live、角色、fence或pending，最後send不得發原frame。socket writable／authenticated於await返回後再核，取消留原pending。未配置atomic stopped evidence或sequence ledger的legacy mobile不得因新code放行，沒有初始化／migration fallback。WS可能已派送卻ACK不明時保留原identity，不切REST或新請求；valid ACK才沿durable/history交接清pending。驗慢store guard與heartbeat／presence writes、撤權／重連epoch；平台host未接仍不列正式協作完成。全部功能完成後集中驗收，效能最後。

## Activation close 與 logical-call drain（最後集中驗收，尚未執行）

在draft append／CAS、sequence reserve、safety記錄await途中close：新call拒、issued身分保留直到已開始call finally退出，下一confirm不能取得lease。closeAndAwaitCalls須非阻塞等待，取消等待不重置count／issued；guard失敗與exception也離開計數、重複舊close不釋放新lease。寫入在close前可能已commit，不能把返回錯誤當rollback。另驗Web coroutine取消但IDB transaction尚未terminal時logical gate可以已退出，host必須仍等physical owner ticket，不能拿logical counts代替全writer exclusion。正式host／新scope註冊尚未接；全部功能完成後集中跨端／故障驗收。

## Awaited activation 租約競爭（最後集中驗收，尚未執行）

在完成真平台writer排除的host下，activate→close→reopen後對舊handle重複／延遲close，新runtime必須仍有效；兩個confirm不能同時取得issued身分。Runtime每筆操作核當前lease身分、closed與物理guard，舊lease不可寫；claim後身份／平台guard失敗不得回傳lease，僅清自身slot，不清其他owner或舊receipt。取消不推論未發布，不自動重試。此manager修正不證明所有legacy writers已退出，host／新scope registration未接時不允許認定已啟用或正式多人完成；跨端競爭與故障驗收最後集中執行。

## 逐點路徑編輯與未套用草稿（最後集中驗收，尚未執行）

手動折線逐點選取、輸入X／Y、stage到preview、刪第一／中間／最後點，確認其他點順序與geometry屬性不變；刪最後一點預覽Auto，只有Apply才提交、可整筆Undo／Redo。無效／NaN／Infinity／超界座標不能stage；未stage輸入不能直接套用舊預覽或換點丟字，可單點cancel。route草稿dirty時label立即操作disabled，discard只捨棄UI、不write。batch／X／Y轉焦與移除欄位後快捷鍵狀態正確，Delete／Paste僅編輯文字、不刪連線。其他端改version時原草稿不得偷偷套新state；以現有scope／CAS規則拒絕。鍵盤、VoiceOver名稱與手機操作／遮擋由使用者驗收，正式編譯與跨端測試最後集中執行。

## 選定線身背景拖曳與手勢權限（最後集中驗收，尚未執行）

先選非self-loop連線，再於背景任意命中segment拖曳，驗projection位置新增一點，manual其餘點保留；Auto原折線内点保留後轉Manual，32點上限拒新增。線與節點／group／label重疊時，卡片或label維持自己的手勢，不能被全屏overlay偷走；未選line或未命中仍原tap／pan。touchSlop內抖動、取消、第二根手指、viewport／scope／relation version改動不commit；右鍵pan、Shift、多選、框選、brush、文字編輯與sequence內部物件不進line edit。成功是一筆既有geometry transaction，驗Undo／Redo、双端CAS；self-loop仍使用extent／label，不轉Manual。實際旋轉端點、iPhone觸控與遮擋由使用者簽核；源碼未驗，集中編譯／測試留最後。

## 手動線段插入轉折點（最後集中驗收，尚未執行）

在manual relation首段、中段、末段拖曳加號控制點，驗只插入一個waypoint，保留其餘點順序、direction／label／intent／color／labelPlacement；在縮放與旋轉endpoint時world位移仍與手指／滑鼠一致。預覽新增點與既有vertex不能因索引移位跳動，pointer key不重建gesture。輕點／touchSlop内抖動／取消不寫入，scope／relation version／viewport改動取消；完成為一筆update可Undo／Redo，與後端CAS及雙端一致。32點上限無新增handle；短段不疊加touch target，實際遮擋由使用者判斷。這只涵蓋可見segment控制點，不以此宣告任意line-body拖曳完成；全部功能完成後集中編譯與驗收。

## 插線中間節點形狀（最後集中驗收，尚未執行）

對Forward／Backward／Both／None與自循環原連線插入中間節點，選rectangle／diamond／其他現有shape，核preview、新TextNode與正式transaction內容相同，方向、端點順序、label／intent不被shape改寫。改形狀必須撤銷confirm，attempted／saving後不得換shape或另造ID；保存結果不明的retry維持原payload。整筆Undo恢復完整原連線，Redo依既有restore／CAS規則，跨端shape一致。選項只按需展開、選定後收合，手機可操作與遮擋由使用者簽核。現有形狀不表示execution語意，不能把後端endpointRewire能力未提供誤當已完成。源碼未驗，最後集中執行。

## 時序圖指定插入位置與建立視窗（最後集中驗收，尚未執行）

引導編輯於root與alt／par／loop巢狀branch，明確選尾端、訊息之前與block之前插入，驗step順序與其他branch完整保留；切branch／編輯已有message不能沿用舊插入目標。目標被移走／刪掉後保存須拒，不能默默append或跨branch。draft preview／Mermaid export／正式transaction保持一致，取消不送後端；prepared身分、Undo／Redo與双端收斂最後驗。iPhone含動態島／橫直向／鍵盤開啟，固定標題、錯誤、保存狀態與close／discard可用，編輯區能scroll；實際遮擋與美感由使用者簽核。源碼接線未執行正式測試，全部業務功能完成後集中驗收。

## GIPHY 限流與分頁續接（最後集中驗收，尚未執行）

讓下一頁遇429／本機rolling-hour額度滿，驗原items／offset保留且下一頁按鈕可見但等待期間disabled；倒數只讀本機clock／budget，不送任何HTTP，解除後也不auto fetch。明確下一頁仍用原offset／query／language；共享key其他端限流不以本機倒數結束保證成功。同key後續較短Retry-After不能縮短既有block，切key不得借舊配額重設或洩漏key。query草稿改動時不拿舊搜尋cursor套新query，locale改動清原session；取消／late response不得蓋新結果。unexpected page處理失敗須有安全提示，不靜默。維持provider排序、不去重／cache查詢結果，不自動download或rehosting；新版跨端功能完成後集中驗收。

## 雲端方案簿刷新與可觀察狀態（最後集中驗收，尚未執行）

雙裝置更新同resource：已提交／已拒絕結果須分開顯示且durable task保留，清掉舊清單／選取／ETag，明確重讀再審閱，不能把idempotent recovery歷史receipt當最新resource。第一頁／下一頁／上一頁皆由明確操作讀取；cursor過期、collection變更、撤權保留當前狀態與錯誤，不自動換cursor或retry。新選取需fresh GET。固定頂部status與取消／關閉在長列表／手機可見，由使用者簽核遮擋；取消完成前不能開始下一job，舊finally不得清新job。操作完成但tasks reload失敗不得顯示rollback或引導重建請求。跨workspace素材materialization仍需正式後端能力，不繞過。源碼未驗，全部功能完成後集中跨裝置測試。

## AI 工具終態與套用回饋（最後集中驗收，尚未執行）

工具loop意外throw或取消時，固定status、request卡、安全log應有typed Failed／Cancelled與該輪HTTP／elapsedMs；已有首個Failed不能被取消蓋掉。範圍檢查、每輪授權、UTF-8／空回應與operation審核不能因exceptionhandler而略過。使用者Apply成功僅Queued，modal應保留待server確認訊息，不立刻消失或改成完成；scope變更也保留queued提示。成功後不能同modal用舊context再send，明確關閉／重開才重新建立scope和授權。與保存不明／衝突／拒絕案例區分，不宣告servercommit。現有log停在10月9日，不能當新版驗收證據；需使用新版並按每次授權流程驗收，全部功能完成後集中執行。

## AI 上一輪狀態與終態診斷（最後集中驗收，尚未執行）

以新版APP測理解驗證與工具round：Preparing／workspace檢查／Connecting／HTTP／Receiving／Completed或Failed／Cancelled各事件可見，終態保留同attempt的HTTP摘要與elapsedMs。修改設定／scope後要保留原診斷且標「上一輪」，撤銷原授權；新attempt／tool round不能繼承上一轮HTTP或provider outcome。同原事件去重，最長24事件；工具request卡、固定status與安全log須一致。驗401／403、HTML／非JSON、UTF-8错误、空回應、tool-only SSE與取消，不因狀態標完成就跳過format／scope／Apply審核。log不得記憑證、endpoint、prompt或raw body。實際網路驗證需每次使用者明確授權，不為診斷自動付費重試；目前源碼未編譯／測試，集中驗收最後。

## ACK history handover 與 Desktop native adapter（最後集中驗收，尚未執行）

連續多筆交易包含換素材、一般編輯與Undo／Redo，第一筆ACK後journal下一筆baseHistory必須保留已確認素材original provenance與完整stack。handover讀到其他transaction、錯digest或不同checkpoint不能誤用；history.workspace需等於原head套用後結果，不能拿最新server snapshot直接塞入舊tail的baseline。驗submit、prepared send、receipt、明確retry及internal retry全部清pending前走同交接；chunk讀取／confirm／CAS失敗時pending留存。Desktop native與其wrapper必須實際轉接appendWithIntent及acknowledgeWithHistory，與Settings／awaited四端一致，不能走預設拒絕或丟棄metadata。正式跨端／故障測試尚未執行，功能全部完成後集中驗收，效能最後。

## Confirmed history 開啟／復原載入（最後集中驗收，尚未執行）

提交多筆Edit／Undo／Redo確認後重啟APP、重新開啟及切換workspace，驗完整undo／redo堆疊、scope與確認checkpoint保持。server其他端已新增／移動／鎖定／刪除物件時，畫布必須仍是server最新資料，history載入不replay、不發操作；Undo／Redo須依法檢查當前state與CAS拒絕衝突。stored checkpoint比server state新、壞manifest／缺chunk／scope變更／取消不得悄悄換空history或套入另一workspace。receipt／retry復原應採用完整confirmed snapshot，不以單筆inverse覆蓋多筆stack；遠端catch-up不得只因有人更新就清空所有本機history。legacy缺baseline仍不補造。此為新接源碼，尚未執行，六主線全部功能完成後集中驗收，視覺由使用者簽核、效能最後。

## Journal baseline 與 ACK 前 history 保存（最後集中驗收，尚未執行）

建立多筆排隊Edit與Undo／Redo，核journal保存最初完整beforeHistory，逐笔applyRecorded須與原操作exact一致；metadata換意圖／原stack改掉必須拒，而非選別的動作。ACK推進baseHistory只含當前head，confirmed snapshot不得含未確認tail；混合交易素材origin與既有堆疊都保留。REST、WS與receipt在history chunk／manifest／confirm各邊界失敗，pending尚存；同transaction與request digest再確認冪等，其他identity不得占同checkpoint，較新snapshot不倒退。restore draft應保留Undo／Redo方向，不把每筆當新Edit。尚未接confirmed snapshot開啟載入與復原畫面完整採用，不列跨重啟完成；集中驗收最後。

## 完整 history durable snapshot（最後集中驗收，尚未執行）

待 repository／UI 接線後，驗 workspace、完整 undo／redo stack 與確認 checkpoint 跨重啟保持；未確認 optimistic queue tail 不得混入已確認 snapshot。對512KiB中文／emoji邊界、缺塊、錯 hash／長度／scope、未知欄位、數字字串、世代競爭、quota與各CAS／confirm前後取消／崩潰逐項驗證。manifest-last之前失敗不能顯示成功，保存結果不明須讀回原世代，不自動第二次寫入。完整64MiB以上拒保存且不截斷 history，不删除舊chunks；reader完整重讀manifest并确认每塊durability，不能只驗counts。原證據／當前server ACL／CAS仍另外必須成立，snapshot非 restore 授權。當前保存層尚未接提交與開啟流程，不列完成；全部功能完成後集中驗收。

## 一般編輯 intent 與 pending 凍結（最後集中驗收，尚未執行）

分別用同步普通編輯與 awaited 編輯建立 draft，確認明確 Edit；Undo／Redo 記其實際意圖。prepare 後 pending 保存相同 intent；stage 遇 head 或 intent 不符不得寫入／dispatch。修改 journal intent 再確認 receipt／明確 retry，不得產生錯方向 history；未知 enum、懸空 intent key、重複 operationId 拒讀。legacy pending 缺意圖仍能確認原交易，但不能靠新 journal 補授方向。確認 HTTP body 未新增 intent、原 transaction／operation ID 與 clientSeq 不變，scope 切換不滲漏其他使用者歷史。尚未涵蓋 ACK 後再次重啟完整 history，功能完成後集中驗收。

## 復原交易的 Undo／Redo 意圖（最後集中驗收，尚未執行）

分別對 Edit、Undo、Redo 製造已 commit 但回應遺失且 APP 重啟的情境：原 journal head 必須明確保存 intent，receipt／明確 retry 成功後 Undo 提供 Redo，Edit／Redo 提供 Undo，不重播原交易。核混合交易完整性、素材 provenance、latest state 前置條件與 CAS。未知 enum／無對應 operationId 的 intent 應拒讀；舊 record 缺 intent 可以確認提交，但不得以 inverse ID／suffix 猜方向或建立 history。ACK 後不留已移除 operation 的 intent，其他排隊操作 intent 不丟失。此尚不涵蓋 ACK 後再次重啟的完整歷史保存，不能宣告 history persistence 驗收通過；功能完成後集中測試。

## 明確 retry 與 history 交接（最後集中驗收，尚未執行）

在已保存 pending／journal 的完整混合交易上，使用者明確重試；核所有原 transaction／operation ID、clientSeq、payload 原樣發送，不因重建 history 而換 ID。最新 ACL 不可寫或 fence 已有結果時不得送出。Conflict 保留 pending；ACK 成功後讀回最新畫布失敗也保留 pending，下一次必須由使用者查 receipt 或明確決定，不自動重送。讀回版本／serverSeq 必須涵蓋 ACK，再清 pending、交接完整 inverse 與素材原證據；不重播原操作，不提供局部混合交易 Undo。驗 scope 切換／取消／並行復原不污染新畫布；仍未保存確認後跨重啟完整 history 與原 Undo／Redo stack 意圖，不能列為完成。全部功能完成後集中測試，效能最後。

## Receipt 確認與完整交易 Undo（最後集中驗收，尚未執行）

製作含換素材與其他節點／連線修改的單一 transaction，模擬提交成功但回應遺失，包含 APP 重啟後 pending／journal 仍存在情境。明確確認 receipt 後不得重送或重播原操作；應載入最新畫布、建立完整交易的 Undo，素材歷史使用原 commit provenance。核對 journal head／wire 任一 kind、payload、expected version 或 operation 順序被改時，不可清 pending；缺 journal 或 legacy 缺素材快照時不得提供局部交易 Undo。其他端已變更／鎖定／刪除相關物件時，Undo 必須依現有前置條件拒絕，不強制覆寫。確認後再次重啟的 history 持久化、retry 交接及原 Undo／Redo stack 意圖尚未接入，不列通過。功能完成後集中執行編譯、fixtures 與雙端驗收；視覺與遮擋由使用者判斷，效能最後。

## Awaited 遷移發布 receipt（最後集中驗收，尚未執行）

啟用coordinator前須真正排除所有legacy與new writers（含其他版本／process／tab），guard不得以UI disabled或兩次來源read取代。fresh recapture須對上已審閱source＋完整plan；首次無receipt但任一native artifact已存在時拒絕。每次Preparing CAS／confirm、draft／safety publication、ledger initialize／confirm、typed readback、Published CAS／confirm前後crash／cancel／quota失敗，不能切runtime或重設counter。Preparing復原只接受原review與exact artifacts，不改ID／clientSeq、不overwrite已前進native狀態；不同digest／generation須另行協調。驗同origin/user/client跨workspace ledger去重且floor一致，完整draft／safety／ledger readback不是counts-only成功，所有generation／hash一致才Published。Published只能證本機初始保存；runtime cutover另驗activation、server receipt／fence另驗，不授權resend。legacy原資料保持，無自動GC／重試。平台guard／UI尚未接，coordinator未實際執行，集中編譯／fixtures／跨端故障驗收最後，效能最後。

## Legacy submission 遷移審閱（最後集中驗收，尚未執行）

wizard／coordinator接線後，完整legacy inventory與真正writer exclusion成立才形成review。驗跨平台planner與既有Desktop sourceDigest vector一致（common SHA256、4-byte big-endian length framing），且丟失wire／unbound fence／floor變更／existing native state不能略過。review凍結caller集合，摘要顯示各scope操作／pending／停止wire／fence／deletion數量、floor與quarantine；不能以摘要或hash相同代替完整來源重讀。确认前修改原source或normalized plan任意內容，requireUnchanged須拒並要求重審。adoptReviewedBundle只在Preparing receipt與真writer排除下發布，exact已有初始bundle可confirm但已前進draft不可rewind；verified absence保存tombstone，不能拿missing當空資料。採用失敗不刪legacy、不換原wire identity、不auto retry。review本身不啟用、不授權network resend；目前只有源碼model，writer exclusion／activation／UI尚未接，不代簽遷移完成。最後集中驗收、效能最後。

## Repository awaited safety 接線（最後集中驗收，尚未執行）

三端runtime啟用後驗Unconfirmed marker CAS／confirm失敗時不POST fence；durable成功後若原pending變更／取消仍不dispatch。receipt settle依序await fence terminal觀察、完整delete／cascade provenance、sequence確認、draft ACK，任一步失敗不能清原pending或改ID重送。新store缺失／quota／錯證據不fallback Settings，舊storenamespace保留。native／awaited共用ACK-derived proof：錯actor／client／payload／cascade／saved snapshot／tombstone版本不可產生restore證據；legacy缺cascade proof不升級authority。restore_objects／relations需exact tombstone＋snapshot；restore_media_reference須走F獨立provenance，不能被generic relation guard誤攔或豁免。Desktop最終WS guard仍讀已啟用native safety，三端未完成activation不得假開WS。來源只有源碼檢查，集中fixtures、實際restart／CAS／跨端驗收最後執行，效能最後。

## Awaited fence／刪除還原證據（最後集中驗收，尚未執行）

repository接新safety／三端啟用後，beginFence的Unconfirmed須在HTTP前CAS＋耐久確認。timeout／取消／unknown receipt不得清marker或新建替代wire；精確requestDigest、scope、transaction都相同才可讀原marker。Fenced／Committed互相衝突拒絕、同終態再確認durability；普通receipt查詢不創造fence marker。deletion batch原子保存且entity唯一，同tombstone改digest／原transaction／serverSeq拒絕，新證據須版本與seq都前進；保存失敗不清pending。測並行CAS、missing／壞資料不reset、非法UTF-8／duplicate keys／字串數值、超限quota。legacy adoption需完整catalog審閱＋排除舊writer，保留原檔，同fingerprint只confirm既有已前進紀錄，不rewind。共用codec與原JVM v1 fixture／legacy fingerprint需一致；數值驗證加嚴的不合規舊資料應顯式reconcile，不覆寫。三端未啟用、repo safety仍待awaited接線，不能宣告receipt/fence全跨端完成；最後集中測試與效能最後。

## Repository awaited 草稿邊界（最後集中驗收，尚未執行）

三端runtime實際啟用後核prepare／stage／pending／ACK／receipt settle／stopped evidence所有await完成才推進UI與network，失敗不得fallback Settings或換request ID。同步retainDraft在awaited配置須明確拒絕；UI採retainDraftAwaited且mutex取得後再驗session／baseline／capability。Desktop native也經awaited UI publication，native I/O離開UI thread；取消／scope換手或重複publication不回寫新scope。awaited ledger路徑不得修改legacy Settings counter，舊配置namespace不搬移；pending／fence／deletion-evidence未完成整套activation時不得放寬WS gating。驗相同原請求receipt恢复、保存ACK未知、sequence reservation不重用、Default背景与UI操作竞争、長交易當前queue保護。現三端未啟用，legacy migration／identity floor與recovery-safety還待完成；最後集中編譯／fixtures／跨端測試，效能最後。

## Awaited 草稿 pending／client 序號（最後集中驗收，尚未執行）

Backend改接awaited與平台注入後再驗：append／stage／ACK／restore／stop／移除／stopped evidence只有CAS＋confirm成功才UI或network進下一步。Web transaction enqueue／request success不能代簽durable；scope／cancel每個await後核。兩writer同generation競爭只一個接受，不自動rebase／retry；同bundle確認未知localACK只confirm，不生成新operation。移除保留scope tombstone及generation，archive不能因移除draft消失；wire-only legacy不可補造journal。ledger跨同origin/user/client所有workspace共用，初始化須verified floor＋legacy writer exclusion，缺失／corrupt不能reset0；範圍先durable再分配、unknown範圍不重用、advance同值仍confirm。測非法Unicode／duplicate fields／字串數值／過大bundle／quota／取消／scope drift，不截斷草稿、不開fake WS寫入。此adapter尚未接線或啟用；三端遷移審閱與大bundle分塊仍待完成，正式集中測試、效能最後。

## WebSocket 原請求寫入／ACK 與 receipt（最後集中驗收，尚未執行）

Desktop native配置下Live走WS；其餘平台在durable pending／sequence adapter未完成時明示REST，不可假裝四端WS寫入完成。先保存原pending再送，frame內transaction／operation IDs／baseVersion／clientSeq／payload與原請求一致。丟ACK／30秒timeout／取消／断線／late ACK／撤權／fence競爭：原pending保持，不自動改REST再送或生成新key；原receipt／fence才settle未知結果。偽造錯actor／client／ordered IDs／seq／version／kind／schema、同UUID異payload、缺／多operations、partial boundary、字串數值／未知frame欄位，都不能保存成功或清draft。accepted／duplicate才由既有deletion evidence與sequence／draft durable流程處理；ACK自身不當fan-out checkpoint。conflict fresh state可能比ACK新，須保留fresh真version，不把較新畫布標成舊head。rejected idempotency_mismatch／transaction_fenced等固定碼可見，原body／ticket／details不顯示。核fan-out先／後ACK、重複廣播、server rebasing、restore／Undo資料保留。mobile／Web正式durable adapter與實際跨端fixtures最後集中驗收，效能最後。

## 協作安全除錯與 Presence 限流（最後集中驗收，尚未執行）

成員面板須持續顯示最近safe stage／code／HTTP／宣告retryAfter，不因client.close或取消把Failed／Revoked／Resync誤改Disconnected，不把歷史等待量當實時倒數。以401／403／ticket_expired／非法frame／local CAS錯誤驗證，無API Key、ticket、URL、scope IDs、rawframe或exception message進UI。pre-auth scope雙null的合法固定protocol_error只能停止、不能被當authenticated liveness。Live presence rate_limited按retryAfter（正數、最大60s、非字串）停止下一publication直到monotonic期限；新error延長窗口要再等待，不重播已拒舊cursor，不改durable submissions。測cooldown期間stop sharing／clear／撤權／背景／scope變更：清除被阻且不再consent時斷連線，不留被heartbeat延租的旧cursor。未知欄位／code／超大retryAfter／NaN／亂序不能被當成功或自動重試。safe diagnostic copy／本機log另待接，正式集中跨端與視覺使用者簽核，效能最後。

## 正式 outgoing Presence 分享與隱私（最後集中驗收，尚未執行）

真Live後publisher與notifications必須共用同socket；初次runner強制發布目前意圖，預設未分享只能是null cursor＋空selection。手動share才送非空內容，stop分享送clear；文字／viewport／asset URLs不進frame。驗兩次handle重建、sharing切換、背景前景、角色／scope／room／connectionEpoch變化，server presenceSeq始終單調，舊token／publisher被替換後不能寫。cursor／selection分別尊重capability速率，慢transport只背壓當前publish不堆pointer queue；等待時取消分享、撤權、切scope，最後實際write前必須阻止敏感frame，失敗須close owner，不auto retry舊位置。selection僅authoritative UUID且不超server上限，非法座標／surrogate／NaN等拒絕。activity epoch變化斷舊socket與heartbeat，不替背景舊游標續租；重連手動consent不沿用。測sleep／delay中的stop／clear競爭，不能漏清而留下被heartbeat永久延租的cursor。現未編譯／真服務驗收，429／protocol_error recovery仍待完善；雙端視覺由使用者、效能最後。

## 畫布即時協作入口與 owner 生命週期（最後集中驗收，尚未執行）

配置runtime的server模式，在成員面板按「連線即時協作」才啟動；本機／無runtime不顯示假的能力入口。核準備／驗證／追趕／Live／失敗／撤權／resync／中斷狀態持續可見，不因close把錯誤覆寫掉；Live文案須明示目前REST寫入／游標分享未接完。多次點擊、啟動途中按中斷、關成員面板、切工作區／帳號／角色、離開畫布、服務中斷，核只有一個owner與socket，scope失效清Presence並cancel準備job。關面板不應擅自關協作；離開畫布必須關閉。真交易事件只wake權威refresh；有文字輸入／拖曳／queue／draft時不得覆蓋history／清pending。觀察Presence與notifications不得增加socket。非Live、撤權或finally後舊Presence不能再次發布；failed／resync要求人明確中斷再重連，不偷偷重試。未接publisher時不能啟用假的游標分享。四端視覺／safe-area／遮擋由使用者判斷，跨端功能集中驗收，效能最後。

## 四端 checkpoint runtime／repository 啟動（最後集中驗收，尚未執行）

核Desktop私有atomic root、Android noBackup checkpoint leaf、iOS私有leaf、Web awaited IDB Documents中saltedkeys皆隔離原資料用途；工廠建構不讀／改資料、不取得ticket或開socket。本機模式不可藉runtime變成server-mode。native read／CAS／confirm不阻塞主執行緒，Web等待transaction durability而非enqueue ACK。Repository createowner途中換actor／workspace／client／subscription或取消不得繼續fresh read／初始化／開socket；metadataRevision缺失／字串／非法值拒絕，不猜0。驗capabilities／metadata 64KiB與state64MiB、15秒timeout、JSON MIME／UTF-8／重複欄位；HTTP錯誤不輸出rawbody。default無runtime repository須明確無協作入口，不用Settings fallback。尚未接畫布，不把工廠成功當正式協作已連線或Android／iOS全draft durable驗收完成；最後再集中跨端／restart／process competition。

## 大型協作 checkpoint atomic manifest（最後集中驗收，尚未執行）

完成平台接線後驗4 MiB以上完整畫布，不可截斷節點／連線／metadata；新上限64 MiB、單chunk512 KiB、最多129refs。測Emoji／繁中跨切分點、每塊UTF-8有效且整份hash一致。於每次chunk CAS／confirm、manifest CAS／confirm前後取消或crash，重新開啟只能看完整舊紀錄或完整新紀錄；失敗不得回catch_up_applied。兩writer發布相同chunk可重用，異內容同key／generation非1須拒；manifest CAS競爭不能自動rebase。缺chunk／錯長度／順序／digest／scope派生key／壞UTF-8／超界總長度／組合domain失敗，保留原資料不reset。reader途中manifest被其他writer更動須停止，不混checkpoint。legacy單record仍可讀；相同next ACK遺失可confirm，不冒充後端receipt。孤兒／舊chunk目前保留，quota失敗必須明確停止；GC需另外驗reader-safe／pending-writer-safe政策後才啟用，不得刪除仍被使用的區塊。此條覆蓋前節歷史4 MiB單record限制，源碼尚未驗收。

## 協作耐久 checkpoint 保存（最後集中驗收，尚未執行）

平台注入後核 key 同部署／user／client／workspace一致才共用；不同scope無法讀或覆寫。state與metadata同一筆保存，不含ticket、Presence或optimistic nodes。fresh REST initialize只在紀錄不存在時進行；已有checkpoint不得用新讀內容直接覆蓋。兩handle／process讀同generation後競爭保存，只一個CAS接受，另一個停止不rebase。模擬CAS已寫但confirm回應遺失，只有exact next＋同／下一generation可確認；其他內容不能settle。取消、磁碟／IDB錯誤、校驗失敗、無效UTF-8／重複欄位／scope／domain、同版不同state或metadata、版本倒退，必須保留原紀錄，不重置空畫布或ACK未保存checkpoint。現單record 4 MiB上限需顯式拒絕，不截斷；大型畫布分塊／atomic manifest仍待實作，完成後另驗crash各階段／orphan／manifest CAS再集中驗收。UI與平台未接線不能代簽已耐久協作。

## 單一協作 owner 與狀態清理（最後集中驗收，尚未執行）

平台耐久儲存／repository 接線完成後，核畫布 notifications／Presence／狀態 UI 共用一個 owner，多個 flow observer 不增加 ticket 或 socket。run 第二次須拒，重連建立新 owner／epoch／ticket。驗 Preparing、Joining、CatchingUp、Live、Disconnected、Revoked、ResyncRequired、Failed 不互相冒充；只有 live_ready exact checkpoint 才 Live。交易 persist CAS／durable confirm 未成功不得更新 confirmedProjection；metadata 同版本不同 title／role 拒絕，較新 metadata需完整授權read／persist。測 owner／editor／commenter／viewer 加入，commenter不能寫內容但可讀。每個 async await途中換scope／角色／取消，舊owner不得再發布Presence／ACK。撤權、resync、timeout、異常或主動close後Presence立即清空、client關閉、publisher停用；未接原提交的operation_ack不可清pending或當receipt。現平台checkpoint／repo／UI待接線，這些不是已驗收證據；視覺由使用者判斷、效能最後。

## Heartbeat／房間 Presence 事件（最後集中驗收，尚未執行）

接線後核 join consume 未驗證成功前不得發 heartbeat 或顯示 Presence；30 秒未確認須退出。heartbeat 單調序號、兩筆未 ACK 停止；亂序／重複／超前 ACK、錯 epoch／workspace、字串序號、未知欄位須失敗，不更新租期。所有實體寫入序列化，失敗後不得再寫舊連線。Presence snapshot 僅一次建立 room，delta 缺段須清除並重新授權連線，舊 delta 不延長租期。檢查 100 字 Unicode 名稱、非法 control／UTF-8、假 cursor 字串數值、非有限／超界座標、超量／重複選取、connection 對應 user/client 被換。heartbeat 只能延長已驗證 Live room，不以 socket 開啟或任意訊息作證。撤權／角色切換／取消／離線後統一 owner 須清 UI 與 publisher，不能留舊游標。現 owner／publisher／畫布尚未接，不宣告正式協作通過；視覺由使用者，效能最後。

## 即時協作 ticket 準備（最後集中驗收，尚未執行）

產品接線後核 GET capabilities→POST ticket 是明確單次流程，途中換帳號／工作區或取消不得發下一請求。非法 capability limits／型別、未知 protocol／schema、不同 socket origin／path／query、超過 16 KiB 或非 JSON／無效 UTF-8，須停止。ticket 需 201、no-store、精確 URL／protocol、合法 token／expiresAt；不得保存到 durable、匯出或 log，不可把 token 放 URL。驗第一次 takeJoin 後第二次必須拒絕、ticket HTTP 慢／握手延遲超期、server Date 與 expiration 不符、socket握手前後 scope 改變；失敗不得重用 ticket、偷偷取得新票或自動重送 operation。Desktop／Android 核沒有 engine-level隱式重试；Web／iOS 核 handshake subprotocol header。這是源碼待驗項，不是已部署／正式認證通過的證據；正式 auth 尚由後端交付，現 x-user-id 僅開發模式。

## 正式協作 durable barrier／Live（最後集中驗收，尚未執行）

接線完成後核 ticket 不出現在 URL／headers／log，socket URL 同部署 origin、固定 path、无 query／credentials，subprotocol 未協商必須失敗。各平台核實 WebSockets engine 能力、handshake header 與 frame 配置上限；現新增 transport 未接到產品，不能代簽已即時連線。輸入錯 workspace／epoch、無效 UTF-8、超界 frame、缺段／亂序／部分重疊交易，必須斷線並停止 Presence，不改 durable checkpoint。模擬 persist callback 失敗、CAS 競爭、取消及 ACK 遺失：只有耐久確認成功才回 catch_up_applied。join barrier 不是已套用；只有 live_ready 的 content checkpoint／version／metadataRevision／connectionId 全一致才顯示 Live。較新 metadata 先 fresh read＋耐久保存，不以 join 缺少 title 的數字當完整狀態。snapshot_required 的 reason／barrier 核准後才裝入完整 snapshot；可比 barrier 新，不能拆交易或自行降低至 barrier。後續 ticket、heartbeat、reconnect、撤權、Presence、原提交 ACK／receipt／fence 必須一併集中驗收。視覺與遮擋由使用者判斷，效能最後。

## 雲端方案簿結果不明恢復（最後集中驗收，尚未執行）

首次送出之前核 durable journal 已保存固定請求與 server Date／TTL。模擬回應遺失、關閉 APP 再開：須由人點「確認重送完全不變的原請求」，key／ID／body／ETag 不變，後端回原結果才保存 Committed；GET 相同內容不是提交證據。測試原窗口內／60 秒安全邊界／已過期、TTL 降低、伺服器時間倒退、Date 缺失或多值／非標準、Age 快取、能力讀取超時與 dispatch 前延遲。任何時間不確定不得送 mutation 或另建同資源替代任務；舊 journal 無窗口只可讀。重送遭 401／403／409／412 或網路錯誤仍為 Unknown，不以重送被拒判原請求失敗。核 Web CORS 可讀 Date、可信 proxy 不改錯時間；超長在途請求與 server clock 異常仍屬部署／契約故障情境，不能以 client Date 防護當正式證明。取消、scope 切換、多 process CAS、ACK durable confirm 遺失須保留原身分及終態，不覆寫成功。視覺／遮擋由使用者判定，效能最後。

## 文件網格樣式（最後集中驗收，尚未執行）

在支持canvasStyle的本機文件開「文件網格樣式」，選lines／dots／none與自訂／auto color。預覽與色彩picker Apply僅改draft；cancel／close／換帳號／工作區／角色必須清preview，不寫文件。最後「確認保存」才建立versioned operation，background保持原值、styleVersion單調；Undo／Redo與reopen恢復exact pattern／color。個人showGrid=false、snap、viewport、theme都不得被正式style更新改掉。來源style被他人更動時拒舊capture，unknown保存重試保留同operation ID，不可重新配ID或改body。server無正式capability入口不能假稱已共享；雙端依BACKEND_CANVAS_STYLE_API_SPEC另驗。iPhone safe area／鍵盤／遮擋由使用者判斷，效能最後。

## 協作傳輸與 Presence（最後集中驗收，尚未執行）

目前Backend adapter應顯示REST polling而非即時協作已連線；member list／本機已保存／capability不能代替authenticated room liveness。未有socket服務不得開啟假的游標或Presence傳輸；未來WebSocket capability與實際TTL線上狀態分開核。輸入NaN cursor、無效UTF-8／surrogate、control identifiers／display name、超界selection IDs，必須拒绝且不更新room lease或peer顯示；正常Unicode名稱與既有epoch／lease／TTL更新仍需驗證。正式authenticated雙端／撤權／catch-up／reconnect依BACKEND_REALTIME_API_SPEC，不能以mock或REST結果代簽。視覺／遮擋由使用者判斷，效能最後。

## 快速方案 identified save（最後集中驗收，尚未執行）

補匯入任務重開：在stage已durable但正式save尚未commit時關閉APP，重開方案庫點「查詢待完成的本機匯入」；應讀到same requestId＋完整definition，不自動save／插入，重新審閱後再次勾選才能繼續。成功save的item／receipt及pending removal必須同generation。stage／save ACK遺失不能另配ID；已commit結果從正式library可見，不重造pending。v3缺pending欄位、重複request或超128 pending／4MiB滿額不得reset／silent GC。驗source素材reference仍需目前workspace bytes／權限驗證，不拿pending當grant；雲端自動同步另列待交付。

在本機模式JSON／bundle匯入中，模擬保存已發布但ACK遺失：原視窗重新批准保存必須使用同request UUID、same definition digest及同scheme ID，confirm原generation，不另追加方案。同UUID不同name／payload／schema／source必須拒絕。保存後rename／delete，原request重試不得覆寫或復活方案；receipt留存且4096筆滿額不能默默GC。驗native與awaited Browser實際durable barrier；讀舊library v1、寫v2及重開，v2 missing receipts／corrupt／invalid Unicode不reset原件。legacy Settings未宣告identified能力，不能用其contentdedup結果代簽此路徑。本轮未交付跨process匯入任務重開UI或user-level雲端sync，另列驗收，不以文件搬移冒充跨裝置自動同步。素材仍需獨立範圍與bytes驗證，視覺／遮擋由使用者判斷，效能最後。

## 時序圖引導式建立（最後集中驗收，尚未執行）

開啟時序圖建立→引導式編輯，新增Actor／Participant並左右排序；新增call、return、async、signal，選same source／target測自訊息，async／signal可切虛線。訊息上下移動只改明確semantic順序，不從畫布位置猜測。移除被引用participant需拒絕且保留舊稿，改名保持ID與訊息端點不變。loop包最後一步、alt／parallel將最後兩步變分支，原巢狀內容不得丟失。選擇root或巢狀branch，改標籤／新增訊息／排序／刪除／再wrap只作用於該分支，另一分支不变、不flatten成單一路徑；最終至少保留一筆branch step，missing location／過深／非法Unicode或超byte限制不得覆寫舊稿。確認預覽不改canvas，最後另行確認插入；測Undo／Redo與reopen、權限／版本漂移和unknown保存仍用原transaction。無效source不能偷偷重設為新圖。close／discard未保存操作由使用者明確確認，視覺／遮擋由使用者判斷，效能最後。

## AI 草稿修改前後差異（最後集中驗收，尚未執行）

補收合記憶：差異區預設展開，使用者收合後關閉／重開AI視窗及APP應沿用，切換其他面板後不得覆寫此偏好；僅保存UI section token，不保存API Key、prompt或approval。長before／after可點「複製完整…（私人內容）」核原資料，空值按鈕停用；只明確點擊才改clipboard。顯示截斷不得切斷Emoji的surrogate pair，複製資料完整但revision normalize1的標示不變，不能當正式operation或import draft。此輪未執行驗收。

AI產生草稿後，先看新增／修改／移除計數與預設展開的差異清單。逐ID展開核對before／after：文字、shape、transform、parent、locked、relation兩端／direction／label／geometry及媒體reference；這是structural資料，不應宣稱看懂媒體像素。僅顯示revision正規化為1，不影響原proposal version gates。排除一筆工具操作後，預覽與差異必須一起變化；有依賴衝突時不可套用。20筆分頁不得漏資料或重複，4096字元節錄需有明顯截斷提示，不改完整草稿或作匯入來源。最後另行批准Apply，確認既有保存佇列、Undo／Redo及重開流程；Claude真服務結果不可用OpenAI成功log代簽。視覺／遮擋由使用者判斷，效能最後。

## 新原素材 preparation v2 驗收前置（2026-10-11，後端已交付、APP 待集中驗證）

票券取消／恢復序列：已有ticket A→開renew確認→取消→普通「恢復原票券」，必須仍用A的ticketRequestId，不能使用未批准的replacement B。明確批准renew B後若stage或簽名ACK遺失，普通恢復改查durable最後身分B，不mint C；原ticket A不得當B繼續PUT／complete。開始lookup清舊phase，401／403／404／metadata差異後不得仍呈現舊Prepared／Ready actionable狀態，但原request與lease歷史不刪。以上待最後集中執行，不宣稱本輪已通過。

metadata契約補確認：準備null尺寸／duration、或APP與backend解析duration不同的素材，核Ready envelope的declared／verified界線。後端正式釐清前，APP應提示metadata差異、保留request，不以重傳／新asset／略欄位方式假成功。此問題目前為code-inspection推論，不能當成真HTTP已重現。另驗成功envelope streaming限64KiB／JSON／strict Unicode與duplicate-key，非JSON／空／超界／timeout不能發布identity；不改原檔transfer大小上限。

最後集中驗收錯誤分類：401應提示認證；403應提示權限不足而非不存在；upload_lease_expired只能明確renew；upload_lease_superseded先查原lease；upload_not_found不自動重傳／complete。404保留requestId，不將其解讀為晚到prepare不可能提交。HTML／巨型body／無效UTF-8／未知code不呈現原body或details，保留HTTP分類。私人同步記錄匯出應含assetUploadPreparations與public lease歷史、exact IDs；不得含signed URL／headers／keys／檔案路徑／素材bytes，也不可拿匯出檔直接重送或當授權。

後端已依BACKEND_ASSET_PREPARATION_V2_SPEC交付fixtures／capability；APP 新增接線尚未集中測試。不得用Media v1反覆POST驗收unknown ACK恢復。最後集中驗證時核固定request ID與metadata digest golden vector；provider body不得含local ID／路徑／owner／review。Ready source必須屬本機owner／workspace及當前審閱，wrong kind／unsafe duration／snapshot drift拒絕。receipt的request ID／digest／owner／workspace／尺寸／hash及固定remote ID均exact；同ID改body拒絕，Pending或Ready回覆只保存identity，不當bytes或授權證明。

全部功能完成後，集中驗收durable-before-HTTP、prepare回覆遺失查原receipt、不產生新asset／request UUID、expired lease的獨立批准、complete unknown先查phase、舊PUT不能改ready、ID collision保留receipt但停止graph投影。APP 同步視窗已加入上傳區：檢查素材→確認保存→確認prepare→查原狀態→確認原ticket（重開沿用原ticketRequestId）→確認PUT→另外確認complete→手動查phase。換票必須走明確renew確認，取消保留身分，不自動重試／刪除。原檔分批讀取，PUT核size／hash／EOF；Ready不能提前當Node發布完成。後續映射／canonical review尚未接完，產品UI與真兩端不得由既有模型tests代簽；視覺／遮擋由使用者簽核，效能最後。

## 映射素材 receipt／fence／rebase 產品路徑（2026-10-11，未人工簽核）

新增本機上傳素材的最後集中驗收流程：上傳與complete後手動查到ready，再另行確認「保存已上傳素材映射」。此步必須重新驗原檔size／SHA-256／EOF、server metadata／receipt及fresh head／permission；不得以先前ready文字當永久證明。映射保存後，本機Node的primary／thumbnail UUID保持不變，原合併方案與receipt不得被改寫；UI提示重新比對，舊方案不能直接套用／prepare。重新審閱的canonical目標應使用server UUID，明確套用後Node／Undo仍使用local UUID，之後prepare與send採server UUID。新server素材尚未被任何server Node引用時也應可準備交易，但Pending／Missing／撤權／wrong kind／metadata漂移不得送出。

模擬映射保存回覆遺失：相同publication只confirm已保存generation；若根本未保存，必須fresh驗證，不重用舊授權proof。ID碰撞／錯document／後續local edits不得覆寫。取消映射驗證不插入Node、不advance baseline、不刪除原檔。此段為待執行SOP，不代表本輪已完成測試；視覺與遮擋仍由使用者判斷，效能最後。

只使用已在server存在且授權的original與Image thumbnail，先下載／驗證並套用本機，再審閱其他本機變更。prepare交易的workspace引用必須是source ID，不得把local UUID送server；confirm送出前仍需獨立批准。收據確認後本機Node／Undo保持local ID，baseline保持source ID；沒有假pending。metadata改變／撤權／head漂移應停止，不能reset成新交易。

測試fence與commit競爭：fenced結果保留原request及local操作／mapping，commit勝出則核original receipt，不當取消。再製造commit前／後peer變更及later local改動，保存四input rebase；預覽／確認使用local ID、核真original commit、fresh授權與private bytes，驗verifying與cancel。unknown ACK重開後同操作重試只confirm原generation；Undo／Redo與canonical continuation保持兩種引用的exact對照。新本機素材仍缺server materialization時不得送出。

此路徑已有工程fixtures與產品接線，尚無live雙端／actual visual／VoiceOver簽核；不得由受控mock判正式多人或完整跨端素材已完成。效能最後。

## Canonical rebase 與現存 server 素材前置檢查（工程回歸）

fixtures保持原submit、later local、真正commit、latest remote四份輸入：本機Node使用local UUID，server兩份使用source ID。只有identity差異不應要求object conflict選擇；later local altText應保留，peer前／後新增均不得消失。雙方altText都改時必須顯式選擇，不以identity normalize抹掉真衝突。原映射／commit不可寫成latest head。mapped rebase publication仍未接，不能用計畫測試簽完整跨端流程。

server已存在的image/png primary與獨立Image thumbnail應允許準備其他合法審阅變更，prepare不代表送出；thumbnail Pending／wrong MIME／wrong workspace／wrong ID必須在local outgoing與server draft前拒絕且原snapshot不變。local→server新素材upload仍待實作，不靠這個case解除gap。

## 素材審閱→確認本機套用（2026-10-11，未人工簽核）

在隔離QA同步空間保存含foreign MediaNode的審閱，先逐一確認原圖／影片及thumbnail的mapping與下載；縮圖必須為Image，不能由原圖取代。按「審閱本機套用」檢查預覽，再「確認並保存到本機」：固定驗證文字與取消按鈕應可見；查遠端授權並重讀private原檔，不自動download缺失檔案、不POST canvas。缺失／corrupt／撤權／改head應保留原畫布與操作ID，未成功不得顯示已套用。

成功後Node能使用local ID、Undo為單一操作、原remote review保持source ID；read-only settle後無假pending。人工驗確認後取消、重開Undo／Redo、image／GIF／video原檔與thumbnail显示，以及存檔未知ACK重試只確認原generation。更新UI時不改審阅選項或另mint操作ID，history已進展後舊retry必須拒絕。mapped upload／rebase目前仍gate，不可簽為完整雙向跨端同步；這次工程回歸不代簽GUI／遮擋／VoiceOver，效能最後。

## Materialized 套用交易工程回歸（2026-10-11，非產品 GUI 簽核）

固定scope、保存foreign素材mapping、下載原檔後，以fresh驗證結果及固定operation呼叫applyMaterializedSyncProposal。檢查local workspace引用local ID，但proposal.target與baseline保留canonical ID；比較應Unchanged，read-only settle後無假pending。注入publish後confirm失敗，重開以同驗證／operation重試，generation不得增加。重開恢復Undo→保存→Redo→保存，Node使用原local素材ID，mapping不得刪改；歷史進展後舊套用重試拒絕且不覆寫。再保存新審閱，superseded保留canonical review＋local operation＋captured mapping。錯target／scope／snapshot必須零publication。

此底層尚未接產品確認入口，不能當實際手機／Desktop操作已通過；含mapping的upload/rebase仍有gate，需後續正式整合與雙端驗收。既有原檔metadata／SHA-256與每次批准規則維持。

## AI 認證拒絕提示（2026-10-11，未人工簽核）

在隔離QA Router使用無效測試key，不使用或分享正式key。理解驗證與工具視窗分別確認：HTTP401終態主狀態顯示認證拒絕並保留HTTP碼；None模式提示準備時無標頭，Basic challenge提示檢查代理且不索取帳密，其他401提示檢查服務網址／金鑰／認證方式與Router上游。HTTP403顯示存取拒絕且不聲稱key有效。不得自動重送、清空錯誤或套用畫布變更；只有重新明確授權才可再次請求。安全log不得含key／原始error body。舊HTTP200紀錄不證明本次401已排除；視覺／遮擋與VoiceOver由使用者簽核，效能測試排最後。

## 遠端原素材下載產品入口（2026-10-11，未人工簽核）

使用本機模式，在「空間→伺服器同步設定與比對」連線隔離QA空間，保存採用遠端MediaNode的審閱方案。此入口只準備／保存素材，Node引用套用尚未交付；原素材下載成功不能簽為完整素材同步。

1. 「遠端原素材」列表每頁20筆、依asset ID去重；前後頁均可抵達所有候選，既有本機Node引用不重列。未連線／僅歷史remote／過期方案不得檢查。外部GIPHY節點不走此原檔拷貝流程。
2. 點「檢查ID」只refresh服務／查metadata，不prepare本機catalog、不下載、不保存映射。顯示MIME／byteSize／目的local ID；沒有實際Ready原metadata、範圍／head或任何primary資料改變都拒絕，狀態固定可讀。
3. 審阅提醒後按「確認保存身分（不下載）」。保存前重查metadata／head／local snapshot；完成僅新增mapping，canvas／checkpoint／outgoing與asset catalog不變。取消不保存；unknown保存ACK重試用同prepared UUID，不另配ID，重開可檢查讀回舊mapping。
4. 另按「審閱下載／重新驗證原檔→確認下載／驗證」。需明示私有本機儲存與網路／空間成本、正確快取可能保留、不套用／上傳Node。取消確認不download。每次操作（包含Ready快取驗證）都要重新批准，不自動retry或因metadata檢查完成就下載。
5. 下載顯示Authorizing／Recovering／bytes進度／Verifying及固定終態；「取消素材落地」能中止，保留mapping，正確已發布private cache可保留。錯誤／取消不留下誤導的Ready／活動進度，不吞掉提示。避免把200MiB檔案放進20秒一般服務動作timeout。關窗／空間切換取消job；parent其他寫入／連線／翻頁及複數下載在busy時禁止。
6. 完成訊息必須明確「本機原檔已驗證；尚未套用Node引用」。重開後不由saved mapping宣稱Ready，須重新檢查／授權及hash；原ID恢復、cache reuse及prefix retry沿前述工程SOP。對非PNG的Image／GIF／Video用MIME分類驗證，不拿`image/png`與`image`字串直接比較。
7. custom server base URL測metadata与download同一scope fingerprint，不能改走default backend。gateway scope不符在HTTP／local prepare前拒絕；遠端gateway每action完成／取消／失敗皆close，本機borrowed gateway不close。source creator可不同reader，fresh ACL仍須服務決定。

實際OS／browser點擊、私密資訊可讀性、20筆分頁、手機safe area／keyboard／遮擋與VoiceOver由使用者簽核；程式回歸不代簽GUI或live服務。物件儲存／雙端／完整Node引用與上傳／背景／時序能力仍待驗，效能最後。

## 素材固定本機身分：工程驗收（2026-10-11）

這是同步materialization的gateway基礎，尚無使用者遠端素材落地按鈕／映射保存／引用替換入口，不將本節勾為完整素材同步交付。只在隔離fixture root／IndexedDB／QA模擬器測試，不手改使用者catalog或private originals。

- 先保存同一Pending descriptor及自己產生的local UUID；preparePinned→關閉重開→相同descriptor重試，ID、metadata與catalog數量不變。模擬已發布metadata但durable ACK遺失，重試只確認原generation，不另生asset。
- 同UUID改checksum、byteSize、尺寸、mediaType、owner、workspace或createdAt均拒絕；Viewer、非canonical MIME、無效SHA-256、非UUID、超200MiB及遠端thumbnail metadata亦拒絕，拒絕不能覆寫舊asset／original。
- 串流upload／confirm實際fixture原檔後，再以原Pending descriptor重試，必須返回原Ready而非降為Pending、不重傳bytes或新增asset。準備回傳Ready只反映原marker，不能代簽重新驗證原檔或fresh服務ACL。
- 一般選檔import及既有original下載／checksum回歸須通過。Browser測真正IndexedDB＋original store重開，不將測試fixture視為實際browser權限／磁碟／GUI通過。Android新Device case只編譯不得標實機passed；native fixtures／模擬器與真人OS picker／雙端服务分開記錄。
- 真正source→destination mapping須在preparePinned前durable保存，尚未接線前原同步素材缺口gate保留；不得把remote UUID當local UUID、只換Node assetId或用thumbnail取代原檔。視覺／播放／遮擋由使用者簽核，效能最後。

映射工程追加：saved proposal／rebase含remote MediaNode時才可reservation；核source owner可以是另一位creator，binding reader與目的local owner／client仍須exact。模擬mapping文件已發布但ACK遺失→重開→讀原mapping→同record重試，destination UUID不變、封存不重複、原workspace／checkpoint／review／outgoing不動。換source metadata／destination／scope、非target原檔或過期review均拒絕，metadata有非法Unicode也拒絕。後續local edits後已有mapping確認要保留編輯，不能藉此獲得fresh下載或套用授權。保存mapping後catalog尚空；按原mapping preparePinned才有Pending，原檔upload／confirm才可Ready，這三步不可混為同一驗收。查private evidence JSON含完整`materializations`，不含signed URL／headers／檔案bytes。尚無產品download／Node替換入口，此工程case不代簽完整服務交付。

下載協調工程追加：用保存mapping、fake服務授權／bytes及真native原檔／IndexedDB，驗每attempt先refresh head／role及source authorization，metadata任一primary欄位變更應在local prepare前拒絕。長度不足、多餘byte、錯SHA-256、取消／scope失效不可confirm或返回Ready，不換ID／刪mapping。完成後local原檔須再讀hash，source授權及remote／local snapshot再查；Ready快取重用也須fresh authorization，不再download遠端，但原檔損毀要拒絕。確認ACK遺失後重開以原ID恢復，不產生第二份asset。

Browser需真的讓第一個1MiB前綴transaction完成後中斷，重開驗cursor保留，再以不同HTTP callback大小重播相同bytes，逐段核前綴後追加。改前綴要拒絕且舊bytes仍不變；abandon tombstone／sealed不能resume。傳輸至多1MiB input batch／consumer buffer及單一queued64KiB piece，不能全影片ByteArray；此為結構限制測試，不把它簽為最後階段效能驗收。末端撤權／head改變不發布Ready，正確private cache可保留供後續重新審閱，不聲稱服務revocation可自動擦除已保存本機資料。產品下載確認、Node套用、real service／雙人／OS picker／UI尚待驗，視覺／遮擋／VoiceOver由使用者簽核。

## 本機同步上傳入口驗收（2026-10-10）

這是新版 APP 驗收流程，尚未人工簽核。使用隔離 QA 帳號／空間，記錄平台、build、帳號／空間對應及原 transaction ID；勿記 token、signed URL 或未授權私人圖文。現階段不能以基本文字交易通過代簽完整素材、時序／背景或正式多人能力。

1. 本機模式進「空間→伺服器同步設定與比對」，明確連線選 QA 空間，審阅兩端與三方差異後保存 binding。一般啟動／畫布編輯不得因此自動送出 canvas 請求。
2. 兩端同標題，用基本文字節點建立差異；選擇合併方案、二次確認保存，再審阅並確認本機套用。驗 Undo／Redo 及關閉重開，確認在實際上傳前伺服器內容仍未改變。
3. 按「準備並保存伺服器請求（不送出）」；此步可能 refresh／讀素材 metadata，但 canvas operations POST 必須為零。準備完成顯示原請求已保存；重開後 transaction／wire IDs／clientSeq 不變。未支援 shape/path/sequence/style/media 要顯示缺口，不可丟欄位後上傳。
4. 按「審閱伺服器上傳」，看完整 target 預覽及不含後續本機編輯的說明；取消不得送出。只有按「確認送出保存的原請求」才進收據檢查與可能的 canvas POST。
5. 原 receipt Unknown 且遠端仍為原審閱基準才送原請求；Committed 應核完整 log 並結算，零重送；Fenced 禁止重送，保留本機。此三條要以服務記錄比對，不能單凭文字狀態簽核。
6. 用測試代理模擬送出後丟失回應，重開後先查原收據；不得生成另一 transaction 或自動 retry。另模擬 wire prepare 成功但 local identity 綁定中斷，續作本機編輯後恢復原 identity，新增編輯不得丟失。
7. 原 commit 結算後關閉重開：checkpoint／已核對 commit／原 outgoing 身分應恢復，local outgoing 不再 pending，原合併 Undo 可用。若其間新增本機編輯，仍 pending；若原 commit 後 remote head 已變，不得簽為完整同步，保留資料並標記新三方審閱待辦。
8. 測切換空間、關閉視窗、取消、撤權、不同 server fingerprint／account／client、stale version、不同 pending transaction；不得錯送／清另一個草稿。無支援能力與 Unknown／Fenced 要有可讀的固定訊息，不瞬間消失。
9. 用隔離原交易測「審閱停止原請求→確認 fence」：取消不得 fence；Unknown才可對該 exact pending fence，Fenced後保留原畫布／基準及停止方案封存，可重新審阅。另測 commit贏得競爭：應核對結算，不得顯示取消或復原server。不得清其他adapter草稿。
10. 模擬停止adapter pending後、清active draft前中斷，以及本機archive提交後丟失ACK；重開能回到原交易恢復，不生成第二fence／transaction，原準備身分及本機新增編輯保留。確認停止封存數量與Undo（僅內容仍符合原套用digest時），新審閱不能刪除舊封存。32筆上限只可拒絕追加，不可靜默清掉舊證據；正式archive匯出仍未交付，勿在使用者真實資料上測滿額／kill。
11. 原交易已Committed而peer又有新編輯：查原收據後應開兩階段審阅。第一階段「原本機套用結果→本機後續意圖／actual commit」須保留此前未見的peer additions；第二階段「actual commit→已審阅意圖／最新remote」須保留peer後續變更，真正衝突必須明確選擇。各步取消不得替換畫布／發canvas POST。
12. 確認「保存rebase方案」本身不套用：重開後原workspace／checkpoint／original outgoing不變，保存的兩份choices與target可讀回。本機續編輯應標過期、重新審阅；遠端head／role在保存前變更須拒絕。unknown ACK重試須保留同一confirmation ID，不可把方案保存簽為完整同步恢復。
13. 保存後另按審閱本機套用，再明確確認：重查原receipt／actual歷史commit／最新head／role及本機內容，任何漂移須拒絕。成功後畫布為審閱target，checkpoint必須是原actual commit而不是最新head，原提交身分與兩階段choices在rebase封存保留；此步canvas POST為零。重開驗Undo回到套用前本機內容、Redo回到target；本機保存已發布但ACK遺失時重試只確認同generation，不新增operation或重複history。後續編輯後不得再次套用舊確認。包含未materialize遠端素材的target須拒絕，不產生失效引用。後續同步仍須獨立準備／審阅／批准上傳；正式服務、雙端、撤權競爭與GUI未驗收前不得簽為完整同步。

14. 已本機套用但尚未準備outgoing時，另一端再編輯／本機續編輯：重新載入最新遠端並開三方審閱，可保存新方案，舊proposal／applied／checkpoint封存保留，畫布與checkpoint此時不變；取消不封存。保存確認時head／role漂移、不同binding、server pending／draft或local outgoing均拒絕，不跳過原receipt。關閉重開後新方案、舊封存及原rebase提交證據保留；若本機內容仍等於舊套用digest，舊Undo仍可用；有後續編輯不套用過期inverse。32筆封存上限拒絕追加，不靜默刪除；新target還需獨立批准套用及上傳。測保存已發布但ACK丟失重試，封存只增加一次。

15. 「審閱同步記錄匯出→確認匯出私人同步記錄 JSON」須顯示私密畫布／帳號工作區身分與原請求記錄提醒；取消確認或儲存選擇器不產檔。Desktop／Android／iOS選新檔或資料夾，Web只可顯示下載已請求，不宣稱磁碟保存完成。選擇器不得受20秒服務逾時限制。選完後重新讀本機snapshot，若自審閱以來內容／封存已變或scope切換則拒絕寫入，目的地應被釋放。驗UTF-8繁中／emoji／換行完整，檔案schema為boarderless.local-sync-evidence.v1；核原outgoing／stopped／rebases／superseded、checkpoint、兩份choices／target與原operation身分均不截斷。匯出前後generation／畫布／封存／pending不變，零HTTP或fence。不含金鑰／transport endpoint／素材原檔，也不是raw server receipt/log完整備份；禁止直接匯入或重送此檔，歷史記錄不當新授權。16MiB上限及非法Unicode拒絕而非產出部分檔。封存清理尚未提供，匯出不解除32筆上限或自動刪舊記錄；安全位置、分享及保存由驗收者管理。

使用者簽核視覺預覽、狀態可讀性、手機鍵盤／safe area／bottom sheet／遮擋及 VoiceOver；效能驗收排最後。保存結果、必要截圖與安全請求身分，不提供 API Key。工程 Mock／IndexedDB 測試不代簽真人雙端、正式認證／reconnect、OS picker 與全素材能力。

## Desktop單一寫入程序驗收（2026-10-05）

使用新build；先自行退出其他舊build／CLI writer，勿把取得新鎖當作已排除舊程式。此項尚未人工簽核，不啟用migration。

1. 正常啟動，記錄build／OS／程序與工作區；再從相同launcher啟動，應聚焦原視窗，不另建立寫入APP。測第二次帶工作區參數，應在原程序開啟該workspace。
2. Mac關閉視窗再開：仍應回到原程序，不能因視窗隱藏而放棄lease；Dock reopen與workspace launcher各驗一次。
3. 原程序尚在啟動且不能接socket時，第二程序只顯示持鎖提示並停止，不可建立另一APP／重設Settings。提示可讀性與遮擋由使用者判斷。
4. 明確Quit後重新啟動應成功。若測強制終止，先用隔離QA資料／測試程序；鎖可再取得不代表草稿已durable，資料恢復另依receipt驗收，不拿使用者未備份草稿做kill測試。
5. lockfile不得以刪除／替換方式解除；遇安全權限錯誤應停止並保留資料，不教使用者刪鎖繞過排他。Windows／Linux各記錄實際結果，macOS自動測試不能代簽其他OS。

保存第二launch、open、focus、close／Quit與故障提示的結果及截圖；不記錄instance token或認證。GUI／VoiceOver／遮擋由使用者簽核，效能仍最後。

## AI 預設 flowchart 補充（2026-10-05）

新 build 理解驗證預設顯示 Mermaid flowchart，N/E 短 ID、群組 subgraph、四種箭頭方向與 label／非泛用 intent 均須保留；回應用短 ID 核對。驗證包不得包含原 UUID／工作區名稱／版本／座標／素材 assetId。node text 可能本身有私密內容，仍需審查。只有建立時正式選定特殊圖型才可換語法，不按外觀／座標猜圖型；目前特殊類型 metadata 尚未接入。空名稱、Emoji、特殊符號、平行線、反向線、群組 nesting 均須重驗。詳 AI_DIAGRAM_EXPRESSION_SPEC；此前完整 scene JSON 檢查項以新合約為準，視覺／語意人工簽核與效能最後保持。

## 連線標籤自動布局補充（2026-10-05）

新 build 建立至少兩條重疊／交叉線與長標籤，確認標籤優先在線側、相互分離且文字完整換行；移動、旋轉節點／縮放 viewport 後確認位置重算、不改資料或增加 Undo。文字含中文／換行、系統大字級與手機版皆需重驗。密集布局不保證零衝突，遮擋由使用者簽核；效能最後。手動拖曳、自循環、插線與時序精靈目前僅規格，依 SEQUENCE_AND_RELATION_INTERACTION_SPEC 後續驗收，不列已通過。

## AI 理解驗證補充（2026-10-05）

依 [AI_UNDERSTANDING_ACCEPTANCE.md](AI_UNDERSTANDING_ACCEPTANCE.md) 執行唯讀驗證。保存 scope JSON／模型／完整回應／inventory 結果／人工語意結論，不記錄認證。用群組階層、循環／分支、反向／雙向、同名不同 ID、旋轉交疊與 scope 外資訊檢驗模型。自動 ID／方向／parent 核對通過不代表理解；圖片無像素不可推測內容。使用者另簽核 modal 可讀性、鍵盤貼上刪字、取消、手機安全區／遮擋及 VoiceOver。真 API 必須在審查後明確同意，確認驗證不改畫布，效能留至最後。

更新日期：2026-10-02
文件狀態：執行基準 v1

## 手機多選模式觸控回歸（2026-10-05）

新 build／安裝後，iOS 與 Android 分別驗文字、圖片／GIF／影片及群組。工程修正：觸控使用系統 touch slop，多選時不進入物件移動；手勢 callback 使用最新 selection state，模式切換重啟手勢，隱藏單物件旋轉／縮放／連線 handle；退出清除選取及 preview。滑鼠拖曳維持原即時反應。

1. 開多選，輕點 A 時手指稍微抖動，應只切換 A 選取，不移動、不新增 history／後端 move、不搶成單物件編輯。
2. 再點 B，A＋B 都保留；再點 A 應只取消 A。重複快速點按，不受 focus 或舊 callback 覆寫。
3. 多選時在物件上明顯拖曳，不得移動物件或選成單物件；跨過觸控容差的拖曳不算一次點選。取消手勢不留下 preview。
4. 關閉多選，原選取高亮清空；點 A 再點 B 應只有 B。退出／重新開啟數次，以及 Escape（外接鍵盤）退出，均無殘留選取模式。
5. 關閉後正常拖曳仍可移動，輕微抖動仍當點選；旋轉過的物件方向正常。Desktop 滑鼠／Shift 多選維持原行為。

記錄平台／OS／build、物件型別、預期／實際結果、操作錄影或截圖。純手勢 classifier 測試與 native compile 不代替實機 UI、VoiceOver、遮擋與視覺簽核；效能最後。

2026-10-05 Undo／Redo 還原：按 UNDO_RESTORE_ACCEPTANCE 的专用QA畫布步驟驗新增Undo/Redo、群組／child／連線delete Undo/Redo反覆循環、保持ID／內容／parent／intent、還原後再編輯、relation-only與media asset引用。工程live不代替四平台UI／VoiceOver／遮擋簽核。正式fixtures16／20問題由後端維護者修，不能簽31份全面通過；效能最後。

2026-10-05 iOS picker返回修正重驗：新build/install後，從照片格子點選一張，應直接關閉system picker回APP匯入；按X應關閉並顯示取消，不得僵住。連續取消／重開、選照片／影片、慢速iCloud、APP取消時late callback不得新增Node。原生wrapper身份回歸已過，不等於GUI已過；不需新增完成button。記錄OS／build／步驟／實際結果與截圖，視覺與遮擋由使用者判定。

2026-10-05 品牌resource工程驗收路由：AndroidHost需depends本次APP APK並把APK作test input，直接讀精確packaged entry，仍驗原PNG bytes／signature／dimensions／hash；缺APK／entry或超量不得source fallback，不設Android stub return-defaults或ignore。AndroidDevice與其他平台保留真正Res reader。76671完整Host656cases零fail/skip，但APP與Device APK建置不代替實際Device資源載入、品牌位置／清晰度／VoiceOver、正式GIPHY整合批准；人工視覺由使用者簽核，效能最後。

2026-10-05 Android恢復屏障：record／remove採checked commit，false當未知保存結果，不送complete或報dismiss成功，不rollback／自動retry。APP在背景dispatcher等待保存，畫面mutex只串行同store寫入，不宣稱多process安全。Host protocol double不當磁碟證據；AndroidMediaRecoveryDeviceTest真實XML case需連線裝置執行，APK綠色build不等於已驗。產品另驗cold launch initializer、舊提醒同位置可讀、已complete取消→殺APP→重開同scope→提示與明確回查／插入、切換scope隔離、dismiss不刪server asset；截圖不含key／signed URL，視覺／VoiceOver／遮擋由使用者簽核，效能最後。

2026-10-05 Desktop恢復保存：預設store同Preferences位置加flush及回讀gate，flush失敗不可送complete／假報record成功，legacy不可因未驗證寫入而刪除。工程fixture以獨立JVM record後halt（不跑正常shutdown hook）、新JVM讀取並dismiss後halt、第三JVM確認移除；只證明隔離Preferences保存，不代簽GUI、網路取消重開、多writer、硬體斷電或其他平台durable。產品驗收：QA工作區取消已送complete的匯入，記錄asset ID→結束APP→重開同user/workspace→提示保留→明確回查→只有使用者插入後才建立Node，dismiss只刪本機提醒不刪server資產；切換user/workspace不漏scope。視覺／VoiceOver／遮擋由使用者簽核，效能最後，詳MEDIA_LIVE_ACCEPTANCE。

2026-10-05 Desktop sink證據：67390真實signed GET配正式JvmFileAssetDownloadSink，成功發布原檔byte一致、abort不刪完成檔，consumer首chunk後取消不emitReady且part清除。隔離測試目錄不等於APP GUI／播放器或正式cache生命週期放行；小fixture可能首chunk即全body，不能宣稱TCP中途掉線。四live cases普通suite均skip；唯讀scope與env命令見MEDIA_LIVE_ACCEPTANCE。其他平台sink、實際GUI取消／重開、過期票券／撤權仍分開驗，視覺由使用者簽核，效能最後。

2026-10-05 素材取消工程證據：93711驗真實PUT後未complete／真實complete返回後的APP coroutine取消，前者pending-only abandon、後者保留Ready及RecoveryRequired，不建Node。原生UI取消按鈕、網路傳输中斷、OS殺APP／重啟仍必須分開驗；不要以gateway gate或InMemorySettings代簽。一般suite目前3個opt-in live cases均skip；功能trace／新QA scope／正常precomplete清理與未刪Ready資料見MEDIA_LIVE_ACCEPTANCE，效能最後。

2026-10-05 GIPHY 分頁驗收（使用者執行）：開啟熱門或提交搜尋，記錄目前結果；按一次下一頁，成功後應定位第一筆新增結果，舊結果仍能往上捲動查看，不去重／重排。改搜尋詞，即使筆數相同也回到頂部；載入期間不可重複送出，尾頁無下一頁，空追加頁／失敗保留原viewport；429依既有提示等待，不自動retry。分別驗Desktop滑鼠／鍵盤與手機觸控，品牌固定、選取／輸入焦點正常；視覺、VoiceOver、遮擋由使用者簽核。記錄平台／查詢類別／步驟／預期／實際／截圖，不放API key或含key的URL。自動offset／狀態測試不是UI簽核，效能最後。

2026-10-05 素材失敗邊界補充：26241驗真實worker PNG checksum_mismatch拒絕，以及complete已成功後APP gateway注入IOException、同asset回查ready與明確插入／dismiss。不是TCP斷線、OS重啟持久保存或所有格式／取消情境；一般suite兩live cases skipped不簽live通過。完整scope／保留QA資料見MEDIA_LIVE_ACCEPTANCE。

2026-10-05 素材live驗收補充：MEDIA_LIVE_ACCEPTANCE 記錄實際APP JVM data／gateway／repository對localhost／S3-driver／worker的六格式校驗下載、Node保存、新client載入、viewer讀寫／outsider及撤權拒絕與縮圖證據。一般suite中livecase為skipped，不簽live通過；opt-in run必須核對本次XML skipped=0／timestamp／scope，禁止Gradle cache取代。每次新增獨立QA身份／畫布，保留trace，不擅自改使用者prefs或清理真實資料；錯誤／截圖禁止貼signed URL／key。該data證據不是原生選檔、正式sink、播放、OS冷啟、雙人UI、正式auth或production放行。使用者在自己的QA工作區依六格式檢查畫面／GIF影片播放與音訊、縮圖／連線、旋轉拖曳、重開與下載，再簽視覺／VoiceOver／動態島／遮擋；原生picker／TLS-CORS／過期ticket／取消恢復／撤權後已發ticket／GC另做功能證據，效能最後。fixture用repo自有media，不借此放行GIPHY下載保存。

2026-10-05 AI HTTP 補充：MockEngine＋channel 可驗 status／mime／charset／declared-size拒絕、redirect不轉向、單次呼叫、不自動retry、取消／consumer error／terminal釋放。正式接線後再驗實際request／socket逾時、JS request-only與Darwin缺connect timeout的引擎差異、TLS／LAN／CORS／認證／撤權；不以配置值當 elapsed-time 證明、不把response gate當完整AI上線。取消不得變成失敗重試；401／403／格式／大小錯誤不能提示可retry，429／5xx／網路錯誤的手動重試須有使用者操作，不靜默重送或雲端fallback。context同意／proposal逐筆確認／commit與Undo仍Pending；沒有新的APP AI入口，視覺／VoiceOver／遮擋由使用者簽核，效能最後。

2026-10-05 AI SSE 補充：有界 byte parser 的 channel fixture 可驗分片 UTF-8／換行／多行事件／EOF／取消／大小拒絕與資源釋放，不是正式 socket、HTTP status／Content-Type／timeout、三類 provider、Key 或 UI 驗收。正式 transport 接線後才驗真實中斷／取消／撤權及 context 同意、proposal 逐筆确认、提交與 Undo；文字串流不得自動改畫布。單 byte 與 8 MiB 上限 case 是功能邊界測試，不當效能測試。視覺／VoiceOver／動態島與遮擋由使用者簽核，效能最後；本輪沒有新的 APP AI 入口，不以 fixture 代簽。

## iOS 照片／影片來源驗收（2026-10-05）

重新build/install後，匯入應先有「照片與影片／檔案／取消」來源選擇。兩來源分別驗取消／重開、選取、上傳與Node重開；Photos只能讀明確選取項目，不要求整庫權限、不刪照片原件。驗PNG/JPEG、GIF保留動態、MP4；HEIC/MOV需記錄Compatible實際輸出，無支援representation應提示不支援，不當原格式完整支援。另測iCloud下載失敗／120s匯出timeout、等待時APP取消／背景／換workspace、不會晚到建立Node、暫存清理與資料隔離。工程helper4cases與雙iOS target compile不代替PHPicker GUI／真實callback取消／實機storage驗收。來源menu、Photos system UI與鍵盤／safearea遮擋、VoiceOver及視覺由產品負責人簽核，效能最後。

## Web 選檔取消競態重驗（2026-10-05）

工程已重現並修focus早於change的250ms取消競態。產品重驗需記錄瀏覽器／版本、JS或Wasm build、檔案來源（本機／雲端）及格式，先重建／重新載入新media-import.js。選完檔案應进入prepare／upload，而非顯示已取消；檔案provider較慢、多次window focus也不得取消。另測native取消與APP取消、同檔重選、取消後late change不啟動上傳。缺input cancel的舊瀏覽器會維持等待，需explicit取消或120s選擇錯誤；timeout不能簽為使用者取消。Node競態測試不是實際storage上傳或瀏覽器原生picker驗收。視覺／VoiceOver／遮擋由使用者簽核；效能最後。

## iPhone API 配置修正重驗流程（2026-10-05）

已修原device loopback預設：iphoneos SDK解析192.168.68.65:3000、simulator保留127.0.0.1；直接Xcode配置2case通過不代表實機網路／上傳完成。驗收必須：

1. 記錄實機／模擬器、OS、APP build、Wi-Fi、檔案來源／格式／大小；先重新build/install使Info URL更新，保留原APP資料，不以清除資料作預設修復。
2. 實機與開發Mac同網路，允許「設定→隱私權與安全性→區域網路」中的APP權限（實際選單依OS）；API3000與server簽發storage9000均須可達。不能把Mac health成功當手機storage可達。
3. 匯入小PNG，記錄prepare／upload／confirm／processing／ready結果，Node建立後重開驗讀；再測原失敗素材。截圖與安全stage可記錄，不保存signed URL query、Key、headers或私有內容。
4. 若仍失敗，依stage与安全exception class追查；不可直接簽為已修，也不能把此設定缺口當未確認截圖的唯一根因。模擬器失敗須另查，因本次保留其loopback設定。

工程命令與有限證據見MEDIA_LIVE_ACCEPTANCE。操作體驗、視覺／VoiceOver／動態島與遮擋由使用者簽核；效能最後。

## Android 原生素材載入工程證據補充（2026-10-05）

7001 在 emulator-5554／API37 正式服務唯讀 instrumentation 通過：JPEG／WebP／三張poster解碼、GIF全部影格、MP4／WebM MediaPlayer prepare／seek command／release，checksum與fixture cache清理、workspace前後一致；scope與重跑見 MEDIA_LIVE_ACCEPTANCE.md。無surface，不能把工程prepare通過當畫面／聲音／連續播放或seek落點通過；普通未opt-in case明確skip，不算live。

人工仍須以實際APP驗播放／暫停、拖曳進度與落點、音訊與靜音、切換工作區／離開畫布釋放、重新開啟載入、操作列大小與遮擋，並記錄平台／裝置／build／檔案來源與格式、分階段錯誤提示。最新iPhone generic失敗截圖尚無足夠環境證據；Mac health正常與Android成功不能代替iOS驗收。視覺／VoiceOver／動態島與遮擋由產品負責人簽核；效能最後。

## 1. 目的

本文件定義 BoarderLess 從功能完成到產品放行的品質檢驗流程，確保每次驗收都有一致的範圍、步驟、證據、責任人與結論。適用於 Desktop、Android、iOS、JS／Wasm，以及圖片／GIF／影片、多人協作、AI、物件庫和自訂形狀等功能。

## 2. 驗收責任

Anthropic 工程驗收補充（2026-10-05）：文字事件 adapter 已加入，不代表 HTTP／SSE framing／auth／UI 可用。正式接線後驗 message／block順序與index、event name/type不符、缺block stop／message stop、usage-only／ping、max_tokens／refusal／tool／thinking／fallback、UTF-8分片／取消／撤權。EOF或未支援block不得標成可採用，unknown metadata不得修改lifecycle或產生操作；錯誤不得洩露raw payload／Key。正式 proposal review／fresh權限與版本／commit／Undo仍必驗，視覺／VoiceOver／遮擋由使用者簽核，效能最後。

AI 串流工程驗收補充（2026-10-05，正式 UI／服務 Pending）：目前 OpenAI Chat-compatible 只有 data-event decoder／注入式 Flow adapter。7 synthetic cases 不代表 HTTP／真正 SSE framing／auth／Anthropic／Local AI 已連線。正式接線後驗 UTF-8／fragmented SSE、stop 後缺 DONE、length／refusal／tool call、scope change／撤權／取消、斷線與手動重試；取消不可顯示成功、錯誤不可洩露 key／URL／raw JSON、無自動 retry／cloud fallback。串流文字不能生成或執行 WorkspaceOperation；逐筆 proposal review／fresh version／ACL／journal／ack／Undo 未交付不得簽核 AI 可用。視覺／VoiceOver／遮擋由使用者簽核，效能最後。詳見 AI_PROVIDER_STREAMING.md。

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
5. 點擊「取消匯入」或從 Command Palette 執行取消，確認不新增 Node。完成確認尚未送出時，已驗證的 pending 上傳才可嘗試清理；完成確認已送出後，即使回應遺失、仍 pending 或使用者取消，都不得用無條件 DELETE 撤銷可能已接受的資產。依 5.24 查核保留／回查結果，不能把保留原檔誤判為取消失效。
6. 後端仍回傳 `uploadUrl: null` 時，確認顯示「此伺服器尚未提供素材上傳」、沒有新增 Node，且剛建立的 pending metadata 已 soft-delete。
7. 後端合約完整時，確認 ready 素材於畫布中央建立單一物件，具有正確 media kind、穩定 asset ID 與縮圖引用；Undo／Redo 操作不重新上傳，重開 Workspace 仍有相同資產引用。操作儲存失敗時須回到權威狀態並保留失敗提示。
   - 縮圖 metadata：分別驗收 complete 直接回 Ready＋thumbnailAssetId，以及 Pending→回查 Ready＋thumbnailAssetId。兩者均直接保留已驗證 Ready 回應的縮圖 ID，不應為取得相同 ID 再補一次 metadata 查詢；縮圖下載仍必須授權及校驗。Ready 未附縮圖時才補查，補查暫時失敗不刪原檔、不重新上傳，後續刷新可恢復。空白或指回原資產的 ID 不作為衍生縮圖；紀錄狀態、asset／thumbnail ID 與 Node 重開結果，不保存憑證或 signed URL。
   - 延後縮圖恢復：影片 Node 在沒有縮圖時插入，待後端生成縮圖後，重新開啟 Workspace 或於素材庫明確刷新 metadata。畫布應採用最新 Ready metadata 的縮圖 ID；Node／operation log／Undo history 不應因顯示縮圖而新增操作。最新 ID 與 Node 舊 ID 不同時，顯示層優先最新 ID；舊伺服器未附 metadata 引用時保留 Node 引用相容。wrong-workspace／wrong-asset／kind mismatch／非 Ready metadata 不得開啟預覽；沒有有效縮圖時 Video 不應下載影片原檔當圖片，播放入口另行驗收。metadata 刷新不是背景輪詢縮圖保證，請記錄刷新動作與回應狀態。
8. 另測空檔、錯誤類型、超過 200 MB、選取後變動的檔案與中途斷網；須顯示錯誤或取消且不新增正式 Node。
   - 下載清理取消：以可控 sink fixture 模擬截斷、checksum 驗證失敗、消費者取消，再令未完成 sink 的 abort 持續 suspend 或拋錯。coordinator 清理等待上限 5 秒，必須保留原始錯誤／CancellationException、不 emit Ready、不 commit 部分內容；已 commit 的校驗檔案不得被此 abort 刪除。此界線只約束可合作取消的 coroutine 清理，不能保證中止同步 native I/O、實際磁碟刪除成功或作為效能 SLA。產品端確認取消後可重新選取／重試，不把 timeout 視為已清除磁碟的證據；實機 filesystem 殘留與錯誤另行記錄。

圖標辨識、原生選擇器焦點、進度／取消按鈕可見性與中央選單遮擋交由產品負責人簽核。此批入口驗收不等同於圖片預覽、GIF／影片播放或完整 storage 整合驗收；尚未實作的項目維持 Pending。

跨層 contract 基線（2026-10-02）：`BackendAssetTransferGatewayTest.uploadConfirmNodeAndVerifiedDownloadComposeAcrossAllMediaKinds` 串接 Image／GIF／Video 各成功及同大小 checksum 損毀下載，共 6 組情境；檢查完整 HTTP 順序、Ready 縮圖不重查、workspace identity header 不送 storage、storage directive headers、Node 的 ID 引用及 checksum 完成前不 publish 本機 Ready。此 fixture 使用合成 4-byte 資料，不可簽核 codec、上傳後 worker 檢驗、原檔／縮圖像素、實際持久化或跨裝置重開。真實驗收先記錄使用者啟動的後端 URL／ready 狀態與可寫 QA scope，再用有效 PNG／GIF／MP4 重複上方流程；伺服器無法連線時保持 Pending，不使用現有使用者工作區試寫或自行啟動服務。

素材 projection round-trip 基線：對三種 Media Node 設定不同旋轉、尺寸、位置、鎖定、圖層、alt text、poster／無 poster 與 Group parent，建立後重開 Workspace，另一使用者端亦需保留相同值；不能以這批 APP JSON mapping tests 簽核後端落盤或即時協作。可控 malformed state 回傳空白 asset／thumbnail ID 時，應拒絕整份不合法 projection，顯示契約錯誤，不默默丟失 Node 或用上一個 bitmap 假裝成功；錯誤主要訊息不得回顯任意 properties 中的 URL／credential。復原合法 state 後依既有 refresh／retry 恢復，記錄修改前後 object ID／version、server seq、group parent 與姿態，遮擋及像素外觀由产品驗收者簽核。

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

Duration preparation 事件順序回歸：`node --test webApp/tests/*.test.mjs` 執行 production JS bridge 的事件模型。無 Duration WebM 探測尾端期間，loadeddata 即使已取得 finite duration 也不能交付 ready；reset 至 0 期間再次 loadeddata 仍需等待 seeked，普通 finite 影片不需額外 probe。取消 pending probe 回呼只完成一次並釋放 URL。模型不代替 native 解碼；仍執行下列 Chrome Headless pixel tests 與真實瀏覽器／GUI 驗收，保留之前初始白色 frame 失敗紀錄。

離線檢查：`./gradlew :shared:jsBrowserTest :shared:wasmJsBrowserTest :webApp:compileKotlinJs :webApp:compileKotlinWasmJs`，必要時設定 `CHROME_BIN`。8 個新增 tests 以 MediaRecorder 現場產生 32x32 無聲 VP8 WebM，在 Chrome Headless 驗證實際 pixel、native clock 與 Blob URL／取消清理；mock gateway 不能代替真實 storage／CORS，WebM fixture 不能證明 MP4、音軌或所有瀏覽器已支援。依 [HTMLMediaElement play Promise 規範](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/play)，不能在播放被拒絕時仍顯示播放成功；依 [Blob URL 釋放規範](https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static) 回收每個已建立的短期 URL。

回歸 fixture 時序補充（2026-10-02）：使用 recorder onstart＋手動 requestFrame 建立白色首幀，再依請求次數切换藍色，不以編譯／事件迴圈經過時間決定首幀。初始白色、seek 後不同像素與先前 Bitmap 不變的斷言保留，失敗訊息需區分首幀／seek／immutable frame／Blob revoke 等階段。JS／Wasm 全套連續 3 輪通過僅為功能穩定性觀察，不是效能測試，也不能刪除前次失敗紀錄或認定 production 播放器的根因已解決；若再次失敗，保留完整 assertion 階段、fixture／瀏覽器版本及當輪命令再追查，不以無限重跑當驗收。

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

### 5.23 工作區素材庫／已儲存素材重新插入

內建物件庫收藏／最近使用驗收（2026-10-04，人工 QA Pending）：

- 在「內建元件」切換全部／收藏／最近使用，空列表不崩潰；搜尋條件只過濾目前分類，清空搜尋恢復該分類。切去工作區素材／GIPHY 後返回，不能把素材或供應商結果混進內建收藏。
- 收藏／取消收藏只修改本機偏好，不新增 Canvas operation／history；同一裝置重開仍保留。偏好為裝置層級，不承諾帳號隔離或跨裝置同步，換帳號／工作區仍是此裝置的內建元件 ID 收藏，不帶入私密畫布文字或物件 ID。
- 成功插入中央、成功 drop 到可用畫布後，元件出現在最近使用最前；重複使用只保留一筆，最多 20 項。這表示本機 optimistic 插入已成立，不代表伺服器 ack；其後伺服器拒絕／Undo 不自動撤銷此使用記錄。
- 拖曳取消／drop 在工具列或面板／Viewer／撤權／pending recovery 等拒絕插入，不新增最近記錄、不顯示成功插入、不因失敗關閉手機物件庫。真正操作競爭與 UI 行為需實機測試，偏好單元測試不能代簽。
- 切換語言／重開／元件目錄變更後，按穩定 ID 找最新名稱；已移除或未知 ID 不顯示成虛構元件，最近順序不得被搜尋打亂。收藏最多 128 項，非法／損壞／超大偏好有界讀取；保存失敗提示不撤銷已插入物件。
- 檢查手機換行、滚動、收藏按鈕可及性、VoiceOver 名称與當前狀態、搜尋框貼上／Delete 不修改背景物件、中央 modal 與底部 inspector 共存。視覺／遮擋由產品負責人簽核；收藏功能不當成完整物件庫／鋼筆交付。

新增自有圖形／無外框文字驗收（2026-10-04，人工 QA Pending）：

- 2026-10-05 追加倒三角形、直角三角形、人字箭頭、雙向箭頭、星形與手動輸入，共 25 種固定形狀。從物件庫、Command Palette 搜尋與 Inspector 切換；箭頭分類須含人字／雙向箭頭，流程分類須含手動輸入。它們仍是文字 Node 的自有 outline，不是 Relation 或任意向量路徑。
- 直角三角形的方框中心在斜邊上，連線起點改用內部重心。以寬扁／高窄尺寸與 0／37／90／180 度旋轉驗連線沿可見輪廓收尾、不出現零長度或跳到透明角；其他形狀仍採原中心。星形／雙向箭頭／人字箭頭驗凹角透明區不可誤選，明確建立 Relation 後再驗移動與 Undo／Redo。
- 每個新形狀編輯長文字／emoji、換色、複製貼上、群組、保存本機方案與重開；另以同版本兩端驗實際服務保存／同步。shape token DTO／projection 自動 round-trip 不等於真實服務已驗收。星形文字留白、直角三角形文字裁切、手機觸控、preview／safe-area／VoiceOver 由使用者簽核，仍 Pending。
- 從物件庫、Command Palette 與 Inspector 選擇無外框文字、三角形、五邊形、八邊形、梯形、加號、四向箭頭。由原 9 種擴充至 19 種；本輪是固定自有形狀，不是鋼筆或任意 Bezier path。不得使用他人專有圖形資產。
- 無外框文字未選取時無卡片填色／邊框／陰影；選取時仍有輪廓與 resize／rotate／connector handles，矩形文字範圍可命中，文字編輯與手機 bottom sheet 不自動搶焦點。預設 paper 使用主題字色，其餘 colorToken 作字色而非填色，自訂 RGB 顏色要保留；檢查明暗背景對比與游標，普通卡片自訂填色的對比字色不受影響。方案縮圖以文字線段標記，不冒充實際文字內容或卡片填色。
- 每種形狀變更寬高、旋轉、拖曳；三角／多邊形的透明角、加號與箭頭凹角不能誤選或作 connector target。箭頭線條的輪廓不等於 Relation，仍須明確建立關聯。
- 連線端點應跟隨形狀的可見邊界，不接到透明角；凹形輪廓使用由中心射線的第一個交點，不能穿出透明區後又接到更遠的箭頭頭部。改形狀、移動、旋轉與 Undo 後重新核對。
- 建群組／複製／貼上／再製／Quick Scheme 保存與重開，保留 shape token、文字、旋轉、parent 與內部 relations；正式後端保存／冷啟／雙裝置需另外驗收，序列化 fixture 不是服務證據。
- 相容性：新增 token 不要求舊版自動識別。舊 APP 讀到未知形狀應明確要求更新／停止編輯，不得靜默改成方形再覆寫；多人共享前核對兩端版本均支援。同樣保留未知／損壞 token 拒絕與原始證據。
- 手機控件命中、文字是否被形狀裁切、長文字／emoji、方案縮圖、safe-area／遮擋／VoiceOver 由使用者签核。更多幾何／標註／容器／圖形與鋼筆仍待辦，效能壓測最後。

入口：「物件庫 → 工作區素材」，與內建元件共用原面板，不另外強制開啟 Node 屬性面板。這是既有 Workspace asset metadata 的瀏覽／重新插入入口，不是 GIF Browser，也不代表 BAI-003 真實 object storage 已驗收。

1. 準備同工作區的 ready Image／GIF／Video、pending／rejected／missing、未知 MIME 與超過 200 MiB 素材；另外工作區的素材不得列入。搜尋既有 MediaNode 的替代文字、asset ID、MIME／status；Image／GIF／Video 篩選、清空查詢及每頁 8 筆翻頁不得遺漏或混入其他工作區。素材本身尚無正式檔名欄位，無既有 Node 替代文字時使用 asset ID，不宣稱可以搜尋遺失的原始檔名。
2. 只有 ready、支援 MIME、大小不超過限制且目前可編輯時啟用插入。Viewer／Commenter 可瀏覽，但不能插入。點插入後重新 GET asset metadata；若素材已 pending／rejected／missing、回傳另一 asset／工作區、目前已知 session 已撤銷編輯權限、帳號／client／工作區改變或連線失敗，不能新增 Node。Metadata GET 可對 Viewer 放行，不代表 mutation 權限已確認：若權限在伺服器已撤銷、APP 尚未 catch up，正式 operation 應拒絕且撤回 optimistic Node，不得留下正式物件。記錄 API／畫布結果，不能只以按鈕的 disabled 外觀簽核。
3. 正常插入使用原 asset ID、新 Node ID、目前畫布中心、與匯入相同尺寸策略；保留 metadata 的 thumbnail 引用，加入現有 CreateObjects／Undo／operation pipeline，不再次 prepare／upload／complete／delete 原始 asset。插入前後比較 asset 數量與 storage 物件數，測同素材重複插入、Undo／Redo；後端既有 soft-delete restore 問題另依 BAI-001 記錄，不把本機 Undo 成功代簽後端 Restore。
4. 測「上傳已 ready 但 Node 插入失敗」：從素材庫重新整理後找到並插入，不必重新上傳。插入失敗訊息應提示物件庫入口；原始已 ready 資產不得被刪除。後端關閉期間此真實情境保持 Pending，不為驗收自行啟動後端。
5. Metadata 保留 thumbnailAssetId，不保存 storageKey／signed URL 到 Node。預覽使用既有授權／size／checksum／decode gate：影片只讀不同 ID 的 poster，沒有 poster 不下載影片原檔；超過靜態 preview 限制的原圖亦不為卡片下載。GIF 僅靜態預覽、不自動播放；縮圖不可用不阻止原始 ready 資產重新插入。縮圖的 ACL、CORS、pixel、mime／checksum 仍需真實 storage 驗收。
6. 測 metadata 載入失敗／重新整理／慢回應／取消／切換帳號與工作區：顯示 loading 或 unavailable，而非把失敗假報成空素材庫；舊請求不得覆寫新 scope。插入後面板不被強制關閉，Node 選取不強制拉開手機屬性 sheet。搜尋鍵盤、翻頁、關閉、其他同時開啟面板、動態島／safe area／遮擋／VoiceOver 由產品驗收者判斷並附截圖與備註。
7. 本輪 7 個素材庫 model／gate tests 與 2 個 metadata thumbnail contract tests 通過；不涵蓋 Compose 觸控或真實 server mutation。將 SOP 5.23 寫入 Workbench 素材、操作、遮擋題備註，附 asset／角色／查詢／頁碼／插入前後截圖；視覺／端到端未驗維持 Pending，效能依第 7 節最後執行。

### 5.24 上傳完成確認不確定／取消與保留

素材拒絕原因回歸：在素材庫逐一確認 upload_missing、byte_size_mismatch、checksum_mismatch、media_type_mismatch、unsupported_format、undecodable_media、dimensions_exceeded、invalid_duration、duration_exceeded 的中文／英文提示。未知／空白碼只顯示一般驗證失敗；null 維持舊版「上傳遭拒」狀態。故意回傳含 URL／credential／HTML 的未知原因，確認 UI 不原樣顯示或保存該文字到 Node。Ready／Pending／Missing 不顯示拒絕原因，Rejected 仍不可插入、不自動 DELETE 或重傳。後端限額與 APP 預覽限額可能不同，提示不得聲稱所有裝置可解碼。新增 2 個 contract／model tests 不代簽真實 worker 與產品畫面。

素材庫取消回歸補充（2026-10-02）：讓 preview loader／list／GET metadata 在 coroutine 取消後才回傳結果或一般錯誤，確認不發布舊 bitmap、不顯示舊請求的 unavailable／插入錯誤，也不插入 Node。涵蓋重新整理、切換工作區／身分、離開面板與取消插入。共用 `awaitActiveMediaRead` 有取消前／返回後／錯誤後 gate；5 個策略測試不等於 Compose 畫面或真實 native loader 的人工驗收。實機畫面與遮擋依產品驗收者簽核。

素材庫預覽重試（2026-10-02）：先載入 ready metadata，再中斷 binary 下載或令 checksum 校驗失敗。卡片應顯示載入提示，失敗後提供帶無障礙名称的「無法載入預覽・點選重試」，不靜默消失；恢復網路並明確按一次，僅重載該卡片的授權預覽。等待時不能連續發起重試，不重新 PUT／complete／建立 Node，不自動播放 GIF／影片；素材插入是否可用仍依角色與 Ready gate，不能因預覽失敗改成禁止插入。缺少 Video poster、pending／rejected／missing、沒有 loader 或超過 library preview 上限時不提供無效重試。翻頁、搜尋重排序、換工作區與離開面板時，狀態不可串到其他 asset；native loader 取消後晚到回應不得覆寫新畫面。model retry gate 測試不代替 UI event／HTTP 次數／焦點／手機遮擋人工驗收，記錄假素材 ID、下載次數與重試前後截圖，不記錄 signed URL。

取消本機匯入不等於撤銷伺服器已接受的完成確認。APP 只在完成確認尚未請求、prepared metadata 已通過來源／工作區／pending gate 時嘗試清理；之後失敗都保留資產供正式狀態回查，不自行重送 complete 或再上傳。Pending-only 原子 abandon、完成確認冪等與伺服器過期清理仍需 BAI-003 正式合約；不能以 client gate 假稱已具 server-side concurrency guarantee。

1. 在 prepare、上傳途中、Confirming 顯示但 complete 尚未送出時取消：不建立 Node，已驗證的本次 pending 上傳可嘗試 DELETE；不得刪除原始裝置檔案。故意回傳 ready prepare、另一工作區或不符 checksum／size／MIME 的 metadata：不得因 client validation 失敗就刪除該未知資產。
2. PUT 成功後讓 complete 回 503、斷線、回應遺失或取消：記錄完整 request 順序、asset ID 與伺服器狀態。APP 不得送 DELETE、不宣稱 Ready、不新增 Node；提示到「物件庫 → 工作區素材」查狀態。實際 server 已 accepted 但 client 未收到回應的場景，需由可控測試環境製造，mock 503 不等於已驗證 durable commit。
3. complete 回 pending 後，在 polling 取消或逾時；即使後續 worker 轉 ready，也不得被 APP 的錯誤清理刪掉。Complete 回另一 asset ID／不符 metadata 時不可插入；原始準備資產保留，另紀錄合約錯誤。Rejected／Missing 不可插入，metadata 保留供原因與清理政策查核，不把保留記錄當成可用素材。
4. 已 ready 後，thumbnail lookup 失敗／取消或 Node 無法插入：原檔仍保留。素材庫重新整理若正式狀態已 ready，可復用原 asset ID 插入；仍 pending 時不可把完成確認失敗當作上傳成功，也不可盲目再次 complete／重上傳。APP 尚無跨重啟的待確認上傳 journal／自動冪等重試，後端過期清理未交付期間此恢復門檻維持 Pending。
5. 清理 HTTP 逾時採 5 秒 coroutine deadline、NonCancellable best-effort；到期保留原始匯入錯誤／Cancellation，不讓清理錯誤覆蓋，並恢復操作。此 deadline 針對可合作取消 transport，不是強制終止卡死的 native IO；需要裝置與真實網路驗證。故意延遲 prepare failure 的 cleanup，檢查取消、關閉、再次匯入與 picker snapshot 清理，不把 mock virtual-clock 測試當成系統 timeout 驗收。
6. Owner 已取消時，非合作 transport 晚到回應不得交付 Ready。完成確認／processing／thumbnail 回應期間切換帳號／client／工作區或目前已知角色撤銷後，不得新增 Node；舊 scope 的 asset ID／錯誤訊息不得覆寫新 scope 狀態，刷新只針對原 scope 尚有效的 session。未 catch up 的伺服器角色變更仍依 5.23 檢查正式 operation 拒絕／optimistic rollback。恢復提示是本機 UI 狀態，不把 signed URL／header／picker handle 保存到 Workspace、剪貼簿或方案簿。
7. 本輪新增 9 個 coordinator 策略 tests 與 4 個 HTTP MockEngine tests，驗證上述 cleanup／保留決策、真實 coroutine cancellation gate、原始錯誤保留及 signed PUT body／503 request 順序；不是正式 object storage／worker／UI 證據。Workbench 素材／錯誤題附 SOP 5.24、request timeline、asset status、取消時點與截圖；後端關閉期間不得自行啟動，端到端／手機遮擋仍由產品驗收者簽核。

### 5.25 素材下載取消／縮圖 metadata 契約回歸（2026-10-02）

- 協作 replay 自動證據不得代簽媒體同步：新 reducer fixture 包含 Image／GIF／Video Node、群組、文字與關聯，只證明正式資料引用與 transform／版本可套用；不證明兩端授權下載、縮圖、播放、WS transport 或實際 storage。真正協作驗收仍須兩台裝置／兩個已授權使用者確認物件內容及播放，由產品簽核。

- metadata 晚到回查（2026-10-03）：使用測試工作區，讓 Ready 影片先無 thumbnailAssetId，於首次 list 後、27 秒內發布合法獨立 poster，保持畫布 server sequence 不變。預览應更新，Node／history 不變且不自動播放；只出現 list GET，不新增 prepare／PUT／complete。首次 list 返回後 loading 提示應消失。永遠不發布縮圖的 legacy fixture，每個窗口最多 10 次 list（含首次）；窗口結束後仍可手動重新整理。切換工作區、關閉 APP 或明確 recovery GET 接管時，舊列表不應覆寫新 metadata。記錄請求次數／API path／sequence／Node 前後資料，不記 signed URL；這是功能驗收，不是效能測試。

- 在授權返回、第一個 chunk、sink write 返回、最後下載返回、Verifying callback 及 commit 返回時取消下載。已取消的 consumer 不得收到 Ready 或 local reference；取消發生在 commit 前須 abort 暫存資料。commit 已完成才取消時，保留已驗證的 cache，不把它交給已取消 consumer；cache 的清理依原本平台生命週期，不宣稱 abort 能復原已完成的原子發布。
- transport 用 NonCancellable 發送晚到 chunk 時，仍須檢查原 consumer 的 Job，不可用 callback 自己的 Job 繞過取消檢查；取消後第一個 chunk 不得寫入 sink。這項測試驗證 callback 邊界，不代表能強制終止卡死 native IO。
- polling／縮圖原素材 GET 回另一 ID 或工作區時，不發布狀態或取得其 thumbnailAssetId；縮圖來源非 ready 時不接受引用。自我 thumbnailAssetId 視為沒有縮圖。縮圖本身的授權與內容驗證仍走既有下載政策，不因原素材可讀而跳過。
- 本輪新增 2 個下載策略 tests（含 7 種取消邊界）與 3 個 HTTP metadata tests。真實 storage、平台 cache 清理與畫面仍需人工／端到端驗收；後端關閉期間維持 Pending。

### 5.26 跨重開的本機匯入待確認清單

Desktop 儲存限制回歸（2026-10-02）：使用實際 36 字元 user／Workspace UUID，不只用 `u`／`w` 測試。舊 v1 提醒 key 長 96 字元，超過 Java Preferences 的 80 字元限制，會在 complete 前失敗並觸發 pending 清理；本次已由真實 Preferences fixture 重現 `Key too long`。v2 key 使用完整 scope 的 SHA-256，固定 70 字元，不截斷 ID。其他平台既存 v1 記錄須仍可讀取，成功寫入 v2 後才移除舊 key；損壞資料不可覆寫。重新啟動更新後 Desktop 再驗證選檔 → 上傳 → 完成 → Node，不能以短 ID 的 memory fake 通過代簽。

1. 在 complete 請求前確認本機已保存 asset ID。模擬完成回應遺失或取消，正常關閉／重開 APP，到物件庫 → 工作區素材查看提醒；不重新選檔、不自動 PUT／complete／DELETE。清單只保存 ID，不保存 binary、signed URL、headers、picker URI、檔案路徑或 checksum。
2. 按「確認」設定 ID 查詢並直接 GET `/assets/{id}`，不依賴素材已出現在列表。ready 可沿原素材庫流程重新插入，pending／rejected／missing 仍不可插入。404／403／無法讀取時保留提醒，不推論為伺服器已刪除。清單不是正式 server status，也不代表匯入一定失敗。GET 成功不賦予編輯權；viewer 可讀但不能插入。
3. 切換使用者／工作區，確認清單隔離；相同使用者在同裝置重開可見，但跨裝置不自動同步。移除提醒只刪本機索引，不呼叫 backend DELETE。成功匯入也可能保留提醒，須人工確認後移除，不能用 optimistic Node 存在推論 durable server commit。
4. 每 scope 最多 128 筆、每頁最多 5 筆；滿載不默默丟棄最舊記錄。準備完成但本機寫入失敗時不得請求 complete，只依原 pending 清理規則處理。損壞資料應顯示本機提醒不可用，不用新匯入覆寫舊資料；平台 settings crash／斷電 durability 另行人工確認。
5. 新增 5 個 store tests 與 2 個 coordinator 順序／寫入失敗 tests；新 store instance 能讀回不等於已驗證實機程序重啟、突然斷電或伺服器 durable commit。QA Workbench 記錄帳號／工作區、取消時點、重開前後提醒與確認動作截圖。後端關閉期間端到端維持 Pending。
6. 直接回查回應期間切換 user／client／工作區，不發布到新 session；回另一 ID／工作區時拒絕。測試先延遲列表 GET，再完成 asset GET，最後才釋放舊列表回應，確認新 asset metadata 不被舊列表抹除或覆寫；404／取消不建立 Node、不刪提醒。新增 3 個 scope／status model tests 不涵蓋 Compose HTTP 回應順序，該競爭需以可控整合 fixture 及實機查核。

### 5.27 GIF／影片圖示控制與素材關聯標註

本項視覺、遮擋及觸控可用性由產品負責人實機判斷；自動測試不代簽。2026-10-02 使用者已回報 Desktop 素材上傳恢復正常，但不視為所有格式、平台及儲存持久性均已驗收。

1. 選取影片並播放，控制列應只有一列圖示，不顯示按鈕文字或另起一列的秒數。驗證播放／暫停、重試、倒退／前進 10 秒及靜音／取消靜音；未選取時不顯示控制列。按鈕保留無障礙名稱與鍵盤焦點，載入期間不可重複觸發播放。
   GIF 選取時只顯示右下角播放／暫停圖示，不顯示按鈕文字；點擊後圖示及無障礙名稱隨狀態更新。驗證暫停、播放結束後重播、未選取時隱藏，以及「減少動態效果」時不可啟動動畫。
2. 依產品要求 GIF／影片採 28dp 緊湊按鈕、14dp 圖示，影片按鈕間距 6dp，不使用 weight 拉伸填滿整列；控制背景依內容寬度收縮，窄物件時可橫向捲動保持單列。在正常及縮小／旋轉影片、Desktop 窄視窗、手機直向／橫向與畫布縮放下，檢查控制列遮擋、操作準確性、捲動與物件拖曳是否衝突。此模式已不保證 44dp 觸控範圍，需特別由產品負責人判斷手機可用性；不以編譯或自動測試通過代簽。
3. 分別選取圖片、GIF、影片，從連線把手拖向文字卡片及另一素材；也從文字卡片拖向素材。手機可點連線把手後選目標，或多選兩個物件使用關聯／支持／衝突操作。群組框不是本項連線目標；不得自連，拖至空白不建立關聯。
4. 選取線條，設定正向／反向／雙向／無箭頭及標註文字。移動、旋轉或縮放素材，箭頭需跟隨物件邊界，標註跟隨線路；驗證搜尋關聯能定位素材端點。
5. 驗證 Undo／Redo、刪除端點、重新開啟畫布，以及一起複製／貼上兩端物件後保留關聯。快速方案預覽及重新插入須保留素材關聯；只複製其中一端不可產生指向不存在物件的線。確認後端仍使用原有 relation 的 object ID，不新增素材 URL 或 binary 至關聯資料。
   - 素材專用及混合快速方案：摘要物件數須包含 Image／GIF／Video，顯示實際 selection 版本；重新插入保留素材引用與關聯。以隔離本機 fixture 驗證缺少 JSON version 的 legacy 方案仍可讀取，未支援版本、非 legacy metadata／JSON 版本矛盾、非法 transform／關聯不顯示預覽且停用插入；不得刪除或改寫原方案。這是本機版本相容驗收，不是跨裝置 CRUD／revision／ETag 驗收。
   - 素材方案來源：在 A 畫布儲存、改名、重啟後於 A 插入須沿用可用素材；開啟 B 不得直接插入 A 的素材 ID，應顯示未支援移轉說明且不改動畫布／history。legacy 缺來源不自動填入 B，僅在目前畫布 metadata 確認 ready／ID／MIME／scope 相符時可用。metadata 未取得時可重新整理後再試；純文字方案不受此素材 gate 限制。此不代替 server ACL 或未交付的 materialization 驗收。
   - 一般剪貼簿與 duplicate：複製包含文字、群組、Image／GIF／Video 及關聯的選取；同畫布 ready metadata 完整時可整組貼上／複製，切換畫布或任一 metadata 不可用時應整組拒絕且不新增 history／待提交 operation。metadata loading／失敗時剪下也不得先刪原件，顯示可重新整理說明；copy 仍保留完整內容，不以移除素材讓貼上偽成功。自動模型 tests 不代替系統剪貼簿／UI history／server ACL 驗收。
   - 明確縮圖引用（2026-10-03）：原檔 Ready 但 Node 攜帶的縮圖 missing／pending／rejected／foreign workspace／非圖像時，一樣整組拒絕貼上、duplicate、方案插入與剪下；原物件、關聯與 history 不變。重新整理確認原檔與縮圖都 Ready 後重試成功；沒有明確縮圖的舊 Node 不應被要求有縮圖才可複製。使用測試 fixture／QA 工作區，不破壞正式資產；記錄 metadata 與操作結果，不記 signed URL。
6. QA 問卷附上播放前後、關聯建立與標註的截圖及實機操作結論；VoiceOver／其他螢幕閱讀器辨識圖示操作另行人工驗收。效能測試仍排最後。

### 5.28 Desktop 大圖的 viewport 分批預覽

1. 從 macOS 相簿匯出並匯入超過 1,200 萬像素的 JPEG／PNG。Node 應先出現最長邊不超過 512px 的低解析預覽，再依可見區域逐塊補清晰細節；原始素材仍完整上傳及保存，未為預覽修改原檔。原圖先經完整下載、大小／checksum 驗證後才解碼，並不是邊下載邊顯示未驗證的內容。
2. 放大、縮小、快速平移、旋轉 Node、切換直橫比例及部分移出 viewport；確認留白位置正確、方塊邊界無縫隙、舊區域不覆蓋新狀態、移出畫布後停止解碼。120ms 防抖期間保留已顯示的粗略預覽；不以 unit tests 代簽實機流暢度與視覺。
3. 每塊最多解碼 512×512 像素，可見網格由中央優先，每次最多 16 塊，超出細節預算的區域保留粗略預覽。ImageIO 的 sourceRegion／subsampling 限制輸出像素，但特定格式仍可能掃描壓縮資料，不能宣稱完全只讀取可見區域的壓縮 bytes。大量物件與高 DPI 效能／記憶體量測仍排最後。
4. 同一圖片平移／縮放可重用已驗證的本機原圖；每次重新授權，按 user／Workspace／完整 asset metadata 隔離，版本變化不可沿用舊檔。權限被撤銷、wrong ID／Workspace、checksum 錯誤或取消不得發布新像素。切帳號／工作區時清除本機快取；最多 4 檔、256MiB，原始檔上限仍為 200MiB。
5. JPEG／PNG 來源 header 最多 65,535px 單邊、268,435,456 總像素，這是來源安全上限，不代表一次配置該數量的像素；粗略圖及每塊輸出仍受原有安全檢查。WebP 沒有 bundled ImageIO region reader，維持舊 Skia 路徑及原限制；GIF 動畫、影片、Android／iOS／Web 暫不套用此 Desktop 區域解碼器，不能記為全面解除所有平台的預覽限制。
6. 紀錄原始格式／尺寸、viewport 操作與畫面截圖。測試 fixtures 通過不能證明使用者原始相簿檔的 EXIF 方向、色彩描述檔、HDR 或特殊 JPEG 編碼均相容；需以實際原圖另行簽核。正常 JVM 結束會刪除此 loader 的暫存檔；強制終止／斷電殘留另行驗收，不自行刪除使用者相簿或原始匯出檔案。

### 5.29 旋轉物件後的拖曳方向

1. 以圖片、GIF、影片、文字卡片及群組分别測試 0°、45°、90°、180°、270°；向畫布的右、下及斜向拖曳，物件必須跟隨滑鼠／手指的畫布方向，不沿旋轉後的局部座標軸偏移，不將角度重置為零。
2. 重複在 25%、100%、200%、400% 畫布縮放下測試，確認距離換算一致；文字卡片選取縮放動畫中也不因 1.012 倍視覺縮放而漂移。預設吸附仍會有預期的對齊修正，Desktop 可按 Option／Alt 暫時略過文字卡片吸附，或關閉吸附以單獨驗證拖曳方向。
3. 旋轉後連續拖曳多次、拖曳多選含素材／群組的物件、取消拖曳、Undo／Redo 及重新開啟畫布，確認提交的世界座標與畫面一致。鎖定／唯讀物件仍不能拖動；resize／rotate／連線把手不應受到物件本體拖曳換算影響。
4. 附上旋轉角度、viewport zoom、吸附設定及操作錄影；共用座標 tests 只能驗證數學換算，不能代簽 Compose 實機 pointer 事件、觸控操作與動態圖層座標轉換。

### 5.30 GIPHY 瀏覽原型與請求控制

目前有縮圖網格／跨平台靜態及明確播放的 GIF 預覽、官方 attribution PNG，非完整 GIF 插入。品牌位置／清晰度、動畫結果網格、MP4／WebP adapter、Canvas external-media 合約尚未驗收或交付；不得因此勾選整體整合通過。規格及官方來源見 [GIPHY_API_INTEGRATION_SPEC.md](GIPHY_API_INTEGRATION_SPEC.md)。

1. 在物件庫開啟 GIPHY，使用該平台測試 key；輸入文字不應發送請求，按搜尋才送出；保留原始文字與結果順序。已顯示的同一搜尋不能因重排版再請求，下一頁必須明確操作。
   - mac 輸入焦點回歸：先選取背景物件，再點金鑰輸入框，以假 key 測試 ⌘V 貼上、⌘A 全選、Backspace／Delete、方向鍵及 ⌘Z；文字應正常編輯，背景物件不得被貼上、刪除、移動或 Undo。搜尋框及物件庫其他搜尋框同樣測試；關閉面板或點回畫布後，畫布快捷鍵應恢復。不得將真實 key 放進截圖／錄影／QA 匯出。
   - 補測原生選單：文字編輯時「編輯」選單的畫布複製／貼上／全選／Undo 等命令不得接管輸入框；⌘K 不開啟畫布命令面板。金鑰框與搜尋框互相切換、GIPHY 分頁切換及整個物件庫關閉再開啟，各重複一次。Backspace 刪游標前文字、fn+Delete 刪游標後文字，遮罩金鑰的選取範圍與貼上後長度需正確；以假字串操作，背景物件及 history 不變。自動測試僅確認停用命令不再註冊 native shortcut，不代替 macOS 實際文字編輯驗收。
2. 取消或改查詢後，舊回應不得覆蓋新結果；取消後可重新搜尋。同 process／key 的預算跨面板實例共享，但不代表跨裝置或重開後的全域限額。429 應停止請求且不自動重試；限額用 MockEngine 驗證，不以耗盡真實 beta 配額驗收。
   - 鍵盤事件優先序回歸（2026-10-03）：重啟更新後的 APP，以假字串 `test-n-f-123` 在金鑰與搜尋框測試貼上、逐字刪除、選取後刪除、方向鍵、Undo；`n`／`f` 應輸入文字，不新增 Node 或縮放畫布。快速切換兩個欄位後立即貼上也必須成功。記錄 APP 版本、平台、欄位、按鍵、預期及實際結果，證據不得含真實 key。最後點回畫布，確認畫布快捷鍵恢復。
3. Desktop／Android／iOS／Web JS、Wasm 選取結果載入 still；同一開啟預覽的重排版不應重新下載，關閉／改選後釋放，不宣稱跨畫面快取命中。直接使用原 GIPHY URL／query，不經我們後端、不另存檔；跨畫面可重用快取必須先取得 GIPHY 書面批准並補驗 revalidation。
   - HTTP MockEngine 驗 encoded 2MiB 上限（含無 Content-Length）、過大宣告、HTML／空回應、非 provider URL、302、取消與不夾帶 Workspace 憑證。native／Web decoder fixture 不代替真正 CDN、CORS、TLS、手機畫面或跨 URL 權限證據；Android Device Test APK 必須另於裝置執行，不以 compile 代簽。真實 key、圖片細節、觸控／遮擋由產品負責人簽核；靜態 still 不代表動畫／插入通過。
4. API 證據只記錄 host、path、狀態碼及請求次數，刪除 key 與完整敏感 query。MockEngine／編譯證據不代替真實 API、視覺與遮擋簽核；視覺由產品負責人判斷，效能測試仍排最後。
5. 「更新預覽」只查選取 ID，不重新搜尋整頁；成功後使用最新 still URL，保留 query。缺失／失敗不得再顯示舊預覽；更新中改選、關閉或換 key，晚到回應不得覆蓋新選取。100 ID 上限、缺失 ID、陌生／重複 ID 及共用 429 gate 使用 MockEngine 驗證，不耗盡真實 key 額度。
6. 選取後初始僅 still，明確點播放才下載小 GIF；播放／暫停圖示不使用大文字控制列。暫停後不前進，播放保留正確 delay／loop；播放完畢可明確重播。開啟 Reduce Motion 應停止且不能啟動；關閉／改選／更新、滾動預覽出面板、APP 進背景／桌面最小化後不再發布動畫幀，返回不能自動續播。取消下載／decode 後返回不得有舊幀覆蓋或資源遺漏；錯誤／超過 2MiB 保留可重試提示，不偷偷載入 original 或重新上傳。
   - common tests 驗 rendition gate／decoder 所有權轉移與取消 cleanup；iOS／JS／Wasm MockEngine → native GIF decoder 的真實兩幀 pixel fixture 驗整合，既有 Desktop／Android GIF decoder tests 驗 codec 本體。Compose 可見範圍、ON_STOP、Reduce Motion、真正 CDN 和實機觸控仍由產品負責人驗收，不以 fixture 結果代簽；Android Device Test APK 建置不等於已執行。
7. 結果網格：確認兩欄縮圖依供應商順序排列、長名稱可截斷但無障礙保留完整描述、選取框與按 Enter／Space 可操作。媒體不存在／載入失敗仍保留原卡片與順序；不混入 Workspace 素材。滾動內層網格與外層物件庫時皆沒有背景畫布移動；最多 3 個應用程式 in-flight 預覽載入，離開 viewport／關閉面板取消，queued cancellation 不開始 download，錯誤後 permit 可供下一張使用。此為功能邊界測試，不代表效能測試或記憶體 benchmark 已執行；版面、遮擋、鍵盤／VoiceOver 由產品負責人驗收。
8. Attribution：使用 [官方原始圖來源紀錄](GIPHY_BRAND_ASSET_PROVENANCE.md) 的 200 × 42 PNG；程式不得換色、裁切或替換為自繪 logo。圖應固定在 GIPHY 面板捲動區上方，滾動網格／預覽時仍可見，不遮住輸入框或操作。驗收 light／dark、不同 DPI、手機直橫向及 VoiceOver 描述；native 斷網仍從封裝顯示，Web 從同源 bundle 顯示，不為 logo 發 GIPHY API／CDN 請求（不宣稱 Web 離線冷啟動）。Resource hash／尺寸 tests 不代替產品對標示顯著性與清晰度的判斷，也不代表 GIPHY production 批准。Web Karma 路由用於測試資源服務，正式 Web bundle 資源路徑仍需建置／部署檢查。

## 6. 多人即時協作專項

REST 撤權 UI 回歸（2026-10-03）：B 已開啟畫布，A 撤除 B membership，讓下一次 scope refresh 回 403。B 應進入連線／權限失效狀態，不再背景 HTTP catch-up，不允許新增、移動、編輯、刪除或匯入。另模擬 submit 503＋refresh 403、submit 403＋refresh 503；兩種 Failed reconciliation 都應停止舊權限寫入，未確認 wire journal 保留，不自動 resend／刪除。權限只降為 viewer／commenter 且 refresh 成功時，應採用新 role、可依授權閱讀但不得寫入。403 policy 的自動 tests 不代替真實 membership／UI／observer 取消驗收；恢復需明確重新連線／驗證，不根據舊 cache 自動恢復權限。

去重 scope：以可控 fixture 讓兩位 actor 的不同交易使用相同 operation ID，應正常 replay；同 actor 重用 ID（包含不同 client）不得作新事件套用，應權威 resync，未完整驗證前不改 checkpoint。只在 QA 資料或 mock 驗證，不刻意污染正式 operation log。

跨頁 catch-up 補充（2026-10-03，取代下方 hasMore 直接 fallback 的舊階段）：以 fixture 將同一完整 transaction 切成兩頁，第一頁回 head=2、實際僅 seq=1；APP 下一頁必須查 afterSeq=1，不可查 2。第二頁完成後一次發布兩筆與完整版本，不得先顯示第一筆或前移 checkpoint。第二頁 403／取消／缺段／head 倒退不得發布部分畫布；超过 8 頁／8,000 records 應停止追讀並走正式 state。正常兩頁可不讀 state，角色／identity 與原 optimistic gate 維持；HTTP mock／自動 tests 不代簽真實兩端、WS、畫面與效能驗收。

REST replay 接線驗收（2026-10-03）：後端啟動後，以兩位授權使用者操作同畫布，讓 B 在沒有 optimistic pending／文字編輯／拖曳時接收 A 的完整交易。B 應由 afterSeq 讀取完整 records，七種現有 operation 的物件／關聯內容及版本一致；小型完整頁不需另 GET state。metadata-only 更新仍應刷新 title／role，且不能改 clientSequence 或認領本機 pending ack。使用 mock／可控 QA fixture 模擬 hasMore、缺段、未知 kind、摘要 response 及 head 落後 metadata：應改讀正式 state，不先發布部分交易或只保存 head。403 必須失敗，不把 cache／state fallback 當授權成功。切換 user／client／workspace、optimistic 草稿存在或取消中的舊 request，皆不能以不相符 raw cache 重播。記錄 before／after seq、version、HTTP path 與 state GET 次數，不記 Node 敏感內容或憑證。此項不代簽 WS／Presence／cursor／selection；視覺由產品簽核，效能仍最後。

REST 提交確認回歸：以隔離 Mock／QA proxy 回傳缺欄位或不相符 actor／client／transaction／operation ID、非連續 seq、部分結果或未知 schema 的 HTTP 200；APP 不得標示 Accepted 或寫入新的 clientSequence／checkpoint。合法 accepted／duplicate 要對應同一原請求且只確認一次。檢查前後 preferences／session 與 redacted request correlation，不存 Node payload／金鑰；此不證明 durable pending outbox、真正 WS ack reconciliation 或 server 斷線重送已完成。

Explicit pending retry 開發回歸（2026-10-03）：先保存原 wire request，模擬 server 已接受但 response 遺失，重建 repository／preferences 後經明確 recovery hook 送出；兩次 body／transaction／operation ID／clientSeq／baseVersion 必須相同，duplicate 不回退 client sequence，正確 ack 才清除 pending。换 user／client／workspace／backend origin 或 viewer 不得送出；不相符 HTTP 200 保留記錄。目前為 MockEngine hook 測試，普通 submit 尚未 prepare，啟動／UI 未呼叫 recovery；不能用本項簽核宣稱實際重連／草稿復原已完成。

回歸異常紀錄（2026-10-02）：此輪新增 ack tests 四平台通過，但 Wasm 全套既有 WebM 測試出現「Initial paused frame must be white」失敗，獨立重跑全套後通過。不得刪除此證據或視為已修復；後續素材 QA 需保留初始解碼畫面與 fixture 產生／ready 時間線，區分 encoder fixture 與 native player 行為，不跳過白色／藍色 frame assertion。

共用背景交付後依 [BACKEND_CANVAS_STYLE_API_SPEC.md](BACKEND_CANVAS_STYLE_API_SPEC.md) §6 驗證：兩端 projection／snapshot／log replay 的 style 與版本一致；Preview／Cancel 不保存；Apply／Undo／Redo 版本單調；remote 改動不被舊 Undo 覆蓋；個人 viewport／zoom／showGrid／snap 不同步。需記錄 client／server schema 與 capability；不同 density 的 grid world-unit 規則仍待共同確認。未交付前維持「裝置本機背景」，不以目前色票 UI 驗收冒充共享與 Undo。

快速方案簿跨裝置驗收另依 [BACKEND_QUICK_SCHEME_API_SPEC.md](BACKEND_QUICK_SCHEME_API_SPEC.md) §7：記錄 resource／selection 版本、兩裝置身分、server ETag／revision；涵蓋競爭更新、回應遺失重送、cursor reset、帳號隔離、migration 重啟與素材 materialization／撤權。只在所有素材 ready 且單一 transaction 插入成功後簽核；不把本機保存、MockEngine tests 或草案交付當成跨裝置完成。QA 證據不得保存金鑰／signed URL；視覺／遮擋交產品簽核，效能最後。

後端交付範圍對照 [BACKEND_REALTIME_API_SPEC.md](BACKEND_REALTIME_API_SPEC.md) 的 fixtures／功能門檻。目前為待確認草案，新 URL／ticket／message／metadataRevision 不視為已存在。驗收須記錄 server protocol／schema／部署版本與 QA scope；文件 JSON 格式檢查不代替 TypeScript／Kotlin 共用 schema、雙端 runtime、ACL 或故障注入證據。

APP 已接上 optional remote notification → 權威 REST refresh 入口；目前 BackendWorkspaceRepository 未提供 stream，正式執行仍是 3 秒 REST polling。離線 tests 可驗證通知範圍／sequence gate、join wakeup、通知合併、stream end／failure fallback 與 observer cancellation，但不證明 WebSocket、真正 operation fan-out、Presence／Cursor／Selection 或 reconnect transport 已交付；BAI-009 的 URL／auth／message fixtures 尚待後端合約，後端關閉期間不得自行啟動。

遠端 refresh 競爭回歸（2026-10-02）：以可控 repository 延遲一次 refresh，在回應前切換 user／client／Workspace、推進目前 workspaceVersion／lastServerSeq，或修改目前角色／標題；舊結果不得採用、回退 checkpoint、重新授予舊角色或覆寫標題。fresh projection 必須與目前 user／client／Workspace 相符，content version 與 server sequence 均不能倒退；同 checkpoint 的正式 role／title 變更仍可刷新。取消 observer 後，故意讓 native transport 回傳結果或一般 HTTP／contract 錯誤，不能發布舊 projection／access revoked／錯誤狀態到新 session；重新連線應使用目前 durable checkpoint，通知本身不前移 sequence 或認領 ack。新增 5 個模型／取消策略 tests 不證明 Compose HTTP 競爭、server metadata revision、WebSocket reconnect 或 Presence 已驗收；需記錄 requested／current／returned scope 與 checkpoint 的時間線，不能保存 token。

本機操作佇列草稿驗收（2026-10-03）：草稿與首次已提交的 wire 請求分開保存，必須先處理未確認 wire，再由使用者明確確認「恢復並送出草稿」。以可控 QA repository／服務延遲第一筆 response，快速建立物件後編輯文字、移動或建立素材關聯，至少留下兩筆相依操作：

- 中斷／重新開啟 APP 前後，本機草稿數、operation ID、物件／素材引用與先後順序保持；開啟後不自動 POST。不同 API base／user／client／workspace 不得看見或恢復另一個 scope 的草稿。
- 第一筆已接受但回應遺失：先明確重送原 wire，其 transaction／operation ID／clientSeq／payload 必須完全一致。確認結果後僅移除第一筆，未送出 tail 留在草稿；使用者再確認恢復，才按 FIFO 送出剩餘操作，不為已接受 head 配新 ID。
- 在「草稿已前移、wire 尚未清除」的邊界中斷，再重送原 wire；草稿 receipt 必須辨識同 transaction，不得誤移除下一筆。此測試不等於所有平台斷電 fsync 保證。
- 已提交 head 成功後，恢復 tail 必須刷新權威 state，核對原 baseline content／workspace version／serverSeq 及目前編輯權限；遠端已變更或 viewer 不得自動 rebase／POST。保留草稿並提示人工檢視，不將舊草稿直接蓋回新畫布。
- 「停止本機重送」不等於撤銷 server commit。相關草稿隔離保留，不可當未送出操作重新配置 wire ID。唯讀屬性比對與 JSON 備份複製入口已接入；原生檔案另存、自動匯入與逐筆合併仍未交付，不能把檢視或隔離提示當人工解衝突已完成。
- 「移除本機草稿」須第二次確認，可取消；只移除匹配 draft ID，後端畫布、素材與 operation history 不變。必須告知本機草稿移除後不可復原。
- 模擬 Settings 滿額、chunk／manifest 寫入失敗、遺失／損壞 chunk：新操作不應顯示為已套用／已送出；未損壞的原 manifest 保留，損壞資料不得靜默刪除或自動重送。上限目前為 200 筆本機操作／4 MiB journal，Settings 沒有跨程序鎖或全平台 fsync 承諾；中斷寫入的 orphan chunks 清理仍待補。
- 素材／GIF／影片只保存引用，不存 signed URL、API key、影片原檔或播放狀態。草稿含私密畫布內容，QA 報告只記錄 scope 的代號、operation／transaction ID、數量／序號與故障時間線，不收錄全文。
- 實際重啟、手機恢復按鈕可操作性、取消／遮擋、VoiceOver 由產品負責人簽核；離線模型與 HTTP fixture 不代簽。效能與長期儲存壓力測試留最後。

草稿備份 reader 核心檢查（2026-10-04，尚無產品匯入入口）：目前只能以自動測試呼叫有界 v1 reader；JVM／JS／Wasm 各 4 tests 通過不等於已可從 UI 匯入。備份仍 `importSupported=false`。後續 UI 接線後再啟用以下人工驗收，現階段全部 Pending：

- 使用自己產生的備份，確認歷史 baseline／proposed／current 與未確認提交提示保持；必須刷新目前 read ACL，不能把保存的 current／版本當目前遠端或權限證明。
- 錯工作區、未知／缺少 schema、额外欄位、篡改 proposed／重複 operation、破損 hierarchy／relation、超過 4 MiB／64 層的檔案必須停止，不修改目前 history／journal／pending wire／viewport，不送出舊 operation、不下載素材。
- 錯誤畫面及 QA 報告不能包含 JSON 全文、私密文字或 decoder 診斷；有效檔案也不是可信來源簽章。跨帳號／撤權／取消／視窗關閉、原生選檔與逐筆合併需後續單獨驗收，不代簽。

JSON 貼上唯讀入口驗收（2026-10-04，取代上段「無 UI 入口」；選檔／合併仍未完成）：

- 開啟 Command Palette，搜尋「檢視 JSON 備份」／draft backup，明確點擊入口。預設輸入空白，不自動讀取剪貼簿；自行貼入先前產生的有效草稿 JSON，確認警告說明私密內容與系統剪貼簿同步風險。⌘V／Ctrl+V、Backspace／Delete、文字選取不能刪除畫布物件或觸發背景貼上；手機貼上選單／鍵盤／安全區域／VoiceOver 由使用者簽核。
- 按「驗證並檢視」：有效備份才取得最新讀取授權；保存的 current 不能出現在「目前遠端」欄，必須是此次新讀取 snapshot。此窗口僅唯讀，不提供套用／提交／重送／copy/export 控制，不改 wire／journal／history／viewport，不下載引用素材。
- 自行修改 baseline／proposed 使 replay 不符、改 workspace／schema、貼入超限資料，確認固定失敗訊息且保留可修正輸入，不洩漏 decoder diagnostics；原畫布不變。Viewer 可讀，401／403／404 必須停止顯示，不以保存資料授權。
- 延遲 fresh response，在回應前關窗、切帳號／工作區、撤權、離開又返回；舊結果不得重開／發布。取消可用於處理中，切 scope 不保留舊输入；開啟／關閉其他 panel 不應被此流程擅自清除。取消前後檢查未自動送出操作。
- 新增 4 coordinator tests 在 JVM／JS／Wasm 通过，只證明函數邊界，不代替上述 Compose／原生／服務操作。檔案 picker、iOS 回歸及逐筆合併另列 Pending；不把唯讀比對簽成完整備份匯入。

Desktop JSON 選檔驗收（2026-10-04，人工 Pending）：

- 命令面板 →「檢視 JSON 備份」→「選擇 JSON 備份」→自行選取先前匯出的本機 JSON。應只載入輸入框；還沒按「驗證並檢視」不能取得比對結果、套用或送出操作。取消選檔須顯示取消、不沿用之前輸入；仍可自行貼上。
- 選檔期間關閉／取消、切帳號／工作區／撤權、回傳與取消競爭，不得發布舊內容或重新開窗。測試同步資料夾、讀取權限拒絕、檔案被移走、空檔、超過 4 MiB、非法 UTF-8、symlink／directory，原檔必须保留且錯誤不顯示路徑／解碼內容。JSON 不含完整備份或格式錯誤時，在驗證階段停止，不自動重試。
- 中文／emoji 與 UTF-8 BOM 能載入，再按驗證才 fresh read ACL；來源檔案不變、不保存路徑偏好、不下載引用素材或上傳備份。原生面板／焦點／⌘V／Delete／VoiceOver／遮擋由使用者簽核；選檔函數 tests 不代替 GUI 驗收。
- Desktop 3 file tests 與 shared 3 selection tests（JVM／JS／Wasm）通過；Android／iOS／Web 選檔未接，不能簽為四平台檔案匯入。逐筆合併、應用、server acknowledgement reconciliation 另待完成。

Web JSON 選檔驗收（2026-10-04，人工 Pending）：

- 在 JS／Wasm 兩種 Web build 分別使用命令面板 →「檢視 JSON 備份」→「選擇 JSON 備份」，確認真實 chooser 能由明確點擊開啟（含 browser user-activation 限制）。選有效 UTF-8 JSON／BOM／中文／emoji，只載入輸入區；再按驗證才刷新讀取授權並比對。沒有素材下載、自動合併／POST、File 持久保存／URL。
- 選檔篩選僅提示，不以副檔名／MIME 作格式信任；驗證錯 schema／錯 workspace／壞 replay 均停止。空檔、超過 4 MiB、非法 UTF-8、讀取失敗不能發布 private decoder error 或檔名。
- 測試 native 取消 event、不支援 cancel event 的 focus fallback（回來後 400ms 無檔）、120s 逾時、page hidden、關窗／工作區切換／撤權與 File arrayBuffer 晚到回應；不得重新顯示舊 JSON。input／handler／timer 應清理；取消不能保證 OS 選檔面板已關閉，手動關閉後晚到回應也不能採用。不自動重試或 reopen chooser。
- Chrome／Safari／Firefox 等產品支援 browser，實際取消／焦點／鍵盤貼上與刪字／手機 keyboard／VoiceOver／安全區域／遮擋由使用者填寫裝置與 browser 版本、截圖與結論。Node 的 5 bridge tests 使用真實 Blob 但模擬 DOM，不代簽真實 chooser、Kotlin JS／Wasm callback 互通、Compose 交互。
- Android／iOS 選檔與逐筆合併仍 Pending，不能因 Web 選檔開發而簽成完整匯入。

Android JSON 選檔驗收（2026-10-04，人工 Pending）：

- 命令面板 →「檢視 JSON 備份」→「選擇 JSON 備份」，在 OpenDocument 選本機／支援雲端 provider 的 JSON（JSON／text MIME 篩選僅提示）；選擇後只載入輸入區，再按驗證才 fresh read ACL／比對。不新增持久 URI grant，不修改／刪除來源檔、上傳、套用或送出舊 operation。
- 測試取消、選檔中旋轉／Activity recreate／destroy、在 URI 回傳與 caller cancel 競爭、scope／帳號／工作區切換；晚到 URI 不讀取、不保留。原媒體匯入與 CreateDocument 另存不能同時 launch；native picker result 未返回前，即使 caller 取消也不能再 launch 第二個。重建時 launcher 註冊順序與 in-flight flags 保留，未知來源結果只能忽略。
- 分別測試 cloud provider 離線／拒絕讀取、遺失 URI、空檔、invalid UTF-8、Unicode 跨讀取段、BOM、超過 4 MiB、provider 故障回傳 0 bytes。原檔必須保留，錯誤不帶 URI／decoder diagnostics；已開始的 provider 阻塞讀取不保證立即中止，但回傳後不能發布取消／過期內容，需記錄時間線。
- 開系統 picker 導致 pause／resume，不自動恢復游標分享或素材播放；底部操作、鍵盤貼上／Delete、焦點、文字裁切與遮擋由使用者簽核。APK／common reader 3 tests 不是 ContentProvider 或 Android UI 簽核。
- Web 新增 4 Kotlin JS／Wasm 實際橋接 tests 已通過，但 click 攔截，仍不代簽真實 browser／OS chooser；iOS 選檔及逐筆合併仍 Pending。

iOS JSON 選檔驗收（2026-10-04，人工 Pending）：

- 命令面板 →「檢視 JSON 備份」→「選擇 JSON 備份」，測試 Files／On My iPhone／iCloud／支援 provider。選檔只讀入輸入區，另按驗證才 fresh ACL／比對，不自動套用／提交或下載素材。JSON／plain text 類型只是篩選，內容仍由 schema／replay gate 判斷。
- 外部 URL grant 失敗必須停止；own-container 不需外部 grant，但相似 home prefix／解析 symlink 指向外部不可當自己容器。撤銷 grant、provider 離線／權限拒絕、URL 遺失、空檔、超量、invalid UTF-8／BOM／Unicode、symlink／特殊檔案需測試，來源檔保留，錯誤不含 path／nativeError／私密內容。不保存 bookmark／URL，不上傳 JSON。
- 選檔／dismiss animation 期間取消、關窗、controller dispose／lifecycle owner 替換、scope 切換及 late delegate callback 不發布舊資料；原素材／另存系統 panel 未關閉時不能再 launch。阻塞 coordinator/provider 不保證立即停，但回來後不得採用 scope 已變的結果。
- 檢查文件 picker 能從目前 Compose 視窗正確呈現、取消後焦點／貼上／刪字、手機 keyboard／bottom sheet／safe area／VoiceOver／動態島與遮擋；由使用者截圖與簽核。4 native file／coordination tests 與 common 四平台回歸只是 sandbox 證據，不代表外部 provider／UIKit 已驗收。
- 備份套用／逐筆合併仍 Pending。另追蹤 Wasm video Mocha 2s timeout 與後續 fixture 計數不穩定，不把重跑綠燈當效能證據；效能測試最後。

合併操作編譯驗收補充（2026-10-04，服務／人工 Pending）：

- 最新重新規劃入口：另一端更新後，舊 choices／驗證按鈕停用；按「載入最新比對並重新選擇」只更新遠端比較、保留原 draft／operations／quarantine／unconfirmed，所有 choices 未選且舊檢查文字消失。不得自動繼承先前選項，不重讀來源檔、不 POST／journal。再次更動要再次檢查。這取代下方必須關閉重開的初版流程。
- 查詢期間 access-loss／scope expired 應關閉 private preview；晚到回應、關閉或更換 review 不留下可用 Checked。UI key／取消只是 APP guard，不是正式服務授權。
- v1 備份缺 wire manifest／原 actor／receipt，false flags 或新裝置沒有 pending 不代表原提交已結束。依 BAI-012 保持正式 Apply Pending；有限 catch-up 沒找到、內容剛好相同不得簽成 ack／not-submitted。原請求晚到與新 merge 去重／settlement 必須有服務證據。

- 選完每列後按「重新檢查權限與素材」。明確按鈕才讀 fresh Workspace／pending／素材 metadata，不下载 binary、不產生 POST／journal／畫布變更。通過文案需明示本次唯讀檢查，不顯示「已合併」；正式套用未接，不能簽為匯入成功。
- 驗 Owner／Editor 通過讀取 gate，Viewer／撤權拒絕；switch user／Workspace、關閉視窗／取消、late response 不得發布有效結果。pending submission／pending draft、quarantined／unconfirmed backup 不可自動重送、清除或繞過。
- 更換素材需讀 before 及 after 的可見 metadata（包含 Undo 的舊引用）；拒絕錯 ID／跨 Workspace／錯 mediaKind／非圖片縮圖／新引用非 Ready。既有 missing media 未變更時不為無關文字編輯讀全部素材；舊引用 Missing 仍需可見且 kind 正確，不冒充可播放。
- 檢查中另一端修改 version／sequence／role，或出現 pending，應停止並要求重新驗證與重新選擇，不把舊 choices 自動套到新 review。metadata 查詢後再檢查 Workspace 與 pending；這不是原子服務提交，二次確認時仍須重檢及正式原提交確認。無 endpoint／restore 合約的結構變更列服務 Pending。

- 最新素材引用 compiler 使用 update_object.properties 保留原 Node／相連 relation ID，不再 delete＋create；驗新素材與 mediaKind、縮圖替換／null 清除、metadata 保留、expected version、鎖定／stale 拒絕及 Undo／Redo。自動 DTO／domain 測試不能代替真實跨畫布 ACL、missing asset、服務拒絕與雙端 projection 驗收。

- 本輪只有純 domain compiler 與 contract gap assessment，UI 沒有套用入口；「檢查合併計畫」不得導致 POST、journal 修改或畫布變動。
- 自動測試檢查 fresh operation IDs、逐次 expected version、原子展開上限、群組 reparent／關聯順序及本機 Undo／Redo。形狀／顏色修改後鎖定需為獨立 lock-only，inverse 先解鎖再還原属性。
- 型別替換與 relation endpoint 更換仍涉及同 ID recreate，現有後端拒絕 tombstone ID。上述例子只能記錄「本機 replay 通過／服務 Pending」，不能簽為正式合併成功；新增的 Undo→Redo、刪除 Undo 亦待 restore 合約。素材引用更新已移除 recreate，但正式提交仍 Pending。
- 正式 Apply 開發後另驗 fresh edit ACL、素材引用權限、原未確認提交、最新版本／重新規劃、二次確認、200 wire operation 上限與 ack；Viewer／撤權／失敗不可新增本機成功記錄。證據去識別化，不記錄備份內容、金鑰或 signed URL。視覺、遮擋與 VoiceOver 交由產品負責人；效能最後。

逐屬性合併計畫驗收（2026-10-04，人工 Pending，尚無套用）：

- 匯入／貼上有效 JSON →驗證取得 fresh remote →逐屬性合併計畫。預設每列均未選擇，所有列有選擇才可檢查；無衝突列也不能默認採用草稿。分別選擇 text、position.x／y、size、rotation、shape／顏色、parent、lock、媒體引用或 relation 屬性；草稿未改的 remote-only 值必須保留。
- 同一個欄位 remote 同時改動時提示衝突。新增／刪除／型別變更／遠端已無該實體須整筆決定，不把 before／draft version 蓋上目前遠端。全選保留遠端不能改畫布；計畫採用草稿也只產生提案，沒有 history／journal／wire 修改或 POST。
- 新群組與 child 只選 child 必須拒絕；cycle／missing group／missing endpoint／self-edge、刪除有未處理 remote relation 的 node 不得自動 cascade／丟資料。已鎖定物件不能隱式編輯／刪除；解鎖必須另明確選 locked=false，後續真正 builder 還需先 emit unlock 才修改。
- 變更選項後舊「有效」提示清除；關窗／新 review 不保留私密選項。現在的「有效」僅指 graph／lock 結構，不表示最新 ACL、素材授權、原未確認提交或 server ack。Viewer 不可因此變成 editor。
- 鍵盤／觸控選擇、長 JSON 捲動、各手機面板／safe area／VoiceOver／遮擋由使用者簽核。5 common tests 三目標通過不代替 Compose 交互；本輪 iOS 尚待回歸。Apply、二次確認、fresh snapshot mismatch、新 operation ID、Undo／journal、原提交處理另列 Pending，不能簽為已完成匯入合併。

草稿唯讀檢視／備份驗收（2026-10-03）：對上述相依操作／隔離草稿明確點擊「檢視草稿」，不要從 Node 選取或重新連線自動開啟：

- 刷新目前讀取授權後，檢視窗應對照原基準、本機草稿結果、目前遠端的物件／關聯屬性，按原順序列出 operation ID。新增／修改／移除、文字／shape／顏色／角度／尺寸／parent／lock／素材 ID／關聯 intent／label 均可查看；遠端為點擊／複製時讀取的 snapshot，不是持續 Live。現在是 JSON 屬性比對，不是視覺畫布或可編輯 preview。
- 不相關遠端修改仍使整體 baseline 不相符；單一物件相同也不能放寬 workspaceVersion／serverSeq／ACL gate。「遠端內容與草稿相同」不是 server ack，不清除 pending wire 或草稿。停止重送後未確認狀態仍可保留。
- 檢視僅 GET，不 POST／DELETE，不套用到主 history／viewport／本機選取、不清 journal，不下載舊圖片／GIF／影片或使用 signed URL。Viewer 有目前 read ACL 可檢視，但不能恢復提交；401／403／404、錯 draft ID／client／user／workspace 必須拒絕，不以舊 cache 偽成功。
- 切換帳號／client／workspace、存取失效或關閉時，舊檢視不得顯示或晚到複製資料；回到原 workspace 也不自行重開。關閉只關此檢視，不關其他面板；Delete、⌘K、畫布選取／Undo 等背景快捷鍵不接管檢視中的文字選取。
- 點「複製 JSON 備份」先顯示私密內容與系統剪貼簿同步警告，第二次確認才再次刷新 read ACL 並複製。取消／關閉不改原剪貼簿；非同步回查後已關窗或 scope 已變，不得晚到複製。查看／複製失敗不自動移除原草稿。
- JSON 應有 format=`boarderless.workspace-draft-review`、schemaVersion=1、importSupported=false、原 operations／baseline／proposed／current、版本及未確認／隔離標記；不含 Settings scope、API base、user／client identity、wire request、票券或 API key 欄位。它含私密畫布內容，使用者文字可包含任意字串；此格式不是機密掃描器或 REST 請求。自行貼入本機檔案保存，不把「已複製」當成原生檔案已落盤／跨裝置恢復／自動匯入完成。
- 以長文字、旋轉 Node、已刪素材、同 ID 的 object／relation 與小螢幕驗收：完整內容可捲動／選取，恢復按鈕可換行且帶 gap；safe-area、焦點／VoiceOver、實際剪貼簿、視覺與遮擋由產品簽核。QA Workbench 的草稿恢復題維持 Pending 直到實機／服務證據完成，不能用模型測試代簽。

Desktop／Android／iOS 草稿原生另存驗收（更新 2026-10-04）：草稿檢視窗新增「另存 JSON 檔案」→私密內容／同步資料夾／不可匯入警告→第二次「確認另存備份」→系統檔案／資料夾選擇。此入口不受開發 QA env 限制；Web 改為下述「下載 JSON 備份」，不宣稱使用原生另存 picker，四平台仍保留原剪貼簿備份。

- 選擇新檔名：選定 destination 後才重新 GET 確認 read ACL，write 前再次檢查 scope／draft ID／取消；UTF-8 JSON 使用既有 versioned review backup，包含 baseline／proposed／current／operations 與不確定標記，importSupported=false。不可清除 wire／journal、不 POST／DELETE、不改 history／viewport、不下載素材，複製備份仍是獨立動作。
- Desktop 選擇既有檔案或 symlink：CREATE_NEW 必須拒絕且原內容不變；不提供靜默 overwrite、不自動挑其他路徑。取消選檔不讀草稿或寫檔。選檔等待中切 scope／關閉檢視／撤權，以及 IO 排程後失效，均不能把舊資料寫入 destination；選檔後 read denied 必須不寫私密 bytes。Android 使用下述 CreateDocument provider 流程，不把 provider 語意冒充本機 CREATE_NEW。
- 測試保存前、建立檔案後與 flush 邊界的 scope／取消失效；程式僅 best-effort 回收此次新建的不完整檔案，不能刪原檔。cleanup 失敗時使用者需檢查選定位置，不能把這個路徑顯示成「已成功保存」。已完成寫入後的取消／撤權不承諾撤回既已保存的私密內容，不把本機另存當後端可撤銷權限。
- 手動開啟檔案核對中文／emoji、完整 operations 與版本、內容沒有 UI runtime key／ticket／signed URL 欄位（使用者自己的文字可能含敏感資料，警告不能替代內容審查）。選取同步資料夾可能由 OS 自行上傳；APP 不自動開啟／上傳／匯入、不保存 destination 偏好。native picker／焦點／取消／檔案權限與 UI 視覺由產品簽核；不宣稱 fsync／斷電持久性，QA Pending。

Android 文件供應商驗收：使用 ActivityResultContracts.CreateDocument（application/json），不能用 OpenDocument 選原檔再 truncate。CreateDocument 可能先建立空文件，fresh ACL 或 scope gate 失敗時需等待 provider cleanup；共用 coordinator 在 NonCancellable 區段 await dispose，cleanup 錯誤不能取代原撤權／取消。URI 只留本機、不存偏好或 wire、不保留 persistable grant。

- 使用本機 Documents provider 選新文件，確認 UTF-8 可讀、沒有覆寫既有檔案。寫入前檢查 content document URI 且可讀為空；非空或無法驗證的結果拒絕寫入／刪除，不能猜測其為 App 新建。真實 provider 的同名建立／空文件／stream／flush／delete 行為須實機驗收，APK 編譯與共用 fake destination tests 不能代簽。
- 選檔期間按返回／關閉草稿／撤權、在 URI 回傳前旋轉／重建／銷毀 Activity、回傳與 coroutine 取消競爭；不得寫私密 bytes。空白 late result 僅 best-effort 回收，非空或無法讀取則保留供使用者檢查。匯出 picker 與原媒體 picker 不可並行；ActivityResult 註冊順序保留，重建後尚未返回的 picker 阻止第二次 launch。
- 寫入前、建立 stream 後與 flush 後失效，檢查新文件 cleanup；已完成保存之後的取消不承諾撤回內容。provider 無刪除能力、離線或進程被 kill 可能留下空／不完整文件，應顯示失敗／請檢查而非偽成功。程式只清此次 CreateDocument URI，不搜尋／批次刪除其他文件。
- 開啟系統選檔造成 Activity pause／resume 後，分享維持關閉、畫布 GIF／影片不自動續播，草稿與原素材匯入不丟失。若選雲端 provider，使用者已授權目的地可能自行同步；不將這種 native provider 上傳宣稱 APP object storage 功能。手機 bottom sheet／焦點／遮擋／VoiceOver 由產品簽核；QA Pending。

Presence APP 入口驗收（2026-10-03）：目前只有 transport-neutral snapshot／observer 與 draw-only overlay，BackendWorkspaceRepository 尚未提供 Presence stream。沒有 stream、stream 結束或失敗時，成員面板應顯示「即時上線狀態尚未連接」，不得把 membership 名單冒充在線名單。此階段可用受控 repository 驗證以下流程，正式交付仍需真實 transport：

- 同一使用者其他 client 仍列為其他上線連線；只有目前 user＋client 的自身連線不重複列出，連線數不冒充唯一使用者數。
- 切換帳號／client／工作區、角色改變、重新連線或存取失效時，舊游標與選取應立即隱藏，不能等下一次 event；舊訂閱晚到回應不得污染新 session。
- 同 room epoch 的重播／倒退 revision 或 lease sequence 不延長有效期。名單內容不變時，只有經 authenticated transport 確認且遞增的本機正規化 lease sequence 才能更新 TTL；不能僅因 socket 還開著就續命。相同 roomRevision 不得夾帶變更名單，retired epoch 不得復活。
- TTL 到期後名單／overlay 清除；新可信存活 snapshot 可恢復。roomRevision／leaseSequence 不是 canvas serverSeq，不寫入 durable checkpoint、正式 history 或 membership。
- 兩端使用不同 zoom／pan，游標與旋轉物件的選取框仍落在同一 world-coordinate 位置；已刪物件的選取不繪製、不重建物件。游標、名稱與選取框不攔截本機拖曳、文字編輯、焦點或 Inspector。
- 視覺（含名稱遮擋、游標／選取辨識、手機面板）及 VoiceOver 結果由產品負責人簽核，模型 tests 或編譯不代簽。記錄 scope／epoch／revision／lease sequence／到期時間線，不保存 token、API key 或私密畫布內容。

Presence 送出管線與 UI 驗收（2026-10-03）：共用 runner／optional publisher 已接畫布限定 passive observer、成員面板分享按鈕及 Live／scope／epoch owner gate；正式 transport 尚未交付，Backend 預設 null，分享按鈕應不可用。不得以模型測試或編譯代簽 UI。以可控 authenticated repository 驗證 UI，正式交付仍需真實雙人服務：

- 分享預設關閉時零個 cursor／selection payload；明確開啟才送出，關閉後清空一次、不重複傳送空內容。不持久記憶成跨帳號的分享授權。
- 只傳 world-coordinate 游標與最多 128 個目前權威物件 ID；不同 pan／zoom、旋轉 Node 仍正確定位，optimistic-only／未知／已刪物件不能送出。表單輸入、API key、未提交文字、viewport／zoom 不在 payload。
- 高頻拖曳只保留最新待送值，一次一筆，cursor 送出至少相隔 100 ms、selection 內容變更至少相隔 250 ms；慢 transport 不累積舊游標，服從正式 capabilities 更低限制。這是正確性驗收，不是排在最後的效能壓測。
- 關閉分享與權限／scope／epoch 失效不同：前者可送清空，後者立即停止、關閉舊 handle，不向新 room 重播；新 handle sequence 從新 session 開始，不把舊游標帶過去。
- publish 失敗不自動 retry；取消後不能啟動下一筆，cleanup 失敗不能吞掉原取消或發送錯誤。實際 socket 寫入須再次查 authority／physical epoch，模型 runner 無法代替 transport gate。
- 手機手指放開／離開畫布、背景化、表單焦點、分享開關可及性與遮擋仍需產品負責人實機簽核，QA 結果保持 Pending 至取得證據。

操作步驟：開啟成員面板，無 publisher 時確認分享按鈕 disabled 且不顯示「正在分享」；受控 Live publisher 啟用後仍不自動分享，明確點擊才開始。拖曳／旋轉／縮放 Node 不受 observer 接管，移到兄弟面板／表單／GIPHY 金鑰輸入區後不再採樣該區座標；⌘V／Delete／文字編輯維持原焦點行為。停止分享後對端游標與選取清空一次。切帳號／client／工作區／角色／room、斷線重連或可用 lifecycle epoch 變更，分享回到關閉，新 observer 不沿用舊 sample，舊 finally 不清新 owner 狀態。檢視私密草稿或切換工作區期間送出空 intent，不採樣畫布。

iOS 另存資料夾驗收（2026-10-04）：第二次確認前說明會選資料夾而非指定檔名，建立 `boarderless-draft-UUID.json`；不能將此 UI 宣稱可指定檔名。Folder picker 只取得 URL，fresh ACL／scope 通過前沒有私密暫存檔。依 [Apple 外部文件存取規範](https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller) 使用 security-scoped URL 與 NSFileCoordinator。

- 在「檔案」選可信資料夾，授權成功才協調寫入子檔案，O_EXCL 新建、0600 權限、UTF-8；失去 scope／取消或寫入失敗只清此次新檔，不碰原檔或資料夾。拒絕授權／唯讀／協調錯誤必須失敗，不用其他路徑 fallback、不重新廣泛授權。
- 寫入完成或失敗後均 stopAccessingSecurityScopedResource；不保存 bookmark／資料夾偏好、不放入 canvas／wire、不自動開啟／上傳／匯入。選 iCloud／第三方 provider 可能由目的地自行同步，不保證「只在本機」。不承諾 fsync／跨進程 writer／kill 後回收。
- Folder picker 開啟／取消／dismiss／controller disposed／lifecycle owner 更換／late delegate callback、Compose 草稿視窗與系統面板的顯示及焦點，由實機驗收。取消前沒有新檔；選完 folder 但 read denied／scope changed 不得寫私密 JSON。解除 folder grant、provider 離線／不同步／不允許寫入也需測試。
- Simulator 的 POSIX／Foundation coordinator sandbox tests 不證明外部 Files／iCloud／security-scoped grant／native picker 已通過。至少驗收本機 Files 與 iCloud provider、同 folder 多次保存不覆寫、既有同名檔／symlink 保留、中文／emoji內容可讀、safe-area／VoiceOver／遮擋；QA Pending。

iOS 自動回歸前置條件：Foundation coordinator 需要 simulator 系統服務，shared 的 KotlinNativeSimulatorTest 已設定 `standalone=false`。先以 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun simctl list devices available` 選 UUID，再 `xcrun simctl boot <UUID>`（已 Booted 不重複 boot）與 `xcrun simctl bootstatus <UUID>` 確認完成；上述 xcrun 都須同一 DEVELOPER_DIR。Gradle 執行 `:shared:iosSimulatorArm64Test --device <UUID>`。若服務／boot 未就緒，修正環境後重跑，不跳過 coordinator case、不更換成未協調寫入。記錄 device／boot／standalone 模式及 XML；本輪 5 native tests 已通過，但不能代簽外部 provider、實機、視覺或遮擋。需要 system services 的測試模式參考 [JetBrains 原始實作](https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/tasks/KotlinNativeTest.kt)。

Web 草稿 JSON 下載驗收（2026-10-04）：使用「下載 JSON 備份」→私密內容／瀏覽器控制目的地／不可匯入警告→第二次確認。重新檢查 read ACL、草稿身分、scope／取消後才產生本機 Blob；下載前再檢查 scope 與頁面可見性。程式只請求下載，不取得檔案系統 handle、不寫任意指定位置，也不能確認下載完成：[download 屬性不保證下載發生](https://developer.mozilla.org/en-US/docs/Web/API/HTMLAnchorElement/download)。

- 未確認、fresh read denied、草稿／身分／工作區變更、取消或 hidden 頁面不得發出下載；草稿、wire、journal 不修改，不 POST／下載素材／上傳 JSON。未確認前不能產生含私密內容的 Blob。
- 正常結果必須顯示「已請求下載」，不能顯示「已保存」。確認瀏覽器下載清單及實際 JSON，包含 schema、基準／草稿／遠端與 operations、中文／emoji，而非只看 toast。檔名建議為 `boarderless-draft-UUID.json`，不包含工作區標題／ID／API key；瀏覽器可改名，不能保證不同瀏覽器都不覆寫。
- 原生另存目的地與 Web 不同：瀏覽器控制位置、命名、覆寫提示及完成狀態；驗收預設下載資料夾、每次詢問位置、同名／改名／取消、同步雲端資料夾、下載遭封鎖及 sandboxed iframe。不宣稱 browser download 有 O_EXCL、0600 或可撤回，開始後撤權／關頁也不能保证已保存檔案消失。
- anchor 點擊後移除；成功請求的 object URL 60 秒後釋放，失敗立即 revoke／清 timer。不保存 URL／Blob 到 localStorage、Workspace、方案簿或後端；無自動 retry。檢查瀏覽器封鎖／取消後可明確重新確認下載，每次新建 request／檔名，避免連續重試產生重複備份。
- JS／Wasm 的 DOM fixture 攔截 click 以免測試寫入磁碟，真實 Blob UTF-8／object URL／timer cleanup 的證據不代表實際下載成功。Chrome／Safari／Firefox 等支援平台、Compose 操作、焦點、視覺與遮擋仍由使用者簽核，QA Pending；不支援匯入／自動衝突合併。

平台 lifecycle 接線驗收（2026-10-03）：Desktop 使用既有 window visibility；Android Activity onPause／onResume／onDestroy、iOS application resign／become active 通知（Compose disposal 移除 observer）、Web visibilitychange／pagehide／pageshow／freeze／resume（disposal 移除 listener）已接 runtime activity flow。初始 unavailable；每次 available→unavailable 推進 epoch，快速 hide→show 也必須讓舊分享 owner 失效。送出前直接讀 StateFlow.value／epoch，不只等 UI 重組。執行以下實機／服務步驟，模型與 headless DOM event tests 不代簽：

- 開啟分享後按 Home／鎖屏／切 App、Web 切分頁／瀏覽器返回與 BFCache、Android 系統選檔／旋轉重建、iOS 開啟系統介面，舊 publisher close／leave，不能再送出 cursor／selection。若平台完全凍結／kill 未能送 leave，對端由正式 server TTL 移除，不保證當下送達清空。
- 立即切回前景，即使 unavailable 值被 StateFlow conflation 跳過，epoch 仍改變、分享回到關閉；GIF／Video Node 不自動續播，需重新明確啟動。不影響已選媒體來源的匯入結果或草稿保存。
- Web blur／focus 不等於頁面隱藏；在同頁表單輸入、開面板不能觸發 lifecycle epoch。pageshow 不得解除 freeze，visibilitychange 不得解除 pagehide；各自恢復事件才能解除對應原因。
- 關閉 controller／page 後事件監聽移除且 tracker terminal，晚到 callbacks 不復活舊 owner。iOS 已接 application active ∧ controller lifecycle RESUMED ∧ 所屬 scene active 三項 gate；無 window／scene 不可用，UIScene 通知按目前 controller 的 windowScene 篩選，不接受其他 scene 的事件。WillDeactivate 的負向訊號須留到同一 scene 明確 DidActivate，不能被全域 App 啟用通知覆蓋；更換 lifecycle owner 使用新 tracker。此為接線＋模型證據，不是 UIKit 多視窗實機簽核。
- iOS 多視窗操作：兩個 controller／scene 都啟用後，只背景化／關閉其中一個，另一個維持正常；全域 App active 不得重新開啟被隱藏的 controller。controller 移到其他 scene，即使兩個 scene 都 active，也必須推進 epoch、重置分享。初次 attach window、系統選檔 presentation／dismiss、快速切 scene、disconnect、controller 移除與重新建立需逐項驗收，記錄 native scene event／controller lifecycle／activity epoch 時序。沒有 window 不可用，不能用 appActive 作 fallback；恢復僅提供可用性，不自動播放或分享。
- 記錄 activity epoch／subscription／room／停止時間線，不含 keys／私密內容。真實 socket 最後寫入仍須驗證同 connection／authority，UI runner 不能取代 adapter gate；QA Workbench 保持 Pending 至實機與雙人證據完成。

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
2. 素材問卷共 12 題：真實 asset lifecycle、GIF、影片控制／聲音、display metadata、生命週期、錯誤／重試、封裝 runtime、素材庫重用（5.23）、完成確認不確定（5.24）、下載取消（5.25）、跨重開回查（5.26）及產品媒體視覺。每題顯示 SOP 章節與證據提示；預設一律 Pending，建置／fixture 通過不自動勾選。新增題目使用獨立穩定 ID，不覆蓋原題答案；效能題仍排最後。
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

Browser video固定fixture（2026-10-05）：pixel oracle固定730-byte SHA-256／32×32 VP8／3白6藍幀且無Duration；不得用timer callback當已編碼frame。Node hash／callback測試不等於native codec；JS／Wasm須驗白首幀／初始position0／後色／immutable／seek／播放結束重播／release，保留原assertions與timeout。重複回歸使用task --rerun並記session／report timestamp，不把cache算新執行。此輪只修QA producer，不當production播放器、MediaRecorder生成或使用者視覺已通過；詳見[BROWSER_VIDEO_QA_FIXTURE.md](BROWSER_VIDEO_QA_FIXTURE.md)，歷史失敗保留，真實素材／服務／實機與效能最後不刪減。

Desktop sequence ledger fixture（2026-10-05，optional未啟用）：先明確initialize乾淨scope的verified floor，再跨Workspace預留range；兩個真正JVM競爭同generation只一winner，另一busy／conflict停止、不可盲retry。DataForced／Published checkpoint kill後freshprocess/store只讀完整旧或新high-water，下一range不重用；CommitUnknown亦freshread。未知payload／tombstone／safe bound耗盡不得重置。repository注入ledger時HTTP前檔案高水位與wire clientSeq一致；這不是productionfloor來源完整性、排除舊writer或硬體斷電證明，實機與legacy migration另列Pending，不在真實prefs故障注入。

client sequence 預留 fixture（2026-10-05）：新 wire送出前已預留高水位；stage／HTTP失敗／不合法ack後不回收range。兩個共用SessionPreferences的repository、兩個Workspace，第一筆response loss後下一筆需更高clientSeq；真正並行時第一筆release前第二筆不可開始transport。重新建立prefs及舊duplicate ack不可倒退，safe integer耗盡／count超界停止、不改counter；malformed ack仍保留exact pending，不推進workspace version／serverSeq。不同prefs instance／多程序／legacy floor與正式全域ledger另列Pending，Settings fixture重開不當power-loss通過；效能仍最後。

repository storage integration fixture（2026-10-05，預設仍 Settings）：用新私密 temp root＋Ktor MockEngine，驗 transport 開始前完整 journal head＋exact wire 已讀得到；stage file failure／CommitUnknown 不 POST，顯式 retry 送同一 request，不換 IDs／clientSeq。transport 期間排入 tail，ack 後 baseline 前進且 tail／receipt 保存。Desktop stop 將未確認 wire 歸檔，不列可 retry pending、不當 server cancel；archive 不得隨 draft 刪除。此 fixture 不是網路 E2E／正式 bootstrap／舊 writer 排他／power-loss 驗收；全域 clientSequence Settings 協調與正式 settlement 另列 Pending，不用真實 prefs 做故障注入。

atomic restore／quarantine／remove fixture（2026-10-05，未接 APP）：用不同 user／client、Viewer、不同 version／seq／baseline／draft ID 驗 restore 停止；pending／quarantined 不得 restore。quarantine 後 exact wire／head 全保留，相同 wire stage 也不能開啟重送；validated late ack 可以記錄但 quarantine 仍在。錯 draft ID／stale generation remove 保留新資料，有 pending 必須拒絕；合法 remove 重開為 tombstone 且 generation 增加。不得將本機隔離當 server transaction cancel，正式原提交 settlement／ACL 另驗；僅使用 fixture，不破壞真實 prefs。

legacy adoption fixture（2026-10-05，未接 production）：先由原 v1 Settings publisher 建 journal／wire，保存 fixture 的全部 keys／值，adopt 後應完全保留。wire-before-markSubmitted 只對 matching head 正規化；ack-wire 殘留／mismatch／missing wire 需停止。before publication fault 無新記錄，after publication CommitUnknown fresh read 有完整 bundle＋fingerprint；相同來源 retry 不覆蓋新 generation，來源不同／tombstone 不復活。marker 要隨 append／stage／ack 保留。這不是舊 reader format 切換、不能證明 legacy writers 已排除；正式遷移 coordinator／reader routing／實機另列 Pending，不讀写真實 prefs、不 POST、不清理 legacy keys。

atomic transitions fixture（2026-10-05，未接 APP）：stage 後新增 tail，再 ack head，重開應保留 tail／exact IDs 且 baseline 正好前進一次；重複相同完整 submission＋版本／序號不再前進。同 transaction ID 不同 local operation／wire payload／clientSeq，或倒退／超限 ack 必須停止。stale generation 不應執行 transition，transition 例外不發布。LocalDraftAcknowledgement 只列本機證據，不替代正式 server receipt／ACL／reconciliation；目前沒有 production 遷移或新 APP 保存入口，仍用乾淨 fixture，不破壞使用者資料。

typed scope bundle 補充（2026-10-05，仍未接 APP）：fixture 需驗 journal／exact wire round-trip 不重建 transaction／operation／client sequence；head、local operation、base version 不一致不能發布。未知 schema／錯誤 scope／UTF-8／超深內容須停止，更新與刪除都不可清掉原記錄。legacy wire-only 與 journal 的已 ack marker 分開記錄，marker 不當成正式 server receipt。typed Desktop wrapper 的 generation tombstone／stale CAS 需重開確認；正式 migration／reader marker／repository 與 BAI-012 receipt 另列 Pending。本節無新的使用者 APP 保存入口，禁止用真實 prefs 做破壞測試；視覺與效能流程維持原安排。

Desktop 原子 store 補充（2026-10-05，未接 APP）：只能在獨立 fixture private root 跑 CAS／scope／checksum／busy／tombstone、兩個真正 JVM writer、DataForced／Published checkpoint terminate 後重開。結果必須是舊或新完整 generation，不混合、不 ABA；CommitUnknown 需 fresh read，禁止盲目 retry 或重建 wire IDs。不能在使用者 prefs／Workspace 破壞或 kill 現有 APP 來代替 harness。此輪 POSIX／本機檔案測試不是 Android／iOS／Web／Windows／網路 filesystem 或真正硬體斷電通過；production SessionPreferences／typed bundle／migration／repository 尚未接線，沒有新的 APP 保存驗收入口。staging 與 lock 的回收須另按排他協定驗證，不手動批次刪除。效能最後。

pending wire／深度格式補充（2026-10-04）：manifest 超長、byte 宣告不符、JSON framing 或深度超 64 應停止讀取／採用，保留原記錄與 wire IDs。quoted brackets／escaped quotes 的合法文字不誤判超深。legacy checksum 正確但超深仍不准直接 ack 清除／替換／自動重送；標為需處理，不把 checksum 當 replay／權限或完整協作證明。自動 fixtures 用獨立 Settings，不人工破壞真實待確認請求。完整 crash／fsync／migration／cross-process 與真實雙端驗收仍依 durability plan；bounded decoder 不代表 arbitrary in-memory 操作樹或 serializer 已全部受控，效能最後。

journal 保存一致性補充（2026-10-04）：invalid scope／version／sequence／transaction 在 chunk publication 前拒絕；invalid ack 保留原 head／FIFO tail，不能清 wire 或冒充已確認。超長 manifest／超出宣告 byte 的 chunks 讀取停止，保留原文；不可自動清除或重送。自動 fault fixtures 只列保存格式／bounds／恢復一致性證據，不當正常重建 store 為 process crash／fsync／斷電測試。後續依 APP_DRAFT_JOURNAL_DURABILITY_PLAN 用獨立乾淨 fixture 測雙 writer、kill／reopen、migration／配額與 I/O，不在使用者真实 prefs 或 Workspace 人工破壞資料。平台 durable 能力與證據分列；未知 schema／損毀／正式服務契約缺少一律 Pending，不勾通過。完整 durability 尚未交付，效能最後。

鋼筆精確座標驗收（2026-10-04，人工 Pending）：

1. 開草稿後使用「在繪圖中心新增節點」，應新增一個位於 viewBox 中心的 Anchor 並選中，切編輯模式；每次是一筆 Undo。封閉路徑／1024 上限／gesture／色盤期間不可新增。連續新增會在同一中心位置，需再明確改座標，不冒充自動排版。
2. 用上一／下一節點選取，顯示正確 index／count，邊界按鈕不可越界；「置中選取節點」只改 camera，不加入 Undo。以滑鼠選取另一節點、刪除或 Undo／Redo，座標表單不可殘留可套用到錯誤 index 的舊內容。
3. Anchor X／Y 改成已知值：輸入期間 path 不變，套用後 Anchor 與雙側 handles 同移且一筆 Undo。切 incoming／outgoing，只改該 handle；缺少者 Apply 建立、取消輸入或換 target 不建立。「移除控制點」只移除目前 handle，不刪 Anchor／另一側，Undo／Redo 應還原。輸入可貼上／刪除，不觸發背景 Workspace 操作。
4. 測負值、小數、±1,000,000；NaN／Infinity／超長／超限不可套用。Anchor 的新座標雖合法，但 translated handle 超限時也需顯示停止訊息且整筆不變。Anchor 接近 ±1m 時按「曲線節點」，若 ±30 新 handles 超限需安全停止提示、不 crash／不部分變更，Undo 數量不增加。超出原 viewBox 的合法節點可用表單／中心選取／fit 找回，不用畫布方框限制冒充路徑 bounds。
5. Desktop／Web keyboard Tab／Enter、Android／iPhone 輸入與 VoiceOver 檢查 target selected state、欄位名稱、index／count、disabled 與捲動可達性，記錄實際 focus／朗讀／鍵盤遮擋。semantic 標籤／模型 tests 不代表真實輔助科技已驗收；完整快捷鍵／pinch／實機結果仍 Pending。色盤或 role／scope change 的晚到輸入不得套用。視覺／遮擋由產品負責人簽核，效能最後。

鋼筆填色／描邊驗收（2026-10-04，人工 Pending；尚非 Node 保存）：

1. 開鋼筆草稿，分別開填色／描邊顏色。沿用既有 Color Picker 的預設色、HEX／色彩模型；預覽只改畫面，按取消／Escape 應還原，第一次關閉應只關色盤、不丟棄草稿。套用只增加一筆 Undo；同色套用不新增歷史，Undo／Redo 應還原顏色、不改 Anchor／camera。
2. 測 fill-only／stroke-only；最後一種可見 paint 不可關掉。對原本關閉的 paint 選色並套用應啟用它，取消不啟用。色盤開啟期間背景 path／history／viewport 操作禁用，Workspace native Delete／Paste／Undo 不得接管 HEX 輸入。
3. 描邊寬度输入例如 0.25／2.5／128，再明確套用；0／負值／超過 128／NaN／Infinity／過長輸入不可套用。preset 與 Undo 後檢查顯示值／實際描邊一致。輸入可貼上／刪除，不應改背景畫布；寬度是路徑 local units，非裝置像素。
4. 建 self-intersecting contour，切非零／奇偶填色規則，Undo／Redo 還原。開放路徑填色為隱式封口，描邊仍開放；目前 editor 單 contour，不能把切規則當多 contour／布林圖形工具已完成。
5. Desktop／Web／手機驗證色盤文字輸入、取消／套用、scroll 與繪圖空間。旋轉／scope／role change 應移除未套用預覽，不保存／同步私密草稿。視覺、色差、描邊辨識、VoiceOver 與遮擋由產品負責人簽核；模型 tests 不代簽，效能最後。

物件庫分類／收合驗收（2026-10-04，人工 Pending）：

1. 開內建物件庫，首次應為「全部」且分類展開。逐一選幾何、流程、箭頭、文字、容器／泳道、圖表；菱形同屬幾何／流程，箭頭同屬幾何／箭頭，泳道可在容器與圖表找到。插入仍是既有元件，不把分類當新增自訂向量支援。
2. 在任一分類搜尋，再切收藏／最近使用，應為條件交集；無結果明確顯示，不自動取消分類。最近使用順序與去重應保留，分類切換不改收藏或最近清單。清空搜尋並選全部可回原清單。
3. 收合分類後仍顯示目前分類，展開能繼續選取。關閉／重開物件庫及冷啟 APP，分類與收合狀態應沿用；收藏／最近不受影響。這是裝置偏好，不要求另一裝置同步，也不要求搜尋內容保存。儲存失敗只標警告，不冒充重開可保存。
4. Desktop／Web／Android／iPhone 檢查窄螢幕換行、按鈕可辨識性、搜尋貼上／刪除焦點、scroll、動態島與其他面板遮擋。截圖記錄裝置、方向、選取分類與收合狀態，由產品負責人簽核；自動 6 cases 不代替真實 UI 或 VoiceOver 驗收。效能最後。

鋼筆草稿 UI 驗收（2026-10-04，人工 Pending；尚非 Node 保存）：

1. Owner／Editor 開 Command Palette，搜尋「鋼筆」或 `pen` →「鋼筆路徑草稿」。Viewer／connection failed／switching／pending recovery 不應取得可編輯入口。確認未保存、離開會丟棄、沒有正式插入／同步的警告；不以開視窗成功簽成素材或向量 Node 保存。
2. 新增模式點空白 viewBox 加直線 Anchor；按住拖曳加 mirrored incoming／outgoing。輕微 touch jitter 不應自動轉曲線。編輯模式拖 Anchor 應帶 handles 移動且保留點擊 offset，不累加位移；選中後拖 handle 應只改該 handle。viewBox 外不加新 Anchor，preview clip 不壓住 toolbar。
3. 開放／封閉、fill toggle、corner／curve、delete，逐步 Undo／Redo；完整拖曳只有一筆 history。取消 pointer／多指／resize 不提交 preview；關閉／scope change 後晚到事件不可採用。背景 Canvas Delete／Undo／Paste／游標分享不得受干擾。編輯器的 keyboard 與逐 Anchor VoiceOver 尚未交付，不誤寫通過。
4. 有 draft 或 Undo／Redo 記錄時，第一次關閉只要求丟棄，取消繼續編輯；再次明確關閉才丟棄。未保存草稿在 role／workspace scope change 消失是目前隔離策略，需記錄；完整 user-level 保存／恢復／冷啟／跨裝置仍 Pending。
5. Desktop mouse、Web、Android／iPhone touch、小螢幕、旋轉、動態島／面板遮擋、控制點可辨識性與 UI 文字由產品負責人截圖簽核。現在只有 gesture model tests／編譯，不證明 Compose pointer wiring／原生事件或實際 renderer 視覺；效能最後。

鋼筆視野補充（2026-10-04，人工 Pending）：建立曲線後放大／縮小，再拖 Anchor／handle，應仍跟隨滑鼠／手指且保持點擊 offset，不因 zoom 產生方向或距離偏移。切「平移視野」拖空白或 Anchor 都只移視野，不新增／改動 path，不增加 Undo；切回新增／編輯才改資料。多指／取消／resize 應丟棄平移 preview。將 Anchor／handle 拖出原 viewBox 或當前可見區後，按「顯示完整路徑與控制點」應找回所有合法控制點；選其 Anchor 後能再次拖 handle。重設回原 drawing area；viewport 改變不加入 Workspace 或跨端同步。Undo／Redo 只改 path，camera 保留。按鈕換行、小螢幕是否仍有足夠可編輯 preview、長文字／遮擋／可辨識性、真實觸控／VoiceOver 仍由產品負責人簽核；目前沒有 pinch zoom／逐 Anchor keyboard 或 VoiceOver 操作，不誤寫完成。效能最後。

鋼筆／自訂路徑補充（2026-10-04，完整功能與人工驗收 Pending）：domain 序列化、有限座標／上限、line／quadratic／cubic／close、handles 與本機 edit Undo／Redo 只能列核心自動證據。目前沒有完整 gesture／Node／保存入口，不簽为鋼筆可用。UI 接線後依 BACKEND_VECTOR_PATH_API_SPEC 驗 Anchor 移動／handles、開放／封閉、填色孔洞／描邊、旋轉縮放／命中與連線、撤銷／重作、複製／群組／方案保存與冷啟／雙端同步；unknown kind／版本、鎖定與撤權不可靜默丟形狀。視覺、手機觸控、遮擋與 VoiceOver 由產品負責人簽核，效能最後。

向量幾何補充：自動 cases 驗 fill holes／方向、open fill／stroke 差異、曲線 approximation、round cap／join、contour ray、旋轉／非等比 world transform 與超量停止。超量不可用矩形冒充原路徑；round renderer policy 應與 hit 同步。這些 helper 未接 production Node／selection／relation，Compose helper 編譯不表示畫面正確。ray centreline／重疊 contours 與 exact stroke boundary 尚需正式策略及 UI／產品驗收，先列 Pending，不代簽。

- 所有範圍內項目都有明確狀態。
- P0、P1 為零。
- 自動測試及必要平台建置通過。
- 功能、資料、權限、同步與重開流程通過。
- 視覺與遮擋已由產品負責人簽核，或明確標記為本次不適用。
- VoiceOver 等必要無障礙項目已有實機結論；未完成時不得誤寫為已驗收。
- 已知 P2／P3 有負責人、處置決策及後續追蹤位置。
# AI 請求設定／上下文驗收補充（2026-10-05，UI 尚未交付）

原子核對記錄保存驗收：先確認實際 adapter 與 activation。預設 Settings 必須顯示原子保存尚未啟用、沒有保存按鈕；不能僅以 Desktop 平台判定已啟用。只有已正式完成遷移及 writer 排他的 atomic adapter 才驗「重新核對並保存記錄（不套用）」：重新讀服務，不沿用舊視窗proof；保存前捕捉generation，並行writer／generation漂移必須拒絕。確認保存與重開後 exact wire／原草稿／tail／quarantine 都保留，v2 evidence完整，client sequence不變，沒有submit／fences／Apply。隔離故障測試分DataForced、Published、DirectoryForced：發布前保留舊版，發布後錯誤不當取消、需按「重新讀取封存」，只本機讀取、不自動retry或清另一pending的查詢／fence狀態。重開顯示已有核對記錄但不是當前授權；再保存若完整commit值／defaults或terminal矛盾要拒絕，不覆蓋舊證據。此APP仍未預設啟用，正式OS kill／native adapter／GUI簽核不得冒稱通過；視覺／VoiceOver由使用者判定，效能最後。

封存完整核對驗收：先查到 committed 或 fenced 才提供「核對完整記錄與最新畫布」，unknown 不提供。committed 重查receipt／完整log／fresh metadata-state；缺段、重複／超量operation、錯payload／baseVersion／commit時間／scope、撤權或 state 比此次 receipt head 舊都不得成功。fenced 只查receipt及fresh state，不假造commit記錄。取消／逾時／關窗／scope切換不得發布成功。成功明示唯讀核對完成但尚未保存 ACK、解除隔離或 Apply，archive／active pending／sequence／journal前後不變。原子保存是另一明確動作，受adapter能力門禁；此階段不是新交易授權。工程故障 fixture 用隔離測試，真服務／GUI另驗，視覺與VoiceOver由使用者簽核，效能最後。

封存查詢 UI 驗收：重建新版，在隔離 QA 畫布的既有停止重送資料上，按「檢視已停止重送的交易」。檢查中央視窗 safe area／長交易 ID／滾動清單／關閉及快捷鍵隔離；逐筆按查詢，只有 receipts POST，不能有 submit 或 fences。分別驗 committed／fenced／unknown／403／錯格式及逾時，訊息保留在正確交易；committed 明示摘要不是完整 ACK，需另按完整核對，Apply 尚未接，不清封存或隔離草稿。關窗及切換工作區／client／使用者後不得發布舊結果；刪草稿後新格式封存仍可列出，其他 scope 不露出。舊 metadata 缺失、損毀或超量應顯示需恢復，不自動清除。工程 tests 與編譯不當 GUI／實機通過；視覺、遮擋、VoiceOver 由使用者簽核，效能最後。

停止重送封存驗收：只用隔離 QA 草稿，記錄 transaction 與 build；明確停止重送後，核對完整 wire（含刪除 provenance）仍有封存、草稿隔離、沒有新增 server 操作或自動 merge。工程故障測試需驗封存分塊／manifest 或 journal quarantine 寫入丟失時 pending 保留，舊草稿分塊不被回收；禁止在正式偏好資料製造損毀。明確刪草稿仍保留 wire，但不能宣稱可恢復整份草稿。原生落盤／OS kill／多 writer／完整 Apply 尚待交付，視覺與遮擋由使用者簽核、效能最後；詳 ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

Fence保存驗收增補：確認前寫Unconfirmed失敗不得HTTP，保存後送出／取消／timeout再重開APP應顯示需查詢且不能重送原wire；unknown不清限制，terminal結果與full-log ACK分開。測marker exact scope／transaction／wire digest，損毀或矛盾不可自動清空；不用正式資料製造fault。現有InMemorySettings重建與讀寫故障測試不是OS kill/native flush證据，四平台需另附進程關閉／重開、checked commit/flush失敗及多writer證據，手機訊息/VoiceOver/遮擋由使用者簽核，效能最後。詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE；尚無損毀marker的完整quarantine修復UI。

原交易fence新增驗收：隔離QA workspace先查unknown，第一下「封鎖原請求…」僅警告＋取消、零HTTP；第二下才送fences exact transaction，需清楚說明不可撤銷、送出後取消不撤销server。檢查commit先贏採committed；unknown／timeout不宣稱fenced、不自動resend／merge，原草稿不清、同transaction当前重送disabled。換workspace／身份／原交易取消arming，晚到結果不發布錯scope；viewer不顯示可操作fence。普通MockEngine不替代真DB race，正式測試會永久封鎖QA交易，勿使用正式資料。durable proof／重開APP與完整merge尚待驗，視覺／觸控／VoiceOver由使用者簽核、效能最後，詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

原提交完整恢復追加：committed查詢結果後另按「核對完整記錄並確認恢復」。測試環境驗receipt/log身份、完整range、baseVersion、payload supplied值與fresh ACL一致時才清pending并載最新state；缺段、錯payload、unknown/fenced、撤權/403都保留原wire，不POST原交易/fences。驗數字1/1.0及server defaults不誤拒、string型別不混淆；原交易版本不能被新head取代。Settings斷電原子、跨重啟Undo及真實服務尚需專項；入口視覺／手機遮擋／VoiceOver由使用者判定，效能最後，詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

原提交查詢新增验收：依 ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE，在非重要QA workspace既有pending的恢復區按「查詢原提交結果」，分別驗committed／unknown／fenced／403／格式錯誤／scope切換。前後原交易與pending不變，不新增node、不submit/fence、不更新history或清草稿；unknown不能記為未提交，fenced當前重送disabled，committed不是完整ACK。保存安全結果及build，人工判定手機按鈕／狀態／VoiceOver／遮擋；MockEngine不代簽真實服務或GUI，效能最後。

最新補充：唯讀 AI 理解驗證入口與設定精靈現已交付，完整 cowork 設定持久化／proposal 尚未完成；本節下方「UI 尚未交付」為先前紀錄。理解驗證的正式操作以 AI_UNDERSTANDING_ACCEPTANCE 為準。

安全除錯 UI 人工驗收：先關閉舊 Desktop APP，重新以 `./gradlew :desktopApp:run` 建置啟動。AI 視窗標題下應有「尚未送出 AI 請求」與「複製安全除錯資訊」，作為新版確認，不以 source 修改／編譯成功宣稱既有視窗更新。送出後記錄 Preparing／CheckingWorkspace／Connecting／HttpResponse／Receiving 與終態；快速中間階段可由階段記錄核對。滾動長提示詞時狀態應固定可見，失敗／取消後保留，人工判定手機鍵盤、動態島、遮擋及 VoiceOver。

以測試環境驗 HTTP 拒絕、200 JSON／HTML、損毀／空 SSE、取消與成功；HTTP 200 不等於完成。前置 scope／version／snapshot 不符或畫布後端不可達時不得送 AI，WorkspaceBackend 的 HTTP 不可誤記為 AI 服務回應。每次重送重新同意，不自動重試。保存安全診斷文字至 QA form：應只有固定階段／錯誤碼、HTTP 狀態與粗略 Content-Type，不得含 token、endpoint、模型、提示詞、回應本文、原始 headers 或 signed URL。實際模型內容需另依已授權資料規範保存；自動 MockEngine 測試不代替真實 Router／GUI 簽核，效能最後。

Configured provider 已接 body／HTTP／SSE／文字adapter與必填approve callback，但沒有正式同意UI，以上狀態不改本章Pending。正式接線後另驗：完整endpoint不得把key放query/userinfo/fragment；遠端HTTPS，Local HTTP需顯示明文／LAN風險並明確同意，不能默認允許；每次request／手動retry都重新review並驗fresh scope，拒絕或取消同意時不得讀secret／發請求。Messages版本明確設定；不能用框選Local來證明地址真的在內網。

此為待正式設定與同意 UI 接線後執行的檢核，不因 serializer 自動測試通過而簽核：

1. 記錄平台／版本、provider 類型、明確協定、model 與輸出預算；截圖不得含 API Key。Local 無默認雲端 fallback。
2. PromptOnly 檢查送出摘要僅含輸入文字，沒有工作區／物件／關聯識別；Selection 檢查僅包含使用者選取內容與端點都在選取內的關聯。取消同意必須零請求。
3. 超過 APP 上限或無效設定須安全提示、零請求，不截斷、無自動 retry；記錄結果，不貼私密 raw body。
4. 回覆只可顯示文字，尚未實作 proposal 的版本不能把文字當操作執行；後續正式 proposal 需逐筆確認、權限／版本重驗與 Undo。
5. 表單／串流面板的視覺、VoiceOver、動態島與遮擋由使用者人工判定，附遮蔽敏感資訊的截圖；效能仍安排最後。不代簽、本節目前 Pending。
# 素材失敗分階段提示驗收（2026-10-05）

最新更新：正式票券origin已改為LAN，Android API37 opt-in instrumentation已驗fixture PNG經真實source／storage／worker／Node重載／sink／原生decode，不是使用者原截圖或native picker GUI。請使用者在更新後原APP重新選取原PNG，驗上傳→ready→可見Node→重開預覽並記錄完整訊息；agent不代簽畫面／VoiceOver／遮擋。詳細trace與QA asset／Node在MEDIA_LIVE_ACCEPTANCE。重跑opt-in會新增QA資料，普通instrumentation應skip live，不把runner的含skip OK當新服務成功。

APP已新增分類提示，人工UI尚待驗；請在驗收form記錄素材格式／大小、實機或模擬器、APP版本、最後階段與完整可見訊息，截圖遮蔽身份與私密內容，不附signed URL／金鑰。

1. 選檔讀取失敗、unsupported／超200MiB／invalid file應分別有安全提示；不得把raw provider錯誤或本機路徑顯示給使用者。
2. API prepare失敗與storage傳輸失敗須分開；即使傳送第一byte前連線失敗，應顯示「檔案傳輸失敗」，不是「上傳準備失敗」。提示指向公開端點設定不等於已證明根因。
3. complete可能已接受或處理階段中斷，原asset復原提示優先；先在Workspace media查同asset狀態，不能自動重上傳／新增Node／刪除ready asset。
4. localhost signed origin在Android可能不可達；正式修正由backend重新簽發可達S3_PUBLIC_ENDPOINT，不在APP改寫票券。配置更新後重新選檔，驗upload→worker ready→Node→重開預覽，原失敗截圖保留。
5. 使用者判定訊息可讀性／截斷／遮擋與VoiceOver；本輪自動分類／stage tests不當GUI、網路或人工簽核。效能最後。
# 2026-10-11 補充：declared／verified 素材驗收（待最後集中執行）

新增映射審閱案例：Ready後畫面並列本機宣告與伺服器驗證尺寸／時長，null顯示—；使用者確認後注入server實際值變更，應停住要求重新lookup／審閱，不能儲存不同於已顯示值的新mapping。切換來源、lookup失敗／撤權不得沿用舊Ready量測啟用映射；重開必須重新取得Ready回覆。人工確認文字可讀與手機不遮擋，後端預檢不代替此簽核。

本節為待執行案例，不代表新版APP已測試／建置或後端預檢可代替雙端驗收。

1. prepare宣告尺寸／duration為null；驗證後Ready回實際值。APP保存同requestId／digest／reservation，不誤報身份改變，映射使用remote實際值且不改本機原descriptor。
2. 寬高按EXIF／影片rotation的顯示方向比較；非null尺寸不符應rejected，顯示declared_metadata_mismatch中文原因，原request保留，不自動再上傳。
3. duration在max(100ms, verified×1%)邊界內／邊界上通過，超界拒絕；null不比較但仍需解碼與格式安全檢查。
4. 缺declared／verified欄位、Ready的verified=null、公開asset與verified不同、byteSize／checksum／scope／digest不同：停住、不授予映射或建立Node。非prepared帶PUT亦拒絕。
5. Ready映射保存後重開，local原檔metadata不被改寫；重新canonical審閱、完整remote核對、canvas交易與Undo／Redo。驗證未知ACK保留固定身份、失敗不自動覆寫既存mapping。
6. 產品負責人簽核拒絕原因文字、觸控／視覺／遮擋／VoiceOver；效能仍最後執行。

# 2026-10-11 補充：AI 每輪狀態（待最後集中驗收）

任務隔離案例：連續兩次任務均有第1輪，第二次在Preparing／preflight或取消時停止。第一次卡片的Completed／Failed、HTTP與response request ID不可被第二次改寫；未建立第二次card的事件只留在目前狀態區與本次安全log。不得沿用前任務consent／結果。

批准只顯示「已批准」，不能未發HTTP就顯示已送出。注入授權後preflight失敗、401／403、200空body、錯UTF-8、半截SSE與cancel，核每輪卡片保留實際typed結果；後續輪開始不抹掉上一輪。工具輪完成只代表隔離草稿步驟，不代表正式canvas套用。複製診斷只含安全摘要，不含endpoint／body／API Key；local log需有每輪terminal事件。真人Claude及Apply／重開Undo另驗，自動mock不代簽；此次源碼接線未執行驗收。

# 2026-10-12：共用背景正式寫入（待最後集中驗收）

核capability schema／operation／minWritableProtocolVersion後才啟用背景控制。Apply送完整default欄位及expectedCanvasStyleVersion；cancel零request。双端CAS只允許合法版本、duplicate不二次加版；回播權威style版本必須等於expected+1，缺欄位／錯primitive／版本跳號不得發布。驗mixed transaction rollback、Undo／Redo版本單調、unknownACK保留原交易→receipt／完整log恢復、重開本機journal。showGrid／snap／theme／viewport不得同步。本節源碼未編譯／測試，預檢不代替真人簽核，效能最後。

# 2026-10-12：圖形正式寫入（待最後集中驗收）

用D批次正式fixtures核普通／平行／自循環create→update→delete→restore與插線完整交易UndoRedo。自循環缺geometry在HTTP前拒絕；geometry清回自動必送完整auto，不送null。manual→auto／舊absence的Undo不誤判before狀態，manual數值與其他屬性仍exact。vectorPath／textPlacement建立與更新帶完整default欄位，清除null往返保留；unknownACK原wire digest核對、映射／journal重開不可丟欄位。旋轉後拖曳、曲線、孔洞與標籤遮擋由使用者簽核，本節未執行測試或跨端驗收。

# 2026-10-12：正式時序 metadata（待最後集中验收）

核E fixtures及Kotlin實際serializer：map wire為完整array，containerId唯一、graph UUID與semantic ID分離，default/null欄位完整。新增graph＋metadata同transaction成功，漏metadata的participant文字／relation修改必須全部rollback；before/CAS不符與版本跳號不得套用。巢狀loop／alt／par與self-message在replay末端驗所有bindings；整圖刪除UndoRedo包含metadata與restore_*且version單調。unknownACK用原交易receipt／完整log恢復、重開不丟sequence，不能只解析Mermaid成功就簽核。觸控／視覺／遮擋由使用者判斷，效能最後；本節新增接線尚未測試／建置。

# 2026-10-12：素材引用歷史還原（待最後集中驗收）

F回播核目前MediaNode、lock／version、原transaction／operation UUID、before／after側及完整restoredReference；不因還原missing引用給Ready或下載grant。證據只能由原fixed wire＋完整ACK＋baseline引用推導，不用inverse suffix猜ID。未知ACK與durable保存／重開及產品批准入口尚未接完，不能認列完成。

cross-kind案例：Image→Video後原圖missing，Undo必須同交易恢復assetId、mediaKind與thumbnailAssetId；目前後端F缺kind provenance，APP不得硬送或只改本機宣告成功。待後端擴充schema／fixtures後驗跨kind UndoRedo、thumbnail null與並行kind修改的拒絕；該案例不是same-kind預檢可替代，效能最後。

# 2026-10-12：雲端方案簿 HTTP 接線（待最後集中驗收）

以B fixtures核resourceVersion2、selection1..9實際Kotlin輸出、payload完整與原檔／縮圖bindings exact coverage；不得降版丟欄位。create If-None-Match:*、update/delete strong ETag與固定冪等鍵，409／412／428／410皆不自動換ID、ETag或restart分頁。驗未知ACK查原資源與durable任務重開（尚待UI/outbox接線）、同帳號兩端競爭與跨帳號owner拒絕；素材availability不作grant、materialization=false阻擋跨workspace插入。4MiB頁面／1MiB request、strictUnicode／duplicate keys／版本不支援需明確失敗，未改本機庫／畫布。本節目前僅adapter，未測試或產品驗收，效能最後。

# 2026-10-12：雲端方案簿 outbox（待最後集中验收）

驗首次HTTP前已await保存固定actor／deployment／client、mutation ID／key／body／ETag及Sending；durable失敗零HTTP。kill／cancel／丟回覆後同任務保持Sending／Unknown，重開不自動重送或换key。成功ACK後保存失敗須exact同generation確認，不用Unknown覆寫已Committed；同key不同內容／同resource未決並行操作與CAS race拒絕。terminal identity不silent GC，128筆／4MiB滿時明確停止。TTL-aware恢復、平台factory與UI尚未接，本節不當完整雲端同步已驗收；效能最後。

# 2026-10-12：方案簿雲端平台入口（待最後集中驗收）

四端核同一套outbox API的真正落盤：Desktop／Android／iOS atomic file CAS與confirm、Web await IndexedDB。不同server／user／client隔離；key必須64小寫hex且不與library Key碰撞，雲端stage不可改本機方案items／nextId／pending import。取得runtime／切換mode不發HTTP，不用default endpoint；legacy Settings沒有durable runtime時明確不可用。跨process或兩tab競爭須CAS拒絕，取消await不把enqueue當成功。UI及TTL恢復尚未接，源碼未測試／建置，本節不是已通過。

# 2026-10-12：雲端方案簿視窗（待最後集中驗收）

伺服器模式方案簿開雲端視窗：開窗零HTTP，點連線才讀capabilities／列表，點方案fresh GET、下一頁不自動restart過期cursor。改名／永久delete／上傳選定本機方案需審閱→保存固定任務→另行送出；stage零HTTP寫入、Prepared才可send，Sending／Unknown不可自動重送。16筆待辦分頁與exact UUID／ETag／body保留。scope失效／取消、capabilities等待時換user不可PUT。

雲端方案存本機identified-save只新增library項目、不插畫布／給素材grant；多source bindings本機格式不能保留時不得折疊，取消／ACK不明須用既有pending import恢復。核手機safeDrawing／IME、卡片與狀態可讀、modal時畫布鍵盤／Presence不搶輸入；使用者簽核視覺遮擋。本節尚未執行，TTL恢復與conflict UI仍未接，效能最後。

# 2026-10-12：雲端衝突讀回與新動作（待最後集中驗收）

Sending／Unknown／Rejected讀遠端現況：GET相同內容不能標Committed；404不能當刪除／fence，原任務不清除。Rejected才可明確另行審閱：原名稱套用最新遠端內容、刪最新revision、不同UUID另存；每次stage／send二次確認。競爭ETag在GET後改變時需412且不覆蓋；新名稱動作保留全部最新payload／bindings，不把舊payload配新ETag。scope撤權／其他writer改原task需停住，不確定task不得另造同resource請求。JSON4096字摘要與非receipt警示由使用者簽核可讀性；TTL原key恢復仍待接，本節未測試或正式驗收。

## Awaited 初次啟用與 lease（最後集中驗收，尚未執行）

平台真正排除所有舊／新 writer 後才啟用。驗 Preparing／missing receipt 被拒且沒有發布副作用；Published receipt 與 frozen review、draft／safety／ledger 全內容及 generation 必須精確匹配。啟用紀錄須 CAS＋durability＋完整讀回，quota／取消／identity drift 不返回 runtime，不改原 namespace／client ID。storage await 前後、submit／fence HTTP 前失效須停止，不清 pending；已 dispatch 的結果未知須以原 receipt／fence 收斂，不能以 lease 關閉作未提交證明。重複 close 舊 handle 不影響後來的 lease。未知 scope／外來 client 不自行註冊；資料前進後須正式 startup reconciliation，不能重跑初始 publication 倒回 ledger。平台 writer 排除與 wizard 尚未接通，本段是待執行案例，不是通過證據；集中測試在全業務完成後，效能最後。

## Awaited draft 分塊（最後集中驗收，尚未執行）

平台啟用後驗單筆上限以上且 domain limit 內的完整草稿、跨 chunk 的中文／emoji、pending／stopped archives 全內容保留。每 chunk confirm 前不得發布 manifest；manifest CAS 失敗／quota／取消保留舊可讀 draft，未知 ACK 以原 bundle／generation 核對，不換 ID。缺 chunk、錯 scope／key／digest／generation、字串數值、非法 UTF-8、duplicate JSON keys 皆拒；讀取中 manifest 換代須拒混讀。舊 v1 read 不寫入，同 bundle confirm 不增 generation；complete readback 必须一致。domain 6 MiB 與 manifest 上限超限明確回錯，不截內容；舊／orphan chunks 不自動清除。這些是待集中驗收案例，不是已通過證據；效能最後。

## 草稿遷移審閱介面（入口接通後集中驗收，尚未執行）

確認開啟視窗沒有 storage／network 副作用；source／plan digest 與各 scope 摘要對應 frozen review。發布與啟用必須分開確認，發布成功只能顯示發布完成，不能宣告已切換。忙碌時不可重複操作；關閉／背景／身分或 scope 變更取消，來源變更需重審，不自動覆寫或重送。失敗訊息持續可見且不包含原文／URL／Key／exception。activation 回傳後宿主未接管／接管失敗須 close lease；宿主成功接管必須原子替換 repository 並保留 lease，舊 UI dispose 不可關掉新 owner。驗小螢幕安全區、長列表捲動與確認按鈕可達性，視覺／遮擋由使用者判定。平台入口尚未接通，本段不是已完成驗收證據。

## 已前進資料 reopen（平台接通後集中驗收，尚未執行）

成功啟用後讓草稿／pending／fence／刪除證據及 ledger 正常前進，再重啟：須保留目前內容、不重新發布原初始狀態、不降 floor。缺 activation／publication receipt 或任一註冊 store、來源 review 改變、artifact generation 倒退、同 generation 換內容、ledger 低於保留 wire clientSeq 皆拒絕。原 fence wire digest 不可換、原 Fenced／Committed 不可回退；原 deletion evidence 不可刪或同版本換內容，更新須 tombstoneVersion／throughServerSeq 都前進。readDurable 要 confirm 後完整讀回；並行 writer 必須由平台真鎖排除，不以檢查次數代替。UI reopen 只確認恢復，不走初次發布；來源／scope 失效取消。尚未接平台入口，本節是待執行流程而非通過紀錄，效能最後。

## 遷移 review 保存與 restart 原方案（最後集中驗收，尚未執行）

首次發布必須原完整 review packet 耐久完成＋來源重核，才 Preparing／artifacts。中斷在 chunk／manifest／Preparing 各階段，重啟只接受原 target／planDigest／sourceDigest 綁定的 bytes，不猜最新、不卡住後換 identity。Published receipt 存在但 packet 缺失／損壞須拒啟用，不重建原方案。驗 64 MiB 上限、中文／emoji 跨 chunk、JSON duplicate keys／字串數值／UTF-8 損壞、scope或chunk hash／generation錯誤、quota與取消。不同 plan 不覆寫，同 packet 只 confirm；restart read 本身不 initialize ledger、不授權 resend／writer切換。packet 含完整私人草稿，必須平台私有 durable store，不写 log／clipboard／外部下載；舊／orphan資料不 GC。本節未執行，平台入口與真 writer lock仍待接通。

## Legacy scope catalog 完整來源（最後集中驗收，尚未執行）

legacy prepare／retain 先保存 scope 清單，再寫草稿；清單保存失敗不得開始新草稿寫入。登記但未寫 draft 可存在，不補造空 journal。驗多 workspace／不同 user或endpoint、pending／只草稿／stopped／fence／deletion 全清單；漏任一旧 wdj1／wsp1 published key 或無 locator 的舊 archive 必須拒遷移，不能只看當前畫布。未知／壞 catalog、重複scope、超限及來源改變拒，原 keys 不刪；native／new awaited runtime不回寫legacy catalog。Settings readback／兩次 source read 不是耐久或排除非配合writer的證據，必須另有平台鎖。舊 hashed identity 只能由正確原 scope 資料補回，不嘗試猜測／重設 client。未執行驗收，效能最後。

## Android server lifetime writer lease（最後集中驗收，尚未執行）

server factory 建立前取得私有 lifetime lease，兩配合process只能一個持有；同 process多factory共用handle，不重複 FileLock。鎖失敗不改 client／namespace、不刪lock／重試fallback；process終止由OS釋放，lockfile保留。owner／mode／symlink／inode更換或失效須拒絕後續 submission／realtime gate；確認 checkpoint逐筆CAS lock仍生效。這個鎖沒有排除同process多repository、未改版writer或外部Settings寫入，遷移仍須完整 cutover barrier。測顯式server模式建立失敗時UI可返回，不遺失本機畫布。尚未執行，不宣告跨端 writer exclusion完成；效能最後。

## iOS lifetime lease／factory 失敗 UI（最後集中驗收，尚未執行）

iOS兩個配合process acquire只有一個成功，nonblocking失敗不掛住UI、不改namespace／client、不刪lock；同processfactory共享descriptor。檔案owner／mode／hardlink／symlink／inode置換與fd失效拒submission／realtime，逐筆record CAS lock保留。App由本機切server時factory失敗須留原repo／畫布，返回模式切換失敗提示；startup explicitserver失敗進chooser，可明確選本機，但不能暗自消費原工作區請求或聲稱server成功。偏好保存失敗candidate關閉、原screen不dispose，安全訊息不含原例外。lifetime鎖仍不涵蓋sameprocess多repo／未配合舊writer，遷移需要額外barrier。未執行驗收，效能最後。

## 儲存與素材 runtime 交接（最後集中驗收，尚未執行）

repository factory成功而mediaRuntimeFor失敗時，不切 storageMode／active session，原畫布與原素材gateway保留，未接管candidate關閉；偏好保存失敗同規則。成功時 repository與素材runtime一對發布，不出現server畫布＋local originals gateway／錯endpoint decoder。startup media factory失敗回chooser並安全提示，不在composition拋出例外；明確重選才重建。factory回傳既有active instance時，失敗不能關閉它。不要把這個session交接當成同process寫入排除或遷移驗收。本段未執行，效能最後。

## Web server writer lease（最後集中驗收，尚未執行）

兩配合分頁／worker相同origin固定lockname只有一個寫入owner；ifAvailable未取得或缺secure context／Web Locks時明確拒連線，不初始化identity／counter、不fallback TTL／steal。same-tab多repo token獨立，一個close不釋放其他owner；取消pending或取得grant前後取消不能漏token。5秒timeout停止；pagehide釋放並使BFCache舊handle失效，不能自動重取或重送pending。開啟／refresh／prepare及workspace／member mutations須await grant，scope／identity保存前再次核。原HTTP已dispatch仍需receipt/fence收斂，失鎖不能當未提交證明。測bridge缺失、promise rejection、重複close／取消、最後owner釋放與同分頁candidate失敗；same-tab cutover／未配合舊writer另驗，不宣告全部排除完成。未執行，效能最後。

## Same-process repository writer owner（最後集中驗收，尚未執行）

同process／分頁兩個新版server factory candidate可建立，但只有一個repo可prepare成writer；第二個拒絕時不清原owner資料、不steal／TTL接管。原repo顯式close後新repo才可claim，舊handle重複close不釋放新owner。同repo並行prepare只保留同一owner；close與claim競爭不能留下closed owner。切模式保留candidate準備失敗的原screen，成功切screen時核dispose舊repo與new prepare順序；若順序未收斂，顯示錯誤而非搶占。HTTP response晚到／401／404不得讓已關閉repo重設新owner的identity／workspace，clientId讀取初始化也要guard。已送出pending不因失owner當作未提交；receipt/fence另驗。此owner不是原始Settings或舊版writer的排除證明，migration前仍須完整quiescence；本段未執行，效能最後。

## Legacy storage drain barrier（最後集中驗收，尚未執行）

讓Default中的draft／pending／ACK／stop、fence／deletion、sequence寫入停在storage操作內，關閉repo：新操作立即拒，owner／Web跨分頁lease不可在原操作finally退出前釋放，新repoclaim拒而非接管。原操作成功或拋錯都扣一次count，取消不得漏count；close／enter競爭不得留下closed owner或提早drain。nullable read只執行一次，不因null跑無guard fallback。同步Settings identity／scope catalog／counter寫入同規則；舊close重複不釋放新owner，onDrained最多一次。drain不等同server未提交／全部checkpoint或awaited store完成，已dispatch仍需receipt/fence，migration須另外完整quiescence。尚未執行，效能最後。

## Checkpoint I/O／Web交易取消 drain（最後集中驗收，尚未執行）

checkpoint native I/O開始後cancel／close，owner需等實體操作退出，期間新repoclaim不得成功。Web IDB transaction開始後取消，先abort而ticket保持到onabort／oncomplete；request／tx error不是terminal。commit已開始而abort失敗時仍等complete，Kotlin已取消不更新UI或授予ACK，但可exact讀回未知保存結果。沒有transaction的取消立即結束且晚到open不寫入。驗bridge callback取消後仍只settle一次、complete／abort／error重複事件、最後ticket釋放才Web lock release；quota／scope換手／owner close也不能提前釋放。其他Receipts／Library／AssetCatalog續用continuation保護，不因callback變更產生cancelled成功；bridge缺失要安全失敗。此處只驗storage，網路quiescence另驗；未執行，效能最後。

## Closed owner drain 後接管（最後集中驗收，尚未執行）

舊repo明確close後仍有storage I/O，新repo prepare須可取消等待而非立刻衝突或搶占；舊repo still-live則立即拒，不能自動關它。count歸零及onDrained完成才claim，callback失敗不可成功；30秒timeout保留原owner／pending／原資料，不reset或fallback。取消新waiter不影響舊操作完成，兩candidate等同owner後只有CAS贏家claim，不自動重試輸家；重複舊close不釋放新owner。驗App切screen dispose／新LaunchedEffect順序及可見連線狀態，不以此替代所有舊writer／network quiescence驗收。未執行，效能最後。

## 實際 legacy 草稿來源審閱入口（最後集中驗收，尚未執行）

legacy Settings server模式連線後打開「成員」→「審閱本機草稿儲存」。核對全scopes的操作／pending／stopped／fence／deletion counts與sequence floor、source／plan digest。opening只讀來源、不HTTP／寫草稿／初始化counter／重送／切模式；畫面須明示發布／啟用尚未接通。缺counter／client／scope catalog／舊archive locator／損壞或漂移顯示持續錯誤，不漏資料、不自動重試；「重新讀取來源」才再讀。開啟審閱阻止背景canvas鍵盤／拖曳修改，Presence按現有審閱gate處理，關閉不關閉成員面板；actor/client/workspace換手或取消不可顯示舊packet。native或已awaited配置不出現錯誤legacy入口。視覺／小螢幕安全區／遮擋由使用者判定，尚未執行本節驗收。

## 舊定位清單補充（最後集中驗收，尚未執行）

成員→草稿儲存審閱→補充原範圍／停止交易定位資料，貼原scope與transactionId JSON後按重新讀取。JSON範例只示schema，不代表自動可推算原值；不得貼API Key。合法原值須匹配現存manifest hash與原完整內容；錯／漏scope、錯archive、duplicate keys／entries、未知欄位、非string IDs、字串version、過深／過大／非法Unicode拒，原資料保持不變。修改輸入清舊review，忙碌禁止改，未重新讀取不能宣告新輸入已驗。raw輸入不保存／log／複製／送HTTP；scope換手關閉不保留到另一個actor。補充catalog不能補造counter／journal，也不授權resend／migration。成功仍須正式publisher recapture／writer exclusion另驗；本段未執行，效能最後。

## AI tool-only SSE 狀態與現有 log 判讀（最後集中驗收，尚未執行）

每個AI request獨立授權後，無文字而只有工具呼叫的回覆也應於第一個bounded UTF-8 SSE frame顯示Receiving，沿本次HTTP／response type／request ID摘要，不沿前回合。每transport只一筆初始Receiving；沒有frame不能偽造接收中。Receiving不代表JSON／tool合法或完整成功，空／未終止／provider error／UTF-8錯誤仍Failed並禁止Apply；只有完整回合及工具處理成功才Completed。diagnostic observer取消／關閉應停止後續emit，不把observer錯誤當Network或繼續讀。log只保存型別安全摘要，不保存raw SSE／prompt／Key。現存最新2026-10-09 Completed只证明原嘗試；新OpenAI／Claude與draft Apply／Undo仍需最後集中验收，不能以舊HTTP200代簽。未執行新驗收，不發未授權付費請求，效能最後。

## 素材替換原交易快照（最後集中驗收，尚未執行）

prepare需保存exact wireOperationId與完整before／after（assetId／mediaKind／thumbnailAssetId）、expectedVersion，與原pending一起耐久後才HTTP；transaction前面若改同node版本，capture依當時序列baseline核，不從現在素材猜history。錯object／version／before／wire properties／operation identity拒，seq gaps不重用。無引用改變不產生restore authority；舊pending／archive無快照不升級。ACK要exact原actor／client／operation範圍／payload才建立same-kindproof，unknown ACK／反向localOperationId suffix／相同assetID不足。跨kind before／after可保存但後端未含kind provenance時不送restore；縮圖null要完整保留。驗回應遺失／restart／stopped保留元資料、empty legacy序列化不變、1MiB pending上限錯誤不truncate。history／durableproof及最終restore ACK另驗；未執行，效能最後。

## 素材引用 commit 證據與 ACK settlement（最後集中驗收，尚未執行）

四端正常替換後，原scope／transaction／wire operation綁定完整before／after與原requestDigest、ACK範圍與version，在pending清除之前耐久保存並讀回。驗REST／WS accepted／duplicate與receipt-confirm恢復，證據寫入或confirm／readback失敗不能清pending；重試相同原交易確認相同generation1，不覆寫別的證據。同key不同內容、scope／client／actor錯、wire／ACK不符、unknown outcome、字串版本／非法UTF-8／duplicate JSON／超512KiB皆拒，不truncate／GC。legacy無snapshot不補造；cross-kind snapshot可保存但目前F不還原kind。generic未配置proof runtime的引用變更在reserve／dispatch前拒，不能落Settings。native／browser I/O納入owner drain，Web取消等terminal。history／Undo／Redo與server provenance另驗，本節未執行，效能最後。

## 素材同kind Undo／Redo 原交易還原（最後集中驗收，尚未執行）

一般引用替換成功後，history需附exact原transaction／wire operation與完整兩側和原object版本；Undo／Redo等待原commit，inverse與rebase保持identity。不因重複assetID／local inverse suffix授權；transaction children與同node多次引用需版本／順序精確綁定。將舊素材改missing後Undo與Redo走restore_media_references而非普通Ready update，expectedObjectVersion採現在CAS；actor／workspace／origin私有證據不符、node locked／reference被別人改、capability／protocol不可寫則拒且不fallback。ACK的restoredReference與新objectVersion必須exact預期，false200不能清pending；REST／WS／receipt都驗，thumbnail null保持。cross-kind backend未含kind provenance的路徑不得冒充支持；普通Ready跨kind與missing還原分開驗。restart／receipt recovery後history重建另待實作／驗收；本節只描述live-history源碼，未執行測試，效能最後。

## 一般選檔 preparation v2 契約共用與 durable reservation（待最後集中驗收）

普通 picker 與本機文件同步應輸出相同 canonical metadata／digest、精確 actor/workspace/requestId；檢查 prepare、lookup、ticket、complete 的錯誤 scope／digest／declared／phase／verified 被拒，不能回退 v1 或換 requestId。普通選檔重新選擇錯誤檔案、修改 bytes/checksum/尺寸/時長須停下；失去 HTTP 回應仍保存原身分。票券 ID 與 lease 保存後才准下一步，舊 lease 不可重新啟用，generation 與 ticket 順序不可倒退。

儲存核心驗 CAS race、confirmDurable 失敗、取消／切換 actor、損壞 UTF-8、重複／未知 JSON key、numeric string、缺少 explicit null、超過大小上限；不能偷偷覆寫、刪掉或裁剪原 request。只有確實讀回自己的 generation 與完整記錄才判定保存成功。此核心尚未接平台 runtime、discovery index、匯入 coordinator／恢復 UI；目前普通 server picker 仍 v1，不能以核心存在標記端到端驗收通過。視覺與遮擋仍由使用者簽核，全部功能完成後集中測試、效能最後。

