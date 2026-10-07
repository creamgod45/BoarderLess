# 原提交唯讀 receipt 查詢驗收

## 全來源初次遷移計畫（2026-10-06，純計算／未啟用）

DesktopLegacyMigrationPlanner凍結source，為每scope建立draft／safety候選與namespace floor；同時包含unsent及safety-only工作區和foreign資料。sourceDigest使用排序catalog與length-framed原始source/counter，不只hash標準化後結果。archive先發布而pending尚未移除時，原wire完整保留為stopped head、草稿全部intentions保留並quarantine retry；不是server取消或commit推斷。Settings stop留下的journal head可用exact archive綁定；缺wire或head矛盾拒絕，不造journal／wire或ACK。

orphan fence即使caller偽造unbound空清單也會重新檢查缺wire而拒絕；known marker必須match request digest。floor重新計算，不讀serverSeq代替clientSeq。上限256scopes、單scope source16MiB／aggregate40MiB；候選native檔仍各codec6MiB。安全codec與native writer共用，legacy import fingerprint不改。

此入口只產生初次plan，existingNativeBundles非空一律要求activation receipt reconciliation，不覆寫或rewind。它沒有證明native catalog完整、舊writer排除或ledger狀態，沒有寫檔／receipt／切換writer；下一步先持久migration receipt、逐檔CAS／generation與digest核對、partial publication恢復，全部確認才activation。舊source保持，unknown不rollback或任意重試。

23035focused10tests成功34s；93705最後JVM794／0fail／0error／6skip、Desktop46／0fail／0error／0skip及JS／Wasm／iOS arm64檢查成功8s（production same-content cache，新增JVM／Desktop測試有執行）。新增5cases：全部來源保留與zero-write、stop partial／missing-conflicting head、原phase/counter digest與catalog order、orphan／forged floor、foreign／safety-only／bounds；不是實際migration、GUI、kill-durability或產品全部完成。

## Desktop原子安全資料（2026-10-05，預設未啟用）

新增RecoverySafetyPersistence與DesktopRecoverySafetyStore，internal SessionPreferences constructor才注入既有fence／deletion facades，public default仍Settings。private per-scope檔案有獨立namespace、typed schema1、scope／tx／entity唯一性、fences256／deletions4096與6MiB總bounds；用既有data-force／atomic rename／directory-force及generation CAS。explicit adoption canonical source fingerprint同檔保存，舊來源不刪除；重覆adoption只返回已前進資料、不rewind。runtime缺記錄／corrupt／tombstone拒絕，不fallback或重新初始化、不retry／GC。

- fence begin先保存Unconfirmed，再允許repository POST；publication故障回報可能已保存，重開沿原wire digest檢查並保留門禁。unknown不寫、不清，normal receipt無原attempt不創造marker；terminal互相矛盾或request不同拒絕。
- CommittedDeletionStore在既有完整ACK gate後驗confirm cascade／snapshot digests，整批objects＋relations一次CAS；不容許半批proof。restore gate使用native metadata與原snapshot／CAS版本一致性，不因保存而升級成server授權。同tombstone不同資料或版本／serverSeq倒退拒絕，新版本與serverSeq必須一起前進。
- safety檔、draft檔與sequence ledger仍是分開的CAS，不是跨檔原子ACK settlement。完整migration plan／activation receipt與舊writer排除未接；default activation、OS kill／硬體斷電及其他平台adapter仍未驗。不得因單一native seam成功就切換使用者writer。

75602focused成功35s；28467前輪actual compile／Desktop tests成功43s；65072最後shared JVM789／0fail／0error／6skip、Desktop46／0fail／0error／0skip與JS／Wasm／iOS arm64檢查成功23s（production／Desktop cache，JVM追加tombstone斷言有重跑）。新增5cases含legacy實際metadata adoption不可rewind／tombstone不可復活、native facade reopen／零legacy fallback keys、三publication fault cascade全有或全無與正式restore gate、metadata版本／scope／immutable拒絕、actual repository失敗zeroHTTP並保留pending及client counter；沒有真API或user prefs遷移，不代簽GUI／OS kill。

## 遷移前fence／刪除證據唯讀盤點（2026-10-05）

