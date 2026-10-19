# APP 素材真實服務驗收紀錄

## 2026-10-08：GIPHY 搜尋語言與分頁一致性

- 物件庫的 GIPHY 瀏覽器改用 APP 已啟用語言，英文 en、繁體中文 zh-TW；依官方支持代碼正規化 locale，中文 script 優先於區域，unsupported fallback en。搜尋原詞原樣送出。
- 每次請求凍結語言；切換語言取消查詢／preview refresh、失效 generation 並清除結果／預覽／offset，保留 key/query，不自動多送 API。next page 不能沿用另一語言的 offset，遲到舊語言成功回應不能發佈。
- JVM GiphyClientTest 14、JS Browser 947、Wasm Browser 947 全部 0 failure/error/skip；Desktop distributable、Android APK、iOS simulator compile、Web JS/Wasm production distribution BUILD SUCCESSFUL（4m12s）。verified 成品已更新；兩份 Web 的 durable-records.js / scheme-bundle.js 與來源一致。
- MockEngine 驗實際 lang/query/offset，未驗真實 key、語言切換 GUI 或 GIPHY canvas 插入。正式 external canvas kind／backend projection／provider-ID 跨端保存及 renderer 仍未實作，不能把查詢改進當成完整 GIPHY 交付。goal 保持 active。詳見 [GIPHY 規格](GIPHY_API_INTEGRATION_SPEC.md)。

## 2026-10-08：Desktop 方案簿實際 GUI 重開與強制結束驗收

- 隔離目前 verified Desktop APP 的 user.home 與 UI preferences，保留正式 native file workspace / quick-scheme repositories；未動正式使用者資料，也未呼叫 AI 或 backend。
- 真實 GUI 保存方案 1（5 物件、4 連結、v6）後正常退出並重開，方案可載入並插入。再保存方案 2，驗 record generation=2 / nextId=3 / checksum 後僅 SIGKILL 自有 QA PID；重開仍有兩份方案，方案 2 可插入。
- 插入後画布 10→15 個節點，選單復原 15→10、重做 10→15；library checksum 不變，測試 APP 已正常關閉。詳見 [驗收記錄](DESKTOP_SCHEME_RESTART_ACCEPTANCE.md)。鍵盤快捷鍵、保存中途 kill、power loss、Web/mobile restart、跨裝置/cloud 同步未由此次驗收證明。完整 goal 保持 active。

## 2026-10-08：方案簿 awaited durable 保存接入 Web APP

- 新增 AwaitedQuickSchemeRepository 與 AwaitedPersistentQuickSchemeStore。每次從實際 durable store 讀取完整 generation/payload，沿用既有 PersistentQuickSchemeStore 的格式、schema/UTF-8/Clipboard 驗證與單調 ID；記憶體只作一次操作的工作快照，回傳前必須等待實際 CAS 與 confirmDurable，不作儲存 fallback。
- Web APP 注入 browserQuickSchemeRepository；方案簿使用獨立 BoarderLess.quick-schemes.v1 strict IndexedDB，而收據仍是 BoarderLess.bundle-receipts.v1。Kotlin 每份 scheme 上限 1 MiB、完整 library 4 MiB，收據仍保留 1 MiB 上限；不把 library 降成 receipt 限制。持續保存仍需要 browser 真正 persisted/persist grant。
- 原生端以既有同步 repository 的 awaited adapter 保留既有格式與 force/CAS；在 Dispatchers.Default 執行，不在畫布執行緒做檔案 I/O。App / WorkspaceScreen 支援選填 awaited provider；既有 native factories 仍可沿用原介面。
- UI 初始載入使用 LaunchedEffect，保存／改名／刪除／JSON 匯入／bundle 匯入與匯出 freshness 都等待操作。保存中显示提示並阻擋重複操作；保存之前凍結選取 payload、名稱、ID 與來源工作區，scope 改變取消工作，epoch 防止舊工作清掉新 busy 狀態。JSON import 取消／關閉會取消其自己的 pending 工作，uncertain save 保留 review，可再確認同一份内容。
- 素材包／JSON import 的 review retry 先比對完整方案，已有相同已保存方案便使用原 ID；storage confirmed failure 不自動重新分配 ID。刷新失敗是 library unavailable，不冒充保存成功；corrupt library 不 reset。
- 真實 IndexedDB 測試新增 6 項：strict publication → reopen UTF-8 / rename / delete / ID 不重用、put success 仍未 save ACK + cancel rollback、兩 repository 競爭不覆寫、denied／aborted transaction 不回 saved scheme、CAS 已 commit 但確認失敗的 review retry 不再分配 ID、1.4 MiB 完整 library 不截斷。grant accepted/denied 是明確 fixture，transaction 是真正 Chrome strict IDB，並非實際 browser permission/eviction/APP process restart 證據。
- 最終版本 JS Browser 945 / Wasm Browser 945 與 JVM focused 18 全數通過，0 failure/error/skip；Web JS/Wasm production distribution、Desktop distributable、Android APK、iOS simulator compile 最終 BUILD SUCCESSFUL（4m13s）。全部 verified 成品已更新，Web durable-records.js 與來源位元組一致性已確認。未刪除旧 Settings 內容，也未自動 migration／合併。新 Web library 初始沒有舊 Settings 的方案。
- 六主線還缺實際裝置之間的完整素材交付 GUI、真實持續保存 grant／跨 APP restart、cloud quick-scheme UUID/revision/outbox/conflict 同步，以及 formal collaboration / shared background / GIPHY / AI 真實成功等原目標。goal 保持 active。


## 2026-10-08：Web 素材包暫存租約與下載確認回收

- OPFS stage 使用獨立 boarderless-bundle-stages-v2 目錄，避免掃到沒有租約協定的舊版 root 檔案。每個隨機 UUID 檔在建立前取得 origin-scoped Web Lock，持有到自己的刪除完成；清理以 ifAvailable 嘗試，不排隊、不 steal、不遞迴刪除，略過非協定檔名與 directory。
- 新的 pick/create 會回收已沒有租約且未發佈下載的檔案。分頁卸載／worker termination 的租約釋放由 browser LockManager 處理，不用時間或 heartbeat 猜測活躍狀態。
- 分頁關閉不能證明 browser download 已結束。匯出在 anchor dispatch 前保存 .download 標記；自動掃描保留有標記的檔案，可能已 dispatch 的失敗／取消也不 abort 刪除其 backing file。匯出對話框有「我已確認本網站的下載完成，清理閒置暫存」，清理本視窗已取得的目的地與無活躍租約的舊下載；別的分頁活躍檔不會被清理。
- 清理 ACK 等到實際 removeEntry 完成；刪除失敗保留 handle/lease，可明確重試相同 stage，不宣稱已清理。取消／錯誤 callback 仍會返回有限分類，不因 cleanup 失敗而掛住。
- 新增真實 Worker + Web Locks + OPFS 測試：活躍 worker lease 阻擋掃除、worker terminate 後鎖實際釋放且未發佈檔可回收、有 .download 標記的 idle 檔自動保留而明確 ACK 才清理、非協定 sentinel 與 matching-name directory 保留。既有 33 MiB 原始素材回圈加入重複下載確認；新增實際 removeEntry 故障注入，未 ACK 且重試不增加下載次數。
- JS Browser 939 / Wasm Browser 939（各含 6 項素材包測試）已通過，0 failure/error/skip。多平台平行編譯曾因 Kotlin daemon 3 GiB heap 內並行 IR/link 工作造成 GC overhead OOM；改用 --no-parallel --max-workers=1 重跑通過瀏覽器測試。最終包含發佈後 abort 保留 backing file 的驗證仍全數 pass；JS/Wasm production distribution、shared JVM / Android / iOS simulator compile 最終 BUILD SUCCESSFUL（3m16s）。Web verified/js 與 verified/wasmJs 已更新，script 與來源位元組一致且 index 引用核對通過。GUI 下載保存、人為確認時序與真正 browser process kill 仍未驗，worker termination 不等同 OS 電源中斷；Web 方案簿 awaited durable 保存及其餘六主線仍未完成。goal 保持 active。
- 原理：https://www.w3.org/TR/web-locks/ （origin lock 排他、ifAvailable、document unload/agent termination）。


