# 當前 loop 剩餘目標

## 2026-10-13：四平台舊方案簿明確恢復宿主（源碼已接、未驗證）

- 核對 Android／iOS／Web 已傳 private persistent／awaited library，並非退 Settings；原缺口是舊索引定義無明確恢復入口。新增 lazy legacyQuickSchemeCatalogReader capability，由四平台 App 宿主提供，Workspace 優先 host reader、再 repo reader；Desktop Local 也可審閱，不限 Server。
- factory／開窗不讀取、不寫入、不 HTTP；手動讀取仍沿既有有界 indexed catalog、逐筆原 JSON／副本／預覽審閱、來源重讀與 identified save。缺 provenance／未知 schema 拒絕，不猜目的 workspace，不自動合併 mode namespace／搬移素材／刪舊 Settings。UI 標題改裝置而非 Desktop 限定。
- 這是 source 接線，不代表四端恢復或跨裝置同步已驗收；Settings 預設來源仍須各平台實測，不證明完整無索引 key catalog／all-writer 排他。未 compile／test／launch，六主線 active，業務完成後集中验收、效能最後。

## 2026-10-13：雲端方案匯入本機結果與即時清單回饋（源碼已接、未驗證）

- CloudSchemeDialog 在 stageIdentifiedSave 前凍結原 QuickSchemeSaveIntent／requestId；保存不明時固定區顯示原 ID 與 pending import 恢復指引，禁止切換 resource／新匯入／雲端 mutation。關窗不撤銷／刪除原 intent。純原 payload 與 availability 審閱分頁仍可用。
- saveIdentified 回覆並核 scope 後才標 localImportSaved，立即回呼 Workspace 刷新方案簿並選新副本；清單 refresh 與關窗 refresh 皆核原 draftReviewScopeId。確認保存後明確繼續瀏覽，清 selected／review，不沿舊選取重配原 requestId。outbox tasks read 失敗不抹掉 local import 的獨立確認状态。
- 僅定義副本，不下載／materialize 素材或插畫布，多 source 限制與來源 authority 不變。未 compile／test／HTTP／跨裝置實測，六主線 active；全部業務完成後集中驗收，效能最後。

## 2026-10-13：連線插入節點原交易恢復入口（源碼已接、未驗證）

- RelationInsertionDialog 不再以 Boolean 混用 preflight 拒絕與送出不明；Rejected 只來自 executeAwaited 前已確認不送出，需重新 consent。Queued 只代表原操作已交 queue，不是 server ACK；false／取消／exception／跨 scope 回饋為 Unconfirmed，固定原 plan IDs 並凍結欄位與再次 consent／Apply。
- 未確認固定區提供 captured owner 的中央原交易審閱入口，關窗只取消目前 coroutine 不撤銷已提交交易。Close／saving／unconfirmed 留在固定頂區，預覽／欄位放獨立 scroll，長內容不把關閉推至末尾。原 split relation 交易、Undo 與 endpoint 不改。
- 未 compile／test／launch／HTTP，六主線 active；全部業務完成後集中驗收，視覺／遮擋由使用者判定，效能最後。

## 2026-10-13：素材 processing 明確有界狀態觀察（源碼已接、未驗證）

- v2 completionAccepted 顯示「持續查詢原素材處理狀態」，使用者明確啟動才最多 20 次 status read、每次完成後間隔 3 秒、60 秒上限；離開 processing 即停止，read failure 不自動重試。取消／scope 失效保留原 reservation，不 prepare／ticket／PUT／complete／abandon 或插 Node。
- 核原 requestId／digest／identity／ready asset descriptor；新的 identity／lease 仍沿原 durable 記錄保存，相同內容改確認原 durable generation 未變，不反覆 CAS。並行改動拒絕，不採快取覆蓋；觀察結束不造 ready，ready 仍另按插入。
- 唯讀／本機操作一般失敗提示與 networkWrite 未確認分開，不說讀取失敗就是新上傳失敗。未 compile／test／HTTP／launch，六主線 active；全部業務完成後集中跨端驗收，效能最後。

## 2026-10-13：AI 理解驗證讀取既有 Desktop 設定（源碼已接、未驗證）

- 本輪唯讀檢查安全 log 最新仍 2026-10-09T02:29:52.408199Z OpenAiChat Completed；沒有新的失敗可歸因，不代發付費請求或將舊完成紀錄當最新 APP 驗收。
- 理解驗證 configuration 提供明確讀取圖形工具已保存非機密設定；不讀 vault token、不保存／改設定、不 HTTP。開始讀取即清目前 token／consent／answer，保留舊診斷並標為前次；loaded 後仍審端點／協定／參數、手動認證與本次授權。取消／close 不採晚到回覆；未知協定／auth／limit 欄位或過長欄位拒絕，不默默選預設協定。
- Desktop settings 實際讀取限制 64KiB+1 偵測字元，拒 symbolic link／非 regular file、非法 UTF-8 與非 strict JSON；null 仍無法區別 missing／invalid／inaccessible，UI 不宣稱必然尚未保存。其他平台 store=null 不冒充有持久設定。未 compile／test／launch／AI request，六主線 active，最後集中驗收、效能最後。

## 2026-10-13：原 writer 釋放恢復 App 入口（源碼已接、未驗證）

- WorkspaceScreen dispose 將原 repository 交給 App 保留；close 拋錯不再在有宿主時直接丟失原 handle。具 owned-drain capability 的原 repository 即使 close 當下未拋錯也保留，涵蓋背景 operation 稍後才失敗的釋放。候選 media 構造失敗時的 cleanup 同樣保留原候選。
- App 僅自動等待原 owner 已記錄的操作退出，無寫入／HTTP／release retry；成功才移除原 handle。失敗／取消不宣稱已釋放。畫布編輯／Presence 與模式切換在保留期間受限，可關閉／重開中央恢復視窗；明確「查原排空」或「僅重試原失敗釋放」，後者只依原 owner Failed 狀態開啟，仍須 await 成功。
- 無 capability 的 close failure 保留且提示無法證明，不空成功。此保留只屬目前 App 生命週期，不是跨重啟 durable handle、全 writer 排他、網路排空或 migration cutover。正式 capture／publication／activation／scope 註冊仍待接，未 compile／test／launch；六主線 active，最後集中驗收、效能最後。

## 2026-10-13：原 repository writer drain 與釋放恢復介面（源碼已接平台、未接遷移宿主）

- BackendWorkspaceRepository 提供 awaitOwnedWriterDrain／retryOwnedWriterRelease，必須先 close 原 repository，且 factory 必須提供原 owner callback；沒有空成功 fallback，不建立新 owner、不重試 HTTP／mutation。closeRequested 阻止關閉後的 prepareWriter，含 await 後再核對。
- Android／iOS／Web factory 綁原 ServerRepositoryWriterCoordinator owner，await 等既有 operation accounting 完成與釋放成功；Web 沿既有 IDB physical ticket，取消 continuation 不冒充實體交易終止。重試只針對該 owner 已失敗的釋放 callback，之後仍須 await。Desktop 未提供此 capability，不假造空 drain。
- 此介面不是 activation logical-call drain、HTTP task drain 或所有 Settings／舊版 writer 排他證明。真正 capture／排他／publication／activation／scope 註冊／App cutover 宿主仍未完成。未 compile／test／launch／HTTP，六主線 active；業務完成後集中验收，效能最後。

## 2026-10-13：Desktop 舊方案簿逐筆審閱匯入（源碼已接、未驗證）

- server native library 增加明確舊 Settings 唯讀入口；開窗不自動讀取。讀取已索引記錄有 count／單筆與總 UTF-8 限額，缺 payload 但殘留 metadata 拒絕，不把半寫入記錄視為刪除；不宣稱涵蓋所有無索引 key。
- 逐筆顯示結構預覽、原 JSON 與明確版本的副本 JSON，分頁顯示完整內容；未知 schema／素材缺來源拒絕，不猜 workspace、不下載素材、不插畫布。保存前重讀來源比對，使用者明確授權後 stage／save 同一 requestId。首次寫入起凍結再送，未知結果保留原 ID，交既有 pending identified import 恢復；關窗不撤銷，不刪舊內容。
- 目前僅 Desktop server 宿主提供 reader，Local 與其他平台未自動接舊 Settings。來源比對不是 all-writer 排他／快照證據。未 compile／test／launch；六主線保持 active，全部功能完成後集中驗收、效能最後。

## 2026-10-13：Desktop 共用儲存宿主與server方案簿（源碼已接、未驗證）

- Desktop改傳WorkspaceStorageRuntime，Local／Server與media runtime走共用App交接，補Desktop activated factory。普通啟動Local，明確 --server 以initialMode保留原行為，外部request的mode優先；不採上次Server偏好自動連線。相同模式選擇no-op，不重建repo。
- server native factory提供private DesktopFileQuickSchemeStore於server-v1/schemes，CloudSchemeRuntime入口不再因fallback legacy Settings消失。Local維持local/schemes，兩者不合併，舊Settings方案不刪／不自動搬移；舊library的明確匯出／匯入與UI說明仍需處理，不能把新namespace空清單當舊資料消失。
- desktop media下載Local只取local gateway，Server取該repo configured syncAssetGatewayFactory，不默默退default endpoint。新runtime構造不HTTP，不self-activate；完整migration／ownership transfer宿主仍待接。未compile／test／launch，六主線active，最後集中驗收。

## 2026-10-13：AI UTF-8 新片段增量驗證（源碼已補、未驗證）

- AiUtf8TextBuffer改StringBuilder＋pending high surrogate＋reservedBytes，新fragment只掃新增字元；完整檢查後才commit，非法／超量fragment不變更原text／pending／bytes。finish仍拒絕未配對字元；已有4bytes預留的pair在下一delta配對不重算／重加。
- appendDelta只回傳新完成字元，tool decoder的text／argument buffer不再每個delta复制所有前文；understanding UI仍需整份文字snapshot，沒有宣稱整個UI零复制或效能實測。strict SSE JSON入口／128KiB text／64KiB單call／1MiB總argument限制保持。
- 未compile／test／AI request／benchmark，完整六主線active；功能完成後集中 correctness驗收，效能測試最後。

## 2026-10-13：共用背景／網格未確認原交易入口（源碼已接、未驗證）

- ColorPickerWindow增加optional onReviewPending，僅readOnly／非busy顯示，不影響其他選色器預設。背景首次Apply凍結後可close本picker並核captured owner導向中央原交易／草稿審阅；原operation、style CAS與durable journal不改。
- CanvasGridStyleDialog首次attempted／非busy同樣提供固定区審原交易，關閉只清grid preview，不重配style operation或重新Apply。原role／scope檢查與正式Undo／Redo維持。
- 未compile／test／network，六主線仍active，所有功能完成後集中跨端／取消／Undo恢復驗收，效能最後。

## 2026-10-13：鋼筆／時序圖未確認直接審原交易（源碼已接、未驗證）

- PenCanvasDialog→PenEditorDialog 與 SequenceCreationDialog增加onReviewOriginalSubmission；首次commit結果不明後固定區提供中央原交易審閱入口。只關當前editor，再核captured owner user/client/workspace，以既有local record reload定位原交易；AI也改共用相同guard。
- Sequence已commitAttempted的Close不再顯示「丟棄草稿」語意，僅關UI，原可能retain的journal／wire／receipt不變。commit前仍用原discard確認；沒有直接retry／替代identity或隱含resend。
- 未compile／test／network，六主線active。正式scope切換／metadata未保存／retained transaction驗收最後集中，效能最後。

## 2026-10-13：原交易中央審閱入口（源碼已接、未驗證）

- OriginalSubmissionReviewDialog 提供saved原transaction／draft ID与count、原status，明確 local record reload／read-only receipt query／開既有draft review；無resend／Fence／delete／restore或新identity操作。中央safe-area視窗，busy／scope／queue guard、Close保持可用，開啟時隱藏背景status並擋canvas input。
- 接底部pending狀態按鈕與AI unconfirmed固定區按鈕；AI只close本身UI再核原actor/client/workspace，明確觸發既有原record讀取，不移除durable記錄。草稿按鈕走既有reviewPendingDraft，收據查詢仍inspectPendingReceipt(false)，不拿receipt當fullACK。
- 功能仍需跨端與恢復fault驗證，沒有自動寫入／HTTP；本輪未compile／test／network，六主線active，最後集中驗收。

## 2026-10-13：原 writer 釋放失敗明確恢復／Web Lock 事實更正（源碼已補、未驗證）

- 實讀server-writer-lease.js確認同tab token以owners Set共享已持有的WebLock；另一tab ifAvailable失敗、不等待或steal。前項關於本tab取得同名第二鎖可能deadlock是待核疑慮，目前實作已避開此點，不能繼續當缺口。仍不證明direct Settings／舊build／完整capture已排除。
- ServerRepositoryWriterCoordinator.Owner.retryFailedRelease明確重試原idempotent onDrained callback，只有original active owner、closed／drained／operations=0且settlement Failed才允許。保持closed、不開writer、不reset操作數、不建立候選或auto retry；成功才清原active，仍需awaitClosedAndDrained確認。
- 實際migration宿主須接原owner的明確cleanup恢復，不能因Failed直接重新claim。當前sourcehook尚無UIcaller／故障驗證，不宣稱正式協作交付；未compile／test／network，六主線active。

## 2026-10-13：activated repository／media／lease 生命週期（源碼已補、未接宿主）

- 新 ActivatedServerInstallation 先準備原 activation的repo＋media runtime，再單次adopt；closed／已adopt／錯scope不能轉移第二次。prepare失敗僅closecandidate，原activation與排他權仍由宿主持有，不能重造legacy repo假rollback。
- closeAndDrain狀態機依序stop activation新calls→repo/network close→logical calls drained→宿主真all-store physical drain→idempotent exclusion release。物件保留原階段，取消／exception不能當Released，explicit retry只續原close，不造另一installation。physical drain／release mandatory無空預設。
- 仍無宿主caller／真platform callbacks，未完成all-writer ownership transfer：目前工廠取得自己的平台lease，宿主必須處理與migration排他lease的實際轉移／共用，不能持同一WebLock又等另一候選acquire，亦不能提早release authority。此交接仍為核心待辦，不因lifecycle類存在宣稱WS啟用。未compile／test／network，六主線active。

## 2026-10-13：已啟用 submission runtime 平台工廠交接（源碼已補、未接宿主）

- Android／iOS／Web／Desktop server repository工廠可接明確 AwaitedSubmissionRuntime；普通啟動仍null、不自行初始化ledger／migration。Android／iOS／Web WorkspaceStorageRuntime提供對應factory，Desktop直接factory提供參數（Desktop宿主尚無此runtime切換流程）。
- createActivatedServerRepository宿主入口要求原ActivatedAwaitedSubmission＋registered scope；factory前後核活躍lease，要求真正Backend adapter持有同一runtime、同API base／user／client／workspace偏好。錯配關candidate，不fallback到legacy，不close／另造activation。正常repository仍需prepareServerWriter真ownership，不拿建構成功當已持有writer。
- 此入口仍無App宿主caller。all-source capture／真all-writer排除、舊repo logical＋physical drain、publication→activation、scope registration／runtime swap與lease全生命周期仍待接，不宣告正式WS已启用。未compile／test／HTTP，六主線active，最後集中驗收。

## 2026-10-13：跨端素材 MIME 與錯誤提示一致（源碼已補、未驗證）

- 核對普通picker、iOS Photos representation／Files、backend models/schema/inspect：正式原檔只有png/jpeg/webp/gif/mp4/webm。v2 WireRequest移除額外quicktime白名單，改共享mediaKindForAssetMediaType且要求canonical trim／lowercase，不與普通import格式規則分岔。
- v2 dialog AssetImportException用typed issue提示UnsupportedFormat／TooLarge／InvalidFile／Processing，不再全部稱原檔不符；網路write流程錯誤仍附未確認／原request恢复说明。Unsupported UI列格式與HEIC／MOV轉表示需求，不改副檔名冒充mime、不自動轉檔／重配digest。
- 非支援的舊wire／digest若存在仍需明確恢復，不自動改metadata或删record。没有聲稱重現使用者iOS故障／完成轉檔支援；未compile／test／HTTP，六主線active、最後集中驗收。

## 2026-10-13：時序原始碼格式變動保留語意 IDs（源碼已接、未驗證）

- MermaidSequenceAdapter新增 parseKeepingUnchangedIdentity：先正式parse／validation，再逐欄比較parsed語意，participants角色／別名／標籤與step順序／kind／dashed／text／branch結構完全一致時沿用原 frozen draft IDs。不因縮排／合法註解／等價supported語法導致 message／block ID 重建；不只比較export文字以免漏掉export無法表示的欄位差異。
- SequenceCreationDialog的source→preview／guided回切都經此adapter；真正語意改變仍用parser新draft，不以位置猜測identity，不忽略unsupported語法。既有編輯器no-op與外部dependency／lock／whole transaction CAS保持。
- 未compile／test／network，完整六主線active，最後集中驗收、效能最後。

## 2026-10-13：連線 geometry 與祖先鎖定一致（源碼已接、未驗證）

- 共用 relationEndpointsUnlocked檢查source／target及完整Group祖先，locked／missing／cycle拒絕。RelationInsertion原相同規則改用helper，geometry gesture begin／屬性面板Apply／自循環建立同步套用，route／label／loop handle畫面停用並顯示原因。
- 不改relation文字／方向語意、歷史restore或backend ACL；鎖是APP geometry操作規則，既有full baseline CAS與gesture cancel維持。Undo／Redo原operation恢復流程未繞過server版本確認。
- 未compile／test／network，六主線active；最後集中跨端pointer／fault驗收，視覺由使用者、效能最後。

## 2026-10-13：AI proposal 正式交易結果分離（源碼已接、未驗證）

- AiDiagramApplyOutcome分成 Queued／Rejected／Unconfirmed；Rejected僅提交前scope／pure review拒絕，executeAwaited false改Unconfirmed，不拿原可能retain的交易當未寫入。Queued仍只表示本機／同步隊列交接，不當server ACK。
- Unconfirmed／取消／未知exception凍結原proposal，禁再次Apply、選取變更／discard及設定重置，固定header提示先核pending／draft recovery。已知Rejected解除凍結但不保留舊Apply consent，可重新審閱。原operation／history不刪、不另配替代transaction，不以Close聲稱rollback。
- 本輪未compile／test／HTTP，六主線 active，所有业务完成後集中驗收。

## 2026-10-13：AI 所有工具輪次逐次 exact-request 授權（源碼已接、未驗證）

- 對齊現有 goal「每次授權」：移除 AiDiagramToolDialog／AiChat 的後續輪自動授權 UI／branch，round 1..8 全部 gate.await 原 immutable review；沒有用首次同意概括之後未見工具結果的新 body。原有一次性 respond identity／cancel／scope guard保持。
- request card actionable除 Pending／round 外還核完整 body、endpoint、model，不能只因輪次相同就將舊卡片當當前 review。流量／費用由使用者逐輪批准；拒絕仍停止、隔離草稿關閉，不自動重試。
- 先前歷史文字提及選擇 autoApprove 的行為已被本項取代；未compile／test／AI calls，六主線active、最後集中驗收。

## 2026-10-13：雲端方案圖形預覽與素材來源審閱（源碼已接、未驗證）

- selected remote resource 接 QuickSchemePreview 結構圖（僅 placeholder／vector／sequence，不 fetch素材），原完整 JSON review 保留；assetAvailability按8筆分頁顯示 original／thumbnail 的 assetId／sourceWorkspaceId與 available／denied／unavailable分類。可用不是 ACL／materialization／Ready 插入證明。
- cloudSchemeLocalCopy集中驗 ClipboardPayload與單一來源限制，UI與保存時用同一份正規轉換；多workspace來源或不支援payload禁metadata copy並顯示原因，不丟來源、不下載原檔、不把本機保存當畫布插入。仍保留 identified save request原receipt。
- 跨 workspace materialization後端能力仍 false，本輪沒有假接通。未compile／test／HTTP，六主線active，所有業務功能完成後集中驗收、效能最後。

## 2026-10-13：新素材單次授權完整上傳流程（源碼已接、未驗證）

- v2 modal 新增一般流程按鈕，一次明確授權 prepare／原 ticket／PUT／complete，各步仍經既有 coordinator durable intent／identity／lease／source validation。controller 先讀原 reservation，僅 never-attempted（無 identity／prepare／ticket／PUT／complete intent）可用，不拿 UI 空狀態當證明。
- 顯示原 upload progress 與每步結果，任何取消／失敗立即中止並回 needsLookup，不自动重送／換 request／ticket。completionAccepted 停在處理中，由使用者明確 lookup；Ready仍需明確 canvas insert，非 Prepared 完成後 detach 原 native handle並按既有 cleanup 規則釋放，失敗提示不重用。
- 分步恢復仍保留，不以一般快捷流程繞過原 receipt／lease deadline。未編譯／測試／HTTP，六主線仍 active；全部業務完成後集中驗收，效能最後。

## 2026-10-13：鋼筆原交易保存不明凍結（源碼已補、未驗證）

- PenCanvasDialog 首次正式 commit 邊界即 publicationAttempted，失敗回 Unconfirmed，不再直接重試同 Apply callback；prepared 原 operation 保留，不換 path／text／placement 或 ID。preflight Stale 與 Invalid 仍為未提交，可關閉重審／修正。
- PenEditor Unconfirmed／取消／未知 exception 凍結 attemptedPath，禁止重新 Apply 和編輯；固定區显示可能已保存／提交與 Close。Close只關 UI，不走「丟棄」語意、不刪 durable journal／receipt，後續核正式草稿恢復。保存前 Invalid 不凍結；未變 no-op 仍 Saved 關閉。
- 未編譯／測試／network，完整六主線 active，全部业务完成後集中驗收、效能最後。

## 2026-10-13：雲端方案簿取消後重讀原任務（源碼已補、未驗證）

- CloudSchemeDialog 初開先明確讀本機 durable tasks，不把空 UI 清單當無待確認任務。送出／原 key 恢復前即清遠端清單、selected ETag、review／conflict；取消不能留下舊 snapshot 可直接另寫。
- tasksNeedRefresh 在取消／讀取失敗時阻擋其他操作，固定區提供重讀本機任務與提示，Close／Stop 保持可用。只有成功讀原 scoped outbox 且 scope 仍有效才恢復；重讀不 HTTP、不 resend、不產生新 key。成功／錯誤後現有 task refresh 同樣核 scope。
- 原 Sending／Unknown deadline 與 key/body/ETag 恢復限制保持，不聲稱 GET readback 證明原 mutation 成功或失败。未compile／test／network，六主線未完成，集中驗收最後。

## 2026-10-13：密集連線標籤避讓優先序（源碼已補、未驗證）

- 自動 label placement 由加權總分改 lexicographic：先減少其他標籤重疊，再減少 node obstacle，再減少 route crossing。多條線的數量不會加總成足以讓 solver 選擇疊在另一個 label 上；同分保持原候選順序／stable relation ID。
- 手動 label bounds 獨立作 reservedLabels，與 auto label 同一避讓層级並保留 gap，不再當一般 node 障礙；手動位置不被 solver 改寫。畫布／匯出共用 measured placement，僅presentation、不生成 geometry／history mutation。
- 密集畫布仍 best effort，不宣稱永遠無遮擋。未編譯／測試／視覺驗收，六主線保留；全部業務完成後集中驗收，視覺／遮擋由使用者、效能最後。

## 2026-10-13：遷移完整資料本機審閱（源碼已補、未驗證）

- 現有可開啟的 LegacyMigrationPreviewDialog 接完整 frozen scope packet browser，可選範圍並逐 4096 UTF-16 字元頁檢視 draft／pending／stopped／fence／deletion／floor，邊界避開 surrogate pair。資料只來自原 review，不因翻頁 recapture／write／HTTP／log，不截斷成摘要取代原資料。
- SubmissionMigrationDialog 同樣增加逐 scope 與完整資料分頁；plan 的原 wire／source／audit／legacyImportDigest 改 strict UTF-8，不用替代字元產生摘要。既有雙確認／原 receipt／guard 不放寬。
- 正式 publisher／activation dialog 仍無平台宿主 caller，真正 all-writer 排除與 runtime cutover 仍未接，不能把唯讀 preview 當 migration 或正式 WS 已啟用。本輪未編譯／測試／network，六主線 active，最後集中驗收。

## 2026-10-13：素材插入原交易未確認與 scope 回饋（源碼已補、未驗證）

- insertStoredMedia 區分 fresh GET／建立 node 前失敗（可重新讀）與正式 executeAwaited 邊界後失敗／取消（原 transaction 可能已 retain／commit）。後者專用 unconfirmed 提示先核待確認交易／draft review，不能建議直接再插入新 ID；不刪素材，不自動 retry。
- 完成、錯誤與取消回饋只作用於原 user／client／workspace；晚到回應不改新 canvas selection。import finally 只清自己 job 的 busy／label，使用 lazy job 先設定 ownership 才啟動，避免同步 GET 提前完成造成 busy 卡住。
- v2 原 reservation／lease／receipt 仍保留並可明確恢復；此處未增加假 restore／ACK，未改後端或 durable protocol。未 compile／test／network，六主線仍 active，最後集中驗收。

## 2026-10-13：AI 工具回覆／參數嚴格字節累積（源碼已補、未驗證）

- 工具 decoder 的文字採 AiUtf8TextBuffer，只向 UI 發布完整字元；UTF-8 上限 128 KiB。各 call 原始 arguments 上限 64 KiB，總量 1 MiB，累積實際 code point bytes，未完成高代理字元預留完整 4 bytes，不用寬鬆 replacement 計數。原 SSE envelope／strict JSON 字串驗證保持不變，原本非法 JSON frame 仍直接 Format，不放寬入口。
- completion 必須確認文字／參數配對完整及 strict tool JSON，超量分類 Limit、非法分類 Format；failure／clear 釋放累積內容。每輪完整 reply 後才進 engine.execute，取消／失敗仍 close 隔離 draft；不提供 partial proposal。任務輸入嚴格 64 KiB，拒絕有專用 UI 提示，保留原任務。
- 尚未編譯／測試／送 AI 請求；不是 Claude／OpenAI Router 或 Apply／Undo 驗收通過。六主線繼續，全部業務功能完成後集中驗收、效能最後。

## 2026-10-13：AI 理解串流 UTF-8 嚴格累積（源碼已補、未驗證）

- 讀本機安全 log 最新仍 2026-10-09T02:29:52Z Completed／OpenAiChat／HTTP 200，沒有本日新失敗證據；不把歷史成功當新版／Claude／工具 Apply 已驗收。
- 理解 probe 改用 bounded AiUtf8TextBuffer：按 Unicode code point 計 UTF-8 bytes，跨 delta 的 trailing high surrogate 保留待配對、不顯示替代字元；非法配對／結尾未配對回 ResponseFormat，超 128 KiB 回 OutputLimit，正常空白完成仍 EmptyResponse。失敗不宣告 Completed、不自動重試。
- 手動貼外部理解回覆使用嚴格 UTF-8 上限，非法／超量明確提示並保留原有效回覆，不靜默修補。未測試／編譯／呼叫 AI，六主線保留，所有業務功能完成後集中驗收。

## 2026-10-13：GIPHY 查詢結果原子替換與取消回饋（源碼已補、未驗證）

- 新搜尋／熱門請求成功前保留原清單、已選預覽、原 issuedQuery 與 nextOffset；成功後才共同替換結果來源及分頁。不再於送出時清空或重新標記舊清單。pendingQuery／pendingOffset 與已顯示來源分離，顯示載入、取消及結果來源／筆數。
- 取消／失敗保留原頁、無自動重試；generation 阻止晚到回應覆蓋，查詢開始使舊 preview refresh 失效，未知 refresh exception 顯示安全 unavailable。API Key／locale 變更仍明確清除舊 session，不跨來源延續 offset。
- 本輪只有源碼閱讀／修改，未呼叫 GIPHY、未編譯／測試。六主線仍 active；全部業務功能完成後集中驗收，視覺／遮擋由使用者判斷，效能最後。

## 2026-10-12：共用背景選色器保存回饋與原操作凍結（源碼已接、未驗證）

- ColorPickerWindow增加可選readOnly／statusText，預設不影響其他color picker流程。背景首次Apply後保留原operation並read-only，不再更改預覽或重複Apply；busy／pending／unconfirmed直接在當前選色器顯示，非busy仍可Close。失敗不聲稱rollback、不配置替代操作。
- stale style顯示選色器內錯誤，關閉只清UI preview，原durable/pending仍沿恢復；open／close重置訊息。仍用正式style CAS／history，未改新色盤算法或後端。未compile／build／test／HTTP，六主線未完成，功能完成後集中驗收、效能最後。

## 2026-10-12：時序編輯鎖定／no-op／未確認保存（源碼已補、未驗證）

- 編輯核心核container及祖先、變更object、participant語意、block子樹、message端點與位置語意的鎖，不能用temporary metadata clear繞過。未修改的locked項目不一律阻擋其他不相關編輯。空白container title保留，暫時layout生成值不寫回；完全相同draft直接關閉、不改版本或配置交易。
- 首次進commit後，失敗／取消保留原prepared交易並停用再次Apply，提示先檢查draft recovery／sync；不以未確認當rollback，也不另造ID。scope precheck失敗仍不派送。未compile／test／HTTP，六主線active，所有功能完成後集中驗收、效能最後。

## 2026-10-12：migration 宿主排除界線與取消状态（源碼已補、宿主未完成）

- 核對ServerRepositoryWriterCoordinator：只涵蓋合作repository，明示不涵蓋direct Settings／舊build，不能冒充all-writer migration quiescence；WorkspaceStorageRuntime目前也沒有publication／activation宿主factory。未以空guard或一般claimAfterDrain開正式migration，未改平台legacy資料。
- SubmissionMigrationDialog的發布／啟用取消新增「可能已保存、保留原收據」提示，固定title／status／stop／close於捲動外；review內容內部捲動。取消不清publication、activation receipt或原source，不當rollback、無自動retry。此視窗仍無實際宿主呼叫，不宣告啟用完成。
- 後續仍需真正排除direct Settings／所有平台writers、完整scope/archive capture、新scope registration與可回復runtime cutover。未compile／test／HTTP，六主線active，集中驗收與效能最後。

## 2026-10-12：多人 WS 寫入缺項顯示與宿主接線核對（源碼已補、未驗證）

- 重新搜索：SubmissionMigrationDialog目前僅有宣告，沒有宿主呼叫；不能把manager／publisher或preview已存在視為正式activation完成。未解除physical writer exclusion／全scope capture／新scope registration條件，沒有偷偷建立ledger或切runtime。
- Backend canSubmitRealtime的既有三條件改共用typed missing set（checkpoint／sequence ledger／atomic recovery）；成員面板顯示實際缺項。不改放行規則、不用connect初始化、不宣告REST bridge即正式WS寫入；missing為空也仍需當前Live／scope／角色／pending／fence guard。
- 宿主publication→activation→repository切換仍為實作待辦，不是外部等待或已交付。未compile／test／network，六主線active；最後集中驗收、效能最後。

## 2026-10-12：v2 原檔 handle 交接／釋放失敗（源碼已補、未驗證）

- 發現視窗原先先await release再清source，若release取消／丟exception，UI會保留可能已釋放handle而再次PUT。改先detach actionable source，再以NonCancellable＋bounded release保存cleanup結果；失敗handle留待清理清單，不重用，顯示專用文字與明確重試本機清理。清理未完禁止再選檔／切原檔／PUT，不刪request或server素材。
- 新選檔先驗原metadata，交接cleanup後再核coroutine／controller scope才接入；若交接中取消，新檔也釋放，原UI handle已移除。dispose等running job退出，釋放當前及已detach但待清理handle；release結果未確認仍不可宣告完全清理，最終native故障／temp處置需最後驗收。
- 未build／compile／test／HTTP，六主線尚未完整交付；跨端picker cancellation／resource failure驗收與效能最後。

## 2026-10-12：素材恢復紀錄非破壞歸檔／重新顯示（源碼已接、未驗證）

- PickedAssetUploadStore discovery index新增可逆archivedRequestIds，可讀舊未含欄位紀錄；CAS＋confirm＋readback只更新本機可見性，保留原request／metadata／lease／意圖歷史，不刪檔或server asset、不清capacity。原request保存successor保留既有歸檔標記，archived IDs必須唯一且屬該scope索引。
- 視窗active／archived清單各可分頁與手動讀取，selected record歸檔前重新lookup原preparation，僅ready／rejected／missing／abandoned允許；未知、prepared、completionAccepted不歸檔。Unarchive只本機改可見性，不HTTP／重傳／復活。local CAS或confirmation不明顯示專用訊息，要求重讀，不聲稱rollback；signed ticket仍不持久化。
- 尚存長期容量缺口：歸檔不是GC，索引仍最多1024筆／512KiB；需分段索引／長期保留設計才能完整解除capacity限制，不以此歸檔UI假裝完成。舊APP可能不認新增欄位而拒讀，不能repair／覆寫新索引。未compile／test／HTTP，六主線未完成，業務功能完成後集中驗收、效能最後。

## 2026-10-12：全部畫布入口的 atomic operation 預檢（源碼已接、未驗證）

- Canvas同步／awaited共同入口採repository實際flattenAndExpand數量與atomic limit，涵蓋貼上／方案／AI草稿／Undo／Redo／一般操作；超限在history publish、draft retain、queue／request allocation前返回false並显示數量／限制，不清原stack、不拆原子交易。local-only沒有remote限制。
- Backend validateRetainedDraft同樣預檢，保護直接sync／awaited／history draft保存呼叫者；pending preparation已有第二層正式wire數量限制。僅核operation count，不冒充CAS／ACL／restore陣列／byte容量／schema完整驗證。舊保存過大草稿不可用換ID／裁剪／拆批清除，仍保留原恢復審閱。
- 大型時序容量仍待BACKEND_SEQUENCE_ATOMIC_CAPACITY_SPEC正式交付；其餘跨端runtime／素材歸檔／正式auth等主線缺口維持。未compile／build／test／HTTP，業務功能完成後集中驗收，效能最後。

## 2026-10-12：時序編輯操作合併與正式 batch 上限預檢（源碼已補、未驗證）

- 讀後端 operation.schema 與 canvas-mutations：REST最多200 operations，sequence metadata CAS每筆執行、graph bindings在整筆transaction末驗證。未修改後端或執行HTTP。時序editor的transform合併為一個多物件操作，text／group變更成批保存，版本以模擬後狀態依序取，不無效增加operation數。
- CanvasWorkspaceRepository新增實際展開count／atomic limit介面；Backend沿正式flattenAndExpand計數，200上限於pending preparation前檢查。時序建立／編輯在保存草稿前顯示展開數與上限，超限不凍結prepared、仍可回source修正；不自動拆transaction，不裁剪graph。local-only無remote limit。
- 明確剩餘契約限制：最大512-message／128-block時序可能超過200 wire operations，現有後端尚不能完整原子建立所有模型上限案例；需bulk operations或更大原子capacity正式交付，不以減少圖形支持當目標完成。跨端編譯／batch fixtures／雙metadata CAS／Undo restore仍待最後集中驗收，效能最後。

## 2026-10-12：既有時序圖完整編輯（源碼已接、未驗證）

- 新增 SequenceCanvasEditing；container inspector／palette進guided／source／preview／確認。相同participant／block語意ID保留object ID，訊息端點相同保留relation ID，端點變更delete＋create新relation，不用未交付endpointRewire。container、樣式、zIndex保留；graph與flat semantics同一筆可Undo／Redo交易。圖外children或relations依賴被刪物件時拒絕，不cascade。
- transaction先解除該diagram metadata、更新graph、再綁完整metadata，scope／baseline必須不變。guided未改source時保留snapshot，真的改Mermaid可能重配語意ID，UI明示。尚需核對backend batch上限、兩次metadata CAS、端點replacement與Undo restore、跨端編譯與正式驗收。未compile／test／HTTP；六主線未完成，效能最後。

## 2026-10-12：雲端方案完整內容更新與衝突明確替換（源碼已接、未驗證）

- 補上以所選本機方案完整內容更新既有雲端resource的入口，不只rename：確認本機方案仍等於捕捉版本，從原payload／schema／素材引用建立body，以雲端選取版本的ETag形成固定update，再沿既有review→durable stage→明確send。純名稱更新、create上傳仍存在；共用轉換helper不搬素材、不假設跨workspace權限。
- Rejected update可明確選取「以被拒內容取代最新版本（非合併）」：使用fresh readback的ETag、新idempotencyKey，原body完整保留，仍需review與stage。Sending／Unknown不提供替換；renameOnCurrent保持名稱only、不覆盖新remotepayload。
- 正式mutation完整JSON（含body／bindings／ETag／target／key）、remote選取與衝突兩份payload提供4096字元分頁，不截掉尾端；頁界保留surrogate pair，分頁無HTTP／write。選新resource清舊conflict，跨scope失效不發表新review。未compile／build／test／HTTP，六主線未完成，集中驗收與效能最後。

## 2026-10-12：共用網格／背景編輯的版本核對與未確認保存狀態（源碼已補、未驗證）

- CanvasStyleDraft 共用 requireCurrent，核 actor/client/workspace、可寫角色、before完整樣式與styleVersion；網格編輯即使重用已準備operation也再次核對，不省略版本檢查。仍沿正式 UpdateCanvasStyleOperation、原operationId與既有Undo／Redo／durable journal，不另用本機偏好存共享內容。
- 網格視窗加入固定pending／unconfirmed訊息與內部捲動；第一次派送後冻结原operation與輸入，不反覆Apply未知保存。同步onApply例外與onFailed保留未確認狀態，不假設rollback；取消／關閉只清preview。背景派送failure補全局未確認訊息。dispatchOperation在無session／忙碌／斷線等未派送條件呼叫onFailed，不silent return；未擴大ACK／receipt權限。
- 沒有執行build／compile／test／HTTP。仍待共享背景跨端收斂、併發CAS、Undo／Redo與restart history最後集中驗收；其餘六主線缺口保留，效能最後。

## 2026-10-12：AI 實際紀錄核對與空回覆／完整回覆摘要（源碼已補、未驗證）

- 安全 log 最新仍為 2026-10-09T02:29:52Z，最後一次 OpenAiChat／Bearer 是 HTTP 200、SSE、Receiving、Completed，temperature=Absent；沒有 10/12 新 runtime 紀錄。這只證明歷史該次 transport 回覆，不證明新源碼／Claude／工具套用／Undo／跨端已驗收，不推論目前還在401或已全部正常。
- AI understanding 與 tool loop 新增 typed reply summary（UTF-8 文字 bytes／tool call數／參數 bytes），加入安全 log／複製資訊與固定狀態區。每round隔離，terminal失敗保留當輪解析summary，沒有 tool name／argument／node text進安全紀錄；round Completed 不等於 canvas applied。
- 正常terminal但空／空白且無工具的回覆改專用 EmptyResponse code／雙語引導，與未完成串流區別；工具參數合計與已完成理解回覆嚴格UTF-8 byte計數。不自動重試、不接受空draft；每輪授權與formal proposal review維持。沒有 AI HTTP／付費請求／build／compile／test，六主線尚未完整交付，集中驗收與效能仍最後。

## 2026-10-12：v2 寫入前意圖 ledger 與 unknown-result 閘門（源碼已補、未驗證）

- 普通素材 preparation 記錄新增 prepareAttempted／uploadAttemptLeaseIds／completionAttemptLeaseIds。原身分與 lease 不變，紀錄只單向追加、不當成 dispatch／PUT 成功／complete 收據；default fields 使用 EncodeDefault.NEVER 保留原紀錄讀取形式。prepare／PUT／complete 先 CAS＋confirm＋readback 意圖，再進 HTTP；相應 response settlement 使用意圖後 generation，確認失敗不可啟動後一步。
- 恢復已知 identity／票券／寫入意圖先 lookup；寫入取消、HTTP／未知失敗清 ephemeral ticket／舊 phase 並設 needsLookup，禁直接再寫或插入。404 不代表原請求不會 commit，只有未有已知 identity 時可明確重送相同 requestId 的 prepare，不配置替代 ID。源檔選擇本身失敗不冒充 server write failure，仍保留原檔與身分。
- 仍待 terminal 歸檔／容量生命週期、正式集中跨端編譯與 response-lost／斷電／取消驗收，以及其餘五主線交付。不執行測試／build／HTTP；完整目標維持 active，效能最後。

## 2026-10-12：v2 恢復分頁／原票券沿用／錯誤引導（源碼已補、未驗證）

- 恢復清單改每頁8筆、新紀錄優先，分頁不觸發 HTTP；恢復原身分時讀回 latestTicketRequestId，一般取票沿用它，不因重啟偷偷 supersede。明確 replacement 按鈕才配置新 ID。已知 asset identity 的恢復紀錄先走 lookup，不再提供 prepare 按鈕；index-only 可沿原 request 建立本機記錄。
- 重新選檔在交接 source 前核對原 metadata，錯檔保留前 source／reservation，SHA-256 變更使用安全 typed error。401／403、404、lease expired／superseded／mismatch、未知結果各提供對應引導，不顯示任意 exception body；失敗清 ephemeral ticket，存取／404 清舊 phase，不自動重傳。顯示 prepared／processing／ready／terminal 語意與允許的機器 rejection code。
- dispose 先 cancel request／close clients，再等待正在執行的 coroutine 退出後釋放 source，避免正在 readChunk 時釋放原檔。尚需集中跨端／cancel／故障驗收、terminal 紀錄歸檔生命週期與跨六主線剩餘功能。未 build／compile／test／HTTP；目標維持未完成，效能最後。

## 2026-10-12：普通 Server 素材匯入 v2 操作視窗與 repository 接線（源碼已接、未驗證）

- CanvasWorkspaceRepository 新增 scoped PreparedMediaImportController factory；Backend 以相同 endpoint／actor／client／workspace 及 MediaReferenceProofRuntime 平台 awaited private records 建立 controller。普通 Server「匯入素材」改開 v2 對話框；缺 durable runtime 明確拒絕、不回退 v1。本機匯入仍沿原本 local gateway。
- 視窗固定標題／狀態、內部捲動／safeDrawing；讀恢復索引、選原身分、重新選檔、prepare、原／replacement ticket、PUT、complete、lookup、Ready 插入皆明確按鈕。HTTP 只显示安全 status/code，不放 provider error body；取消保留原身分，close 釋放 source與 clients。切 scope 阻止續寫與 Ready 插入，視窗阻擋背景 canvas 操作。Ready 必須重新 GET 並匹配 verified descriptor，關閉視窗後沿既有正式畫布插入交易，不把 prepare 當 Ready。
- 尚需完成細節：ticket 過期／未知回應的 UI 引導、恢復索引分頁與正式 terminal 歸檔、handle cancellation／快速切頁資源邊界，以及平台最後集中編譯／故障／雙端驗收。源碼接線不等於 runtime 驗收完成；六主線維持未完成、效能最後。本輪未測試／build／HTTP／啟動後端。

## 2026-10-12：一般匯入 v2 恢復索引與分步 coordinator（源碼新增、UI 尚未切換）

- PickedAssetUploadStore 增加 scope-bound、append-only durable discovery index；先確認 index 再發布 request 記錄，故障留下的 index-only 原身分可查，不換 requestId。讀取記錄要核對已持久化索引，缺索引不授予後續 HTTP。索引／記錄同樣 CAS＋confirm＋readback；1024 筆／512 KiB 滿時拒絕而不刪歷史，後續仍需明確歸檔生命週期。
- MediaReferenceProofRuntime 增加普通選檔 store factory，沿現有各平台 awaited private records；實際 APP runtime 呼叫入口仍未接。PickedAssetUploadCoordinator 新增 reserve／discover／prepare／lookup／reserveTicket／issueTicket／upload／complete 明確步驟，所有身分保存先於相應 write；lease durable 確認後才返回 signed ticket。PUT 使用 bounded chunk、順序／總量／SHA-256／EOF 原檔驗證，不用 DirectAssetUploadSource 捷徑。lookup 不重傳、沒有自動 complete／node insertion，scope／cancel 或 CAS 不明停止保留身分。prepare 在 capabilities await 後再次核 scope 才寫。
- 尚缺 UI 授權步驟與恢復面板、普通匯入入口 v2 切換、terminal reservation 歸檔策略；普通 server picker 仍 v1。未編譯／測試／網路，六主線未完成，業務功能完成後集中驗收、效能最後。

## 2026-10-12：一般素材匯入 v2 共用契約與身分保存核心（源碼新增、尚未接入入口）

- prepare／lookup／upload-tickets／complete 共用 AssetPreparationWireRequest 與相同回應驗證；本機同步保留自身 review／projection 檢查，不把選檔 reservation 當成其授權。普通選檔記錄固定 server／actor／client／workspace／requestId、canonical metadata digest、原 asset identity、append-only ticket／lease；重新選檔必须匹配原 metadata，不配置新身分掩蓋未知回應。
- 新增 PickedAssetUploadStore：原 request 分域 key、CAS generation、successor 驗證、awaited confirmDurable＋讀回、嚴格 JSON／UTF-8／512 KiB 上限。拒絕覆寫 race，不裁剪歷史、不保存路徑或 signed directives。此核心尚未連到平台 runtime；也尚缺 durable discovery index、一般匯入 coordinator／恢復 UI，因此普通 server picker 仍是 v1，不宣告已切換或具備跨端恢復。
- 本輪僅源碼與文件變更，未編譯、測試、HTTP 或啟動後端。六主線維持未完成，待業務功能完成後集中驗收，效能最後。

## 2026-10-12：素材清理不明的恢復提醒保存（源碼已接、未驗證）

- AssetImportCoordinator在validated ticket、completion尚未請求時，abandon只有成功回覆才視為已清理；失敗／timeout改發RecoveryRequired並呼叫新bounded onRecoveryRequired。completion或checkpoint已進入者同樣保存提醒，仍不abandon、不retry server write，原error／cancel不能被提醒失敗遮掉。
- APP普通匯入共用retainMediaRecovery：原actor/workspace下鎖定提醒清單，已存在者讀回，不盲目重寫；beforeComplete與recovery hook共用。scope切換仍保存原scope reminder，但不更新新scope UI；原檔handle最後仍release。此Settings提醒是best-effort書籤，不是v2 reservation、receipt／lease或斷電durability證明。
- 明確新缺口：普通Server picker匯入仍使用BackendAssetTransferGateway v1；v2 gateway／durable reservation目前用於本機文件同步的LocalAssetUploadCoordinator。普通匯入v2準備身分／lookup／lease／reselect recovery尚需接，不以局部v2存在宣告跨端素材已交付。未編譯／測試／HTTP，六主線未完成，集中驗收最後、效能最後。

## 2026-10-12：awaited pending／fence 的 WS dispatch guard（源碼已接、未驗證）

- BackendRealtimeWriter／Owner原提交檢查改為suspend，repository經DraftBoundary／RecoveryBoundary讀真正已保存的pending與fence，不再固定讀legacy Settings。每次await後核current coroutine、runtime／writer／actor/client/workspace、owner與Live角色；socket在最後guard返回後再核writable/authenticated/active才發frame，guard不含憑證或raw log。
- canSubmitRealtime使用已配置checkpoint runtime＋sequence allocator＋atomic stopped-evidence capability；正式awaited runtime可符合，不再一律因awaited非null拒。未遷移的mobile legacy仍缺atomic/ledger而不放行，不自動建ledger／換runtime；實際platform host／scope registration仍待接。
- mutex下先持久stage原request，WS不明仍不fallback REST或另造ID，ACK／history／durable settlement沿現有規則。新的awaitable guard可能延後其他socket writes，需最後集中故障／heartbeat驗收；未HTTP／WS／編譯／測試，六主線未完成，效能最後。

## 2026-10-12：activated runtime close／logical-call drain（源碼已接、未驗證）

- activation lease使用closed／calls／releasing單一原子state，全部guarded draft／sequence／safety方法先登記呼叫、finally離開。close拒新call，但有in-flight時不釋放issued身分；最後call退出才CAS釋放自身、發drained訊號，後續confirm不能越過尚未退出的舊runtime。claim後guard失敗也沿同close路徑。
- ActivatedAwaitedSubmission新增closeAndAwaitCalls供host非阻塞等待logical calls退出，核closed／count0／issued非自身；重複close仍不清新lease。不auto cancel、timeout takeover或推論cancelled write未提交。
- 明確區分此logical gate與platform physical I/O：Web取消後IDB ticket／所有其他writer仍必須由host另行await owner physical drain、持全writer exclusion才能capture／cutover。host與scope registration未完成，不能把本增量當正式啟用／協作完成；未編譯／測試／HTTP，集中驗收最後、效能最後。

## 2026-10-12：awaited activation 租約原子身分核對（源碼已接、未驗證）

- 再核Android realtime factory及共用activation／publisher，三端publication／activation host仍未接，不把來源preview或checkpoint/proof store當新draft runtime已啟用。
- AwaitedSubmissionActivationManager 的issued由普通Boolean改成opaque lease identity的MutableStateFlow CAS。Runtime active同時核closed、當前issued身分與既有實體writer／原身份guard；close只CAS釋放自身身分，舊handle重複或延遲close不清新runtime。claim後guard失敗清自身slot並throw，不回傳不可用lease，仍不自動publication／切repository。
- 真平台全writer排除、quiesce capture與factory／UI host、新scope registration仍是未完成主線工作；未初始化／遷移／HTTP／編譯或測試，不能列正式協作與durable draft完成。集中驗收最後、效能最後。

## 2026-10-12：轉折點逐點座標／刪除與草稿保護（源碼已接、未驗證）

- RelationGeometryControls新增具編號的逐點選取、X／Y欄位、加入preview與移除點，保留batch座標輸入。改動先留UI草稿，明確Apply才走既有geometry operation；刪最後一点顯示Auto路徑預覽，不立即發operation。invalid／非finite／超界座標沿Manual構造驗證，不能stage／apply。
- 未stage的座標編輯阻止套用整路徑與換選另一點，可明確cancel單點；route草稿未套用時禁用會更新version的label操作，避免丟失路徑。新增discardRouteDraft只清UI草稿。三個文字field focus聯集與dispose核對，canvas刪除／快捷鍵應yield，不把Delete當刪線。
- 未編譯／測試／鍵盤／VoiceOver／手機視覺驗收；轉折點操作尚不能據此宣告連線或六主線完成。集中驗收最後、效能最後。

## 2026-10-12：背景中的選定線身直接拖曳（源碼已接、未驗證）

- GridCanvas新增relationLineDrag，僅背景空間命中已選定非self-loop連線才取gesture；精確project到原world polyline segment，新增一個轉折点後拖曳。Auto轉Manual保存既有內部折點，不像原單一midpoint入口取代整條路徑；最多32點、仍一筆geometry operation，無endpoint重接或新wire。
- 初始pass命中成功才consume，未命中沿原tap／pan；背景Canvas位於物件卡片之下，不加全屏上層透明攔截。touchSlop／取消／多指不commit，scope／version／viewport依既有gesture核對。右鍵pan／Shift／框選／multi-select／brush／文字編輯不取line gesture。sequence綁定與awaited queue gate同步補至既有control handles，不繞过整圖操作規則。
- 自循環仍以loop extent／label handles操作，不硬轉Manual；新direct拖曳不含物件覆蓋區或label専屬觸控區。未編譯／測試／真觸控／視覺簽核，六主線仍未完成，集中驗收最後、效能最後。

## 2026-10-12：手動折線線段新增轉折點（源碼已接、未驗證）

- 新InsertWaypoint(index) gesture從原manual route的指定segment中點插入一點，保留原waypoints／labelPlacement與全部relation屬性；累計screen displacement除zoom後只移動新點。原endpoint／物件rotation仍沿世界路徑geometry，不改滑鼠方向。
- 選定manual relation的較長segment顯示小加號控制點，與既有實心vertex區分；短於56dp segment不疊加touch target，32點上限時不提供新增。預覽偵測插入位置後保留原vertex索引並讓新增handle跟隨，不因preview重啟pointer key。單擊／未跨touchSlop／cancel不出operation；真正完成才一筆既有UpdateRelationAttributesOperation，可Undo／Redo。
- 未擴後端wire或endpoint rewire，未宣稱任意線身位置皆可拖曳；這輪新增選定線段的直接控制入口。未編譯／測試／觸控與旋轉實測，六主線未完成，集中驗收最後、效能最後。

## 2026-10-12：插線中間節點形狀選擇（源碼已接、未驗證）

- RelationInsertionDraft.plan新增shape參數，預設Rectangle維持舊呼叫；新TextNode在同一create/delete/create transaction保存選定NodeShape。RelationInsertionDialog提供現有NodeShape全集選擇，預覽重建並撤銷confirm；attempted／saving後禁止改形狀，維持固定transaction／object／relation ID與不確定保存後不得改payload的規則。
- 原連線direction／firstLabel／intent、完整original刪除快照與Undo仍沿原operation；不改後端契約、不改端點rewire、不把flowchart shape當execution語意。shape選項按需展开，選好即收合，不讓全25類按鈕常駐佔滿視窗。
- 自循環／geometry／label drag既有接線已檢源碼，這輪只補插入形狀缺口，未宣稱任意線段拖曳或全部圖形已交付。未編譯／測試／HTTP，六主線未完成，集中驗收最後、效能最後。

## 2026-10-12：時序圖引導式插入位置與手機建立視窗（源碼已接、未驗證）

- SequenceGuidedEditor新增明確分支尾端／指定step之前的新增位置，訊息與巢狀block皆可作插入目標；目標為semantic ID，不按畫布y猜執行順序。切branch或改為編輯既有message撤銷插入目標；保存時重新核目標存在同branch，丟失目標拒，不默默append。所有修改仍隔離在draft，先parse／preview／confirmation再正式single transaction。
- SequenceCreationDialog改中央safeDrawing＋IME留白的響應式尺寸，標題／saving／錯誤／close-discard固定區，source／guided／preview獨立scroll；儲存時不能關閉，prepared transaction不允許改source／另造身分規則保留。沒有修改後端、operation contract或推測圖形execution。
- 尚未編譯／測試／實際iPhone遮擋簽核；完整時序圖／連線／六主線仍未完成，集中驗收最後、效能最後。視覺與遮擋由使用者判斷。

## 2026-10-12：GIPHY 限流後分頁保留／明確重試（源碼已接、未驗證）

- RateLimited不再隱藏下一頁：保留provider原offset與結果，按鈕在本機cooldown期間disabled，顯示剩餘秒數；解除只開放使用者明確重試，不fetch／prefetch／cache API結果／reset配額。budget只提供read-only remainingDelay，取rolling-hour與Retry-After較長限制；同key新block不縮短既有期限，共用key其他裝置仍需GIPHY enforcement。
- 搜尋／trending／preview刷新也沿cooldown gate；改key會重新按該client狀態，不log key。修改query但尚未送出時，舊search的下一頁disabled並提示原query session，trending不因文字草稿混入search。next click擷取原offset而非!! mutable，保持原語言／generation guard。
- unexpected UI page handling exception亦回安全Unavailable，不默默結束；Cancel仍不標錯或auto retry。不涉及素材download授權或rehosting。未API／編譯／測試，GIF分頁／跨端素材與六主線正式集中驗收仍未完成，效能最後。

## 2026-10-12：雲端方案簿版本刷新／分頁／固定狀態（源碼已接、未驗證）

- CloudSchemeDialog mutation／recovery後按durable task phase分Committed／Rejected，不再把所有結果只標保存完成；兩者都清舊選定resource／ETag／page／review，明確要求重讀。恢復得到的歷史receipt不冒充當前resource，不自動發新mutation。
- 加上一頁的明確read，保存cursor歷史而非整份page cache；返回也重新向gateway讀，過期／撤權沿原錯誤處理、不自動restart。新載第一頁重置cursor與舊conflict，選定resource仍fresh GET後才可審閱寫入。沒有自動同步或跨workspace素材繞過。
- status／取消／關閉移到固定頂部，列表獨立scroll。busy直到取消／finally完成才解除，避免取消後舊job清掉新job；操作完成但tasks reload失敗單獨提示，不把已保存結果當rollback。未HTTP／编译／測試，跨裝置正式交付與六主線仍未完成，集中驗收最後、效能最後。

## 2026-10-12：AI 工具流程終態與 Apply 結果保留（源碼已接、未驗證）

- 實讀Desktop安全log末8行，最新仍2026-10-09T02:29:52Z，OpenAiChat／Bearer／HTTP200 SSE→Receiving→Completed，temperature=Absent；沒有新版執行證據，不推論當前401／Claude／Apply已解。
- tool dialog未預期exception／取消增加typed terminal診斷，沿當輪timeline保留HTTP與elapsedMs、request卡與安全logger一致；首個Failed不被Cancel覆蓋，不記exception原文。Apply Queued不立即關modal，保留「待server確認」＋明確關閉／重新開啟取得新scope指引，scope變更不覆蓋queued提示。
- Apply成功後同dialog禁止新send，避免持舊workspace上下文继续送AI；需明確關閉／重開。只保留成功資訊，無自動再次授權／重送。未呼叫AI／编译／測試，新版實際網路與草稿審核／Apply／Undo仍待正式集中驗收，六主線未完成。

## 2026-10-12：AI 診斷終態保留與每輪耗時（源碼已接、未驗證）

- 理解wizard修改設定／scope只撤銷授權與舊回答，不清上一輪診斷；明確標為上一輪、不是新設定結果。只有明確開始新attempt才重設timeline，取消／失敗／完成仍留在固定status區。
- 新AiProbeDiagnosticTimeline逐attempt／tool round保存typed HTTP status、contentType、認證摘要、wire摘要與request ID等上下文，terminal事件不再丟response資訊。增加單調時鐘elapsedMs，UI列表／request卡與Desktop安全log共用，非性能驗收，不記token／endpoint／prompt／原body。每輪獨立，不把上一輪HTTP=200或401帶到下一輪；相同原事件去重，bounded24保持。
- 未呼叫AI、未測試／編譯；舊log的HTTP200不能證明新版或Claude／工具Apply正確。本輪改善可觀測性，AI實際新版故障與六主線完整集中驗收仍待完成，每次授權與UTF-8／空回應保護保留，效能最後。

## 2026-10-12：ACK 的完整 history／素材 provenance 交接與 Desktop adapter（源碼已接、未驗證）

- 新 acknowledgeWithHistory 接 Settings、awaited bundle、activation guard與Desktop原生adapter。repository所有提交／prepared／receipt／retry ACK 清除入口共用 acknowledgeConfirmedPending：先讀durable snapshot，只有checkpoint相同且原transaction＋request digest一致才帶完整history進journal advancement；畫布須等於原head套用結果，不能把server最新其他變更或未確認tail當baseline。
- journal下一筆起點改用已確認的完整history（包含素材原provenance），不再只用原operation重新inverse而遺失已確認metadata；原ACK idempotency／generation CAS／無自動retry規則不變。history read／identity／交接失敗仍保留pending。
- 發現DesktopWorkspaceDraftPersistence與native包裝尚缺appendWithIntent轉接，已補完整intent／beforeHistory與ACK handover，避免Desktop Undo／Redo走預設拒絕或遺失baseline。四端接線仍僅源碼，未編譯／測試／HTTP，不能宣告跨端完成；六主線仍未完成，集中驗收最後、效能最後。

## 2026-10-12：開啟工作區／復原載入 confirmed history（源碼已接、未驗證）

- restoreConfirmedHistory 由backend submissionMutex內讀完整durable snapshot，核當前actor/client/workspace與checkpoint不超過server state；保留undo／redo stacks，但workspace一律替換為已開啟server最新畫布。讀取不replay、不送網路write，舊版本／損壞記錄不自動fallback空history。
- adoptWorkspace（初次開啟／切換／重新建立等共用入口）先讀本機sync history或server confirmed history，再一起採用session／history。receipt／retry在pending清除前讀回完整confirmed history並交給UI，不再以單筆inverse蓋掉已存在完整stack；無完整snapshot且原證據足夠時才保留既有單筆fallback。普通遠端catch-up改保留stack，只更新workspace，Undo／Redo仍重新檢前置條件與CAS。
- 這條跨重啟載入已接源碼，尚未編譯／測試／故障驗收，不能列正式完成。缺baseline的legacy history無法自動補造；journal ACK baseline素材origin、多排隊交易與作用域取消細節仍待核對。六主線未完成，集中驗收最後、效能最後。

## 2026-10-12：journal baseline stacks 與 ACK 前 confirmed history 保存（源碼已接、未驗證）

- UI普通／awaited保留傳完整beforeHistory；journal可選baseHistory保留起點完整undo／redo，後續操作由明確intent重建，applyRecorded要求實際stack產生的操作與saved operation完全相同，不以suffix推論。Settings與awaited追加／ACK皆接baseline推進，validator核原workspace與replayHistory完整結果。舊缺baseline記錄不補造。
- 新confirmedHistories沿四端proof runtime，REST／WS／receipt在完整ACK／素材原證據驗證後、pending acknowledge之前，只保存journal head後的完整history；不含未確認tail。相同baseline確認點優先讀已保存history，保留既有素材provenance；保存失敗保留pending，無auto retry。snapshot綁原transaction與exact request digest，同ACK重入驗原identity，較新checkpoint不倒退。
- 草稿restore可交回完整replayHistory，UI採用它而不是把Undo當Edit。獨立confirmed history尚未接工作區開啟載入，receipt／retry畫面採用完整confirmed snapshot仍待接；journal ACK baseline素材origin交接及跨端細節仍待集中核對。六主線未完成，未編譯／測試／HTTP，集中驗收最後、效能最後。

## 2026-10-12：完整 history durable 保存層（源碼新增、未接 runtime 流程／未驗證）

- WorkspaceHistory 可序列化完整 workspace／undoStack／redoStack；SavedWorkspaceHistory 綁 api/user/client/workspace scope 與確認的 workspaceVersion／serverSeq。新增 WorkspaceHistoryStore，沿 AwaitedDurableLocalRecordStore，salting namespace 不與素材原證據混用；既有四端 proof runtime 可建立此 store，但 repository／UI 尚未呼叫。
- 512KiB UTF-8 邊界區塊、每塊 content hash／長度／固定 generation=1、manifest-last CAS 與 durable confirm；完成後完整 readback，不以 count 代替内容。reader 驗 scope／世代／hash／型別、全部 chunk 與 manifest 一致；durable read 確認每塊＋manifest。完整上限64MiB，超過直接拒、不截斷堆疊，無 GC／fallback／自動 race 重試。明確 expectedGeneration，確認 checkpoint 不倒退。
- 僅為保存基礎，不授權 replay／asset restore，不代表 ACK 清理前已保存或重啟已恢復。下一步接 committed-prefix history、提交 settlement 與開啟載入，處理 optimistic queue tail 不能混入已確認保存；完整六主線仍未完成。未編譯／測試／HTTP，集中驗收最後、效能最後。

## 2026-10-12：同步一般編輯 intent 與 pending 凍結（源碼已接、未驗證）

- WorkspaceScreen 同步普通編輯使用 retainHistoryDraft(Edit)，backend 同步／awaited 普通保留也明確保存 Edit；Undo／Redo 仍由 awaited UI 指定。operationId 不重複，intent map 不得指向不存在的操作。
- prepare 從 journal 擷取 intent 到 pending 的可選欄位，缺值不序列化、不升級舊資料。Settings／awaited stage 在寫入前核對 journal head 與 pending intent。receipt／retry 取 pending 的凍結意圖，history 核對 journal 同值；不從送出後可變 metadata 猜方向。intent 不進後端 wire，不改其冪等 digest 語意，不是原交易 provenance。
- 已補同步編輯待辦；確認後再重啟完整 history 的持久化／載入仍未接，既有草稿 replay 的多筆 Undo／Redo stack 恢復仍待接。六主線保持未完成，未編譯／測試／HTTP，集中驗收最後、效能最後。

## 2026-10-12：草稿明確保存 Edit／Undo／Redo 意圖（源碼已接、未驗證）

- awaited UI publication 增 intent，由 Undo／Redo 按鈕明確指定；backend 在同一次 journal append 保存 operationId→intent。Settings 與 awaited bundle／activation wrapper 全接；intent 空 map 不序列化，舊 append 不自動補意圖，既有資料不改寫。ACK 移除相應 intent，保留後續交易標記。
- receipt／明確 retry 在清 pending 前取 intent。完整已核交易且 intent 已保存時，Undo 的 inverse 放 redo stack，Edit／Redo 放 undo stack；不以 ID suffix 猜測，不重播原交易，CAS／provenance 規則不變。舊 journal 缺 intent 仍可確認提交，但不製造錯方向 history。
- 此為 pending 跨重啟的單筆 stack 方向交接，不是確認後跨重啟完整 history 保存。同步普通編輯 intent、完整多筆歷史持久化／恢復仍待接；六主線尚未完成，未編譯／測試／HTTP，集中驗收最後、效能最後。

## 2026-10-12：明確 retry 的完整交易 history 交接（源碼已接、未驗證）

- retryPendingChangeWithHistory 與 receipt 共用 pendingHistoryOperation：從尚存 journal head 核完整原 wire／素材快照，不能只返回素材子交易。UI 兩種明確復原成功後都採用最新畫布並建立完整原操作 inverse；dismiss 不建立 history。
- retry 在 submissionMutex 內核原 transaction、fence、最新 scope／ACL，再只送已保存 request；Conflict 不清 pending、不產生歷史。ACK 沿既有完整 committed／restore evidence 驗證。成功後先讀回最新畫布並確認版本／serverSeq 不落後 ACK，再進序號與 acknowledge；讀回失敗保留 pending，沒有自動重新送出或新造 ID。
- 舊 confirm／retry API 保持回傳 session 相容。確認後再次重啟的 history 持久化、原 Undo／Redo stack 意圖、遷移 publication／activation host 與全六主線尚未完成。未跑編譯／測試／HTTP，正式集中驗收最後，效能最後。

## 2026-10-12：receipt 確認後完整交易 history 交接（源碼已接、未驗證）

- 新增 confirmPendingReceiptWithHistory，清除 pending 前讀完整 journal head，核 localOperationId／baseVersion、重新展開後逐項核對原 wire（只代入原 operationId／clientSeq），並驗素材 before／after 快照。原 committed log、最新 ACL／state、durable 原證據仍是必要條件；不再送交易。
- UI receipt 確認後採用最新畫布，再以完整原 operation 的 inverse 建立一筆 history 並附已確認素材 provenance；不重播交易、不只還原 media children。Undo 繼續走目前 state 的前置條件／CAS。缺 journal 或 legacy 缺素材綁定證據可確認原提交，但不製造局部 Undo。
- 僅覆蓋 pending 尚存時的明確 receipt 確認（含重啟後 journal 可讀）；確認完成後再次重啟的完整 history 持久化、retry recovery 的 history 交接與原 Undo／Redo stack 意圖保存仍待完成。未編譯／測試／HTTP，六主線未完成，集中驗收最後、效能最後。

## 2026-10-12：同種類素材 Undo／Redo history 接線（源碼已接、未驗證）

- durable ACK證據轉MediaReferenceHistoryOrigin後由SubmitOutcome／submission coordinator交回WorkspaceScreen，在原submitted operation的exact inverse history中附原workspace／actor／transaction／wire operation／before objectVersion與引用兩側。transaction child按原inverse結構核對，重複同node引用依原版本區分，不靠相同assetId或inverse suffix製造authority。
- MediaReferenceChange inverse與history版本rebase保留origin；有origin時DTO走restore_media_references，普通新替換仍update_object／Ready。prepare先驗canvas writable＋restore capability、private immutable evidence、actor／workspace、原兩側與版本、當前node CAS／locked；缺證據不fallback普通換素材。
- REST／WS／receipt ACK settlement加exact restoredReference與objectVersion預期核對，拒不符合原選定before／after的回覆，不因server 200就套錯UI或清pending。pending F wire驗exact fields／UUID／numeric version／targetSide；不把F當deleted object tombstone還原。
- awaitsHistoryCommit僅讓Undo／Redo等待原queue／commit完成，不冒充legacy draft atomic；canvas一般交互仍沿既有規則。原metadata空值不序列化，舊draft不升級authority。此live-history同kind路徑源碼已接，但重啟／receipt恢復後history重建、cross-kind後端完整provenance与fixtures／正式驗收仍待完成。
- 未編譯／測試／HTTP，六主線維持未完成；集中驗收待全部業務功能完成後，效能最後。

## 2026-10-12：素材引用 commit 證據儲存／ACK 接線（四端factory已接源碼、history未完成）

- 新MediaReferenceProofRuntime／MediaReferenceCommitStore保存scope／localOperationId／原transaction、requestDigest、ACK範圍／workspaceVersion與完整引用changes。先validate原pending／完整ACK與exact wire properties，再immutable generation1 CAS＋confirm＋完整讀回；同key只接受同證據，不更新／GC或把local紀錄當server授權。
- REST／WS accepted與confirmPendingReceipt先保存該證據，後sequence／draft ACK清理；失敗pending保持、receipt重查可確認原相同證據。legacy無media snapshots不補造。cross-kind原before／after保留，但仍不授予目前F歷史還原。
- Android／iOS private records與owner operation accounting、Web Documents physical transaction tickets、Desktop native awaited records實際factory注入。generic自訂repo無proof runtime時，在變動引用prepare／reserve前明確拒，不能提交後才發現無證據store；預設四端已注入，不fallback Settings。
- history association、Undo／Redo正式restore operation與跨kind後端完整provenance仍待接。未編譯／測試／HTTP或讀寫真proof資料，六主線維持未完成；業務功能完成後集中驗收，效能最後。

## 2026-10-12：素材引用原交易快照保存（prepare源碼已接、history未接完）

- 唯讀核後端canvas-mutations.ts：mediaReferenceOf／provenance仍僅assetId＋thumbnailAssetId，跨mediaKind缺口仍存在。APP不因此推測原kind或硬送跨種類歷史還原。
- PendingWorkspaceSubmission新增非空才序列化的mediaReferenceChanges（wireOperationId＋完整before／after／expectedVersion）。prepare按transaction順序核對當時MediaNode原值、配對exact wire／properties，再與原pending一起保存後才dispatch；no-op引用不授權restore，舊empty entries字節格式保留。
- pending validation核每個saved change與原wire、operation identity所在request範圍、版本與三欄patch精確一致。新ACK helper先validate完整原ACK／actor，再建立same-kind原transaction／operation proof；legacy無快照不補造，cross-kind快照保留但不授權目前F還原。
- 仍需proof durable store／ACK接線、history版本與Undo／Redo正式operation；此快照／helper不能宣告F完成或server授權。未編譯／測試／真HTTP，六主線維持未完成，集中验收最後。

## 2026-10-12：AI 安全 log 重核／tool-only SSE 接收狀態（源碼已改、未驗證）

- 只讀既有 .boarderless/logs/ai-probe.log；最新紀錄仍為2026-10-09T02:29:52Z HTTP200／SSE／Completed，temperature=Absent，沒有更新失敗樣本。這是該舊嘗試的證據，不證明新build、Claude或全部工具流程正常；未讀Key／env或發paid request。
- 核現在tool-loop已有每回合Completed与first typed failure保留，不重複修已存在功能。實際缺口：tool-only reply無TextDelta時UI沒有Receiving。AiSseHttpTransport改首次交付bounded／UTF-8 SSE frame即記Receiving，沿該次HttpResponse安全摘要；每次transport只一次，不記raw frame／prompt／Key。
- Receiving不是JSON／tool已接受或整個reply完成，後續UTF-8／空回應／格式／scope／授權驗證維持；診斷observer失敗依既有consumer failure邊界停止、不繼續下一frame。此為源碼接線，未编譯／測試／真AI呼叫。
- 六主線維持未完成，migration正式發布／啟用與其他功能仍待接，集中验收待業務功能完成後，效能最後。

## 2026-10-12：legacy 原定位清單補充 UI（源碼已接、未驗證）

- 實際來源審閱視窗增加可展開的原scope／stopped locator JSON輸入，唯讀查證使用、不寫Settings／log／clipboard／網路。輸入更新清除舊review，明確「重新讀取來源」才凍結這次輸入並查證；busy不可改，過大／非法Unicode不接受。
- LegacyMigrationCatalogInput strict version1、exact root／scope／locator keys、必須strings、唯一scopes／locators、各最多256、4MiB與bounded JSON depth。保留exact apiBase／ID不normalize；身分不是靠輸入宣告，而是交原全key inventory核hash與完整內容。
- repo在Default decode後，補充資料合併保存catalog與已知archive／safety，仍要求全部published manifests涵蓋；漏key／錯ID／未知archive拒。UI成功只代表讀到同時點來源，不是全writer排除／發布／啟用／重送授權。
- publication／activationhost與新scope registration仍待接，未初始化、遷移或執行測試；六主線保持未完成，集中驗收最後。

## 2026-10-12：legacy 遷移來源審閱實際 UI 入口（源碼已接、未驗證）

- WorkspaceScreen 成員面板新增「審閱本機草稿儲存」，只在 legacy Settings backend 配置提供（native sequence／awaited已啟用配置不誤當legacy）。開啟顯示read-only LegacyMigrationPreviewDialog，範圍summary＋source／plan digest、持續loading／safe failure與明確重新讀取按鈕；無publish／activate／resend操作。
- repository.reviewLegacyMigration 在submissionMutex與已配置writer operation accounting內、Default讀完整catalog／archives／safety與floor，再建立共用planner frozen review；讀回後核scope／cancel。missing catalog／counter／identity／原archive locator不猜測、不以0重置。Mutex／preview不冒充全writer排除，正式發布仍須fresh recapture與migrationhost。
- modal跟actor/client/workspace scope生命週期，納入既有canvas交互與Presence審閱gate；開啟不關閉原成員面板、不讀畫布文字到log／網路、不搬移或切換storage。真正publication／activation入口及平台host仍待接，新scope註冊與六主線未完成；未編譯／測試，集中驗收最後。

## 2026-10-12：關閉 owner drain 後再接管（三端factory源碼已接、未驗證）

- Owner增加awaitClosedAndDrained，只有已明確closed owner才可等待；要storage count為0、active slot已退出、onDrained成功完成，單看drained flag不作證。release callback失敗記Failed，等待方拒，不冒充已釋放。
- 三端prepare接claimAfterDrain：若前owner仍live立即拒；若closed但儲存還在退出，以可取消flow等待最多30秒，然後CAS競爭claim並再核platform lease。timeout不清舊owner／count、不重置資料或fallback，不自動重試輸掉的candidate。
- 對App模式切換，允許旧screen dispose後background I/O未立刻退出時新screen在既有連線狀態等待，不阻塞主執行緒或搶占live repo。完整migration host／來源capture＋排除仍待接，新scope註冊与六主線未完成；未編譯／測試／啟用migration，集中驗收最後。

## 2026-10-12：checkpoint 實體 I/O drain／Web交易終態（三端已接源碼、未驗證）

- ServerWriterOperationLease 增 awaited操作與browser physical ticket。Android／iOS checkpoint record stores在背景I/O外包同owner accounting；Web Documents store每筆bridge操作獨立持ticket，Kotlin coroutine取消不先釋放。
- durable-records.js取消active transaction改abort但等待onabort／oncomplete才finish；tx.onerror只要求abort、不當terminal。terminal callback即使已取消仍通知Kotlin，只結束ticket、不讓已cancelled continuation成功或更新UI。沒有transaction時取消立即finish，晚到IDB open不能開始寫入。
- BrowserDurableLocalRecordStore constructor可選writerOperations，現僅server checkpoint注入，其他store callback仍沿既有continuation guard。失敗／取消不fallback；complete可能已commit的原請求仍須exact讀回，取消不是未提交證明。
- 納入checkpoint physical I/O不代表整個owner網路／所有新版durable功能已排除；遷移仍須cancel＋await全部工作、真platform排除與正式入口。沒有啟用新awaited runtime或跑測試，六主線維持未完成，集中驗收最後。

## 2026-10-12：legacy 背景寫入 drain barrier（三端factory已接、未驗證）

- ServerRepositoryWriterCoordinator owner改closed／operations／drained單一原子state；close立即拒新工作，但已開始儲存操作finally離開才釋放owner與一次onDrained。新owner不搶未退出操作；舊重複close不釋放新owner。無阻塞UI等待／TTL或強制中斷同步I/O。
- ServerWriterOperationLease接legacy DraftBoundary／RecoveryBoundary／native sequence wrapper的Default執行區間；nullable結果不fallback／重跑。repository同步draft、scope catalog、Settings序號與identity／workspace寫入也用同operation accounting，clientId初始化getter包含在guard內。
- Android／iOS factory注入accounting，Web owner drain後才lease.close，避免背景legacy write尚未結束就讓新分頁進入。純snapshot計算不是寫入；未改legacybytes／operation ID，沒有啟用awaited runtime或測試。
- 未涵蓋直接Settings／未配合舊writer、server已dispatch交易或全部新版awaited／checkpoint I/O；migration coordinator仍需cancel並await所有工作＋真platform exclusion。平台遷移入口、新scope registration與全六主線仍待完成；集中驗收最後，效能最後。

## 2026-10-12：三端 same-process repository writer owner（factory源碼已接、未驗證）

- 新 ServerRepositoryWriterCoordinator 使用原子 owner claim／release，candidate建立不搶占；Android／iOS／Web factory在prepare取得唯一owner，active guard同時檢平台lease＋owner，close只release自身。重複舊close不解除新owner；同repo並行prepare允許同owner，其他repo仍拒，不TTL／steal／默默close對方。
- WorkspaceScreen既有DisposableEffect在dispose關閉repo，因此模式切換candidate預備不需提前奪owner，進入新screen才prepare。HTTP錯誤清identity／workspace及load後clientId getter前核owner，已關閉repo不回寫新owner的Settings；已dispatch交易仍靠原receipt／fence，失owner不代表未提交。
- 這個owner只覆蓋新版平台factory，未覆蓋直接Settings存取／舊版writer，也不證明migration時in-flight作業都已停止。完整quiescent barrier與遷移factory／UI入口、新scope註冊仍待接；未啟用awaited runtime／放寬WS，未編譯／測試。六主線保持未完成，集中驗收最後。

## 2026-10-12：Web server writer lease 實際接線（源碼已接、未驗證）

- 新 server-writer-lease.js 用 origin-wide Web Locks exclusive／ifAvailable；真正 callback grant 才通知 Kotlin，持有 callback promise直到最後一個同分頁 owner釋放。JS／Wasm bridge＋BrowserServerWriterLease suspend acquire 有5秒上限、取消／失敗釋放自身token；不支援secure-context Web Locks即拒，沒有localStorage TTL／steal fallback。
- Browser server factory注入prepare／active／close lease；repository open／refresh／prepare／協作owner及工作區／成員mutation先await真lease，submissionScope與metadata／identity寫入邊界檢查有效，close釋放。索引載入helper；pagehide清owner並失效，BFCache恢復不自動重取／重送，須重新載入。
- 同分頁多repository共用鎖且token獨立，仍不是same-process cutover或完整legacy writer排除；未配合舊分頁／worker仍需處置。沒有啟用awaited submission migration／放寬WS；未編譯／測試／真socket。六主線仍未完成，集中驗收最後，效能最後。

## 2026-10-12：repository／素材 runtime 一起交接（實際 App 源碼已改、未驗證）

- App startup與模式切換先建立 repository＋匹配的素材 runtime，兩者皆成功且偏好保存成功才以單一 activeSession 發布，避免 repository 已切換後 mediaRuntimeFor 在 composition 拋錯或沿用錯下載 gateway。
- media factory／偏好失敗時未接管 candidate 關閉（不關閉仍 active 的同一instance），保留舊 session／畫布；startup失敗回chooser，不把server素材改成本機／HTTP fallback。此處只預備 runtime，不讀素材／開socket或做 migration。
- 三端 writer／migration切換協調仍待完成，這個 UI session 交接不代表完整同process writer exclusion。未編譯／測試，六主線保持未完成；業務功能完成後集中驗收，效能最後。

## 2026-10-12：iOS lifetime lease／儲存模式建立失敗 UI（源碼已接、未驗證）

- iOS server factory 接私有 server-writer-control lifetime flock，nonblocking acquire；同process共用descriptor，owner／mode／NOFOLLOW／inode與device／nlink核對，repository submission及 realtime gate與checkpoint factory檢查有效性。失敗不刪lock、不變identity或fallback，process結束由OS釋放；不把這個跨process鎖冒充同process cutover barrier。
- App 不在 storageMode改變後未捕捉地建立repository。選模式先建立candidate／取得lease，成功保存選擇後才切activeRepository；失敗保留原screen／repo並回false，未接管candidate關閉。startup factory失敗回模式選擇與安全錯誤，不拋出鎖exception或假裝server連上。
- en／zh錯誤提示涵蓋儲存開啟及偏好保存失敗，不顯示原exception。尚需同process切換協調、Web lease、三端遷移入口及factory／新scope註冊；沒有啟用新awaited runtime或放寬WS寫入，未編譯／測試。六主線維持未完成，集中驗收最後。

## 2026-10-12：Android server factory 生命週期鎖（實際 factory 源碼已接、未執行）

- AndroidRealtimeBackendRepository 建立 repository 前取得 noBackup 私有 server-writer-control 的 process-lifetime exclusive FileLock；同 process factory 共用 handle，其他配合的 process tryLock 失敗即停止，不等待、刪 lock、改身分或 fallback。限制目錄／檔案 owner、mode、NOFOLLOW、inode／device與 lock validity，保存 directory 耐久。
- repository submissionScope 與 realtime owner scope gate 注入 lease 有效性檢查，checkpoint factory也先核 handle。原 AndroidAtomicLocalRecordStore 逐筆 CAS lock 保留；不同 lifecycle lock不冒充同 process repositories排除，也不涵蓋未接 factory 的舊版 writer。
- 尚需同 process cutover協調、iOS／Web生命週期 lease與平台 migration factory／UI入口，故沒有啟用 awaited submission runtime／放寬WS gating。源碼核對 Android manifest無獨立process元件不代表可以免平台鎖或非配合writer檢查。
- 未初始化／遷移真資料、未編譯／測試／啟動 APP；六主線保持未完成，業務功能完成後集中驗收，效能最後。

## 2026-10-12：legacy scope catalog／來源收集接線（源碼已接、遷移未啟用）

- 新 LegacyMigrationCatalogStore 記錄 legacy hashed draft／pending 對應的完整 scopes；repository prepare／retainDraft／awaited retain 先登記再草稿寫入，新 awaited runtime 不寫此 catalog，Desktop native identity 配置也不寫 legacy 清單。catalog-only scope 不代表有 draft，缺／壞 catalog 不重建 manifest 身分。
- SessionPreferences.captureCataloguedLegacySequenceForMigration 合併保存清單、顯式補充清單、目標、完整 stopped／fence／deletion 自帶 scope，再交既有 all-key inventory驗完整性與 floor；前後重核 catalog／safety／archives，保留舊 unidentified archive 的人工 locator 要求。
- 舊 wdj1／wsp1 的 scope 與 chunk key 不可逆雜湊，不能從 Settings keys 推算身分。未列舊 published key 仍明確拒，不把 current workspace／recent list當完整 catalog；需原 scope recovery 資料才能補齊。Settings readback 不冒充 durable 或跨 process lock，平台 writer exclusion仍待接。
- 沒有 capture 真 legacy／初始化 ledger／遷移／啟用，未編譯／測試。UI入口／平台 factory與真writer鎖、新 scope registration仍待完成；六主線保持未完成，集中驗收最後。

## 2026-10-12：原遷移 review 耐久保存／重啟讀取（源碼已接、平台未啟用）

- 新 LegacyMigrationReviewStore 保存 immutable target＋planDigest 綁定的完整 normalized plan，以 UTF-8 安全 chunks 逐一 CAS／confirm，最後 manifest CAS／confirm＋完整重新讀回。64 MiB packet 上限與 scope/domain codec 驗證維持；不同 plan 各自獨立，不猜 latest、不覆寫原方案或 GC。
- publisher 首次／Preparing 路徑先保存原 review 並 fresh recapture，才寫 Preparing 或 artifacts。Published 確認及 reopen 必須讀回 receipt 指定的原 packet，比完整 bytes；缺 packet 明確拒，不憑摘要重建。loadPublishedReview 提供 restart 的原方案讀取，不代表 capture 完整或啟用授權。
- 讀取 strict JSON／numeric／UTF-8、逐塊 key／digest／generation、whole plan digest與 target/source 校驗，確認後重讀 manifest；中斷保留 orphan chunks 與 legacy 原資料。沒有 capture 真資料、執行 migration／切換 runtime或測試。
- 平台 writer 排除、完整 legacy catalog capture、WorkspaceScreen／factory入口與新 scope registration仍待接通，六主線維持未完成；所有業務功能完成後集中驗收，效能最後。

## 2026-10-12：已前進資料重新啟用（源碼新增、平台鎖未接）

- 新增 activation.reopen：必須已有原 activation／Published receipts，來源 fresh review 不變；不重新 publication、不 initialize、不降 floor，缺任何註冊紀錄皆拒絕。三個 awaited store 新增 readDurable，以 confirm＋再次完整讀回核 generation／內容。
- publisher.confirmAdvanced 比原 artifacts：generation 不倒退，同 generation digest 必須相同；ledger 不低於原 floor 與保留 wires 的 clientSeq，safety legacy digest／原 fence wire 身分不變、終態不回退，舊 deletion evidence 只能相同或版本及 seq 同時前進。仍要求真正所有 writer 排除，這不是用兩次 read 替代平台鎖。
- dialog 增加重新啟用模式，只另行確認 reopen、不重跑發布。這只完成原已註冊 scopes 的恢復邊界；平台入口／全 writer 鎖與完整 legacy catalog capture、持久 frozen review 讀取、新 workspace 註冊仍待接通。沒有執行真遷移或啟用，未編譯／測試；六主線維持未完成，集中驗收最後。

## 2026-10-12：遷移審閱 dialog（UI 源碼新增、平台入口未接）

- 新增 SubmissionMigrationDialog，直接使用 frozen review／publisher／activation manager。開啟不 capture、不寫入或 HTTP；畫面顯示 source／plan digest 與每 scope 的草稿操作、pending、stopped、fence、刪除證據、floor、quarantine，不顯示 raw draft／endpoint／認證。
- 發布與啟用各自兩次點擊確認，工作狀態持續顯示；來源與宿主 scope gate 在 recapture／完成後重驗。失敗不宣告完成、不換 ID，不自動重送或切換 repository。取消關閉保留紀錄；activation lease 僅在宿主成功接受後轉交，否則 close。
- 宿主必須原子安裝並持有 lease、提供真 capture／writer exclusion／identity checks。尚未接 WorkspaceScreen 入口或平台 factory，不能視為使用者已可遷移；平台排除、startup reconciliation、新 scope 註冊仍待完成。未編譯／測試／執行遷移，六主線仍未完成，集中驗收最後。

## 2026-10-12：awaited draft 分塊發布（源碼已接、未啟用）

- AwaitedAtomicWorkspaceDraftPersistence 由整份單筆 v1 改為 v2 manifest＋不可變 scoped content-addressed chunks；既有 v1 開啟只讀、不自動搬移。每塊 UTF-8 code point 邊界拆分、CAS＋confirm 後才唯一發布 manifest，完整草稿 domain codec 仍限制 6 MiB，未截斷／放寬 operation 或 archive 上限。
- 讀取核 scope、numeric fields、chunk generation／size／digest、整份 digest，再重新讀 manifest 比 generation＋bytes，拒 missing／損壞／並行變更。save 最後完整 typed readback；同內容只 confirm，不換交易 ID 或 silent rebase。
- quota／取消保留舊 manifest 與 pending，未知 manifest ACK 必須原內容核對；舊及 orphan chunks 不 GC。極小 platform limit 也可能容不下 manifest，明確拒絕而非截斷。平台 activation／writer 排除、重啟 reconciliation、新 scope 註冊仍待完成；沒有遷移資料／啟用 runtime，未編譯／測試。六主線未完成，集中驗收與效能維持最後。

## 2026-10-12：awaited activation lease 與送出邊界（源碼新增、未啟用）

- 新增初次啟用確認 manager：要求既有 Published receipt、fresh frozen review、完整 artifacts 精確讀回及原身分／真正 writer exclusion，再 CAS＋confirm 啟用紀錄；回傳的 runtime 每次 storage await 前後核 lease，不自行切換平台 repository。
- publisher 新增鎖內 Published-only 確認，不因 receipt 改成 Preparing／missing 而發布資料。重複關閉舊 handle 不解除新 handle 的 issued 狀態。
- repository 的 submissionScope 核有效 lease，submit 與 fence 在 awaited reads 後、HTTP dispatch 前再次檢查；失效不換 ID、不清 pending、不 fallback legacy。已送出請求仍須 receipt／fence 判定，不能因關閉 lease 宣稱伺服器未提交。
- 平台 writer exclusion、啟用 wizard／factory、資料已前進後的 startup reconciliation、新 workspace 註冊與大 draft 分塊仍待完成。沒有遷移真資料或啟用 runtime；未編譯／測試／HTTP。六主線維持未完成，所有業務功能完成後集中驗收，效能最後。

## 2026-10-12：awaited legacy publication coordinator（源碼新增、未執行）

- 上輪為 progress：frozen migration review。本輪AwaitedLegacyMigrationPublisher先要求真正ALL legacy/new writer exclusion＋fresh recapture exact review；必須注入guard，無default，不以兩次read或UI禁按作排除證明。
- 新Preparing／Published private local receipt綁target、sourceDigest、normalized planDigest；首次無receipt必驗draft／safety／ledger目標皆不存在。先CAS＋confirm Preparing，後逐scope發布reviewed bundle／safety、按origin/user/client去重ledger，過程重查來源／guard／cancel，不自動retry或fallback，不換identity／清來源。
- 完整typed readback比draft／safety全內容、floor、generation及contentDigest，再唯一final CAS＋confirm Published。未知publication新invocation可重查同receipt與exact artifacts，已前進或不同native狀態要求activation-receipt reconciliation，不rewind。
- Published僅本機初始publication證據，不是runtime cutover、完整migration驗收或server receipt／重送權限。平台writer排除、activation manager／wizard、完整大draft分塊仍待接；尚未呼叫coordinator、capture真資料、初始化ledger或切換runtime，未編譯／測試。六主線維持未完成。

## 2026-10-12：共用 legacy 遷移 planner／frozen review（源碼新增、未執行遷移）

- 前兩段為 progress：awaited safety與planner抽共用。本輪原Desktop純planner移common LegacySubmissionMigrationPlanner，JVM僅保留typealias／delegate，不重造三端規則；common SHA256與4-byte big-endian framing保持原sourceDigest算法。
- LegacySubmissionMigrationReview凍結完整normalized plan，提供sourceDigest／planDigest與每scope草稿操作、pending／停止wire、fence／deletion counts、floor、quarantine摘要。requireUnchanged須重新capture的原sourceDigest與完整normalized JSON都一致，摘要數量或scope hash相同不足以通過；只回fresh clone，不代表exclusion／授權／啟用。
- Awaited draft新增明確adoptReviewedBundle入口，供Preparing publication receipt＋真正writer exclusion之下使用；scope完整驗證，已有狀態只接受exact reviewed bundle並confirm，不倒回已前進draft。verified no-draft以scoped tombstone保存generation；原legacybytes不刪、不改pending IDs／seq。
- 實際migration coordinator／activation receipt／writer exclusion平台實作與wizard UI仍未接，沒有capture真legacy資料、寫DB／Settings、初始化ledger或啟用三端。未編譯／測試；六主線未完成，集中fixtures／真跨端與效能最後。

## 2026-10-12：repository awaited safety／刪除證據純核對（源碼已接、三端未啟用）

- 上輪為 progress：safety codec／store。本輪AwaitedRecoveryBoundary選唯一已配置store；native legacy在Default背景處理，新awaited錯誤不fallback Settings。repository的fence state／begin／observe／restore proof／ACK deletion record改await，僅仍啟用legacy native WS的最終同步guard保留舊store檢查。
- beginFence awaited durability完成後再核cancel與原pending一致，才POST fence；receipt commit先await observe與完整deletion records，後sequence／draft ACK，不以未知lookup清marker／pending。
- 從CommittedDeletionStore抽共用deletionEvidenceFromAcknowledgement／requireDeletionRestorationEvidence，先核完整原ACK／payload，再核delete IDs、expected版本、cascade IDs與saved snapshots。native與async共用，不複製新證據規則；legacy無cascade proof不創造restore authority，restore返回版本數字拒字串。
- generic tombstone guard只處理restore_objects／restore_relations，不把restore_media_reference誤當relation刪除還原；F仍需其獨立原交易provenance，不因此略過F驗證或宣告F history完成。
- AwaitedSubmissionRuntime完整三介面現在已被repository使用，但三端legacy inventory／floor／identity activation與大draft分塊尚未接，WS gating未放寬；未編譯／測試／真HTTP，六主線仍未完成。

## 2026-10-12：共用 recovery-safety codec／awaited fence與deletion（源碼新增、未啟用三端）

- 上輪為 progress：repository awaited boundary。本輪RecoverySafetyBundle／Codec由JVM移common，保持v1欄位順序／序列化／legacy fingerprint，不改既有namespace、不搬移資料；SHA256改common實作，strict Unicode／duplicate-key／數值型別拒絕，不以字串tombstone或serverSeq作證。
- 新AwaitedRecoverySafetyPersistence／AwaitedAtomicRecoverySafetyPersistence保存同scope完整fence＋deletion bundle，CAS＋confirm後返回；scope／cancel在await後核，缺store要求明確verified adoption，不把missing當empty。原wireDigest精確綁request，beginFence不可越過terminal，Unknown不清marker、不創造marker，terminal互相矛盾拒絕，同終態再confirm。
- deletion batch同entity唯一、同tombstone不能改證据，新tombstone及serverSeq都必須前進；資料上限／quota失敗保留pending與原紀錄，不自動GC。legacy adoption只由排除writer＋完整catalog審閱呼叫，已有同digest只confirm、不倒回舊source。
- AwaitedSubmissionRuntime現在必須同時提供drafts／sequence／safety，不能構造只有pending的半套配置。repository的fence／deletion方法與三端activation仍待接新safety，現不啟用／不放寬WS條件；未編譯／測試／真HTTP，六主線未完成。

## 2026-10-12：Backend repository awaited 草稿／序號邊界（源碼已改、三端未啟用）

- 上輪為 progress：awaited adapters。本輪新增opaque AwaitedSubmissionRuntime／AwaitedDraftBoundary／legacy awaited sequence wrapper，repository 的journal／pending／stopped／ACK／restore／remove／stage／evidence與sequence reserve／advance改suspend boundary，native舊storage用Default背景執行，不混新舊namespace、不因awaited故障fallback Settings。
- prepareSubmissionLocked改suspend；retainDraftAwaited在submissionMutex內重新驗baseline與能力再保存，Desktop native現在宣告awaitsDraftPublication讓既有UI先await再publish；同步retainDraft僅允許legacy路徑，awaited配置明確拒sync捷徑。
- awaited配置不更新legacy Settings sequence counter；原Desktop legacy配置仍沿既有native ledger／控制規則。fence／deletion recovery-safety仍待全面awaited化，所以新runtime不能單憑awaited pending打開WS寫入；canSubmitRealtime維持已啟用native完整配置，三端無實際啟用或資料搬移。
- 尚需legacy inventory／floor／identity activation、三端recovery-safety factories、完整draft分塊、fixtures與真雙端驗收；本輪只source inspection，未編譯／測試／真HTTP。六條主線維持未完成。

## 2026-10-12：跨端 awaited pending／序號儲存介面（源碼新增、尚未啟用）

- 上輪為 progress：WS原請求ACK。本輪核 mobile/Web仍為sync Settings draft boundary；不以Web enqueue或checkpoint runtime代替真正pending durability，不放寬canSubmitRealtime條件。
- 新AwaitedWorkspaceDraftPersistence／AwaitedAtomicWorkspaceDraftPersistence沿既有WorkspaceDraftBundleTransitions，完整bundle含journal／pending／ACK／stopped evidence／retained archives，scope envelope＋tombstone保存generation，strict UTF-8／duplicate keys／CAS＋confirm後才返回。stage不補wire-only journal、不自動retry；同bundle重試只confirm，並行變更拒絕。
- 新AwaitedClientSequenceAllocator／AwaitedAtomicClientSequenceAllocator跨workspace共用origin/user/client high-water；先驗既有floor且排除legacy writers才能明確initialize；缺失／壞資料不當0，count1..200，reserve先durable再返回range，advance不降值且即使無變更仍confirm。
- Desktop既有advance的no-op分支也補confirmDurable，避免fresh read冒充前次unknown耐久障壁已完成。
- 尚未把Backend repository所有draft／ledger呼叫轉awaited、注入三端runtime或實作legacy遷移審閱；目前不啟用新adapter。完整draft仍受選定platform single-record上限，未來須接分塊或明確上限流程，不截內容。未編譯／測試／HTTP，六主線保持未完成。

## 2026-10-12：原耐久請求 WS submit／ACK 接線（部分平台源碼接通、未驗證）

- 上輪為 progress：協作diagnostics／限流。本輪 owner 增加單筆bounded等待的submit_operations；原protocol／client／transaction／baseVersion／operations沿pending，不生成新ID。write前核Live、edit role與repository原pending／fence gate；30秒ACK timeout／cancel／socket release皆不清pending、不autoREST fallback。
- ACK綁workspace／epoch、transaction、完整ordered operationIds、actor／client／clientSeq／baseVersion／kind／schemaVersion、交易boundary與submitted payload保留語意。accepted／duplicate才進既有deletion evidence→sequence advance→draft acknowledge路徑；conflict／rejected留下原請求並顯示固定安全code。ACK不套projection、不冒充fan-out／durablecheckpoint，陌生或晚到unmatched ACK fail／原receipt待查。
- Repository選當前註冊Live owner寫入；只有native sequenceAllocator＋atomic stopped/pending bundle能力配置才啟用WS寫入（現Desktop native），Android／iOS／Web既有pending durability尚未完整接通故保留REST，不宣告四端WS寫入完成。UI明示實際寫入路徑；root scope／finally／stop取消註冊與client.close。
- shared ACK驗證加入preservesSubmittedValues，REST與receipt共同受保護。修正既有REST conflict fresh state被舊conflict版本覆蓋的問題：fresh current須至少到conflict head，直接保留fresh內容／version，不降標籤版本。
- mobile／Web durable pending adapter、正式reconnect／原receipt/fence跨端收斂與fixtures集中驗證仍待完成；未編譯／測試／真服務。六主線維持未完成，效能最後。

## 2026-10-12：協作安全除錯／Presence cooldown（源碼新增、未驗證）

- 上輪為 progress：outgoing Presence。本輪 BackendRealtimeDiagnostic 僅保存固定stage／allowlisted code／HTTPstatus／retryAfterMs，無host、ticket、ID、rawframe／exceptiontext；成員面板持續顯示最近紀錄，啟動前metadata／capability HTTP失敗也顯示安全HTTPstatus。不把歷史retryAfter當目前倒數。
- authenticated protocol_error嚴格key／code／retryAfterMs解析，rate_limited只在Live接受（0..60s限制，需正值），publisher以monotonic cooldown背壓未來意圖、不自動重播遭拒cursor；未知／非法錯誤fail不續租。pre-auth雙null scope固定error可轉安全failure，但不成立join／Live／Presence。
- 等待deadline被新error延長時重新核cooldown；清除因限流受阻且consent已撤回時stopPublication關socket，避免heartbeat替舊cursor無限延租。publication失敗與socket取消保留Failed／Revoked／Resync終態；重複close不覆蓋原diagnostic。
- 尚未本機collaboration log／diagnostic copy、WS原提交ACK接線、reconnect完整流程／跨端驗收；未編譯／測試／真HTTP。六主線持續未完成，效能最後。

## 2026-10-12：正式 Presence outgoing 與畫布分享（已接源碼、未驗證）

- 上輪為 progress：畫布入口。本輪 socket 提供受控writer，與heartbeat／catch-up ACK同Mutex；只接受scope／epoch／protocol精確presence_update，實體寫入前核連線有效與publisher的最後consent／Live gate。結束同步停writer，不累積pointer queue。
- owner提供綁session／subscription／room／實體epoch的publisher，一次只有當前token有效；handle local sequence驗單調，server sequence由owner維持跨handle不重置。cursor有限world bounds，selection驗UUID／authoritative IDs、尊重server maxSelectionIds；cursor與selection各依capability做背壓，delay後再核scope／取消／consent。
- 畫布publisher runner在正式owner首次啟動時強制發一次目前意圖（預設sharing=false所以clear），避免前一handle殘留。手動share才發非空presence；關閉sharing後原意圖會clear，等待中的敏感publication若失去consent則停止並close owner，不auto retry。非空publication實體write再核sharingbool。
- activity epoch納入owner清理，背景／foreground轉換斷舊連線且重新連線後需再手動分享，停止舊heartbeat替舊游標延租。無owner仍可走原publisher fallback，不能假稱任何fallback即時可用。Live文案更新為REST寫入＋手動Presence分享。
- 原提交WS ACK／receipt／fence、正式reconnect／429診斷、完整雙端驗收仍待辦；未編譯／測試／真服務。六主線未完成，效能最後。

## 2026-10-12：畫布即時協作明確入口／共用 owner（已接源碼、未驗證）

- 上輪為 progress：四端runtime。本輪成員面板在配置durable runtime的Backend repository上增加「連線／中斷即時協作」，Preparing／Joining／CatchingUp／Live／Disconnected／ResyncRequired／Revoked／Failed持續顯示，zh-TW／en同步。不開窗就偷偷連線，不自動retry。
- root持有單一owner，啟動前核既有safe refresh gate，途中核repo／actor／client／workspace／role／subscription／switch／connectionFailed；DisposableEffect換scope取消準備job、close owner。失敗／撤權／resync在close後保留終態，不被Disconnected蓋掉。
- confirmed projection／metadata／Live事件變為notification，走既有authoritative REST refresh＋原本編輯／queue／拖曳／draft gate，不以廣播直接覆蓋樂觀history或清pending。仍保留REST輪詢，REST寫入未切換到WS。
- Presence與通知observer共用owner而非另開socket；非Live同步gate隱藏Presence、狀態／finally清空。分享仍未接publisher，Live文案明示「寫入REST／游標分享尚未接上」，不宣告全協作完成。
- 未編譯／測試／真服務／GUI驗收；後續接outgoing Presence、原提交ACK／receipt／fence與reconnect、安全診斷、跨端集中驗收及效能最後。六主線保持未完成。

## 2026-10-12：四端 checkpoint runtime／repository 入口（已接源碼、未驗證）

- 上輪為 progress：分塊manifest。本輪新增opaque RealtimeCheckpointRuntime 與 BackendWorkspaceRepository 可選注入；createRealtimeOwner 綁固定部署／current preferences actor/workspace/client／subscription，先fresh canvas capabilities，再以同caps開checkpoint store與owner。每await前後由scope gate確認，不使用預設server替代部署。
- fresh metadata 明確要求metadataRevision非字串有限safe integer，不以缺欄位0冒充；state沿 negotiated decoder。HTTP JSON strict UTF-8／重複欄位、有15秒timeout與64KiB metadata/capability、64MiB state讀取上限，錯誤只保留HTTPstatus、不讀raw error body。
- Desktop native server factory沿private atomic root提供background awaited records；Android server-mode工廠新增noBackup private checkpoint leaf；iOS server-mode工廠新增private leaf；Web server-mode工廠沿Documents awaited IDB namespace＋獨立saltedkeys。所有native read／CAS／confirm離開UI thread，Web真awaited。
- 原本local工廠／資料格式不改；runtime建構不開socket、不搬移／清資料，default無runtime backend不冒充durable能力。現有Android／iOS draft/submission仍有各自storage待辦，不能以checkpoint注入代表全主線durable完成。
- 未接畫布owner生命週期／通知與Presence共用、publisher／WS原提交ACK／reconnect；collaborationTransport仍RestPolling。未編譯／測試／真HTTP，六主線仍未完成。

## 2026-10-12：大型協作 checkpoint 分塊／atomic manifest（源碼新增、未驗證）

- 上輪为 progress：checkpoint store。本輪解除其單record 4 MiB限制：完整紀錄最多64 MiB，分為最多129個512 KiB UTF-8獨立有效區塊；依scope＋chunk摘要／長度得到64hex immutable key。
- 每塊CAS首次保存、精確bytes／generation=1驗證＋confirm後，才單一CAS發布version2 manifest（scope／總bytes／整份SHA256／ordered refs）。取消／錯誤／其他writer競爭不提交部分manifest；chunk競爭只接受同內容，不覆寫。
- read先驗manifest邊界，再驗每塊scope派生key／length／hash／UTF-8，組合後核whole digest／strict JSON／domain；最後重新核manifest generation＋bytes未變，不混讀不同checkpoint。legacy v1單record維持讀取，不在open時搬移。
- 原CAS＋durability ACK不明恢復維持exact完整next與同／下一manifestgeneration；chunks不是network receipts。舊chunk／失敗孤兒保留，GC／quota仍需reader-safe政策與平台接線，不能自動刪除資料或宣告不限容量。
- owner factory已沿同store；平台runtime、repository／畫布、Presence outgoing／原提交ACK等待完成。未編譯／測試／真HTTP；六主線保持未完成，集中驗收與效能維持最後。

## 2026-10-12：協作 durable checkpoint／owner 組裝（源碼新增、平台待注入）

- 上輪為 progress：單一 owner。本輪 RealtimeCheckpointStore 以 awaited platform record 保存 scope＋完整 state＋metadata；固定 deployment SHA256／actor／client／workspace 分區、用途 salt 生成 64 hex key，不用 Settings，不存 ticket／Presence／optimistic session。
- 每個 handle 只 open 一次並保留 expected record／generation，write 必須 CAS＋confirm；其他 writer advance 不自動採為新 baseline、不覆寫。exact next＋同／下一 generation可恢復本機原write的ACK不明，不能冒充網路receipt。projection 與metadata不降版，同版本不同完整內容拒絕，UTF-8／duplicate fields／scope／domain全驗、不重置壞資料。
- createOwner 核固定serverScope，已有checkpoint直接讀；只有不存在時才用 fresh authenticated state／metadata initialize；串接 owner的save callbacks至真正awaited CAS／confirm。每await前後核scope／cancel，沒有自動開socket，run另行授權生命週期。
- 核現有Web record上限4 MiB、Desktop上限6 MiB，先統一checkpoint上限4 MiB並顯式超限fail。不宣稱大型畫布完成；完整交付前須補分塊checkpoint／atomic manifest，不能藉縮小輸出或忽略節點通過。
- 平台runtime注入、repo／UI 共用owner、Presence publisher、原提交ACK、reconnect仍待接。未編譯／測試／真HTTP；六主線及大畫布保存仍未完成。

## 2026-10-12：單一協作連線 owner（源碼整合、未接 repository／畫布）

- 上輪為 progress：heartbeat／Presence。本輪 BackendRealtimeOwner 整合 prepare ticket、socket、durable projection callback、fresh metadata／snapshot callback、Live／Presence state flows。僅 run 一次、不自動 reconnect；觀察 flow 不另開 socket。
- 固定 endpoint／actor／workspace／client／subscription／connection epoch；Preparing／Joining／CatchingUp／Live／Disconnected／Revoked／ResyncRequired／Failed 分開。每次 async 邊界重新核 scope／cancel，撤權／resync 明確保留終態，finally 关闭 gateway／socket／projection／receiver並清 Presence。
- 所有 transaction 沿既有 reducer，平台 persist callback 必須 awaited CAS＋confirm 成功；confirmedProjection 僅成功後更新。metadata 新 revision須完整 fresh read／耐久保存，same revision不同內容拒絕，不用 join 缺少 title 的部分資訊冒充完整狀態。
- 查 backend WORKSPACE_ROLES 真值含 commenter，修正前兩輪 projection／presence 只接受三角色的缺漏；owner metadata 同樣接受四角色，peer role 不構成編輯授權。
- 尚未注入平台 durable checkpoint store／repository 共用 owner、Presence publisher、WS submit ACK 與 receipt/fence、重連與画布狀態 UI。陌生 ACK／protocol_error fail，不拿來清未知提交。產品仍 RestPolling，源碼未編譯／測試／實際連線，六主線未完成。

## 2026-10-12：socket heartbeat／Presence 正式事件解析（源碼新增、未接畫布）

- 上輪為 progress：capabilities／ticket。本輪 socket 接 protocol heartbeat，validated join consume 後才開始；物理寫入 Mutex 保護，單調序號／精確 ACK，兩筆未 ACK 則 fail，join consume 30 秒未完成 watchdog 主動失敗，不自動 reconnect 或重送 operation。
- BackendRealtimePresence 驗 scope／connection epoch／Live，strict peer／cursor／selection 型別與 bounds；snapshot 一次建立 room、delta revision 缺段則 close／resync，舊 delta 不延長 lease，connection identity 不可改。正規化為現有 full snapshot model，不以 peer role 或 selection 當內容或權限。
- authenticated in-order heartbeat ACK 的 renew 介面可延長目前已知 room lease；尚需 owner 只在 Live 呼叫並在 revoke／timeout／socket finally 清除 UI snapshot。displayName 域限制對齊後端 100 UTF-16 字與 400 UTF-8 bytes。
- 尚未接統一 repository owner、Presence publisher、原提交 ACK／durable scope／reconnect／畫布入口；目前產品仍 RestPolling。未編譯、測試或真連線；最後集中驗收與效能最後，六主線維持未完成。

## 2026-10-12：正式協作能力／一次性 ticket HTTP（源碼新增、未驗證）

- 上輪為 progress：socket＋projection。本輪新增 BackendRealtimeGateway，讀 workspace-scoped capabilities 後取得 protocol 1／client-scoped ticket；固定目前部署，不猜測其他路由，不自動 retry／reconnect／提交 operation。
- 嚴格 capabilities 型別／limits、同 origin socket endpoint、16 KiB HTTP body、JSON／UTF-8／重複欄位；ticket 只接受 201＋no-store、精確 capability URL／protocol、base64url token，驗 expiresAt，server Date 存在時比較期限。不用裝置 wall clock推定有效。
- PreparedRealtimeJoin 不提供 data class copy／ticket toString，僅記憶體保存且 takeJoin 一次；計時從 ticket HTTP 前開始，包含準備耗時並預留 1 秒，最長 15 秒 HTTP timeout。connectPrepared 綁同一 capability，handshake 後送 join 前再次核 monotonic期限與 scope／取消。
- socket 改用現有 platformHttpClient，Desktop／Android 沿原有 OkHttp 不自動網路重試／不轉址設定。尚需正式 Web／iOS engine 握手核驗。
- 尚未接 repository／畫布 UI、heartbeat、Presence、提交 ACK／receipt／fence與 reconnect；產品仍 RestPolling。未編譯、測試或發 ticket／socket 真請求，六條主線仍未完成。

## 2026-10-12：正式協作 socket／durable projection 基礎（源碼新增、尚未接產品入口）

- 上輪為 progress：方案簿期限恢復。本輪新增 BackendRealtimeSocket 與 BackendRealtimeProjection；Ktor WebSockets 共用依賴沿現有 3.6.0，不開後端或測試。
- socket 僅接受目前 API 的同 host／port／HTTP→WS 或 HTTPS→WSS、固定 realtime path，拒 credentials／query／fragment，不 follow redirect；subprotocol 必須 boarderless.v1，ticket 只放 join frame，frame 嚴格 UTF-8、有上限且順序處理，不輸出 raw frame／ticket。尚需核各端 handshake header 可讀與 engine-level frame allocation 限制。
- projection 封鎖 workspace／connectionEpoch 不符、交易缺段／重疊／錯順序。沿現有完整 transaction reducer；persist callback 成功且 cancellation 檢查後才推進 checkpoint／形成 catch_up_applied。join barrier 不冒充已套用；Live 必须 checkpoint／version／metadataRevision／connectionId 全符合 server live_ready。
- snapshot_required 驗 reason／barrier／版本並限制 snapshot 分支；須 fresh 完整 snapshot、durable persist，再 ACK。metadata title 不在 join 內，較新 revision 須另外 fresh metadata read 與耐久保存，不能只照 server 數字 ACK。
- 仍未接 ticket HTTP、heartbeat、Presence snapshot／delta、operation ACK→原 durable submit、reconnect／撤權、repository/UI 共用連線；產品仍宣告 RestPolling，不假稱正式協作完成。源碼未編譯／測試／真 socket，集中雙端驗收與效能維持最後，六主線未完成。

## 2026-10-12：雲端方案簿原請求期限內恢復（源碼已接、未驗證）

- 上輪取得文件版本與工作區差異證據，未改實作；本輪補 Sending／Unknown 的明確重送入口，沿用固定 mutation（ID／key／body／ETag），不建立替代請求。
- 第一次 mutation HTTP 前保存 server Date＋capability TTL；恢復前讀取新 capability，拒缺失／非標準／多個 Date、Age 快取、時間倒退及原窗口過期。窗口不隨重送延長；TTL 降低取較短值，預留 60 秒，能力讀取 timeout 15 秒、dispatch 前 observation freshness 15 秒。
- 舊 journal 可讀，但沒有原窗口就不能恢復。重送時的 4xx 不證明原請求未提交，仍保留 Unknown；取消仍不推定 rollback，durable terminal ACK 不覆寫為 Unknown。
- HTTP Date 必須由可信部署提供，Web 必須可讀此 header；它不是密碼學 receipt，也不能防伺服器時鐘錯誤或超長在途請求。正式集中驗收須核反向代理、CORS 與 TTL 邊界。源碼尚未測試／編譯／真 HTTP；六主線保持未完成。

## 2026-10-12：雲端方案簿衝突審閱（已接線、未驗證）

- 上輪為progress：cloud UI。本輪新增CloudSchemeConflictReview，fresh GET之前／之後核固定scope與durable task exact；404只視為opaque absent/unavailable，不當tombstone或fence，不以GET相同內容settle原task。
- UI讀Sending／Unknown／Rejected遠端現況並列原任務／目前revision與JSON摘要；狀態不改Committed。只有Rejected可另行審閱新動作：原名稱套最新完整內容（不把新ETag配舊payload）、刪最新版本、不同UUID另存遭拒內容。全部重新stage／send授權，不自動retry或restore舊ID。
- 不確定task仍禁止同resource替代，TTL-aware原key恢復未接完；原task／key／body／ETag與審閱證據保持，不以衝突UI代替正式receipt。多source素材仍不折疊進本機格式。
- zh-TW/en補readback非證明、404非取消、4096字摘要與各新動作警示。最後集中驗證需競爭GET→stage→PUT的ETag變更／412、撤權、原task被另一writer修改、相同內容未提交、late原請求與false-success防護。
- 本輪未測試／編譯／打包或真HTTP，六條主線仍未完成，cloud TTL／WS／F durable與cross-kind增量、真人Claude及雙端集中驗收持續保留。

## 2026-10-12：雲端方案簿產品視窗（部分接線、未驗證）

- 上輪為progress：平台runtime。本輪Workspace方案簿在durable runtime＋固定server factory存在時提供CloudSchemeDialog；modal納入draftReviewVisible阻擋canvas／keyboard／Presence input，scope切換重置。開窗不發HTTP，明確連線讀capabilities／首頁、逐資源fresh GET與下一頁，410不自動restart。
- 本機選定方案上傳、fresh cloud資源改名／刪除先形成固定UUID＋ETag完整mutation審閱，再確認stage durable，另確認send。outbox16筆／頁；只Prepared可首次送出，Sending／Unknown展示保留但不能直接重送，TTL／衝突恢復仍待接。
- 選定cloud資源可明確identified-save至本機library，不插入畫布、不作assetgrant；多source binding不折疊丟資料（本機import停用），原檔／縮圖引用仍由後端ACL／Ready驗證。關閉後讀回本機庫，未知本機save留既有pending import恢復。
- gateway在capabilities await前後重新核cancel／scope，避免換user／workspace後繼續PUT。HTTP錯誤只顯示狀態碼，無rawbody；zh-TW/en新增授權／永久delete／取消不等rollback／結果未確定提示。
- 尚未測試／編譯／打包或GUI驗收；完整cloud TTL-aware恢復、rename/delete conflict差異／另存與materialization、F跨kind與durablehistory、正式WS等仍待完成，六目標未complete。

## 2026-10-12：雲端方案簿平台儲存／固定服務入口（已接線、未驗證）

- 上輪為progress：cloud outbox首次送出。本輪QuickSchemeRepository／Awaited介面新增可選cloudSyncRuntime，native awaited wrapper轉接，不用Settings legacy庫冒充durable能力；Browser AwaitedPersistentQuickSchemeStore直接用現存awaited library recordstore。
- Desktop FileStore共享原records adapter／lazy root；Android與iOS共享各自AtomicLocalRecordStore。cloud key與library Key分開，既有方案格式／local Int ID不改。native read／CAS／confirm用background awaited wrapper，不在UI thread同步I/O；Web維持真async IndexedDB ACK。
- 核平台Key要求64小寫hex，修正outbox原帶前綴key，現在以用途salt＋scope JSON的SHA256作key，不以放寬filesystem／IndexedDB規則解决。既有未發布模型無需搬移使用者資料。
- BackendWorkspaceRepository暴露CloudSchemeServer固定baseUrl factory；runtime取得或mode切換不自動連線，不用defaultBackendBaseUrl替代已選部署。adapter已有actor／serverScope可與outbox精確比對。
- 平台source已接，但尚未UI／TTL恢復／衝突與cloud cache／local import mapping，未測試／編譯／打包或真HTTP。最後驗收核四端durable／跨processCAS／取消await、library與outbox互不覆蓋、相同actor不同部署隔離。六主線仍未完成。

## 2026-10-12：雲端方案簿 durable outbox／首次送出（部分實作、未驗證）

- 上輪為progress：B HTTP adapter。本輪CloudSchemeOutbox使用AwaitedDurableLocalRecordStore，固定deployment hash＋actor＋client scope、單CAS record＋實際confirmDurable；key只含scope hash。128筆／4MiB有界，strict UTF-8／duplicate-key與recordschema，不reset損壞記錄、不自動GC已完成identity。
- 任務保存完整mutation UUID／Idempotency-Key／body／ETag及Prepared／Sending／Unknown／Committed／Rejected。samekey不同內容拒絕；同resource有未決任務不另造操作。exact未知本機ACK可confirm同generation，其他writer変更CAS拒絕。
- sendCloudSchemeTask在HTTP前await保存Sending，重新核actor／endpoint／scope；response保存真HTTPstatus與完整validated resource，delete只接受204。cancel保留Sending不推定rollback；ACK落盤不明不重送、不用Unknown覆蓋已存Committed。gateway不再丟掉mutation HTTP status。
- 尚未平台factory／產品UI與TTL-aware明確恢復。beginAttempt只接受Prepared，重開Sending／Unknown不當retry授權；後續需依server idempotency window與readback處理，不用GET內容相同冒認原提交。outbox不改local library／canvas，不自動同步。
- 未測試／編譯／打包／真HTTP。最後集中須跨平台await/CAS、process kill、失敗ACK與同身份readback、body／ETag衝突與期限外停止；cloud UI、WS、F durable整合／cross-kind增量與真人Claude等仍未完成，六主線不complete。

## 2026-10-12：雲端方案簿 HTTP adapter（部分實作、未驗證）

- 上輪為progress：F模型／replay與cross-kind缺口。本輪改推B主線，不以F缺口停住整goal，未測試／編譯／打包或真HTTP。
- BackendQuickSchemeGateway按正式user collection提供capabilities／list／get／create／update／delete；resource UUID與固定Idempotency-Key由caller傳入，create If-None-Match:*、update/delete具體strong ETag；資源body／header ETag精確一致、owner／revision／IDs驗證。
- CloudSchemeBody驗selection v1–v9正式domain與bindings原檔／縮圖exact coverage，不雙重編碼payload。版本協商與maxRequestBytes在mutation前檢查；保留素材availability為只讀狀態，不授予跨workspace materialization。
- 成功JSON用streaming execute、4MiB+1偵測／15秒／strictUTF-8／duplicate-key parser；單request最大1MiB，name1..80Unicode scalar。page bounded且ID唯一；cursor_expired等只傳HTTP分類，不自動從首頁重跑或換資源ID／ETag重送。無raw錯誤body／Key／signedURL進UI。
- 此僅adapter與固定mutation模型，尚未接repository factory、durable endpoint/user-scoped outbox、衝突審閱／重開與產品UI；caller必須先durable保存及授權，尚不啟用自動HTTP。最後集中需實際Kotlinserializer與B手寫fixtures雙向核對、ETag競爭／unknownACK／TTL／cursor失效／ACL及跨端重開。
- 六條主線未complete；F cross-kind後端增量、正式WS adapter／真人Claude／完整多端與最後集中驗收仍未完成。

## 2026-10-12：F素材引用還原模型／replay（部分實作、未驗證）

- 上輪為progress：sequence writer。本輪核F payload／worker provenance，新增capability requiresProvenance＋operationKinds辨識及restore_media_references回播，驗object/type／lock／expected-version／nextversion／original UUID／targetSide／restoredReference keys，保留非引用properties，不當作Ready或下載grant。
- BackendMediaReferenceRestoration以完整原ACK、固定submitted operation與baseline MediaReferenceChange推導originaltransaction／operation ID；request用fresh session的權限／版本／目前另一側引用核對，只送server provenance定位，不送任意assetId。此為證據模型，尚未接durable journal／UndoRedo mapper與產品批准入口，不自動執行HTTP。
- 核到cross-kind缺口：APP引用含mediaKind且可替換不同種類，F只記assetId／thumbnail。暫時模型拒cross-kind，未放寬正式history；BACKEND_NEXT_DELIVERY補三欄provenance／committed schema／migration／fixtures交付需求。這是程式碼風險，尚非真人故障重現。
- 未測試／編譯／打包／真HTTP。F剩durable保存／unknownACK及UndoRedo整合；跨kind需後端增量，cloud schemes／WS與最終多端／Claude驗收亦未完成，六目標保持完整。

## 2026-10-12：時序圖正式 operation／replay 接線（已實作、未驗證）

- 上輪為progress：D圖形writer。本輪按E已交付canonicalDiagrams與committed payload接線，未測試／編譯／打包或真HTTP，不改backend。
- 新BackendSequenceWire將map按containerId排序為正式陣列，完整encodeDefaults／explicit-null語意，schema／semantic驗證沿domain；graph object／relation ID驗lowercase UUID，semantic ID不改成UUID。讀array拒重複container與超64份，不以Map去重遮蔽錯誤。
- supportsSequenceDiagrams須正式schema、operationKinds與可寫protocol匹配；prepare／retain不再無條件拒所有sequence，仍受能力gate。update_sequence_diagrams wire帶expectedversion及完整before／after，不單獨PATCH。
- reducer驗權威next=expected+1、before完整map一致與schema上限；graph與metadata在完整transaction後才驗hasValidSequenceBindings，不中途發布／跳過缺引用。UndoRedo沿原domain inverse＋delete／restore_*同交易、版本單調。
- recoveryProjection保留sequence陣列／version並核整體round-trip；prepared request重建與原receipt／digest身份不變。最後集中驗證需核E正式fixtures、巢狀alt／par／loop、自訊息、CAS／before衝突、graph更新漏metadata rollback、刪除UndoRedo／unknownACK與重開。
- F素材引用歷史還原、cloudscheme CRUD／durable outbox與WebSocket仍待接；真人Claude及全部跨端驗收也未完成，完整六目標未complete。

## 2026-10-12：圖形寫入 DTO 與恢復接線（已實作、未驗證）

- 上輪為progress：canvasStyle writer。本輪按D批次既有source實作，未測試／編譯／打包、未改backend。
- capability已協商read schema且minWritableProtocolVersion=1時，開啟relationGeometry／vectorPath／textPlacement writer；保留prepare／retain的featuregate。toExpandedDtos與prepared request重建傳遞能力，原digest不重新配置。
- relation create／update完整encodeDefaults geometry；自循環必須明確geometry，不臆造保存資料。geometry變更整體替換，清回legacy absence轉為正式full-auto reset，不送非法null；domain歷史before比對將legacy absence與full-auto視為相同路徑，其他屬性及版本仍exact。
- TextNode create／update保存typed vectorPath／textPlacement，完整defaults／nullable欄位；清除送null。新增supportsTextPlacement，local／backend與素材canonical upload contract gap依能力處理，不再無條件阻擋已交付功能。
- recoveryProjection對物件／線條採同能力的mapper，replay已有properties／style shallow merge與domain驗證，不丟typed資料。delete／restore原IDs與provenance仍沿既有路徑。
- 最後集中驗證必須核D正式fixtures、defaults／null清除、selfloop／平行線／插線UndoRedo、manual→auto歷史、旋轉與world拖曳、unknownACK重建及重開。sequence／F provenance／WS／cloud schemes仍未接完，完整六目標不complete。

## 2026-10-12：正式 canvasStyle 寫入與恢復接線（已實作、待集中驗證）

- 上輪後端文件commit不屬APP實作，本輪以167086a交付源碼核對C批次operation／committed payload，未改backend、不測試／建置／打包。
- supportsCanvasStyle須read schema1、正式operation kind與minWritableProtocolVersion=1均匹配；未協商不啟用。retainDraft／prepare保留能力gate，正式DTO帶expectedCanvasStyleVersion＋完整encodeDefaults style，不省略default欄位。
- reducer新增update_canvas_style：核完整payload、expected等於目前style版本、權威next=expected+1且safeinteger，原子更新style與workspace checkpoint；所有交易前後完整domain驗證沿用協商read能力。REST replay／unknown-ACK恢復傳遞能力，不再把非default style默默丟棄。
- recoveryProjection保留baseline style與version，完整round-trip核對；原request digest／receipt／fence／journal身份不更換。Undo／Redo使用既有UpdateCanvasStyleOperation inverse與新版本送出，不倒退版本。
- 待最後集中驗證：C批次fixtures default完整wire、CAS conflict／duplicate、mixed transaction rollback、UndoRedo、unknownACK→receipt→原log重建＋重開。geometry／vector／sequence寫入及F provenance、WS／cloud schemes尚待接，六主線未complete。

## 後端 A–F 接線第一階段：能力協商與新版 state（已實作、未驗證）

- 上輪後端交付屬新的證據：A–F源碼與fixtures已存在，不再以缺服務源碼阻擋APP。此輪唯讀核正式capabilities controller與state格式，未啟動backend／付費AI或執行測試／建置。
- APP repository從固定部署GET capabilities協商讀取schema；404才走舊能力，其餘HTTP錯誤不當缺能力。只協商read，尚不啟用未接好的writer。
- WorkspaceStateDto保存canvasStyle／version、sequenceDiagrams陣列／version；mapper無損讀vectorPath、textPlacement、geometry，驗自循環明確geometry、重複container与完整sequence bindings。讀新版state要求宣告能力及成對欄位，不默默忽略或套預設。
- repository載入與cached replay／恢復mapper使用已協商read能力；未知新operation仍fallback權威state。writer旗標／prepared submission／正式ACK／reducer、snapshot v3、F provenance Undo、cloud scheme CRUD／outbox與WebSocket transport尚待接，不將read接線當整體同步完成。
- 最後集中驗證要用正式A–Ffixtures核normal／錯primitive／unknownschema／缺欄位、vector／自環／sequence bindings及舊state；當前源碼尚未編譯或驗收。

## 2026-10-11：AI 任務間請求狀態隔離（已實作、待集中驗證）

- 上輪為progress：AI每輪结果。本輪檢查發現round在新task重設為1，Preparing在本輪request card尚未建立時，原indexOfLast(round)會匹配上一個task的card並覆蓋其terminal diagnostic。
- 每次run coroutine保存自己的round→request entry ID表；診斷只更新這次run所建立的card，未建立card事件只進目前狀態區／安全log。授權仍由目前pending review處理，沒有跨task共用consent。
- 唯讀核目前backend routes／capabilities仍只见素材preparation宣告，未提供正式WebSocket／user quick-schemes／canvasStyle／sequence operation接線；不猜endpoint、不改backend。本轮未測試／編譯／打包或真HTTP，不能據局部修正宣告完整六主線完成。
- 最後集中QA增加連續兩個任務同round=1、新任務preflight失敗及取消：前任務Completed／HTTP／request ID須原封保留，新事件不得混用前任務回覆。

## 2026-10-11：AI 每輪請求結果保留（已實作、待集中驗證）

- 上輪為progress：素材量測審閱。本輪唯讀本機安全log，最新仍2026-10-09T02:29:52Z OpenAiChat／HTTP200 SSE Completed，未見新failure；不讀token、不發付費请求，不能據此認列Claude／Apply／Undo通過。
- AI請求卡片將「已批准」與傳輸狀態分開；每輪保留latest typed diagnostic及HTTP／contentType／request ID等安全摘要，下輪Preparing不覆蓋前輪卡片。工具回覆＋isolated tool turn完成後記錄本輪Completed，不把HTTP200當完成，不把草稿執行當已改正式畫布。
- 每次新任務清舊message／round／local-log旗標；複製安全診斷附request entry／round／approval及結果，不帶endpoint／body／credential／工具arguments。本機既有安全log也收每輪完成事件。
- 最後驗收需包含授權後preflight失敗、401／403、200但空／錯UTF-8／半截SSE、工具回覆後下一輪拒絕、取消與scope失效、Claude每輪與草稿Apply／重開Undo。此次未測試／編譯／打包，六主線未完成。

## 2026-10-11：Ready 素材量測值審閱與授權綁定（已實作、未驗證）

- 前一輪為progress：declared／verified APP接線。這輪續作產品審閱入口，不跑測試／編譯／打包或真HTTP。
- 素材上傳面板並列本機宣告與Ready伺服器實際尺寸／duration（null標—），說明display orientation、本機原檔與history保持、映射不等於畫布發布。沒有目前選定request的Ready回覆則不提供映射确认；換來源／重新lookup／失敗清除舊量測審閱。
- verifyUploadedAssetMaterialization新增必要expectedReadyIdentity；fresh Ready receipt必須等於使用者已看過的完整descriptor，其後仍核server asset、private原檔hash／size／EOF、final receipt與head。未知local ACK只可確認已保存的同一mapping，不可用舊approval保存新量測值。
- 後續原檔核對仍採localSource保存的本機metadata，server檢查採mapping.source實際值，避免duration容差或null補值造成重開後誤拒。QA補Ready量測變更重新審閱案例；需最後正式fixtures／多端／重開驗收，不當已通過。
- 完整六主線仍未complete；本輪未代改backend或連線AI，其他服務依賴不因這個入口完成而解除。

## 2026-10-11：APP 對接 declared／verified 素材回應（已實作、未驗證）

- 前次為後端交付與決策資訊，本輪唯讀核對最新 worker、envelope schema／service，確認 declared／verified 已存在；依正式源碼修改 APP，不執行測試／編譯／打包或真 HTTP。
- preparation receipt 必填 declared／verified；declared 完整匹配原 request metadata／digest，固定 remote reservation descriptor 使用 declared，不再與 Ready asset 的量測尺寸混用。Ready 必須有 verified，與公開 asset 實際值一致；非 null 宣告尺寸 exact、duration max(100ms, verified×1%)，byteSize／checksum／scope仍嚴格一致。舊回應缺新增欄位停住，不回退 v1。
- Ready 映射保存實際 remote descriptor，另保存原 private localSource；本機 asset ID／尺寸／duration／history 不改寫，後續 local original核對用private descriptor、server核對用verified descriptor。stage／canonical upload的reservation連結只比較不可變identity，其後仍fresh驗Ready、完整remote descriptor、原檔hash／size與ACL。
- declared_metadata_mismatch新增安全錯誤token與中英文提示；prepare／lookup／complete狀態顯示拒絕原因，不自動配置新request或重傳。Ready／其他非prepared回應不得帶PUT directive。
- 待最後集中驗證：新版正式fixtures、null→verified、duration容差边界／超界、EXIF／video rotation、錯scope／缺verified／錯digest、映射保存＋重開＋後續canonical upload與Undo。舊已存mapping不自動改寫，需明確重新審閱；換素材Undo遇missing仍需後端具原交易證據的還原契約。
- 完整六條主線保持未完成；正式WebSocket／雲端方案簿／canvasStyle／geometry／sequence服務依賴及多端最終驗收仍待交付，不能用後端224個預檢測試認列本轮APP通過。

## 2026-10-11：AI 差異審核的收合記憶與完整資料複製（已實作、待集中測試）

- 上輪為progress：票券取消／恢復修正。本輪延續已加入AI review UI，未執行測試／編譯／打包或真AI请求。
- 新PanelSection.AiChanges使用既有collapsedPanelSections偏好，預設展開；Dialog重開沿用使用者上次收合選擇，與其他面板偏好共用root狀態更新，不以新Settings實例覆寫root快取。不保存proposal、prompt、endpoint、token或任何approval。
- 每筆差異可明確複製完整before／after私人結構資料至clipboard，不自動複製；revision仍為顯示用normalize1，不是可重送operation／正式receipt。顯示4096字元截斷避免切開UTF-16 surrogate pair，完整proposal不變。
- zh-TW／en與QA SOP補收合重開／private clipboard／Unicode截斷驗收。這些是既有審核流程接線，不擴充AI工具權限；六主線及backend契約／真人AI／最後集中驗證仍未完成。

## 2026-10-11：取消換票後的原票券恢復修正（已實作、待集中測試）

- 上輪為progress：v2 streaming response／metadata缺口。本輪核取消／retry接線，發現原ticketIdentity同時作renew draft，取消renew後普通ticket可能用未批准replacement UUID，與「恢复原票券」語意不符。
- LocalAssetUploadPanel分開普通首次ticket與replacement draft identity。普通恢復優先使用durable最後ticketRequestId；renew只有獨立批准後才使用replacement。未批准renew不改原ticket；確認issue／renew前清舊ephemeral directive，失敗須恢復durable目前身分，不把舊lease當新lease。
- 手動lookup開始清舊phase；401／403／404／metadata mismatch清phase與ephemeral ticket，避免仍顯示舊Prepared／Ready操作。原request／public lease及映射不刪，錯誤後仍只readback，不retry／new request。
- QA SOP補取消renew、未知ACK與權限失效的操作序列。本輪未測試／編譯／打包／真HTTP；backend declared-versus-verified metadata及realtime／cloud schemes／style／geometry仍待外部交付，六目標尚未complete。

## 2026-10-11：素材 v2 回應讀取與 metadata 契約缺口（已實作、待集中測試）

- 上輪為 progress：grid style UI。本輪回素材主路徑核adapter／backend worker，未測試／編譯／打包或真HTTP。
- v2全GET／POST改prepareRequest streaming execute，避免普通HTTP helper先完整保存body再套大小上限。成功response限定application/json、最多64KiB＋1偵測byte／15秒、strict UTF-8與duplicate-key parser；失敗仍16KiB／2秒／closed code enum。取消／超界／invalid response不配新ID，不把protocol body當原檔下載。
- worker markReady覆寫尺寸／duration，而APP immutable source descriptor要求metadata exact；null欄位或跨端duration解碼差異可能讓Ready拒收。新增專屬MetadataChanged提示，不泛指網路重傳、不放寬identity gate。BACKEND_NEXT_DELIVERY補declared／verified正式契約與fixtures依賴供user轉交；不是已實測所有素材失敗，也未擅改backend。
- 其他APP主線可續作，六goal未complete；此metadata場景與正式realtime／cloud schemes／style／geometry能力仍需外部交付，最後集中驗證。

## 2026-10-11：文件網格樣式的產品入口（已實作、待集中測試）

- 上輪為 progress：協作transport／handoff。本輪核canvasStyle已有versioned背景與grid fields、renderer支援，UI僅有背景色及個人showGrid／snap；新增grid style dialog，不跑測試／編譯／打包。
- CanvasStyleDraft新增gridOperation，捕捉scope／style version／完整before，after只改gridStyle。Desktop toolbar與compact menu加入入口，只在repository.supportsCanvasStyle且edit scope允許時開啟；目前local支持，未交付server capability不假造共享。
- Dialog提供lines／dots／none、自動對比色及既有ColorPickerWindow，pattern/color preview僅暫態，不改history／文件／viewport／snap／showGrid。color picker Apply只是採納draft color，最後文件確認才dispatch同versioned style operation；固定prepared identity供失敗重試，不另配不同body。
- preview在cancel／dispose清除，user／client／workspace／role改變關閉舊editor並清preview。使用safeDrawing與IME inset，scope改變／stale style拒絕；原Undo／Redo走既有history。zh-TW／en與QA SOP補分離兩種Apply的規則。
- 本輪源碼未驗證，正式共享背景仍需BACKEND_CANVAS_STYLE_API_SPEC服務及雙端驗收；六主線完整保留，最後集中驗證。

## 2026-10-11：协作傳輸狀態與下一批後端交付（已實作、待集中測試）

- 上輪為 progress：scheme pending import重開入口。本輪唯讀核目前api routes仍無realtime ticket／socket／Presence／user-level quick schemes；asset v2可用不代表其他契約已交付，未啟動／改動後端。
- 新WorkspaceCollaborationTransport明確標Configured capability而非physical liveness；Backend adapter為RestPolling，members panel顯示「REST不是即時協作」、WebSocket能力與實際Presence線上狀態分離。不假設觀察Flow或member list即為已加入room。
- Presence接受gate補strict UTF-8／bounded plain identifiers／display name／selection IDs與cursor finite；NaN／invalid surrogate／control token不得进入room state，既有epoch／lease／TTL／scope不放寬。
- 新BACKEND_NEXT_DELIVERY整理realtime→user schemes→canvasStyle→relation/vector/sequence依賴及共同capabilities／fixtures／replay門檻，供使用者轉交backend agent，未自行發其他聊天訊息。修正relation v2文件舊sequence描述，與實際sequence-canvas schema、semantic ASCII IDs／graph IDs、flat branch positions及limits對齊，正式wire capability仍待交付。
- 本輪未測試／編譯／打包或送真服務請求，六主線尚未全部完成；APP其餘接線可續作，不將局部backend依賴當全goal impasse。

## 2026-10-11：快速方案匯入任務落盤與重開入口（已實作、待集中測試）

- 上輪為 progress：identified save／receipt。本輪追加durable pending definition與APP重開審阅入口，未執行測試、編譯或打包。
- Library record v3同atomic CAS保存pendingSaves(requestId＋完整definition)，最多128筆且整庫4MiB上限；v1／v2可讀，v3缺pending欄位拒絕、不reset原件。pending不當正式library item、canvas／cloud operation。stage先落盤、save再將item＋receipt及pending removal同一record發布；未完成save可重開取回同request。
- Native Desktop／Android／iOS與awaited Browser提供stage／listPending；相同pending request不同definition拒絕，已commit則核原receipt／原結果，不另mint方案。unknown stage ACK明確重試只confirm原generation。保存後改名／刪除仍不容舊request覆寫或復活。
- JSON／bundle save先stage definition再identified save。方案庫加入「查詢待完成的本機匯入」及逐項重新審閱；讀回不save、不插入、不renew素材，也不自動使用先前checkbox授權。重新確認使用保存UUID與definition；成功移除pending顯示，已commit但ACK遺失從正常方案list可見原item。
- zh-TW／en與QA SOP补重開pending／scope／unknown ACK驗收。user-level雲端CRUD／自動跨裝置仍依賴backend交付，不以本機恢復冒充完成；六主線完整保留，最後集中驗證。

## 2026-10-11：快速方案匯入的固定保存身分（已實作、待集中測試）

- 上輪為 progress：巢狀sequence editor。本輪核quick scheme JSON／bundle已能搬移完整definition與素材，本機保存回覆遺失原先只以內容dedup，不足以辨別原請求；新增identified save，未跑測試／編譯／打包。
- QuickSchemeRepository／Awaited邊界新增supportsIdentifiedSave＋saveIdentified(requestId, definition)。Persistent library v2同atomic record保存固定UUID／canonical definition digest／allocated scheme ID；同request不同body拒絕，相同request只confirm既有generation，rename／delete後舊request不overwrite／recreate。receipt最多4096且不自動清除，既有v1可讀；v2 missing receipts拒絕，不reset原件。
- Desktop／Android／iOS wrappers與awaited Browser提供此能力；awaited snapshot的confirm要求真正await store barrier，不將計算snapshot當durable proof。legacy Settings不宣告能力，保留原ordinary save行為，不把contentdedup宣稱為identified ACK。
- JSON與bundle import視窗保留同一保存request UUID用於明確重試；bundle gateway修正為repository exact endpoint factory，不fallback預設server。此輪未提供import-task跨process重新開啟UI／雲端outbox，不能宣稱全自動跨裝置完成。
- user-level CRUD／雲端方案同步仍需BACKEND_QUICK_SCHEME_API_SPEC交付；本機request receipt不是遠端授權／素材grant。QA SOP補原請求／rename/delete／v1→v2驗收，六主線完整保留，最後集中驗證。

## 2026-10-11：時序圖分支內引導式編輯（已實作、待集中測試）

- 上輪為 progress：參與者／top-level message與block建立。本輪補巢狀分支操作，未執行測試／編譯／打包；不以既有parser測試代簽新增UI。
- domain 新增 stepsAt(location) 與renameBranch，按stable blockId＋branchIndex讀／改精確語意分支，完整validatedSnapshot拒missing block／branch、非法label及不完整draft，不flatten alt／parallel或重配IDs。
- 引導式editor列出root與巢狀分支，選擇位置後新增／編輯message、排序、刪除及wrap loop／alt／parallel只作用於當前分支；保存標籤保留branch contents。分支至少保留一步，完整participant引用／message／depth／capacity invariant維持；讀入的虛線／nested結構保留。文字輸入上限與domain允許4096字元一致，實際byte／Unicode仍由model gate驗證。
- 最後確認仍導向既有semantic preview及二次Insert，不直接mutatecanvas／HTTP；zh-TW／en與QA SOP補分支驗收。六主線及正式後端sequence capability／最後集中測試尚未完成。

## 2026-10-11：時序圖引導式建立工具（已實作、待集中測試）

- 上輪為 progress：AI草稿前後差異。本輪核時序圖已有strict Mermaid parser／semantic draft／canvas preview及confirmed transaction，但建立入口依賴手寫source；新增引導式編輯，不執行測試／編譯／打包。
- SequenceGuidedEditor 可新增／改名actor或participant、左右排序、指定訊息兩端（可same participant自訊息）、call／return／async／signal及虛線、訊息編輯／上下排序／刪除；引用中的participant刪除由完整draft invariant拒絕，非法labels／capacity不覆蓋舊draft。
- 明確loop包最後一步、alt／parallel将最後兩步變成各分支，保留巢狀結構。existing source先strict parse才能進入；無效／不支援source不暗中丟棄。內部分支編輯維持原source入口，本輪不宣稱full visual nested editor。
- 確認引導式預覽才export canonical Mermaid並帶完整semantic draft進既有scene；原capture／二次confirm／固定transaction／未知保存重試不變，不直接寫canvas或HTTP。離開引導式需明確discard，關閉dialog保留既有discard確認。
- zh-TW／en與QA SOP補建立流程；六主線及正式server sequence capability／真人操作／最後集中驗證仍未完成。

## 2026-10-11：AI 工具草稿的修改前後審核（已實作、待集中測試）

- 上輪為 progress：素材恢復evidence與safe errors。本輪核AI workflow已有設定、per-round批准、typed tools／草稿／batch preview／final apply gate；尚未Claude真服務與完整Apply／Undo簽核，依使用者要求不代送AI或跑測試／編譯／打包。
- 新 aiDraftChanges 從原workspace與實際selected proposal preview生成 exact node／relation／title／canvasStyle／sequence差異，僅normalize entity revision為1，保留parent／transform／geometry／media reference／shape等完整serialized fields，不更動proposal或操作IDs。
- AI草稿预覽新增預設展開的前後差異清單：新增／修改／移除計數、20筆分頁、逐ID展開before／after plain JSON，可選取文字。每欄最多顯示4096字元並標截斷，原完整方案不變，不把display當可匯入／可執行資料，也不推論媒體像素內容。
- 勾選／排除項目後preview與diff一起更新；原每輪HTTP授權、二次Apply consent、frozen unknown保存identity與Undo入口未放寬。新增zh-TW／en文案與QA步驟；本輪源碼尚未驗證，六主線仍完整保留。

## 2026-10-11：素材恢復記錄匯出與可辨識錯誤（已實作、待集中測試）

- 上輪為 progress：Ready mapping／canonical review接線。本輪將 assetUploadPreparations 加入私人同步 evidence 匯出，保留固定request／remote identity／ticketRequestIds／public lease歷史；default-empty維持舊格式相容，仍不含signed URL、headers、credentials或素材bytes，不是可import／replay的submission格式。
- v2 HTTP錯誤最多讀16KiB＋1偵測位元組、2秒，strict UTF-8，只允許closed machine-code enum；message／details／未知code不交UI或exception。新增401認證、403無權、expired／superseded lease、缺上傳原檔、404不證明晚到request不提交的提示，無自動retry／new UUID／renew或fallback。
- 唯讀核AI安全log最新仍2026-10-09T02:29:52Z OpenAiChat／tools9／reasoningNone／temperatureAbsent／HTTP200 SSE Completed，修正AI_DIAGNOSTICS的舊10/8「尚無成功」敘述；不推論所有401或Claude已解決，沒有代送AI或讀key。
- 唯讀核backend routes仍未交付user-level quick schemes及正式canvasStyle／geometry／sequence operation能力；其既有BACKEND_*規格仍為待交付，不假造endpoint或偷丟欄位。六主線保持完整，繼續APP業務接線，最後集中驗證；本輪未測試、編譯、打包、修改backend或提交。

## 2026-10-11：已上傳素材 ID 映射與 canonical 同步接線（已實作、待集中測試）

- 上輪為 progress：上傳 UI／private original streaming 接線；本輪新增 Ready→本機映射→重新審閱→canonical transaction 的產品路徑，不執行測試、編譯、打包或真服務請求。
- 新 verifyUploadedAssetMaterialization：exact saved request／binding／review、fresh canvas head與edit role、v2 Ready receipt／server asset metadata、private original分塊 SHA-256／size／EOF、末端重新核receipt／asset／head，產生 ephemeral publication input，不改 Node或後端。需先保存 remote identity，未 Ready 不授予映射。
- stageUploadedAssetMaterialization 將 verified remote→existing local ID映射同generation存入document，不另建local素材；CAS／固定 request／metadata／namespace／destination gates沿用。unknown保存ACK只確認已寫入exact mapping及原document，不另配UUID。UI 新增獨立publish授權；未有durable結果的重試重新驗證，不將舊proof當授權。
- 保存映射保留本機 Node／Undo及原 proposal不變，requiresAssetIdentityReview提示重新比對；舊方案的apply與prepare入口拒絕，不能偷偷改saved target／receipt。新 merge 使用canonicalLocalWorkspace，在重新審閱／明確套用後保留local ID，後續wire使用server ID。
- prepareLocalSyncUpload 允許已保存 upload preparation＋exact materialization所對應的新server素材，不再限定必須已被server Node引用；仍逐筆驗 Ready／workspace／MIME／exact saved descriptor及thumbnail Image，其他未materialize引用仍阻擋。
- zh-TW／en與QA SOP補publish／重新審閱要求。本輪程式尚未驗證；其他五主線與正式雙端／完整素材恢復／最後集中測試仍未完成，不縮減完整goal。

## 2026-10-11：素材 v2 同步視窗與本機原檔串流來源（已實作、待集中測試）

- 上輪為 progress：v2 gateway／document durable identity／分步 coordinator 已加入；本輪繼續產品接線，不執行測試、編譯、打包或真服務請求，遵循全部業務功能完成後集中驗證的要求。
- Canvas repository 新增同一 endpoint 的 preparation gateway factory，Backend adapter 提供；LocalWorkspaceSyncDialog 新增 LocalAssetUploadPanel，列出本機 primary／thumbnail 素材及已保存請求，20筆分頁。逐步 inspection→reservation→prepare→ticket→PUT→complete 均由明確操作；每筆 request／remote identity 顯示，GET lookup 可手動檢查 prepared／completionAccepted／ready／terminal phase。
- 重新開啟／簽名未知 ACK 的普通 ticket 操作沿用保存 ticketRequestId；獨立 renew 先明確確認取代舊 lease，未確認／未知保存保留同 draft ID。票券 URL 只在 Compose runtime，dismiss 後不持久化；錯誤提示含安全 HTTP status，不吐 raw body／keys，錯誤後唯讀 reload 原 durable identity，不重新 POST。
- withLocalOriginalUploadSource 使用 private local gateway authorize＋exact Ready source descriptor，Channel(1) producer／sequential offset consumer 分批讀取 original，結束／取消關閉 producer，不完整載入影片。PUT coordinator 仍核 exact size／hash／EOF及目前 document／scope；無自動 complete、abandon 或 Node insertion。zh-TW／en 補 UI 文案。
- 尚待 Ready 後映射／canonical review 發布接線與完整產品恢復，正式雙端、全部六主線及最後集中驗證仍未完成。此 UI 尚未編譯或真人驗收，不引用前輪綠燈作新增功能證明。

## 2026-10-11：後端 v2 交付後的 APP 接線（已實作、待集中測試）

- 使用者要求先完成全部業務功能，再集中執行測試／跨平台建置／打包，效能最後；本輪未執行測試或編譯，不能沿用前輪成功結果證明新增程式。
- 唯讀核對 backend preparation service／strict schema／routes／migration 與交付紀錄；v2 服務缺失不再是已知阻塞。後端交付報告為 204 tests 通過，本輪沒有自行重跑或更動 backend。
- 新 BackendAssetPreparationGateway 接 capabilities、canonical explicit-null prepare、原 request lookup、ticket 與 complete；核 binding scope／request digest／固定 asset metadata／phase-status／lease identity，無自動 retry、v1 fallback 或新 UUID；HTTP 例外不帶 raw body、票券或秘密。
- 本機 document 新增 default-empty assetUploadPreparations，CAS／durable 保存 request、immutable remote identity、ticketRequestIds 與 public leases；保留舊票券與固定身分，同結果 unknown ACK 僅確認原 generation。native 與 awaited/browser 共用入口，簽名 URL 不持久化、不改 Node／history／checkpoint。
- LocalAssetUploadCoordinator 接明確 reserve→prepare／lookup→reserveTicket→issueTicket→upload→complete steps；先 durable 後 HTTP，無隱含重試，未授權 canvas publish。單次 PUT 核保存 lease／fresh prepared receipt／local review及同 endpoint，限定每塊至多1MiB，讀取時核 SHA-256／exact size／EOF；不走略過 byte proof 的 direct File 路徑，取消保留原 identity，不自動 complete／abandon。這些為源碼實作，尚未產品 UI 接線或驗證。
- 下一步仍須原檔來源與串流上傳的產品入口／取消與狀態 UI、Ready 後 ID 映射與 canonical review 發布、unknown ACK 完整產品恢復及雙端驗收；其他五主線完整保留，不將 goal 縮成 v2 adapter。最後集中執行 fixtures／故障恢復／跨平台測試。

## 2026-10-11：新local原素材的 fixed prepare identity 契約與模型

- 上輪為progress（mapped rebase／receipt-fence及跨端compile）。唯讀核backend asset.service prepare每次randomUUID、schema additionalProperties=false無requestId、route/controller無upload-ticket renew。complete冪等不能恢復未知prepare，因此未對v1試塞參數／自動POST或猜checksum dedup；新增BACKEND_ASSET_PREPARATION_V2_SPEC交付需求，尚非已實作服務，需另由backend agent接。
- 新PreparedAssetUploadRequest綁local actor／client／workspace、目的binding及current saved review引用，Ready source／kind／scope／safe duration驗證；request UUID與canonical metadata UTF-8 body／SHA-256 digest固定，body排除local ID／owner／review／path。LocalAssetUploadPreparation只保留known remote identity，wrong request／digest／owner／metadata或換ID拒絕；Pending→Ready只保留同identity，不当Ready／ACL／Node授權。namespace碰撞保留已知receipt但舊mapping規則拒graph投影，不另mint請求。
- 三項JVM測試與Desktop compile通過；canonical digest golden vector另以Ruby JSON.generate＋Digest::SHA256獨立核對並加入最終斷言，log /tmp/boarderless-pinned-assets.OHtTZ8/prepared-asset-upload-model-final.log。QA SOP及Local-first spec補前置流程。本輪不重打包Desktop、無真HTTP或backend/env變更／提交／push。
- durable reservation／v2 capability adapter／lease及bounded upload／映射發布與產品入口仍待接，backend prepare v2為此路徑依賴；其他主線仍可進行，完整六goal保留active，不縮成只交付模型／文件。

## 2026-10-11：mapped rebase 保存／產品確認與 canonical receipt-fence

- 上輪為progress（四input canonical計畫、MIME／thumbnail gate）。新增applyMaterializedSyncRebaseProposal＋awaited adapter，AppliedLocalSyncRebase捕捉default-empty mappings，local投影operation／真original-commit checkpoint／canonical continuation同generation保存；Undo／Redo保持local ID，歷史commit及後續transaction保持source ID。驗讀／封存確認captured mappings仍屬同document。
- Dialog rebase預覽／確認已接fresh原receipt＋review head及private原檔verify，有固定狀態／cancel且不用20s通用timeout。unknown ACK recovery核已發布archive exact及原receipt後confirm原generation，不另transfer或mint交易；forged目標／history漂移拒絕。stageSyncOutgoing以canonical-target對應取代blanket mapping拒絕；prepare仍驗所有server references與exact saved descriptors，metadata漂移在draft／outgoing前停止，新local original仍MediaMaterialization gap。
- 新fixture驗mapped rebase unknown ACK→reopen同generation／canonical後續交易／local Undo→save→Redo→save及later retry拒絕；mapped prepare→original receipt→send→settle保留local原圖／縮圖ID和canonical baseline，fence保留完整local intent／mapping與原request。publication proofs／server均受控fixture，不當live byte／ACL／雙端證明；原檔hash流程仍沿先前真local byte回歸。首次Kotlin equality推導錯誤已以typed mappings修正，沒有保留失敗結果作成功證據。
- 最後focused回歸7s（materialized-receipt-fence-final.log；之後補descriptor gate由完整回歸核）。最終shared JVM1160項（6skip、0failure／error）、Desktop52項（0skip／failure／error）；JS／Wasm／iOS arm64編譯、Android APK及Desktop包通過1m30s（/tmp/boarderless-pinned-assets.OHtTZ8/materialized-rebase-cross-final.log）。核包內新MaterializedSyncRebase API，temporary APP /tmp/boarderless-pinned-assets.OHtTZ8/build/desktopApp/compose/binaries/main/app/BoarderLess.app，未啟動／回填project verified／改user prefs，git diff check通過。
- QA SOP與Local-first spec補產品驗收。local→server新原素材傳輸、live雙端／GUI／VoiceOver及其他五主線仍未完整交付；goal保留完整六條active。未改backend/env、提交/push或代發真AI。

## 2026-10-11：mapped rebase 的四輸入 canonical 比較與 MIME gate 修正

- 上輪為progress（素材確認套用產品接線）。LocalSyncRebaseContext與Stopped archive改用captured mapping核canonical target／local operation exact對應；rebase intent baseline與later local按source ID比較，commit／latest仍原server snapshot，不把latest冒充歷史提交。新增fixture驗無假identity衝突、later local altText保留、peer前後新增保留；雙方真正altText衝突仍必須explicit Local／Remote選擇。
- prepareLocalSyncUpload修正image/png與image的錯誤直接比較，primary及thumbnail驗exact ID／Ready／same workspace／media kind；缺thumbnail reference加入素材gap。新增Ready PNG＋thumbnail可prepare而不send，Pending／wrong MIME／wrong workspace／wrong ID在outgoing／adapter draft前拒絕且snapshot不變。沒有猜local→server upload協定。
- focused rebase／upload／transfer回歸及Desktop compile通過7s（materialized-rebase-plan-final.log；之後補exact ID gate由完整回歸驗）。最後shared JVM1157項（6skip、0failure／error）、Desktop52項（0skip／failure／error）成功59s，log /tmp/boarderless-pinned-assets.OHtTZ8/materialized-rebase-plan-jvm-full.log；git diff check通過。本輪未重打包Desktop，不宣稱上一輪APP已含這些新增修正。
- mapped stageSyncOutgoing限制仍在：投影rebase publication／UI／完整canonical receipt路徑未接齊，不能提前開upload而遺失本機引用。QA SOP與Local-first spec補工程規則；四端／live雙端與其他五主線未完成，完整goal active。未改backend/env／提交／push、未發真AI。

## 2026-10-11：素材預覽／確認套用與 unknown ACK 產品接線

- 上輪為progress（原子保存、canonical比較及完整JVM回歸）。LocalWorkspaceSyncDialog預覽改用local投影，保留原prepared operation ID，確認fresh verifyMaterializedApplyOrRecovery後由WorkspaceScreen執行local history／原子保存；不再套用原remote ID而被existing-assets gate拒絕。mapping變更會使舊preview失效。固定verifying提示／cancel job，materialized驗證不使用通用20s timeout；缺檔不自動download。
- unknown ACK recovery必須stored snapshot exact＝原review＋同操作applied結果，只有confirm原generation，不重新transfer／auth或另一publication；非同結果仍需fresh原檔／授權驗證。新增回歸實際驗recovery不增加auth次數及later local edit拒絕。
- candidate／inspection／reservation／transfer新增thumbnail獨立引用，所有共享引用kind必須一致且thumbnail只能Image，destination去重含既有thumbnail；不拿original替代縮圖。新增thumbnail inspect＋save zero-download及wrong-video-kind拒絕case。
- 最後focused45項及Desktop compile通過20s（materialized-apply-ui-final.log）；完整shared JVM1155項（6skip、0failure／error）、Desktop52項（0skip／failure／error），JS／Wasm／iOS arm64編譯、Android APK及Desktop createDistributable成功1m31s，log /tmp/boarderless-pinned-assets.OHtTZ8/materialized-apply-ui-cross.log。暫時APP包仍在/tmp/boarderless-pinned-assets.OHtTZ8/build/desktopApp/compose/binaries/main/app/BoarderLess.app，核包內verifyMaterializedApplyOrRecovery；沒有project verified副本／啟動APP／真服務呼叫。
- QA SOP與Local-first spec補產品步驟；Compose實際操作／visual／VoiceOver、mapped upload／canonical rebase、真雙端與其他五主線未完成，六主線goal保持active。未改backend/env／提交／push或發真AI。

## 2026-10-11：素材本機套用與 canonical 審閱的原子保存

- 上輪為progress（pre-apply真原檔驗證）。新增applyMaterializedSyncProposal及awaited adapter：exact frozen proof／operation／local snapshot gate，把workspace、captured mapping、applied digest及本機receipt同generation保存；原proposal／remote receipt保持canonical ID，local operation供Undo保留local ID。新增AppliedLocalSyncProposal.materializations預設empty，無映射舊記錄維持原判定；驗讀與superseded重新核projection exact並要求mapping仍在document中。
- unknown ACK explicit retry確認同操作／review／checkpoint／mapping／實際workspace，confirm原generation；本機歷史進展後拒絕舊retry。canonicalLocalWorkspace只投影實際使用的local destination，comparison／pending／merge不產生假upload差異，proposal.localDigest仍綁raw local CAS。read-only settle保存canonical baseline；stageSyncOutgoing對captured mapping仍明確拒絕，避免未接canonical rebase時進入錯誤四input流程。
- 新2cases涵蓋unknown confirm→reopen同generation、local Node引用、原review與checkpoint不改、comparison Unchanged／settle無pending、恢復Undo／保存／Redo／保存、舊retry拒絕、superseded mapping保留，以及forged target／later local edit零覆寫。首次compile錯WorkspaceMemberRole namespace已修正；最後focused46項及Desktop compile通過20s（materialized-publication-archive-final.log）。完整JVM／Desktop回歸成功43s（/tmp/boarderless-pinned-assets.OHtTZ8/materialized-publication-jvm-full.log），計數另由XML核對。
- XML核完整shared JVM1154項（6skip、0failure／error），Desktop52項（0skip／failure／error）；不是四端或live服務驗收。產品確認按鈕、mapped upload／canonical rebase與跨端GUI仍未接，不宣稱完整素材交付；QA SOP及Backend Local-first spec補工程流程。未改backend/env、未發真AI或提交／push；六主線goal保持active。

## 2026-10-11：套用前 materialized target 真原檔驗證

- 上輪為progress（可逆identity projection與13項素材回歸）。核現存持久化要求operation.after＝canonical proposal.target，不能直接替換引用後放寬檢查。新增verifyMaterializedTarget：current未apply proposal、無outgoing／rebase，frozen local review與fresh remote head；foreign original／thumbnail都有mapping，remote兩次authorize檢exact descriptor，再核本機original size／SHA-256，才產生ephemeral localTarget。
- 原檔verify抽為共享verifyMaterializedLocalOriginal，materializeRemoteAsset仍沿用同驗證且要求private local gateway；新的pre-apply不prepare／upload／confirm或download remote，不改catalog／canvas。既有local references檢Ready／owner／workspace／kind；不把mapping／Ready marker當byte proof，不持久化授權結果。
- 新增2個fixture cases：尚未下載拒絕且零prepare／remote download，下載後可產生exact reversible target但document完全不變；corrupt private bytes、末端撤權、metadata改變、scope取消及head漂移拒絕。最終15／15 JVM（0failure／error／skip）與Desktop compile通過20s，log /tmp/boarderless-pinned-assets.OHtTZ8/verified-materialized-target-final.log；git diff check通過。尚未產品接線／正式publication、comparison／wire與Undo雙重表示／跨端GUI，六主線維持active。未修改backend/env或發真AI。

## 2026-10-11：素材引用的可逆 ID 邊界

- 上輪為progress（新版Desktop package及67項測試）。目前local apply仍限定既有asset ID，直接替換遠端ID會污染canonical review／wire delta；新增MaterializedAssetProjection作明確邊界的identity-only轉換，不改原review或receipt。按binding／local user-client-workspace驗scope，source及local一對一，兩namespace不許交集；錯namespace、重複、foreign scope及kind拒絕。
- primary與thumbnail各自映射，未映射引用保持原字串，未知thumbnail不刪掉或拿original代替；mapping不當Ready或授權證明。toLocal→toCanonical可exact回復，其他物件／properties／versions不改。尚未接apply／comparison／wire，現有素材gate不解除，下一步需verified原檔與同一transaction的canonical／local歷史表示一起接，不可僅放寬asset ID檢查。
- focused首次3項通過19s；最終projection3＋review actions3＋transfer5＋mapping2共13項及Desktop compile通過，log：/tmp/boarderless-pinned-assets.OHtTZ8/materialized-projection-final.log。只為工程identity邊界，不是live跨端交付；六主線goal維持active，未修改backend／env／提交／push或代發AI。

## 2026-10-11：Desktop 認證診斷交付包核對

- 上輪為progress（401／403可見狀態、指引與8項回歸）。本輪確認project verified成品不存在，遵循本機清理規範，不回填舊路徑。隔離createDistributable成功22s，AI診斷15項及Desktop52項皆0failure／error／skip；APP bundle位於/tmp/boarderless-pinned-assets.OHtTZ8/build/desktopApp/compose/binaries/main/app/BoarderLess.app，核包內shared jar確實含authenticationGuidance函式，不只核源碼。
- 未啟動APP／讀key／代發AI或更新使用者偏好，安全log最新仍10/9 Completed。本次401根因與新版GUI人工驗收未完成；AI_UNDERSTANDING_ACCEPTANCE列明退出舊版／開啟暫時驗收包步驟。素材映射引用與projection、正式協作、跨裝置方案、背景及完整圖形主線仍保留，goal active。

## 2026-10-11：AI 401／403 可見診斷指引

- 使用者新紀錄為Preparing→CheckingWorkspace→Connecting→HTTP401 Other→HttpRejected；認證拒絕已確認，但沒有preparedAuth／challenge，不推定金鑰、Router或上游哪層有誤。本機安全log目前最新仍為10/9 HTTP200／Completed，不能拿舊成功排除此新失敗。未讀credentials、未代送或自動重送AI請求。
- 理解驗證與工具視窗共用狀態標籤新增「認證被拒絕／存取被拒絕，尚未收到模型輸出」。401按prepared None／Basic challenge／未知拒絕分流安全操作指引，403明確不證明key有效性；僅terminal HttpRejected分類，不把workspace403或HTTP response中間狀態誤當AI認證終態。Basic僅提示檢查代理入口，不新增Basic支援或索取帳密。
- focused AiProbeDiagnosticsTest含guidance及UI label／hint斷言8／8通過（0failure／error／skip），Desktop compile通過，最終log為/tmp/boarderless-pinned-assets.OHtTZ8/ai-auth-guidance-final.log（7s）。真Router根因與GUI未驗收，六主線維持active。

## 2026-10-11：遠端原素材檢查／保存／下載的分開產品授權入口

- 上輪為progress（bounded原檔下載、prefix恢復、fresh auth與原檔hash）。本機同步視窗新增LocalSyncMaterializationPanel，target中的foreign MediaNode原檔按asset ID去重／每頁20；先explicit唯讀檢查資訊，再確認保存identity，另按審阅下載／確認下載或cache revalidate。保存及inspect均重新核對remote head／metadata／local review，unknown保存ACK保留同一prepared UUID。取消確認不下載，下載有固定狀態／bytes進度／cancel job，取消／錯誤清除誤導進度；沒有Node自動套用／新上傳／自動retry，metadata只為原素材，external GIPHY不搬到local catalog。
- 從repository取得同baseURL的owned syncAssetGatewayFactory，不用default endpoint fallback；downloadServerScope比對binding／repository，異scope在transfer前拒絕。remote每action success／failure／cancel close，本機borrowed gateway不close。coordinator也檢查download scope，避免自訂endpoint与default gateway誤配。
- 修正既有local apply素材前置檢查，把WorkspaceAsset MIME與MediaNode.kind作mediaKindForAssetMediaType分類，而非直接比較image/png與image字串；錯kind仍拒絕。新增functional helper回歸：metadata inspection零寫入／零download、mapping unknown ACK同generation、scope／head／metadata／unreviewed source拒絕、PNG classified Image／wrong Video拒絕，以及gateway scope與close lifecycle。
- JVM13／13、JS／Wasm各19／19及Desktop compile通過（/tmp/boarderless-pinned-assets.OHtTZ8/materialization-ui-cross.log，35s）。iOS17／17（無failure／error／skip）及Android APK建置通過（materialization-ui-native.log，2m40s）；獨立QA device EE950796-8D24-44BD-BA45-3667F9F32805確認Shutdown後已刪除，inventory確認不存在，不動既有device。Compose點擊／status／分頁／取消／safe area／VoiceOver與live服務尚未人工簽核，fixtures不代簽GUI。QA SOP加入7步產品入口流程，BACKEND_LOCAL_FIRST_SYNC_SPEC補路由／批准邊界。安全AI log仍最新10/9 HTTP200／Completed，無新failure／paid AI calls。
- 六主線仍保留：materialized原檔雖可準備，Node引用／comparison／wire projection及明確套用尚缺，原素材apply/upload gate不解除；local→server素材、真雙端／capability／authenticated reconnect／完整圖形與快速方案簿及人工QA仍未完成。未修改backend／env／提交／push，goal active。

## 2026-10-11：保存映射的 bounded 原檔下載／本機 verification 協調

- 上輪為progress（immutable映射durable保存／跨重開）。新增materializeRemoteAsset，僅接受現存mapping＋current saved review；同server配置先refresh exact remote head／role、每次source authorize及完整descriptor核對，才prepare原local ID。沒有新UUID／abandon／canvas POST／Node或checkpoint修改，tickets／headers不保存。
- 分塊橋接既有local upload：transport batch與consumer buffer各至多1MiB、single queued64KiB piece，不全影片ByteArray；producer自己驗完整長度／EOF／多餘bytes／SHA-256，local sink不讀全original則立即拒絕／取消producer，不deadlock。local confirm後還會重讀實際private original計hash，末端再查remote head／role、source授權及exact local review；Ready cache重用仍走授權／hash，不另download遠端。正確已發布cache遇末端撤權／head變更或取消可保留，但不交付Ready結果或自動套用Node。
- Browser original新增resume cursor，只有同token／size的unsealed／未completionRequested original可接續；missing只可新建從未使用ID，abandon tombstone／sealed不可復活。重播來源核對既有prefix bytes／chunk boundaries再append，不reset或overwrite。source consumer固定chunk邊界獨立於HTTP callback；因此不猜Range協定，但仍完整重下載來源。
- 首批JVM10／10、JS／Wasm各16／16及Desktop compile通過（transfer-cross.log，35s）；新增末端撤權／head變更後，final JVM11／11、JS／Wasm各17／17、iOS13／13通過，無failure／error／skip，Android APK／Desktop compile通過（/tmp/boarderless-pinned-assets.OHtTZ8/transfer-final.log，2m57s）。含真Desktop／iOS PNG原檔保存／重開cachereuse及fresh auth／再hash，真IndexedDB first1MiB完成後中斷／重開／拒改前綴／改HTTP chunk大小重播追加、original tombstone／sealed拒resume、既有local gateway回歸。Fixture服務授權／bytes非live backend；沒有真paid AI請求。iOS獨立QA Transfer device C96E57A4-518C-473F-9C10-25A9C323369D測完確認Shutdown再刪除，不動既有device。QA SOP及BACKEND_LOCAL_FIRST_SYNC_SPEC已補檢查邊界、private cache保留及非產品入口界線，git diff --check通過。
- 安全AI log仍最新10/9 HTTP200／Completed，沒有新實際failure。六主線仍保留：產品逐次下載授權modal／狀態／取消／resume入口、引用／comparison／wire projection与明確套用、local→server素材傳輸及正式雙端／能力／GUI仍未完成；現有素材gap gate不解除，goal active；未修改backend／env／提交／push。

## 2026-10-11：遠端原素材→本機資產 immutable 映射的 durable 保存

- 上輪為progress（四平台Pinned local prepare／未知ACK／Ready不降級）。新增MaterializationSourceDescriptor／LocalAssetMaterialization、LocalWorkspaceSyncSnapshot.materializations及store.stageSyncMaterialization，在同一workspace document CAS／durable barrier保存完整source fingerprint、目的local UUID／createdAt、origin review及兩端scope／actors。只存identity reservation，沒有Ready旗標、URL／headers／tickets或bytes；原creator可不同於授權reader。
- 新reservation必須saved review仍current、checkpoint／local before exact，target有對應MediaNode且mediaKind吻合；scope／actor／review／metadata錯或過期拒絕。每來源一份immutable映射，destination在全document集合唯一且不沿用已在local Node引用的ID，最多4096筆。新local ID不同remote ID。相同unknown ACK重試confirm現存generation、不另生ID；後續local edits後原record確認保留編輯，不拿保存內容當fresh服務權限。
- 保存不改canvas／workspace version／checkpoint／proposal／outgoing，也不初始化asset catalog或下载；重開後才可按原mapping preparePinned。private audit envelope增加optional materializations（舊檔default空），仍不是import／replay／新授權。沒有放寬既有素材apply／upload gap gate。
- JVM13／13、JS／Wasm各11／11、iOS8／8通過，無failure／error／skip；Desktop compile、Android APK建置通過。logs /tmp/boarderless-pinned-assets.OHtTZ8/mapping-jvm.log（12s）、mapping-browser.log（30s）、mapping-native.log（2m51s）。包括fake durable unknown ACK、真Desktop document重開、真IndexedDB重開後按原mapping prepare同一Pending、iOS真document重開再upload／confirm原檔而畫布仍未套用；server asset／ACL資料為fixture，不代真服務。iOS新建隔離BoarderLess QA Mapping 2026-10-11，UUID FE8A2AB4-7E9B-4B76-91AF-5C8E47A3AADE，測完確認Shutdown再刪除，不動既有device。git diff --check通過，QA SOP及BACKEND_LOCAL_FIRST_SYNC_SPEC更新。
- 安全AI log最新仍10/9 HTTP200／Completed，没有新failure。六主線仍保留；下一步每次fresh服務授權／metadata比對、bounded streaming下載落地／驗原檔與原identity恢復協調，產品明確審閱後的引用／comparison／wire projection整合及local→server素材materialization仍缺，不能把此pure mapping核心當完整同步。未修改backend／env／代送真AI／提交／push，goal active。

## 2026-10-11：素材落地可恢復的固定本機身分基礎

- 上輪為progress（完整本機同步封存匯出）。回到素材主線：現有verified下載／原檔保存可沿用，但local prepare每次新UUID無法安全恢復source→destination。新增四平台PinnedLocalAssetGateway.preparePinned；一般選檔仍新UUID並沿用同一底層metadata保存，不自行下載遠端素材或更改Node。
- 呼叫者必須先durable保存生成的local UUID與完整映射，再以exact Pending descriptor準備。驗local owner／workspace／edit role、UUID、canonical支援媒體、200MiB上限／SHA-256、dimensions／duration、不含remote thumbnail／rejection；metadata不存在才新增，完全相符Pending或Ready只confirm原generation，Ready不降級、異值不覆寫。Ready marker仍非此次重新驗bytes的證明。Desktop單asset atomic metadata，Android／iOS同步catalog及Web awaited catalog都有exact retry分支。
- JVM7／7、JS／Wasm各12／12通過，無failure／error／skip，Desktop compile、Android APK及新增Device test編譯通過（Android尚未執行）。logs /tmp/boarderless-pinned-assets.OHtTZ8/jvm.log（20s）與cross.log（3m32s）。第一輪初始化腳本因/tmp清理而不存在，已建立新的隔離build目錄，不重用缺失handle；首輪validator test發現通用source驗證只檢checksum非空，Pinned新增嚴格SHA-256再重跑通過。包含Desktop真原檔及Browser真IndexedDB／original store重開、Pending→upload→Ready→same descriptor重試，不新增asset；unknown catalog ACKfixture確認同generation。
- iOS獨立臨時QA simulator測試5／5通過，無failure／error／skip，含Pinned validator／unknown catalog ACK2及真原檔IosLocalAsset3（ios.log，1m29s）。使用明確DEVELOPER_DIR及新建BoarderLess QA Pinned Assets 2026-10-11，UUID 3FFC9E58-7D37-49BD-B79D-147AA91F604F，測完已確認Shutdown並刪除此獨立fixture device；不動既有simulator。AI安全log再次只見10/9 HTTP200／Completed，沒有新的實際failure，不代送AI或讀credentials。QA SOP新增工程素材身分驗收，BACKEND_LOCAL_FIRST_SYNC_SPEC第3節補APP基礎／未接入口界線；git diff --check通過。
- 尚缺durable source→destination mapping、auth每次下載、bounded streaming落地／原檔verify、原identity恢復協調、明確審閱後Node asset引用替換，以及local→server materialization／formal完整能力；既有素材gap gate不解除，不把Pinned API冒充完整素材同步。其餘六主線／真雙端／GUI及正式服務驗收仍保留，goal active；未修改backend／env／提交／push。

## 2026-10-10：私人同步記錄完整本機封存匯出

- 上輪為progress（未prepared outgoing之applied重新審閱及封存Undo）。安全AI log再查仍最新10/9 HTTP200／Completed，沒有新的實際失敗，不代送真AI。六主線仍全部保留。
- 本機同步視窗新增「審閱同步記錄匯出→確認匯出私人同步記錄JSON」；用既有Desktop／Android／iOS新檔目的地與Web Blob下載runtime，不寫固定資料夾或覆蓋檔案。選目的地後重讀同owner／workspace snapshot且完全等於審閱值；漂移／scope切換／取消不寫。原生picker不受20秒服務timeout；資源清理沿用共用 awaited dispose。匯出不連服務、不查receipt、不send、不fence、不改generation或清封存。
- 新boarderless.local-sync-evidence.v1含current workspace／checkpoint／proposal／applied／outgoing／stopped／rebaseProposal／rebases／superseded，保留原domain operation／prepared wire IDs／clientSeq／digest及choices。使用本機structured map key格式、UTF-8／Unicode／深度及16MiB上限，不截斷封存；不含APP credential設定或素材原檔。私人畫布自行輸入的機密仍完整包含，非去識別化report。
- JVM13／13、Desktop新檔目的地2／2、JS／Wasm各20／20通過，無failure／error／skip，Desktop compile通過；logs /tmp/boarderless-sync-evidence.log（40s）與 /tmp/boarderless-sync-evidence-browser.log（45s）。首輪新test誤用不存在RenameWorkspaceOperation而compile失敗，修正後測出structured map keys設定缺漏，補與本機相同設定後全部指定回歸通過。Browser actual IndexedDB重開後匯出實際Blob，核完整rebases／superseded／畫布及零mutation；不代表真browser磁碟保存。git diff --check通過。QA SOP第15步及BACKEND_LOCAL_FIRST_SYNC_SPEC第5節更新。
- 此v1僅private audit副本，不是raw server receipt／committed log完整備份、不是fresh proof、沒有直接import／replay／submission；封存匯出基礎已提供，但清理／上限解除須另定可靠備份與明確批准，不能以DownloadRequested自動刪資料。native picker／provider／GUI、全素材materialization、正式雙端／reconnect及其他主線仍未交付，goal active；未修改backend／env／提交／push。

## 2026-10-10：未準備 outgoing 的已套用方案重新審閱

- 上輪為progress（atomic rebase apply／歷史基準／Undo跨重啟）。修正下一次remote或local變更後，舊applied未settled令重新審閱永久停用的卡點；無outgoing／rebase待套用時可重新載入服務並審閱。已準備outgoing仍禁止取代，必須先走原receipt／fence／commit恢復，不替換wire IDs。
- 新SupersededLocalSyncProposal保留舊proposal／applied／當時checkpoint，包括已核對commit證據；新方案保存與舊封存同CAS／durable barrier，canvas／checkpoint不變。32筆上限拒絕追加，不丟舊記錄；exact unknown ACK重試不重複封存。重開時若current digest仍符舊apply，從封存恢復Undo；後續內容不同不使用過期inverse。原rebase封存亦完整保留。
- 三方保存入口新增stageLocalSyncReview，確認時fresh head／role／binding完全符合審閱且server pending／draft為空，scope有效才保存。保存不apply、不prepare、不send、不fence；新target需另行明確套用與上傳授權。歷史remote只有讀回審閱，不提供保存前繞過fresh服務檢查。
- 首批JVM21／21及Desktop編譯通過（/tmp/boarderless-sync-rereview.log，42s）。最終JVM22／22、JS／Wasm各26／26通過，無failure／error／skip，Desktop compile檢查通過（/tmp/boarderless-sync-rereview-cross.log，30s）。新增coordinator fresh head／Viewer／closed scope／server draft／local outgoing拒絕及零send回歸、Browser實際IndexedDB再次審閱後重開／舊證據／Undo驗證；首輪cross run誤跑完整JVM且新fixture使用空CreateObjects而失敗，改為有效fixture並對每個test task指定filters後重新驗證全部指定cases。QA SOP第14步加入此流程；git diff --check通過。封存匯出、全素材materialization、正式雙端／reconnect／GUI與其他六主線尚未交付，goal active，未修改backend／env／代送真AI／提交／push。

## 2026-10-10：審閱後 rebase 的本機原子套用與歷史基準結算

- 上輪為 progress：安全log證實10/8的401有送Bearer，但較新10/9紀錄已HTTP200／Completed；沒有新失敗紀錄，不憑舊401修改認證或代送付費AI。六主線範圍不縮減。
- 已保存兩階段rebase新增獨立審閱／明確確認本機套用入口。確認時重新核對原receipt／actual commit／最新head／role／scope及本機before；使用完整LocalWorkspaceContentOperation，durable保存成功後才發布history／session／selection。此步不送canvas交易，剩餘變更的準備與上傳仍須另行授權。
- workspace、actual歷史commit checkpoint、後續普通同步proposal／applied、原intent／wire身份／兩份choices封存與本機operation receipt在同一CAS／durable barrier更新。不將最新head冒充原commit，不丟peer additions；封存32筆上限拒絕追加而非清掉證據。未materialize的遠端素材仍拒絕套用。
- unknown保存ACK重試核exact原operation與post-state，只confirm原generation；私有history publication input可重播approved before→after，不把已保存畫布退回before。重開恢復Undo／Redo；後續local edit拒絕再次套用旧確認。原始server回應完整匯出、封存匯出、再次remote漂移後的continuation重新審閱、全素材materialization與真人雙端仍待完成。
- JVM20／20相關測試及Desktop編譯已通過（/tmp/boarderless-sync-rebase-apply.log，42s），涵蓋rebase5／coordinator6／merge9。JS／Wasm各24／24通過，無failure／error／skip，含實際IndexedDB4項，新增套用後關閉重建、歷史基準／原身份／Undo／Redo驗證（/tmp/boarderless-sync-rebase-apply-browser.log，1m12s，另含Desktop compile檢查）。首次編譯發現新測試誤用HistoryResult.result，已改為succeeded，修正後完整指定測試通過；git diff --check通過。QA SOP第12／13步分清保存、套用與另行上傳。未修改backend／env／提交／push，沒有live server／GUI簽核，goal active。

## 2026-10-10：原 commit 後的兩階段意圖／最新 head 審閱與 durable 方案

- 上輪為 progress（驗證actual歷史commit snapshot）。本輪新增LocalSyncRebaseContext／Plan，分開保存原submitted target／applied operation、後續local內容、actual歷史commit、最新remote head。第一階段以原applied local結果為基準，將後續本機意圖與actual commit比较，避免把未見過的peer additions當local deletion；第二階段以actual commit為基準，比較已審阅本機意圖與最新remote。各階段entity衝突要明確選擇，沿用完整graph／sequence／lock驗證，不猜執行順序或丟棄未知資料。
- verifyAndSettle遇到已提交而head／內容不同時，帶 verified context 到兩階段產品視窗，不直接丟generic error或覆寫畫布。重用三方preview／完整屬性／每筆選擇／二次確認；首階段只是準備下階段，最後確認重查原receipt／歷史commit／latest head／role再保存exact frozen LocalSyncRebaseProposal。unknown保存重試保留同一 prepared confirmation ID，不新配方案ID。
- stageSyncRebaseProposal 與本機文件同CAS／durable barrier保存 localBefore／checkpoint digest／原intent及outgoing／actual commit／最新head／兩份choices／target；重算兩階段target以拒絕偽造。local／head／scope改變拒絕新保存，相同unknown ACK只confirm原generation。後續local edits保留保存方案但rebaseIsCurrent=false；該flag不當fresh ACL。重開可審阅歷史保存方案，真正再保存需新的server驗證。
- 目前只保存完整審阅方案，原workspace／checkpoint／original outgoing都不變；尚未atomic apply、接受historical baseline或準備替代上傳。已保存rebase intent不容普通committed settle／fence清掉。下一步須明確套用與history／baseline settlement，仍保留原提交證據和後續local意圖，不能用ordinary merge直接把unseen peer objects刪除。
- JVM18／18、JS／Wasm各22／22相關測試通過，無failure／error／skip，Desktop compileKotlin通過，含rebase3／coordinator6／既有merge9及Browser actual IndexedDB4；logs /tmp/boarderless-sync-rebase.log（31s）與 /tmp/boarderless-sync-rebase-browser.log（58s）。新IDB case驗保存／關閉重建／讀回兩階段方案，原canvas／checkpoint／request不變；不是production grant或真人UI。QA SOP新增12項中的兩階段／取消／unknown ACK／過期／保存非套用門檻。六主線goal active，未修改backend／env／代送真AI／提交／push；不是live backend或真人雙端驗收。

## 2026-10-10：原 commit 邊界的完整歷史畫布重建

- 上輪為 progress（fence恢復／本機意圖封存）。核對後端 operation service：baseVersion 只拒絕超前，不要求等於當前 head；不衝突的其他 transaction 可先提交。因此不能將 original target 直接冒充 actual server commit snapshot，亦不能只重播自己的transaction漏掉前面的他人變更。
- verifyPreparedSubmission 新增可選保存的 baseline session，驗 user／client／workspace／baseVersion與原 snapshot版本。完整原receipt／log核對後，從baseline.lastServerSeq收集到receipt.toServerSeq的連續歷史，最多8000records／8pages／每頁1000；遇缺頁、seq gap、倒退head、超界拒絕。停止點精確為原transaction尾端，較新head records不算該commit基準。
- recoveryProjection 僅允許現有legacy完整可往返的domain snapshot，使用正式mapper還原DTO並驗domain round-trip，不近似shape／vector／geometry／style／sequence等未支援欄位。沿用正式committed projection reducer，包含他人在原transaction前的提交；另再次核對window內originaltransaction payload／完整range／identity。title尚無versioned operation，fresh title不等於baseline時拒絕，不造歷史title。
- 返回 PreparedSubmissionVerification.committedWorkspace 為原commit時畫布，current仍是最新state／ACL，兩者分開。verifyAndSettle傳入保存baseline，未因此放寬較新head的結算；下一步仍需明確四方／意圖差異審閱及durable新方案，不能將後續remote變更直接overwrite或隱含local deletion。
- JVM14／14、JS／Wasm各14／14相關測試通過，無failure／error／skip，Desktop compileKotlin通過，含歷史2、Mock receipt recovery1（新增commit後別人刪node，而歷史仍保留node）、coordinator6、既有reducer5；logs /tmp/boarderless-sync-history.log（41s）及 /tmp/boarderless-sync-history-browser.log（59s）。不是actual IndexedDB新方案／真人UI驗收。未聲稱live backend／較新head恢復UI已完成；原六主線goal active，未修改backend／env／代送真AI／提交／push。

## 2026-10-10：明確 fence 與保留本機意圖的恢復入口

- 上輪為 progress（準備／查收據／二次確認上傳 UI）。本輪同步視窗新增獨立「審閱停止原請求→確認 fence」；需明確批准不可逆 fence，不是普通取消或 server Undo。先查原 receipt；Committed 改核對結算，Unknown 才以 exact pending identity／fresh edit ACL fence，之後重新驗原 receipt，Fenced 才可封存。commit 贏得競爭不當作取消，Unknown仍保留 pending，無新 canvas send／IDs。
- 停止 adapter 原 pending 並保留其原 wire archive；adapter draft 僅在完整 operations 等於原 outgoing transaction 時移除 active slot，其他草稿不動。本機 archiveFencedSyncSubmission 同一文件 CAS／durable barrier 保存完整 proposal／applied operation／outgoing prepared identity及 observed head，清 active proposal/applied/outgoing，畫布／原 checkpoint 不變。上限32筆，滿額拒絕而非丟棄舊證據；archive export／完整原始 server fence evidence 仍待補。
- unknown archive ACK 重開精確重試只 confirm 原 generation，不再 fence；後續本機編輯保留。當內容 digest 仍匹配時，重開可從最後停止方案恢復原合併 Undo；新審閱可建立新 proposal，原 archive 保留。統一 public sync snapshot factory 防止回傳漏掉 archive／outgoing 狀態。
- 重連比對入口亦容許「wire已stop／active draft尚在」的精確舊草稿，讀回完整 operations 比對後才開恢復入口，不能把其他 pending/draft 當自己的。該入口只有 read，清理及 fence 均仍經二次確認。
- 新 coordinator 回歸含 fence後畫布／基準／Undo／新審閱、commit-vs-fence競爭、unknown本機封存ACK／保留後續編輯。JVM27／27、JS／Wasm各30／30相關測試通過，無 failure／error／skip，Desktop compileKotlin通過；logs /tmp/boarderless-sync-fence.log（31s）及 /tmp/boarderless-sync-fence-browser.log（59s）。Browser actual IndexedDB outgoing回歸分別跑Committed結算與Fenced封存，關閉重建後驗原identity／畫布／基準及Undo。coordinator fence競爭是受控fixture，正式receipt另有MockEngine回歸，沒有live fence／真人GUI／雙端／authenticated reconnect／完整素材與全部backend capabilities驗收。較新remote head的commit後合併、原始evidence／archive export與其餘六主線仍未完成，goal active；未修改 backend／env／代送真AI／提交／push。

## 2026-10-10：同步準備／收據／二次確認上傳的產品入口

- 上輪為 progress（原收據驗證／committed settlement）。新增 LocalSyncUpload coordinator，LocalWorkspaceSyncDialog 接入「準備並保存（不送出）」、「查原收據並結算」、「審閱上傳→二次確認原請求」。準備核對 fresh remote exact snapshot／edit role／binding、完整 transaction capabilities 與現有 server media Ready，先保存 domain outgoing，再保留 server draft、prepare wire、原子綁身分。缺少 title／sequence／geometry／vector／textPlacement／external media／素材 materialization 等能力顯示閉合 gap tokens，不省略欄位或偷傳部分畫布。
- 明確批准送出仍先 verify 原 receipt：Committed 走結算而非重送；Fenced 拒絕；Unknown 必須 remote baseline 仍完全相同、edit permission 有效、local outgoing 未變，再 sendPreparedSubmission 原 request。回應未知保留 pending，不自動產生新 IDs／retry。Unknown／Fenced 在 UI 固定訊息區顯示，不假裝成功。
- 若 server pending 已保存但 local binding 中斷，preparedSubmission 接回相同身分；後續 local edits 保留，這條恢復不準備新請求。重連 UI 允許查該 exact pending／draft，不再因自己的 pending 永久封鎖收據入口；其他未確認交易仍拒絕配對。ACK confirmation 若讀到不同 remote head，不以較舊驗證結算。
- coordinator 回歸涵蓋純準備零send、精確重複、原 receipt→單一send→結算→已提交再查零重送、Fenced／較新remote／closed scope拒絕，以及 wire prepare後local binding中斷＋新local edits恢復。JVM14／14、JS／Wasm各17／17相關測試通過，無 failure／error／skip；Desktop compileKotlin通過，logs /tmp/boarderless-sync-upload-ui.log（31s）及 /tmp/boarderless-sync-upload-browser.log（58s）。新 coordinator server 是受控 fixture，wire/receipt 路徑另有既有 MockEngine 回歸；不是 live 後端交付。QA SOP 已加入新版產品驗收步驟與零canvas POST／二次批准／原收據／重開／遮擋人工門檻。尚無真人GUI、live server、authenticated reconnect／全素材／完整backend能力與雙端實測，六主線goal active。未修改 backend／env／代送真AI／提交／push。

## 2026-10-10：pending 清理後的原收據驗證與 committed 基準結算

- 上輪為 progress（wire prepare／本機 identity binding）。新增 verifyPreparedSubmission：用 exact domain operation＋保存 IDs／clientSeq／baseVersion 重建 canonical DTO，核 SHA-256；mapper/schema 漂移、payload／scope／client 不符在 HTTP 前拒絕。只查原 transaction receipts、完整 committed log，再讀 fresh state／ACL；不重送 canvas、不寫 recovery preferences／sequence／ACK，可處理 adapter 已清 pending 而本機尚未結算的 crash window。
- 原 log 驗證沿用正式 receipt／ack validator，核 actor／client／transaction／每個 operation ID／seq／kind／baseVersion／workspaceVersion／payload／完整 range／committedAt。Unknown 不冒充完成，變更 payload 拒絕。PreparedSubmissionVerification 是 transient verified read，不是保存證據或重送授權。
- 新 settleCommittedSyncSubmission 只接受綁定同一 prepared identity 的 Committed，fresh remote head 必須仍等於該 commit version／seq且內容等於原 target；remote 已有後續編輯時拒絕，保留 pending，尚待新三方審閱流程。它在同一文件 CAS／durable barrier 保存 checkpoint＋已核對 commit metadata／原 outgoing，清 local outgoing；此間新本機編輯保留 pending，合併 Undo 仍由既有 current digest gate 恢復。
- committed 結算保存的為原提交內容／身分及 committed metadata，不聲稱已保存原始 server receipt／log 全文；原始 log 必須重新查回驗證。adapter 仍有 pending 時亦需其正式 confirmPendingReceipt，不能只清 local outbox 假裝已解除 adapter pending。
- 新回歸覆蓋 pending ACK 已清後查原收據、摘要 mismatch 零HTTP、committed payload mismatch／Unknown、preferences 零修改，及本機 unknown settlement ACK／同 generation retry／保留後續編輯／拒絕較新 remote head。JVM23／23、JS／Wasm各26／26相關測試，無 failure／error／skip；Desktop compileKotlin通過。Web actual IndexedDB 綁定→commit結算→關閉重建→讀回commit／baseline→Undo通過，非GUI或production origin grant。logs /tmp/boarderless-prepared-recovery.log（42s）與 /tmp/boarderless-prepared-recovery-browser.log（1m）。初次新收據fixture lookup參數順序錯誤被validator拒絕，修為具名參數後重跑通過，未放寬validator。完整產品 worker／原 fence outcome／較新 remote head 的合併／原始 evidence 保存與雙端實測尚未交付，六主線 goal active。未修改 backend／env、未代送真AI、未提交／push。

## 2026-10-10：兩階段 wire 準備與本機 outgoing 身分綁定

- 上輪為 progress（完整 transaction compiler／durable domain outbox）。本輪 BackendWorkspaceRepository 新增 prepareSubmission／preparedSubmission：在同一 submission mutex 下核對 saved draft 的完整 head operation／base workspace／version、client 與 edit role，配置 wire transaction／operation IDs／clientSeq，再由既有 draftPersistence 保存 exact request，不發 HTTP。相同 pending 只確認原請求；其他內容／client／fence 狀態拒絕，沒有重新配置 IDs。
- PreparedWorkspaceSubmission 僅含 route fingerprint、user／client／workspace、domain operation ID、wire transaction／operation IDs、client sequences／baseVersion、canonical DTO JSON SHA-256；不保存 endpoint／headers／credentials。摘要不是 raw transport bytes。sendPreparedSubmission 明確比對保存身分、要求 edit role／無 fence、確認原 pending 後送同一 request，沿用完整 ACK 驗證與既有 acknowledge，不自動 retry／fallback。
- local outgoing 新增 optional prepared；bindSyncOutgoingSubmission 在同一文件 CAS／durable barrier 綁定到 exact domain transaction。核對 binding fingerprint／user／workspace／client、local operation ID、base version；unknown ACK 重試僅 confirm 原 generation，不能換 transaction／digest，保留此間新增本機編輯。尚未透過產品 worker 自動串起兩個儲存入口。
- JVM 28／28，無 failure／error／skip，Desktop compileKotlin 通過。含 prepare/reopen/exact wire／identity mismatch 零HTTP1、既有 pending recovery6／receipt12、local sync9；log /tmp/boarderless-prepared-submission.log（BUILD SUCCESSFUL 40s）。HTTP 僅 MockEngine 模擬 response loss，未呼叫真後端／AI。JS／Wasm 各31／31相關測試通過（上述28＋actual IndexedDB3），綁定 prepared identity 後關閉重建 repository、重讀及精確重試通過；log /tmp/boarderless-prepared-submission-browser.log（BUILD SUCCESSFUL 1m）。不是真實 server／production grant／GUI驗收。
- 仍待：原 wire pending 已被 adapter ACK 清除而 local settlement 未完成的 crash window 恢復、完整 receipt／fence evidence 與 baseline settlement、兩階段產品批准入口／capability及media preflight、authenticated reconnect／雙端端到端驗收。Settings 測試不聲稱 fsync；Native durable adapter／Web awaited 保存能力維持原範圍。六主線 goal active，未修改 backend／env／提交／push。

## 2026-10-10：完整畫布交易編譯與 durable 待送交易

- 上輪為 progress（下載基準結算、Undo／後續編輯及跨端重開）。本輪抽出 buildWorkspaceContentTransaction，沿用正式 domain operations，把已審閱 target 編譯成單一 transaction；舊 leaf draft compiler 委派共用路徑。支援時序 metadata／綁定物件新增、修改、刪除，刪除前暫時解除受影響 metadata、交易末重建，transaction 最终 bindings 必須有效，inverse／UndoRedo也驗證。保留 geometry／vector／文字配置／群組／外部媒體／背景既有屬性，不靜默丟失。
- 正規化比較新增 sequenceDiagramsVersion，不混入目的地 revisions。title 尚無 atomic canvas operation，明確拒絕並需後端規格，不用另一個 rename HTTP 假装與画布交易原子。完整時序／geometry 等仍受既有 backend contract capability gate，編譯成功不是允許上傳。
- 新 OutgoingLocalSyncTransaction 保存 exact domain transaction IDs／完整操作、proposal ID、遠端版本／seq／client，在本機文件同一 CAS／durable barrier 保存。驗結果必須等於審閱 target，操作深度／唯一 ID／展開數與允許 operation 型別有界。unknown confirmation 重開重試只確認原 generation；別的 transaction 不能覆寫原 outgoing。新離線編輯保留原交易且仍待同步；未收據結算不能改 proposal 或走 read-only settlement。
- 這是 canvas transaction outbox，不是已序列化 HTTP envelope：現有 backend adapter 另配置 wire operation IDs／clientSeq 並保存 request。後續須將兩者以 durable identity 連結，再透過原請求 receipts／fences 恢復；不能直接以 domain ID 查 server receipt，亦未自動呼叫 HTTP。產品上傳批准入口／worker 尚未接通。
- JVM 19／19，無 failure／error／skip，Desktop compileKotlin 通過；包含共用舊 merge8、完整交易2、local sync9。log /tmp/boarderless-sync-outgoing.log（BUILD SUCCESSFUL 41s）。JS／Wasm 各22／22相關測試通過（上述19＋actual IndexedDB3），新增 outgoing exact IDs／內容保存及重開；log /tmp/boarderless-sync-outgoing-browser.log（BUILD SUCCESSFUL 1m1s）。不是正式origin grant或雙端GUI驗收。新增 BACKEND_LOCAL_FIRST_SYNC_SPEC.md 提供待交付的 atomic title operation／wire identity mapping／素材 materialization／整合驗收要求，未自行增加後端 endpoint。
- 六主線 goal active，完整上傳、receipt/fence settlement、媒體 materialization、title／完整 backend capabilities、authenticated reconnect、雙端真人及各平台 GUI仍未交付；未修改 backend／env、未代送真 AI、未提交／push。

## 2026-10-10：下載合併的基準結算與下一次審閱

- 上輪為 progress（本機套用／重開 Undo 回歸及 UI 編譯修正）。本輪新增 settleReadOnlySyncProposal：僅對已 durable 套用、target 與審閱 remote 內容相同的方案，經同 binding 的 fresh refresh、無 pending request／draft 後結算；版本／seq／內容不同、帳號／空間不符或需要上傳的 target 都拒絕。不把讀回內容當 upload receipt。
- checkpoint 與 applied.settledCheckpoint 在同一文件 CAS／durable barrier 保存，保留套用操作與 Undo；unknown confirmation 後精確重試只確認原 generation。結算期間的新本機編輯不覆寫，仍 hasPendingContent；已結算方案可重新審閱下一次合併，保存新 proposal 才替換前一方案／套用記錄。新 proposal 的保存仍不是新一次套用或伺服器授權。
- 同步 UI 新增「重新讀取伺服器並確認下載基準」，歷史快照不能啟用；按下才 refresh，核對 adapter serverScope、remote scope 與 pending request／draft，再結算。此動作不發送伺服器畫布寫入。保存格式明確區分結算前 digest 與已結算 baseline 的校驗；初次回歸抓到舊校驗衝突，修正後重跑，不算失敗那次已通過。
- JVM 16／16，無 failure／error／skip，Desktop compileKotlin 通過。回歸含 unknown confirmation／重開、單一 generation、保留 Undo／後續編輯、拒絕 stale remote／不同帳號／seq、新一次 proposal，以及需上傳不能走 read-only settlement。log：/tmp/boarderless-sync-settlement.log（BUILD SUCCESSFUL 33s）。JS／Wasm 各10／10相關測試通過（合併8、actual IndexedDB2），結算後重開驗證 settled marker／baseline／無 pending content／UndoRedo。log：/tmp/boarderless-sync-settlement-browser.log（BUILD SUCCESSFUL 1m1s）。無真實雙裝置／production origin grant／GUI驗收。
- 尚未完成完整同步：需上傳 target 的 durable outgoing／transaction receipt/fence／未知回應恢復、遠端原檔 materialization、完整 backend capabilities、authenticated reconnect worker、雙裝置衝突及重開驗收仍待交付。此節只完成下載結算，不改原六主線成功標準；goal active，未修改 backend、未代送真 AI、未提交／push。

## 2026-10-10：已審閱方案的本機原子套用與重開 Undo

- 本輪以目前工作樹為準補齊既有 applySyncProposal／LocalWorkspaceContentOperation／UI：修正重開 history 呼叫語法並加入缺少的中英文確認、保存中後結果及 history 文案。套用僅寫本機完整文件、保存操作與 syncApplied，在同一 CAS／durable barrier 後發布畫面，不發送 HTTP，也不推進 checkpoint。
- 補強 unknown ACK 精確重試：operation／proposal ID 必須相同，且目前完整 workspace 必須仍等於該 operation 的套用結果。之後有新編輯時不能以舊重試回傳套用成功；保留新編輯與原 proposal，另待 settlement。重開只在 applied digest 仍吻合時恢復合併 Undo；Undo／Redo 使用本機版本而非遠端 revisions。
- JVM 15／15，無 failure／error／skip，Desktop compileKotlin 通過。新增 awaited unknown confirmation／固定 generation／checkpoint 不前進／重開 UndoRedo／套用後新編輯拒絕舊重試，以及 Desktop 真實本機文件套用後再次關閉重開。驗證 log：/tmp/boarderless-sync-apply-verified.log。JS／Wasm 實際 Chrome IndexedDB 與共用合併測試通過，驗證 log：/tmp/boarderless-sync-apply-browser.log（BUILD SUCCESSFUL 27s）；首次隔離測試因未指定 fixture repo root 中止，補上 BOARDERLESS_TEST_REPO_ROOT 後成功，不算第一次已通過。fixture grant 不是正式 origin 真人授權或瀏覽器 GUI 驗收。
- AI 安全 log 目前可直接讀取，2026-10-09 台灣時間10:29 的 OpenAiChat／Bearer／ChatCompletions／Gpt56Luna 工具請求為 HTTP200 SSE，最後 Completed；reasoning=None、temperature=Absent。此證據改變下一步：先前提供的401不再代表此路徑最新狀態，但不能推定其他 route／Claude、草稿 Apply 或完整六主線已驗收。未讀 key、未代發新 AI 請求。
- 尚待 fresh remote ACL／scope／receipt/fence revalidation、遠端素材 materialization、baseline settlement、durable outgoing／未知回應恢復、authenticated reconnect worker 與雙裝置完整验收。全部六主線 goal 保持 active；本機套用不是伺服器同步完成，未修改 backend／提交／push。

## 2026-10-10：衝突選擇、二次確認與 durable 合併方案

- 上輪屬於 progress：同步目的地設定／三方預覽已接入產品並通過對應驗證。本輪在本機同步比對入口加入「選擇並審閱合併方案」，可選本機／伺服器、看三方與合併結果、再二次確認保存 exact frozen plan。初次配對差異與真正三方衝突必須選擇；單邊變更預先保留，仍可明確改選。
- 新 LocalWorkspaceSyncMergePlan 涵蓋 title／canvasStyle／完整 sequence metadata、全部物件型別與連線属性；以 entity 作明確 atomic 選擇單位。比對只忽略各端 entity revision，保留 geometry、floating text／font、vectorPath、媒體 reference、群組／zIndex／lock／sequence semantics。結果檢查 parent cycles／missing group、懸空 relation／自環 geometry、鎖定與完整 sequence bindings；混合出無效畫布時不能準備確認，不丟掉不支援的資料。
- 新 LocalWorkspaceSyncProposal 保存 binding、canonical SHA-256 local/checkpoint fences、remote snapshot/version/seq/role/client、explicit choices、target；它是草稿，不是已套用畫布／HTTP request／ACK。Board.syncProposal 與本機文件在同一 atomic record CAS／durable barrier 保存，Native 和 awaited Web 共用規則。
- stageSyncProposal 核對 owner／binding／checkpoint／local digest／已存 proposal ID，再重新計算 choices→target。相同 unknown ACK 重試只 confirm 舊 generation；同 ID 改 payload、過期 local fence、偽造 target 均拒絕。普通 sync ACK 不能清掉已審閱 proposal。新離線編輯保留原方案，proposalIsCurrent=false，必須重新審閱，不會將舊 target 當現有畫布。
- 重開 UI 可離線讀回保存選擇／合併預覽；歷史 remote snapshot 清楚標為歷史，不當最新 ACL／receipt。所有 choice 變更會取消上一次準備確認；保存僅進本機，尚無 local apply 或 network submit。使用者可關閉；取消／未知保存確認後重開仍從 durable record 恢復。
- JVM 35／35 無 failure／error／skip、Desktop compileKotlin 通過，含 Native 真實檔案 stage／重開／server factory 不被呼叫、unknown ACK、stale/forged target/ID、單邊變更、dangling/group/lock、完整時序與不一致選擇拒絕。log /tmp/boarderless-sync-proposal-rebuilt.log。中途一次 warm JVM compile 成功但 JAR 缺 WorkspaceSession／CanvasObject／新 Proposal，造成大批 unresolved：不算通過；隔離 shared clean＋--no-daemon 重新建置後直接查 jar names 均存在，entries=2162。沒有停止主要 APP 或删除共享 SDK／其他專案 cache。
- JS 14／14、Wasm 14／14 無 failure／error／skip，XML class count 各3；新增 actual IndexedDB 保存／重開／繼續離線編輯測試，仍使用明確 fixture persistence grant，不等於 production origin 真實授權或瀏覽器 GUI 操作。log /tmp/boarderless-sync-proposal-browser.log（BUILD SUCCESSFUL 1m55s）。
- 尚未交付：已審閱方案的 atomic local apply＋Undo／Redo＋baseline settlement、fresh remote ACL／scope／receipt/fence revalidation、素材 ID materialization／後端完整能力、durable outgoing request／未知回應恢復、authenticated online/reconnect worker 與兩台裝置衝突套用後重開驗收。完整 offline-first sync 與原六主線 goal 保持 active；本節沒有把保存 proposal 當完成同步，未提交／push。 依腳本 discover／tracked／busy 規則清除隔離建置 791.0 MiB；主要使用中 Desktop APP 保留。

## 2026-10-10：同步目的地比對入口與三方畫布預覽

- 上一輪屬於 progress：本機啟動與 durable baseline 已改動權威程式並完成驗證。本輪接到產品入口：本機「空間」新增「伺服器同步設定與比對」，Desktop／Android／iOS／Web 使用各自現有 server factory，composition／一般啟動不呼叫 factory 或 HTTP；只有按連線才開啟服務、載入／選擇空間。Desktop 保留 native server journal factory，未啟動／修改 backend。
- 可先看兩端帳號／空間 ID、三方預覽和完整屬性差異，再確認保存 binding；保存 binding 不會覆寫任一端。連線／讀取超時給出保留本機的錯誤；隨時可關閉並取消；scope／epoch 不符不發布畫面。綁定前 fresh refresh 必須仍與已審阅快照相同，且本機 durable snapshot／CAS fence 仍相同。遠端 pending draft／submission 必須先恢復，不能在未知提交上建立對應。
- BackendWorkspaceRepository 提供配置 route 的 SHA-256 syncServerScope；只保存 fingerprint，不保存 URL／token。已綁文件遇不同 route／account／space 不會改連另一個空間。此比對 UI 使用既有 API，不能算正式 auth／production WS 已交付。
- WorkspaceDraftReviewDialog 加入原基準／本機／遠端共用 viewport 的唯讀預覽；完整 leaf choice 完成後也能看 merged proposal。重用正式畫布 text／vector／relation／sequence 繪製元件；從出口量測抽出無 raster 限制的 measured paint，巨大畫布不配置巨大 bitmap。影片／原檔／GIPHY 只畫 metadata placeholder，不取得 URL、下載或播放；正式 export 仍強制需要全部 verified frames，沒有因此放寬匯出保證。
- 差異列表補 DocumentChange（標題、canvasStyle、sequenceDiagrams），sync review 可包括只在 remote 出現的 entity；原草稿模式保留只列本機修改的預設，不把別人的新增混進匯入提交。初次兩端內容不同的 binding 保存 requiresInitialReview，不把 remote 人為當成共同 baseline 後靜默 UploadPending；普通 checkpoint ACK 不能清除初始審核。
- JVM 89／89 無 failure／error／skip，Desktop compileKotlin 通過；含三方 viewport／巨大坐標、初始內容差異不能靜默 ack、完整差異、既有 merge／draft 與實際 PNG／PDF export renderer 回歸。log /tmp/boarderless-sync-preview-final.log。另 ImageComposeScene 真實渲染 1／1 通過（/tmp/boarderless-sync-preview-render.log），親眼檢查原檔輸出中的外側文字／物件位移／影片資訊占位；不是 OS GUI／真人雙端證據。JS 14／14、Wasm 14／14 無 failure／error／skip，實際 XML class count=3，驗證 diff／viewport／initial pairing model 且編譯新共用 UI；不是瀏覽器 UI 點擊／server 操作驗收。log /tmp/boarderless-sync-preview-browser.log（BUILD SUCCESSFUL 1m55s）。依清理腳本 discover／tracked／busy 規則移除本次隔離 build 與 preview raster 共 789.1 MiB，主要使用中 Desktop APP 未停掉／刪除。
- 下一步仍是完整產品同步：明確初始配對／衝突選擇和重新確認、atomic local apply＋baseline acceptance、durable remote request／receipt／fence／未知 ACK 恢復、authenticated reconnect worker 與全素材／完整 canvas capabilities、下載 publication 和雙端離線→重連→衝突→套用→重開驗收。現有入口只是目的地設定及唯讀比對，沒有自動上傳／合併／下載，不宣稱完整 local-first sync 完成；六主線 goal 保持 active，未提交／push。

## 2026-10-10：本機優先啟動與持久同步基準（接續實作中）

- 新使用者需求列為交付門檻：一般啟動、編輯、保存不等待伺服器；網路恢復後才同步，衝突須詢問、顯示差異與畫布預覽，確認前保留兩端。原先「Local／Server 分開保存」不能當作此需求已完成。
- App 的一般啟動固定選本機，不再因上次 Server 偏好强制連線；明確的外部 Server 空間請求／Desktop --server 仍是使用者選擇的遠端入口。偏好寫入失敗也不阻擋普通本機啟動。
- 共用本機文件 record 新增可選 syncCheckpoint，與現有畫布共用原子 CAS／durable confirmation。保存 server scope fingerprint、帳號、遠端工作區對應、上次同步 baseline／version／seq，不保存 token；目前本機完整文件是持久待同步快照，不用記憶體 queue 冒充斷電後可恢復。
- Native 與 awaited Web repository 提供 localSyncStore；不會因 binding 或 ACK 複製遠端內容覆蓋本機。binding 僅接受未變更的本機快照與對應帳號／工作區，不能自動將本機 UUID 當遠端 UUID。ACK 重試相同基準可確認，過期 expected checkpoint 不能改寫新基準；同步期間新本機編輯保留並仍標為 pending。
- 純三方比對區分未對應／一致／單邊上傳／單邊下載／雙邊衝突，保留標題、樣式、圖形、連線 geometry、時序與外部素材內容，只忽略各端 revision counters。衝突回傳 frozen WorkspaceDraftReview，不產生網路請求或寫入任何一端。
- JVM 13／13 無 failure／error／skip，含 Desktop 真實檔案保存／重開且 server factory 完全不呼叫；Desktop compileKotlin 通過。log /tmp/boarderless-local-first-native.log。Web JS 12／12、Wasm 12／12 無 failure／error／skip，包含實際 IndexedDB baseline 與離線編輯共同重開；log /tmp/boarderless-local-first-browser-verified.log。初次瀏覽器 test filter 未匹配實例，報告只有 skipped root；修正 trailing wildcard 後確認實際 XML 測試數，不用 BUILD SUCCESSFUL 代替實跑。依清理腳本 discover／tracked／busy 規則移除隔離建置 786.7 MiB，主要使用中 Desktop 成品仍保留。
- 尚未交付：目的地配對 UI、正式 authenticated reconnect worker、remote receipt／fence ACK reconciliation 與 durable outgoing request、素材 identity／跨端上傳、後端缺少的完整圖形／canvasStyle／sequence 能力、下載 publication、衝突差異＋三方畫布預覽＋明確選擇／重新確認／套用，以及斷線編輯→雙端變更→重連→衝突選擇→重開的端到端驗收。不得把本節基礎模型或自動測試算成完整自動同步。
- 已詢問第一次連線要建立獨立空間或選現有空間先比對；未答覆前不猜目的地、不連線建立／覆蓋空間。六主線 goal 保持 active。

## 2026-10-10：完成意圖與素材包收據的未知 ACK 恢復

- 修正 BrowserLocalAssetGateway.upload 過早 seal：現在僅 verifyPending，原檔保持私有；confirm 先 await durable requestSeal intent，再重驗 SHA-256、seal 並保存 Ready。manifest 的 requestedChecksum／completionRequested 與 sealed 一致性受檢查；write／abandon 不可跨已接受完成意圖。
- 原檔 state 區分 Missing／Pending／CompletionRequested／Published。若 requestSeal 交易已提交、回應卻遺失，保持 Pending 原始 bytes，重開從 durable intent 重验／发布同 ID，再確認 catalog。普通完成確認前、沒有 durable checkpoint 的失敗仍清理 private originals 與 metadata；取消後的未發布內容不會被 authorize 當成 Ready。
- 素材包 beforeComplete 收據 CAS 也可能成功後失去 ACK。AssetImportCoordinator 新增顯式 retainCheckpointOnFailure（預設false），只有 QuickSchemeBundle materialize 啟用；进入 identity hook 後不再 abandon，发 RecoveryRequired。PersistentSchemeBundleImportReceipts.recovered 先核對 stored ID、scope 及全部原始 metadata；僅 local editable repository 的 Pending 使用既有 local ticket confirm（不 prepare／upload），再檢查 Ready；遠端仍只能讀取確認，不 invent HTTP completion URL。
- 新增真实 IDB 两类回归：upload 完成但 beforeComplete 尚未開始就失败，確認原檔及catalog清除且從未可下載；completion intent ACK 遺失後重開同 ID 恢復；素材包 receipt ACK 遺失後 private Pending 原檔保留、重開從同 receipt完成，asset count保持1。完成前清理測試以真實 browser dispatcher 驗 bounded cleanup timeout，避免 runTest 虛擬時間跳過實際 IO。
- JS 45／45、Wasm 45／45、JVM 40／40，無 failure／error／skip；涵蓋 gateways／originals／Desktop真實bundle、AssetImportCoordinator／QuickSchemeBundle 及 Desktop file asset／bundle。logs /tmp/boarderless-browser-checkpoint-recovery.log（1m26s）與 /tmp/boarderless-native-checkpoint-regression.log（41s）；JS syntax／diff check 通過。
- 沿清理腳本 discover／tracked／busy 規則移除隔離 build／Karma outputs，回收814.8 MiB；主要 Desktop 使用中輸出與文件保留。本次沒有真實 production grant、OS picker、跨裝置 GUI／付費 AI／正式多人驗收，六主線 goal active，未提交／push。

## 2026-10-10：Desktop 原檔素材包到 Web 的保存／重開／再匯出

- 新回歸採用既有 Desktop 實際匯出的 BLQS0001（與 iOS fixture 完全相同，SHA-256 bfd0087c7c3f916807e989dc2c7a925f8d02cdc9f38c8795d92e9ef07fea3016），經 browser File／私人 OPFS snapshot 的 production runtime review，再走本機 gateway、strict IndexedDB receipts／catalog／originals／方案簿與文件。
- 在素材完成、方案未發布時中斷；關閉／重建 repo 及儲存入口後，durable receipt 恢復同一 destination asset ID，不再 prepare／upload。方案保存重試僅一份，sourceWorkspaceId 轉到目的本機空間。貼入後文件重讀、原檔 exact bytes、32×24 真實 Skia 解碼、Undo／Redo 與原素材 count=1 都驗過；再匯出後以原始 checksum／byteSize／bytes 驗證，沒有用 preview 代替原檔。
- 選檔／下載 dispatch 與 persistence permission 仍為明確 fixture；實際 OPFS／IDB、codec、gateway、方案及文件流程是真正執行，但不等於真實 OS picker／production origin grant／實體裝置 GUI 交換完成。Web 與 native 的整體實作／驗收剩餘事項保持原六主線範圍。
- 查到 karma-webpack 預設把成品留在專案外 _karma_webpack_數字。Karma config 改 output.path 為生成 test package 下 karma-webpack（JS／Wasm 各自獨立）；Browser test task 新增 karma.config.d inputs，避免改設定却被 UP-TO-DATE 跳過。最終實跑確認新兩端路徑都存在且測試成功。
- 清理腳本新增舊 Karma 辨識：flat known generated filenames、目前使用者 ownership、無 symlink／非成品、commons.js 同時有 BoarderLess-shared-test 與 cg.creamgod.boarderless，stream 比對不執行或輸出原碼。Gradle wrapper jar／Karma live process 也阻止刪除。12 個 disposable fixture 清理測試通過。
- JS 10／10、Wasm 10／10 無 failure／error／skip（Desktop bundle1、bundle adapter6、local gateway3）；最終 BUILD SUCCESSFUL（15s），log /tmp/boarderless-browser-desktop-bundle-verified.log，diff／JS syntax 通過。用腳本 discover／tracked／busy 規則移除105個已辨識舊 Karma 及本次隔離 build，共約5.3 GiB；重新 discover 已辨識 Karma=0。無法辨識的空目錄／其他專案不刪，主要 Desktop 使用中 build 仍保留。
- 六主線 goal active，未提交／push，未宣稱 cloud 方案簿／正式多人／真人付費 AI／完整跨端 GUI 等剩餘交付已完成。

## 2026-10-10：Web 本機／伺服器模式入口與失敗恢復 GUI

- 新增 browserWorkspaceStorageRuntime 並在 Web main 注入 App；復用既有模式 chooser、偏好與切換 gate。Local factory 提供 await 文件及原檔 gateway；mediaRuntimeFor 會要求本機 AssetDownloadGateway，缺少時拒絕而非 fallback HTTP，Server factory 沿用 backend repository。媒體活動 StateFlow 在切換時保留。
- 新增二項 runtime 整合回歸：production factory 保存／關閉／重建 UTF-8 本機文件、媒體入口及 activity 共用、缺少本機 gateway 拒絕、server factory 無請求建立／關閉；UiPreferences 模式 Local／Server／null 新實例重讀及 IndexedDB persistence denial 不降級 memory。偏好新實例重讀不是整個瀏覽器重啟驗收。
- CUA 實際載入 development Web：內建瀏覽器拒絕持久化，App 正確顯示本機文件失敗，無成功保存宣稱。補上 common WorkspaceScreen 的首次失敗面板（session==null）：直接重試連線及 guarded 切換儲存模式。實際 reload 看見兩個按鈕，點切換返回 chooser，兩種模式選項仍可見。沒有 fake production grant，也未切 Server 或啟動／修改 backend。
- GUI 截圖 /tmp/boarderless-web-mode-failure.png 及 /tmp/boarderless-web-mode-chooser.png；Chrome browser automation surface 本次不可用，改用 IAB。本機保存成功的 GUI、真實 production persisted grant／重啟／eviction／原檔交換仍待後續，不能把 denial UI 當成功存檔證據。
- 最終 JS 9／9、Wasm 9／9，均無 failure／error／skip（mode2、gateway3、originals4），Web JS／Wasm compile 及 development webpack 成功；最後 BUILD SUCCESSFUL（1m35s），log /tmp/boarderless-browser-mode-final.log。diff check 通過。關閉 QA tab 與 port9863 臨時 server 後，沿清理腳本 discover／tracked／busy 規則回收隔離 build 約1.1 GiB。
- 六主線 goal active；模式已接入 Web 入口，但完整平台／跨端／真人正式協作／AI 付費驗收仍未完成，未提交／push，保留主要 Desktop 使用中輸出及使用者文件。

## 2026-10-10：Web 本機素材 gateway、文件與預覽整合

- 新增 BrowserLocalAssetGateway（AssetTransferGateway／AssetDownloadGateway），使用 awaited metadata 與原始分塊儲存。prepare 保存 Pending；upload 分塊傳輸、驗完整 checksum 並 seal；confirm 核對同 ticket／內容且重新確認原檔後保存 Ready。owner／workspace／session、ticket URL／headers、完整 source metadata、closed lifecycle 都有檢查。
- 原檔 status 是只讀交易，區別 missing／pending／ready；已 seal 但 catalog 寫入失敗的 Pending 可驗原始 bytes 後恢復 Ready。abandon 不刪 uncertain completion 已 seal 的原始內容；沒有開始原檔的 Pending 也能清掉，並留下 ID fence。關閉／取消於異步讀取後被檢查，不回傳舊 gateway 成功結果。
- browserLocalWorkspaceRepository 現為 documents delegate 加本機 gateway，listAssets／getAsset 支援目錄及恢復，close 同時關閉 gateway／documents。browserMediaImportRuntime／rememberBrowserMediaImportRuntime 接受 optional 本機 AssetDownloadGateway，預覽／GIF／video／export 共用它；每次預覽只關閉新建 backend adapter，不關閉本機共享 gateway。Web main 的模式 UI 尚未注入，不能宣稱使用者已可切換本機。
- 真實 Chrome IndexedDB 新增三項 gateway 回歸：匯入協調器保存 CRC 正確 PNG、建立 MediaNode、關閉／重開文件與儲存後下載 exact bytes、實際 Skia 解碼 preview／export（1×1）、wrong owner／closed 拒絕；原檔已 seal 但 metadata 更新中斷後保持原檔並恢復 Ready；無原檔 Pending abandon 及 forged ticket 拒絕。測試來源由呼叫端 finally release，符合現有 runtime 的責任。
- 最終 JS 16／16、Wasm 16／16，0 failure／error／skip（gateway3、originals4、catalog4、documents5）；BUILD SUCCESSFUL（1m11s），log /tmp/boarderless-browser-asset-gateway-verified.log。JS syntax／diff check 通過；沿清理腳本 discover／tracked／busy 規則刪除隔離建置 655.5 MiB。
- Web mode UI、真實 persistence grant／瀏覽器重啟／eviction、GIF／video／大素材完整 GUI、跨端 bundle 實際操作及其他五主線總驗收仍未完成。六主線 goal active，未提交／push，主要 Desktop 使用中的輸出與文件保留。

## 2026-10-10：Web 原始檔案分塊保存與發布入口

- 新增 local-originals.js，production 獨立 BoarderLess.local-originals.v1。manifest、chunks、usedIds 同 strict IndexedDB 交易；每塊至多 1 MiB、整體延用 200 MiB 上限，僅 tx.oncomplete 回報成功。持久化權限拒絕／quota／abort／cancel 不降級 volatile storage；未 sealed 的 bytes 不可供下載。
- BrowserOriginalStore 的 JS／Wasm 橋接提供 begin、write、publish、download、abandon。publish 在 Kotlin 分塊重新讀取整個暫存並驗大小及 SHA-256，符合後才 seal；download 再驗 manifest checksum 與實際內容。全檔沒有一次轉為 ByteArray 或 Base64，橋接僅傳单塊。
- manifest 的 token、next offset 與不可變 sealed 狀態 fence 同 ID 的競爭／錯序寫入。abandon 清除暫存內容但留下 usedIds 永久 ID fence；不允許較晚的舊 publish 把同 ID 重建內容當原稿。取消於 bridge 呼叫前已生效時不啟動新交易。
- 實際 Chrome IndexedDB 新增四項原檔回歸：跨 1 MiB 分塊與重讀精確位元組、部分原檔／錯誤 checksum 不发布、put success 之後取消會回滾 offset、seal abort 後精確重試、storage denial、token／offset／競爭寫入及 abandon 後重開不能重用 ID（後數项合併於四個測試）。fixture permission 不代表真實 production origin 授權。
- 最終 JS 13／13、Wasm 13／13，無 failure／error／skip，含 originals4、catalog4、documents5；BUILD SUCCESSFUL（1m20s）。log /tmp/boarderless-browser-originals-final.log，JS 語法及 diff check 通過。沿清理腳本 discover／tracked／busy 規則移除本次隔離輸出 656.3 MiB；不刪主要 Desktop 使用中輸出與文件。
- 目前交付原始 bytes 儲存底層，尚未接入 Browser AssetTransfer／Download gateway、匯入／預覽與 Web 本機模式；跨瀏覽器實際 permission／eviction／GUI／原檔交換仍未驗收。六主線 goal active，未提交／push。

## 2026-10-10：Web 本機素材目錄的 awaited 保存

- 新增 AwaitedLocalAssetCatalog，重用既有 PersistentLocalAssetCatalog 的格式與校驗。每次操作載入私有 snapshot，真正 CAS 與 confirmDurable 完成後才回傳；取消不回報成功。相同 Pending ID／完整 metadata 在未知確認後可核對重試，不再分配另一份記錄；同 ID 改 byteSize 等內容會拒絕。
- 新增 browserLocalAssetCatalog 及 AssetCatalog scope、JS／Wasm operation／cancel 橋接；production 使用獨立 BoarderLess.local-asset-catalog.v1（4 MiB）。此階段只交付 metadata；原始 bytes 的分塊保存、checksum、完成 manifest／下載／preview 及 Web 本機模式尚待接入。ready 的呼叫契約仍要求 gateway 先驗證並持久化原始內容，不能用 metadata 測試宣稱素材匯入交付。
- Chrome 真實 strict IndexedDB 新增四項目錄回歸：Pending／Ready 重讀與固定 ID 重試、CAS 已提交但 confirm 中止後精確核對與 abandon、put success 後取消及持久化拒絕、兩個 catalog 競爭寫入不覆蓋已確認素材及損壞資料不得重設。permissions 為明確 fixture；不是實際 production origin 的持久化授權驗收。
- 最終 JS 19／19、Wasm 19／19，0 failure／error／skip；含目錄4、文件5、durable records4、方案簿6。BUILD SUCCESSFUL（1m57s）；log /tmp/boarderless-browser-local-assets.log。JS 語法與 diff check 通過。
- 使用清理腳本 discover／tracked／busy 規則刪除本次隔離建置 648.6 MiB；保留主要 Desktop 使用中輸出及使用者文件。六條主線 goal 保持 active，未提交／push。

## 2026-10-10：Web 本機文件的獨立 IndexedDB 儲存入口

- 新增 browserLocalWorkspaceRepository，使用既有 AwaitedLocalWorkspaceRepository 文件編碼、操作收據與 generation fence。文件儲存入口等待 strict transaction 完成及 confirmDurable 後才回傳；取消、拒絕持久化權限或交易中止均不回報成功，也不降級成記憶體保存。
- BrowserDurableLocalRecordStore 改用 Receipts／Library／Documents 明確 scope；JS 與 Wasm 新增文件橋接，production 資料庫 BoarderLess.local-documents.v1（4 MiB）與 bundle receipts／quick schemes 分開，取消也路由到對應 operation registry。尚未注入 Web App 的本機模式，仍需原始素材 gateway、模式 UI、真正權限／瀏覽器重啟與 eviction 驗收。
- 實際 Chrome IndexedDB 回歸新增五項：UTF-8 文件／重讀／相同 receipt／UndoRedo、put success 後取消不發布 session、權限拒絕與 abort、同 key 的收據／方案儲存不覆蓋文件、競爭寫入不丟失已確認 workspace。permission 是明確測試 fixture，並非宣稱真實 origin 已取得持久化授權。
- BUILD SUCCESSFUL（2m36s）：JS 998／998、Wasm 15／15 通過，均無 failure／error／skip；兩端各含新文件五項及既有 durable records／quick schemes 測試。驗證 log：/tmp/boarderless-browser-local-documents.log。JavaScript 語法及 diff check 通過。
- 透過 clean-build-artifacts.py 的 discover／tracked／busy 規則清理本次隔離輸出 646.6 MiB。主要 Desktop 使用中的專案輸出仍保留；不刪使用者文件。六條主線保持 active，尚未提交或 push。

## 2026-10-10：剩餘確認與連線入口接入 awaited publication

- connectNodes不再同步回nullable RelationId；四個menu／選取／connector呼叫改onConnected，dispatchOperation确认保存後才選取新edge。GIPHY插入與支持CanvasStyle的背景apply改dispatcher，成功才關閉library／background picker。ColorPickerWindow新增可選busy状態，保留hsb／token等parent state而显示保存中，期間不提供edit／apply／cancel；其他picker預設行為不變。
- RelationInsertionDialog的onConfirm改suspend，attempted plan保持固定node／edge IDs，saving禁止重複consent／apply／cancel／dismiss，只有true才close。ClipboardPasteRetryDialog也await onRetry，成功才清除同一private draft；取消不吞CancellationException，不以queued coroutine當保存成功。
- WorkspaceScreen剩餘一般內容入口不再直接使用同步execute：該方法只由native dispatcher分支呼叫。這是來源檢查，不是宣稱Web runtime已注入或完整GUI驗收；REST recovery／外部runtime callback仍需在總驗收中核對。
- 新回歸用真正backward relation insertion transaction驗ACK前原edge／node保留、失敗後固定split IDs再試，成功後actual方向與first-segment條件／label保留且新圖完整。最終28/28 pass、0failed/errors/skipped（publication13、relation insertion8、canvas style6、Desktop方案1），JVM／JS／Wasm／iOS simulator／Android共用compile成功(2m)，diff check通過。
- 下一步是Web document專用strict IDB、原檔gateway、模式UI及瀏覽器重開／permission／eviction驗收；目前未宣稱該主線完成，也未做本次新增視窗GUI／live AI／GIPHY／正式多人backend驗收。六主線goal保持active，未提交／push。
- 沿cleanup discover／tracked／busy清除独立build約95.6MiB；保留主要Desktop使用中輸出。

## 2026-10-10：gesture／文字／貼上發布入口

- Group／Text／Media／GIPHY card drag、move＋reparent transaction、兩類resize／rotation handles及relation geometry release改dispatchOperation；新物件與變形仍由same retained operation更新history。此步不宣稱滑鼠拖曳GUI先前缺口已驗過。
- dispatchOperation新增onFailed，只在captured owner及beforeHistory仍為當前scope時回呼；save waiting顯示保存中。貼上（含快速方案與v9 external refs）和自循環在onSaved才更新selection；貼上失敗保留原ClipboardPasteDraft供同operation retry。
- 新文字commit保留pendingNewNode及已輸入文字，成功才清除private draft；既有EditText也只在成功後結束editing，失敗保留input。NodeTextEditor新增enabled以禁止保存期間修改／commit，可在再次聚焦或修改後明確重試。原生delegate仍同步保存，未換文件格式或server契約。
- 新publication回歸驗commitNewNodeDraft真正UTF8文本保存失敗后同ID／內容再save；最終32/32 pass、0failed/errors/skipped（publication12、new node4、relation gesture6、geometry integration7、hit2、Desktop方案1），JVM／JS／Wasm／iOS simulator／Android共用compile成功(2m3s)，diff check通過。
- 仍有connectNodes返回值、GIPHY插入按鈕、background picker、relation insertion confirm及paste retry boolean入口待轉換；Web document IDB／原檔gateway／模式UI尚未完整接入。未做新的GUI／付費AI／live GIPHY／多人server驗收，六主線goal保持active；未提交／push。
- 沿cleanup discover／tracked／busy清除独立build約95.4MiB；保留主要Desktop使用中輸出。

## 2026-10-10：一般屬性／排序／群組操作接入 awaited dispatcher

- WorkspaceScreen新增dispatchOperation，原生沿既有同步execute，awaitsDraftPublication repository則冻结owner／完整beforeHistory／HistoryResult後launch等待synchronizeAwaited。synchronizeAwaited支援明確captured owner／history，不能用較新的畫布狀態套用舊操作；非同步queue／submit期間inputBlocked，避免再接受依舊版本建立的操作。
- 刪除、關係attributes、TextNode／Group／Media attributes、三類layer排序、選取transform、group／ungroup／fit、keyboard nudge、自動排版／分配／對齊及numeric precise transform等18個一般操作入口已轉dispatcher。只有onSaved才清除delete選取、選取新group／ungroup children；失敗保持原選取。原生無suspension的新工作流程不改原有即時同步publication。
- 新兩項回歸驗已在UI排隊的舊transform結果遇較新history不啟retention，以及真正group transaction的group／parent IDs在ACK前不得出現在文件、ACK後一次保存完整parent tree。最終37/37 pass、0failed/errors/skipped（publication11、history14、layer5、movement4、alignment2、media1），JVM／JS／Wasm／iOS simulator／Android共用compile成功(2m10s)，diff check通過。
- 連線建立／貼上／GIPHY插入／背景picker及部分card gesture回呼仍待逐一處理，Web document IDB／原檔gateway／模式UI尚未接完；未做GUI、live provider或多人backend驗收。六主線goal保持active，未提交／push。
- 沿cleanup discover／tracked／busy清除独立build約95.3MiB；保留主要Desktop使用中輸出。

## 2026-10-10：筆刷物件建立等待 durable publication

- CanvasBrushControls／BrushCanvasLayer commit改為suspend，WorkspaceScreen筆刷入口接executeAwaited。完整筆畫prepare完成後先設定pending draft，再由composition coroutine等待保存；Ready才清除預覽並由既有畫布渲染物件。避免onRelease先清points導致等待時畫面筆畫消失。
- saving期間禁止新stroke、樣式變更、retry及close；显示保存中。失敗保留同一BrushCanvasDraft與object／operation IDs及線寬／顏色，手動retry不重新prepare或配置ID。live scope啟動前再檢查，CancellationException原樣傳遞。pointerInput沒有把saving／pending加入key，保留原有避免同一按下事件被重啟成重複tap的保護。
- 新publication回歸用真正prepareBrushStroke檢ACK前文件仍舊、失敗後prepared object及style不變、再次保存同ID成功且只有一物件。最終27/27 pass、0failed/errors/skipped（publication9、brush3、vector geometry7、pen creation8），JVM／JS／Wasm／iOS simulator／Android共用編譯成功(2m10s)，diff check通過。
- 此輪未做實際GUI绘製／waiting／retry，沒有宣稱該證據已補齐；一般sync按鈕與Web document IDB／原檔gateway／模式仍待接入，跨使用者backend等其餘主線缺口維持原範圍。六主線goal保持active，未提交／push。
- 沿cleanup discover／tracked／busy清除独立build約95.1MiB；保留主Desktop正在使用的輸出。

## 2026-10-10：鋼筆／時序圖審閱套用等待保存 ACK

- SequenceCreationDialog及PenCanvasDialog的commit改為suspend，WorkspaceScreen两個authoring入口轉executeAwaited。只有confirmed publication回true才選取新物件／fit sequence並關閉審閱，失敗保留prepared operation與固定IDs。
- SequenceCreationDialog及PenEditorDialog用自身coroutine scope等待套用；saving期間禁止重複insert／apply、draft修改及dismiss／discard，顯示保存中。鋼筆冻结當次path／text／textPlacement，將saving納入cameraBusy並停止pointer authoring。確認失敗保留attempted path，既有同操作retry行為保持；CancellationException不再當普通Retry吞掉。
- 新publication回歸驗真正PenCanvasCreation與SequenceCanvasCreation操作：第一次save失敗不變文件，再次await ACK前不Ready，同一prepared operation重試後物件IDs、sequence bindings／vector內容及ID allocator數不變，重新解碼workspace等於預覽結果。
- 最終29/29 pass、0failed/errors/skipped（publication8、pen creation8、pen editing6、sequence publication7），JVM／JS／Wasm／iOS simulator／Android共用compile成功(2m21s)，diff check通過。此輪未做GUI等待／重試／dismiss的操作驗收，不把單元結果視為GUI證據。
- 一般sync畫布按鈕與brush commit、Web document專用IDB／原檔gateway／模式UI仍需接入，其他主線缺口保持原範圍。未改backend server、未送live AI、未提交／push；六主線goal保持active。
- 沿cleanup discover／tracked／busy清除独立build約95.0MiB；確認主要Desktop PID19229仍在執行，保留其使用中輸出。

## 2026-10-10：Undo／Redo 接入 awaited draft publication

- WorkspaceScreen抽出synchronizeAwaited(HistoryResult)，executeAwaited仍用于AI與素材入口，Undo／Redo現在用真實undo／redo結果等待retainDraftAwaited ACK後才更新history／submission queue及清除選取。保存失敗保留原画布與歷程stack，不再先清除選取。
- WorkspaceRepository新增awaitsDraftPublication capability，原生預設false、AwaitedLocalWorkspaceRepository true。異步歷程命令等待前筆submission及publication結束，再以最新session／history計算Undo／Redo；native仍可沿既有optimistic queue運作。submit effect在開始前等待draftPublicationBusy結束，避免新保存與舊收據確認相互交錯；等待若失去owner／connection則停止。
- 新兩項回歸驗背景Undo的ACK等待期間可見style／history不變、ACK後UndoRedo canvasStyleVersion依序1→2→3且objects不變／record重新解碼一致；失敗Undo不改visible history／redo stack。最終18/18 pass、0failed/errors/skipped（publication7、awaited local4、canvas style6、Desktop持久1），JVM／JS／Wasm／iOS simulator／Android共用compile成功(2m16s)，diff check通過。
- 一般sync按鈕、sequence／pen callback及Web模式／document IDB／原檔gateway仍未全部接上；此輪未做GUI／live provider／多人backend驗收，不能宣稱Web本機或共用背景跨使用者同步已交付。六主線goal保持active，未提交／push。
- 沿cleanup discover／tracked／busy清除独立build約94.7MiB，保留主Desktop使用中輸出。

## 2026-10-10：AI／素材插入等待 durable draft ACK 後發布

- 新awaitDraftPublication，保存前後檢查user／client／workspace／role／session checkpoint及完整WorkspaceHistory；等待retainDraftAwaited完成並ensureActive後才回Ready。role撤銷、不同文件／歷程、disabled scope拒發布，保存exception回Unconfirmed，Cancellation原樣傳遞；已保留收據不被scope拒發布刪除。
- WorkspaceScreen增加executeAwaited，AI圖形工具最終proposal apply、匯入素材與重用既有素材三個真正APP入口已接入。只有Ready更新history／submissionQueue與queued狀態；不回傳先算好的success。新增中英unconfirmed／scopeChanged訊息，避免把未確認保存說成已套用。
- draftPublicationBusy期間阻止其他同步synchronize／画布互動、workspace切換與remote catch-up。現有原生同步保存入口保持原行为；既有每回合AI授權／完整方案review不變。一般同步按鈕、UndoRedo、sequence／pen commit callbacks仍需逐一轉換，Web awaited repository尚未注入，不能把這三個入口當作完整Web發布管線。
- 新五項publication回歸驗ACK前不得Ready、保存後歷程changed不發布但receipt仍可submit、foreign owner／viewer／disabled不啟動retention、保存途中role撤銷，以及失敗／取消不成功發布。最終10/10 pass、0failed/errors/skipped（publication5、awaited local4、Desktop持久1），JVM／JS／Wasm／iOS simulator／Android共用編譯成功(2m23s)，diff check通過。
- 仍需真正Web strict IDB／permissions／重開、原檔gateway與模式UI，及實際付費provider成功／GUI終局套用等完整主線證據；此次未改backend server、未送live AI、未提交／push。六主線goal保持active。
- 沿cleanup discover／tracked／busy清除独立build約94.5MiB；主Desktop使用中輸出保留。

## 2026-10-10：Web 本機文件 awaited 持久化邊界

- Web目前APP仍使用預設repository，只有快速方案／bundle receipts具備真正strict IndexedDB；同步retainDraft不能等待IDB completion，因此沒有將新repository直接注入、也未用localStorage或記憶體ACK替代。
- 新common AwaitedLocalWorkspaceRepository使用既有PersistentLocalWorkspaceRepository文件codec／驗證／收據規則，對單一載入snapshot計算結果，再以AwaitedDurableLocalRecordStore CAS＋confirm完成後才回傳。snapshot是私有工作資料，每次operation重讀durable state，Mutex序列化同instance，跨instance由generation fence拒覆寫；未改原生文件格式或backend。
- WorkspaceRepository新增retainDraftAwaited，原生預設轉給現有同步retainDraft。新awaited repository同步retainDraft明確拒絕，避免未await就發布；close／取消不回傳未確認結果。已commit但confirm失敗需同操作明確retry，沿原有saved receipt確認generation，不再配置ID或寫第二次。Local documents Key只改成可供internal共用，SHA256與record scope保持一致。
- 新四項回歸驗CAS及confirm完成前session不得escape、unknown publication同收據retry沒有新增write／同ID改內容拒絕、UndoRedo與重新建立repo讀回UTF8、兩repo競爭只有一winner、不因cancel／closed／invalidUTF8回報新文件或reset。最終5/5 pass、0failed/errors/skipped（awaited4＋Desktop實際文件1）；JVM／JS／Wasm／iOS simulator／Android共用compile成功(2m7s)，diff check通過。
- 此次為Web離線交付必要的production保存邊界，尚未接WorkspaceScreen等待保存／Web模式選擇、document專用IDB database／原檔gateway，也未驗實際browser permissions、eviction或APP重開。不能宣稱Web本機模式已交付；六主線goal保持active，未提交／push。
- 沿cleanup discover／tracked／busy清除独立build約94.1MiB；主Desktop仍使用的輸出保留。

## 2026-10-10：GIPHY Desktop 真實 GUI 屬性／移動／重開驗收

- 自建独立bundle與user.home、memory preferences，使用測試引用qa-unresolved／文字target／連線，不操作主要Desktop文件、不輸入provider key／送AI請求。GUI保存「GIPHY GUI 已修改🙂」、鎖定使操作框消失，Undo後恢復未鎖定、Redo重現鎖定，再解鎖。
- ExternalMediaNodeCard標題改用與本機素材一致的單一awaitEachGesture流程，補Role.Button／a11y select action。真正GUI AX現在可選取標題，重開後顯示修改文字。此改動未以測試宣稱修好滑鼠拖曳：CUA drag在新舊引用卡片及既有TextNode都只有選取，沒有移動，原因仍未確定；拖曳中預覽與滑鼠放開保存驗收仍缺。
- GUI用Shift+Right兩次及Shift+Down一次移動引用，畫布物件／操作框／連線端點更新，target沒有移動。點新路徑開連線屬性，點舊路徑清除選取，沒有殘留旧hitbox。讀取独立QA BLDS0001，校驗header／payload checksum、strict UTF8，確認position(400,220)→(440,240)、reference不變、target仍(780,240)、替代文字已保存、locked=false。
- 結束並重啟owned QA APP，GUI顯示修改文字、移動後物件及連線；record SHA256重開前後相同。fixture與evidence留在独立QA資料夾，不保存key／remote media。fixture準備曾遇參數／不存在root／root權限錯誤，修正測試資料建立流程後fixture1/1與Desktop打包成功，未改APP儲存保護。
- 最終回歸11/11 pass、0failed/errors/skipped（GIPHY6、gesture4、Desktop持久1），Desktop/JVM compile與createDistributable成功(1m9s)，JS／Wasm／iOS simulator／Android共用compile成功(1m39s)，diff check通過。仍無live GIPHY／原生跨裝置／背景GIF／外部引用export或backend契約證據，六主線goal保持active。未提交／push。
- 沿cleanup discover／tracked／busy檢查刪除独立build及QA APP約538.3MiB。仅終止owned QA process，保留主Desktop正在使用的輸出，未宣稱整體0B。

## 2026-10-09：GIPHY 引用屬性與素材圖層操作

- 共用媒體屬性辨識涵蓋MediaNode與ExternalMediaNode。選取引用物件現在有替代文字編輯／保存與lock／unlock，素材混合選取可共同改鎖定；標題／狀態計數、兩物件建立關係及group selection納入引用。GIPHY屬性顯示provider ID，沒有虛構本機asset ID／觸發上傳。
- 素材圖層操作及Layers面板的canMove／move入口同時接受本機與外部引用，兩類素材使用同一排序集合。修改沿UpdateMediaNodeAttributesOperation→既有draft保存及UndoRedo，不新增第二套持久模型。仍沿原有依物件類型排序設計，沒有宣稱已統一所有type的z-order。
- MediaObjectPropertiesTest驗混合選取／排除文字node、相對排序／鎖定／UTF8替代文字、UndoRedo與兩種identity不變。DesktopExternalMediaNodeTest加入locked／zIndex重新開檔確認。最終11/11 pass、0failed/errors/skipped（properties1、media operation1、desktop持久1、layer tree3、ordering5），JVM／JS／Wasm／iOS simulator／Android共用編譯成功（3m47s），diff check通過。
- 初次Kotlin daemon引用上輪已刪inline class而失敗，fallback完成後再補Layers分支入口；最終以in-process compiler重新驗證。沒有實際GUI屬性點按／live provider／跨實體裝置驗收，外部引用export與backend契約仍未交付，六主線goal保持active；未提交／push。
- 沿清理腳本discover／tracked／busy檢查刪除本輪独立build約93.5MiB；既有Desktop正在使用的專案輸出仍保留。

## 2026-10-09：GIPHY 背景回應防護與拖曳連線同步

- 背景／工作區切換／連線失敗停止可見引用解析；user、client、workspace、key、activity epoch共同界定scope，回應與still解碼完成時再檢查scope。快速背景再回前景，即使UI未觀察中間狀態，舊epoch回應仍不能發布；只清除暫存metadata，不修改持久node。
- ExternalMediaNodeCard移動改用畫布共用dragPreviews，不再只有卡片私有位移。連線render／hit test、共用媒體操作框與GIPHY可見集合使用同一位移；取消清除預覽，放開後走TransformObjectsOperation，重新檢查可寫／locked／version。
- 新回歸驗外部引用拖曳後連線點擊位置更新、移出視野無引用、取消恢復原位置；17/17 pass（GIPHY6、geometry7、selection4），JVM／JS／Wasm／iOS simulator／Android共用編譯成功（1m23s）。前次lifecycle驗證21/21 pass（1m7s）。此次第一輪isolated JVM測試編譯classpath缺main，明確將新main jar加入test compiler libraries／friendPaths後重跑成功，沒有把失敗算通過。
- 沒有實際GUI拖曳、live GIPHY／付費AI或跨實體装置驗收；backend外部引用契約、PNG/PDF外部引用輸出與其餘主線仍待交付，六主線goal保持active。未提交／push。
- 沿清理腳本discover／tracked／busy檢查移除此次独立build約110.6MiB；既有Desktop仍使用的專案輸出未刪，未宣稱整體0B。

## 2026-10-09：可見 GIPHY 引用批次解析

- 新GiphyVisibleReferences，以單一workspace UI scope保存當前可見provider identities的Pending／Ready／Missing／Failed狀態；同ID的多物件只解析一次，每批最多100 ID。只保留仍可見的metadata，離開視野丟棄URL；沒有設定／檔案或素材cache保存。
- WorkspaceScreen按user／workspace／本次GIPHY key重建resolver與client；可見集合以真正旋轉後corners及resize/rotation preview計算，初始0×0 viewport不送請求。150ms合併可見集合變更，Compose effect取消舊回合，resolver generation再拒絕遲到回應，不能覆寫新集合。
- ExternalMediaNodeCard不再每個物件自建GiphyClient；改讀batch resolution並只在可見scope直接載入still／播放GIF。缺失內容保留畫布node，顯示中英missing提示；Ready也可手動重新讀取。resolver只重试被明確指定的引用，失敗／限流停止剩餘批次，不加自動retry或timer。key／workspace變更會重建card scope與清除frame。
- 新GiphyVisibleReferencesTest驗101不同IDs的HTTP MockEngine請求100＋1、重複引用／集合不變不花第二次budget、URL離開可見集合即移除、集合改變後非取消的遲到回應也不能commit、missing不自動恢復／手動retry指定ID、限流只呼叫第一批且其餘Failed、取消不改成Unavailable，以及viewport與preview transforms／去重。
- 第一輪編譯缺Compose runtime.key import而失敗；等待terminal後修正，最終20/20 pass、0failed/errors/skipped（新batch4＋GiphyClient15＋原生方案交換1），Desktop/JVM、JS、Wasm、iOS simulator與Android共用編譯BUILD SUCCESSFUL（58s）。未呼叫live GIPHY，沒有將MockEngine視為provider／GUI驗收。
- 仍需真正GUI驗可見／背景／移動／重試與多物件播放，跨端原生v9方案交換、live key／provider內容移除、backend支援與PNG／PDF外部引用輸出仍未交付；六主線goal保持active。
- 沿cleanup discover／tracked／busy檢查清除本輪独立build約110.2MiB，既有Desktop使用中輸出保留，未宣稱整體0B。未改backend server、未提交／push。

## 2026-10-09：GIPHY 選取、方案與素材包引用交換

- ClipboardPayload新增可選externalMedia與ClipboardExternalMedia，version9才可包含引用。版本1..8既有內容仍可讀；空externalMedia不寫入JSON，舊版格式不自動升級。跨type共同檢查總數／ID唯一／transform／parent／relation，引用沿用嚴格provider＋ID型別，altText上限8192。
- workspaceClipboardPayload不再拒絕ExternalMediaNode，保留群組包含、世界位置／旋轉／大小／locked／相對圖層與連線。prepareClipboardInsertion／clipboardMaterialization将外部引用一起重配新畫布ID、父群組與端點，provider ID保持不變；新增圖層加法溢位拒絕，避免Long.MAX_VALUE貼上產生錯誤圖層。
- 快速方案decode／PersistentQuickSchemeStore支援v9；JSON transfer、BLQS0001素材包保留引用定義，referencedAssets仍只收真正MediaNode原檔，純引用方案不需要sourceWorkspaceId／asset download／upload。QuickSchemePreview納入引用的bounds、連線位置與矩形占位，不載入GIPHY媒體；objectCount包含引用。
- pastePayload在套用前檢supportsExternalMedia；SchemeBundleDialog對不支援workspace阻擋review同意與import，并在任何materialize／原檔upload前再次確認。審閱中英顯示GIPHY引用數及只交換ID、顯示需key的說明。此版本仍不支援含引用的PNG／PDF輸出，不會默默漏掉。
- 新DesktopExternalMediaSchemeTest使用實際本機workspace／scheme library／file bundle，保存群組、GIPHY與text及一條關係→JSON轉移→bundle export/review→另一份repository materialize、library重開→插入並再次文件重開。目的畫布IDs／parent／relation端點重配、引用同ID，原檔authorize次數0、materialize沒有任何asset receipt回合、listAssets空，Undo清空整組／Redo恢復三物件；另驗v8含引用拒絕、錯parent與圖層溢位拒絕。
- 最終交換／bundle／payload聚焦28/28 pass、0failed/skipped，Desktop/JVM、JS、Wasm、iOS simulator、Android共用編譯BUILD SUCCESSFUL（1m33s）。另跑SequenceClipboard6＋TextPlacementAndPenAlignment3，9/9 pass、0failed/errors/skipped，BUILD SUCCESSFUL（18s）。future-version拒絕測試从9更新為10，沒有取消未來版本拒絕。
- 尚未操作clipboard／scheme GUI，未驗相同v9素材包在實體Android/iOS/Web匯入與GIPHY live渲染；仍需可見引用批次解析、原生／Desktop GUI及backend外部引用契約。未改backend server、未提交／push，六主線goal保持active。
- 驗收後沿cleanup discover／tracked／busy檢查刪除本輪独立build約109.6MiB，既有Desktop使用中輸出保留，沒有宣稱整體build為0B。

## 2026-10-09：GIPHY 本機画布引用物件與保存

- 新 ExternalMediaNode，保存provider＋ID與可讀altText，沒有Workspace asset ID／media URL／API key／local path。共用transform／version／parent helpers與MediaNodeAttributes歷程支援該物件；本機repository明確supportsExternalMedia=true，其他repository預設false。客戶端BackendWorkspaceRepository serializer明確拒绝該类型，未更動backend server契約或啟動服務。
- GIPHY Browser在可寫本機模式提供中英「插入GIPHY引用」，以一般CreateObjectsOperation→retainDraft→submission queue保存，不上传或复制GIPHY原檔。本次輸入的GIPHY key只留在當前APP／repository的remember狀態；mode切換／APP關閉後不持久保留。
- 新ExternalMediaNodeCard按viewport可見性重新Get GIFs by ID，still／animation使用原API網址與既有各端直接loader，圖片與網址僅在可見scope記憶體。缺key／移除／失敗有提示或手動重試；still decoder失敗不遮掉仍可使用的GIF播放。原始Powered By GIPHY標誌與內容一起等比例縮放，不改圖；文字header可選取拖曳，與既有通用resize／rotation／connection handles接合。
- connectableNodes／拖曳連線目標、LayerTree、AI結構上下文／review預覽、draft merge納入新type；AI仍不傳pixels或media URLs。apply前再檢repository capability，避免其他入口將provider物件排入不支援的Server保存。
- 暫時明確阻擋含ExternalMediaNode的clipboard／quick scheme capture，不能把它默默漏掉；PNG／PDF原檔export也顯示專用未支援提示。下一步需完成portable selection與方案引用交換、批次解析以及真正GUI／live GIPHY驗收，不能以此宣稱素材主線完整交付。
- DesktopExternalMediaNodeTest使用真實DesktopFileWorkspaceRepository，保存建立／移動／35度旋轉／屬性修改與UndoRedo，重開完整workspace與provider identity相同，listAssets保持空，重開projection不含URL或key。第一次測試錯把Desktop二進位record當UTF8讀取而失敗，修正為核對真正重開workspace的序列化projection；最終42/42 pass、0failed/errors/skipped（新本機1、GIPHY15、MediaNode1、History14、draft merge8、LayerTree3）。
- 最終Desktop/JVM、JS、Wasm、iOS simulator、Android共用編譯BUILD SUCCESSFUL（58s）。這是程式編譯／真實本機檔案與mock provider回歸，沒有宣稱新增node GUI、跨端原生重開或live provider操作通過。
- 驗收後沿cleanup discover／tracked／busy清除本輪独立build約109.8MiB；先前僅Jvm compile曾復用configuration cache，最終驗證明確--no-configuration-cache，沒有刪除既有Desktop仍在使用的專案build。未提交／push，六主線goal仍active。

## 2026-10-09：GIPHY 可持久引用與重新解析

- 新 domain/media/ExternalMediaReference，明確序列化provider=giphy＋opaque ID；不含API key、媒體網址、檔案路徑或bytes。ID為1..128個ASCII字母數字／底線／連字號，拒絕URL、path與控制文字。這是後續畫布／portable selection使用的引用層，尚未新增ExternalMediaNode或宣稱可插入畫布。
- GiphyItem.durableReference及GiphyClient.resolveReferences接到現有「重新讀取預覽」操作；每次直接Get GIFs by ID，不回用舊metadata。resolve在花budget前拒絕URL／path／過長ID，browse遇不合法provider ID拒絕整份response而非过滤／改排序。
- GiphyClientTest新增持久JSON往返→第一次拿新媒體URL→第二次provider省略該ID仍為missing的流程驗收，保留既有搜尋locale、order、rate limit、取消與batch測試。最終15/15 pass、0failed/errors/skipped。Desktop/JVM、JS、Wasm、iOS simulator、Android共用編譯 BUILD SUCCESSFUL（1m）。使用MockEngine，沒有用實際GIPHY key或宣稱live provider驗收。
- 第一輪因清除獨立build後增量編譯仍引用不存在BoarderLessColors.class而失敗；等待該程序terminal後，以--rerun-tasks -Pkotlin.incremental=false重新全部驗證，通過結果來自第二輪，不把第一輪fallback視為成功。
- 官方要求仍是client直接API與media loads，標準整合不可自行cache／proxy／改寫或持久保存media URLs／原檔：[GIPHY API要求](https://developers.giphy.com/docs/api/)。後續還需接畫布物件、selection／quick schemes／UndoRedo與各端渲染，不能以目前typed reference取代完整GIPHY主線交付。
- 驗收後沿cleanup discover／tracked／busy檢查刪除本輪獨立build約109.3MiB；既有Desktop使用中的build保留。未改backend、未提交／push，goal繼續active。

## 2026-10-09：Desktop 素材包交付 iOS 原生保存

- 先實際執行 DesktopSchemeBundleTest.originalBundleTravelsToAnotherNativeRepositoryAndReceiptsSurviveRestart，透過既有明確 QA-only export hook 匯出965-byte BLQS0001素材包，含真正 ImageIO 產生32×24 PNG原檔及「原檔🙂」方案。Desktop測試1/1 pass、0failed/errors/skipped，BUILD SUCCESSFUL（23s）。
- 新 IosDesktopSchemeBundleTest 保存同一實際匯出位元組作固定驗收樣本（SHA-256 bfd0087c7c3f916807e989dc2c7a925f8d02cdc9f38c8795d92e9ef07fea3016），並核對固定樣本與匯出檔逐位元一致。iOS27 simulator 用真正 IosSchemeBundleSource private snapshot review，刪除選取來源後仍可匯入。
- IosFileWorkspaceRepository／IosFileAssetGateway materialize 在原檔Ready後、方案保存前刻意中斷；重開repository與PersistentSchemeBundleImportReceipts後只恢復原先記錄的目的asset ID，不重傳或建立第二份asset。目的ID與Desktop來源ID不同，payload與sourceWorkspaceId改成目的文件。
- 真正 IosFileQuickSchemeStore 保存並重開方案，saveMaterializedBundle重試保持同一方案ID；prepareClipboardInsertion＋retainDraft將MediaNode保存到文件，重開文件仍引用同一目的asset ID。原生download比對PNG全bytes，IosAssetPreviewLoader實際解碼32×24。iOS test XML確認1/1 pass、0failed/errors/skipped，BUILD SUCCESSFUL（1m58s）。
- 此證據是Desktop產出的實際格式交付到iOS原生檔案／方案／畫布流程，沒有mock asset gateway；尚未涵蓋實體裝置、UIDocumentPicker GUI、Android／Web同一素材包匯入、GIF／video或雲端方案同步，六主線仍未完整交付。
- 驗收後確認無使用本輪exact build的程序，再沿cleanup discover／tracked／busy檢查清除獨立輸出約167.9MiB、移除暫存.blscheme與本輪產生Python cache，將本輪simulator恢復Shutdown。不清除既有Desktop程序使用中的build。未改backend、未提交／push，goal保持active。

## 2026-10-09：行動端快捷入口綁定儲存模式

- RecentWorkspace 新增可選 storageMode；行動端 RecentWorkspaceStore 以 local/server 不同設定鍵保存、改名、刪除與 reconcile。相同 workspace ID 不再互相覆蓋或被另一模式清單清除；Desktop 舊入口仍用原設定鍵。
- Android dynamic shortcut 的 ID 與 Intent 新增模式；iOS Home Screen quick action userInfo 新增模式。行動端舊的未帶模式快捷入口視為 Server（原本入口只有 Server）。未知模式或不合法 workspace ID 被拒絕，pending request 以 ID＋mode 一起 compareAndSet，避免同 ID 的舊請求清除另一模式新請求。
- App 冷啟動先保存快捷入口指定模式，再建立對應 repository；偏好寫失敗停在 chooser。WorkspaceScreen 初次 openOrCreate 只讀符合當前模式的 request。執行中收到另一模式的快捷入口，使用與手動切換相同的保存／編輯／復原／拖曳／匯入／筆刷 gate；可以切換才保存偏好並重建畫布，不能切換則提示先完成編輯與保存，不將 ID 交給錯誤 repository。
- RecentWorkspacesTest 6/6 pass、0failed/skipped，含相同 ID 兩種模式重開／改名／reconcile 隔離與同 ID 跨模式 request CAS。Desktop、Android debug Kotlin、iOS simulator、JS、Wasm 編譯全部 BUILD SUCCESSFUL（3m6s）。主畫面快捷操作的實際冷／熱啟動 GUI 尚未驗收，不能用這些測試代替。
- build-and-clean wrapper 在建置成功後因既有 Desktop hot-reload PID19229 使用專案 build 而清理退出1（不是編譯失敗）。另沿同一 cleanup discover／tracked／busy 檢查，僅刪除本輪 exact /tmp/boarderless-ai-reasoning-build 約119.7MiB。既有 Desktop 使用中的輸出仍保留，未終止該程序，也未宣稱整體0B。未改 backend、未提交／push，六主線 goal 繼續 active。

## 2026-10-09：iOS 本機模式 GUI 與程序重啟驗收

- 使用獨立 QA bundle `org.boarderless.qa.mode`，iPhone 18 Pro／iOS 27.0 simulator（1241AD9A-8A40-4B06-A313-002C357CE81D）。Debug xcodebuild BUILD SUCCEEDED；安裝後清理建置輸出約735.1MiB。所有 GUI 透過 Device Hub／cua_repl 操作，未使用 simctl launch 或其他 UI 注入。
- APP 初次進入顯示繁體中文「選擇畫布儲存方式」；選「本機畫布（可離線）」後實際進入文件。使用「第一個想法」及裝置鍵盤輸入「我」，按完成；私有 local-documents record generation=2 包含該節點，偏好 ui.workspace.storageMode=local。
- 以 simctl terminate 終止確切 QA 程序，再從 App 資料庫圖示重新開啟。AX 顯示「想法：我」和「本機檔案 · 已開啟本機文件：BoarderLess」；畫面截圖確認網格、繁體中文節點與本機狀態實際顯示。再次進「選單 → 空間 → 切換儲存模式」顯示 chooser，重選本機後同節點仍在。兩次重開後 record SHA-256 保持 922ccc365e28361da273c41e3c2b48477f05b19dfce0f51be792913d6f2cdb0e，generation 仍為2。
- Android debug APK BUILD SUCCESSFUL（1m10s），已安裝於獨立 read-only emulator-5560，隨後移除並關閉；CUA 無法綁定 SDK Qt emulator app，因此 Android GUI 尚未通過。Android 建置輸出清理約149.3MiB。
- 本輪證據限 iOS 本機模式入口、單一文字節點落盤、程序終止重啟與回到本機模式；未驗正式多人／Server 模式連線、原生 picker、GIF/video、大檔、跨裝置 bundle、OS 斷電或快捷入口與模式配對。六主線仍未完成，goal 保持 active。
- 驗收後已 uninstall 確切 QA bundle 並將本輪 simulator 恢復 Shutdown。最後 cleanup --apply 偵測到既有 Desktop hot-reload APP PID19229 仍使用專案 build，安全拒絕刪除約134.0MiB；沒有結束非本輪建立的程序，也未宣稱最終 dry-run 0B。該 APP 結束後仍需執行清理腳本。

## 2026-10-09：行動端本機／伺服器 APP 模式入口

- 新WorkspaceStorageRuntime與WorkspaceStorageMode，Android MainActivity／iOS MainViewController實際注入native local file factory及media runtime factory，伺服器仍使用既有BackendWorkspaceRepository。未選過模式時先顯示中英文選擇；明確選擇才建立repository並保存偏好，未知token不擅自選Server，偏好寫失敗保留chooser提示重試。不搬移本機或server資料。
- App以repository為Compose key，切換模式重新建立畫布／任務／播放狀態，避免舊remember資料混入新scope；每個media runtime有自己的preview cache。空間面板新增切換入口，編輯／拖曳／待保存／復原／匯入中阻擋切換。Spaces入口在server連不上／無session時仍可開，無未結變更时可以返回chooser；有待復原journal時需先處理該狀態。
- withWorkspaceAssetDownloadGateway集中選擇：已有platform gateway必須支援download並直接借用，不由单次preview/export/GIF/video回合close；local-only缺gateway或僅upload能力明確拒絕，不走HTTP。Server回合使用既有BackendAssetTransferGateway並finally close。Android四種load closure及IosMediaPicker.runtimeFor均改用此入口，GIPHY仍是線上的直接顯示預覽。
- 新WorkspaceMediaRoutingTest驗借用同一gateway、decoder失敗也不close，以及缺少／僅upload的local gateway拒絕且不呼叫download callback；與common local document、WorkspaceChangePolicy回歸共JVM9/9 pass、0failed/skipped。JS／Wasm／iOS simulator／Android APK及Desktop入口編譯 BUILD SUCCESSFUL（1m49s）。最終之後只整理縮排與移除未使用import，無行為變更。
- 尚未操作新的mobile mode chooser GUI、正式APP cold restart、GIF/video播放或跨模式原檔交換，不能用此前gateway原生測試取代APP驗收。快捷入口的workspace ID與模式對應仍需檢查，沒有宣稱完整六主線達成。
- build-and-clean.sh清除約253.2MiB、dry-run0B，不保留APK／APP或build；未修改／啟動backend、未提交／push，goal繼續active。

## 2026-10-09：iOS 本機原檔 gateway 與預覽重開

- 新增 IosFileAssetGateway，接 IosFileWorkspaceRepository.localAssetGateway、listAssets/getAsset，沿用已完成共用catalog。原檔以1MiB上限分段POSIX write/read，核對大小／SHA-256，force original及directory後才發布Ready；Pending已發布原檔可重開同IDrecover。metadata／Workspace只保存ID與屬性，不保存picker path／temporary download URL。
- canonical root、私有0700目錄／0600原檔、owner/type驗證、NOFOLLOW、process Mutex及cross-process flock保護原檔變更；讀取descriptor再fstat核對size／permissions，處理EINTR與部分write。取消／失敗清除自己的part；Ready確認失敗不刪既有已發布原檔，重試以同catalog identity確認。
- 新IosLocalAssetTest真實IOS file source snapshot→AssetImportCoordinator→本機gateway→MediaNode保存→刪原選檔來源→新repository重開→原生download sink全bytes一致及真正IosAssetPreviewLoader解碼1×1 PNG。另驗upload後Ready前重開Pending並同ID恢復、截斷source清理Pending/part、原檔改bytes後拒download、path traversal／錯client／跨workspace拒絕。
- 最終iOS原生聚焦5/5 pass、0failed/skipped（新素材2＋本機畫布1＋共用unknown publication／UTF-8兩項），iOS arm64真機目標共用編譯通過，BUILD SUCCESSFUL（1m31s）。這是booted iOS27 simulator的原生檔案與預覽，不是實體iPhone／原生pickerGUI／GIF/video／跨APP process kill或OS電源中斷驗收。
- 本輪未改Android、Desktop原檔格式或backend；兩行動端已有原檔gateway，但MainActivity/MainViewController仍待明確本機／後端模式入口及preview/GIF/video runtime選擇，不宣稱已能在完整APP使用本機模式。跨端完整bundle及其餘六主線仍未完成。
- 此次boot的iPhone18 Pro simulator已恢復Shutdown，沒有其他Booted simulator；build-and-clean.sh清除約159.4MiB，dry-run0B，未保留APP／build或test XML、未提交／push，goal繼續active。

## 2026-10-09：Android 本機原檔與可恢復素材 catalog

- 新增共用 PersistentLocalAssetCatalog，以單一4MiB原子record保存最多4096份原檔metadata；包含workspace/owner/type/size/checksum/dimensions/status/createdAt。catalog嚴格UTF-8／JSON／ID／status驗證，Pending→Ready可重試確認同一generation；列表不依掃描原檔檔名或picker路徑。iOSgateway尚未接入，Desktop既有gateway未改格式。
- 新AndroidFileAssetGateway已接AndroidFileWorkspaceRepository.localAssetGateway、listAssets/getAsset，提供prepare／bounded1MiB分段upload／size與SHA-256核對／force original+directory→Ready metadata／同IDrecover、list、authorize及stream download。API24相容java.io＋android.system.Os，原檔0600／目錄0700、canonical path／owner／NOFOLLOW／process Mutex與cross-process writer lock，取消清理自己part，保存結果不明保留可重開original，不silent reupload或allocate replacement ID。
- AssetDownloadCoordinator與真正AndroidFileAssetDownloadSink驗32×24 PNG，刪除原picker-source fixture後新repository仍有MediaNode、完整asset與原檔；download byte-for-byte相同、BitmapFactory真解碼。另在upload完成、Ready前重開，同IDrecover後list保持一份；截斷source清理Pending／part，損壞原檔不publish cache，錯client／跨workspace／path traversal被拒。
- AndroidLocalAssetDeviceTest在read-only獨立emulator-5560実際執行兩項，instrumentation兩個status0、OK (2 tests)，0.241s；原始輸出保留/tmp/boarderless-android-local-asset-result.txt。本輪只用fixture PNG，未涵蓋GIF／video／大檔或GUI原生chooser。
- 最終JVM focused6/6 pass（共用文件與Desktop原檔回歸），JS／Wasm／iOS共用程式碼及AndroidDeviceTest build+native instrumentation BUILD SUCCESSFUL（2m24s）。首次Android編譯用了不存在的Os.unlink，改Os.remove後重新完整驗證上述最終結果，未用失敗版本作交付證據。
- MainActivity／MainViewController仍未切換本機模式；Android runtime preview/GIF/video尚需選對local download gateway，iOS原檔gateway與入口尚未做，跨装置素材包全流程及六主線仍未完成。未修改或啟動backend。
- 自有Android test package已uninstall、5560 emulator session已確認exit0；未對5554安裝或停止。build-and-clean.sh自動清除約271.8MiB，dry-run0B；未保留APK／APP或build，未提交／push，goal繼續active。

## 2026-10-09：Android／iOS 原生本機畫布 store adapter

- 新增 AndroidFileWorkspaceRepository／androidFileWorkspaceRepository 與 IosFileWorkspaceRepository／iosFileWorkspaceRepository，委派既有共用 PersistentLocalWorkspaceRepository。使用 app-private filesDir／Library local-documents、各平台同步鎖、既有原子store 4MiB上限與force/CAS；沒有記憶體fallback或backend請求。Android時間格式使用所有支援API可用的UTC SimpleDateFormat，不引入minSdk24無法直接用的java.time。
- 尚未注入 MainActivity／MainViewController，不把無local asset gateway的repository直接切換為APP預設；行動端既有backend模式保持。接續需完成原檔／preview／GIF／video gateway與明確local/server入口，不能宣稱整個離線APP已交付。
- 新NativeLocalDocumentAcceptance透過實際平台檔案store驗UTF-8文字、明確Loop geometry、背景修改、收據舊操作submit、同ID改內容拒絕、Undo/Redo再次重開、第二份文件與改名／activeID切換。AndroidFileWorkspaceDeviceTest在指定emulator-5560執行，runner必須收到OK (1 test)才exit0；localDocumentAndroidDeviceQa成功，不使用emulator-5554。iOS新IosFileWorkspaceRepositoryTest與兩項共用failure回歸在booted simulator執行3/3 pass、0failed/skipped；JVM既有Desktop／common回歸11/11 pass。
- 最終 :shared:iosSimulatorArm64Test／Android device APK及原生instrumentation／JVM BUILD SUCCESSFUL（4m28s）。臨時providers.exec捕獲Android runner stdout未回印Actions風格console，成功依runner內的精確OK斷言及Gradle assertNormalExitValue證明；沒有保留該APK或原生test XML。
- Android使用既有AVD的read-only獨立5560 instance，未修改5554；自有test package已uninstall並停止5560。此次啟動的iPhone18 Pro simulator（原為Shutdown）已恢復Shutdown。根目錄只刪自有UUID test leaf，未刪使用者文件或整個simulator／AVD。
- build-and-clean.sh自動清除約363.2MiB，dry-run0B。這是原生repository重開，不是APP process kill／GUI保存／OS電源中斷或跨裝置同步；未修改或啟動backend，六主線goal繼續active。

## 2026-10-09：本機畫布保存移至共用引擎

- 確認 Android MainActivity／iOS MainViewController 目前僅注入本機 quick-scheme／bundle 保存，未注入本機 CanvasWorkspaceRepository；畫布仍為後端模式，不能把方案保存宣稱為離線畫布交付。
- 將 DesktopFileWorkspaceRepository 的多文件／active ID／原子 generation CAS／操作摘要收據／UTF-8及sequence驗證／同ID重試確認邏輯移至 commonMain PersistentLocalWorkspaceRepository。平台提供 DurableLocalRecordStore、LocalDocumentLock 及時間，SHA-256改用既有共用 kotlincrypto；Desktop APP 的 repository 已真正改委派此引擎，並保留原 file格式／hash key／素材與方案gateway。沒有新增 migration 或複製測試server資料。
- 共用引擎不假造素材能力：檔案／原檔存取仍需平台gateway，Desktop保留原 list/recover；行動端尚未接入此引擎，仍需完成各端store adapter、local assets及APP模式入口。尚未切換行動端而使現有backend媒體讀取失效，也沒有宣稱native離線或跨裝置完成。
- 新共用回歸注入CAS已發佈後失敗，重開可恢復UTF-8畫布；重試同收據只confirm、不增加generation／改寫bytes，改內容重用ID及stale workspace被拒。非法UTF-8不reset，恢復原完整record仍可開啟。既有Desktop真正atomic-file／素材／背景UndoRedo／AI loop／多文件等回歸一起執行。
- 最終聚焦JVM15/15 pass、0 failed/skipped；JS／Wasm／Android／iOS simulator共用編譯與Desktop入口編譯 BUILD SUCCESSFUL（1m33s）。首輪共用層引用Desktop具體asset list/recover編譯失敗已修正為平台層提供，最終結果為修正後版本。未驗新的手機GUI／native檔案重開或OS crash。
- build-and-clean.sh清除約127.7MiB，本機未保留build／APP；未修改或啟動backend、未提交／push，完整六主線goal繼續active。

## 2026-10-09：Desktop 真實 AI 設定與拒絕授權驗收

- 使用隔離 user.home、QA-only MemoryPreferencesFactory 與正式 native file repositories 的自有 Desktop APP，實際檢視新版名稱／用途說明、15 個 N aliases 與12條 E aliases，捲動至設定與任務欄位；取消能回畫布。未讀正式使用者的 key／preferences 或連線 backend。
- 真正 GUI 分別進入 OpenAI Chat（max_completion_tokens=2048）與 Claude Messages（max_tokens=2048/input_schema）的第一輪審核，勾選前同意送出按鈕 disabled，拒絕後回設定；最終新版另驗 Chat max_tokens picker 確實改變待審核 body。使用 example.invalid endpoint、fixture-model、無 key，未批准任何 API 請求。
- GUI 發現 ConsentDenied 誤導為請求／設定失敗，改用共用 diagnostic label/hint 及工具 task message 明示「未授權，本次請求未送出；任務已停止。」最終 APP 已親眼確認；安全診斷仍保留型別化 ConsentDenied，不改授權／transport 行為。相同提示目前在狀態／hint／message 三處重複，後續可簡化顯示。
- 自有 log6行／3次 ConsentDenied、零 Connecting/HttpResponse；最後一輪前後2份 native records SHA-256完全一致，已確認自有QA PID停止。詳見 AI_DESKTOP_CONSENT_ACCEPTANCE.md。不是實際 provider 成功、第二輪工具審核或最終套用／多人驗收。
- 最終JS／Wasm／Android／iOS simulator共用編譯、Desktop distributable與GUI QA task BUILD SUCCESSFUL（3m04s，含人工操作等待）；初輪臨時 init 使用 Gradle9已移除的 project.exec 失敗已改用 providers.exec。中間首次退出後 AX重新選取啟動新QA程序，cleanup正確拒絕；停止已識別自有程序再清理完成，最終正常退出後直接確認PID，不再讀已關閉APP的AX。
- 最終自動清理約553.4 MiB、dry-run0 B，未保留APP／build。未提交／push、未修改或啟動backend；六主線完整 goal 保持active。

## 2026-10-09：AI 編輯草稿用途與任務代號

- 使用者先前無法從功能名稱判斷用途，標題改為「AI 協助編輯圖形（草稿）」／Edit diagram with AI (draft)，開始按鈕改為「產生編輯草稿」。補中英文說明：可新增分支、排列或插入節點，工具讀取核准文字／位置／尺寸／關係，不傳圖片像素或素材 URL 欄位。沒有新增 vision 或模型任意操作 APP 的權限。
- 開始前的核准物件列表顯示 N 代號，另列 E 代號、source／target 與實際 Forward／Backward／Both／None 箭頭及 label。工具引擎和畫面共用 alias factory，按 object/relation ID 排序；平行關係各有 E 代號，範圍外關係不列。這些代號只在該次 task 有效，仍需逐輪審閱實際 body。
- 原先只在引擎拒絕的超過256條核准關係，現在也在開始前禁用；草稿審核的 insert_node_on_relation 改顯示中英文操作名稱，避免使用者只看到 raw tool name。
- 新回歸直接比較核准範圍的 alias 與 read_approved_graph 真正工具回傳，包含輸入順序不同、平行反向線及 outside relation；JVM 聚焦33/33 pass、0 failed/skipped。JS／Wasm／Android／iOS simulator 共用程式碼編譯 BUILD SUCCESSFUL（1m34s）；尚未實際檢視 GUI 排版或呼叫真實 Router。
- build-and-clean.sh 已清除約124.6 MiB；成品沒有保留。未修改或啟動 backend，完整六主線 goal 保持 active。

## 2026-10-09：AI 草稿連線插入節點

- 新增明確 capability 控制的 insert_node_on_relation，OpenAI Chat／Claude Messages 均使用同一份閉合 schema。啟用時共九個工具，未啟用維持八個；節點位置、尺寸、文字、形狀與顏色必須明確提供。
- 手動與 AI 插入共用一筆 create/delete/create 交易，保留實際流向、第一段 label/intent 及顏色；新連線重新路由，Undo 還原完整原 geometry。沿用四個既有 wire operation，不新增後端契約。AI 新節點與手動一致置於最高圖層，檢查可表示的圖層／原 relation 版本及端點座標範圍。
- 移除的 relation logical ID 在整次任務仍保留，不能被後續工具重用。範圍、鎖定、時序圖保護與最後 fresh-authority review 持續生效；每輪 HTTP body 各自審閱與授權，拒絕插入不修改 references 或 proposal。
- 最終 JVM 聚焦 48/48 pass、0 failed/skipped，涵蓋方向、隔離提案、wire、Undo/Redo、portable loop、圖層與超界拒絕、兩協定逐輪授權串流。JS／Wasm／Android／iOS simulator 共用程式碼編譯 BUILD SUCCESSFUL（1m35s）。使用 MockEngine，未呼叫真實付費 API，未驗實際 GUI 或正式多人 backend。
- build-and-clean.sh 結束後已清除約124.4 MiB建置資料，未保留成品。六主線仍未完整交付，goal 保持 active；backend 範圍確認仍未收到回覆，本輪未修改或啟動 backend。

## 2026-10-08：AI 本機自循環與 durable 方案 geometry

- AI 圖形草稿移除固定 selfLoops=false，透過 aiCanvasToolCapabilities 使用與手動畫布相同的 repository.supportsRelationGeometry；Desktop 本機文件支援時宣告 true，正式 BackendWorkspaceRepository 仍 false。未修改 backend 或放寬其 self-loop 拒絕。
- AI create_relations 同端點時保存明確 RelationGeometry(route=Loop)，按 working workspace 的既有與同批新增 self-loop 數量沿用 80+count*32（上限4096）extent。避免只靠 renderer 隱式自循環而讓 Clipboard／方案簿驗證與 preview 失敗；不新增 AI wire 工具或供應商呼叫。
- 新共用測試覆蓋 capability、雙 self-loop 的不同 extent、隔離 proposal／fresh-authority review、live route、Clipboard JSON roundtrip、方案 preview 與 Undo/Redo；native 新增實際 DesktopFileWorkspaceRepository 保存→新 repository 重開、UTF-8 label／geometry／已保存提交收據及 quick-scheme 讀回。最終聚焦 JVM 24/24 pass、0 failed/skipped，JS、Wasm、Android、iOS simulator 共用程式碼編譯 BUILD SUCCESSFUL（1m48s）。首輪錯誤引用 graph 的編譯失敗已修正為 working workspace，最終上述結果為修正後版本。
- build-and-clean.sh 已在此次驗證結束後清除約124 MiB，新產物沒有保留；最後 dry-run 0 B。此入口之後預設 --no-build-cache，避免重新累積本專案的全域編譯輸出 cache；清理與 wrapper 的失敗後清理／保留錯誤碼共11項 disposable tests通過。
- 尚無此功能的真實 Router/GUI 或正式多人跨端驗收；backend 的範圍確認仍待使用者回覆，不能用本機旗標代替 server capability。其他五主線與完整六條 goal 繼續 active。

## 2026-10-08：AI Chat 輸出上限欄位的明確相容選項

- AI 理解驗證與圖形草稿的 OpenAI Chat 設定新增 max_completion_tokens / max_tokens 選擇；預設保留較新欄位，不因 HTTP 400 自動切換或重試。上限空白不送任何一個欄位；Claude Messages 仍在指定上限時使用 max_tokens。
- AiRequestBodyProfile 凍結選擇，理解與每輪工具 body 使用同一個選項、最多一個 token 上限欄位。理解視窗切換選項會清除授權；工具開始時凍結設定，仍保留每輪請求 review/consent。既有安全診斷會記錄實際欄位及數值，不增加原始 request/response 保存。
- 新增兩項共用測試，涵蓋兩種 Chat 選擇、理解／初始工具／後續工具、既有 safe diagnostic、空白與 Claude 欄位隔離。聚焦 AiRequestParametersTest / AiRequestBodyTest / AiDiagramToolWireTest / AiDiagramToolLoopTest / AiWireDiagnosticTest 的 JVM 測試及 JS、Wasm、Android、iOS simulator 共用程式碼編譯 BUILD SUCCESSFUL（1m57s）。未驗真實 Router、GUI 點選或聲稱供應商所有模型皆支援舊參數。
- 此次透過 build-and-clean.sh 執行，結束後實際刪除約 124 MiB 新 build / .gradle / .kotlin；最後 dry-run 為 0 B。没有重新保留 verified 成品。AI log 仍只有台灣 11:15 的舊 reasoning/tools 拒絕，修正後實際 API 成功待驗。
- 共用背景的 backend capability / operation / projection 仍未接上；已提出是否解除既有不動 backend 限制的範圍確認，尚無回覆，沒有修改或啟動 backend。其他 APP 工作仍可繼續，六主線 goal 保持 active。

## 2026-10-08：依使用者要求清除本機建置與成品

- 使用者確認本機只保留專案，最後成品也刪除；正式下載由 GitHub Actions 統一打包發佈。新增根目錄 Clean BoarderLess Build.command、scripts/clean-build-artifacts.py 與 build-and-clean.sh（建置後自動清理），說明見 BUILD_CLEANUP.md。
- 已實際清除所有當前專案 build/verified、artifacts、.gradle、.kotlin，三個已知隔離 build root、media-validation 的 build/gradle-cache 與三個 QA APP 副本；兩輪合計約 8.5 GiB 建置資料。最後 dry-run 計畫為 0 B。先前記錄的 verified 安裝／發佈檔與 /tmp test XML 已按要求刪除，不能再假設那些檔案仍可用。
- 清理保護 10 項 disposable fixture 測試與兩支 shell 語法檢查通過；Git tracked source、原始資料與共用 Gradle/toolchain cache 保留。之後本機驗證需要重建，結束後使用清理腳本，不持續累積成品。六主線 goal 不因此完成。

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


## 2026-10-08：實際 OpenAI 圖形工具 400 已定位並修正請求

- 新版本機 log，台灣時間 11:15 的兩次實際 OpenAiChat 請求：requestEndpoint=ChatCompletions、requestModel=Gpt56Luna、tools=8、stream=true、reasoning=Default；HTTP 400 Json，errorBody=Parsed、providerType=InvalidRequest、providerParam=ReasoningEffort、rejection=ReasoningToolsInChat。實際拒絕的是 Chat 工具搭配額外推理，不是 endpoint 拼錯或沒有送 Bearer。沒有保存或讀取原始錯誤訊息／API Key。
- 依官方 GPT-5.6 Luna 文件（medium 預設／支援 none）及 function-calling 的 GPT-5.6 Chat 範例（reasoning_effort=none），圖形工具 serializer 對明確的 gpt-5.6/luna/terra/sol ID 加入 reasoning_effort=none。未知 Router model ID 不按前綴猜測；Claude 和純文字理解保持原有參數。當前工作樹已具有 max tokens／temperature 選填行為，本次保留，不重新加上預設輸出上限。
- 視窗顯示相容設定；每一輪完整 body 仍先呈現並授權，沒有改 endpoint、HTTP 自動重試／fallback 或直接套用畫布。provider rejection 分類的錯誤提示也改為確切原因。Responses 協定尚未加入 APP，不能只把現有 Chat endpoint 改成 /v1/responses。
- 驗證：37 項 JVM 全數通過，包含實際類型的 mock 400 拒絕條件、兩輪各自批准的修正 body／隔離草稿、模型與協定範圍、選填參數和安全診斷。Android APK、JS/WasmJS/iOS arm64 共用編譯及 Desktop 打包通過。使用獨立 /tmp/boarderless-ai-reasoning-build 輸出，已更新兩個 verified 成品。
- 尚未發出修正後的新真實 OpenAI 請求，也未取得成功草稿的使用者證據；修正後的服務驗收待新版 log。這輪未實作 iOS 素材包（開始查看時發現 AI 的新實際失敗證據，因此先處理已確認的阻礙）。完整六主線 goal 仍 active。
- 官方依據：https://developers.openai.com/api/docs/models/gpt-5.6-luna 、https://developers.openai.com/api/docs/guides/function-calling 。


## 2026-10-08：Android 快速方案簿接入 durable 私有檔案

- Android MainActivity → App → WorkspaceScreen 現在注入 AndroidFileQuickSchemeStore；新增、改名、刪除和 .blqs 匯入保存使用整本方案簿的 atomic CAS 記錄，包含 nextId、名稱、payload、schemaVersion、sourceWorkspaceId。資料位於 app filesDir/quick-schemes，完成 fsync／rename／目錄 fsync／回讀後才回報成功。
- Desktop 的既有方案簿驗證抽成 PersistentQuickSchemeStore，共用相同 library v1 結構和 SHA256 key；Desktop 舊檔案可直接讀取。Android 使用既有 AndroidAtomicLocalRecordStore，方案簿 payload 上限 4 MiB；素材收據仍使用原本 1 MiB，不混用檔案。
- 編號保持單調、不因刪除而重用。無效方案和損壞檔案會報錯，不自動清空。Android 既有 Settings 方案未搬移／刪除，首次開新檔案方案簿為空；遵循尚未發布階段不擴大資料遷移工作的範圍。
- 驗證：26 項 JVM（DesktopFileQuickSchemeStore 5、payload 8、transfer 6、bundle 7）全數通過。Pixel_9_Pro_XL 自有 read-only 模擬器上 6 項原生測試全數通過：方案簿 3 項（UTF-8 內容與改名刪除／ID重新讀回、約 1.28 MiB library、損壞拒絕）和素材收據 3 項（CAS／權限／pending ID 重開恢复）；沒有跳過。Android APK、JS/WasmJS/iOS arm64 共用編譯、Desktop 打包通過。
- 已更新 androidApp/build/verified/BoarderLess-debug.apk 和 desktopApp/build/verified/BoarderLess.app。這輪沒有做 APP GUI 新增方案／程序 kill 後復原／真機斷電測試，也沒有新增正式伺服器自動同步 API；跨裝置自動同步仍未完成。完整六主線 goal 保持 active。
- AI log 尚未出現新診斷格式的使用者請求，HTTP 400 的真實根因仍待新版記錄；沒有擅自使用 API Key 或重送請求。


## 2026-10-08：AI 請求與錯誤回應診斷摘要

- 使用者實際 log：台灣時間 09:58 一次 HTTP 200 Completed，10:05、10:18、10:20、10:22 四次 HTTP 400 Json。既有記錄未含 provider error 參數，無法據此確認根因。
- Connecting 現在記錄 endpoint 類別、已知模型類別、UTF-8 請求大小、messages/tools 數量、stream、reasoning_effort 是否省略、max_completion_tokens/max_tokens 與標準參數名稱。其他模型／自訂參數名稱只記 Other；沒有 URL、headers、金鑰、prompt、畫布文字或工具 schema 原文。
- 非 200 JSON 回應最多讀取 16 KiB + 1 byte，等待最多 2 秒；嚴格 UTF-8 和 bounded JSON depth。記錄有限清單的 error.type/code/param 與已知錯誤訊息分類；不保存原始 message 或回應。空、過大、UTF-8/JSON 錯誤、讀取失敗只記分類，保留 HTTP 拒絕；不重送、不改 endpoint、每次請求仍須授權。
- 現有 ~/.boarderless/logs/ai-probe.log 與「複製安全除錯資訊」直接沿用新摘要，既有輪替與私有檔案權限不變。
- 驗證：34 項 JVM 診斷、transport、工具迴圈和本機 log 測試通過；isolated JVM 測試需額外把本次 jvmJar 加入 runtime classpath（暫存 init script，未修改 repo 測試設定）。Desktop createDistributable 與 Android / JS / WasmJS / iOS arm64 共用編譯通過，已更新 desktopApp/build/verified/BoarderLess.app。
- 這是診斷改善；未發送新的真實 OpenAI 請求，尚未確認使用者這次 400 的確切根因或修復。完整六條主線 goal 仍未完成。


2026-10-08 最新使用者實測：OpenAI 路由文字理解成功，使用者直接提供本次ask／answer作為證據。回覆保留N1..N11全部11節點、E1..E6全部6連線的exact source/target/forward，以及N2／N7的parent N5、其餘null；沒有重複或虛構項目。Traditional Chinese summary正確描述N8分支結構、N3自環僅是圖上的關係、N5 containment及N11未連接媒體；uncertainties不猜圖片內容／執行順序／條件。這是使用者報告的實際OpenAI路由成功證據，覆蓋先前「最後實測只有HTTP401／OpenAI理解仍阻塞」的當前狀態；不將成功歸因於某一修正，也不宣稱其他provider／endpoint已排除401。

目前OpenAI文字理解可用，401不再列為此路徑的現行阻塞。每次授權／嚴格UTF-8／空回應與safe log保護維持；沒有讀key、代送新AI請求或把完整用戶圖表／原回答寫入安全log。Claude Messages的實際成功、AI工具draft loop＋review→Apply→Undo／Redo／durable reopen完整流程尚待實際交付證據；不能由唯讀理解成功推定工具或Claude完成。完整六主線goal保持active，其他製作缺口不變。

2026-10-08 Android 方案包反向匯入：chooseSource／receipts已接MainActivity→App→SchemeBundleDialog。新OpenDocument launcher追加在bundle export後，保留六個既有registration keys；所有素材／草稿／PNG/PDF／bundle picker互斥，bundleImportPickerInFlight保存及recreation還原、destroy取消waiter、遲到既有document不讀／不刪。content URI只用來即時建立AndroidSchemeBundleSource owned cache snapshot；分塊≤1MiB／fd.sync、positional read、immutable byteSize及release，scope變化／truncation清private snapshot，原picker文件不變。review／checksum及confirmation／fresh authority沿既有共用codec與dialog。

Desktop收據規則抽為PersistentSchemeBundleImportReceipts＋SchemeBundleReceiptStore；Desktop仍用原DesktopAtomicDraftStore。共用Ready寫入現在也驗全部dimensions／duration及mediaKind。Android store使用filesDir下獨立scheme-bundle-receipts目錄，scope user/client/workspace＋bundleDigest key、pending-before-complete／Ready ID、generation CAS、UTF-8 JSON及generation+payload SHA。private root700／files600、leaf symlink拒絕、process mutex＋cross-process file lock、exclusive owned temp→fd.sync→atomic rename→directory fsync及readback；same-content retry確認durable，不以pending／corrupt／permission或GET失败當成可重新allocate／upload。Native SDK無O_DIRECTORY／unlink，採open後fstat確認directory、Java file.delete刪own symlink fixture。

首native run78407三cases均在canonical path gate失敗，發現Android framework filesDir路徑別名；production及fixture先canonicalize framework提供的父目錄再建立leaf，leaf symlink與private mode要求仍完整保留。32288最終build成功18s：Android Host913（新增snapshot2）、JVM1019（6既有skip）、native Android selected3皆0failures/errors（native0skip），Android APP assembleDebug、JS／Wasm／iOS arm64 compile通過。3 nativecases在Android17實際執行Os.rename／file及dir fsync／filelock，驗新store instance讀回generation及payload、私有權限、stale CAS拒絕與corrupt不reset；pending receipt重開先GET原ID、Pending拒絕而Ready重用、different ID／metadata／foreign owner拒絕；dangling symlink及不安全root mode拒絕。snapshot host cases驗>1MiB獨立source、修改原picker文件後positional bytes不變、release idempotent、scope失效／44byte短檔清理。gateway狀態為fixture，未代正式服務／APP kill／系統選檔器GUI。

40600 Desktop distributable成功14s，Android ready APK及Desktop ready bundle更新，diff check通過。本輪readonly／no-snapshot／no-window AVD emulator-5560已指定serial emu kill，session56453 exit0且adb inventory空。Android方案包匯入出口現已接線且native收據驗證，不再是export-only；真正Android↔Desktop APP媒體包互傳、硬中止後恢復、SAF GUI、Android方案library本身native atomic保存、iOS/Web包adapters及自動跨裝置API仍待交付。完整六主線goal維持active，真AI401仍未排除；未改backend/env、未提交/push。

2026-10-08 快速方案 Android 原始素材包匯出：新增 androidSchemeBundleRuntime，MainActivity→App 接上 SchemeBundleRuntime.chooseDestination，快速方案簿既有「匯出方案包」入口現可產生 .blscheme／BLQS0001。追加 application/octet-stream CreateDocument launcher，不改既有五個launcher順序；與素材／草稿／圖片PDFpicker共用單一in-flight門禁，保存bundlePickerInFlight以處理recreation、onDestroy取消waiter、取消／遲到callback僅清可確認空白document，不讀／刪既有非空文件。

沿共用QuickSchemeBundleCodec重新authorize／verify originals／asset及整包SHA，不轉文字JSON分享，不把signed URL或local URI帶進包。重用Android private stage＋fd.sync後一次SAF發布；staged destination新增maximumBytes參數（canvas既有budget不變），bundle使用既有BLQS protocol MaximumBundleBytes，不錯用32MiB canvas budget。每塊fresh scope／取消檢查，發布前確認空白，publication開始後失敗保留不確定輸出，abort僅清private stage，未聲稱SAF atomic或所有provider durable。

48435全AndroidHost911 tests（含新增2）、0failures/errors/skips及Android APP assembleDebug成功22s。新增測試使用真Java file stage／provider output file，33MiB opaque original以重用1MiB buffer串流產生checksum、共用export完整落盤後review重新驗整包／original SHA、Unicode scheme及media reference；再次abort不刪已發布檔。另一case33MiB已stage後scope失效零publication／清stage與空白document。gateway／document provider為fixture，原始資料刻意opaque，未聲稱native PNG decoder／ContentResolver／系統選檔器GUI／真跨APP驗收。ready APK更新androidApp/build/verified/BoarderLess-debug.apk，diff check通過。

Android反向匯入尚缺durable pending/ready receipt adapter，chooseSource／receipts保持Unavailable；iOS/Web原始包adapter及自動跨裝置方案API仍待交付。此步僅完成Android方案包匯出接線及驗證，未縮小六主線scope或宣稱完整跨裝置交付。下一步Android durable receipts＋private immutable snapshot／import picker、iOS/Web adapters及實機互傳。真AI401仍未排除，goal active；未修改backend/env、未提交/push。

2026-10-08 使用者補充匯出修復紀錄已讀並與source核對：[修復 PNG 與 PDF 匯出](https://chatgpt.com/s/cx_6ac6f30ccf608191865c61999775acaf)。macOS固定419萬／1677萬pixels及8192edge門檻不屬產品規格，現行desktopCanvasExportRuntime使用Int／IntArray表示範圍，CanvasExportRaster亦僅表示限制；desktop destination maximumBytes=Long.MAX_VALUE，不沿用scheme包上限。UUID預設檔名避免衝突，已存在文件要求另命名；保存成功顯完整savedLocation並提供revealSavedFile（Finder／資料夾），實際Finder GUI仍未驗收。既有CanvasExportSceneRenderTest含1×／2×完整scene→PNG/PDF實寫測試，已在上一輪JVM1019中通過。此次EXIF修正保留上述當前改動，不恢復舊固定尺寸上限。舊批次尺寸上限記錄僅是歷史狀態，不代表目前macOS規格；其他平台仍需個別核對並避免聲稱同步解除。

2026-10-08 Desktop JPEG EXIF 方向修正：desktopJpegOrientation 只讀 JPEG header／APP1，以JPEG自身segment length逐marker最多64KiB配置解析 little／big-endian TIFF orientation，忽略前置 XMP，截斷／非法marker明確拒絕，不新增總metadata容量門檻。DesktopViewportImageLoader 以方向後尺寸規劃 viewport／export tiles，將 upright region 反投影回原始 sourceRegion，再只旋轉／鏡射已取樣小tile；複製ARGB不做alpha疊加，原檔不整張解碼。方向1..8的 live tiles 與 export 共用同一路徑，原授權／checksum／cache撤權保持。

新真JPEG測試先在舊程式重現錯誤（14666失敗），修正後43538 focused通過，再9190全 Desktop52 tests／0failures/errors/skips及 distributable 成功22s。1024×640四象限JPEG跨512px tile邊界，八種方向逐一驗viewport與export寬高及四角顏色（含方向5..8交換尺寸），fixture涵蓋兩種TIFF byte order及前置XMP；另驗截斷APP1／連續FF marker直到EOF確實拒絕及無EXIF回1。兩次load只download一次、authorize兩次，來源為fixture，native ImageIO／Skia像素真實執行。移除新metadata固定總量門檻後82429最終全Desktop52 tests／0failures/errors/skips及distributable重驗成功21s；ready Desktop bundle更新、diff check通過，未提交/push、未改backend/env。

此證據限Desktop正常JPEG viewport/export路徑，不代普通Skia preview／PNG eXIf／WebP／ICC或其他端live-preview parity與physical GUI。其他原始六主線缺項維持，真AI最後401仍未排除，goal active。

2026-10-08 AI 認證輸入與安全診斷：理解／工具草稿視窗共用 aiCredentialHeaders，Bearer 模式接受原始 key 或單一完整 Bearer 值，去除外側空白後僅產生一個前綴；空值／孤立 Bearer／重複前綴／內嵌空白／控制字元／非 ASCII／超過 header 上限均在 HTTP 前以不含輸入的 typed validation error 拒絕。ApiKey 保留 x-api-key 語意，None 不使用輸入。每次 HTTP 的既有批准、UTF-8／空回應保護及停用隱式重送維持。

Connecting 在 request builder 完成後記錄 preparedAuth 閉合列舉，HttpResponse／HttpRejected 與 terminal failure 保留 preparedAuth、authChallenge（WWW-Authenticate 的首個 scheme／多欄位類型）；不記 key、realm、error_description、endpoint、model、prompt 或 response body。preparedAuth 是準備階段的 header 類型，不聲稱已量測 wire 上游／client plugin 改寫；challenge 僅提示，不能單憑它判定 401 根因。Desktop 既有本地 logger 自動寫這些 typed 欄位到 ~/.boarderless/logs/ai-probe.log，仍每檔256KiB＋一backup、私有權限及 bounded queue。

76453 最終 build 成功1m57s：JVM1019（6既有skip）、JS917、Wasm917、Desktop50，全部0 failures/errors；Android main、iOS arm64 compile 及 Desktop distributable 通過。新增 common credentials 3 cases 與 MockEngine 401 case，驗確實準備單一 Bearer、單次請求、Basic challenge 及 private key／realm／body不出現在安全報告。已更新 desktopApp/build/verified/BoarderLess.app，無 QA preferences；diff check 通過。真實使用者安全 log 目前仍不存在，最後實際請求證據仍 HTTP401／Other，沒有讀取真 key、代發付費 AI、或宣稱已解除 401。

畫筆／compact inspector 邏輯核對：brushMode 排除物件及連線屬性面板，close 清 expanded／editing；status 以實際 toolbarHeightPx 上移並在 compact inspector／modal 隱藏。先前獨立 Desktop compact GUI 的避讓／關閉證據保持有效，本輪僅核對程式，未新增 physical GUI 驗收。原始六主線維持 active／完整 scope，正式多人／跨裝置／shared背景／GIPHY provider-node及批准、四端完整GUI仍未交付。未改 backend/env、未提交/push。Bearer 格式核對 OpenAI 官方 API reference：https://developers.openai.com/api/reference/overview#authentication。

2026-10-08 Web 大圖原檔匯出：新增 BrowserExportImageSink／loadBrowserExportImage 與 runtime.loadExportImage。每次authorize核對asset/workspace/Ready／PNG/JPEG/WebP及1..200MiB，沿原AssetDownloadCoordinator驗size／SHA256，Kotlin不合併整個original，≤1MiB chunks以Base64跨JS/Wasm後逐塊轉native Blob parts；encoded originals只在transient handle，不進IndexedDB／localStorage／canvas payload。checksum成功後才native decode；輸出前從實際檔header規劃完整aspect-fit／EXIF orientation與≤4096每邊、4,194,304pixels，createImageBitmap resize＋from-image，sRGB canvas lossless PNG bridge到Skia。取消移除handle，late bitmap.close，canvas資源finally清理。native瀏覽器內部decode peak不作保證，不聲稱所有codec永不配置原尺寸像素。

header處理PNG IHDR＋eXIf、JPEG SOF＋Exif（忽略XMP APP1，避免把已有Exif方向重設）、WebP VP8X／VP8／VP8L＋EXIF；PNG/WebP可跳過壓縮資料讀metadata，取消逐輪检查。來源header最大65535每邊／268,435,456px；JPEG pre-SOS header／單個EXIF metadata限1MiB、PNG/WebP最多65536 chunks，超限明確拒絕，未默默fallback到小preview。main index.html載入image-export.js，Karma載入同source並追蹤Gradle inputs。

10060 最終驗證成功1m17s：JS913／Wasm913，0 skipped/failures/errors，各新增5 Native Chrome cases：14.4MP PNG→2500×1000完整邊緣8px條紋≥14次轉換、top紅／bottom藍與alpha128；撤權零二次download、foreign workspace零download、corrupt checksum拒絕無handle；取消decode後handle立即釋放及late bitmap只close1次；JPEG EXIF6＋後續XMP APP1長寬交換100×200／top紅bottom藍；33MiB trailing-padded PNG以1MiB重用buffer串流驗SHA後仍可16×8 decode，超過舊32MiB preview限制。取消測試首輪Wasm timeout因fixture以錯receiver呼叫Window API，修正saved.call(globalThis)後保留所有assertions並重驗。gateway為fixture，瀏覽器Blob/bitmap/canvas/Skia是真實執行，不代正式服務。

JS及Wasm production distributions都通過，webApp/build/verified/{js,wasmJs}已更新；逐byte確認artifact image-export.js與當前source一致，index包含script，diff check通過。四端均有export-specific image callback與APP接線；仍待完整APP GraphicsLayer/readback／下載到磁碟、JPEG／WebP／EXIF／ICC live-preview parity、真GIF／video frame及各平台GUI驗收。六條原始主線完整scope維持active，AI最後真401未排除，正式多人／跨裝置／shared背景與GIPHY provider-node／批准未完整交付。未改backend/env、未提交/push。Browser resize／EXIF語意依MDN createImageBitmap官方browser文件：https://developer.mozilla.org/en-US/docs/Web/API/Window/createImageBitmap。

2026-10-08 iOS 大圖匯出原檔解碼：新增 loadIosExportImage 與 IosMediaPicker.runtime.loadExportImage callback，依同一previewPermits限制並重新authorize、核對asset/workspace/Ready及PNG/JPEG/WebP／1..200MiB。原檔沿IosFileAssetDownloadSink＋AssetDownloadCoordinator真正checksum驗證，ImageIO從verified file URL讀header及完整圖像sample，不把整個original轉ByteArray。共用imageExportSize控制完整aspect-fit／不upscale／來源header及≤4096每邊、4,194,304px輸出；EXIF orientation5..8先交換規劃長寬。CGImageSourceCreateThumbnailFromImageAlways＋ThumbnailMaxPixelSize＋WithTransform，忽略embedded小thumbnail並套用方向；只把bounded sample lossless PNG bridge到Skia，source／thumbnail／dictionary／number／CFData／destination皆finally釋放，原檔由NonCancellable sink.release清理。

69875 最終native回歸成功49s：iOS Simulator925 tests，0 skipped/failures/errors，iOS arm64 compile通過，git diff --check通過。新增3 cases 使用真Skia來源、ImageIO decoder、CoreFoundation metadata及Ios verified sink：6000×2400／14.4MP PNG→2500×1000，左上紅／左下藍無垂直翻轉、alpha128維持、右下8px條紋至少14次轉換；撤權無第二次download，foreign workspace零download；corrupt checksum拒絕且preview私有file集合與before完全相同。另真ImageIO寫JPEG EXIF orientation6，輸出100×200（來源200×100，不upscale），top紅／bottom藍證明順時針方向，不只驗尺寸。授權／transport為fixture，不代正式後端證據。本輪自行boot之Simulator已恢復shutdown且inventory確認。

這是iOS native export image decoder／verified original pipeline驗證，未代APP完整dialog／Files picker／iCloud或其他provider保存GUI，也未證明各端live-preview與EXIF／ICC一致性；WebP與其他EXIF方向仍待個別驗收。Web export-specific decode仍待接線。原始六主線維持完整scope／active，AI最後真失敗401未排除，GIPHY provider-node保存批准／正式多人／跨裝置／shared背景未完成。未改backend/env、未提交/push。方向規則依Apple ImageIO官方文件與Xcode SDK header（https://developer.apple.com/documentation/imageio/kcgimagesourcecreatethumbnailwithtransform）；Apple .md官方內容已讀，web工具不支援text/markdown時以官方URL讀取。

2026-10-08 Android 大圖匯出接線與實際 native decoder 驗證：新增 AndroidExportImageLoader 與 MainActivity.loadExportImage callback，靜態圖片不再使用1024 edge的普通preview。新下載前重新authorize／核對asset ID、workspace、Ready、PNG/JPEG/WebP及1..200MiB，原檔沿AssetDownloadCoordinator＋AndroidFileAssetDownloadSink真正checksum驗證，decode後只清本次隨機private directory，無file/pixel cache、URL或handle持久化。新版Android ImageDecoder使用software／sRGB／targetSize，在header檢查後依完整圖片aspect-fit输出；API24–27 fallback完整512px region合成，以SRC取代並逐塊回收，不截成viewport／16塊。兩條路徑都核對輸出尺寸及取消，失敗回收已取得bitmap。

新增共用 imageExportSize：來源最大65535每邊／268,435,456pixels，輸出最大4096每邊／4,194,304pixels，不upscale、保完整aspect、極端長寬至少1px。3 common cases驗14.4MP／portrait／tiny、極端aspect、非法header與輸出在native allocation前拒絕。22534完整build成功1m58s：Android Host903、JVM1012（skip6）、JS906、Wasm906皆0failures/errors；Android APP／Device APK及iOS arm64 compile通過，production Android ready APK更新，diff check通過。

找到既有Pixel_9_Pro_XL AVD，以-read-only／-no-snapshot／-no-window啟動emulator-5560。48774最終connectedAndroidDeviceTest指定AndroidExportImageDeviceTest成功10s，2 tests／0 skipped/failures/errors：真Android17(37.2) ImageDecoder與強制region fallback都將6000×2400 PNG輸出2500×1000，跨tile alpha完全保持128、右下8px條紋至少14次轉換；真Android原檔download sink＋checksum＋decoder pipeline輸出可讀pixel，拒絕新授權時零download，corrupt checksum確實在decode前拒絕且所有私有part／directory被清，來源檔bytes保留。gateway授權／transport為fixture，不代正式後端或FilePicker證據。模擬器已透過指定serial關閉，process及adb inventory確認停止，原AVD保存資料未寫入。

這是native大圖像素與verified原檔pipeline交付，不代API24–27實際OS／JPEG EXIF／WebP或整個APP輸出dialog／系統保存GUI驗收。iOS／Web export-specific decode仍待接線；六條原始主線保持active，AI最後真失敗401未排除，正式多人／跨裝置／shared背景／GIPHY provider-node與批准仍未完整交付。未修改backend/env、未提交/push。API依Android官方ImageDecoder setTargetSize與BitmapRegionDecoder文件（https://developer.android.com/reference/android/graphics/ImageDecoder、https://developer.android.com/reference/android/graphics/BitmapRegionDecoder）。

2026-10-08 Desktop 大圖匯出：檢查確認原 export 使用 loadPreview，超過12MP／32MiB的來源會被preview policy拒絕；live viewport loader則只補最多16塊。新增 MediaImportRuntime.loadExportImage(session,id,width,height)，Desktop 接同一 verified-original loader／快取，並重新authorize每次請求。匯出full-image模式依box aspect-fit決定sample，所有source tiles完整交付（上限128則拒絕，不take16），結束才合成≤4096／4,194,304px bitmap。以BlendMode.Src取代各塊，避免base＋final把半透明像素加深；WebP無ImageIO region reader仍沿既有bounded Skia path，過大來源明確失敗。

CanvasExportDialog 對靜態圖片優先使用 export callback，依node local size×輸出scale取樣；GIF第一影格與影片poster沿原規則。scale／capture／runtime變動即remember新frame state，立即清舊bitmap／保存狀態，await所有媒體完成後才生成raster。其他三端尚無export callback，保留既有preview路徑，不宣稱四端大圖品質完成。

21689 最終完整build成功1m33s：JVM1009（skip6）、JS903、Wasm903、Desktop50，全部0failures/errors；Android main／iOS arm64 compile與Desktop distributable通過，production ready bundle更新且不含QA preferences，diff check通過。新增2 Desktop cases：6000×2400／14.4MP PNG→2500×1000 export（18個512px sampled tiles，完整source coverage超過live16上限）；最右下細8px黑白stripes保留至少14個轉換，跨tile alpha維持0x8000ff00而非double-over；cache hit撤權拒絕（1次download／2次authorize）。另驗thin-aspect source fit及全tile面積覆蓋。這是真native decoder／verified gateway pipeline與Skia合成測試，授權gateway為測試fixture，不代替APP大圖GUI／正式服務。

下一步其他端export-specific bounded original decode、真GIF／video poster及physical匯出驗收，並繼續完整六主線。AI最後已知真失敗401未排除，GIPHY保存批准／provider-node同步與正式多人／跨裝置／shared背景仍未完整交付。Goal保持active，未修改backend/env、未提交/push。

2026-10-08 iOS PNG／PDF 匯出接線與保存驗證：新增 IosCanvasExportPicker（UTTypeFolder、UIDocumentPickerDelegate、取消／遲到callback fence、dismiss後才resume、lifecycle disposal），MainViewController 注入 CanvasExportRuntime。系統面板打開時不寫檔；選擇folder後每次建立 BoarderLess-UUID.png/pdf，無bookmark／持久folder preference。IosCanvasExportDestination 保留bounded owned chunks（1MiB／總32MiB）、fresh scope／coroutine guard，commit期間才持有 security-scoped access並在finally釋放。

原生保存：NSFileCoordinator 包住目標URL accessor；同資料夾隨機hidden .partial、O_EXCL／O_NOFOLLOW／0600，分塊write可處理EINTR、fsync／close、最後guard、Darwin renamex_np(RENAME_EXCL)排他發布，再force parent directory。既有final從不覆寫／删除，發布後durability不確定保留final供檢查；發布前失敗清stage，cleanup失敗附加原exception，不靜默忽略。UI只顯示typed成功／失敗／發布狀態不確定，不顯示NSError或用戶路徑。

37173 最終驗證成功48s：iOS Simulator全套919 tests，0 skipped/failures/errors；新增4 native cases確認PNG／PDF實存bytes全部等於encode、0600權限／既有檔保留、4個pre-publication guard failure全部無partial／final、1MiB＋tail multi-chunk位元一致、真Foundation coordinator保存並拒絕覆寫。iOS arm64 compile通過，git diff --check通過；本輪自行boot的Simulator已恢復shutdown。這些是真sandbox filesystem／NSFileCoordinator證據，不代替security-scoped Files面板／iCloud或其他FileProvider／APP readback／真機存檔GUI验收。

目前四端均有export adapter與APP接線，Desktop另有實際全畫布PNG／PDF保存與透明PNG GUI證據；Android／Web／iOS physical rendering及外部保存驗收仍Pending，不能聲稱完整四端交付。接續大圖original/tile品質與GIF／video真來源、六條原始主線正式多人／跨裝置／shared背景／GIPHY provider-node及AI真401排除。Goal保持active，未修改backend/env、未提交/push。

2026-10-08 Web PNG／PDF 匯出接線與二進位驗證：新增 browserCanvasExportRuntime，webApp Main 注入 CanvasExportRuntime，沿既有畫布選單／dialog 提供全畫布、所選、倍率與透明背景。每次下載命名加入 UUID，stage chunks 深拷貝（每塊≤1MiB、總≤32MiB），commit 單次 fresh scope/cancellation 檢查後 dispatch，abort 釋放所有未發布 bytes。JS 用原生 ByteArray/typed-array Blob parts；Wasm 以 bounded Latin-1 chunk interop 並立即還原 Uint8Array，不用 UTF-8 解碼二進位。Blob 使用 image/png 或 application/pdf，真 anchor 點擊前再核對 scope／document.hidden；失敗立即清 timer、URL、anchor，成功保留 URL 60s 供瀏覽器讀取後 revoke。已加入 usesBrowserDownload 與中英文文案：僅報已要求下載，不聲稱檔案已寫入磁碟。

81598 完整驗證成功52s：JS903、Wasm903 全0 skipped/failures/errors，各新增4 cases 使用真 Blob 並攔截 DOM download，確認 PNG／PDF encode bytes 全部完全相同、MIME／filename 正確、owned chunks（含0與所有high-bit bytes）不受原 buffer 變更影響、URL建立後失效零click／完整cleanup、abort／click失敗不可retry及無URL／anchor／timer殘留。Android main／iOS arm64 compile、Desktop compile 與 Web兩個Main compile通過。44702 JS及Wasm production distributions成功1m24s，複製 webApp/build/verified/{js,wasmJs}（本機產物，未發布）。git diff --check通過。

這是 Web binary download adapter 與接線驗證，不代替真 APP 畫布 GraphicsLayer readback／瀏覽器下載到磁碟／重新開啟 GUI 驗收。現有 Desktop 真Compose rendering及實際保存證據另列；下一步 iOS export adapter、Web／Android physical rendering／存檔驗收、大圖original/tile品質與媒體真來源。原始六主線保持完整 scope／active，AI最後真失敗401未排除，GIPHY仍display-only及保存批准／provider-node同步未完成，未改backend/env、未提交/push。

2026-10-08 Android 匯出實作：新增 androidCanvasExportRuntime／StagedAndroidCanvasExportDestination，MainActivity 追加 PNG 與 PDF CreateDocument launchers（保留原三個 launcher 註冊順序），跨 picker 單一 in-flight fence，savedInstanceState 恢復標記，onDestroy 取消 waiter，取消競態／遲到 URI 只清可確認的新空白文件。CanvasExportRuntime 已注入 App，沿既有畫布選單／dialog 提供相同全畫布、所選、倍率、透明背景。

編碼先寫 App 私有 cache stage、flush＋fd.sync，最後確認 scope 與 document 空白再分塊複製。每塊檢查取消／scope；SAF provider 不保證原子發布，因此 publication 開始後若失敗或 scope 改變保留不確定輸出供檢查（UI 報狀態不確定），abort 只清私有 stage，尚未發布時僅刪已確認空白文件，非空／不可讀文件不刪。32MiB stage 上限涵蓋目前 pixel 限制的 PNG／PDF 最大編碼大小。崩潰後 cache stage 回收與實際供應商保存／durability 未驗收。

52305 最終 build 成功14s：整套 Android Host 900 tests，0 skipped/failures/errors；其中新增5 tests 覆蓋 multi-chunk staged delivery、scope 失效後清 stage／空白 URI、非空文件零 overwrite/delete、provider partial failure 保留輸出、publication 途中 scope 改變停止後續 chunks。Android assembleDebug 通過，APK 保存 androidApp/build/verified/BoarderLess-debug.apk。這些 tests 使用真 Java file stage 與 fake document provider，不代替 ContentResolver／系統 picker／旋轉重建真機驗收；adb devices 無裝置。尚未接 iOS／web export adapter，六主線 goal 保持 active，AI 最後實際401仍未排除，未改 backend/env、未提交/push。

2026-10-08 Desktop 匯出入口及實際保存驗收：CanvasExportDialog 已接 Main→App→WorkspaceScreen，原生 File／命令搜尋／App 畫布選單均有 PNG／PDF 入口。Dialog 支援全畫布／所選、0.25/0.5/1/2 倍、透明／畫布背景；preview result 綁定 scene／measurement／media frames／theme／density／physical window profile，每次 profile 改變建立新 state，保存前及各階段核對 state identity 與 capture scope。圖片與 GIF 載入 scope-authorized preview（GIF 第一影格），影片使用已保存 poster，缺少素材明確失敗。atomic commit 開始後若失敗報發布狀態不確定，保留已發布檔案。

獨立 QA home／記憶體 preferences 的新版 Desktop 實際從畫布選單保存 PNG 和 PDF，兩次顯示成功。Pillow 解碼 PNG 1472×2224；pypdf strict 確認單頁及全部 RGB stream 與 PNG 完全一致，縮圖親眼檢查全畫布三個重疊時序圖及圖片。2× 超限顯示尺寸錯誤並停用保存；0.5× 透明 PNG 實存 784×1160，alpha 0..255、角落透明。所選圖片 preview 244×224 已驗（未另存 selection 檔）。匯出 dialog 關閉回到畫布，canvas record bytes 與原 fixture 完全一致。QA 透過 Quit 退出且 inventory 確認停止；未關閉使用者原 Pen QA。CUA 檔名欄位 setValue／鍵盤貼上未生效，保存使用原生預設檔名，不據此聲稱鍵盤操作驗收。

79561 最終完整 build 成功 1m21s：JVM1009（skip6）、JS899、Wasm899、Desktop48，全零 failures/errors；Android main、iOS arm64 compile、Desktop distributable 通過。ready bundle 更新自正式 distributable（不含 QA preferences），diff check 通過。尚未完成 Android／iOS／web destination adapters、大圖 original/tile 品質、影片 poster／GIF 真素材 GUI、極端字體邊界及跨裝置正式多人驗收。AI 最後實際 HTTP401 仍未排除，六主線 goal 保持 active，未提交/push，未改 backend/env。

以下為先前批次紀錄；目前狀態以此段與 CANVAS_EXPORT_IMPLEMENTATION.md 為準。

2026-10-08 完成匯出場景／離屏錄製核心：CanvasExportCapture deep-freeze owner及完整workspace，role／user／client／workspace／server seq／epoch／read-ready全檢查；群組包含descendants、standalone selection解除外部parent但保持version，部分managed sequence拒絕。共用MeasuredRelationLabels與live renderer，actual TextNodeCard／GroupFrameCard／RelationCanvas／RelationLabel／WorkspaceSequenceScenes直接用於export；MediaPaintBounds由live media與verified-frame export共用。測量包含floating text local offset＋owner rotation、Bezier control hull及round stroke outsets、self/manual route／arrow／label，sequence內部物件只用semantic scene，缺少media frame明確拒絕。CanvasExportPreview固定量測完整輸出尺寸、縮放panel預覽、使用DrawScope的GraphicsLayer.record橋接draw context後toImageBitmap/readPixels；修正首版能看到preview但readback全空白的實際錯誤。新增common Capture3cases／PaintGeometry2cases及JVM真Compose scene1case，jvmTest新增host Skia runtimeOnly依賴。縮小240×180預覽的1030×770 readback與完整scene直接繪入sRGB bitmap，在相同physical window shadow lighting條件下逐ARGB像素完全一致（changed0/max0）。不同window尺寸或untagged Skia surface不作byte-exact參照。

實際PNG／PDF fixture親眼查看：floating中文紅色serif／underline標籤、旋轉outline、Bezier超出viewBox及stroke、left self-loop＋manual route／label、旋轉圖片、Alice/Bob Request/Done時序圖全出現；pypdf strict驗1頁1030×770，PDF RGB stream全像素等於PNG，PNG全opaque所以無SMask；Poppler渲染親眼查看，reader有image邊界平滑差異。是合成資料的真Compose rendering，不是已接APP按鈕或真original來源下載。17786完整build成功1m18s：JVM1009（skip6）、JS899、Wasm899、Desktop48，全0failures/errors；Android main／iOS arm64 compile及Desktop distributable通過，ready bundle更新，diff check通過。

下一步：CanvasExportDialog／Main→App→WorkspaceScreen entry、verified image/GIF/video frozen frame來源與明確規則、各端destination adapter、實際APP圖文全畫布／selection匯出及重開驗收。Preview result須綁renderer profile（palette／density／window metrics／scale／frame identity）；profile變動時清舊raster或顯示凍結bitmap，避免新preview配舊pixel結果。超大font／italic／shadow邊界及各端physical rendering未驗，不以窄fixture代替全範圍驗收。完整六主線保持active，不標goal完成，未push／未改backend/env。


2026-10-08 匯出底層新增交付：CanvasExportRaster frozen straight ARGB（1..4096／4,194,304px限制）、portable RGBA PNG（zlib stored blocks／Adler32／CRC）、單頁RGB＋alpha soft-mask PDF及xref／Length、實際painted bounds的export layout（外側文字／route等由renderer測量提供）、背景編碼／1MiB chunk／fresh guard／cancel與NonCancellable abort。Desktop重用force＋atomic new-file destination，新增PNG／PDF picker runtime（尚未接Main／APP UI）。9個新增cases驗 frozen input、超限拒絕、跨DEFLATE blocks的ImageIO exact ARGB及Java CRC32、PDF offsets／length、scope change／cancel原exception、actual new-file／既有檔保留／stale零final及無partial。實際QA PDF以pypdf strict驗1頁及所有RGB／alpha與PNG一致；Poppler渲染親眼驗方向與半透明sample正確，閱讀器邊界平滑差異有記錄。6342完整build成功1m35s：JVM1003（skip6）、JS894、Wasm894、Desktop48皆零failures/errors，Android main／iOS arm64 compile及Desktop distributable通過，ready更新。這是編碼／保存底層，不是APP完整畫布export完成；authoritative圖文／relation／sequence／media renderer、全畫布／所選Dialog／四端保存與GUI仍待接線。詳CANVAS_EXPORT_IMPLEMENTATION.md。typed AI safe log仍NO_SAFE_AI_LOG，最後實際401未排除，六主線goal保持active。未提交/push、未修改backend/env。


2026-10-07 本輪面板修正驗證完成：JVM method-too-large 以 non-inline WorkspaceCanvasSurface 分開 canvas body 與初始化／effects 解決，保留原 remembered state lifetime，未保留大範圍 state 搬移。77365 完整 build 成功1m31s：shared JVM 994（skip6）、JS browser888、Wasm browser888、Desktop48，全部零 failures/errors；Android main、iOS arm64 compile、Desktop distributable 通過；ready bundle已更新且無QA prefs fixture，git diff --check通過。

獨立新版 Desktop QA（新空白 home）實際確認 compact 佈局：筆刷畫一筆後未出現屬性 sheet，筆刷 controls 保持可見；status 在 toolbar 上方且不重疊。關閉筆刷後選取最後一筆，bottom sheet 出現真正 close，點擊 close 後整個 sheet 與選取 handles 消失；原生 File→搜尋可開啟搜尋文字欄。safe 本機 record checksum 通過，僅1個 persisted vector path object，沒有新增一串重複物件。CUA drag 仍僅產生短點；super+F、super+K、Escape 在這次工具操作皆未改 UI，故不把工具鍵盤輸入當快捷鍵實際驗收，完整自由筆畫／觸控、右鍵 pan、移動連線的 GUI 仍待驗。新QA已透過原生 Quit 退出，app inventory已確認不再執行；未關閉或覆寫使用者先前QA。正式 home 的 typed AI safe log 仍 NO_SAFE_AI_LOG，最後已知實際失敗 HTTP401 未排除。PNG/PDF、四端／六主線仍未完整交付，不標 goal 完成。


2026-10-07 本輪筆刷／輸入修正（尚在驗證）：native Desktop 筆刷直接繪製在 release 建立一個 vector-path TextNode，private preview 固定 owner／完整 baseline／viewport／樣式，儲存失敗保留 exact operation/id 供重試，單筆 Undo/Redo 與真正檔案重開已有測試。移除 save pending 狀態造成的 pointer coroutine 重啟。繪圖時保持零選取、不顯示 relation/object inspector；退出或 Escape 才選取最後保存的一筆；失去編輯權限關閉 mode。compact bottom sheet 增加真正 close（清除選取／編輯焦點），收合仍保留。Brush toolbar 使用 safeDrawing inset，回報實際排版高度；status 底部依高度+間隔上移，避免 narrow FlowRow 換行後重疊。搜尋新增 Cmd+F／Ctrl+F，原 plain F fit 及 Cmd+K 保留。新增 AreaSelectionLayer 保證可在節點上開始框選；框選時停用普通 viewport transform，secondary drag 改移動畫布。GridCanvas 更新 latest pointer callbacks，relation hit 使用 rendered preview geometry、14dp 近線判定，label 也可直接選取；common 兩個測試驗 endpoint move 與 active transform hit。這些互動尚未全部 GUI 驗收。

前一輪78473完整 build 成功1m30s；本輪搜尋＋面板新增改動碰到 WorkspaceScreen JVM method-too-large，正將 inline Box 的 canvas body 抽為 non-inline composable 邊界，保持原 state lifetime/delegates；尚未把舊測試結果當作本輪成功。獨立 QA app 使用新空白 home，不覆寫正在使用的先前 QA。PNG/PDF 匯出、四端筆刷與原六主線仍未完整交付，goal 未標完成；未提交/push、未修改 backend/env。


2026-10-07 使用者新增需求與目前文字／鋼筆進度：一般圖形及鋼筆新增九宮格、外側上／下／左／右、X/Y local offset、位置重設；文字浮動層不受shape／shadow裁切，不再clamp zoom，字體／offset跟owner transform一起縮放／移動／旋轉。新增可保存的TextPlacement／TextAppearance，fontSize1..512、weight100..900、#RRGGBB色值、System／SansSerif／Serif／Monospace／Cursive字體類別、italic／underline／strikethrough。這是平台通用字體族選擇，未宣稱嵌入指定商業／自訂字型檔。文字顏色已接既有標準ColorPickerWindow，支援preview／apply／cancel恢復原色；字體样式與位置分開reset，重設位置保留樣式。Inspector本機畫布入口與Pen編輯器共享設定；Pen preview及canvas共用style。新增／重編Pen同一transaction包含文字／樣式／path，label-only編輯不normalize或改transform；retry時固定首operation與內容，draft close檢查文字設定變動。

鋼筆新增對齊輔助線／吸附獨立開關；新增或拖曳anchor／控制點，以6dp屏幕距離轉path units找其他anchor與viewBox中心的最近X/Y，排除被移動anchor；anchor吸附保持incoming/outgoing相對offset，一次gesture一筆Undo，cancel／多指仍捨棄private preview。新增common3cases驗全部外側／oversize text bounds、無效offset/size/weight/color拒絕、吸附grab offset及control points／一次Undo、label-only geometry保持、Clipboard v8保留位置及完整appearance／UndoRedo。native既有Pen persistence兩cases延伸真正重開、方案保存與UndoRedo保留樣式。attrs變更（color／shape／layer／lock／AI layout）及merge operation保持文字設定。Clipboard帶新設定升v8，普通v6與sequence v7仍維持原wire；v7夾帶設定拒絕、未来v9拒絕；native方案reader/store接受v8。remote服務尚未協議TextPlacement保存，遠端Inspector不顯示新設定且paste先擋，draft merge列明contract gap，不宣稱已支援server／四端跨装置保存。

實際GUI只驗到新版Pen入口、對齊開關、展開位置／font欄位及選項的可見版面。AX點文字欄位＋paste未改值，另一次使用者切換app造成工具提示需重新讀取，已重讀；未把這些動作當成填值／吸附線／外側標籤／zoom旋轉／標準picker互動已親測。private草稿已關閉、QA已退出，QA canvas與上一輪15物件檔byte-exact未變。完整26996 build成功1m33s：shared/jvmTest=988 (skip6)；shared/jsBrowserTest=883 (skip0)；shared/wasmJsBrowserTest=883 (skip0)；desktopApp/test=48 (skip0)，0failures/errors；Android main／iOS arm64 compile及Desktop distributable通過。ready bundle更新、無QA prefs fixture；shared SHA256 f0a3686cb374e31688549c0126d4b8b187c9a02cc87c2d9e394853ef2554e67e. AI理解Dialog現在Send立即記typed Preparing，重複stage去重；headers／client構造在probe之前失敗及dispose後取消也記typed終態到safe log；沒有原始錯誤、key/body。未實際連線Router成功、最後401仍未排除。

新增且尚未完成的明確要求：PNG與PDF匯出（全畫布／所選物件）、畫布直接筆刷自由繪製並在放開後成為可移動／旋轉／復原的一個物件；後續須接真正file export／完整文字與媒體與sequence scene／UI及重開驗收。不得把現有Pen anchor編輯器當作此筆刷功能已完成。六條主線及這些新增要求持續，goal active，未push、未改backend／env、未要求legacy測試資料migration。


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

2026-10-07 時序圖 clipboard／方案 v7 已接線：workspaceClipboardPayload 只允許完整受管圖（root＋全部 participant/block object bindings），部分 leaf/block 選取拒絕而不輸出普通圖形替代；v7 帶 sequenceDiagrams list及relation originalId，普通內容維持v6且新empty/null欄位以EncodeDefault.NEVER省略。嚴格核格式／版本／object-relation limits／duplicate relation IDs／完整binding labels、端點、parents、self route及metadata ownership；v1-v6不能夾帶新metadata，未來版本仍拒絕。QuickScheme reader／native atomic library開放v7，transfer仍schema1 wrapper、內含明確selection.version=7及完整payload；strict duplicate JSON／Unicode reader與1MiB剪貼簿輸入上限。沒有legacy migration或新server契約。

ClipboardInsertion 把既有 materialization／group ordering 移出WorkspaceScreen，純prepare先分配全新object及relation IDs，重配container／participants／blocks／messages bindings、保留語意 IDs／tree positions、全部world positions及relation geometry同offset、zOrder進目的畫布頂層。CreateObjects→CreateRelations→metadata complete CAS同一transaction，一次published workspace/history；不支持sequence workspace在送交前拒絕，server capability仍false。成功後只選seq roots及普通物件，避免managed leaves使後續reuse被擋；execute失敗不改selection／history並存首筆ClipboardPasteDraft，scope modal阻ordinary輸入，明確重試同一op及IDs／close，重試核owner/user/client/workspace、role/version/seq／完整baseline／asset availability。尚非app quit後的專用paste retry journal；原native receipt/file recovery仍保留atomic old-or-new且exact op idempotent。

QuickSchemePreview拆出，ordinary縮圖排除seq glyph placeholders／message routes，使用WorkspaceSequenceScenes直接讀bindings畫headers/lifelines/messages/branch frames；ordinary和seq用相同fit viewport，納入rotated sequence root corners；media仍沿既有verified destination gate，序列和media capability分別提示。小縮圖不宣稱具備全文閱讀／zoom功能。copy/cut/duplicate/save scheme與既有native Edit／palette／方案入口共用真正v7 payload及pure insertion；whole cut仍用deleteSequenceAware原子clear metadata＋delete。

新增common SequenceClipboardTest6項與native DesktopSequenceClipboardTest2項：完整v7／partial零payload／ordinaryv6 wire omission；same-workspace duplication全部新bindings、self routeoffset、root-onlyselection／一次version及trueUndoRedo；缺label/participant/relation／duplicate ownership／missingID／downgrade零identity allocation拒絕；scheme export/import保留角色、完整participants/messages/blocks tree、另一workspace insertion；wholecutUndo/paste；frozenretry身份／撤權／scope／snapshot拒絕。native scheme library重開→filetransfer→destination純prepare零filemutation→save/reopen/UndoRedo bindings一致；全部AtomicDraftStage faults exact paste retry一receipt。首compile僅缺新增Strings.sequence.unavailable已補，沒有放寬驗證。

六主線goal仍active。自動account-scoped方案同步、正式server／四端physicalGUI、sequence structured wizard／existing editing等仍待交付；本輪v7是完整metadata重用及明確檔案交付，不以它代替自動跨裝置同步。未動backend／.env／未做測試資料migration；actual AI401仍未有新live證據。

最終隔離 build 33295 成功1m48s：JVM973（6既有skip）、JS870、Wasm870、Desktop48全部0failures/errors；iOS arm64／Android main compile／Desktop包通過。ready desktopApp/build/verified/BoarderLess.app已更新，shared SHA256 67ab4ad79c67779004f332719d745a4d28c256e56dd61c2c60f61ffec225a03e；核 ClipboardPayload／Insertion／WorkspaceClipboardSelection／QuickSchemePreview／ClipboardPasteDraft classes，无QA prefs fixture。格式工具僅對4個抽出檔案autoformat，仍回報既有風格一致的 wildcard imports／一個長行，不把format回報當成lint全通過；git diff --check通過。

同production jars＋獨立QA home/memory prefs GUI：選root「再製」成功，workspace10→11，1→2diagrams，各3participants／8messages／2blocks，原metadata不變、只增一receipt；native EditUndo到12，objects／relations／sequence metadata與before完全相同；Redo到13恢复相同diagram IDs／bindings。Root「儲存為方案」生成native library一項schema7／payload7／8messages，不改workspace13；快速方案顯示v7及3participants／8messages場景縮圖，8訊息與Alt/Loop frames可見但縮圖文字很小。按「插入」到workspace14／3diagrams，各完整bindings。Native Quit後新開視窗Undo空，3scenes及scheme縮圖／payload7重開可讀；canvas與scheme record bytes均與插入／save後exact一致。最後native Quit正常結束且process不存在。GUI沒有操作使用者clipboard、沒有AI請求、沒有修改使用者app/auth/prefs。未做GUI故障注入或native Copy/Cut快捷鍵驗收；其serialization／wholecut與實際同paste helper有common及native檔案覆蓋，不誇稱全部physical GUI已驗。

2026-10-07 AI 時序理解與草稿受管邊界：驗證包沿既有 flowchart／N-E inventory 增加 EXPLICIT_SEQUENCE_CONTEXTS；只從 authoritative bindings 提供 participant role／label、message kind／dashed／source-target／label、block kind／branches、parentBlock／branch／local order。所有引用採本次 N/E/S/B aliases，無原 UUID／asset IDs；partial scope 只列已授權 object aliases、complete=false，不擴大授權或輸出未選取語意。Parallel branch 順序明確不代表執行序列；普通 cycle 不推導時序。回應須逐項回傳完整 context 作 metadata fidelity 核對，缺項／順序／branch／kind 錯誤拒絕；並改採 strict JSON duplicate-key／Unicode parser。此核對不等同自然語言理解分數或真 Router 成功。

AI tool approved graph 共用相同 scoped contexts，sequenceReadOnly 標示並宣告 sequenceDiagrams=false；update／layout／parenting／group children／create relation 對受管 root、participant、block、其內物件及會移動受管 descendants 的一般 ancestor 拒絕 Unsupported。工具 reducer 後再次核 sequence metadata／受管 objects／relations／ancestor 未改；final review 另獨立比較，手工構造的可執行 layout proposal 也拒絕。普通範圍外 text tools 繼續提供 isolated proposal。AiUnderstandingDialog 的 finally 與註解誤在同列造成 compile syntax error，拆開後每次 attempt 包含 preflight rejection 都消耗 consent 並釋放 busy；不自動送出新付費 request。

新增 common AiSequenceSafetyTest 五例：Actor／Call-Return-Async-Signal／Alt-Loop-Parallel 和 explicit positions、deterministic alias／完整 acknowledgment mismatch、partial privacy／ordinary unchanged、duplicate JSON／malformed UTF-16 拒絕、五種 tools 零 proposal 拒絕與 outside edit 成功、手工 proposal 最終拒絕。首輪 22 targeted tests 僅 Unicode test 期待 IllegalArgumentException 而 JVM strict parser 實際拋 MalformedInputException，改成核「必須失敗」保留 production Unicode 拒絕行為。完整六主線仍 active；actual user-dir 安全 log 尚不存在，最後 HTTP401未排除。未動 backend／.env／legacy migration。

本輪最終隔離 build 69259 成功 1m42s：JVM965（6既有skip）、JS864、Wasm864、Desktop48，全部0failures/errors；iOS arm64／Android main compile／Desktop distributable通過。ready bundle已更新，shared SHA256 7b7cf2c8f71d454171699c9b05ec8a408bfa35c97889ea7ad64e6b04c966fe15，核 AiSequenceContext／AiCanvasPacket／AiDiagramDraft／AiDiagramReview classes，无QA prefs fixture。GUI 同一 production jars＋隔離 home/memory prefs，在AI理解視窗明確選QA合成全畫布，scroll見 EXPLICIT_SEQUENCE_CONTEXTS complete=true、3participants、8messages、Alt/Loop positions；始終「尚未送出AI請求」，API設定空白、下一步disabled，取消及native Quit正常關閉。QA document bytes與after-sequence-redo.record完全一致；沒有network request，不能用此宣稱真Router401已解決。git diff --check通過。

2026-10-07 時序圖顯示與建立入口已接線：SequenceDiagramScene 直接讀 authoritative metadata，顯示參與者 headers／actor標記、dashed lifelines、順序訊息列、call/return filled heads／async open heads／signal無箭頭、非零 self-call、alt/loop/par frames及branch separator。TextMeasurer完整量測／換行 labels，不省略文字；message與branch row spans及header高度隨量測增加。投影共用container的非等比scale／rotation／viewport；保存時採同一measured layout配置root／header／block bounds。一般nodes／block frames／relation paths及labels排除managed bindings，不重複繪製；一般hit test也排除舊message route，managed leaf selection禁一般mutation。Root的普通fit-to-children／ungroup禁用，避免把只有headers的邊界當整張時序圖；root仍沿既有group選取／移動／保存。

命令面板「建立時序圖」僅native capable workspace提供：空白source→明確載入示範或自己編寫→strict subset驗證／line diagnostics→完整preview→本次confirmation→single exact transaction。取消／preview零保存；第一次save preparation後凍結payload／IDs，失敗明確retry，不讓改內容重用identity。live role／完整workspace snapshot／version／seq／client-user／queue／recovery／connection guard再次檢查；modal納入workspace-scoped flags與pointer／keyboard／presence阻擋，撤權關閉，切scope重置。成功後fit新圖。尚為Mermaid authoring flow，不把它當逐步participants/messages/branches表單精靈或既有diagram editing已完成。

WorkspaceScreen碰JVM MethodTooLarge後，新增SequenceCanvasMembership把binding分類／一般view篩選拆出，再將完整palette builder移到WorkspacePaletteEntries＋read-only context snapshot。保留既有command IDs／順序／guard，invoke仍讀live state。曾誤將dialog插進LaunchedEffect、fit傳IntSize而非CanvasSize，均由compile揭露並修；沒有關閉compiler檢查。額外保護root title不與participant header重疊，禁fit/ungroup按鈕，source editor有有限高度。

GUI在自有private fake home＋memory PreferencesFactory QA bundle操作：native File→命令面板→建立→明確load example→validate preview。確認前插入按鈕disabled，preview file bytes完全不變。明確confirm＋Insert後實際畫布顯示3participants／8messages／alt两branch與nested loop／self-call；private record workspace7→8、只一新receipt，既有pen與loop完整不變。Native Edit Undo保存version9、exact原objects/relations、移除sequence；Redo保存version10、相同metadata／bindings／geometry，object/relation versions依restore正常增加。Quit／reopen讀回同一圖，memory prefs不保留camera所以再按「符合內容」檢視全圖；reopen／view-fit文件bytes不變。QA正常結束，沒有動使用者實際app／auth／prefs，沒有AI network request。對長標籤／手機遮擋、pointer rotation/scale、actor/par/async的真GUI、VoiceOver逐項navigation尚未驗；目前a11y僅group及scene counts。Preview圖較小，尚需專用zoom/pan controls。Metadata-aware copy/cut/duplicate/scheme仍拒絕payload，既有reuse按鈕部分仍顯示enabled而無動作，需正式接線／狀態修正。

64250最終隔離build成功1m24s：JVM960（6既有skip）、JS859、Wasm859、Desktop48均0failures/errors；iOS arm64／Android main compile／Desktop包通過。新增common scene3cases：variable row/branch/header保留IDs與完整message、container非等比90°旋轉／viewport投影、managed membership排除duplicate glyphs並禁partial mutation／保留root及outside object。ready bundle desktopApp/build/verified/BoarderLess.app已更新並核Scene／CreationDialog／Membership／PaletteContext classes，shared SHA256 a2475c702d79755140b785a8c206d21b72cc36e6c20791dc5994dd0e2b68f35c，无QA prefs fixture。此源碼／回歸包與GUI採相同production jars。

完整六主線goal維持active；時序剩餘structured wizard／existing editing／clipboard-scheme／正式server與跨端驗收，其他主線不以此取代。actual user-dir AI安全log仍不存在，最後真HTTP401未排除，不宣稱實際Router可用。未動backend／.env／未做legacy migration。

2026-10-06 時序圖正式本機保存接線：Workspace 新增 sequenceDiagrams＋單調 sequenceDiagramsVersion，普通文件 default fields 不輸出，保留舊 ordinary snapshot／receipt shape。SequenceCanvasDiagram 採 boarderless.sequence-canvas.v1 扁平 participants/messages/blocks／parent block＋branch index＋contiguous order；明確綁定 container GroupFrame、participant TextNode、message Relation、block GroupFrame IDs。toDraft 回建完整巢狀語意，不從位置推導、不只保存 Mermaid。schema／unique IDs／missing parent／order gap／unreachable cycle／完整端點／object-key identity／root ancestor cycle／label／direction／self-message route 驗證；受控 bound renderer 尚未接線。

SequenceCanvasCreation 凍結 workspace maps／metadata，配置一次 root、各 binding 與 operation IDs；先準備完整 preview，未保存零 I/O。CreateObjects→CreateRelations→UpdateSequenceDiagrams 在單一 transaction 中建立，final graph 驗證後才一次 workspace revision／history；metadata CAS 核 full before map＋version，不自動 rebase內容。逆交易所需的 metadata/label 暫態僅允許 transaction 內部，最終狀態完整驗證；standalone metadata錯 label拒絕。參與者 rename＋metadata 的同交易Undo/Redo已測，metadata版本單調，canvas object／relation restore版本依既有history增加且binding IDs不變。

Native FileRepo supportsSequenceDiagrams=true表示保存能力，未開wizard入口／未宣稱renderer已完成。read/write 完整binding驗證；native reader改用既有 strict JSON duplicate-key／Unicode parser，sequence structured-map在Map解碼前拒絕重複root IDs／缺 schema／錯 shape；checksum正確但bindings損壞亦拒絕，原文件不reset。16層semantic tree以flat metadata保存，通過既有64層JSON guard，沒有放寬全域深度。partial ordinary object/relation deletion拒絕；whole diagram deletion先clear metadata再delete完整cascade，同一Undo恢復全部。History及transaction final-state guard阻止一般文字修改使metadata脫節。未接metadata-aware clipboard／scheme，因此既有selection export/cut/duplicate/library入口不產生會丟失語意的payload，保留原圖；尚需正式支援。

Backend supports flag保持false；metadata操作含nested transaction在draft append／DTO前拒絕，不配client seq、不HTTP。state raw sequenceDiagrams／sequenceDiagramsVersion字段（含null）明確contract error，不ignoreUnknownKeys抹掉。merge review/compiler遇到metadata明確拒絕，列SequenceDiagrams gap；正式merge／clipboard／projection合約尚待接。唯讀backend/src再查無sequence/canvasStyle/quick-scheme新合約，未修改或啟動backend／.env／使用者資料，沒有legacy migration工作。

測試新增 common7＋native3：原子create/full binding/serialize／history remote restore versions、stale CAS／partial delete／ordinary rename rejection、whole delete＋UndoRedo、flat schema/order/cycle/duplicate/raw schema拒絕、scope/ACL/snapshot/bounds／ordinary field omission、正式Backend零draft/seq/HTTP、atomic rename逆序metadata校驗；native preview file unchanged→save/reopen→UndoRedo→delete/UndoRedo/reopen、三file checkpoint exact-op同receipt重試／same ID異內容拒絕、16層保存／valid-checksum corrupt refs保留。初次native fixture local save helper漏suspend已修；18400完整build僅JS surrogate fixture失敗，生成JS把孤立surrogate字面值轉成?，改CharArray runtime建真正非法UTF-16，production validation未放寬。62608最終完整build成功53s：JVM957（6既有skip）、JS856、Wasm856、Desktop48均0failures/errors，iOS arm64／Android main compile及Desktop新包通過。ready bundle已更新核SequenceCanvasDiagram／Creation／UpdateSequenceDiagrams／strict metadata decoder classes，shared SHA256 93a5ae85bf55d8dd1e2d7446318438b086907e97b2ef8e4f769e2da1bb01c41d；无QA prefs fixture。

此輪是正式原生保存／history交付，未完成專用renderer、建立精靈／editing UI、sequence-aware clipboard／scheme、server跨裝置／四端GUI／OS kill驗收。沒有新實際AI安全log，最後使用者HTTP401未排除；AI sequence capability不宣稱已支持。完整六主線goal仍active。詳 [SEQUENCE_DIAGRAM_IMPLEMENTATION.md](SEQUENCE_DIAGRAM_IMPLEMENTATION.md)。

2026-10-06 時序圖語意核心與 Mermaid subset 接續：新增 explicit participants／roles、獨立 message step IDs／call-return-async-signal／self endpoints、alt/loop/par branch tree 與 containment。bounded schema／Unicode／端點／unique identities／nesting／capacity validation；capture caller lists 深拷貝。MermaidSequenceAdapter 定義 plain-text dialect v1，unsupported syntax／config／links／activation／notes／wrong branches／未閉合 blocks 常數 code＋line reject，不交付 partial draft。source64KiB／2048lines，支持 explicit/implicit participant order、六 arrow kinds、巢狀 alt/else、loop、par/and；export 純 tree，不宣稱 Mermaid 外部 reimport 保留原 message IDs 或 full AST。新增依 named branch 的 insert/reorder helpers，以及 dedicated lifeline/message/self route/block row layout plan，parallel bands 不當 execution time。尚未 workspace identity binding／正式operation／durable保存／renderer／wizard入口，因此時序圖主線仍未交付；不把ordinary graph偽裝成sequence。

8 common cases focused通過19s；63170 full隔離build成功1m32s：JVM947（6既有skip）、JS849、Wasm849、Desktop48均0failures/errors，iOS arm64／Android main compile及Desktop包通過。官方Mermaid12.1.0 parse在temp strict runtime接受3組語法case（synthetic Transport/Stateful two branches＋loop/par、implicit/repeated/self、Unicode＋six arrows）；只是syntax acceptance，不取得AST，不代替正式產品renderer。沒有加入product npm依賴。ready Desktop包已更新並核 core classes，shared SHA256 f923603cc6493e47bd8b3360e5a7dd32f6eafb422b743adde2fb032c1be857c7。詳 [SEQUENCE_DIAGRAM_IMPLEMENTATION.md](SEQUENCE_DIAGRAM_IMPLEMENTATION.md)，列下次publication／history／UI接線條件。actual user-dir AI safe log仍不存在、真401未排除；六主線goal active。

2026-10-06 鋼筆原件重新編輯與 Desktop 真 GUI 驗收：選取已有 vectorPath 的節點後，屬性動作「編輯路徑錨點」載入完整單一輪廓及 paint。明確 Apply 更新同一 object ID、保留連線；bounds／stroke padding 正規化補償原件非等比 scale 與 rotation，Transform＋attributes 置於同一 transaction／一次 workspace revision／一次 Undo。未變更關閉不重寫原路徑（包含 implicit Close／quadratic control ownership 的 canonical 差異）；preview／cancel 不保存。capture actor／完整 snapshot／version／seq，node 及全祖先 unlock 守衛；compound、超過 1024 anchors、missing/cycle ancestor 拒絕，不截斷原件。首次保存失敗凍結 exact operation 供同 ID retry。抽出 PenPathNormalization、WorkspaceReuseActions 與八個原本獨立的 modal MutableState holder，保持同一 draftReviewScopeId reset 邊界；解決 WorkspaceScreen JVM MethodTooLarge，沒有放寬 compiler 或 guards。

真 GUI 發現並修正兩個缺陷。MacNativeMenus 原先 AWT EDT 同步讀／建立 NSMenu，AppKit accessibility 同時 invokeAndWait AWT，隔離 QA thread dump 確認等待循環。改為有強 callback reference 的 Objective-C dispatcher，在 AppKit main thread 非等待／合併 latest 更新中完成所有 NSMenu read／construction／write；兩個 scheduling regression tests 通過，修後多次 menu／AX 操作即時回應、中文原生選單正常。細長 open retracing quadratic 的兩個 self-loop ports 被 centre-origin ray 選到同一點，GUI 建立前 guard 拒絕而沒有保存。改為 outside→inside vector contour ray；miss／same-port 時以 bounded flatten actual points 取不同 ports，flat tangent 外側 corners 保持非零跨度，旋轉與非等比縮放重算；四側／退化 stroke／triangle 測試通過。這仍是 contour／stroke centreline 近似，不宣稱 exact stroke silhouette 或 arbitrary path 全避障。命令面板在 native capable workspace 改顯示正式插入 hint，server preview-only warning 保留。

用户解鎖後在自有隔離 QA bundle 驗證，不修改使用者執行中 app／文件／auth：另設 private fake home＋memory PreferencesFactory，production jars 相同，QA fixture 不進交付 bundle。兩個中心重合 anchors 加 quadratic control 形成非零 stroke；插入→既有路徑重開編輯→curve／stroke2→8→Apply→native menu Undo／Redo→Quit／reopen。核对 private record：preview bytes 不變、同 object ID、單 revision／receipt、Undo／Redo path＋transform 恢復；重開讀回已編輯原件。修正版在同一極薄 path 建立實際可見 loop→Undo removes relation→Redo restores same ID／geometry（version 正常增加1→3）、原 node 不變→Quit／reopen loop 仍可見，reopen file bytes 不變。插入節點 review dialog 可開啟，取消無保存；CUA 文字輸入／paste 未生效，因此沒有宣稱完成該 GUI 提交流程，也未宣稱 physical pointer／touch drag、四端 GUI 或 crash-kill 已驗。Undo stack 目前不跨 restart 保存。

44318 最終隔離 build 成功1m55s：JVM939（6既有skip）、JS841、Wasm841、Desktop48 均零 failures／errors；iOS arm64／Android main compile 與 createDistributable 通過。新增 common path editing6、native editing2、loop退化1、desktop menu scheduling2 cases；native tests涵 reopen／preview零保存／完整UndoRedo／三個file fault exact-op重試與receipt去重。已更新 desktopApp/build/verified/BoarderLess.app，核含 editing／normalization／modal holder／loop routing／native menu dispatcher classes，production無 QA fixture；shared jar SHA256 f53405cf20747cf65b4a1f31b543310c2417c3f875ad23ea50075a7942c30bb9。GUI owned QA 已正常結束，測試文件與安全 thread dump 保留在隔離暫存目錄。

六主線 goal 維持 active。尚待正式 Backend collaboration／receipt-fence live authority、素材跨端 materialization、方案簿自動跨裝置、共用背景正式同步、compound path／custom shape library／sequence participant-step-block 及 wizard。最後真實 AI trace 仍為使用者提供 HTTP401 contentType Other／HttpRejected；未新增實際 Router 成功證據，不把 transport tests 當驗證通過。不將尚未發布的 legacy／測試資料遷移重新列為前置。

2026-10-06 鋼筆正式本機畫布插入接線：TextNode 增加可選 structured VectorPath 自訂輪廓（既有 shape 為普通節點 fallback），attributes 包含完整路徑供 CAS／inverse／merge；Desktop FileRepo supportsVectorPaths=true。Pen editor 增加明確插入；捕捉 actor／workspace snapshot／version／serverSeq，queue／recovery／連線與 scope 守衛；预覽、取消無 retainDraft。首次已準備 insertion identity 固定，保存失敗保留 exact operation，editor fields／pointer 暫停；非法／不可見／超大／過度複雜路徑仍可修改後重試。插入沿 execute→retainDraft→history／queue，single CreateObjectsOperation，path 不當 SVG markup／URL／media asset。控制點與 stroke bounds 正規化為非零 viewBox，拒絕 renderer geometry budget 外的路徑。WorkspaceScreen 達 JVM method 上限，鋼筆 consent／capture／retry 元件拆到 PenCanvasDialog，沒有關閉編譯檢查。

自訂輪廓使用現有向量 command renderer／fill-stroke hit test，node move／resize／rotate／group／lock／text label 沿現有操作保留 path；shape presets 是明確轉回 ordinary shape，Undo 恢復 path。primary color 修改 fill 或 stroke，原 style 其餘保留。Auto／manual／loop ports 使用 vector contour；stroke silhouette 仍為 contour／centreline近似，未聲稱 exact stroke offset。selection 升 v6，舊 v1..5 禁止 vector payload；方案檔與本機方案簿支持 v6，路徑 local coordinates 隨 node transform 搬移、preview 畫 vector。Clipboard explicit nulls 必須保存關閉 fill／stroke，否則解碼會誤補預設 paint；本輪 native reopen 測試發現並修正此真實問題。方案簿新增 strict payload／版本一致驗證；v5 loop fixture 改為明確 v5，future rejection 改 v7，不放寬 guard。Backend 在 append／DTO conversion 前拒絕 vector create／delete／attrs（nested 亦拒），projection 含 vectorPath（包括 null）reject，merge 有 VectorPath contract gap。AI graph 標 custom-vector 且 tools 不編輯 unsupported path，pen capability 維持 false。

保存重試補強：Desktop FileRepo retainDraft 現在可核對已發布同 ID／digest／after version／完整 after workspace，再 force 既有 generation 而不追加或重寫；不同內容或任何較新的文件明確拒絕，因此 publication unknown 後原插入可明確恢復，不能 rewind 後續標籤／其他編輯。Native fault test 覆蓋三種檔案 checkpoint、跨 repo 重開、單 activity、同 receipt 重試、同 ID 異內容／較新文件拒絕。vectorPath=null 在 ordinary node／attributes serializer 明確不輸出新欄位，保持既有 operation receipt bytes；有 path 時則完整保存。向量節點零尺寸 transform，以及將 path 附到零尺寸普通節點，皆回傳 rejected，不讓 copy constructor 例外穿過 history。

驗證：48946 最終隔離全套成功2m1s：JVM930（6既有skip）、JS834、Wasm834、Desktop46均0 failures/errors；iOS arm64／Android main compile、新 Desktop app 成功，更新 desktopApp/build/verified/BoarderLess.app 並核 PenCanvasCreation／Dialog／Editor／VectorPath／FileRepo classes。Common PenCanvasCreationTest 8cases：控制點+stroke bounds／serialization、無圖形+超大+foreign拒絕、普通節點不新增null欄位、actor／ACL／完整snapshot／checkpoint守衛、attrs／轉普通shape與Undo／Redo／text／transform／zero-size拒絕+merge vector gap、v6方案檔+fill/stroke null完整保留與舊版本拒絕、Backend零draft／seq／HTTP、contour hit／rotation／relation ports；projection另增vector與null拒絕。Native2cases：preview檔案不變→真history插入→reopen→方案簿→Undo／Redo→reopen，以及三fault的同payload保存確認／不重複activity／異內容或較新canvas拒絕。初次compile漏imports／WorkspaceScreen method超大、fixture API名稱／nullable repo、旧v5 fixture constructor默认升级、null paint丢失均已修正；930無失敗。不是真GUI／真server／OS斷電驗收，六主線 goal 仍 active。

尚未 GUI 操作／實機四端／多人與 server vector protocol 驗收；已插入路徑重新開啟錨點編輯器、shape library 及時序 participant／step／block／Mermaid wizard 尚未完成。此交付不代替剩餘六主線要求，實際 Router 401 未排除，goal active。


2026-10-06 Desktop server-v1 原生保存接線：--server 改用 ~/.boarderless-storage/server-v1 的獨立新 namespace；native UUID／user/workspace metadata、草稿與原始 pending wire、receipt settlement、fence／刪除證據、共享 client sequence 都接到 private atomic file store。不讀舊 Settings／不搬移 server 測試資料，也不將 legacy migration 作交付前置。scope 首次登錄先寫 Preparing、建立並 force draft/safety/ledger，再寫 Ready；重新開啟會補完尚未完成的首次登錄。已登錄資料缺失則明確拒絕，不建立零序號／新 identity 覆蓋證據；跨工作區共享的 sequence 持續單調。Atomic store 建目錄後亦 force parent。ACK 仍先 safety 後 draft，各 record 原子發布，沒有宣稱跨檔 transaction 或實際 OS 斷電驗收。

驗證：31759 隔離全套 build 成功33s：JVM919（6既有skip）、JS825、Wasm825、Desktop46均0 failures/errors；iOS arm64／Android main compile 與 Desktop 打包成功，更新 desktopApp/build/verified/BoarderLess.app 並核 native storage／explicit engine／AI transport classes。新增 native bootstrap 5cases 覆蓋全新 metadata／client／draft／跨 workspace 序號重開、Preparing fault/reopen、已登錄四類 record 缺失不 reset、真 repository transport 回應遺失後 exact pending wire 明確重送、新 namespace 接受刪除 ACK 後 receipt／provenance 重開。Lost-response fixture 最初缺 JSON serializer 及錯用公開 retry fresh GET，修正為 typed JSON 與 internal retry persistence 邊界；不是 live ACL／server／GUI 驗收。追加 ACK fixture 初次漏 explicit type/respond imports 已修正，production guards 未放寬。現有 real-socket OkHttp tests 覆蓋401／503 Retry-After:0／redirect僅一個請求、逐次授權與分片中文 UTF-8 SSE。尚無新版真 Router 成功證據；最後使用者401未排除，六主線 goal 仍 active。


2026-10-06 Desktop本機連線geometry拖曳接線：選取普通線後顯示path handle，auto的中點只在跨過位移門檻並完成drag後建立manual waypoint；manual已有waypoints逐點拖曳。自循環外側handle沿旋轉後的side normal改extent，保持16..4096與同端點；所選label的實際measured bounds可拖曳，world anchor投影到實際source→target弧長，保存pathFraction／tangent／normal，既有manual與auto均不先跳到固定midpoint。長route／zero segments處理先找最近projection再驗完整placement，不因非最近segment的距離過大提前拒絕。

Pointer boxes只覆蓋handle或所選label區，沒有整張canvas pointer overlay攔截其他objects；handle在label重疊時優先。Touch slop是screen pixels（既有ObjectSelectionGesture），mouse任何實際位移；pointer down／click／未跨slop／拖出又回原點／cancel零operation。gesture捕捉user/client/workspace、完整content snapshot、relation/version、viewport與單一operation ID；move只更新working geometry，renderer／labels讀preview而history／檔案／queue維持原件。其他mutation與workspace change在gesture期间阻擋；撤權／scope／history／viewport／selection／recovery變化清preview，不auto-rebase。up才一次UpdateRelationAttributes→retainDraft→history→queue，Undo沿既有inverse。每個pointer cycle有token，move/end/cancel僅處理同token，第二pointer拒絕新begin；stale回呼不能清別的gesture或重複保存。begin的seed只有actual displacement才render，避免點中點時改auto路徑。

23056最後隔離build成功73s：JVM914（6既有skip）、JS825、Wasm825、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過並更新desktopApp/build/verified/BoarderLess.app，核含gesture／handle classes。Common6cases：auto handle整gesture一history及zoom world delta／out-and-back no-op、manual指定point與越界、旋轉loop extent/clamp/端點、長route/manual labelanchor／degenerate、owner/ACL/snapshot/viewport drift拒絕、screen slop／discard preview零正式變更。Native新增多次move時file不變→single commit→reopen→Undo保存且activity只一新增drag。尚無實機滑鼠／觸控／遮擋／accessibility驗收；formal server geometry支持仍false，沒有新增HTTP或更改其他agent backend。時序metadata／精靈、鋼筆正式canvas object、多人／跨裝置合約及實際Router401驗證仍未完成；最新safe log仍不存在，六主線goal active。原「直接拖曳尚未實作」歷史段落現在僅適用server模式與實機驗收缺口，不再覆蓋此本機接線。


2026-10-06 Desktop本機自循環與geometry接線：FileRepo支持RelationGeometry；選取一個text/media可明確建立自循環，重複loop逐次增加有界extent，不把普通node當時序participant。geometry採boarderless.relation-geometry.v1＋mode union（auto／manual≤32 world points／loop side+extent16..4096；label auto／manual fraction+有界world offsets），constructor及strict decoder拒絕錯schema／type／token／nonfinite／錯endpoint route。CreateRelations只在明確geometry時允許self-loop；零尺寸端點拒絕建立，已有loop的端點不可transform至零尺寸。RelationAttributes包含完整geometry，before-state／version guard與inverse保留路徑，delete/cascade只一份relation，native文件atomic保存與reopen無損。

renderer／hit test／labels共用relationWorldRoute：loop沿真shape外側不同ports並跟隨旋轉／移動／縮放，direction只改箭頭；manual world waypoints不隨node移動，邊界接段重算。Inspector提供四側loop、extent增減、輸入world waypoint list→小預覽→明確Apply、重設auto、manual label offset增減／重設；變更走既有history／queue，未Apply的文字不入文件。manual label按實際source→target弧長與world offsets重算，manual bounds加入auto label避讓。普通parallel edge在本機放寬duplicate guard，不推測時序；自動路徑可能重疊，可明確設定manual路徑，不宣稱parallel自動分流／完全避障。直接拖曳線條waypoint或label尚未實作。

選取／clipboard升v5，geometry與self-loop保存／複製，manual world points隨整體paste平移且越界不套用；v1..4仍可讀，舊版本不可帶新geometry／self-loop。方案簿native與transfer codec讀v1..5，geometry-aware縮圖包含loop外側路徑；ClipboardJson改strict unknown fields防抹除未識別資料。插入節點以同一route midpoint放置，explicit loop可拆A→N→A，新段重設auto、原loop snapshot／Undo geometry保留。Backend支持旗標維持false：UI關閉新入口／拒絕新clipboard幾何，draft append和DTO conversion前rejectcreate/update/delete/cascade（含nested transaction）；projection帶geometry（包括null）明確contract error，不忽略後繼續同步。AI工具capabilities未開loop／geometry，不能把前端能力當工具已驗。

56839最終隔離build成功62s：JVM907（6既有skip）、JS819、Wasm819、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過並更新desktopApp/build/verified/BoarderLess.app，核含geometry model／controls／routing classes。Common geometry7cases驗create／delete／cascade UndoRedo、world route／direction／node move／manual anchors、geometry update inverse／stale state、strict bounds/schema/union、v5＋scheme與loop插線、zero-size拒絕、Backend零Settings／零HTTP；projection新增unnegotiated geometry不丟棄。Native新增loop→geometry edit→reopen→node cascade→Undo→reopen。最初fixturebuild方法／HistoryResult undo／projection objects漏參數與舊v4 fixture被constructor升v5影響均修正，沒有放寬production guards。未做GUI／真server v2／OS kill驗收；鋼筆仍preview editor未正式canvas object，sequence participant/step/block metadata／Mermaid wizard未實作。實際AI401仍未排除，完整六主線goal active，不把本機geometry當正式跨端同步完成。


2026-10-06 快速方案簿檔案保存與方案檔交付接線：新增QuickSchemeRepository邊界；Desktop FileRepo選擇DesktopFileQuickSchemeStore，~/.boarderless-storage/local/schemes的一份private atomic record同時保存nextId與完整items，force／atomic move／directory force之後才回成功。ID不重用，改名／刪除原子保存、重開直接讀回；list可在文件尚未open前安全初始化，沒有搬移舊Settings或測試server資料。512items／payload1MiB／整簿4MiB，嚴格schema／UTF-8／unique排序IDs／nextId／provenance；checksum／unknown／incomplete內容拒絕且不reset原件。Settings方案簿留在server與既有平台路徑。APP保存／改名／刪除例外不當成功，嘗試fresh list呈現已發布內容並標尚未確認；初始讀取失敗有明確訊息與重試。

方案簿新增匯出方案檔、匯入方案檔→預覽→新checkbox確認存入方案簿；讀檔和審核不自動存入，也不自動插入畫布。四端沿既有JSON picker／delivery runtime：Desktop／Android新文件、iOS新folder file、Web只標download requested；iOS／Web改採呼叫者的安全suggested filename並加UUID，draft／scheme用途不混名。transferBusy與incoming review納入canvas／keyboard／presence阻擋，user/client/workspace／reconnect epoch失效取消publication與write guard；檔案選完後重新核定同一定義，取消／stale／read failure釋放destination，無HTTP。格式boarderless.quick-scheme v1包含name／完整versioned selection／sourceWorkspaceId，不帶本機scheme ID／auth／cache／picker路徑；目的library配置新ID。root／selection版本必填、unknown字段／duplicate escaped keys／dangling Unicode／depth／empty／oversize拒絕，1000objects／2000relations／64 hierarchy depth；既有AI strict parser抽出共用parseStrictJsonObject，沒有放寬AI協議。素材只保留source binding與reference，仍要verified目的assets才能插入，沒有複製原始素材或把引用當grant。這是明確方案檔搬移；不是user-scoped CRUD／outbox／自動跨裝置同步的替代驗收，BAI-006仍Blocking。

54700隔離build最終成功73s：JVM898（6既有skip）、JS811、Wasm811、Desktop46均0failure/error；iOS arm64與Android main compile、新Desktop包通過並更新desktopApp/build/verified/BoarderLess.app，核含native store／transfer codec／strict JSON classes。新增Native5cases：初次list／多文件共用／reopen／rename/delete與IDs、save/rename/delete×三file fault的完整old/new、corrupt／schema／missing／duplicate拒絕不reset、invalid/oversized零published write、兩獨立device dirs方案檔definition搬移＋canvas零改動。Common6cases：selection v1..4／fresh local IDs、strict不可信JSON／UTF8、object／hierarchy bounds、media provenance不授權foreign canvas、choose→fresh→write順序／browser request、cancel／stale／scope失效且dispose。最初媒體fixture錯用Image而非image已修正，production allowlist未放寬。尚未四端GUI／真檔案provider驗收，沒有新的AI user-dir安全log，實際Router401仍未排除。完整六主線goal active；不重新引入未發布測試資料遷移前置。


2026-10-06 背景文件保存與Undo／Redo已接線：Workspace新增strict CanvasStyle／canvasStyleVersion；預設Desktop FileRepo宣告支持。背景Apply沿execute→retainDraft→既有history／queue，完整style隨atomic文件保存，重開讀回；render依history style即時顯示Undo／Redo結果。viewport／showGrid／snap仍個人Settings，本機document mode不再把background回寫Settings，亦不自動搬移舊偏好。server mode支持旗標維持false，背景仍裝置偏好；正式Backend缺state／operation／capability，沒有發明endpoint或宣稱跨裝置同步。

UpdateCanvasStyleOperation有before-state＋style CAS；Undo／Redo只rebase版本並保留before guard，styleVersion單調增加，安全整數上限拒絕。schema1的default／warm／cool／lowercase hex、grid none／lines／dots與auto／hex嚴格驗證；grid renderer可讀這些正式樣式，spacing沿現有規則，未宣稱跨density合約已驗。picker捕捉user/client/workspace/style/version；scope／撤權取消、style drift拒絕Apply、Preview／Cancel零operation；未變更零operation。首次保存嘗試凍結同一operation/after，換token不能沿用identity。草稿三方合併增加workspaceStyle row與完整style比較，明確KeepRemote／UseDraft後產生新guarded operation；Backend contract gap阻擋未支持的樣式提交，retainDraft亦在任何append前拒絕（包含nested transactions），DTO conversion拒絕而不reserve seq／HTTP。

驗證：CanvasStyleOperationTest6cases（history單調／新identity、remote Undo與整數上限、strict serialization與transaction rollback、picker scopes／no-op／個人preferences、backend零Settings寫入／零HTTP、三方合併style conflict與新operation），FileRepo新增真Apply／Undo／Redo檔案reopen。50802最終隔離build成功79s：JVM887（6既有skip）、JS805、Wasm805、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過，更新desktopApp/build/verified/BoarderLess.app並核class。首次fixture漏canEditContent import、使用不存在MapSettings與漏TextNode transform已修正；既有contract-gap test以entries全量集合推斷舊3種改為明確3種，新增style gap另驗。尚無GUI驗收／正式多人樣式同步或方案簿CRUD；沒有新AI安全log，實際Router401未排除。完整六主線goal active，測試資料遷移不作前置。


2026-10-06 本機素材交付更新（覆蓋下方歷史的「local media gateway停用」）：新增DesktopFileAssetGateway並接到Desktop預設本機repository、WorkspaceScreen匯入與Desktop圖片／GIF／影片讀取。素材位於~/.boarderless-storage/local/assets，獨立private原始檔與atomic metadata；每次chunk≤1MiB、上限200MiB，不把原始檔或picker/cache路徑塞入workspace JSON。先完整驗size/checksum、force原始檔、atomic move與directory force，才發布Ready metadata。workspace只保存asset ID；重開素材庫可查詢／重用，刪掉picker原始檔仍能讀回。查詢檢查本機session user/client/workspace；下載經既有AssetDownloadCoordinator與sink checksum驗證，損壞／跨畫布／截斷不能出預覽。原始檔已落盤但Ready尚未發布的中斷，可在明確metadata refresh時驗證並settle同一ID，不重上傳；沒有完整原始檔的Pending不假稱Ready。本機進度使用保存／驗證本機檔案文字，server mode仍用Backend gateway。

DesktopFileAssetGatewayTest4cases：真正PNG來源→import coordinator→media node→retainDraft→新repo重開→刪來源→verified download sink→ImageIO讀回；跨workspace／foreignclient／path ID／checksum corruption拒絕；changed/truncated source取消pending且不留original/part；upload完成而confirm未執行的restart→明確getAsset恢復同一ID。最終34087隔離build成功74s：JVM880（6既有skip）、JS799、Wasm799、Desktop46均0failure/error；iOS arm64與Android main僅compile通過、Desktop新包成功並複製desktopApp/build/verified/BoarderLess.app，直接核含local repository／media gateway classes。尚未GUI操作驗收、未宣稱四端本機素材或跨裝置交付完成；GIPHY沿現有provider display路徑，不當作本機上傳素材。沒有新user-dir AI probe log，最後真Router證據仍HTTP401；完整六主線goal active，不重新引入測試資料遷移前置。


更新：2026-10-05（使用者要求更新 loop 與現有目標）。本次 get_goal 確認既有目標為 active，沿用其「依本文件最新執行清單完成六主線」範圍，不重建、不標完成。此文件是當前選工依據；下方歷史紀錄不覆蓋本節。

當前執行目標（使用者2026-10-06澄清）：APP尚未發布，伺服器全是測試資料；正式資料遷移不是前置需求。先交付可用的本機自動保存與重開還原，再完成六條主線。Desktop預設本機文件，正式伺服器流程保留明確--server入口；不搬移或刪除測試資料，不再以舊writer/catalog遷移門禁拖延離線保存。完整素材／多人receipt/fence／跨裝置方案簿／共用背景UndoRedo／AI工具／圖形連線時序範圍仍保留。沒有新增automation。

最近完整成功基線為74580（2026-10-06隔離build）：JVM876／JS799／Wasm799／Desktop46零failure/error，JVM6既有skip，iOS arm64／Android main編譯與新版Desktop包成功80s。新包desktopApp/build/verified/BoarderLess.app，包含DesktopFileWorkspaceRepository預設本機保存及明確OkHttp引擎。FileRepo5tests＋真socket HTTP2tests通過；尚非GUI操作驗收。這輪新增scope catalog接線與7tests已按使用者澄清移除；51663是撤回前的歷史基線。實際Router401仍未排除。

### 當前實際切片與下一步

- 2026-10-06 使用者澄清未發布／server僅測試資料後，移除本輪scope catalog及indexed capture接線和7tests。Desktop新增真正預設DesktopFileWorkspaceRepository：~/.boarderless-storage/local內一份atomic record保存documents JSON（active ID、client identity、完整workspace objects/relations、bounded local receipts），編輯retainDraft先force＋atomic replace，再發布history；queue submit只驗已保存operation digest並補force，local seq=0，沒有伺服器HTTP或假server ACK。重開直接讀最新保存內容，不依賴server；多文件create/open/rename與delete保留內容，Undo/Redo結果沿相同保存入口。App／WorkspaceScreen可注入CanvasWorkspaceRepository，狀態明標本機檔案／已保存此裝置，local不啟動remote polling。Desktop--server明確保留原測試server流程。尚未接本機asset導入，local media gateway停用，素材／GIPHY及正式協作仍待六主線後續交付，不宣稱全APP離線完整。

  HTTP確認依賴已為Ktor3.6.0＋OkHttp，而非JDK內建client；新增明確platform factory供AI probe/tools/SSE與backend／asset clients，Desktop/Android明指OkHttp、connection retry與engine redirects關閉，503 Retry-After數字0移除該hint防隱含second request；Ktor layer依caller followRedirects設定，AI維持false。backend metadata request30s／connect10s／socket30s，asset request10min／connect10s／socket30s，AI保留120s／10s／30s。iOS/Web保留既有原生／browser engine。真loopback socket（不是MockEngine）驗401／503含00／307僅一HTTP且不redirect、正常Chat SSE分byte中文emoji＋一次approval；fixture最初漏id/object而失敗，補標準fixture字段，不放寬production decoder。FileRepo5tests驗submit前保存最新queued edits／reopen identity、real historyUndoRedo／多文件、三file publication faults、stale/blank/changedID zero-overwrite、corrupt/unknown schema不reset。74580完整通過，newpackage已複製verified路徑；不是真paid Router成功或GUI/斷電驗收。下步優先本機素材與APP實際操作，維持完整六主線，不再盤點測試資料遷移。

- 2026-10-06 新版Settings journal／pending／stopped發布前接LegacyWorkspaceScopeCatalog：scope hash鍵＋canonical v1 entry先寫入並讀回，再發布chunks和manifest；256 entries／8192 characters／32KiB每entry，未知schema／key與scope不符／非canonical／count越限停止、不修原件，不刪ACK後／draft移除後catalog。captureIndexedLegacySequenceForMigration合併完整indexed和明確external legacy scopes，再用原inventories要求所有published manifests覆蓋，包含未送出／foreign namespaces；counter／archive／safety不省略，末尾recheck index抓observed drift。原沒有index的hashed chunks仍不能逆推，需要external scopes／legacy archive IDs；catalog不是writer exclusion、ACL或durable證明，native default仍未啟用。新增三runtime7cases：真writers順序／ACK與remove後索引保留、higher floor與unsent／foreign完整capture、old unindexed zero-write external補全、dropped／published-then-throw index不發布wire、corruption／schema／forged／oversize拒絕且原件不變、途中drift零write、過量索引讀任何value前停止。80473既有JVM通過；58119原目錄cache snapshot缺失失敗，88596隔離build JVM876/Desktop46通過但browser缺repo root失敗，設既有BOARDERLESS_TEST_REPO_ROOT後51663完整通過。使用者明確指出本機離線保存耗時過長，下一輪以APP真實自動存檔與重開讀回為先，不繼續用底層切片代替可用流程。

- 2026-10-06 原生ACK接線補強：DesktopWorkspaceDraftPersistence.acknowledge先以完整typed bundle／exact submitted／version／seq計算合法settlement。已保存相同ACK不發布新generation，而透過新confirmDurable在同key鎖內核對generation、force既有record及目錄；read本身不是durability證明，force例外仍報AtomicDraftCommitUnknownException，stale generation拒絕、不重試。正式repository/native activation／ledger／safety fixture涵蓋刪除提交及後續Unicode草稿，normal＋兩檔×三boundary的六個fault核對pending exact wire或settled tail、刪除provenance先保存、clientSeq不重配、legacy零寫入與明確retry相同payload；不同ACK拒絕。新AtomicDraftStore回歸驗證未知publication後補force不變bytes／generation／paths，兩force failure仍未知且stale zero-force。33409 fixture wildcard同名型別編譯失敗，explicit remote import修正；23003 focused通過，76168與93575全套成功；最終56000全套JVM869／JS799／Wasm799／Desktop46零failure/error（JVM6既有skip），Android/iOS編譯、新Desktop包成功23s。這是例外注入與file reopen，不是APP OS kill／斷電或正式server驗收；draft/safety/ledger仍分檔，未宣稱跨檔原子或啟用native預設。下一步保持default bootstrap／完整legacy catalog與writer retirement、正式receipt/fence/native recovery及其餘五線。user AI log仍不存在，401未排除。

- 2026-10-06 最新完整成功基線43520：JVM867／JS799／Wasm799／Desktop46零failure/error，JVM6既有skip，Android/iOS編譯與新Desktop打包成功39s。首次AI套用固定reviewed proposal/items，失敗後include/exclude停用，同transaction ID不能改內容；明確重試需新確認與既有fresh authority。真journal manifest已寫入後拋例外回歸核對原選取仍可讀，改選取及原operation從未前進baseline均拒絕append／覆寫，需原草稿review/recovery，不宣稱自動恢復或Settings durable。61711初次全套JVM類別載入失敗不算成功；核jar正常後重跑通過。上一輪亦修正安全logger容許現有755 APP父目錄但保持logs700／檔案600。沒有新live AI log，401未排除。下一步仍為native default bootstrap／完整scope catalog與legacy writer retirement／native ACK settlement及正式服務驗收，其餘五線缺件保留，goal active。

- 2026-10-06 既有relation合約的「插入節點」已實作：選取連線後在Reuse section按明確入口，不因單擊/選取/拖曳自動新增。RelationInsertionDraft凍結workspace maps與完整original relation，單次配置node／兩relation／三child operations／transaction IDs，修改preview沿用IDs；對endpoint／所有ancestor lock、missing／cycle／selfLoop、版本safe integer餘量／zIndex／有限geometry／UTF-8上限fail closed。預設world point使用與正式renderer一致的orthogonal避障route／clearance midpoint；共用direct parent保留，跨parent不改舊hierarchy。Backward按實際B→N→A且新段Forward；Both兩段均Both並提示非單序；None保留None並提示關係拆分；原label/intent/color在第一段，第二段不複製label/intent。

  RelationInsertionDialog明確預覽與confirmation，文字／第一段label/intent／world X/Y/size可改，圖形及端點/新node/附近物件可見；position±1e6／size1..100000／text64KiB，非法geometry有提示。首次queue嘗試後freeze fields，可明確重試同一payload，不能換內容重用identity。workspace/user/client/role／local snapshot／queue／recovery／pending edit/drag同步guard；modal列canvas interaction blocked，close/cancel零retainDraft／HTTP／history。確認後既有execute→retainDraft→single transaction/history/submissionQueue，CreateObjects→DeleteRelations original→CreateRelations2；不採用unsupported端點update／geometry v2 metadata。Server ACK由既有流程處理，UI只標queued，新node選取；未知ACK／native durable切換完整驗收仍待完成。

  RelationInsertionTest8cases涵蓋四directions／conditions只first／color、同IDs編輯/custom geometry／snapshot gate、共parent／locked/locked ancestor/cycle/missing/self拒絕、atomic changed-original拒絕與remote Undo/Redo exact restore/tombstone versions、UTF-8/geometry/version/zIndex bounds、caller mutable maps freeze、visible障礙繞路midpoint；真正BackendWorkspaceRepository retainDraft→submit MockEngine→四wire kinds/expected old relation version/第二段无条件→ACK8/seq14→pending清除。不是真服務或GUI/四端實機證據。56749 initialcompile成功12s；86159/95453 fixture引用未提供MapSettings，換既有InMemorySettings；62056/99904 fixture committed DTO漏clientSeq/base/head fields，補完整既有契約，未放寬production。59309 focused7＋Desktop成功5s；96812第一次全套＋包成功42s；追加正式避障point/附近context＋第8case後85438最終全套與新包成功52s。

  本輪亦重新核對backend/src/routes/api.routes.ts及services：user-level quick schemes/resource／canvasStyle mutation仍不存在，保留BAI-004/005，不猜endpoint／client重傳asset／把本機library冒作跨装置。沒有修改或啟動backend、.env、使用者prefs，沒有新AI live log，最新使用者實際AI證據仍HTTP401。selfLoop／persistent geometry／sequence metadata／正式向量Node仍缺合約和實機，不加入AI已驗capabilities。六主線goal active。

  85438最終：jvmTest {'tests': 865, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 798, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 798, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}，iOS arm64／Android main compile及Desktop createDistributable成功52s。直接核包含RelationInsertionDialogKt。

- 2026-10-06 APP工具任務與批次review已接線：Ai理解視窗新增「AI圖形草稿」入口，editor且提交佇列／recovery安全時可用，延續canvas modal／鍵盤隔離。AiDiagramToolDialog提供selection或whole≤128objects、模型／完整endpoint／認證／version／local HTTP、任務，key僅記憶體，沒有設定／key持久化；Router工具支援需真任務驗證，初始capability僅APP既有七shape／preset node colors／group，selfLoop=false，未宣稱pen/media/v2能力。每個真正HTTP都先展示完整不可變body＋endpoint/model並等待新checkbox＋send，拒絕／取消／dispose釋放pending gate；gate依同一review實例而非round值同意，stale／double按鈕不能批准下一輪。session／history／role／queue／recovery變化同步guard並取消清plan；backend fresh check失敗以WorkspaceBackend typed status區分AI本身HTTP。

  完成後唯本機preview：call items中文／英文操作名稱、逐項include/exclude→立即重算domain dependencies/version、node shapes/color/group/rotation fit preview與完整文字／geometry／relation direction/label/intent inventory，Double bounds避免大座標Float overflow；小圖連線是中心直線概略，不宣称正式geometry或實機視覺已驗。AiDiagramDraft→loop Ready新增本機logical refs mapping，維持read N及new A等名稱，UUID僅UI、未進任何provider body／safe log。final無proposal顯「未提出可套用變更」，不假稱產生圖形。

  Apply必須再次明確checkbox，refresh後pure reviewAiDiagramApply核對user/client/workspace、fresh及live editrole、三session和proposal version、lastServerSeq不退、opened/live/fresh/history完整snapshot一致；不是auto-rebase。合法selected Transaction沿existing execute→retainDraft→history→submissionQueue，標queued由原submission/ACK/recovery/Undo處理，AI UI沒有直接submit／繞journal、沒有在ACK前markCommitted。排除new node但保留依賴edge／empty／scope/permission/version/snapshot/status矛盾拒絕，失敗不改正式畫布；Settings預設durable／native default／unknown ACK完整驗收仍屬六主線待辦，不能聲稱這個UI解決全部保存。成功排隊後關視窗且清key/plan。每round自動安全logger，不需要再讓使用者複製資料。

  新增AiDiagramReviewTest5cases在三runtime：exact-instance consumed consent／stale/duplicate、cancel/close、fresh Editor實際selected transaction→history Undo/Redo、foreign/read-only/版本/seq/三snapshot/已使用proposal拒絕、exclude dependency及empty零op。25609 compile與39100初次因漏withTimeout import失敗，補import後18904 focused5＋Desktop classes成功40s。25227第一次全套＋新app image成功59s；追加無變更提示／操作名稱localized、backend preflight typed errors及live snapshot gate後83581最終全套＋打包成功59s；數字如下，不是付費Router或GUI／Android/iOS runtime驗收。交付app image desktopApp/build/compose/binaries/main/app/BoarderLess.app，直接核對包內AI dialog及logger class。仍無新live log，最後使用者實際AI證據仍401。

  83581最終結果：jvmTest {'tests': 857, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 790, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 790, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}，iOS arm64／Android main compile及Desktop createDistributable成功。下一步真Router/tools與四端GUI／scope撤權／formal ACK／Undo簽核，同時接續六主線其他缺件；goal active。

- 2026-10-06 工具bounded request loop已實作（AiDiagramToolLoop.kt）：専用client每次invocation ownership/finally close，最多8次HTTP；每一輪以不可變AiDiagramRequestReview實際wire body取得新的approve，再fresh scope validation→fresh credential acquisition→再validation，cheap session guard在每frame／每tool／observer回傳／最後proposal發布前核對。沒有automatic retry／fallback或沿用首輪同意。完整正常response且fresh validation後才能執行本機AiDiagramDraft；tool結果須下一輪另批准才送Router。末輪仍tools、跨輪64calls／args總1MiB超限均在該輪執行前拒絕；duplicate／foreign／closed／unknown／internal tool failures整個task停止，schema／unsupported geometry等safe rejected可在另一次批准的新round修案。任何取消／失敗／拒絕同意清整份隔離draft，不返回可提交proposal；成功Ready仍只AiProposal，要APP一次batch review＋fresh authority／正式transaction，無Repository／prefs／canvas寫入。

  HTTP沿既有SSE transport，正常status/type／timeout／UTF-8 parser門禁保持；endpoint/version/credential validators抽為共用helper，text-only ConfiguredAiProvider仍同樣規則與拒tools。每round safe typeddiagnostics保留第一failure／HTTPstatus/contentType，401不被generic decoder error覆蓋，不記body/header/model/endpoint/exception。取消（含外部timeout）向caller傳遞；本機fresh validation自有15s timeout回安全準備失敗。callback failure停止並向caller傳遞，完整tool預算／scope snapshot與capability sets在首個suspension前freeze。

  新增AiDiagramToolLoopTest10cases：兩方言真正MockEngine HTTP read/create→tool-result→finaltext→實際proposal preview、exact body approve/credentials/http順序／每roundfresh secrets、deny第二輪零第二HTTP、scope撤權於approve/preflight/secrets/frame/tools/Completed、401/EOF/empty/malformed zero-retry、rejected修案另批准、duplicate/unknown/8requests、approve/secret/HTTP取消與外部timeout、invalid endpoints/headers/preflight/timeouts/observer、跨round64call／1MiB總args與caller context/caps變動freeze。54447／79368 compile成功各10s；57889 focused17共3fail皆client close fixture誤認isCancelled，讀實際Ktor3.6.0 source確認close呼叫clientJob.complete，改join＋isCompleted，不改production釋放語意。78706第二次build在jvmJar遭暫時ZIP並行寫入衝突失敗；35685之後focused17成功5s，最終前直接zip testzip與AiDiagramToolLoopKt class核對artifact完整有效。

  仍無新本地live AI紀錄，最新真實證據仍使用者HTTP401；本輪僅MockEngine、不發付費模型／backend请求。APP工具任務入口／每round授權UI／cancel／preview／batch review／fresh versions／submit/ACK/history Undo尚待接線，六主線全部範圍保持active。

- 2026-10-06 工具專用OpenAI Chat／Claude Messages串流解碼已實作（AiDiagramToolStream.kt），未放寬舊text-only adapters。Chat單choice／穩定completion ID，支援交錯call index args fragments；call identity/name/type固定、重複index／ID拒絕，正常tool_calls finish後仍必須[DONE]。Claude順序且完整關閉text／tool_use blocks，input_json_delta累積、empty-input無delta回{}；全部正常tool_use stop後仍必須message_stop。不支持thinking/server tool/custom function等模式明確Unsupported，非偷偷執行或降級。僅terminal Completed暴露完整request/session綁定call；text delta只是進度，EOF／length／wrong stop／refusal／錯lifecycle／transport／cancel均無可執行partial calls。

  共同限制：每event262144bytes、8192events、wire總4MiB；text128KiB UTF-8、64calls、單args64KiB／總1MiB。完整JSONobject嚴格重複key含escaped aliases／depth／decoded Unicode surrogate驗證，status失敗只safe enum，不外洩原錯誤。wire builder亦改用同一strict parser驗round-trip，避免Claude parse後last-key-wins。Flow在terminal停止upstream，CancellationException保留並finally關decoder清buffer，零HTTP／credential／執行／prefs／真正canvas。新增AiDiagramToolStreamTest8cases：Chat interleave完整args、Claude text＋tool次序、多種EOF／空白／異常停止、duplicate／malformed／surrogate／provider error、wrongdelta／thinking／lifecycle、UTF-8／event上限、flow cancel/transport/terminal與實際draft只執行一次→兩方言tool results。87378 production JVMcompile成功10s；37086 focused stream8＋wire6測試成功17s。

  仍無新真Router紀錄，最後已知為使用者HTTP401；逐次批准的有界request loop、APP工具任務／預覽審核／fresh authority／正式submit與Undo尚待交付，六主線goal保持active。本輪不發付費AI／backend請求。

- 2026-10-06 OpenAI Chat／Claude Messages八工具wire catalog與完整歷史request body已實作（AiDiagramToolWire.kt）：兩方言使用同一份完整JSON schema，additionalProperties=false／必填欄位／有界geometry／logical IDs／实际shape和color subset，未宣稱strict provider能力或強制tool_choice；純建body，沒有HTTP／credential／scope擴張／canvas mutation。Chat以assistant tool_calls保留原args string，配對role=tool／tool_call_id；Claude以assistant tool_use/input物件，緊接單user消息全部tool_result並is_error標識拒絕。結果均status＋committed=false＋safe failure enum，untrusted文字只在user／tool資料，不升system。request/session綁定，唯一call ID，結果必須逐一同序配對；unknown tools／malformed root／extra或少結果／cross scope拒絕。最多8歷史turn／64calls、args每64KiB／總1MiB、text128KiB、data256KiB、最後escaped UTF-8 body1MiB；model/output budget明確，無default model、自动retry或fallback。

  新增AiDiagramToolWireTest6cases核對八schema完整subset、Chat exact args與result pairing、Claude多tool立即配對及rejected flag、實際AiDiagramDraft read＋create→兩wire round-trip／private IDs排除／原workspace不變、foreign／duplicate／malformed history拒絕與UTF-8／escaped final wire／call／depth上限。37366 JVM production compile成功11s；82286初次測試compile因fixture Workspace漏title失敗，補fixture必填欄位後50558 focused6成功4s。沒有真Router／付費AI請求，text-only adapters仍拒tool calls。本地安全log尚無新的實際紀錄，最新已知仍HTTP401；包含logger的Desktop app image已於上一輪重新打包。串流完整call解碼、有界可取消且逐次批准的request loop、APP review／fresh authority／正式submit／Undo仍待接線，六主線goal active。

- 2026-10-06 中立AI八工具隔離executor已實作：明確capability subset、批准scope safe graph N/E refs、APP UUID、create nodes/groups/relations、update text/attributes、layout及local validation，輸出既有AiProposal，不改真正畫布／HTTP／prefs。request/session binding／唯一call ID、64calls/32items/args UTF-8 64KiB總1MiB/graph256KiB/semantic wire≤200，strict extra/duplicate escaped keys/numeric type/token/depth/geometry、scope/locked ancestors/unapproved group descendants/cycle/unsupported self-loop拒絕；失敗零draft/mapping改動，排除依賴→preview conflict，Close清私密上下文。新增7cases含local Undo/Redo、caller-owned context/caps freeze、media credential排除、完整多tool方案與bounds；27128 final JVM/Desktop/JS/Wasm actual tests＋iOS/Android compile成功43s，三runtime各7cases零fail/error/skip。text-only adapters仍拒tools、真Router401及APP工具review未交付，沒有付費AI。下一步優先tool definitions／OpenAI Chat/Claude工具request/stream/result adapters及有界loop，再review→fresh authority→正式submit／Undo；native default cutover門禁待真實legacy排除與catalog，不重做已驗native seams或executor。

- 2026-10-06 同namespace新workspace native登記已接：activation schema2之控制檔單筆Preparing／Ready entries、唯一scope／最多256／payload1MiB與phase-generation核對；不刪歷史、不接受schema1降級。Preparing→空draft tombstone／typed empty safety→Ready，所有檔readback後才開放。Preparing runtime關閉、unknown outcome新invocation完整source／identity／catalog／exclusion重驗才resume，existing slot generation1 exact；Ready advanced正常typed讀，不再初始化、缺檔不當empty。舊prefs dynamic認得新Ready，source／migrationimmutable、一個shared ledger不配置序號，foreign新namespace拒絕；projected1024records quota先檢。新增6cases含actual repository stage→ACK、zero legacy mirror、12fault與12獨立JVM kill；50611 JVM821／Desktop46及跨平台檢查成功21s，零fail/error、shared6skip。未動userprefs/defaultAPP／backend／發AI或HTTP。下一步default bootstrap／真實legacy exclusion與scope catalog、其他namespace verified初始化、ACK settlement／fresh review→Apply；門禁缺外部證據則AI tool schema／隔離draft優先，不重做已驗enrollment。

- 2026-10-06 verified activation／native SessionPreferences routing已實作：Preparing gen1→空draft持久tombstone槽gen1→Active gen2，綁Published receipt／source／target，partial resume只接受exact初始state。94060重現原始empty scope後續草稿丟檔誤當absence，已修；啟用後任何native artifact消失停止。open先後核完整legacy source／全部typed scope／最低floor，每次facade與clientId核control／必要exclusion／identity。原生draft／fence／deletion／sequence一併接，legacy counter不mirror、不mint缺身份、直接clientSequence拒絕；多workspace共ledger，foreign client archive保留但禁止送出、新scope未登記拒絕。新增9cases、9fault及9個獨立JVM kill；79972 JVM815／Desktop46及跨平台實際compile成功21s，零fail/error、shared6skip。未動userprefs或預設APP bootstrap，不把fixture callback當舊writer排除。下一步真實legacy exclusion／全scope catalog／new native enrollment／default routing，再完整ACK settlement與fresh review→Apply；門禁缺證時推進AI tool schema／隔離draft，不重做已驗activation。

- 2026-10-06 完整native遷移publication coordinator已實作／驗證：先保存綁target／完整sourceDigest／exact artifact digest與generation的Preparing receipt，再跨scope draft／safety及共用namespace ledger發布，全讀回才CAS為Published。全native record catalog排除未知／外來／symlink，absence保持absence；同receipt的partial resume只補缺artifact，既有generation1必須exact match，advanced／tombstone／corrupt與source drift拒絕且不rewind，Published丟檔不補。每步必要writer exclusion callback無default，不把合作lease或double read當舊writer排除。新增7cases、33個publication fault及15個真正獨立JVM kill；97160全套JVM806／Desktop46和跨平台檢查成功12s、零fail/error、shared6skip。來源legacy／staging保留，不啟用production、不改userprefs、无HTTP。下一步利用Published receipt接activation／runtime routing及真正legacy exclusion，再跨檔ACK settlement與fresh review→Apply；不重做已驗publication coordinator，詳APP_DRAFT_JOURNAL_DURABILITY_PLAN。

- 2026-10-06 AI實際失敗已由安全報告確認HTTP401／Other／HttpRejected，尚未進串流；不能據此斷言是哪層認證錯。依使用者要求已接Desktop自動安全log，位於`~/.boarderless/logs/ai-probe.log`，每檔256KiB＋一backup，UTC／attempt ID／dialect／auth mode／安全事件，不保存Key／endpoint／model／context／原回應。背景bounded queue／每attempt24筆／寫檔與排隊錯誤隔離；POSIX私有權限／symlink拒絕。使用者後續不用手動複製診斷；新Desktop build執行才會收集，未重啟使用者APP或代發AI。新增5cases；33073最後跨平台compile及全套JVM799／Desktop46成功25s，3909追加不可寫來源fixture後JVM799成功5s，零fail/error、shared6skip。Android main compile97589成功11s。真Router401是否解除與WindowsGUI未驗；詳AI_UNDERSTANDING_ACCEPTANCE。下一步利用後續本地log確認設定；等待有效認證依據時繼續migration receipt／partial recovery及tool schema，不再把「未收到安全報告」當阻塞。

- 2026-10-06 初次遷移pure planner已驗：DesktopLegacyMigrationPlanner凍結全source、排序catalog、重驗floor／fence binding／head，輸出各scope draft／safety／namespace floor與retry quarantine。處理archive已發佈但pending尚在的stop phase，保留exact wire／全部intentions並隔離重送；缺head wire／矛盾／orphan fence／偽造floor拒絕，sourceDigest綁ORIGINAL state與counter，不只normalize結果。含unsent及safety-only／foreign scopes，上限256scopes、每scope source16MiB／總40MiB，native寫入仍各codec6MiB。RecoverySafetyBundleCodec由writer與planner共用，import fingerprint不改。23035focused10tests成功34s；93705最後JVM794／0fail／0error／6skip及Desktop46／0fail／0error／0skip成功8s，新增5cases。初次planner對existingNativeBundles非空明確拒絕，不覆寫／rewind；partial migration／已有native state須下一步receipt coordinator接續。沒有native寫入、啟用、userprefs變更、真API或付費AI；AI新版實際失敗已非阻塞詢問使用者安全診斷，不據缺回覆猜原因。
- Desktop原子安全資料已接facade／repository seam：RecoverySafetyPersistence＋private per-scope file、namespace key、generation CAS、schema／scope／唯一性／bounds、explicit adoption fingerprint，不fallback Settings、不把缺記錄或tombstone重置。fence begin落盤前不得HTTP、unknown不解除、terminal矛盾／request digest不同拒絕；刪除cascade完整批次單CAS，等tombstone必須完全相同、較新版本／serverSeq才能替換。SessionPreferences新增僅internal explicit constructor注入，預設未啟用。75602focused成功35s，最後65072 JVM789通過23s（28467前輪完整實際compile43s）；新增5cases含native facade重開、原子cascade及正式restore gate、三publication故障、actual repository零HTTP與pending／sequence未變、adoption不可rewind及tombstone不可復活。尚非跨draft／ledger／safety檔案原子settlement、全migration或OS kill。不重做此seam；下一步全來源migration plan／activation receipt與舊writer排除門禁。
- 安全來源盤點已接：capture包含全部fence attempts與committed deletion evidence，驗scope／hashed key／schema／state／digest／版本／serverSeq、bounds與觀察到的異動；不讀無關Settings值、不寫入。matching pending／stopped／typed wire必須與fence request digest一致，缺wire marker仍保留且回傳unboundFenceAttempts，不能用數值floor直接activation。22255focused24tests首輪1fail因fixture用正常save覆寫pending，被既有門禁正確拒絕；改為隔離fixture注入合法格式但錯digest的marker，不放寬保存規則。69218最後shared JVM784／0fail／0error／6skip、Desktop46／0fail／0error／0skip與JS／Wasm／iOS arm64編譯成功1m16s；新增5cases。沒有遷移、原子保存這兩種資料、default activation、真API或讀秘密值。下一步安全來源native保存與全migration activation gate，不重做已驗inventory。
- Desktop新版合作程序排他已接main：APP／Settings建立前取得lifetime-writer.lock，持有至Quit／程序結束，Mac關閉視窗仍持鎖。第二次啟動嘗試既有socket open／focus轉交；拿不到鎖或root／file安全檢查不通過，提示後停止APP啟動，不fallback成第二writer。舊build／CLI不遵守此鎖，不能據此自動啟用遷移。87035首輪5tests中protocol fixture誤將合法ID invalid當非法，改invalid/path後70538 Desktop全46／0fail／0error／0skip與shared JVM779／0fail／0error／6skip成功9s；新增5cases含獨立JVM排他與kill後鎖釋放、stable inode、POSIX symlink／permissions拒絕及pure open/focus token校驗。不是實際APP／Dock／WindowsGUI或資料kill-durability驗收。此輪只有Desktop改動，不把未重跑的跨平台compile當新證據。
- 「跨工作區停止交易盤點與 client sequence 下限」唯讀切片已驗，不啟用遷移。完整 archive inventory 驗全域 metadata／legacy exact catalog、損毀、讀 chunk 前 aggregate bound 及觀察到的目錄異動；floor 驗跨 workspace／namespace 篩選與同交易 exact wire 一致性；capture 驗缺身份／counter 不初始化、完整來源及 counter 漂移拒絕、零自身寫入。
- namespace isolation fixture 已改不同交易使用不同 ID；counter 漂移 fixture 已改正式 backend.clientSequence key。新增9個測試，focused與全套成功，不以 fixture／double read 當正式服務、OS kill 或多 writer 排他證據。
- 後續完整草稿盤點已驗：scope catalog 同時涵蓋未送出的 journal，不再只列 pending 工作區；保留完整 intentions／quarantine，缺目錄／損毀／讀取前16MiB aggregate limit／目錄異動拒絕，capture 重讀 journals。7439 focused8tests通過57s，1856全套774通過；新增5cases。
- Desktop多筆archive保存已驗：typed bundle v3 retainedStopped保留獨立historical wire／各自evidence，v1/v2不新增空欄位；可與另一active pending並存，append／ACK／明確移除draft不刪archives。採generation CAS、原子發布、重開讀回、per-transaction repository hint與floor納入；legacy journal與archives可分階段任意順序adopt，不能覆寫同交易wire或重送其ID。38875首輪test import名稱衝突編譯失敗，修正後44582focused成功18s；最後68150 JVM779通過，新增5cases。尚非自動全scope migration／預設activation／OS kill／跨程序排他。
- 接著補Published migration receipt後的activation／runtime reader routing與舊版非合作writer排除門禁，利用已驗完整publication coordinator、lease／inventory及native draft／safety／ledger seams；初次plan不可當already-native重新遷移入口，必須對照receipt與每檔generation／digest再決定resume。不得部分檔案完成就切writer，或rewind已前進native record。不能以合作鎖或double read代替舊writer排除、缺counter／client ID不得補零或新身份；unbound fence需explicit recovery，floor只是下限。之後才進入fresh review／merge Apply；AI安全報告到達時優先診斷，不代發付費請求；其他平台native仍未接。
- 前一目標更新回合只改文件；本次續行修測試並完成回歸。未啟動後端、讀金鑰、改使用者 prefs 或新增排程。既有 active goal 已引用本文件，保留六主線總範圍及 active 狀態；目標工具僅支援狀態更新，未改寫其 objective 字串。

## 最新有效執行清單（取代下方歷史待辦）

### 已完成開發，不再重做

2026-10-05 Desktop atomic evidence續接：repository明確capability與saveAPI，網路前capture generation→重新完整權威核對→背景單檔CAS，保留exact stopped wire／原journal／tail／quarantine；bundle保存proof升v2，v1不增新欄位，矛盾terminal／immutable commit值變動／head倒退拒絕。UI只有atomic adapter才提供保存，預設Settings false且零HTTP拒絕，已保存hint與獨立reload archive不重設另一pending的receipt/fence。4905最後JVM760／0fail／0error／6skip＋Desktop/JS/Wasm/iOS arm64編譯成功1m23s；新增4cases，native repository實際mapper wire／POSIX檔案reopen、committed與fenced、DataForced/Published/DirectoryForced及網路generation race，common schema／證據與Settings不可保存門禁。12540focused及21819、3628較早全套成功不代替最後內容。尚未預設activation／跨平台native／完整legacy catalog與floor／OS kill／settlement及merge Apply，勿冒稱四端完成；不重做此native seam。

2026-10-05 封存完整核對已接：視窗 committed／fenced 後另按「核對完整記錄與最新畫布」，重新receipt；committed沿共用helper核對完整log／wire／commit時間／payload，最多200records；fenced不假造ACK。fresh metadata/state至少到receipt head與原session checkpoint，scope／撤權／archive異動／取消拒絕，返回transient verification而不改Settings／sequence／journal／quarantine／另一pending。55985 JVM756／0fail／0error／6skip＋Desktop/JS/Wasm/iOS arm64編譯成功46s，新增13mode矩陣與fresh-state取消2cases；66159focused前輪成功33s。不是持久settlement或Apply／正式服務／GUI。下一步封存權威核對結果的原子保存／草稿狀態轉移與fresh review→明確merge，不重做唯讀核對。

2026-10-05 停止重送後恢復入口已接：Settings scoped manifest inventory（全域256上限／目前scope合計4MiB）、active manifest保持舊四欄、legacy exact journal head fallback與不完整catalog fail closed；repository stoppedChanges／inspectStoppedReceipt、Desktop bundle stopped讀取seam，中央safeDrawing modal逐筆查既有receipts，scope／取消／15s gate及canvas鍵盤隔離。committed／fenced／unknown／403／格式錯不改任何本機資料、不清隔離草稿、不submit/fence，另一active pending不被誤查。91585 JVM754／0fail／0error／6skip＋Desktop/JS/Wasm/iOS arm64編譯成功1m22s，新增5cases含scope／metadata／legacy／超量／zero-write矩陣／進入engine後取消。未真服務／GUI簽核；full-log settlement、完整legacy catalog修復、production floor／durable與merge Apply仍未接，勿重做封存唯讀入口。

2026-10-05 Settings stop 保存缺口已修：先以 scope／transaction 不可覆寫封存完整 wire（含 restore provenance）並回讀，再隔離 journal／確認原 pending，最後才移除重送槽。分塊與 manifest 保存均回讀確認；journal 新版本完整可讀後才回收舊分塊。24881 故障測試曾重現 silent manifest drop 仍刪舊分塊，未放寬斷言；修後 82476 通過，最後追加 pending 保存屏障的 45482 全套 JVM749／0fail／0error／6skip＋Desktop/JS/Wasm/iOS arm64 編譯成功。新增5個 cases。這是 Settings 封存前置，不是原子 durable archive、完整草稿備份或 Apply；封存檢視／receipt reconciliation、migration catalog／sequence floor、原生 OS kill／多 writer 仍待接，詳 ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

2026-10-05 fence attempt保存／重送門禁接線：HTTP前保存Unconfirmed並回讀，綁scope/transaction/exact wire digest；unknown不清，terminal只更新已有attempt，損毀或矛盾fail closed。pendingChange回resendBlocked，UI與repository retry都阻止重送，重新建立同Settings的preferences/repository仍生效；沒有新增transaction或清草稿。97436 JVM全套744零fail/error、6skip＋Desktop/JS/Wasm/iOS arm64編譯成功46s，58610 focused含pending recovery通過47s。這是Settings/InMemory回讀與重建測試，不證明OS kill/四平台durable/斷電/跨程序安全；下一步原生保存驗證、fault quarantine及fenced→fresh review→merge Apply，勿再列此core未接。詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

2026-10-05 explicit fence續接：unknown原交易恢復區雙次確認／不可撤銷警告／scope綁定，第二次才POST既有operations/fences；fenced或commit勝出的terminal證據仍保留wire與草稿，逾時不冒充取消server、當前同transaction暫停重送。42143 JVM743tests零fail/error、6skip及Desktop／JS／Wasm／iOS arm64編譯成功33s；新增fence matrix含engine進入後取消，52748 virtual短timer假未送到fixture已修成確定進入後取消，7246 focused6tests成功15s。未發真fence／改backend／代簽GUI；持久proof與fenced→fresh review→merge Apply／Undo仍待接，勿將BAI-012全部完成。詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

2026-10-05 已提交原交易恢復續接：receipt committed後明確「核對完整記錄並確認恢復」，重新receipt→完整log range／wire身份與payload值核對→fresh ACL/state→既有evidence／sequence／journal ACK順序，成功才清pending並載最新head；unknown／缺段／錯payload／baseVersion／撤權仍保留，不resend/fence。49374 JVM全套742tests零fail/error、6skip及Desktop／JS／Wasm／iOS arm64编譯成功57s，新增恢復矩陣與normalized payload測試。真實服務、delete provenance跨重啟及GUI仍待驗；下一步明確fence→merge Apply與多writer／durable，不能把BAI-012標全部完成。詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

2026-10-05 新增正式receipt唯讀APP入口：未確認變更恢復區「查詢原提交結果」，使用既有operations/receipts，核對exact transaction／actor／client／每個operation及序號範圍；committed不冒充完整ACK，unknown不當未提交，fenced僅顯示已有證據並停用當前重送。查詢不清pending、不submit/fence、不增加版本。93230 JVM全套740tests零fail/error、6skip及Desktop／JS／Wasm／iOS arm64編譯成功37s，3個新receipt tests（含正式repository MockEngine）；未驗真服務／GUI。下一步完整ACK reconciliation／exact provenance及明確fence→merge Apply，不重做lookup核心，詳ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE。

最新驗證89488：JVM全套737tests／0fail／0error／6skip（未opt-in live／外部parser）、JS／Wasm／iOS arm64編譯及Desktop classes成功35s，含本次自循環route和AI追加保護。Backend唯讀未見v2 geometry/capability，create self-loop仍拒絕；未改backend／env／使用者APP，也未發live AI。僅core／編譯證據，不是GUI／正式同步通過。

- AI 唯讀理解驗證：Local AI Router 的 OpenAI Chat／Claude Messages 相容設定精靈、逐次同意、Mermaid flowchart＋短 ID inventory、串流、核對及手動複製流程。
- AI 固定標題下方狀態、HTTP／串流／前置檢查錯誤分類與安全複製；失敗／取消保留。96978 JVM 全套730tests零fail/error、6skip及JS／Wasm／iOS arm64編譯成功，Desktop classes通過。
- 追加保護：每次請求結束清同意、回應按UTF-8 bytes限128KiB、空白完成回應判未完成。47483 focused probe共8tests零fail/error及Desktop classes成功31s；54819後續JVM全套732tests零fail/error、6skip，JS／Wasm／iOS arm64及Desktop classes成功28s。真實Router／GUI仍待使用者安全報告。
- 自動連線標籤避讓已實作。自循環已有隔離路徑核心／四側shape ports／旋轉移動縮放／hit-testing測試，但未接renderer UI／模型解碼／history／保存；手動geometry／插入步驟／時序圖仍未實作。66432 focused routing9tests通過，含5個新cases；Plus凹形穿入問題已重現後修正，不以中心ray作錯誤邊界oracle。
- 新 BACKEND_DIAGRAM_RELATION_V2_SPEC 已完成草案：現有／新增能力、geometry 格式、self-loop、parallel edges、原子拆線、exact restore與第二階段metadata。文件不是後端已交付。
- Undo／Redo restore 正式接線、Web選檔取消競態、iOS Photos callback reference與手機多選touch-slop修正已實作；人工／跨端未驗項仍保留，詳既有驗收文件。

### 下一輪優先順序

1. **原交易恢復與安全保存（下一個可執行切片）**：完成 fenced 原請求保留 exact wire／草稿→讀最新畫布→逐筆 review→明確 Apply 的流程。exact wire封存、完整唯讀核對、Desktop atomic evidence seam及legacy archive／sequence floor唯讀capture已驗，不重做；完整來源至原生儲存的publication／partial recovery已驗；verified activation／explicit native routing也已驗；同namespace新workspace enrollment亦已驗；下一步真實writer排他、全scope catalog／新namespace初始化與default bootstrap，再接真正的草稿狀態轉移、fresh review及明確Apply。Android／iOS／Web原子adapter仍待補。不能用 stop→restore 當安全 merge，不得略過 PendingSubmission／PendingDraft／quarantine 門禁，或以marker／receipt摘要／transient或本機保存proof當當前權限授權。完整ACK settlement／Undo及取消、撤權、版本漂移、OS重開仍須驗證。
2. **資料正確性與協作收尾**：原生 journal／fence marker 落盤、OS 重開、損毀 quarantine、legacy 多 writer 排他及 sequence floor 來源；補 restore 正式 fixtures 的匿名化 map-key／relation CAS 缺口。既有 receipt lookup／完整 commit 核對／二次 fence／重送門禁已接，不重新開發。正式 WS／auth／Presence 待後端合約交付，REST 輪詢不算即時協作完成。
3. **連線 v2 與 APP 互動**：先由後端確認正式 capability 路由、self-loop、geometry 整體替換／state-event round-trip 與 fixtures；再依序接自循環 renderer／模型／history→手動線與標籤拖曳→插入中間 node 的 preview／原子提交／Undo／Redo。交付前只做隔離核心，不猜 endpoint、不開放假可用同步 UI；單擊線條只選取，不原地改端點。
4. **AI 真實失敗與工具工作流**：固定狀態／安全除錯、逐次授權、UTF-8 回應限制及空回應保護已完成。真實失敗已確認HTTP401，Desktop自動安全log已接；等待後續log與Router認證依據，不盲改參數或代發付費 AI。批准scope中立schema／八工具隔離draft已實作；接續兩方言tool definitions／request／stream／tool-result迴圈／審核預覽；提交須 fresh ACL/version、正式 transaction 及 Undo。只支援 Local AI Router 的 OpenAI Chat／Claude Messages 相容服務；預設 Mermaid flowchart，特殊圖型須明確建立及持久化。
5. **素材與 GIPHY 完整交付**：補跨端 picker／原生播放／取消重開恢復、票券撤權及實際 GUI 驗收；保留既有六格式 live 與平台切片證據，不重跑當新進度。GIPHY 持久保存仍需供應商許可，使用者 modal 同意不能替代。
6. **其餘產品主線**：user-level 快速方案簿同步、正式 canvasStyle operation／projection／Undo、多形狀／鋼筆向量保存；時序圖 metadata／精靈／converter 為後續階段。先核對既有規格及真正交付，不能將文件存在當成服務可用。
7. **人工與效能驗收**：視覺辨識、遮擋、iPhone 安全區、觸控與 VoiceOver 由使用者依 QA SOP 判定；效能安排最後，不代簽。

### 持續執行規則

- 每轮選一個未完成、可驗證的 APP 切片；完成後記錄變更、測試範圍、未驗項與下一步，再繼續。
- 缺後端合約、使用者安全除錯記錄或人工簽核時，明確列為等待輸入，切到其他可安全做項；不能猜結果，也不反覆重測已通過核心充當進度。
- 舊 pending 的提交狀態未確定前不造新 ID 重送；fence 已嘗試／逾時仍保留原 wire、草稿及重送門禁。
- 連線與節點的新後端交付以 BACKEND_DIAGRAM_RELATION_V2_SPEC.md 草案協商；receipt／restore 驗收分別依 ORIGINAL_SUBMISSION_RECEIPT_ACCEPTANCE.md、UNDO_RESTORE_ACCEPTANCE.md。
- 完整六主線達成並無必要工作剩餘才完成 goal；單一測試綠燈、核心或規格交付均不符合完整完成門檻。

### 完成門檻與限制

完整目標仍是六主線交付，不以本輪AI或規格切片標complete。只改APP／docs，不修改或啟動其他agent管理的backend、不動他人env／staged變更。後端新規格先由維護者確認；未有正式capability不猜endpoint。最新選工依本節，下方較早「尚無UI／restore尚未接線／下一步寫HTTP」均屬歷史，不能重新列成開發待辦。

## 歷史進度與測試證據（非當前選工清單）

## 最新剩餘工作摘要（優先閱讀）

使用者決定 Mermaid flowchart 為預設 AI 表達；APP 理解驗證已改成 flowchart＋N/E 短 ID 精簡 inventory，不送存檔 UUID／版本／座標。特殊圖型不得猜測，須建立時明確選定並持久化，目前 metadata／特殊 converter 尚未接入。詳 AI_DIAGRAM_EXPRESSION_SPEC；runtime parser/render／雙向匯入尚未實作，原六 scope active。

新增時序圖／自循環／線與標籤拖曳／線上插入中間步驟需求，詳 SEQUENCE_AND_RELATION_INTERACTION_SPEC。本輪 APP 自動標籤避讓已實作，未開放被 backend invalid_relation 拒絕的 self-loop；需後端能力／正式 style 合約與時序 metadata。插入節點用原子 delete/create，不原地換端點；手動拖曳與建立精靈尚未交付，原六主線保持。

AI 理解驗證切片已交付：JSON scene／只讀 3 步驟 Router 設定與同意送出／串流／inventory 核對及手動複製貼入。87856 JVM／JS／Wasm／iOS arm64 編譯驗證成功，未呼叫真模型；使用者依 AI_UNDERSTANDING_ACCEPTANCE 驗語意、方向、階層與 scope 隱私。下方「尚無 AI 設定 UI」為此前記錄，現有驗證入口不代表完整安全持久化設定、tool-call loop、草稿預覽提交已完成。原六 scope 保持 active，效能最後。

AI定位依使用者補充：不是只聊文字，而是知道BoarderLess服務能力、讀批准context、调用工具完成圖形規劃。新AI_DIAGRAM_TOOL_WORKFLOW定義第一批capability／read／nodes／groups／relations／layout工具、隔離draft／tool result循环与批次審核→fresh權限／版本→正式transaction／Undo。現有text-only adapters拒絕tools，尚未實作tool loop；先設定精靈，再工具schema／executor／兩方言adapters與APP preview/commit。不由模型直接取得後端或秘密、每次tool call不直接提交。

AI範圍依使用者收斂：第一階段只接Local AI Router提供的OpenAI Chat-compatible與Claude／Anthropic Messages-compatible兩方言，不另做第三套Local原生API、其他協定／多供應商fallback。APP目前確實尚無AI設定UI／精靈；下一個AI切片優先「AI設定入口→Router endpoint→協定→模型／認證→明確測試與保存」，再接context同意、串流／proposal review及確認提交／Undo。只更新規格，非UI已交付；詳細安全與驗收在AI_PROVIDER_STREAMING，人工QA與效能最後保持。

手機多選 jitter／退出殘留修正已實作並驗：系統touchSlop、selection-only與最新callback／mode-key，退出清selection／preview，text／media／group一致且隱藏單物件handles。32570 JVM711tests零fail/error、5個live skip，iOS704tests零fail/error/skip與iosArm64編譯成功；不重做此修正，剩iOS實機新build依QA SOP驗A→B→退出單選、群組及素材、VoiceOver／遮擋。原六scope與效能最後保持。

Undo／Redo restore已接APP入口、pending及正式submit，不重做planning-only slice。36118 JVM707tests零fail/error、4個其他live skip，restore opt-in live1case無skip，僅新隔離QA畫布。剩餘：後端修正式16／20 recorder map-key匿名化與relation CAS缺失，重跑正向contract；跨端／人工UI、撤權／未知ack、Settings多writer／cold-start原子保存、history stack跨重啟、receipt／fence草稿merge仍待驗或實作。詳UNDO_RESTORE_ACCEPTANCE；原六scope保持，以下「尚未接入」為早期紀錄。

iOS選照片／X無反應的原生reference判斷已修；17751橋接wrapper回歸、Simulator tests與iosArm64 compile通過，需新build人工picker重驗，不新增完成button。未动使用者iPhone17 simulator，新增驗證sim已shutdown。

restore APP對接第一步：新增BackendRestorationPlan，從刪除前權威projection與完整committed delete transaction（已有reducer gate）取得被刪物件／cascade relation的tombstone CAS版本，產生IDs-only restore_objects→restore_relations順序與新wire IDs／clientSeq。拒絕缺段、未知操作、cascade不符、missing IDs與safe integer溢位；不是從任意inverse快照猜版本。41693最後focused JVM3cases通過13s，未改backend／送POST，未做跨端或正式fixtures驗收。尚未接WorkspaceHistory／Undo／Redo／pending journal，現有入口不宣稱已修；下一步保留exact deletion provenance與同ID／snapshot一致性、完整跨平台／31fixture、正式提交與版本rebase，receipt/fence仍後續。原六scope保持。

iOS Photos入口已補，不重做Files-only問題：匯入來源menu與PHPicker single/Compatible、provider callback內私有copy／既有source與取消清理；15679 Simulator測試＋iosArm64 main compile55s成功，照片helper4cases通過、新boot已shutdown。完整GUI/iCloud/HEIC-MOV representation／實機upload仍人工待驗；下一步已交付restore／receipt/fence合約APP整合優先，原六scope active，效能最後。

2026-10-05使用者交付資料正確性：唯讀核對backend source已有restore_objects／restore_relations、typed object／group invariant、POST operations/receipts與fences及canvas-v1 index31fixtures；後端回報145tests／競爭5次穩定，APP尚未重跑後端或驗運行服務capability。BAI狀態改後端交付／APP待對接。完成本轮iOS照片入口後，下一步優先正式restore wire／tombstone version／reducer／Undo-Redo／fixtures接線，再receipt/fence与backup provenance／confirmed merge。type替換／relation端點仍unsupported_change、舊DB掃描遷移待後端；不以restore局部交付當草稿完整合併完成，不修改／啟動後端。

84426 Web取消競態修正最後JS／Wasm Karma與兩種entries建置成功1m38s，BrowserMediaImportRuntimeTest各3cases零fail/error/skip；兩種processedResources橋接JS與source hash一致。Node全20cases通過含新競態3cases；此缺口不重做，使用者須載入新Web build重驗原檔／storage，fixture不當完整native picker／網路驗收。原六項scope保持，效能最後。

Web選檔誤判取消競態已重現並修：移除focus後250ms空files即取消，等待change／native cancel／explicit cancel；120s選擇deadline為錯誤、不假稱取消，listeners／input清理。原程式delayed-change regression確實fail，修後Node Web20cases通過；JS／Wasm與entries結果另列。需用新Web build驗實際原檔與storage，不重做已驗Android／iPhone配置，原六主線active，人工QA／效能最後保持。詳MEDIA_LIVE_ACCEPTANCE。

iPhone配置缺口已修：83922 Xcode解析原Info API確實127.0.0.1:3000且優先於Kotlin fallback；Config.xcconfig增加iphoneos SDK條件192.168.68.65，simulator保留loopback，不改ATS／backend。78986直接Xcode／plutil配置驗證2cases通過2.02s，explicit部署override仍優先；README與QA重驗步驟已補。不重做此配置修正，但使用者截圖平台／build未確認，不当該問題唯一根因或iPhone實機上傳已通過。需重新build/install後重驗，或接續iOS正式sink／來源網路證據；原六主線active、人工簽核與效能最後保持。

7001 Android原生載入唯讀 live 通過1.379s／1case無skip：既有JPEG／WebP及三張poster原生解碼、GIF全部影格、MP4／WebM MediaPlayer prepare／seek command／release与fixture cache清理，前後workspace不變。37369 Device APK成功10s，只更新獨立QA APK，無新增server資料；普通run明確skip。不重做此已驗範圍；影片surface／音訊／實際連續播放／seek落點、picker UI／OS重啟仍Pending。最新iPhone generic失敗截圖待使用者補實機／模擬器、格式與build，Android成功不解除iOS；下一步原生播放或iOS正式來源與sink證據。詳細scope／重跑見MEDIA_LIVE_ACCEPTANCE。正式WS／auth仍未交付，原六主線active、效能最後。

2026-10-05 正式素材簽發已更新LAN origin，因此回素材主線。5118 Android emulator API37實際ContentResolver／source snapshot→signed PUT→worker ready→Node保存→新client載入→正式Android sink校驗下載／原生解碼成功2.635s（1case無skip），詳MEDIA_LIVE_ACCEPTANCE。原使用者截圖／picker UI／OS重啟／Android其他格式播放仍待驗；不把1PNG當所有平台完成。不改backend／signed URL；保留1新QA asset／Node與獨立QA test APK，無使用者資料覆蓋。

556321 AndroidDevice切片：真實SharedPreferences checked commit/XML/dismiss與原生GIPHY品牌Res/hash各1case通過，live case普通run明確skip；不要重做这兩個已驗Device範圍，不宣稱完整Device全套／APP restart。AIUI本輪未改，正式WS／auth仍未交付，原六主線active。

73472 iOS最後回歸2m44s成功，failure分類3／import25 cases零fail/error/skip，本次boot恢復shutdown。APP分階段提示修正已驗不重做；Android實際上傳仍待backend公開storage端點更新後重驗。若服務仍未交付，下一步續接AI設定／同意／串流UI與其生命週期；原六主線保持active，不代簽人工QA，效能最後。

98749 APP failure提示最後跨JVM／Desktop／JS／Wasm／AndroidHost tests與APP／Device APK／Web entries成功2m54s，分類3cases與coordinator25cases通過；iOS另列。此APP提示／stage修正不重做，backend signed origin仍loopback待維護者；Android實際picker→storage上傳／GUI提示與視覺簽核未完成，原六主線active。

2026-10-05 Android素材錯誤接續：唯讀再查現有QA asset簽發仍localhost:9000，等待維護者配置後重驗，不改後端／signed URL。APP補上第一byte前Uploading(0)狀態、typed file issue／選檔／prepare／transfer／confirm／processing分階段安全提示；原RecoveryRequired asset提示優先、cancel／scope保護與pending-only清理不改。新增前byte失敗case＋failure分類3cases，結果另記。這是診斷可用性修正，不當原Android上傳已通過或storage設定已修。

2026-10-05 使用者Android模擬器素材失敗轉為目前優先診斷：原PNG200214bytes／1262×1308；同Android工作區GET200但assets空，現有QA readyasset正式content GET200的signed storage origin為localhost:9000；後端S3 presign PUT/GET均使用publicEndpoint，Android API預設192.168.68.65:3000。Mac上LAN9000可達403；不改signed URL、不修改／啟動backend。需後端維護者配置可達S3_PUBLIC_ENDPOINT後使用者重驗，尚不當此上傳實機case已完成。

AI composition最終96784成功1m11s（JVM／Desktop／JS／Wasm／AndroidHost／APK／Web）；72724 iOS成功55s，本次boot恢復shutdown，各composition7cases零fail/error/skip。97780取消fixture premature readiness已修，不忽略channel斷言；正式APP設定／context同意UI／secure store／proposal／實際provider仍Pending。使用者新增Android素材錯誤已切回素材診斷，原六主線active。

2026-10-05 接續 ConfiguredAiProvider：正式 backend WS／auth仍無新交付，APP 已將 request body／HTTP SSE／Chat或Messages decoder串接，explicit endpoint／location／version與逐次approve→credential→dispatch門禁；尚無設定／同意／串流 UI或secure store。六項範圍保持，新增六case composition測試結果另記；下一步真正APP設定與review lifecycle，不重做本文或parser。

98833 iOS最後回歸1m37s成功，request本文5 cases零 fail/error/skip；本次新增 boot 已恢復 shutdown。本文 serializer 開發移出重做，正式 transport composition／設定／secret／context 同意／proposal 尚未接線。下一輪沿原六主線核對合約並接APP缺口，不當整體完成。

30965 request 本文回歸成功3m3s：JVM／JS／Wasm／AndroidHost AiRequestBodyTest 各5 cases零 failure/error/skip，APP／Device APK建置成功；Device未執行、實際provider未連線。下一步仍接 explicit profile 與 HTTP／event adapters 的 composition、context同意／secret／設定 UI，不重做本文 serializer；iOS結果另列，六主線仍 active。

2026-10-05 接續 AI request 本文：新增兩方言 explicit model／token budget serializer 與 context privacy／graph／UTF-8 bounds；正式 backend WS／auth 仍未交付。不重做既有 SSE／HTTP parser。此切片尚非完整 provider 接線，下一步將本文組裝與既有 HTTP／event adapters 組合，再接設定／secret／同意與 proposal UI；測試結果另記，完整六主線保持 active。

61321 iOS最後全套33s成功，原生Res品牌1case通過，新增boot恢復shutdown；76671 Host APK fixture已修、完整Host656零失敗，這個缺口移出重做。下一步回正式素材／協作合約與APP保存接線，AndroidDevice／GUI重啟及產品簽核保留，六項goal仍active。

76671 已修1532 AndroidHost品牌資源route：直接驗當次APP APK，不回退source、不忽略原assertions；全套1m35s成功，Host656cases零fail/skip，原失敗歷史保留但不再待修。APP／Device APK、JVM／JS／Wasm品牌regression通過；iOS最後另記、AndroidDevice仍未執行。接續原六主線production／正式合約與實機缺口，不重做品牌fixture／恢复屏障；完整goal仍active。

70483最後iOS回歸39s成功，import24cases通過，新增boot恢復shutdown；suspend barrier開發不重做。下一輪修AndroidHost原生品牌資源test的正確路由／artifact驗證，保留1532失敗；實際AndroidDevice／冷啟動／GUI重開與其他六主線正式交付仍Pending。

Android恢復checked commit／Startup接線、APP background suspend barrier已實作；22874保存3／import24／store9的明確Host切片及APP／Device APK成功4s。1532全套仍有GiphyBrandResourceTest JVM stub Log.d失敗，未放寬／忽略，不當完整Host通過；接續正確資源Host artifact或Device routing。實際AndroidXML／殺APP重啟／cold startup未執行（adb無裝置），iOS本次回歸另記，六主線保持active。

71953 iOS最後回歸43s成功，store9cases通過，新增boot恢復shutdown。Desktop恢復flush與共用write verification開發移出重做；仍補完整網路取消／GUI重開、其他平台保存與正式服務缺口，六項goal保持active，效能最後。

Desktop恢復提示flush修正已接預設MediaRecoveryStore（同prefs位置、不搬資料），common回讀確認與legacy保護已加；62458三目標store9cases／JVM Preferences3cases通過，含獨立JVM halt與重讀。此局部已驗不重做，完整GUI／網路取消→APP重開與其他平台durable、多writer／硬體斷電仍Pending；iOS最後回歸另記。原六項scope保持，詳MEDIA_LIVE_ACCEPTANCE。

67390 Desktop正式檔案sink唯讀live成功5s：實際signed GET／校驗發布／consumer首chunk後取消清理，不新增server資料。此已驗PNG sink範圍不重做；仍補其他平台正式sink／GUI播放、TCP傳输中斷、OS重啟與過期／撤權票券。四live cases普通run均skip，scope與重跑命令見MEDIA_LIVE_ACCEPTANCE；完整六項維持active。重新核對backend仍無正式WS／auth交付。

71572普通JVM／Desktop回歸3s成功，三live cases明確skip，不當新live證據；此次僅JVM harness／docs修改。接續未驗的實際傳输取消／過期／撤權票券或正式平台sink，不重跑上述已通過live案例。

93711 真實素材取消邊界10s通過：完整PUT後未complete取消會pending-only abandon；server complete返回後取消保留同asset／提醒、無自動Node／重上傳。此APP gateway邊界case不重做，實際傳输中斷／UI／OS重啟／過期／撤權票券與產品簽核仍待驗，見MEDIA_LIVE_ACCEPTANCE；六主線goal保持active。唯讀核對backend仍WS／正式auth未做，readiness／DB可用。

76149 iOS Simulator最後回歸成功53s，GiphyClientTest 12 cases通過，新增boot已恢復shutdown；下一頁定位修正不重做，實際服務／UI由使用者確認。

最新追加：GIPHY手動下一頁成功後定位新增結果、新搜尋回頂部；20556三目標GiphyClientTest各12 cases通過，真實服務／UI確認仍Pending。26241新增真實checksum拒絕與APP邊界complete回應遺失復原證據（同asset、不重上傳、不自動Node、明確dismiss），不重做此已驗範圍；實際TCP斷線／OS重啟／過期／取消與票券撤權仍Pending。詳APP_PROGRESS／MEDIA_LIVE_ACCEPTANCE，原六項goal仍active。

本節為目前選工依據，下方較早日期與 session 是歷史證據，不代表仍需重做。下一輪先唯讀確認服務合約是否有新交付；有正式合約就回到素材／協作整合，沒有則先做同範圍內不依賴服務的草稿保存接線。

| 主線 | 已完成、不要重做 | 剩餘交付 |
| --- | --- | --- |
| 1. 素材／GIPHY | 素材 APP 主流程、預覽／恢復、GIPHY 瀏覽與輸入保護；本機 S3-driver 六格式 APP data／worker／ACL／Node保存與新client載入 live整合 | 原生平台 picker／sink／播放、網路／過期／取消／恢復／撤權票券邊界與產品簽核；GIPHY 正式插入、重開與同步及授權modal來源／供應商批准確認 |
| 2. 協作／草稿 | REST reducer、Presence 模型、四平台匯出／匯入唯讀預覽、merge planning／compiler／preflight；typed bundle／transitions／legacy adoption helpers、repository persistence seam、Desktop atomic adapter／client-sequence ledger 與整合測試 | WS auth／join／fan-out／重連／Presence transport；完整 legacy floor／舊 writer 排他／reader routing／bootstrap 與正式遷移啟用；其他平台 durable storage；正式 merge Apply／receipt reconciliation；雙人雙裝置驗收 |
| 3. 快速方案簿 | 本機方案與引用／schema 檢查 | user-level API、revision 衝突、跨裝置與素材授權流程 |
| 4. 共用背景 | 裝置本機背景偏好 | canvasStyle operation／projection、同步、Undo／Redo 與舊偏好遷移 |
| 5. AI | 中立 proposal 模型；OpenAI Chat-compatible／Anthropic Messages 文字事件 decoder／configured composition；有界 SSE byte framing／scoped HTTP transport | Local AI Router 的兩方言設定入口／步驟精靈優先；安全憑證、實際 Router／timeout／TLS／CORS、context同意、串流 UI／proposal 審核與確認後修改／Undo |
| 6. 圖形／鋼筆 | 25 種固定圖形、分類／收藏／最近、Anchor／Bezier 草稿與座標／樣式／視野工具 | 更多自有圖形；新固定形狀實機／服務驗收；正式向量 Node 保存、畫布命中／連線、複製／群組／方案／同步與完整編輯能力 |

### 下一個可執行開發切片

1. 先唯讀重確認服務可用：本機 Media v1 的六格式 APP data／storage／worker／ACL／Node新client載入已完成 live證據，不重做該成功範圍；接續原生平台picker／sink／播放與真實過期／取消／恢復／撤權票券邊界，按MEDIA_LIVE_ACCEPTANCE區分人工QA。另核對GIPHY保存批准／正式插入與WS／auth合約；不猜endpoint、不自行啟動後端。
2. 無新合約時，接續同範圍 APP 保存工作：完整 legacy client-sequence floor 的來源盤點／捕獲、舊 writer 排他與 reader routing，再做 bootstrap／遷移啟用。Desktop ledger 已有跨 Workspace 配號、真實多程序競爭與 kill／reopen 證據；不可自動把 missing ledger 初始化成 0。預設仍 Settings／allocator null，不直接切換使用者資料。
3. 對新增的 production 遷移協調補中斷／重啟／未知結果／多 writer 測試；既有 typed codec、CAS、pure transitions、adoption helpers 與 repository file＋MockEngine fixture 不重做。fingerprint 不等於舊 reader 切換旗標；保存失敗不 POST，未知結果不自動 resend，stopped archive 保留 exact wire。
4. 正式 receipt／provenance（BAI-012）、restore／endpoint（BAI-011）交付後，接逐筆確認 merge Apply／journal／ack／Undo 與原提交 reconciliation；local acknowledgement 不當服務端權威 receipt。Android／iOS／Web 正式 durable storage 與實際服務、双人雙裝置證據仍須補齊，不能以 Desktop fixture 通過取代。

正式 WS／auth 在 backend/PROGRESS.md 仍列未做；merge Apply 仍受 BAI-011／BAI-012 合約限制。規格草案不是可用服務，不猜 endpoint、不修改或啟動他人維護的後端。視覺、VoiceOver、動態島／遮擋由使用者依 QA SOP 簽核；效能排最後。

最新證據摘要：本機六格式素材live整合已由實際APP data／gateway／repository驗storage／worker／ACL／Node保存與新client載入，詳MEDIA_LIVE_ACCEPTANCE；正式UI／平台與服務失敗邊界仍待驗。Desktop ledger5／repository integration4、四目標allocator與Browser固定影片fixtures保留歷史證據，不當production durable storage啟用、完整migration或硬體斷電驗收。原六項goal保持active，沒有建立背景排程。

## 本次更新後的執行焦點

2026-10-05 素材正式服務重新核對：localhost:3000 readiness／DB可用，公開頁storage:s3，已回到優先素材live整合，不繼續重做AI helpers。38758最終live實際重跑成功18s，1test／0failure／0error／0skip，六格式真實APP gateway／coordinator／repository完成storage→worker→ready→校驗下載→Node保存→新client重新載入；viewer讀／寫拒絕、outsider／撤權404及GIF／影片縮圖已驗。66861普通JVM／Desktop全套成功9s，livecase刻意skip，不當新live證據。原生picker／sink／播放／TLS-CORS／過期／取消／恢復／已發票券撤權／正式auth／產品簽核仍Pending，見MEDIA_LIVE_ACCEPTANCE。新增3個QA工作區／9個fixture users保留，不改使用者prefs、不啟動或修改backend、不自行清理。完整六項／使用者人工QA／效能最後保持active。

使用者新增GIF授權確認modal需求（2026-10-05）：希望明確同意APP代下載再插入。若來源為GIPHY，使用者勾選不等於供應商對保存／重驗證的書面批准；官方標準整合禁止partner自行保存。先確認批准範圍或來源，再接實際下載／asset保存，不做假同意即可合規的入口、不偷偷改為另一種產品流程。尚未實作modal／下載保存，詳GIPHY_API_INTEGRATION_SPEC，原素材主線保持。

39155 iOS最後全套成功（41s），HTTP7cases通過，四目標regression補齊，新增boot simulator恢復shutdown。下一步仍正式request／provider／secret／UI／proposal與實際網路驗收；helpers不重做，原六項完整scope／人工QA／效能最後保持。

24321 最後 JVM／Desktop／JS／Wasm tests、Android APK／Web entries成功（2m37s），HTTP7 cases三目標通過；acknowledged rendezvous＋原consumer exception保存修掉跨dispatcher問題，不放寬terminal／cancel／identity assertions。iOS結果另列。下一輪接正式request／provider設定／secret與UI；helper不重做，實際網路／產品簽核仍Pending，原六項完整scope／效能最後保持。

HTTP SSE 接續（2026-10-05）：冷 request／scoped response／status-mime-length gate／body cleanup／timeout配置與 redirect-retry plugin拒絕已加入，不讀 key、不猜 endpoint、不發 provider request。97917 compile失敗已修公開API，14178 JVM／Desktop首輪成功22s；最後回歸另列。AI 下個切片為正式 request／provider設定、secret／context同意與APP接線，不重做SSE／HTTP helpers；實際socket與產品驗收仍 Pending，完整六項／人工QA／效能最後保持。

99269 iOS 最後全套成功（35s），SSE7 cases通過，四目標最後 regression 補齊，新 boot simulator 已恢復 shutdown。SSE byte framing／events 不重做，AI 接續 HTTP／request／gateway／secret／UI／proposal；正式服務合約核對仍優先，原六項完整範圍與使用者人工 QA 保留，效能最後。

2026-10-05 剩餘目標續更新：維持上表六條完整主線，只將已實作 SSE byte framing 移到完成欄，不重做事件 adapters。AI 下個切片為 HTTP request transport；正式服務合約核對仍優先，未交付的 storage／WS／user scheme／canvasStyle／向量 Node 不能靠 fixture 標成完成。人工視覺／VoiceOver／遮擋由使用者簽核，效能最後；不新增背景排程、不宣稱完整 goal 完成。

SSE 本輪證據：42687 首輪六-case 跨 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m51s）；追加總 wire 第7 case 後，36526 JS 預設 Mocha 2s timeout 失敗（36s），JVM／Wasm 邊界通過。功能 harness 明確調為 10s（非效能門檻），不降低 8 MiB 限制、不改 parser 或斷言；61687 全套成功（11s），JVM／JS／Wasm XML 各7 tests／0 failures／0 errors／0 skipped，JVM／Desktop 與 entry 部分 same-content cache。iOS 最後回歸另列；真正 socket／HTTP／provider／UI 與原六項完整交付仍 Pending。

80744 iOS 最後全套成功（35s），Anthropic7 cases通過，四目標最後regression補齊，新boot simulator已恢復shutdown。下一輪優先正式服務條件核對；同範圍AI可接真正bounded SSE／HTTP／request，event helpers不重做，不把文字adapter當完整proposal／AI上線，原六項scope保留，效能最後。

36635 最後 JVM／Desktop／JS／Wasm tests、Android APK／Web entries成功（1m9s），Anthropic三目標各7 cases通過，包含最後limits／unknown delta。部分same-content編譯／Desktop cache，iOS另列；接續真正SSE transport／request／gateway／secret／UI／proposal，不重做event helper，原六項完整scope／人工QA／效能最後保留。

42537 首輪 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m31s），Anthropic 6 cases通過；補第7 limits／unknown delta case後最終結果另列。文字事件層開發與HTTP／UI／proposal正式交付分開，完整六項與人工QA保留。

Anthropic 文字事件接續（2026-10-05）：獨立 named SSE decoder／AiCoworkProvider adapter；message／text block lifecycle、索引／stage、正常 reason＋message_stop，EOF 不成功，unknown 顶層 metadata 不改state，unsupported blocks／delta／異常停止不採用。常數錯誤／bounds／取消／terminal cleanup，不猜 endpoint、不讀 key、不生成 operation。6 common fixture tests，最終回歸確認後另列；HTTP／真正 SSE framing／request／三類服務／secret／UI／proposal／確認提交仍待完成，詳 AI_PROVIDER_STREAMING，原六項保持。

5967 iOS 最後全套成功（48s），AI chat event adapter 7 tests 通過，四目標 regression 補齊，新 boot simulator 已恢復 shutdown。接續完整三類 provider／HTTP／SSE／UI／proposal／確認提交，固定 event decoder 不重做，六項完整 goal 維持 active，效能最後。

27069 的 JVM／JS／Wasm XML 確認 AI chat event adapter 各 7 cases 通過（0 failure／error／skip）；iOS 最後結果另列。接續真正 bounded SSE transport／request、獨立 Anthropic adapter 與正式 gateway／key／UI／proposal review，原六項服務需求與人工 QA 保留。

AI 文字事件接續（2026-10-05）：依官方 Chat Completions docs 補 bounded data-event decoder／AiCoworkProvider Flow adapter，正常 stop＋DONE 才成功，EOF／refusal／tool／異常停止不採用；取消保持取消、terminal 停 upstream、raw error 不顯示，不 retry／生成 operation。23127 首輪 JVM test compile 失敗（27s，assertEquals 單 subtype list 推導），已修正，27069 最終 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m6s）。7-case XML／iOS結果另核對。HTTP／SSE framing／request／gateway／key／Anthropic／UI／proposal／commit／Undo 全部仍待接，詳 AI_PROVIDER_STREAMING；原六項完整 scope 保持。

97167 iOS 全套最後成功（54s），shape geometry 10／library 5 tests 通過，固定 shapes 四目標回歸補齊；新增 boot simulator 已恢復 shutdown。下一輪服務／正式保存條件核對、production 缺口優先；新 shapes 開發不重做，完整向量／其他六項服務與 SOP 人工 QA 保留，效能最後。

80815 最終 shared JVM／JS／Wasm、Desktop tests／Android APK／Web entries 成功（53s），shape geometry 三目標各 10 tests 通過，library 5 cases／全 shapes DTO round-trip 通過；Desktop 同內容 cache。iOS 另列，25 種固定形狀開發不再重做，實機／正式服務及完整鋼筆仍 Pending，效能最後。

固定自有形狀追加（2026-10-05）：19→25，倒三角／直角三角／人字箭頭／雙向箭頭／星形／手動輸入；接物件庫／palette／Inspector、分類與中英 labels，render／hit／relation 共用 polygon。直角三角連線起點用內部重心，不從斜邊上的 box centre 發射；DTO／projection 既有全 shapes loop 涵蓋新增 token。25732 首輪 JVM 失敗（28s）抓出 RightTriangle 邊界零出口，修實作並保留內外 boundary 斷言；56785 修後 JVM／Desktop 成功（19s），最後新增 2 geometry／1 library case 的回歸另列。正式服務／複製方案／手機文字裁切／視覺／VoiceOver 依 SOP 待使用者簽核；不是向量 Node 保存交付，原六項 scope／效能最後保持。

12116 iOS 最後全套成功（46s），floor 5 tests／0 failures／0 errors／0 skipped，四目標最後回歸補齊；新增 boot simulator 已恢復 shutdown。接續完整 catalog／writer 排他／existing ledger 與 capture 世代協調及正式 reader routing／bootstrap，未切使用者資料，完整原六項保持 active。

72180 最終 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m47s）；captured floor 三目標各 5 tests／0 failures／0 errors／0 skipped。iOS 最後結果另列；合併 helper 不重做，接續 complete capture／writer 排他與 reader routing／bootstrap，原六項及人工 QA 保留。

captured floor 合併（2026-10-05）：已加入 legacy counter／pending／typed pending＋ack submitted＋stopped archive 的有界純合併；同 API-user-client 跨 Workspace max、missing 不归零、foreign invalid 不略過，serverSeq 不當 clientSeq。28174 JVM／Desktop 首輪成功（28s）；最後 bounds／5-case 回歸結果另列。接續完整 scope catalog 來源／舊 writer 排他／已存在 ledger 協調及正式 bootstrap，來源 lower bound 不當 complete verified floor，完整六條主線不縮減。

93424 iOS 全套成功（33s），inventory 4 tests／0 failures／0 errors／0 skipped；四目標盤點 regression 補齊，新增 boot 的 simulator 已恢復 shutdown。下一步保留完整 catalog／writer 排他／floor／正式切換；不重做本輪 coverage helper，不宣稱完整 goal 完成。

盤點回歸：7022 JVM／Desktop 全套成功（27s），67388 JS／Wasm 全套、Android APK／Web entries 成功（1m40s）；新 LegacyPendingInventoryTest 三目標各 4 tests、0 failures／0 errors／0 skipped。iOS 結果另列；下一步仍是完整 scope catalog 來源／舊 writer 排他與 verified floor，不將唯讀盤點當遷移啟用。

legacy pending inventory（2026-10-05）：已補所有已發布 v1 pending manifests 對外部 scope catalog 的 coverage gate／唯讀 capture；缺 scope、未知／損毀／超量資料停止且保留 evidence，不從目前工作區推算完整 floor、不反查雜湊或回收 orphan。完整 scope catalog 來源、舊 writer 排他、Settings＋typed receipt／stopped archive floor 合併與 bootstrap／reader routing 仍待完成，這個 helper 不當 production 遷移啟用。新測試結果確認後另列，原六條主線／人工 QA／效能最後不變。

剩餘清單核對（2026-10-05）：原六項目標仍 active；已完成的保存 primitives／repository seam／ledger fixture 移出重做待辦，正式啟用與服務／人工驗收保留。下一輪依上方四個切片執行，視覺、VoiceOver、動態島／遮擋由使用者依 SOP 簽核，效能最後。

Browser fixture 強制重跑補記：49568 JS／Wasm 全套 BUILD SUCCESSFUL（6s）；已核對兩份 BrowserVideoPlaybackTest XML 各 8 tests、0 failures、0 errors、0 skipped，時間戳分別為 2026-10-04T17:18:12.290Z／17:18:10.491Z，較首輪更新。這是固定測試輸入的重複執行證據，不證明歷史問題定因或 production 播放器已修復；先前失敗紀錄與人工 QA 保留。

Browser video fixture（2026-10-05）：fixed VP8無Duration輸入替代不具encoded-frame ack的timer recorder，改善pixel oracle；新增hash／bytes／initial position檢查，沒有改播放器或放寬原native assertions。54809 JS／Wasm全套成功（22s）、影片各8 cases通過，Node4 cases通過；強制重跑結果另列。來源競態風險不等於歷史失敗已定因，人工QA／正式素材E2E與六項範圍保持；legacyfloor／writer排他／ledgeractivation仍待完成。

18119 最終iOS全套成功（31s），reservation2／pending recovery6 tests各0 failures／0 errors／0 skipped，common allocator seam四目標回歸齊備；Desktop ledger5／repository integration4 tests已通過，新boot simulator恢復shutdown。接續legacy floor完整來源／舊writer排他／bootstrap，未啟用production，不把此局部交付當完整六項完成。

53009 單獨Wasm全套成功（40s），reservation2／pending recovery6共用回歸通過，iOS最後內容另行確認；既有影片不穩定性不因本輪成功而解除。Desktop ledger已具process fixture與allocator接線，但legacyfloor／舊writer排他／正式啟用仍待完成。

97601 最終 JVM／Desktop／JS tests、Android APK／Web entries成功（1m16s），ledger5／repository integration4 cases通過，真實獨立JVM競爭與kill/reopen已執行。接續verified legacy floor／writer排他／activation，前輪同prefs鎖與ledger primitive不重做；Wasm／iOS最後內容另行確認，完整原六項與人工QA保留。

Desktop sequence ledger（2026-10-05）：有界binary counter／跨Workspace API-user-client key／generationCAS與optional repository allocator已加入；verified floor才initialize，未知／損毀／tombstone不歸零。90801 JVM／Desktop首輪成功（26s），雙真正JVM／unknown range與注入repository已驗；補第5 kill/reopen case後最終結果另列。預設仍Settings reservation，production bootstrap／legacy writer排他／完整floor遷移／其他平台allocator待交付，不重做前輪同prefs lock。

33924 iOS最後全套成功（31s），reservation2／pending recovery6／ack validation7 tests各0 failures／0 errors／0 skipped，四目標common回歸補齊；新boot simulator恢復shutdown。新wire預留與同prefs多repo串行已實作，接續跨程序ledger／legacyfloor／activation；影片initial frame不穩定仍待實際修復，不把局部完成當六項完整交付。

34801 同內容Wasm全套重跑成功（6s），reservation2／pending recovery6／ack validation7通過，影片suite8通過；8775單獨初始幀失敗保留、未修復，不以成功重跑代簽。iOS最後回歸另列，跨程序ledger／legacyfloor與production activation仍待完成。

8775 單獨 Wasm 615 tests／1 failed（38s）：影片initial paused frame白色assertion失敗，非先前timeout；reservation2／pending recovery6通過。素材／影片不穩定性擴充為單獨執行也可能失敗，不以重跑抹去，原因與實際修復仍待驗。全套重跑及iOS結果另列，完整目標保持。

24830 最終 JVM／Desktop／JS tests、Android APK／Web entries成功（1m8s）；reservation 2／pending recovery 6／ack validation 7 tests JVM／JS各0 failures／0 errors／0 skipped。Wasm／iOS另行確認；新wire預留與同prefs多repo鎖已開發、不重做，下一步跨程序ledger／legacyfloor／activation仍待完成，完整六项保留。

clientSeq reservation（2026-10-05）：新 wire 預留有界 safe range／持久 high-water 後才 stage／POST；失敗留下 gap、不回收，ack只 max advance。同 SessionPreferences 的多 repositories 共用 mutex，並行／跨 workspace 失敗與重開／exhaustion fixture已加入，最終結果另列。不同 Settings instance／process、legacy floor／全域 ledger及舊 writer排他仍 Pending，不宣稱全域序號完整交付。77830 舊 malformed-ack assertion與新預留政策不同失敗，已改驗預留不回收＋pending保留，完整ack gate未放寬。

20958 iOS 全套最後成功（31s），transition 6／pending recovery 4／draft recovery 5 tests 各 0 failures／0 errors／0 skipped，common seam／預設 Settings 四目標回歸齊備，新 boot simulator 已恢復 shutdown。Desktop 最後 4-case integration是 MockEngine＋file證據，尚非真實 backend E2E或production啟用；原六項與使用者人工QA不縮減。

93072 最終 JVM／Desktop 全套成功（7s），repository/storage 整合 4 tests／0 failures／0 errors／0 skipped，最後 wire-only gate 已驗。接續 activation／legacy writer／reader routing／global client sequence，不再重做已接 persistence seam；正式 server receipt 與完整六項產品／實機 QA 保留。

98027 JVM／Desktop／JS tests、Android APK／Web entries 成功（57s）；74809 單獨 Wasm 全套成功（40s）。JS transition 6／pending recovery 4／draft recovery 5 tests 通過；補最後 JVM-only wire-only HTTP 前 gate 與 repository 第 4 case 後另列 JVM／iOS證據。既有 Settings 預設保留，production activation／正式 receipt 仍 Pending。

repository storage seam（2026-10-05）：所有 repository 草稿讀寫已經統一接口，預設仍 Settings；Desktop atomic adapter 由 fixture 注入，尚未 bootstrap 啟用／遷移。stop archive 保留 exact unconfirmed wire，不作 server cancel。81081 JVM／Desktop 成功（7s），repository＋MockEngine＋file 整合 3 cases／transition 6 cases 通過；其他平台最終回歸另列。production legacy-writer exclusion／reader routing／跨程序 global clientSeq／正式 receipt 尚未完成，完整六項服務與人工 QA 保留。

98101 最終 iOS 全套成功（49s），transition 5 tests／0 failures／0 errors／0 skipped，四目標 common 最後回歸已補齊；Desktop typed store 6 tests 通過，新 boot simulator 已恢復 shutdown。接續 production legacy-writer coordinator／reader routing／repository 接線；原子接口不是已啟用 APP 持久保存，不宣稱完整 goal 完成。

12311 最終 JVM／Desktop／JS tests、Android APK／Web entries 成功（1m5s），transition 5 tests JVM／JS、Desktop typed store 6 tests 各 0 failures／0 errors／0 skipped。Wasm／iOS 另行確認；restore／quarantine／exact remove helper 已開發、不重做，正式保存接線及使用者確認／fresh authority 仍待交付。

atomic restore／quarantine／remove 邊界（2026-10-05）：新增 fresh session scope／role／baseline／version／sequence restore gate、保留 unconfirmed wire 的隔離、draft ID＋generation exact tombstone removal；隔離連相同 wire staging 也停止，不宣稱伺服器取消。新共用／Desktop cases 最終回歸另列；production reader／legacy writer 排他與 repository 尚未接、正式 receipt 依賴保留。不再重做前輪 append／stage／ack／snapshot adoption，完整六項產品範圍與人工 QA 不縮減。

1694 iOS 全套最後回歸成功（33s），migration 3 tests／0 failures／0 errors／0 skipped，四目標共用 regression 齊備；新 boot simulator 已恢復 shutdown。接續 production legacy-writer 排他／來源捕獲／reader routing 與 repository 接線，adoption helper 不再重做；正式 receipt／其他平台 storage／原六項產品與人工 QA 保留。

45263 單獨 Wasm 全套成功（30s），migration 3 tests／0 failures／0 errors／0 skipped，既有影片 suite 8 tests 通過。這不是並行不穩定性已修復；iOS 最後內容另行確認，production migration／writer 排他及正式保存仍未完成。

94629 最終 JVM／Desktop／JS tests、Android APK／Web entries 成功（1m13s），migration 3 tests JVM／JS 各 0 failures／0 errors／0 skipped、Desktop adoption 2 tests 通過。Wasm／iOS 另行執行，先前影片不穩定性不因這輪分開執行而解除。

legacy snapshot adoption（2026-10-05）：validated legacy source→typed bundle＋migration fingerprint 的 prepare／Desktop CAS adopt 已加入；同來源重試不倒退、不同來源／tombstone／ack-wire 不確定停止，來源不刪除／wire IDs 不重建。42548 JVM／Desktop 首輪成功（27s），後補 marker 隨 append／stage／ack 保留，最終結果另列。production legacy-writer 排他／原子快照／reader routing、正式 receipt／repository 與其他平台 storage 仍未交付；不將 adoption helper 當使用者遷移完成，完整六項 goal 保留。

本輪最後結果：78985 單獨 Wasm 全套成功（8s）；91376 iOS 全套成功（34s）。transition 3／bundle 3 tests 四目標各 0 failures／0 errors／0 skipped，Desktop typed store 5 tests 通過；新 boot simulator 已恢復 shutdown。68905 並行影片失敗保留為未解不穩定性。接續 migration／reader marker／repository 原子接線及正式 receipt，未開 production 保存、不縮減六項 goal。

68905 並行回歸失敗（1m30s）：Wasm 影片 clock 2000ms timeout，pageHide blob revoke 次數多 1；保留不穩定性待辦與失敗證據，單獨 Wasm／iOS 結果另列，不因重跑通過視為修復。新 transition／typed store 回歸與 production 保存交付仍分開。

atomic 狀態轉移（2026-10-05）：append／stage／ack pure transitions 與 Desktop one-shot CAS update 已加入；一次保存 head＋wire、ack baseline＋FIFO tail＋exact local acknowledgement，重复 ack 不只比 transaction ID。legacy wire-only／只有 marker 的原提交仍需明確 reconciliation，不自動猜成功。此 local evidence 不解除正式 receipt／BAI-012；production／migration／其他平台仍待接。55111 測試漏 import compile 失敗後修正，最終跨目標結果另列；不縮減完整六項目標。

65295 iOS 全套另行成功（33s）；bundle 3／journal 10／pending 8 tests 各 0 failures／0 errors／0 skipped，四目標共用回歸補齊；新 boot simulator 已恢復 shutdown。Native 模型／編譯不證明 production 保存或 UIKit／使用者 QA，完整六項 goal 保持 active。

51308 最終 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m42s）；bundle 3／journal 10／pending 8 tests 三目標各 0 failures／0 errors／0 skipped，Desktop typed store 4 tests 通過。iOS 另行確認；下一步 migration 與正式保存接線，所有原產品主線與人工 QA 保留。

typed bundle 接續（2026-10-05）：已加入 journal＋pending wire scope bundle／codec 與 Desktop typed CAS wrapper，不再只是 opaque storage。共用原 journal／wire validator，驗 head／local operation／base version，保留 wire-only legacy 表達與本機 ack marker；未知 schema 不覆蓋／刪除。正式 receipt 不是本機 marker，BAI-012、migration／reader marker、SessionPreferences／repository 及其他平台仍未完成。36473 JVM／Desktop 首輪成功（20s）；新增共用 identity／一致性 tests 後跨目標回歸結果另列。本機真實資料與後端未動。

Desktop 原子保存底層（2026-10-05）：新增 private local POSIX root 的 binary scoped record、6 MiB bound／checksum／generation CAS／tombstone、同程序 mutex＋穩定 FileLock、file force／ATOMIC_MOVE／directory force。busy／conflict／CommitUnknown 分開，unknown 不盲目 retry、失敗 staging 保留。獨立 JVM fixtures 已驗雙 writer 單一 winner、發布前／後 kill／reopen 舊或新完整資料；最後補 scope mismatch／重入守門後全套回歸結果另列。尚未接 SessionPreferences／typed bundle／migration／repository，不切現有使用者資料，也不是 APP queue 或硬體斷電完成；其他平台與完整六項目標保留，下一步按 durability plan 接 typed bundle 與正式 storage bridge。

pending wire／深度接續（2026-10-04）：wire store 補 manifest 512 chars／UTF-8 累計 bytes 宣告上限，與 journal／備份 reader 共用解碼前 JSON framing／64 depth guard，quoted brackets／escaped quotes 不誤算。encoded 新記錄超深時 chunk publication 前拒絕；legacy valid-checksum 超深記錄保留原文且停止 load／replace／ack，不重建 wire identity、不清證據或自動 resend。這是格式／有界解碼防護，未完成 fsync／atomic scope bundle／migration／跨程序 writer；接續仍依 durability plan，不缩減原六項產品範圍。自動結果另列，效能最後。

43149 最終全套 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（41s）；helper 3／wire 8／journal 10／backup reader 4 tests 三目標各 0 failures／0 skipped。iOS 另行確認，compiled code 的部分 tasks cache。保留原 identity／未知或損毀停止證據不當正式協作、fsync、migration 或完整持久保存完成。

8180 iOS 全套另行成功（34s），helper 3／wire 8／journal 10／backup reader 4 tests 各 0 failures／0 skipped，四目標最後修改回歸補齊；新 boot simulator 已恢復 shutdown。接續仍需 durability plan 的真正 atomic store／process harness／migration／跨程序 writer，而非继续把格式檢查當完成門檻；原六項目標與人工 QA 保留。

journal 狀態一致性接續（2026-10-04）：save／load 共用 validate，超限 version／serverSeq、空白 scope／transaction ID 在任何 chunk 發布前拒絕，原 head／tail 保留。manifest 限 512 chars，讀 chunk 時累計 UTF-8 bytes 不可超過宣告，不組裝超額內容／刪原始證據。51910 全套 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m17s），WorkspaceDraftJournalTest 三目標各 9 tests／0 failures／0 skipped；iOS 另行確認。這是有界保存／讀取一致性，不是 fsync、跨程序 writer 或斷電完成。接續實作順序與 atomic scope bundle／migration／安全 reclaim／process harness 見 [APP_DRAFT_JOURNAL_DURABILITY_PLAN.md](APP_DRAFT_JOURNAL_DURABILITY_PLAN.md)，正式合併／reconciliation 仍待服務契約，完整六項目標保留。

93666 iOS 全套另行成功（46s），journal 9 tests／0 failures／0 skipped，四目標 journal 最後修改回歸補齊；新 boot simulator 已恢復 shutdown。正常 fixture 重建不冒充真正 process／斷電持久性、scope 多 writer 或正式端到端；接續按 durability plan 實作並收集平台證據，其他五項範圍不變，效能最後。

鋼筆精確座標接續（2026-10-04）：新增中心 Anchor 按鈕、上一／下一節點與置中選取，非 pointer 入口可建立及選取現有 Anchor；表單獨立編輯 Anchor／incoming／outgoing 的 X／Y，明確 Apply 才一筆 history。移 Anchor 同移 handles，控制點可單側建立／移除；有限 ±1,000,000／16 chars 及衍生 handles bounds 原子驗證，無效不採用。target selected semantics 與欄位名稱已接，但 keyboard／VoiceOver／原生焦點實機驗收仍 Pending。新座標工具不保存／POST／同步，不將表單當完整 accessibility 或正式向量 Node 交付；完整六項範圍不變，效能最後。

最終 21304 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m9s），PenAnchorCoordinatesTest 三目標各 4 tests／0 failures／0 skipped；iOS 另行確認。座標與控制點模型、表單編譯不證明實際 input／selection／focus／輔助科技或正式保存；SOP 已補中心新增、精確編輯、邊界、捲動與 keyboard／VoiceOver 人工步驟。

邊界修正後 45089 全套 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m18s），座標 5 tests 三目標各 0 failures／0 skipped；「轉曲線」產生超限 handles 時改安全提示、不部分採用。此結果取代上方 4-case 首版作本輪最新證據；iOS 最後修改另行確認，完整產品及人工 QA 仍 Pending。

74411 最終 iOS 全套成功（27s），座標 5 tests／0 failures／0 skipped，四目標最後修改回歸已補齊；新 boot simulator 已恢復 shutdown。此輪新增／座標／選取 semantics 僅列 APP 開發與模型／編譯證據，原生輸入、keyboard／VoiceOver、視覺／遮擋及正式向量保存／同步仍待交付或驗收，完整 goal 保留。

鋼筆填描邊接續（2026-10-04）：沿用既有 ColorPickerWindow，填色／描邊獨立預覽與套用，取消／Escape 只移除預覽、保留 editor；套用一筆 history、同色 no-op、拒絕 stale draft。可選 fill-only／stroke-only，但不允許兩者皆關；custom local stroke width 大於 0 至 128、NonZero／EvenOdd 規則。色盤開啟停用 path／history／camera，背景 Workspace gate 仍有效；tool area 可捲動，保留繪圖空間，寬度欄位跟隨 history。自動結果另列，實際色盤輸入／觸控／小螢幕／視覺 QA Pending。仍是單 contour、未保存草稿，正式 Node 保存／完整向量操作／selection／relation／clipboard／group／方案／同步及可及性保留，效能最後。

57022 最終全套 shared JVM／JS／Wasm、Desktop tests、Android APK／Web entries 成功（1m56s），PenStyleColorEditTest 三目標各 4 tests／0 failures／0 skipped；iOS 另行確認。色盤／工具捲動及描邊欄位實際操作不以編譯／模型測試代簽，完整六項目標未刪減。

33700 iOS 全套另行成功（1m24s），PenStyleColorEditTest 4 tests／0 failures／0 skipped，四目標樣式回歸補齊；新 boot simulator 已恢復 shutdown。Native UI 編譯與 pure tests 不代替色盤／焦點／取消／捲動的實機驗收。下一步正式保存／畫布接線仍依向量合約，其他服務整合優先序不變，效能最後。

鋼筆視野接續（2026-10-04）：接入裝置本機 camera／共用 screen↔local transform、放大／縮小、明確平移模式、重設與 fit path＋所有 handles。fit 包含 viewBox 與未選控制點，找回超出視窗的合法 handles；Pan 以 pointer down camera 與累計 screen delta 計算，取消／resize／多指還原 camera。視野不修改 path／Undo history，不保存或同步給協作者。實際 gesture／觸控與產品 QA 仍 Pending；正式 Node／operation 保存、完整 style／可及性／selection／relation／clipboard／方案／同步保留。

最終 5330 全套 shared JVM／JS／Wasm、Desktop tests、Android APK／Web entries 成功（1m45s），viewport 5 tests 三目標各 0 failures／0 skipped。iOS 另行回歸；下一步仍保留正式向量保存／production selection／relation／完整 style 與無障礙，不能把 editor camera 當完整鋼筆或同步交付，效能最後。

80583 iOS 全套另行成功（1m35s），viewport 5 tests／0 failures／0 skipped，四目標共用視野回歸已補齊；新 boot simulator 已恢復 shutdown。Native 編譯／模型測試不替代實際 Compose pointer／觸控／小螢幕及產品簽核，QA SOP 人工項目維持 Pending。

物件庫分類接續（2026-10-04）：新增全部、幾何、流程、箭頭、文字、容器／泳道、圖表七種篩選，與搜尋、收藏及有序最近使用交集。分類面板預設展開；選取分類與展開／收合保存為裝置本機偏好，重開沿用。未知 token 回全部，失敗提示不阻止當次篩選；不保存搜尋內容、不改 Node／Workspace 或冒充跨裝置同步。既有圖形可屬多分類，未增加假的向量 Node。

最新證據：72408 全套 shared JVM／JS／Wasm、Desktop tests、Android APK／Web entries BUILD SUCCESSFUL（1m42s）；ComponentLibraryPreferencesTest 三目標各 6 tests／0 failures／0 skipped。iOS 回歸另行確認，實際面板、手機換行／焦點／遮擋與重開 QA 仍 Pending。下一步保留鋼筆正式保存／viewport／可及性與完整畫布整合，服務六項範圍不縮減；效能最後。

補齊 iOS 證據：25246 全套 iosSimulatorArm64Test BUILD SUCCESSFUL（1m13s），分類／偏好 6 tests／0 failures／0 skipped；本轮分類共用測試四目標已通過，Native UI 編譯成功。新 boot simulator 已恢復 shutdown，實際操作／產品簽核仍 Pending，不縮減後續工作。

鋼筆編輯入口接續（2026-10-04）：Command Palette 搜尋「鋼筆／pen」開啟未保存草稿視窗。新增／編輯 modes、點擊 Anchor／拖曳新曲線 mirrored handles、選中控制點獨立拖曳、開放／封閉、fill toggle、corner／curve、delete、Undo／Redo；preview 在 pointer up 才作一筆 history，cancel／resize／multitouch 不採用。背景 Canvas native shortcuts／pointer／Presence 在視窗期間 gate。關閉有二次丟棄，canvas clip 不覆蓋其他 controls；role／Workspace scope change 仍移除私密草稿。不提供假的 Node 插入／保存／同步；正式 Node、stroke／color UI 完整設定、viewport／accessibility、clipboard／方案／同步仍全部待接，不能把 temporary draft editor 當完整鋼筆交付。

已補 toolbar 分組、基本細／中／粗描邊與 role downgrade cleanup。下一步正式 Node／operation／capability 保存整合、palette／viewport／更多可及性、實際 pointer QA 與完整 silhouette／選取／relation，整體畫布／複製／群組／方案與跨裝置需求未刪除。

最新證據：27472 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m36s），66031 iOS 全套成功（44s）；gesture 4 tests 四目標各 0 failures／0 skipped，simulator 已恢復 shutdown。Native UI 編譯不替代 UIKit／Compose pointer／手機／產品 QA；效能最後。

向量幾何接續（2026-10-04）：新增 adaptive quadratic／cubic flatten、EvenOdd／NonZero fill hit、round-cap／join stroke hit、first forward contour／centreline ray intersection；open fill 隱式封口、open stroke 不封口。local tolerance 與 32768 points／16 subdivision depth 上限，超量拒絕，不退回方框。新增旋轉／非等比縮放 world↔viewBox hit／ray helper；Compose draw helper 的 fill／stroke 使用相同 commands 與 round policy。尚未接鋼筆 UI／Node／選取／relation production caller；ray 尚非精確 stroked silhouette offset，重疊 contour／完整外框策略需驗證，不能宣稱連線功能完成。正式保存／同步仍 Pending。

最新證據：78381 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m22s），61582 iOS 全套成功（1m12s）；geometry 7＋pen model 8 tests 四目標各通過，simulator 已恢復 shutdown。UI／實機／正式服務／產品視覺 Pending，效能最後。下一步實際 Anchor 編輯與 production renderer／hit／relation 接線，保存／複製／群組／方案／同步仍全部保留。

鋼筆接續（2026-10-04）：開始 VectorPath commands／style／schema／bounds、PenAnchor handles／single-contour draft、curve conversion 與 bounded local Undo／Redo，以及 Compose Path command adapter。這是完整鋼筆的基礎，不是功能已上線；畫布 gesture／Anchor UI、命中／連線、Node／projection／操作／clipboard／Quick Scheme／保存與同步仍待開發。正式 kind／capability 與 restore 合約未交付，需求見 BACKEND_VECTOR_PATH_API_SPEC（Proposal）；不把 arbitrary properties 的接受當支援。

鋼筆核心最新證據：84515 shared JVM／JS／Wasm＋Desktop tests／Android APK／Web entries 成功（1m44s），8 PenPathDraftTest 三目標各通過；本輪未跑 iOS，Compose adapter 僅編譯，實際 gesture／繪圖／觸控及正式服務 Pending。下一步接 Anchor 編輯與共用渲染／命中／連線，不重做核心或跳過正式保存／同步。

重新規劃／撤權接續：當 preflight 發現更新，停止舊欄位選擇／檢查，只有使用者按「載入最新比對並重新選擇」才替換 remote comparison；original draft／operations／flags 不改，舊 choices／checked message 清空，review key 更換取消舊 coroutine。scope expired 關閉 preview；正式 access-loss HTTP 在 callback gate 內標記 connectionFailed，不再保留可用檢查結果。preflight 在第一個 await 前複製 choices，防 caller map 被改。

最新證據：15570 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m43s）；12594 iOS 全套成功（1m18s），preflight 8 tests 四平台各 0 failures／0 skipped。simulator 已恢復 shutdown，Compose／UIKit／實機／正式服務與人工 QA Pending；效能最後。

原提交 reconciliation 缺口已列 BAI-012：v1 備份 domain IDs 並非 wire IDs，無來源 receipt；有限 REST catch-up／false flags／本機無 pending 都不能證明原請求 settled。正式套用仍待 provenance／完整 receipt 與 late-original settlement／fence 合約，不猜新路由或把未知當完成。

合併 preflight 接續（2026-10-04）：新增 checkDraftMergeProposal 與匯入視窗「重新檢查權限與素材」。先 fresh edit ACL／同 user-client-workspace／review version-sequence，再拒絕本機 pending submit／draft、備份 quarantine／unconfirmed 與 contract gaps；只讀 changed media 的 before／after 引用 metadata，檢查 ID／Workspace／kind／新引用 Ready／縮圖 Image，再次 refresh 與 pending 檢查。版本變更要求重新驗證、重新選欄位，無自動採用。通過只顯示本次唯讀檢查結果，不存 proposal／POST／journal，不等於原提交已 settle 或持久授權。

最新證據：79164 JVM／Desktop／JS／Wasm tests、Android APK／Web entries 成功（1m41s）；preflight 6 tests 三目標各通過。本輪未跑 iOS，Compose 真實操作／正式服務／人工 QA Pending。完整過程與限制見 APP_PROGRESS。

下一步仍是正式原提交 reconciliation／restore 與 endpoint 合約、二次確認和 Apply／journal／ack／Undo 接線；已補 preflight 開發不得當整個匯入套用已完成。四平台服務／實機與人工 QA Pending，效能最後。

素材引用接續：新增 UpdateMediaReferenceOperation／序列化／History rebase／既有 update_object.properties wire mapping，compiler 改為原 Node 更新，不刪除重建、也不重建相連 relation。thumbnail 明確 null 清除；鎖定、版本與 before-reference 不符拒絕。正式 ACL／素材校驗／Apply／journal／ack 與真實雙端仍 Pending。下方先前「素材替換只能 delete＋create」紀錄已被此更新取代；型別、relation endpoint、restore 缺口仍保留。

最新素材操作證據：87892 全套 shared JVM／JS／Wasm＋Desktop tests／Android APK／Web entries 成功（50s）；另行 14936 iOS 全套成功（49s），4 reference＋8 compiler tests 四目標各通過。部分 compile／Desktop tests cache；真實 backend、原生 UI 及人工 QA 尚未驗收，simulator 已恢復 shutdown。

最新接續（2026-10-04）：逐屬性合併計畫與 UI 已完成開發，新增純 domain operation compiler（文字／transform／屬性／群組／素材替換／關聯），不可當作正式套用已完成。compiler 使用新 ID、目前版本、原子展開 200 筆上限；最後鎖定拆為獨立 lock-only，讓 inverse 先解鎖。新增唯讀 contract gap assessment，標示 object／relation ID 重用與 Undo／Redo restore 缺口；不等於授權或 tombstone 查詢。後端 soft-delete ID 不能 recreate，素材替換與 relation endpoint 替換目前只能本機 replay，正式提交仍未接。具體缺口見 BAI-011。

最新證據：15880 全套 shared JVM／JS／Wasm 回歸成功（1m12s），compiler 7 tests 每目標各 0 failures／0 skipped；本輪最終修改未跑 iOS／Android／Desktop entry，真實服務及人工 QA 仍 Pending。

下一個實作入口：補正式 restore 與 endpoint 合約，再接 fresh edit ACL、素材引用授權、原未確認提交 reconciliation、最新版本重檢、二次確認與 journal／ack／Undo。素材引用 update operation 已完成 APP 開發，不再重做。不能跳過 contract gap 直接送 compiler 產物。四平台草稿選檔、逐屬性計畫不再重做；原生 QA 仍 Pending，效能最後。

1. 素材主流程不重做：只補正式 storage／worker／ACL 端到端證據及實際缺陷；GIPHY 正式畫布插入／重新開啟／同步仍待合約與開發。
2. 協作主線：核對正式 WS 認證／capabilities／ticket／事件合約，接 transport、operation fan-out、join／catch-up／live barrier，完成雙人／雙裝置驗收。已完成 APP Presence model／runner／分享 UI／scope／activity gate 不再重做；無 transport 時維持「未連接」。
3. 合約未就緒時的 APP 收尾：補匯入／schema 檢查／逐筆明確合併及 journal durability 缺口。不重做 Desktop／Android／iOS save、Web 下載請求、唯讀比對、剪貼簿備份及 baseline 恢復入口；原生 picker／provider 與實際 browser 下載驗收仍保留。
4. 接續產品順序：快速方案簿跨裝置同步 → 共用畫布背景 → AI 三類 provider → 自有物件庫／鋼筆。
5. 人工驗收獨立保留：原生 picker／provider／lifecycle／多視窗／觸控／焦點與雙端清除待驗；視覺、動態島、遮擋由使用者透過 QA Workbench 簽核，效能最後。

正式服務合約未就緒時，亦可補原六項目標內不依賴後端的 APP 缺口。收藏／最近使用、19 種固定圖形與無外框文字均已完成開發，不改變正式協作 → 方案簿同步 → 共用背景 → AI 的服務整合順序，也不把物件庫局部完成當整體交付。

### 本輪完成開發：iOS 草稿另存（人工驗收仍 Pending）

- folder picker、獨立 destination、scope／取消 gate、security-scoped grant、協調寫入、UTF-8／排他新檔與 partial cleanup 已接。只選資料夾，程式產生 UUID 檔名，不保存 bookmark、不支援匯入。
- 原 `NSCocoaErrorDomain:512, accessor=false` 在 standalone runner 重現；相同測試與產物改用已 boot 的 simulator／非 standalone 後通過。shared 測試設定已使用 `standalone=false`，保留真實 Foundation 測試，不跳過或改用未協調寫入。save-as creation intent 使用 ForReplacing，實際 accessor 仍 O_EXCL，不准覆寫。
- 最終 session 5057（43s）全套回歸成功，iOS 檔案測試 5 tests／0 failures／0 skipped，共用 export 4 tests 四平台通過。原生 Files／iCloud／外部 provider／UIKit 與產品 QA 仍 Pending，不把 booted sandbox 測試當完整交付。

### 接續完成開發：Web 草稿 JSON 下載請求（實際下載 QA Pending）

- JS／Wasm Web entry 已接 browser runtime；二次确认、fresh read ACL／draft identity／scope／取消 gate 後才產生本機 JSON Blob，下载前再檢查 scope／頁面可見性。程式產生不含私密名稱的 UUID 檔名，沒有檔案系統 handle／指定目的地／自動匯入／上傳。
- 結果另列 DownloadRequested，UI 不冒充「已保存」。瀏覽器控制位置、命名、覆寫及完成狀態，可能封鎖／取消；保留原生 Saved 結果、不把 Web 當 O_EXCL／0600。成功請求 object URL 60 秒後 revoke、錯誤立即清理，不自動 retry。
- 最終 session 22356 BUILD SUCCESSFUL（2m51s），共用 export 現 5 tests 四平台通過、Web 3 tests 在 JS／Wasm 各通過；native／Desktop／Android APK／Web 編譯亦成功。DOM fixture 不會真的下載到磁碟，瀏覽器下載清單／實際 JSON／取消／焦點／視覺仍由使用者簽核。

本清單是後續 loop 的執行依據。新 goal 的 objective 保留上述六項剩餘產品範圍及 QA／後端邊界；不為清理待辦而將未完成的整體目標標成完成。

### 本次清單更新的邊界

- 本次只更新執行文件與系統剩餘 goal，沒有新增功能或重跑測試。上述 iOS／Web 匯出與 19 種圖形是先前已完成紀錄；未啟動或修改後端、不代簽驗收。
- 已完成 APP 實作移入下方紀錄；服務整合、實機測試與使用者 QA 簽核仍保留，不因移出開發待辦而消失。
- 下一輪先確認正式 transport 的可用合約；未確認前可補上述 APP 獨立缺口，不猜測 endpoint、不自行啟動或修改其他 agent 維護的後端。

### 下一輪選工與依賴

- 先唯讀核對後端交付文件：正式 WS auth／capabilities／ticket／event 合約齊備才接 transport；缺少時回報具體合約欄位，不把既有 REST 或 transport-neutral tests 當正式同步。
- 合約仍缺少時，優先按上方切片接 typed atomic scope bundle／fixture migration／repository；草稿有界 reader、唯讀預覽與合併 planning／compiler／preflight 已完成，不再重做。鋼筆仍可推進不依賴正式向量服務的 APP 缺口；不能將備份的 `importSupported=false` 直接改為可匯入，也不能自動送出舊 operation。
- 素材／GIPHY、方案簿、共用背景與 AI 的正式服務依賴按下方各節補齊。原生操作、視覺與遮擋繼續列 Pending，交由使用者依 SOP 記錄證據；效能最後。

## 已實作，不再重複開發

- 素材 APP upload／download、完成確認、checksum／大小與授權檢查、縮圖引用、Image／GIF／Video Node。
- 素材庫、預覽重試、上傳確認不確定時的 recovery／本機記錄、晚到縮圖 metadata 有界回查。
- 素材 clipboard／duplicate／方案引用檢查，整組成功或整組停止。
- GIPHY 瀏覽原型、搜尋／Trending／手動分頁、官方 attribution、靜態及明確播放 GIF 預覽、輸入焦點保護。正式畫布插入仍未完成。
- 完整 committed ack、首次 wire 保存、明確重送／停止本機重送入口。
- 本機草稿 journal：先保存再 optimistic publish、scope／checksum／manifest-last、FIFO tail 保留、ack receipt 跨 wire cleanup 邊界去重、權威 baseline／編輯權限確認後由使用者明確恢復並送出、停止重送後隔離，以及兩次確認移除本機草稿。QA Workbench 新題預設 Pending；實機／斷電／多程序／衝突解決仍待完成。
- 草稿唯讀屬性比對與 JSON 剪貼簿備份：fresh read ACL、scope／epoch 發布隔離、取消背景鍵盤命令接管、未確認／隔離提示，無 POST／journal 修改／素材下載；原生另存按平台分列，匯入及 merge 未完成。
- Desktop 草稿 JSON 原生新檔另存與共用 export coordinator：fresh ACL after picker、identity／scope／取消 gate、不覆寫、不自動開啟／上傳／匯入；共用 tests 現為 5 個四平台通過、2 個 Desktop file tests 通過。原生 picker／實機權限／durability 與 merge 仍待驗，其他平台另列。
- Android 草稿 CreateDocument／空文件 probe／provider write 與 awaited disposal 接線、picker 重建／晚到結果隔離；共用 export tests 現共 5 個四平台通過。Desktop files tests 沒有驗證 Android provider；原生裝置驗收仍待完成。
- iOS 草稿 folder destination、獨立 picker／grant／取消與 late callback 隔離、NSFileCoordinator save-as intent 與 O_EXCL 新檔寫入；5 個 native file tests 在已 boot simulator 通過。外部 provider／原生 presentation 尚未人工驗收。
- Web 草稿 Blob 下載請求／DownloadRequested 結果與獨立警告／失敗訊息，JS／Wasm DOM 3 tests 各通過；瀏覽器實際保存、封鎖／取消及 UI 仍待 QA，不宣稱原生 destination／可撤回。
- 七種 operation 的原子 projection reducer，已接 REST catch-up、scoped raw projection cache、snapshot fallback。
- REST 401／403／404 撤權分類及 Failed save reconciliation 的舊權限寫入封鎖已接 UI；真實 membership／角色變更與 observer lifecycle 仍待整合驗收。
- 跨頁 catch-up assembler 已接線並回歸；去重已依 actor＋operation ID 修正並驗證，不能因此宣稱完整協作已交付。
- Presence transport-neutral model／observer、scope／角色／subscription gate、epoch／lease／TTL、draw-only 游標／旋轉選取 overlay 與成員連線狀態入口；8 個 common tests 四平台通過。Backend repository 預設無 stream，雙向傳輸與正式服務驗收仍未完成。
- Presence transport-neutral outgoing publisher／共用 runner／世界座標與權威選取正規化，加上畫布事件／分享開關／Live owner gate；7 個 common tests 四平台通過。optional publisher 預設 null，UI／服務未驗收，不假稱已送出網路資料。
- 平台 activity 來源、terminal tracker、iOS controller／scene 隔離與送出前 direct StateFlow／epoch gate；3＋2 個 common tests 四平台、1 個 DOM event test JS／Wasm 通過。原生 lifecycle／通知路由／多視窗實機與真實 leave／TTL 仍待驗收。
- QA Workbench 與 SOP 的素材／協作驗收入口。
- 內建物件庫全部／收藏／最近使用切換、本機穩定元件 ID 偏好、20 項去重最近排序與既有搜尋組合；成功本機插入後更新、拒絕／取消不記錄、手機失敗不關面板。3 個偏好／view tests 四平台通過；Compose 插入／取消／焦點／可及性與視覺仍待人工 QA，不宣稱新增大量圖形或鋼筆。
- 固定自有 NodeShape 由 9 擴充至 19：無外框文字、三角形／五邊形／八邊形／梯形／加號／四向箭頭；物件庫／Inspector／Command Palette 已接。新 polygon 共用繪製／命中／射線端點，文字無背景／陰影、選取輪廓、字色與縮圖線段。四平台 geometry／payload／projection 回歸通過；實機視覺、正式後端冷啟與雙端同步、舊 client 相容限制待 QA，不能算大量形狀／鋼筆已完成。
- 草稿備份 reader 核心：既有 v1 JSON 嚴格欄位／工作區／安全序號、有界 4 MiB／64 層、200 筆 operation、物件 key／群組／relation 引用及 baseline replay 等於 proposed 檢查；錯誤不帶私密 decoder diagnostics。4 tests 在 JVM／JS／Wasm 各通過，全套三目標回歸成功（session 55896，41s）。尚未接匯入 UI／picker／fresh ACL／逐筆合併，不修改 `importSupported=false`，歷史 current 不是授權或目前權威。

證據及限制見 [APP_PROGRESS.md](APP_PROGRESS.md)。最新 40475 全套成功（1m22s）：JVM／iOS Simulator／JS／Wasm tests、Desktop tests、Android APK 與 Web 編譯；匹配輸入的部分 compile task 使用 cache，相關 tests 執行。fixtures 不代替真實端到端。

## 1. 素材正式整合與交付

- 取得可用後端 QA 服務與可寫 scope，驗證真實 object storage／worker、upload／download、完成確認／縮圖、失敗恢復及跨使用者 ACL。後端由其他 agent 維護，不自行啟動／修改。
- 不重做素材 APP 主流程；僅修實際驗收發現的缺口。
- GIPHY external-media Node／重新開啟／同步合約及畫布插入；依官方 API 規範完成 GIF Browser，而非只交付瀏覽原型。
- 由使用者簽核視覺、播放／聲音、動態島與遮擋。

## 2. 多人即時協作：APP 開發主線

- 確認正式 WebSocket capabilities／認證／ticket／事件合約與 fixtures，不猜測 endpoint。
- 接入 operation fan-out、scope／connection epoch、join／catch-up／live barrier、原子 publication；保留 REST polling fallback。
- Presence／上線成員、world-coordinate 游標與選取的正式 transport 與本機更新送出，不分享個人 viewport／zoom。保留已完成的 APP model／顯示／隔離入口，不重做；自動測試不代表已交付同步。
- 保留已完成的 iOS controller／scene／application 與其他平台 activity 接入；初次 window attach／scene 移轉／通知路由／disconnect 的 UIKit 實機驗收，以及背景 close／leave／前景重新確認分享、Compose pointer／焦點／scope 競爭與真實兩端清除仍待驗收。
- 斷線重連／server sequence catch-up、撤權／角色變更、pending acknowledgement reconciliation。
- 補草稿匯入／版本檢查與逐筆明確合併；保留已完成 Desktop 新檔另存／Android CreateDocument／iOS folder export／Web 下載請求／唯讀比對／JSON 複製備份／journal 與 baseline 相符恢復入口。另驗證 Android／iOS provider／原生 picker、實際瀏覽器下載、跨程序 writer、崩潰／斷電持久性、orphan chunk 清理及本機 schema migration；不把 Settings manifest-last 當完整 durable queue。
  - 有界 v1 reader／replay 驗證、Command Palette 明確貼上 JSON／fresh read ACL／scope gate／唯讀比對及 Desktop／Web／Android／iOS JSON 選檔已接。Web 4 expect/actual tests、iOS 4 native read tests／共用 reader 與 coordinator 回歸通過；最終 83030 iOS、97279 其他 tests／APK／Web entries 成功。選檔只載入輸入區，需另按驗證，不改檔案或保存路徑／URI／bookmark；外部 security grant、UIKit／provider／browser UI 操作仍待驗。下一步逐筆使用者確認合併與新 operation 建立；不得直接重送備份 operations 或將保存的 current 作權威。此處仍不支援採用／提交備份；另追蹤並行回歸下既有 Wasm video 的 Mocha 2s timeout／fixture 串擾跡象，不以成功重跑隱藏不穩定性。
- 兩位使用者／兩台裝置端到端驗收。

## 3. 快速方案簿跨裝置同步

- user-level CRUD API、resource v2 payload、revision／ETag／衝突流程、schema migration。
- 素材來源／授權／retention／跨畫布 materialization，不以 client 重傳替代。
- 保留已有本機方案及 schema／來源檢查，接同步並驗收，不重做整套本機方案簿。

## 4. 共用畫布背景

- `canvasStyle` projection／`update_canvas_style` operation、同步與 Undo／Redo。
- 遷移原偏好、處理 concurrent picker；viewport／zoom／個人檢視仍留本機。

## 5. AI Cowork

- 第一階段只支援 Local AI Router 的 OpenAI Chat-compatible／Claude Messages-compatible；不另建 Local 原生協定。先補設定入口／步驟精靈，再接串流與 proposal。
- 上游金鑰由 Router 保管；APP 只設定 Router 認證與 endpoint，憑證不寫共享資料或一般偏好，安全保存／session-only選項須明確。裝置設定不分享協作者。
- proposal 必須確認才修改畫布，驗收取消／錯誤／Undo。

## 6. 物件庫與自訂圖形

- 已有 19 種固定 shape 與圖表範本、無外框文字、七類交集篩選與記憶分類／收合 APP 開發，不重做；繼續補更多基本幾何、流程、標註、容器、架構／拓樸圖形。分類與既有 shapes 的實機／服務 QA 仍待完成。
- 搜尋／收藏／最近使用與分類 APP 開發已完成，不重做；大量形狀與人工 QA 保留。鋼筆已有 volatile Anchor／Bezier 草稿 UI、中心新增／逐節點選取／精確 Anchor 及 handle 座標表單、獨立 fill／stroke 色盤預覽及套用、custom stroke width／fill rule／open／closed、zoom／pan／fit／reset，本機模型及 gesture tests 不再重做；正式 Node／operation／capability 保存、pinch／完整快捷鍵與可及性及畫布 selection／relation 仍待接，實際 editor viewport／色盤／座標／觸控／取消與 VoiceOver 驗收保留。多 contour／opacity／dash／cap-join 擴充不以現有單 contour 與 round policy 冒充完成。
- 自訂圖形群組／複製／同步／存入快速方案簿的完整流程。
- 參考「無邊際」能力廣度，使用 BoarderLess 自有圖形，不複製專有資產或介面。

## 最後階段與完成門檻

逐屬性合併目前進度（2026-10-04）：三方 leaf／整筆存在性選擇、proposal graph／lock 驗證、domain operation compiler、素材引用原 Node 更新、fresh ACL／素材 preflight 與明確重新載入／重選 UI 已完成 APP 開發，相關四目標回歸證據見上方及 APP_PROGRESS；不再重做 planning／compiler／preflight。正式 Apply 仍無提交入口：須先取得 BAI-011 restore／型別及 relation endpoint 合約、BAI-012 原提交 provenance／權威 receipt／late-original settlement 或 fence，再接二次確認／durable journal／ack／Undo 與服務／原生驗收。唯讀檢查通過不等於可送出，v1 備份仍不直接重送原 operations；完整合併未交付。

- 效能測試仍排最後；視覺／實機／遮擋由使用者透過 QA Workbench 簽核。
- 未取得服務／雙人／實機證據的項目留待驗，不以自動測試代簽。
- 不為修改清單而將尚未完成的整體 goal 標成 complete。