capture現在包含全部fence attempts及committed deletion evidence，不只wire／journal。只讀fence1／wdel1值；valid hashed key、scope、schema／enum、digest、tombstone／serverSeq範圍核對，foreign或已找不到wire的marker仍保留。fence最多256、deletion最多4096，各source aggregate4MiB、每record4096字元與depth gate；超量或損毀／觀察到的來源異動拒絕、不修復／刪除／rollback。typed record改名不改旧JSON欄位，既有restore與fence回歸保留。

matching pending／stopped／ACK／retained archive必須與fence request digest相同；孤立marker列入unboundFenceAttempts，不由opaque hash推clientSeq，不假造wire或清重送門禁。serverSeq不是clientSeq。capture回傳floor只是已知來源下限，不能單憑capture成功初始化ledger或activation；unbound來源需明確恢復／完整核對，fence summary／localdeletion記錄本身不成server權限。

22255focused24tests首輪fixture正常save覆寫pending而失敗，既有immutable wire門禁正確；改成隔離marker digest注入。不放寬production gate。69218最後shared JVM784／0fail／0error／6skip、Desktop46／0fail／0error／0skip及JS／Wasm／iOS arm64compile成功1m16s，新增5cases含zerosettings／無關值不可讀、foreign/orphan、metadata/key/digest/版本、來源異動及record／aggregate gate。不是native原子保存fence/deletion、全migration／啟用、OS kill或正式服務驗收。

## Desktop合作程序生命週期排他（2026-10-05）

新版Desktop main在APP／Settings建立前取得`.boarderless-storage/lifetime-writer.lock`，持有至Quit／程序退出；Mac關閉視窗只是隱藏，不释放。穩定鎖檔不刪除、不rename，不採過期時間或PID猜測；busy或不安全root／lockfile則提示並停止啟動，無第二writer／資料reset fallback。POSIX驗root0700、file0600與NOFOLLOW；Windows file-lock路徑有編譯但Windows平台ACL／GUI尚未驗。

第二launch先嘗試已有instance socket轉交workspace或focus，socket只是discovery，不證明排他。舊build／tools可忽略新鎖，必須另確認已排除這些writers，持鎖不成migration authorization，也不能初始化缺失counter／identity。未改defaultSettings／遷移資料。

70538最後Desktop46tests／0fail／0error／0skip，shared JVM779／0fail／0error／6skip成功9s；87035focused首輪protocol fixture誤判合法ID，修非法字元fixture後通過。新增5cases含真independent JVM拒絕及kill後重取lease、stable inode、same-process競爭、POSIX symlink／permissions與pure token protocol。kill是隔離lease harness，不是實際APP／草稿落盤／OS crash／資料恢復驗收；main GUI、Dock reopen、啟動競爭提示與Windows人工待驗，詳QA SOP。

## Desktop多筆歷史封存（2026-10-05，尚非預設啟用）

typed bundle v3新增retainedStopped，獨立保存多筆historical exact wire與各自optional完整核對evidence；原stoppedPending仍為當前隔離journal的head，不能把歷史wire綁到任意journal。v1/v2不輸出空retainedStopped。scope／transaction唯一性、與pending／ack／primary stopped重複、evidence terminal／wire一致性均驗；總bundle6MiB且retained上限256。透過generation CAS adopt全部輸入，unknown publication不重試、不删legacy；journal先或archives先都保留已有狀態，不rewind。

append／stage／ACK保持歷史archives，新wire不能使用archived transaction ID；明確移除draft只刪其journal／ack，不刪retained wire／proof。archive-only可開始另筆正常draft；保存其中一筆proof不能更動另一pending或其他archive。repository stoppedChanges逐transaction取得proof並核對，清單hint不是授權；floor包含所有retained wire。v3舊版本reader必須拒絕，不能忽略新欄位後寫掉資料。

38875初輪test import同名衝突編譯失敗，修正後44582focused成功18s；68150最後JVM779／0fail／0error／6skip＋Desktop classes與JS／Wasm／iOS arm64檢查成功22s，production同內容cache，93012前輪實際compile成功37s。新增5cases，涵蓋lifecycle／reopen、generation／wire衝突、兩種adoption順序、三publication faults、正式repository零HTTP／zero-write per-txhint；不是OS kill／GUI／自動migration。預設Settings仍未切換，完整writer排他／全來源migration（fence attempts、deletion provenance、typedrecords及nativeledger）與fresh review→Apply仍待完成。