## 2026-10-08：Web 完整原檔方案素材包入口

- Web APP 已注入 browserSchemeBundleRuntime，接上既有完整 QuickSchemeBundleCodec 與 browserSchemeBundleReceipts；不是縮圖或只有 JSON 的方案檔。
- JS/Wasm 共用 browser interop，選檔後以 1 MiB 分段複製至隨機命名的私有 OPFS 快照，讀取位置/長度與固定 file size 經檢查。匯出也以私有磁碟 stage 分段 append，不把整個最大 1 GiB bundle 存成 Kotlin byte-array list。
- 每個 await 前後檢查取消、工作區 scope 與頁面 visibility；native/Kotlin Mutex 避免交錯讀寫。取消或失敗清理自己取得的 stage/snapshot；遲到 callback、continuation resume 途中取消也釋放自有 handle。從不刪除或修改使用者選的原檔。
- 發佈只能嘗試一次，完整 codec SHA 校驗後才交給瀏覽器下載；UI 使用「下載含原檔方案素材包」與「已要求下載」，不把 dispatch 當成 OS 已保存。輸出檔名含 UUID。下載 backing stage 暫時保留，不能在固定短時間後刪除仍可能被大型下載讀取的檔案。
- 真實 Chrome OPFS / File 測試 4 項：33 MiB 原始素材 export → download File → private snapshot → codec review SHA；建立快照後再改動選取的磁碟檔仍讀到完整原始內容；實際 writer 返回前撤權或取消不下載且清理 stage；選檔複製中取消清理 late snapshot，原 selected File 仍可再讀。OS picker/anchor click 被 fixture 接管，因此不是人工下載保存 GUI 的證據。
- JS Browser 937 / Wasm Browser 937 全數通過，0 failure/error/skip。Web JS/Wasm production distribution 最終建置成功，已更新 webApp/build/verified/js 與 wasmJs；index.html 引用與 scheme-bundle.js / durable-records.js 位元組一致性已核對。
- 下一步：已發佈下載 stage 與異常關閉 stage 的安全回收（需區別活躍 tab，不能掃刪別人使用中的檔案）、Web 方案簿 awaited durable 保存；真實 browser persist grant、GUI 選檔/下載與跨裝置完整鏈仍需驗收。完整六主線 goal active，未宣稱全部交付。
- 原理：https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system 。OPFS 是受 browser quota 管理的私有 origin storage，並非使用者下載資料夾。


## 2026-10-08：Web strict IndexedDB 收據保存與 awaited 授權邊界

- SchemeBundleReceipts 的 beforeComplete / ready 改為 suspend；PersistentSchemeBundleImportReceipts 使用 AwaitedDurableLocalRecordStore。Desktop/Android/iOS 以現有同步 durable store 的 awaited adapter 保留原檔案格式與 force/CAS 語意，不新增 migration。
- Web 新增 BrowserDurableLocalRecordStore 與 browserSchemeBundleReceipts factory、JS/Wasm bridge、durable-records.js。需要 secure context 和實際 StorageManager persisted/persist grant，不以 volatile memory/localStorage 或 relaxed transaction 假裝 ACK；IndexedDB records 的 CAS 比較完整已驗證舊 document，確認 strict transaction 並且只在 transaction complete 回報。
- Kotlin 檢查嚴格 JSON/UTF-8、generation+payload SHA256 和安全整數 generation；損壞、storage denial、transaction abort／stale CAS 不 reset。confirmDurable 也等待 strict 同內容重新發佈。取消會 abort 未提交 transaction，遲到 open connection 關閉，沒有自動 retry。
- materialize 現在於 awaited receipt recovery 之後重新檢查 fresh authority，且在 pending receipt 發佈之後、server confirm 之前再檢查；避免等待 IDB／權限提示期間撤權或版本改變卻繼續 prepare/confirm。共用測試驗「發佈未 ACK → confirm=0」、「保存失敗 → confirm=0」以及 read/publish 邊界撤權。
- 最終驗證：JS Browser 932 與 Wasm Browser 932 全數通過、無 skip（各包含 4 項真正 strict IDB fixture），JVM focused 11、iOS native 14 都 pass；Android 收據 3 項以 adb -s emulator-5560 明確安裝/執行 AndroidJUnitRunner，輸出 OK (3 tests)。未對同時在線的 emulator-5554 安裝或關閉。Android APP APK、Desktop distributable 與跨端編譯通過，兩個 verified 成品更新。
- Browser fixture 的持續保存 grant 為明確 accepted/denied fixture（沒有假稱已驗真實 browser persist permission）；DB / transactions / request success / complete / rollback 都使用實際 Chrome API。驗 utf8 / connection reopen、put success 先於 ACK、取消已 put 未 commit、兩 connection 競爭只一成功、corrupt 不 reset。這些不是 browser eviction／power loss／正式 APP restart 的證據。
- Web 完整素材包 Source/Destination/File picker 和 APP runtime 仍未接好；factory 現在可供後續入口使用，不能宣称整條 Web 匯入可用了。Web 快速方案簿亦未切換 IndexedDB。完整六主線 goal 保持 active。
- 原理依據：https://www.w3.org/TR/IndexedDB/ 、https://developer.chrome.com/blog/indexeddb-durability-mode-now-defaults-to-relaxed 、https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/persist 。


## 2026-10-08：iOS 原始素材包反向匯入與方案簿 durable 保存

- iOS 的同一個 IosDraftImportPicker 現在支援 JSON 草稿與 UTTypeData 素材包選取（互斥 pending / 關閉及動畫 continuation 沿用原邏輯）。MainViewController 組合 export/import runtime 與 scope-bound receipt factory，APP 的 .blscheme 匯入入口已接上。
- IosSchemeBundleSource 只在讀取時持有 security-scoped grant，並經 NSFileCoordinator 以最多 1 MiB chunk 複製成自有 0600 私有快照。只接受 regular file、45 bytes 至 BLQS0001 的 1 GiB 協定上限，拒絕短檔／增長／截斷；後續範圍審閱／上傳讀取私有快照，取消／scope change 清理自有副本，不修改原始 Files 文件。
- 新增 IosAtomicLocalRecordStore：0700 owned leaf、0600 regular records/lock、process NSRecursiveLock + flock、generation CAS、嚴格 UTF-8／strict JSON／SHA256，force 檔案、rename、force 目錄、回讀後 ACK。不存在的 key 可以建立；損壞、symlink、錯誤權限不自動 reset。framework Library parent canonicalize，owned leaf symlink 不解參照。
- 素材收據使用 PersistentSchemeBundleImportReceipts 保存 user/client/workspace/bundleDigest 和 pending destination IDs；只有原本 ID 的 GET Ready／owner／素材 metadata 相符才可復用。Pending 未解決時仍拒絕自動配置新 ID；尚未實作完成 pending server upload 的自動續傳。
- iOS App → WorkspaceScreen 同時注入 IosFileQuickSchemeStore，共用 Desktop/Android 的單一 library v1 與 nextId 方案驗證；原本 Settings 資料不搬移／刪除，新方案簿初次為空。
- 原生證據：最新 test XML timestamp 2026-10-08T04:06:23–26Z（台灣 12:06），iOS simulator 14 項全部 pass、無 skip（Persistence 6、Destination 4、CanvasExport 4）。涵蓋 33 MiB 完整 bundle codec export → native private snapshot → review checksum，CAS stale/corrupt/modes/symlink，pending ID 重開與 Ready 正確復用，方案改名／刪除／ID 重新讀回，copy 中途 cancellation/scope 失敗清理與原始檔不變。ios arm64 及 simulator production compile 通過。
- 實際 Files picker／iCloud provider GUI、APP process kill/power loss、Desktop↔iPhone 實際檔案轉移及正式後端自動同步仍未驗收；沒有產出簽署真機 IPA。完整六主線 goal 保持 active。AI log 最新仍為 11:15 的舊 reasoning/tools 拒絕，尚無 none 修正後的真實成功記錄。


