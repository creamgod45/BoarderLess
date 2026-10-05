# APP 草稿 journal 持久保存接續計畫

日期：2026-10-04。狀態：Implementation plan，尚未接入。此文件不改後端、不宣稱 Settings manifest-last 已具備斷電或跨程序保證。

## Desktop 原子檔案底層接續（2026-10-05）

最終證據：89467 shared JVM／Desktop 全套成功（11s），DesktopAtomicDraftStoreTest 為 6 tests／0 failures／0 errors／0 skipped。真實雙 JVM 競爭與 checkpoint 終止／reopen 已執行；typed bundle、migration 與 production 接線尚未完成。其他平台本輪未重跑，硬體斷電仍未驗證。

已實作 `DesktopAtomicDraftStore`，尚未切換 SessionPreferences／repository：呼叫者提供私密本機 POSIX 目錄（0700），穩定 scope lock 檔與 staging／record 0600。scope 僅接受 64 hex hash；單一 opaque payload 最多 6 MiB，binary v1 envelope 帶 scope／generation／length／SHA-256，損毀、scope 不符、symlink 或超額讀取停止。這不是 typed journal／wire bundle schema。

CAS 要求 expected generation；null 僅匹配首次不存在。空 payload 和 tombstone 不同，tombstone 保留 generation 防止 ABA；不移除記錄檔。每個 cooperating read／write 經同程序 mutex 與穩定 lock 檔，忙碌回報而非無限等待，禁止同 thread 重入再開 lock channel。Java 鎖具平台相依性，採共同遵守協定，不將它當任意程序不可破壞的安全鎖。[Java FileLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html)

發布為 staging 完整寫入→file force(true)→同目錄 ATOMIC_MOVE→directory force(true)。不降級一般 move；目標替換／原子操作可用性依 provider。發布嘗試後任何失敗回 CommitUnknown，保留 staging，必須 fresh read，不盲目重送或換 wire IDs。[Java Files.move](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...))

force 的契約對本機與非本機裝置不同；本輪測試證明此主機的呼叫與 process terminate／reopen，不證明實際硬體斷電、其他 filesystem／OS 或任意目錄同步保證。[Java FileChannel.force](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean))

接線前仍需 typed scope bundle／內容 schema、exact wire／receipt 驗證、v1 migration／reader marker、repository 原子呼叫、異常 UI、quota／disk-full／sync failure 的平台證據。Android／iOS／Web 實作、Windows 非 POSIX 與真正 power-loss 仍 Pending。不得將這個未接線 primitive 宣稱 APP durable queue 已交付；下一階段按下方契約接線，不繼續用格式 fixture 代替。

## 現況與完成範圍

Desktop全域序號接續（2026-10-05）：ClientSequenceAllocator可注入repository，Desktop ledger以API-user-client而非Workspace鍵保存versioned16-byte high-water，使用既有privatePOSIX原子primitive與generationCAS。明確verified legacy floor初始化；missing不由reserve建立0，corrupt／unknown／tombstone停讀不覆蓋。rangeCAS成功後才鏡像Settings並stage，gap保留；unknown／busy／conflict無內部retry。allocator與draft bundle仍為不同記錄，先reservation後bundle可留下gap但不能重用range；production啟用仍需同cooperating協定、舊writer排他、Settings＋pending＋ack／stoppedarchive的完整floor遷移，不能因為有ledger就猜舊floor。Settings mirror不是authority，不以它覆蓋ledger。

repository 接線接續（2026-10-05）：新增 WorkspaceDraftPersistence boundary，BackendWorkspaceRepository capture 該實作而非直接用兩個 Settings stores。預設仍 Settings adapter，Desktop adapter 只在測試 scope 注入；actual repository／MockEngine／file fixtures 驗 stage-before-POST、fault 停送、unknown 結果不盲重送及 ack tail 保存。Desktop stop archive 保留 exact wire，reader 不將 archive 列成可重送 pending，不能 remove evidence 或假稱 server cancel。此 seam 已接但 production cutover 尚未啟用；仍須 legacy-writer 排他／reader routing／bootstrap／其他平台實作與正式 receipts。global clientSequence 仍在 Settings，不能宣稱它與 scope bundle 原子或跨程序協調完成。

typed 接續（2026-10-05）：新增 WorkspaceDraftScopeBundle v1／codec 與 DesktopDraftScopeBundleStore，journal＋exact pending wire 在同一 generation payload 保存；journal 內既有 lastAcknowledgedTransactionId 僅為本機 marker，不是 BAI-012 正式權威 receipt。檢查 scope／schema／UTF-8／6 MiB／depth、共用既有 journal／wire 狀態驗證、head transaction／local operation／base version 對應；拒絕已 ack 卻仍被視為 pending 的 wire，不自行清掉或猜 reconciliation。legacy wire-only 可表達，空狀態用 generation tombstone。未知 typed 記錄不可用 CAS 更新或刪除；Concurrent writer 仍由底層 generation CAS 拒絕。此輪未接 production／migration、未建 reader marker、未改使用者資料，正式 receipt 與 wire payload 對 domain intention 的完整權威驗證仍待接線。