## 遷移前唯讀停止交易盤點與序號下限（2026-10-05）

後續補齊未送出 journal 的全 scope capture：外部 scope catalog 必須同時涵蓋沒有 pending 的草稿；journal manifest全域256／declared bytes16MiB於讀chunk前檢查，保存完整intentions與quarantine，不以未送出的operation ID抬高sequence floor。缺目錄、duplicate、corrupt chunk、目錄異動拒絕且零自身寫入；capture重讀journal來源。7439 focused8tests通過57s，1856最後JVM774／0fail／0error／6skip及Desktop／JS／Wasm／iOS arm64編譯成功41s，新增5cases。這仍不是writer排他／原生migration；現Desktop bundle單筆stoppedPending限制尚需改成多筆保留，不能任選archive或抹除來源。

已新增全 scope `stoppedInventoryForMigration`、必要 stopped 來源的 `CapturedClientSequenceFloor` 與 `SessionPreferences.captureLegacySequenceForMigration`。現代 manifest 可讀 exact scope／transaction；舊無 metadata 格式必須提供完整 exact locator catalog，不推測或漏掉其他 workspace。全域上限256 manifests／4MiB declared wire bytes，在 chunk 讀取前 gate；損毀、缺漏、manifest／目錄觀察到的異動均拒絕，不清資料。相同 scope／transaction 跨 pending／stopped／typed bundle 必須是同一完整 wire；foreign namespace 也先驗證，再排除於此 namespace floor。

capture 只讀已有 client identity／counter，不呼叫會建立身份或將缺 counter 當零的 getter；重讀來源／counter，發現漂移不回傳 floor、不 rollback 外部 writer。此兩次讀取不是鎖／原子 snapshot，caller 仍須排除所有 legacy writers、提供完整 pending／legacy archive／typed bundle catalog，並納入已有 native ledger。尚未遷移使用者偏好或預設啟用 adapter，沒有 OS kill／硬體斷電／真正多程序 writer 排他證據。

74258 focused曾因舊fixture同交易不同wire被拒絕而失敗；修正不同交易ID與counter漂移fixture正式key，57453 focused14tests成功11s。26070最後JVM769／0fail／0error／6skip與Desktop classes、JS／Wasm／iOS arm64編譯成功35s。新增9cases，零自身寫入／legacy catalog／corrupt chunk／aggregate gate／catalog與counter變動／namespace與exact wire矛盾均覆蓋；不是正式服務或端到端Apply驗收。

## Desktop 原子核對記錄保存（2026-10-05 新增，尚非預設啟用）

已將 `DesktopWorkspaceDraftPersistence` 的完整記錄保存接入正式 repository API。只有 adapter 明確提供 `supportsAtomicStoppedEvidence=true` 才可在視窗顯示「重新核對並保存記錄（不套用）」；預設仍是 Settings、capability=false，沒有此按鈕，也不以多 key 寫入冒充原子保存。Desktop 預設 activation 仍需 legacy writer 排他／全 scope 遷移／sequence floor，未自行更換使用者 prefs。

- 保存動作重新走 receipts／完整 committed log（fenced無ACK）／fresh ACL-state，不能接受先前 UI transient verification 當提交憑證。原子 generation 在網路前捕捉；網路期間即使另一 writer 發布相同 bytes，也以 generation CAS 拒絕覆寫。
- 同一 private file CAS 保存 exact stopped wire、原 journal／queued tail、完整 receipt／normalized ACK 與觀察到的 head。保存 evidence 時明確升 typed bundle 至 v2；沒有 evidence 的 v1 encode 不增加新欄位，旧版 decoder 不可悄悄寫掉 v2。codec 及 transition 重驗完整證據，unknown／缺ACK／錯scope／commit內容不符／terminal矛盾／checkpoint倒退拒絕。重新保存不可改掉已保存的完整 commit records（含 server defaults）；JSON 數字的等值表示可接受，其餘值／欄位差異拒絕。
- 成功仍保留 stopped wire 與 quarantined journal，不修改 client sequence、外部 deletion evidence 或 acknowledgement stack，不 rebase／clear／Apply。重開清單只顯示「已有保存的核對記錄」，不是目前權限／版本授權；後續合併必須重新核對。
- capture／CAS 使用背景 dispatcher。取消或 timeout 不能撤回已開始的 atomic rename；發布中／後失敗回報結果可能已保存，必須重新讀取，無自動 rollback／retry／GC。before-publication fault 保留舊 bundle，publication 後故障仍能讀到完整 v2 wire／draft／evidence。視窗新增「重新讀取封存」，只重讀本機 catalog，不重設另一筆 pending 的 receipt／fence UI 狀態，不再 POST 原交易；保存成功同樣只刷新封存清單。
- 尚未預設 production 啟用、沒有 Android／iOS／Web 原子 adapter、沒有完整 legacy archive migration、fault quarantine 修復、OS kill／硬體斷電證據、草稿狀態轉移與 merge Apply。此切片不是 BAI-012 或六主線全部完成。