## 2026-10-08：iOS 素材方案包串流匯出

- 新增 IosSchemeBundleDestination；iOS MainViewController 透過現有 IosCanvasExportPicker 的 schemeBundleRuntime 將 .blscheme 匯出入口注入 APP。Files 選取資料夾時尚不寫入素材；取消／生命週期 close 沿用同一個 native picker 管理，避免 PNG/PDF 與 bundle 同時開兩個系統視窗。
- 素材以最多 1 MiB chunk 直接串流到自有 0600 暫存檔，採 BLQS0001 協定的 1 GiB bundle 上限；沒有沿用 canvas 的 32 MiB／記憶體 chunks 限制，沒有解碼重編碼或縮小原始素材。完成後 force 私有檔案，在 security-scoped folder 內透過 NSFileCoordinator、同目錄 stage、fsync、RENAME_EXCL、目錄 fsync 交付唯一檔名。
- 每個寫入／複製及發佈前邊界檢查取消和工作區範圍；owned-only 暫存清理，發佈後若 durability 回報失敗保留 final 檔案；abort 不刪除 final 或既有使用者檔案。
- 驗證：iOS arm64 與 Simulator arm64 編譯通過；指定 boot 的 iPhone 18 Pro simulator 上 IosSchemeBundleDestinationTest 4 項和既有 IosCanvasExportTest 4 項全數通過、無跳過。33 MiB opaque original fixture 由完整 codec 匯出後逐 chunk review，確認 scheme/asset checksum 和原始 byte size；另驗證實際 coroutine 取消、scope change、exclusive collision 和 private mode。這是原生檔案／Foundation coordinator 證據，不是圖片解碼／Files picker 或 iCloud provider GUI 驗收。測試後已關閉本次啟動的 simulator；未 erase 使用者裝置。
- 尚未完成 iOS 反向匯入、安全 scope-bound durable 收據、正式 APP Files GUI 路徑、實體 iPhone／外部 provider QA；這輪没有產出可安裝真機 IPA。完整素材跨端主線與六主線 goal 都仍未完成。
- AI 本機 log 最新仍是台灣時間 11:15 的 reasoning/tools 400，沒有修正後 none 請求的真實成功結果；未自行讀取 Key 或送出付費請求。


2026-10-07 提交後完整隔離驗證90288成功1m44s：shared/jvmTest: tests=985, failures=0, errors=0, skips=6；shared/jsBrowserTest: tests=880, failures=0, errors=0, skips=0；shared/wasmJsBrowserTest: tests=880, failures=0, errors=0, skips=0；desktopApp/test: tests=48, failures=0, errors=0, skips=0。iOS arm64／Android main compile／Desktop distributable通過，ready bundle已更新且含ShellHint classes、無QA prefs fixture；shared SHA256 c8bb6a86882fb2686cabf8437024246ad29cfb6af4b8bfab3fca6c078726ad03。此輪包含目前未提交的鋼筆提示，不宣稱觸控長按 GUI 通過。git diff --check通過。


2026-10-07 Desktop 含原檔方案 GUI 已接線與隔離驗收：快速方案新增含原檔 bundle 匯入／匯出入口；native file picker、strict review、每次明確確認、fresh user/client/workspace/full snapshot fence、durable original receipts 與方案保存已串接。匯入只保存 verified originals＋方案，插入畫布仍是另一個明確動作；export 新檔不覆蓋既有檔。Dialog scope 固定於開啟時，關閉取消 owned job／snapshot；uncertain library save 以 exact normalized name/payload/schema/source workspace 沿用同一方案，避免重建 ID。新增 common review/library-save 2 個 cases。

隔離 Desktop GUI 使用自有 QA home／memory prefs，未讀正式帳號或 API key：原 fixture 匯入前與 review 階段零原檔、未確認不保存；確認保存後 1 份 Ready 原檔／2 個方案，畫布與 baseline 完全相同。匯出容器 footer／原檔 SHA256 通過，81-byte PNG 與原 fixture 完全一致。另行插入後畫布增加 1 個 MediaNode（共15個物件，3份既有時序圖保留）；App 重開仍可見圖片與兩個方案。重複匯入同一 fixture 沿用 receipt，仍只1份 original／2個方案，canvas record 與插入後備份 byte-exact 相同。QA 已正常退出。這是 Desktop 小型 PNG 的實際入口驗收，未代替大檔／GIF／影片、四端實體裝置／自動 cloud sync／正式協作驗收；其他端 bundle runtime 尚未接線。取消在 beforeComplete receipt 後與 complete 之間的 missing-pending 處理仍需明確恢復入口。

另 agent 的27個鋼筆按鈕提示已存在4個未提交檔案；核對 hover／long-press 的來源實作，但當前 GUI 驗收使用先前已建置 bundle，未宣稱新增提示懸停／手機長按已親測。5個分組 commit 已在本機 history，另有4428380設定提交，未 push。iOS log 接線也已存在：bash -n 通過；抽取實際 Xcode Run Script 以 mocked Gradle 驗普通 stdout/stderr、附加中文／emoji log、failure exit23保留、IDE override跳過皆通過；package 採用的 tail -n +1 -F 實際能在producer結束前輸出追加 task 行。不是 Actions／完整 xcodebuild／簽章驗收。安全 AI log 仍不存在，最後真實 probe 仍 HTTP401；六條主線 goal 保持 active。


2026-10-07 素材隨方案交付核心：新增 QuickSchemeBundleCodec 的 BLQS0001 串流容器（12-byte magic/manifest-length header＋strict UTF-8 manifest＋declared originals＋whole-container SHA256 footer）。Manifest帶既有 QuickScheme transfer definition與唯一 referenced asset descriptors，不帶signed URL／credential／ownerId／device path；source asset IDs限制plain identifier。引用集合須與media original及thumbnail IDs exact相同、thumbnail必須Image、每mediaKind與mediaType一致；最多64個原檔、每檔沿既有200MiB、容器1GiB、manifest4MiB、read/write chunk≤1MiB。不用Base64或把全部影片讀進memory；同一asset多個MediaNode只交付一份。圖片／GIF／影片都是workspace stored originals，GIPHY provider display資料不當作可快取原檔或暗中下載。