`WorkspaceDraftJournalStore` 使用 Settings、4 MiB／200 operations、checksum、chunk-first／manifest-last。domain journal 與 exact pending wire 分開保存；ack 先保存 journal advancement／receipt，再清 wire。讀寫狀態驗證、損毀停止與 fixture 重建已完成，但不是完整 durable queue。

本輪補 save/load 共用狀態驗證，避免超限版本／sequence／空白 scope 或 transaction ID 寫成無法重讀的資料；manifest 限 512 characters，chunk 累計 UTF-8 bytes 不可超過 manifest 宣告。拒絕或讀取失敗不刪原始證據。這些只證明資料一致性及有界讀取，不提供 fsync、跨程序排他或 power-loss 保證。

接續已補 pending wire 相同 manifest／bytes 防護，journal／wire／backup 共用解碼前 64 depth／framing guard。新 encoded 超深資料不發布，legacy checksum 正確但超深仍保留並停止，不清除或重建 identity。這沒有遷移舊 schema，也不保證 arbitrary in-memory operation construction／apply／serializer 可承受無界深度；後續 storage schema 與 iterative preflight 仍需明確設計。

## 下一階段的 APP storage 契約

1. 以 API base＋user＋client＋Workspace 雜湊作 scope；不寫 access token、signed URL 或密碼。存於平台私密持久資料區，不放 cache、共用下載或臨時目錄。
2. 一個 scope 的 journal、exact wire identity、最後已確認 receipt 與本機 generation 作為原子 bundle。generation 是本機 compare-and-set 標記，不替代 server version／sequence。先原子保存成功才發 optimistic UI 或送 HTTP；不支持時維持停止及備份入口。
3. API 必須包含讀取、帶 expected generation 的比較／寫入，以及帶 exact identity 的移除；取消或 writer busy 不允許部分採用。跨程序／多視窗鎖不得只靠 coroutine mutex。寫入不得自動覆蓋未知或較新的 generation。
4. crash publication 要具體驗證完整新 bundle 或完整舊 bundle，不得讀到混合狀態。所有 bytes／schema／depth／operations 與 checksum 先驗證；同 scope 的 writer／reader／migration／reclaim 遵循同排他協定。
5. 各平台明列 process atomicity、crash atomicity、cross-process exclusivity、power-loss evidence 與持久區可能被系統清除的限制。不用共用 Boolean「已保存」冒充全部保證。Web storage eviction／配額／瀏覽器清除不能當作已解决；Native 或 Desktop 沒有實際同步落盤證據也不能簽斷電通過。

不在此文件指定未驗證的 library／平台原語；選定實作前需核對平台 API 與 filesystem／storage 保證。先完成 store fixture 及 process fault harness，再接 SessionPreferences／repository，避免只提供未使用的 helper。

## 不可打破的提交與復原順序

clientSeq 接續（2026-10-05）：新 wire 的 1–200 個 safe range 預留並保存 high-water 後才 stage，失敗 range不回收；一次mapper不重建operation IDs，ack max advance不倒退。同 SessionPreferences 的 repositories共享鎖，但不同instance／process與Settings fsync無此保證；正式activation仍需全域原子 ledger、legacy pending／ack 的floor遷移及舊 writer排他。服務以operation identity去重且不要求跨提交seq連號，gap不是serverSeq缺口；不能把clientSeq當reconciliation或permission。

restore／quarantine／exact remove 接續（2026-10-05）：pure restore gate 與 Desktop exact removal 已加入；quarantine 保留 head／exact pending，不能再 stage，即使相同 identity 也拒絕。remove 要求 journal ID／expected generation 且無 pending，發布 tombstone 留 generation。late validated ack 保留 quarantine。正式 APP 接線仍需 fresh authority、使用者明確確認及 reader／legacy writer coordinator；此 helper 不提供網路 ACL 或伺服器取消保證。

2026-10-05 接續：pure append／stage／ack transitions 與 Desktop one-shot generation update 已實作，尚未接 repository。LocalDraftAcknowledgement 存 exact submission＋version／serverSeq，使同 ID 不同內容的重複 ack 被拒絕；它是本機已驗證結果的紀錄，不是服務端 receipt／signature 或原提交 settlement proof。legacy 只有 marker 或 wire-only 仍停在明確 reconciliation，不能自動視為 idempotent 成功。下一步仍須 migration reader marker、正式 outcome adapter 與 production 原子呼叫順序。

- append：檢查 current generation／scope／baseline；保留 exact wire 與 FIFO tail；commit 新 bundle後才採用畫布變更。
- submit：新 wire UUID／clientSeq／transaction identity 首次保存後不重建；已存在未確認 wire 不覆蓋。
- ack：只有完整、匹配並通過正式 server outcome 驗證的 ack 才進位。atomic bundle 同時保存新 baseline／tail／receipt 並清該 exact wire；未知 outcome 或 late original 不猜成功。
- retry／recovery：仍由使用者明確選擇及 fresh ACL／scope／baseline gate；不自動重送匯入的 operations。原提交 reconciliation 與 restore 仍依 BAI-011／BAI-012 正式契約，storage atomicity 不解除服務依賴。
- quarantine／本機刪除：保留隔離與二次確認，帶 exact draft ID／generation，不能刪新 writer 已發布的另一筆資料。