4905 最後 JVM760／0fail／0error／6skip及 Desktop classes／JS／Wasm／iOS arm64編譯成功1m23s。新增4cases：common v1/v2／wire identity／malformed/unknown／terminal/immutable log/head guard；default Settings capability false且零HTTP；native repository用actual mapper wire與POSIX私有檔案驗committed/fenced、三publication故障及讀回完整草稿／tail／wire／proof、generation race。12540 focused與21819/3628早輪成功，不替代最後內容；沒有使用者prefs遷移、真服務、實機或OS kill/斷電證據。

## 封存交易完整核對（2026-10-05 新增）

封存視窗查到 committed／fenced 後，另顯示「核對完整記錄與最新畫布」。按下才執行，不自動串接或提交。這是權威資料的唯讀核對，不是原子 ACK 保存／合併 Apply。

1. 重新 POST 既有 receipts，核對 exact archive request；unknown／receipt 不符直接拒絕，不把先前視窗結果當持續授權。
2. committed：GET operations，afterSeq=fromServerSeq-1／limit=200。使用與 pending committed 恢復共用的完整核對 helper；最多200個 records，核對原交易連續 range、每個operation／actor／client／clientSeq／kind／baseVersion／schema／version／commit時間及原 payload 值。missing／duplicate／超量／payload變動或 checkpoint 不足拒絕。fenced 沒有 commit，不讀 operations 或假造 ACK。
3. GET 最新 metadata/state；撤權或 scope 錯拒絕。版本及序號須至少到達此次 receipt head 與開啟 session checkpoint，且在 safe integer 範圍；不能用只包含原 commit 的過舊 state 通過。Viewer 可讀，回傳 fresh role 不表示可編輯。
4. 完成後再次核對同 scope 的 exact archive 未變且 coroutine 未取消；返回 transient verification（含 fresh session、原 commit version/toServerSeq，fenced 則無 commit 欄位）。它不作備份 Apply 授權，也不跨重開保留；每次後續寫入仍需重新核對 fresh ACL/version。
5. 唯讀完整核對不改 archive／active pending／journal／fence marker／deletion evidence／sequence，不解除 quarantine、不 submit/fence；UI 保留明確「核對完成但尚未套用」訊息。Desktop adapter另有上節原子 evidence 保存，尚未預設啟用；完整 settlement／fresh review→merge Apply 仍未接。

55985 最後 JVM756／0fail／0error／6skip＋Desktop/JS/Wasm/iOS arm64編譯成功46s。新增2cases：13mode（valid／fenced／unknown／missing／payload／base／time／duplicate／head／oversized／denied／stateScope／stateStale）逐key snapshot零寫入矩陣、fresh state已進入engine後取消無成功結果。66159focused前輪33s成功；不代替正式backend fixture、服務、ComposeGUI、實機或OS kill。

## 停止重送後的封存檢視與查詢（2026-10-05 新增）

畫布恢復區新增「檢視已停止重送的交易」。有可讀封存才顯示，中央 safeDrawing modal 提供交易 ID 與逐筆明確查詢；不同工作區／使用者／client 隔離，不顯示原 payload、認證或 endpoint。關閉／切換 scope 取消視窗請求，15 秒查詢期限與固定每筆狀態；畫布與快捷鍵在視窗期間停止互動。