Export核明確來源workspace／fresh scheme與scope，逐asset authorize Ready metadata並核source ID/workspace，每原檔驗exact bytes／SHA256再全container checksum，最後fresh scheme和scope仍相同才commit；失敗abort只清owned partial。Review先strict JSON／Unicode／format/version／refs／metadata／limits，再stream驗全部asset SHA及wholefooter、完整EOF／no trailing，零upload/library/canvas mutation；failure releasesnapshot。Materialize要求caller綁exact review至明確confirmation，先重驗同一immutable bundle，逐次fresh editor/user/client/workspace/version/seq/full snapshot authority，沿原AssetImportCoordinator prepare/upload/confirm/Ready／uncertain recovery。beforeComplete要求hash＆完整讀取並先記目的ID，readImported／recordReady是必備receipt邊界；Ready全部驗原metadata及目的ownership、distinct目的IDs才回完整remapped scheme，original與thumbnail引用都重配，sourceWorkspaceId改目的workspace。尚不自行save library／insert canvas，caller必須再核scope後保存。

JVM新增DesktopSchemeBundleSource選取檔→private immutable snapshot（copy≤1MiB、sourcefileKey/size/mtime及EOF核對），刪／改picker原檔不影響snapshot；release只清owned files。DesktopSchemeBundleDestination force完整partial後以createLink atomically發布NEW檔名，不overwrite／replace舊檔或symlink；publish後directory-force uncertainty時abort也不刪finalfile。當前Posix/macOS adapter，未宣稱Windows／Android／iOS／browser picker已完成。

DesktopSchemeBundleImportReceipts沿既有DesktopAtomicDraftStore，scope=user/client/workspace＋bundleDigest，source descriptor→destinationId pending-before-complete／Ready，private exact-CAS／idempotentconfirm；重開先getAsset核同ID／Ready／full metadata，不將pending/missing/failed lookup當成可重新上傳。expected descriptor或destinationID改變拒絕，不reset損檔。首compile abort推導Unit?不符合interface已加Unit；native測試抓到key不該带prefix及新檔CAS應null而非0，改正並把測試核exact intentional interruption訊息，避免其他失敗冒充Ready後中斷。

新增common7cases＋JVM2cases：2MiB以上原檔stream（1MiB上限、short reads）、shared GIF media dedup/remap／receipt重試不prepare、corrupt original/footer/truncated/trailing拒絕、wrongworkspace/changedscheme/checksum export零publish、scope取消release/abort、撤權/改snapshot/改檔後零prepare、Ready後receipt失敗保留pendingID及原方案；native真正PNG來源import→原檔bundle→刪picker來源→另一native repository/materialize→Ready後故障→receipt/repository重開復用同ID→scheme保存／canvas插入／repo重開→verified download及ImageIO／exact original bytes一致、只一目的asset；file writer新檔visibility、no overwrite／snapshot獨立／abort無partial漏檔。

這是完整串流codec／原生檔案與receipt核心，尚未接GUI bundle picker／匯出匯入確認入口，其他三端binary adapters及physical跨裝置交付也未完成；不能用headless原生驗證宣稱使用者現在已可從APP跨裝置交付媒體方案。六主線goal維持active；不替代自動cloud scheme sync／正式協作／shared背景同步／GIPHY canvas provider交付。未動backend／.env／migration；actual user-dir AI log仍不存在，最後401未排除。

最終隔離 build 8701 成功1m23s：JVM982（6既有skip）、JS877、Wasm877、Desktop48全部0failures/errors；iOS arm64／Android main compile／Desktop distributable通過。ready bundle已更新核 codec/review/JVM source/destination/receipt classes，shared SHA256 3115d54cb51a519d2b68edba8a5a27b7aef2a41c3d68782447e0e6535ed2926d；无QA prefs fixture。這次沒有GUI新入口或GUI驗收，不把包中class存在當作APP媒體bundle交付已可用。git diff --check通過。

2026-10-06 本機素材交付更新（覆蓋下方歷史的「local media gateway停用」）：新增DesktopFileAssetGateway並接到Desktop預設本機repository、WorkspaceScreen匯入與Desktop圖片／GIF／影片讀取。素材位於~/.boarderless-storage/local/assets，獨立private原始檔與atomic metadata；每次chunk≤1MiB、上限200MiB，不把原始檔或picker/cache路徑塞入workspace JSON。先完整驗size/checksum、force原始檔、atomic move與directory force，才發布Ready metadata。workspace只保存asset ID；重開素材庫可查詢／重用，刪掉picker原始檔仍能讀回。查詢檢查本機session user/client/workspace；下載經既有AssetDownloadCoordinator與sink checksum驗證，損壞／跨畫布／截斷不能出預覽。原始檔已落盤但Ready尚未發布的中斷，可在明確metadata refresh時驗證並settle同一ID，不重上傳；沒有完整原始檔的Pending不假稱Ready。本機進度使用保存／驗證本機檔案文字，server mode仍用Backend gateway。

DesktopFileAssetGatewayTest4cases：真正PNG來源→import coordinator→media node→retainDraft→新repo重開→刪來源→verified download sink→ImageIO讀回；跨workspace／foreignclient／path ID／checksum corruption拒絕；changed/truncated source取消pending且不留original/part；upload完成而confirm未執行的restart→明確getAsset恢復同一ID。最終34087隔離build成功74s：JVM880（6既有skip）、JS799、Wasm799、Desktop46均0failure/error；iOS arm64與Android main僅compile通過、Desktop新包成功並複製desktopApp/build/verified/BoarderLess.app，直接核含local repository／media gateway classes。尚未GUI操作驗收、未宣稱四端本機素材或跨裝置交付完成；GIPHY沿現有provider display路徑，不當作本機上傳素材。沒有新user-dir AI probe log，最後真Router證據仍HTTP401；完整六主線goal active，不重新引入測試資料遷移前置。


## 最新 iOS 照片／影片來源入口（2026-10-05）

使用者確認iOS只有Files選項，原IosMediaPicker確實只接UIDocumentPicker。現在匯入先顯示中央來源選擇「照片與影片／檔案／取消」，Photos走PHPickerViewController、single selection、image/video filter與Compatible representation；Files保留原picker與格式。UIKit呈現固定Main，來源menu dismiss完成才開下一個picker；scope取消／dispose關閉owned panel，不resume晚到結果。

Photos不查PHAsset／整本照片圖庫、不請求廣泛Photos權限。選取後NSItemProvider file representation在completion內先核對1..200MiB並複製到UUID私有暫存，避免系統在callback返回時刪URL；再交既有IosFileAssetTransferSource immutable snapshot／SHA256／upload，finally刪中介副本，cancel取消NSProgress，resume取消清owned檔，120s export timeout安全失敗。正式支援provider明確提供GIF／PNG／JPEG／WebP／MP4／WebM，GIF優先不變成靜態JPEG。Compatible請求不是所有HEIC／MOV轉碼保證：未提供支援representation時明確unsupported，不偽裝副檔名／mediaType，也沒有新增APP自行轉碼或Live Photo動態支援。

