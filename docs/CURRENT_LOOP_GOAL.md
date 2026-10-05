# 當前 loop 剩餘目標

更新：2026-10-05。依使用者要求，已完成 APP 開發移至完成紀錄，不再列為重做目標。保留原六項產品範圍與順序；「已實作／自動測試通過」不等於真實服務／實機／產品簽核完成。本次確認系統 goal 仍為 active，沿用原六項完整目標，更新本文件的剩餘執行清單；不另建重複 goal、不宣稱已建立背景排程。

## 最新剩餘工作摘要（優先閱讀）

iPhone配置缺口已修：83922 Xcode解析原Info API確實127.0.0.1:3000且優先於Kotlin fallback；Config.xcconfig增加iphoneos SDK條件192.168.68.67，simulator保留loopback，不改ATS／backend。78986直接Xcode／plutil配置驗證2cases通過2.02s，explicit部署override仍優先；README與QA重驗步驟已補。不重做此配置修正，但使用者截圖平台／build未確認，不当該問題唯一根因或iPhone實機上傳已通過。需重新build/install後重驗，或接續iOS正式sink／來源網路證據；原六主線active、人工簽核與效能最後保持。

7001 Android原生載入唯讀 live 通過1.379s／1case無skip：既有JPEG／WebP及三張poster原生解碼、GIF全部影格、MP4／WebM MediaPlayer prepare／seek command／release与fixture cache清理，前後workspace不變。37369 Device APK成功10s，只更新獨立QA APK，無新增server資料；普通run明確skip。不重做此已驗範圍；影片surface／音訊／實際連續播放／seek落點、picker UI／OS重啟仍Pending。最新iPhone generic失敗截圖待使用者補實機／模擬器、格式與build，Android成功不解除iOS；下一步原生播放或iOS正式來源與sink證據。詳細scope／重跑見MEDIA_LIVE_ACCEPTANCE。正式WS／auth仍未交付，原六主線active、效能最後。

2026-10-05 正式素材簽發已更新LAN origin，因此回素材主線。5118 Android emulator API37實際ContentResolver／source snapshot→signed PUT→worker ready→Node保存→新client載入→正式Android sink校驗下載／原生解碼成功2.635s（1case無skip），詳MEDIA_LIVE_ACCEPTANCE。原使用者截圖／picker UI／OS重啟／Android其他格式播放仍待驗；不把1PNG當所有平台完成。不改backend／signed URL；保留1新QA asset／Node與獨立QA test APK，無使用者資料覆蓋。

556321 AndroidDevice切片：真實SharedPreferences checked commit/XML/dismiss與原生GIPHY品牌Res/hash各1case通過，live case普通run明確skip；不要重做这兩個已驗Device範圍，不宣稱完整Device全套／APP restart。AIUI本輪未改，正式WS／auth仍未交付，原六主線active。

73472 iOS最後回歸2m44s成功，failure分類3／import25 cases零fail/error/skip，本次boot恢復shutdown。APP分階段提示修正已驗不重做；Android實際上傳仍待backend公開storage端點更新後重驗。若服務仍未交付，下一步續接AI設定／同意／串流UI與其生命週期；原六主線保持active，不代簽人工QA，效能最後。

98749 APP failure提示最後跨JVM／Desktop／JS／Wasm／AndroidHost tests與APP／Device APK／Web entries成功2m54s，分類3cases與coordinator25cases通過；iOS另列。此APP提示／stage修正不重做，backend signed origin仍loopback待維護者；Android實際picker→storage上傳／GUI提示與視覺簽核未完成，原六主線active。

2026-10-05 Android素材錯誤接續：唯讀再查現有QA asset簽發仍localhost:9000，等待維護者配置後重驗，不改後端／signed URL。APP補上第一byte前Uploading(0)狀態、typed file issue／選檔／prepare／transfer／confirm／processing分階段安全提示；原RecoveryRequired asset提示優先、cancel／scope保護與pending-only清理不改。新增前byte失敗case＋failure分類3cases，結果另記。這是診斷可用性修正，不當原Android上傳已通過或storage設定已修。

2026-10-05 使用者Android模擬器素材失敗轉為目前優先診斷：原PNG200214bytes／1262×1308；同Android工作區GET200但assets空，現有QA readyasset正式content GET200的signed storage origin為localhost:9000；後端S3 presign PUT/GET均使用publicEndpoint，Android API預設192.168.68.67:3000。Mac上LAN9000可達403；不改signed URL、不修改／啟動backend。需後端維護者配置可達S3_PUBLIC_ENDPOINT後使用者重驗，尚不當此上傳實機case已完成。

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
| 5. AI | 中立 proposal 模型；OpenAI Chat-compatible／Anthropic Messages 文字事件 decoder／注入式 Flow adapters；有界 SSE byte framing／scoped HTTP transport | 完整 request／能力／三類 provider 設定與正式 transport 接線、實際網路 timeout／TLS／CORS、串流 UI／Key 管理／proposal 驗證與確認後修改／Undo |
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

Anthropic 文字事件接續（2026-10-05）：独立 named SSE decoder／AiCoworkProvider adapter；message／text block lifecycle、索引／stage、正常 reason＋message_stop，EOF 不成功，unknown 顶層 metadata 不改state，unsupported blocks／delta／異常停止不採用。常數錯誤／bounds／取消／terminal cleanup，不猜 endpoint、不讀 key、不生成 operation。6 common fixture tests，最終回歸確認後另列；HTTP／真正 SSE framing／request／三類服務／secret／UI／proposal／確認提交仍待完成，詳 AI_PROVIDER_STREAMING，原六項保持。

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

- OpenAI-compatible、Anthropic-compatible、Local AI Server 統一 adapter／串流事件／proposal 格式。
- 遠端金鑰預設後端保管；Local AI 設定只留裝置，不分享給協作者。
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