- 新封存 manifest 增加 scope／transaction discovery metadata，key 與完整 wire 必須匹配。active pending manifest 維持原四欄格式，避免破壞舊版 strict decoder。scan 最多 256 個 manifests，manifest 上限 4096 字元；目前 scope 的 wire 合計最多 4 MiB，在讀 chunk 前檢查。
- 舊無 metadata 的封存可由仍保留 journal headTransactionId 提供 exact legacy catalog；catalog 不完整、格式錯誤、scope／transaction 不符或超量，都保留資料並顯示無法讀取，不能悄悄丟掉。尚無跨工作區 legacy catalog 修復精靈，亦無自動 GC。
- APP 使用既有 operations/receipts，只送選定 archive 的 transactionId。前後重新核對 exact saved wire；committed／fenced／unknown 經既有嚴格 receipt 驗證，403／錯 actor／缺 operation／scope 錯／格式錯／取消不得發布成功。
- 查詢不寫 Settings／journal／fence marker／sequence、不清封存或 pending、不 POST submit/fences、不解除 quarantine、不建立新交易。同 workspace 存在另一筆 active pending 也不得查錯交易。viewer 可發出唯讀查詢，是否仍有讀權限由伺服器判定。
- committed 是摘要，不是完整 ACK；需另按上節完整核對。full-log／fresh state 的唯讀驗證已接，但原子 settlement／合併 Apply 未接；fenced 也不是可自動替代提交的授權。人工驗收 modal 視覺／遮擋／VoiceOver 依 QA SOP。

91585 最後 JVM754／0fail／0error／6skip及 Desktop classes／JS／Wasm／iOS arm64編譯成功1m22s。新增5個 common cases，含 scoped／read-only inventory、legacy missing catalog／metadata錯、256及4MiB提前限制；repository committed／fenced／unknown／actor／scope／operation／403／格式錯矩陣、另一active wire與preferences重建、engine進入後取消；逐key snapshot確認零本機寫入。10935／22153較早成功不代替最後內容。沒有真實服務／Compose GUI／實機／OS kill證據。

## 停止重送的原交易封存（2026-10-05 新增）

Settings 正式 adapter 原先 stop 只隔離 journal 並刪 pending，會失去 exact wire；現改成先以 scope＋transaction ID 封存完整 PendingWorkspaceSubmission（含 operation ID／clientSeq／payload／刪除版本與 snapshot digests），回讀一致後才隔離 journal、再次核對 pending，最後釋放重送槽。封存不是 receipt 或 server cancellation，不能授權 merge Apply。

- 原 pending key 保持 v1；封存採獨立 wsa1／wsac1 manifests/chunks，每個交易不可覆寫，只允許 exact 重複保存。相同 workspace 的不同交易各自保留，不能由 acknowledge 清掉封存。
- 分塊及 manifest 保存都回讀確認，完整資料經既有 checksum／scope／大小／深度驗證；journal 完整新版本確認成功後才回收舊分塊。失敗不清 pending、不重送、不更新 client sequence。未發布的孤立新分塊不自動回收。
- 使用者明確刪除隔離草稿不會刪除 wire 封存；但封存不是整份草稿備份。不能把「wire 可找回」當作草稿刪除後仍可完整復原。
- common tests 涵蓋大 Unicode／刪除 provenance、Settings 重建、交易／scope 隔離、不可覆寫、損毀 fail closed、silent chunk／manifest 丟失，以及 repository stop 的封存／quarantine 失敗與明確再試；不是四平台 OS kill、硬體斷電或多 writer 原子證據。
- 封存檢視／receipt及 full-log／fresh state 唯讀核對現已接入，詳上節；原子 settlement／Apply 未接，未新增自動 GC。既有 legacy migration inventory 不包含新封存，production 遷移須補全 scope catalog 及 sequence floor 核對。完整 durable archive／fenced→fresh review→merge Apply 仍未完成。

驗證：24881 首輪 full run 748 tests／1 failure／6 skipped，故障 case 揭露 journal manifest 未發布仍回收舊 chunks；修保存屏障後 82476 成功。最後 45482 JVM749／0fail／0error／6skip，Desktop classes／JS／Wasm／iOS arm64 編譯成功 1m1s；沒有真實服務、實機 GUI 或 OS kill 驗證。