依 [Apple Photos Picker](https://developer.apple.com/documentation/PhotoKit/selecting-photos-and-videos-in-ios) 與 [NSItemProvider暫存生命週期](https://developer.apple.com/documentation/foundation/nsitemprovider/loadfilerepresentation%28fortypeidentifier%3Acompletionhandler%3A%29) 接線。31981 simulator main首次compile15s成功；15679最後iOS Simulator測試与iosArm64 main compile成功55s，IosPhotoSelectionTest4cases／0fail／0error／0skip：single compatible config、GIF優先／unknown格式不重新標記、provider副本在原檔刪除後可hash/read並清兩份自有副本、empty/missing拒絕無leak。不是PHPicker GUI／iCloud／真實callback取消／硬體upload驗收，亦非Xcode完整APP build；新增boot simulator已恢復shutdown。

產品需重新build/install後驗兩來源／取消再開、PNG/JPEG／動畫GIF／MP4、HEIC/MOV Compatible實際representation及unsupported提示、iCloud離線／export等待取消、背景／換workspace取消、Node保存重開；不得刪user Photos原件。視覺／VoiceOver／safearea遮擋由使用者簽核，效能最後。未修改backend／env／keys，原六主線保持。

## Web 選檔誤判取消修正（2026-10-05）

使用者回報Web選完檔案顯示已取消。production media-import.js 原先在window focus後250ms檢查空files並finish(null)，會早於本機／雲端provider的change，移除input並丟掉後續選擇。新增 deterministic delayed-change case在原程式下確實失敗（9cases中1fail，8pass）；這證明競態存在，不宣稱已重現使用者瀏覽器的完整網路上傳。

移除focus推斷取消，改以input change／cancel或APP explicit cancel完成；清除input listeners／value與deadline，late events不重入。無cancel事件的舊瀏覽器保持待選，使用者可按APP取消；120秒deadline回明確選擇錯誤，不冒充使用者取消。append/click錯誤用安全常數。未改上傳協定、storage／backend、Kotlin來源hash與release。

84426 JS／Wasm Karma與兩種Web development webpack最後成功1m38s，BrowserMediaImportRuntimeTest兩目標各3cases／0fail／0error／0skip。兩種processedResources的media-import.js與修正source SHA-256一致，確認資源已進建置。Karma使用fixture選檔，不當使用者原生picker／實際signed PUT已通過；新focus競態由上述Node case驗證。未改其他平台，未啟動或修改backend、未讀env／keys；完整六項仍未完成。

`node --test webApp/tests/*.test.mjs`20cases／0fail／0skip通過，包含focus先到／延遲change、多次focus、native及explicit取消、deadline／late event、原有binary chunks／File upload／headers／abort／其他Web fixtures；git diff --check通過。JS／Wasm Karma與entry建置結果後補。需重啟／重建Web dev服務並重新載入新media-import.js，再以原瀏覽器重验選檔→prepare→upload→ready；視覺與完整實際storage上傳不由Node fixture代簽。

## iPhone 建置 API 設定修正（2026-10-05）

83922 唯讀 `xcodebuild -showBuildSettings` 證實原 iOS 配置為 `http://127.0.0.1:3000`；Info.plist 的 BoarderLessBackendURL 經 Xcode 展開後優先於 Kotlin fallback，因此實機會連手機自身，而不是目前LAN服務。這是可證實的實機預設配置缺口，但使用者截圖的安裝版本／實機或模擬器／檔案來源尚未確認，不能宣稱已找到該截圖唯一根因。

Config.xcconfig 新增 `BOARDERLESS_BACKEND_HOST[sdk=iphoneos*]=192.168.68.65`，保留 simulator loopback、現有 Info.plist／Local Network 說明與 ATS 設定，不開 NSAllowsArbitraryLoads、不改 backend 或 signed tickets。此位址是目前已驗證的開發LAN服務（health status=ok／database=up），不是 production預設；換網路需改device設定或build override，release部署需明確HTTPS服務。README補上重新建置／安裝與storage可達要求。

新增 `iosApp/tests/backend-config.test.mjs`，直接讀 Xcode 對兩種SDK的Debug解析及plutil讀source，非手寫xcconfig parser：simulator127.0.0.1／device192.168.68.65、scheme/port、Info URL參數、既有localnetwork permission與未開arbitrary loads、explicit HTTPS override優先。69022首輪2cases通過；加generic destination避免無關裝置inventory後78986最後2cases／0fail／0skip通過2.02s，git diff --check通過。只測建置配置，不當完整APP build／實機安裝、權限或素材上傳通過；未啟動模擬器、未覆蓋任何已安裝APP。

重跑配置驗證：

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer node --test iosApp/tests/backend-config.test.mjs
```

下一個產品驗收：使用者重新建置並裝到iPhone，同Wi-Fi且允許區域網路；以小PNG先驗prepare→upload→worker ready→Node→重開，記錄APP build、裝置、檔案来源／格式及最新分階段提示。若仍失敗，依實際stage与console的安全exception class追查；不得記錄API Key／signed query／私有檔案內容。不同Wi-Fi、storage9000不可達、檔案供應者與格式問題仍需各自證據。

## Android 原生載入唯讀驗證（2026-10-05）

`AndroidMediaNativeLoadLiveTest.retainedAssetsDownloadDecodePrepareAndRelease` 在 emulator-5554／API37 實際 instrumentation 通過：7001 `OK (1 test)`、case code0、無 skip，1.379s；Device APK 37369 建置成功10s。只更新既有獨立 `cg.creamgod.boarderless.shared.test` QA APK，不覆蓋使用者 APP。

使用既有六格式 QA workspace `c47bf81e-2a79-4fb0-aea6-2d36d47ffac3`、owner `60e37ef6-1e7a-4066-898b-c1878ae84d33`，GET-only refresh／正式 content ticket／signed GET／Android checksum sink；不 prepare、PUT、complete、提交 operation 或建立 identity。JPEG／WebP 與 GIF／MP4／WebM 的三張正式 poster 經 production AndroidAssetPreviewLoader 原生解碼，尺寸與 metadata 一致；GIF 全部影格48×32、總延遲1000ms；MP4／WebM 經 production loadAndroidVideo／MediaPlayer.prepareAsync，64×48、duration>0、muted、不失敗，pause／mute／seek command 後 idempotent release。各 loader 移除自己的暫存檔，fixture unique cache directory 最後為空並刪除；前後 workspace／version／serverSeq 相同。沒有新增或刪除 server 資料。

影片沒有 attach surface：不當畫面渲染、聲音、實際連續播放或 seek callback／實際落點驗收。GIF 解碼不是播放節奏或視覺簽核。未 opt-in 的另一普通 instrumentation 明確 `AssumptionViolatedException`／case code-4 skip，即使 runner 顯示 OK，也不算 live 通過。只改 AndroidDeviceTest source／文件，未改 production loaders；不重跑未變動的四平台 production suite。

重跑（唯讀、硬性核對上列 scope／base；scope不存在即失敗，不建立替代）：

```sh
/Users/wangfuxian/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r \
  -e class cg.creamgod.boarderless.AndroidMediaNativeLoadLiveTest \
  -e boarderlessMediaReadLive true \
  -e boarderlessMediaBase http://192.168.68.65:3000 \
  -e boarderlessMediaUser 60e37ef6-1e7a-4066-898b-c1878ae84d33 \
  -e boarderlessMediaWorkspace c47bf81e-2a79-4fb0-aea6-2d36d47ffac3 \
  cg.creamgod.boarderless.shared.test/androidx.test.runner.AndroidJUnitRunner
```

使用者最新 iPhone 畫面僅提供 generic import failure toast，未確認實機／模擬器、來源格式與 APP build；Mac readiness=ok 不能證明手機可連 storage9000，Android 此 case 不解除 iOS 問題。待補環境後重現 iOS picker→source→upload，不能猜成 ATS／權限／格式原因。視覺／VoiceOver／遮擋仍由使用者簽核，效能最後。

## Android 裝置實際 LAN 上傳驗證（2026-10-05）

本輪唯讀正式 content directive 已從 localhost:9000 更新成 `http://192.168.68.65:9000`，因此回到素材優先主線，不修改AI介面。後端配置由維護者更新；APP沒有修改／重啟backend，也沒有改寫signed URL。

新增 `AndroidMediaBackendLiveTest.contentUriUploadNodeReloadAndVerifiedNativeSink`：emulator-5554、API37 真正 instrumentation／Android HTTP engine，與測試APK私有 read-only ContentProvider。重用已保留的 QA workspace `1883808a-7a58-4062-9556-9de88bd55e89`／owner `8d9cac0b-a741-447c-9f37-97de230de00d`，明確arguments opt-in才允許server寫入；不讀／改使用者APP prefs、不使用原截圖、不新增identity。這段是本輪例外重用QA scope，早期「每輪新建scope」是各JVM案例歷史範圍。

fixture為backend manifest的PNG（442bytes、64×48、SHA256 `15b8da68f777d7caaab816ede7dc7364cc979cc9bffaab4e93d39e24b624d49d`）。先驗fixture hash，透過真正 ContentResolver query／getType／openInputStream 與 `AndroidUriAssetTransferSource` 產生immutable snapshot／原生bounds，再走正式 prepare／signed PUT／complete／worker ready；Uploading(0)與Ready有斷言。

接著正式CreateObjects→accepted→refresh，新的repository／client再GET載入同Node；正式content ticket斷言LAN host／9000，AndroidFileAssetDownloadSink逐chunk下載／SHA256驗證／publish、逐byte與fixture相同、只有1個ready檔，BitmapFactory原生解碼64×48。不是MockEngine，不是JVM代跑；未驗native picker UI、使用者原200214bytes截圖、OS／Activity重啟、其他五格式Android播放或完整双人雙裝置。

證據：2904首次Device APK compile失敗6s（MatrixCursor intersection／WorkspaceSession參數／reference欄位），已修；17126建置成功10s。APK merged manifest確認獨立package／instrumentation target為 `cg.creamgod.boarderless.shared.test`；本輪新增私有QA provider、INTERNET與cleartext配置只改test manifest，不修改原APP manifest。原APP package `cg.creamgod.boarderless`不覆蓋。安裝前QA package不存在，本輪僅新增test APK。

5118實際instrumentation返回 `OK (1 test)`、2.635s、status code -1／case code0（無skip）：

- 新ready QA asset：`09fd30bd-76bf-4276-9aa4-4742bb8147b5`
- 新QA Node：`edd28f84-0f76-4fc4-9fc2-385df6797236`
- 實際signed storage origin：`http://192.168.68.65:9000`（不保存query／signature）

僅1個新QA asset／Node保留在上述原QA workspace，沒有自動刪除server資料。fixture自有source／snapshot／下載檔與唯一flat temp目錄已清理；獨立QA test APK仍安裝於模擬器，便於重驗，未改使用者文件。

556321普通切片另跑3個instrumentation cases：AndroidMediaBackendLiveTest明確assumption skip（不能当又一次live成功）；AndroidMediaRecoveryDeviceTest真實checked commit／XML／dismiss 1case與GiphyBrandResourceTest原生Res／hash 1case各code0通過，runner `OK (3 tests)`包含1skip。這只解除这兩個從未執行的Device切片，不當完整AndroidDevice全套或APP kill/restart驗收。

重驗（會再新增1個QA asset／Node，請只對指定QA scope明確執行；普通run不要設live旗標）：

```sh
/Users/wangfuxian/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r \
  -e class cg.creamgod.boarderless.AndroidMediaBackendLiveTest \
  -e boarderlessMediaLive true \
  -e boarderlessMediaBase http://192.168.68.65:3000 \
  -e boarderlessMediaUser 8d9cac0b-a741-447c-9f37-97de230de00d \
  -e boarderlessMediaWorkspace 1883808a-7a58-4062-9556-9de88bd55e89 \
  cg.creamgod.boarderless.shared.test/androidx.test.runner.AndroidJUnitRunner
```

這裡為功能驗證時間，不是效能門檻；視覺／VoiceOver／遮擋仍由使用者簽核，效能最後。下方JVM證據與未驗條件保持其原範圍。

更新：2026-10-05。這是 JVM APP data／repository 對已運行本機 backend 的功能整合證據，不是畫面、原生選檔／播放、正式登入或產品放行。

## 執行環境與邊界

- `http://localhost:3000/health/ready` 實際回覆 `status=ok, database=up`；服務公開狀態頁顯示 `storage: s3`。測試未讀 `.env`／簽名金鑰、未啟動／修改 backend 或 worker。未單獨核對 S3 vendor，不能稱 AWS production 驗收。
- 重用 repo `backend/tests/fixtures/media/manifest.json` 與六個真實編碼 fixture。先核對 byteSize／SHA-256，再作任何 API 寫入；非 GIPHY 內容。
- 使用真實 `BackendAssetTransferGateway`、`AssetImportCoordinator`、`AssetDownloadCoordinator`、`BackendWorkspaceRepository`／MediaNode factory，不是 MockEngine。sink 為隔離記憶體 fixture，commit 前核對 SHA-256 與原檔逐 byte 相同，不宣稱正式 Desktop 檔案快取已驗。
- 每次建立新的 owner／viewer／outsider 與 QA 工作區；不讀／改既有 APP preferences，repository 使用 InMemorySettings。只操作該 run 新建資料。撤除的是 fixture viewer membership；沒有自動刪除 QA users／workspaces／ready assets，APP 原本的 pre-completion cleanup 政策不變。
- backend 現在是 dev `x-user-id` 身分機制：ACL 證據是已交付伺服器角色規則，不是正式 token／登入安全已完成。

## 已通過的功能範圍

PNG／JPEG／WebP／GIF／MP4／WebM 六格式各驗：prepare→signed PUT→complete→ready、owner 與 viewer authorize→signed GET→校驗、viewer prepare 回403、outsider content 回404、建立正式 MediaNode operation→accepted→refresh。

GIF／MP4／WebM 另外等待縮圖 metadata，確認縮圖 ready／image MIME，並用 viewer 經正式 authorize／download／checksum 讀取。沒有自動視覺識別或播放判定。

新建第二個 repository 與 clientId、使用 owner／workspace identity 重新載入 server state：六個 Nodes／assetId／mediaKind 都存在，並再校驗下載。這是新 repository／client 的冷載入，不是 kill APP、Desktop OS 冷啟或雙人 UI 協作驗收。最後撤除 viewer，舊 viewer session 的 content 請求回404；不接受新 signed ticket。

404 非成員／撤權是 backend `requireWorkspaceAccess` 的刻意隱藏存在政策；已查實際 response 與 service，不把它改成任意403／404都算通過。

## 可追溯證據

`MediaBackendLiveAcceptanceTest.realStorageWorkerAclInsertAndColdReopen`：

- 79932 首輪 compile 失敗（MapSettings 未在依賴中），改既有 InMemorySettings；未發 API 寫入。
- 12162 失敗（9s）：PNG ready／owner-viewer下載已通，ACL 斷言誤期待 outsider403；核對實際404後只修斷言，不改 server ACL。
- 51149 成功（10s），XML `2026-10-05T01:38:08.049Z`，1 test／0 failures／0 errors／0 skipped。
- 38758 最後 source／Gradle opt-in cache gate 成功（18s），XML `2026-10-05T01:39:31.934Z`，1 test／0 failures／0 errors／0 skipped。實際重跑，不是快取；六個 `LIVE_MEDIA_ACCEPTED` 與 `LIVE_MEDIA_COMPLETE` 記錄已核對。

最後成功 scope：workspace `c47bf81e-2a79-4fb0-aea6-2d36d47ffac3`；owner `60e37ef6-1e7a-4066-898b-c1878ae84d33`；viewer `3451f636-5611-42da-893c-dedf1b761b83`（已撤除）；outsider `1fcf0ef0-02a9-4978-854c-6b27df661e27`。

| MIME | Asset ID | Node ID |
| --- | --- | --- |
| image/png | a3eba4ee-33fe-4e65-babd-79ea530b49a1 | 461f9f29-5743-4fc0-95ea-25ee41a76aec |
| image/jpeg | 92d842e8-d11a-4467-9e59-b36694eb218c | 57162df6-a0e6-4690-9014-93f2290c94f1 |
| image/webp | 8e374c2c-a25c-4ce2-93e9-3f01b08990c8 | 2706da11-d9e6-4576-a684-24daa0351e16 |
| image/gif | fa7a6188-fcbf-48ce-b05d-44cff16acaa7 | d958b19b-57e3-4247-a5d2-c5abb8bed199 |
| video/mp4 | 0c124f64-fdc3-4d54-a536-faff2a64623f | 7b82247c-b826-4851-aacd-aa0424307ecf |
| video/webm | 20adddf8-e685-432a-9142-f8ab992b2019 | de843c82-6d36-4c59-b787-95bc3c3b0b59 |

前兩個 scope 也保留：`7c3386e9-2b84-40a2-8ca2-dc8061e049e6`（PNG partial、未插入 Node）與 `70a434ba-8e9a-4ddc-9962-619e2a834ef5`（六格式成功）。共3個新 QA 工作區／9個新 fixture users；不是使用者原工作區。清理另由使用者選擇，不批次刪除或自行處理 storage object。

## 重新執行

### Desktop 正式檔案 sink 的唯讀 live驗證

67390 XML timestamp `2026-10-05T02:00:39.990Z`，1 test／0 failures／0 errors／0 skipped。78410普通JVM／Desktop回歸成功3s，live四case均skip，Desktop同內容cache；不當新的live證據。

84429首輪成功13s；67390改成GET-only `repository.refresh(seed)` 後最後成功5s，避免openOrCreate的404自動建立fallback。`realDownloadToJvmFileSinkAndConsumerCancellation` 實際signed GET配正式 `JvmFileAssetDownloadSink`，不是記憶體sink：原檔size／checksum與PNG fixture一致才下載；成功發布檔案逐byte一致且無part，後續abort不刪已驗證檔案。另一下載收到第一chunk並寫入part後取消真正consumer coroutine，Ready不emit、part清除、不發布target。這是consumer callback gate，不是TCP半途中斷或Desktop UI播放證據；小PNG可能已全數進入第一chunk。

重用上述cancel-run的workspace／owner／Ready asset，只讀server、未新增scope／Node／素材／身份，累計仍5個QA工作區／11個users。測試自建暫存目錄與檔案已清理，不接觸APP正式cache／prefs。四live cases中的此case需額外明確scope，缺少即skip；其餘三case維持各自原先opt-in建立fixture流程。

唯讀重跑（scope若已清除應失敗，不自動建立替代；先核對是否仍為PNG fixture）：

```sh
BOARDERLESS_MEDIA_LIVE_ACCEPTANCE=true \
BOARDERLESS_MEDIA_LIVE_BASE_URL=http://localhost:3000 \
BOARDERLESS_TEST_REPO_ROOT="$PWD" \
BOARDERLESS_MEDIA_LIVE_DOWNLOAD_USER=8d9cac0b-a741-447c-9f37-97de230de00d \
BOARDERLESS_MEDIA_LIVE_DOWNLOAD_WORKSPACE=1883808a-7a58-4062-9556-9de88bd55e89 \
BOARDERLESS_MEDIA_LIVE_DOWNLOAD_ASSET=4c8c014b-a259-41c2-8084-f69ef7171d29 \
./gradlew :shared:jvmTest --tests '*MediaBackendLiveAcceptanceTest.realDownloadToJvmFileSinkAndConsumerCancellation*'
```

scope env已加入Gradle inputs，live仍禁止cache復用。未opt-in普通suite現在4 cases均skip；不能用普通綠色build冒充本次live。iOS／Android／Web正式sink、Desktop播放器與GUI、TCP傳输中斷、OS重啟／過期票券仍需各自證據。

### 真實服務取消邊界（2026-10-05）

93711 `realCancellationBeforeAndAfterCompletion` 成功10s；XML `2026-10-05T01:55:57.989Z`，1 test／0 failures／0 errors／0 skipped。真實prepare／PUT／complete／worker與下載，僅在APP gateway返回邊界以deferred gate暫停，取消實際子coroutine；不是TCP傳输中斷或UI取消按鈕驗收。

- 完整PUT後、尚未complete即取消：保留CancellationException、不emitReady／RecoveryRequired、不建Node；coordinator執行一次abandon，metadata404、提醒空，重複abandon成功、原ticket complete409不能復活。正常pending-only cleanup僅作用本run新資產；未驗storage刪除job何時完成。
- 真實complete返回後取消：不abandon、不emitReady、不建Node；RecoveryRequired與提醒保留，回查同asset ready並校驗下載。嘗試abandon ready回409，之後仍ready。無重上傳；提醒為InMemorySettings，不宣稱OS durable或殺APP恢復。
- workspace `1883808a-7a58-4062-9556-9de88bd55e89`，owner `8d9cac0b-a741-447c-9f37-97de230de00d`；放棄asset `1b34e250-85f8-4568-831e-a16fded1c445`，保留ready asset `4c8c014b-a259-41c2-8084-f69ef7171d29`。沒有Node，新增scope／身份／ready保留，累計5個QA工作區／11個fixture users。

單獨重跑使用下方env與 `--tests '*MediaBackendLiveAcceptanceTest.realCancellationBeforeAndAfterCompletion*'`。class目前3 cases；全class每次新增3工作區／5身份，一般未opt-in三case皆skip。前文2-case與4scope紀錄是歷史時點，不是最新總量。真實網路傳输途中取消、完整OS重啟、signed URL過期及撤權票券邊界仍Pending。

### 追加失敗邊界證據

26241：`realChecksumRejectionAndLostCompleteResponseRecovery` 實際成功22s，XML timestamp `2026-10-05T01:45:55.749Z`，1 test／0 failures／0 errors／0 skipped。

- 真實PNG bytes搭配錯誤宣告hash，worker回Rejected／checksum_mismatch；APP回RecoveryRequired、不emitReady、不abandon、不建Node；content授權409。
- 第二資產實際complete成功後，APP gateway邊界刻意拋IOException（不是TCP封包遺失）；回查同asset ready及校驗下載，不重新prepare／PUT、不自動建Node。相同ticket重複complete兩次維持asset ID；只證明API結果，未量測內部job數。
- 重建MediaRecoveryStore但沿用InMemorySettings，提醒保留；不是OS durable／APP kill證據。明確建立Node後才dismiss本機提醒，Rejected提醒與server Ready資產保留。
- workspace `d9d59aab-290f-466a-923c-2eafebc1f3bb`，owner `aa83feea-20be-4909-b588-f38f6e1018fc`；Rejected asset `d0b8a971-87a7-4c5c-9d75-307d9bd6f299`，Ready asset `3e348c90-a5c6-42c7-82b6-9ee2da0099ea`，Node `066ea533-28c4-41a0-acb2-3357bc4dd164`。加上上述歷史run，目前共4個QA工作區／10個fixture users；未刪除。

只重跑此邊界時用下方相同env與 `--tests '*MediaBackendLiveAcceptanceTest.realChecksumRejectionAndLostCompleteResponseRecovery*'`，避免重做六格式。整個class現在有2 cases，每次全class執行建立2工作區／4身份；一般suite未opt-in會skip兩case，不能當live通過。

先由 backend 維護者確保 API／DB／storage／worker 已運行。於 repo 根目錄執行（需專案相容 JDK）：

```sh
BOARDERLESS_MEDIA_LIVE_ACCEPTANCE=true \
BOARDERLESS_MEDIA_LIVE_BASE_URL=http://localhost:3000 \
BOARDERLESS_TEST_REPO_ROOT="$PWD" \
./gradlew :shared:jvmTest --tests '*MediaBackendLiveAcceptanceTest'
```

只接受明確 opt-in 和 localhost／127.0.0.1:3000，不提供 production URL 入口。每次會建立新的 QA 資料，先確認可接受。live flag／URL／fixtures 都是 Gradle inputs；live run 禁止 up-to-date／build-cache 復用，必須核對 XML skipped=0 與本次 timestamp／scope。未 opt-in 的一般全套會明確 skipped，不能拿綠色 build 當 live 通過。錯誤輸出只保留 stage／exception class，不公開 response body、signed URLs 或 headers。

## 尚待產品簽核與服務邊界

### Android 恢復保存屏障（開發與裝置簽核分開）

後續76671（1m35s）已修原1532品牌Host fixture：common斷言保留，Host精確讀Gradle供應當次APK entry，缺檔／超量失敗，不從source補檔；JVM／iOS／Web／AndroidDevice仍Res API。完整AndroidHost656cases／0 failures／0 errors／0 skipped，APP與Device APK建置成功。此解除Host runtime-stub路由缺口，不把1532歷史失敗刪除；不代表AndroidDevice／cold launch／OS重啟與產品簽核完成。

70483最後iOS全套成功39s，import24cases通過，新增boot simulator恢復shutdown；common suspend barrier四目標回歸補齊。Android實機保存／cold launch仍Pending；focused Host不解除1532完整suite品牌資源失敗，下一步修其正確artifact／Device route。

1532全套5m39s失敗：AndroidHost 656tests中1個GiphyBrandResourceTest呼叫DefaultAndroidResourceReader，於JVM stub `Log.d not mocked`失敗；未改該測試／放寬assertions／設returnDefaultValues。其他目標import24cases與新增Android保存3cases均通過。22874明確聚焦Android保存3＋import24＋store9及APP／DeviceTest APK建置成功4s；此切片綠色不解除完整Host suite失敗。品牌資源待Host artifact fixture或Device routing，不以本輪成功重跑抹去。iOS最後結果另列。

本次核對multiplatform-settings 1.3.0實際Android sources：Settings預設`${packageName}_preferences`採apply，改commit=true仍忽略boolean。Android recovery factory現以Startup initializer取得同application preferences，僅record／remove改checked commit；false即「結果無法確認」向上傳遞，不以記憶體更新當保存成功、不rollback／自動重試，未搬移legacy或改其他偏好。規範見 [SharedPreferences.Editor](https://developer.android.com/reference/android/content/SharedPreferences.Editor)：apply非同步；commit回傳成功與否，false也可能是未知結果。

beforeComplete改suspend；APP保存／dismiss移到Default dispatcher，畫面內mutex串行處理兩種寫入，不讓同步commit／flush占UI執行緒。取消等待屏障不送complete；保存完成後若取消仍依既有pending-only政策處理，可能留下保守提醒，不自動刪資產。這個畫面mutex不是多process／多window CAS，不當完整durable journal。

AndroidHost新增3個SharedPreferences protocol double cases：commit不用apply、false但memory更新仍fail並保留legacy、不確認dismiss時不retry。DeviceTest新增真實SharedPreferences／XML落盤及dismiss case，只碰unique test檔。現場SDK adb清單無装置，因此DeviceTest即使APK建置成功也不得標成執行通過；process kill／重啟與GUI仍待實機。merged APP manifest已確認Startup provider／recovery metadata與既有SettingsInitializer同時保留。79152首輪43s編譯失敗缺startup import，已明確加入與原runtime相同的startup-runtime1.2.0 compile dependency；最後回歸結果另列。

### Desktop 恢復提示的保存修正（非live API測試）

71953最後iOS全套成功43s，MediaRecoveryStoreTest 9cases通過，新增boot simulator恢復shutdown；這驗common行為及iOS編譯，不證明iOS Settings硬體落盤。普通suite四個live cases皆skip，未以此重做或冒充新API live證據。

本次檢查multiplatform-settings 1.3.0實際JVM sources：預設Settings使用Preferences.userRoot，put／remove只更新Preferences，沒有flush。依 [Java Preferences規範](https://docs.oracle.com/en/java/javase/17/core/preferences-api1.html)，寫入允許非同步；恢復ID必須在complete前確認保存，不能只以記憶體可讀推論重啟可讀。

MediaRecoveryStore的JVM預設改為同userRoot／同key的flush wrapper，不搬移既有資料；putString／remove等待flush，異常向上傳遞，不靜默重試／rollback未知寫入。共用save核對回讀值後才移除legacy key；被忽略的寫入／刪除不能回報成功。beforeComplete既有gate在record失敗時停止complete，僅保留原本pending-only cleanup政策。Android／iOS／Web factory仍是原Settings，未宣稱其他平台相同落盤保證。

62458最後JVM／Desktop／JS／Wasm、Android APK／Web entries成功31s：MediaRecoveryStoreTest三目標各9cases；MediaRecoveryPreferencesTest JVM3cases（flush失敗保留legacy、真實Preferences UUID、3個獨立JVM record→halt→read-dismiss→halt→read-empty）。halt不執行正常shutdown hook，只有隔離的unique QA node，完成後清理；不是硬體斷電、跨process CAS／多writer防覆蓋、GUI或網路取消重開整合驗收。82074首輪1m54s因未傳fixture-root env導致Karma配置失敗，保留紀錄，不當測試通過；補env後62458成功。iOS回歸結果另記。

依「產品 QA 品質檢驗 SOP.md」由使用者在實際 APP／自己的 QA 工作區驗六格式匯入、重開、GIF／影片播放音訊、圖片預覽／viewport解碼、縮圖、連線標註、旋轉拖曳、下載到正式平台sink、小螢幕控制列／遮擋／VoiceOver並截圖。不要把 fixture owner 自動寫入使用者 APP 身分。

Android／iOS／Web 原生 picker／DirectAssetUploadSource／CORS／TLS、實際 worker 重啟／延遲、過期 signed URL、真實TCP斷線／OS重啟／cancel恢復、其他格式checksum拒絕、撤權後已發 ticket 的存活邊界、storage GC／production登入、安全部署仍需各自正式證據；以上有限失敗case不是這些全部完成。GIPHY 授權 modal／保存仍待來源或供應商批准確認，WS／方案簿／背景／AI／鋼筆完整主線保持，效能最後。