## v1 migration 與 orphan reclaim

2026-10-05 captured floor 合併：`CapturedClientSequenceFloor.calculate` 唯讀合併明確 legacy counter、validated legacy pending、typed bundle pending／acknowledged.submitted／stoppedPending 的 wire clientSeq；API base／user／client 相同則跨 Workspace 取 max，其他 namespace 不提高目標 floor，但仍驗證其資料、未知／損毀不跳過。serverSeq／baseVersion 不參與 clientSeq。missing counter 不默認 0；明確提供 0 仍須 caller 證明完整來源。每類最多 256 records、legacy wire 每筆 1 MiB／總 4 MiB／depth 64、typed bundle codec 每筆 6 MiB／總 12 MiB；freeze／decode 後採用，不改 evidence、不初始化 ledger。輸出只是所提供來源的 lower bound，不是 exhaustive capture／writer exclusion／server receipt 證明；complete scope catalog、舊版本 writer 排他、已有 ledger 及來源世代協調／reader routing／bootstrap 仍待完成，不作 production 切換。

2026-10-05 legacy pending 來源盤點：新增 `PendingWorkspaceSubmissionStore.inventoryForMigration(knownScopes)`，在 caller 已排除所有舊 writers 的前提下，唯讀列出所有 `wsp1.` 已發布 manifests，要求外部 scope catalog 完整涵蓋；缺 scope／未知 key／版本／checksum／chunk 不符停止，不忽略另一工作區較高序號。v1 manifest 只含 chunk metadata、不含可發現 scope，雜湊不能反查，不能用「目前工作區」當完整 catalog。最多 256 個 scopes／manifests、總宣告 bytes 4 MiB，讀任何 chunk 前先檢查總上限，保留原件與 orphan chunks、不做 reclaim。這是 floor 來源的 coverage gate，不是 verified floor 或 writer 排他證明；Settings counter、typed pending／ack／stopped archive 與完整 catalog 來源、production capture／reader routing／activation 仍待接。空清單只有未存在任何已發布 legacy pending 時可回傳空，不表示其他序號來源為 0。

fixture migration 接續（2026-10-05）：WorkspaceDraftLegacyMigration.prepare 將已停止 legacy writers 後取得的 validated snapshot freeze／有界驗證，matching wire-before-markSubmitted 的中間狀態只補相同 head，不換 wire IDs／clientSeq、不猜 ack。journal 已 ack 但 wire 殘留、head mismatch／missing wire／scope mismatch 停止並保留來源。legacyImportDigest 和新 bundle 一起 CAS 發布；Desktop adoptLegacy 的同指紋重試只 fresh read 返回現狀、不倒退，來源不同／tombstone／unknown schema 停止。實際 Settings fixture keys 不刪除，發布前／後 fault 測試驗完整舊／新狀態。這是 snapshot adoption primitive，不是 production migration coordinator；legacyImportDigest 不是舊 reader 的切換旗標。正式接線須排除舊版本／多程序 writer、原子來源捕獲與 reader routing，不能因為雙次讀值相同就宣稱排他。此輪不讀寫真實 prefs，不自動 GC／POST／重送。

migration 先取得 scope 排他、只讀驗證 v1，建立新 schema bundle並重讀比對，再原子發布 reader format marker。任一步失敗留 v1、不 POST、不重建 wire IDs、不採用備份 current 作權威。未知 schema 保留原文並停止，不降級或清掉。

orphan reclaim 只在排他維護及完整 committed reference 驗證後執行，不能靠 Settings key prefix 或時間戳猜未引用。active publication／migration staging 不可回收；讀到損毀或 unknown schema 停止回收、保留診斷證據。先 dry-run 列精確 scope／generation／候選數，再接可恢復清理；不得掃除其他 APP preferences 或其他使用者資料。

## 必要證據與接線門檻

- store unit tests：CAS／scope／invalid state、writer busy、bounded decode、old/new publication、exact wire identity、FIFO ack／receipt dedup、quarantine／exact remove。
- fault injection：每個 write／publication／sync／ack／migration 邊界前後失敗；新 process 重開驗舊或新完整資料，不以同一 InMemorySettings 重建代替。
- 實際平台 harness：兩個獨立 writer 競爭、process terminate、磁碟滿／配額／權限／I/O 錯誤、reopen；physical power-loss 單獨列證據，不能用正常結束代簽。
- repository 接線：保存失敗不 optimistic、不 POST；ack 不丟 tail；未知 outcome 不自動 resend；scope／role 改變晚到結果隔離。
- 使用者 SOP：乾淨 QA scope、備份先行、只使用 fixture，記錄平台／版本／重開／失敗步驟；不在真實 Workspace 或私密 prefs 人工破壞資料。

整體 journal durability 與原六項產品目標仍 Pending，效能最後。視覺／VoiceOver／遮擋仍由使用者簽核。