最新：另已接明確「核對完整記錄並確認恢復」入口。下方唯讀查詢仍不變更pending；只有這個分開確認動作在完整log與fresh ACL/state核對成功後，才保存deletion provenance／原提交ACK及序號並清除pending。不是從receipt摘要臆造payload，也不POST原交易或建立fence。

## 完整已提交交易恢復（新增）

1. 先按查詢，結果committed才出現「核對完整記錄並確認恢復」；點擊後重新查原交易receipt，unknown／fenced或資料不符拒絕。
2. 以原fromServerSeq-1讀正式operations log，limit=200（既有transaction上限），取原range；完整operation數／連續序號／transaction／actor／client／clientSeq／kind／baseVersion／schema／commit時間需一致，不能依目前canvas內容相似認定已提交。
3. 核對原payload supplied values。伺服器可補合法預設欄位，不能改既有值；array保留順序與數量，JSON數字1/1.0語意相等、字串不能當數字。比較深度限64，不遞迴任意深輸入。
4. 再GET metadata/state確認目前ACL、workspace scope及至少包含原commit的head；權限已撤、缺段、payload不符、版本／scope不符或取消，保留pending，不resend/fence。
5. 核對成功後沿既有CommittedDeletionStore及journal acknowledgement順序保存原交易證據、client sequence並清pending，UI載入最新state。ACK使用原交易version／toServerSeq，不從新head猜Undo版本。Settings多key原子／硬體斷電不由本流程保證，history stack不跨重啟恢復。

MockEngine增驗valid／unknown／missing operation／payload變動／baseVersion錯誤／fresh ACL403；成功返回最新head並清pending，失敗保存原wire，全程唯一POST為receipts。另驗normalized payload型別／數字／array。不是正式backend fixture、真實服務或GUI證據。

2026-10-05。APP 已接後端現有 `POST /api/v1/workspaces/:workspaceId/operations/receipts`，不是猜新路由；依 `backend/src/schemas/operation.schema.ts` 與 `services/receipt.service.ts` 實作。不修改／啟動後端，不建立 fence，不向真實服務送測試交易。

## 使用入口及結果

本裝置有未確認 pending request 時，畫布恢復區顯示「查詢原提交結果」。只送這筆transactionId，後端回完整交易。沒有pending不顯示入口，不為驗收製造破壞性交易。

- committed：完整receipt的交易／actor／client／operation ID、kind、clientSeq、serverSeq順序及range符合保存的exact wire。只代表權威提交證據，不是包含payload的完整ACK；不清pending、不更新history／workspace version或deletion provenance。後續沿既有明確恢復入口核對。
- unknown：僅截至查詢head未見提交；可能晚到，不能當未提交、撤銷或替代交易的授權。
- fenced：只讀已有伺服器fence，不是本次建立。當前恢復區停用重送；pending／草稿保留，完整fence→重新review→merge仍未接。
- 403／404／逾時／格式或scope不符：安全顯示無法確認，保留原請求；無fallback猜路由、自動resend或原始error-body輸出。

UI查詢15秒截止；切換workspace／user／client或pending交易後，晚到回應不得套用到新scope。重新建置APP才驗新版入口；編譯不代表目前視窗已更新。

## 嚴格檢查與測試

response恰好一筆transaction lookup；committed恰好一份完整receipt，actor／client／transaction、lookup版本一致、safe integer head／range、連續serverSeq及saved wire逐筆一致。未知status、錯誤type／scope、缺漏／重複／順序錯誤、head過小或其他receipt皆拒絕。unknown／fenced不可夾帶committed欄位或receipt。

BackendSubmissionReceiptTest含validator錯誤矩陣與MockEngine正式repository／Settings pending查詢。只觸及receipt路由，committed／unknown／fenced／wrong-scope／malformed／403均保留原pending，錯誤user／transaction在HTTP前拒絕。不是正式後端fixture、真實ACL或GUI驗收。

## 人工QA

以非重要QA畫布既有pending為前提，保存平台／APP build、結果及安全截圖。確認前後原交易／待恢復數量不變，沒有新增／刪除node、submit／fence、history版本變化。測試環境提供已提交／未知／已fence案例，不在正式使用者資料上造case。

後端關閉或撤權應保留pending並提示失敗；切工作區舊查詢不可污染新scope。手機按鈕、狀態、VoiceOver、遮擋由使用者簽核，效能最後。

## 明確確認的原交易 fence（新增，2026-10-05）

最新追加：PendingFenceAttemptStore已接SessionPreferences，fence HTTP前先保存Unconfirmed並回讀確認；失敗零HTTP。記錄綁scope／transaction及完整wire SHA-256，不保存payload副本。terminal查詢只更新已有attempt，unknown不清限制，conflicting terminal或wire不符／損毀皆fail closed。正常無attempt的唯讀lookup不創建marker；original pending及sequence仍不變。

重新建立preferences/repository會從marker回復resendBlocked，UI與retryPendingSubmission雙層拒絕重送；cached committed亦需完整log確認，不靠重送當恢復。marker保留，不自動GC，也不從本機標誌推定server未提交。這更新下方「只在視窗記憶體」的舊限制，但不是Settings fsync／跨程序排他／斷電原子保證。

測試追加：MockEngine進入時marker已Unconfirmed；fenced／commit勝出／unknown／403／scope或格式錯誤／取消後，以同一測試Settings重新建立preferences及repository，原wire仍在且retry零HTTP。silent write failure零fence，unknown不清標記、digest mismatch／wrong scope／conflicting proof／corrupt marker檢查。這是InMemorySettings重建／讀寫失敗證據，未證明實際OS kill／重啟或四平台durable落盤。

剩餘：原生保存／flush／checked commit／multiwriter與OS restart實驗、marker故障quarantine／匯出修復入口、正式fence→fresh review→merge Apply。遺失marker不能当未fence，新服務查詢仍權威；損毀marker不可自動重置解除防線。QA需新增APP重開后query／retrydisabled與各平台持久性證據，效能最後。

APP 已接既有 `POST /api/v1/workspaces/:workspaceId/operations/fences`。先查詢原交易為unknown，且目前可編輯時才顯示「封鎖原請求…」。第一下只顯示不可撤銷警告／取消，沒有HTTP；第二下「確認阻止原交易提交」才送相同pending transactionId。確認綁定當前scope／原交易，切換scope取消確認。這不是「停止本機重送」：fence會永久阻止原交易提交，取消只在送出前有效。

後端與submit搶相同lock：fenced表示已封鎖；若commit先贏，回committed，APP轉為既有完整log恢復入口，而非宣稱封鎖成功。不接受unknown作fence成功；403、scope／格式錯誤、取消、timeout保留原wire與草稿，不ACK、不clear、不新增替代交易。

UI對送出後結果不明的同一transaction暫停重送；重新查詢仍unknown不能解除不確定性，terminal committed/fenced才更新結果。可再次明確確認同ID fence，但無自動retry。這是當前scope記憶體狀態，不是durable fence proof；重開APP需重新查詢，server fence仍為權威，缺少本機旗標不能證明原交易未提交。

MockEngine驗fenced／commit勝出／unknown／scope不符／403／malformed／engine進入後取消；viewer及錯transaction在HTTP前拒絕，wire及sequence不變，只呼叫fences，不觸submit、ack、merge。取消fixture先等engine進入，再取消，避免短virtual timer在HTTP前取消卻假稱傳輸測試。未發真fence、未改backend，亦非真實DB競爭測試。

人工QA以隔離QA workspace驗第一下／取消零request，第二下exact ID且一個request；warning可讀、unknown不冒充fenced，commit/fence競爭只採server terminal結果，原pending／草稿保持。送出後取消不是server撤銷；逾時後查詢與重新確認另驗。視覺／遮擋／VoiceOver由使用者簽核，效能最後。

完整fenced原交易→fresh review／逐筆選擇→替代merge transaction／Undo仍未完成；不自動清pending，不把stopped archive當server取消。下方先前「尚缺fence」由本節更新，merge及durable缺口仍保留。

## 尚未完成

已接上述明確完整log恢復，不再列成純planning-only。仍缺cold-start結果／多writer原子保存、明確確認後fence與fresh ACL/version的merge Apply、正式跨端fixtures／服務及人工驗收；新流程的真實刪除provenance／跨重啟Undo仍需完整case。receipt本身不含payload或baseVersion，禁止僅憑查詢結果清pending或猜Undo版本。v1備份缺原wire identity的限制不因此消失。
