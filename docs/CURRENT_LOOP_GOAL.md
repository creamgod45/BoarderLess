# 當前 loop 剩餘目標

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

- 2026-10-06 完整native遷移publication coordinator已實作／驗證：先保存綁target／完整sourceDigest／exact artifact digest與generation的Preparing receipt，再跨scope draft／safety及共用namespace ledger發布，全讀回才CAS為Published。全native record catalog排除未知／外來／symlink，absence保持absence；同receipt的partial resume只補缺artifact，既有generation1必須exact match，advanced／tombstone／corrupt與source drift拒絕且不rewind，Published丟檔不補。每步必要writer exclusion callback無default，不把合作lease或double read當舊writer排除。新增7cases、33個publication fault及15個真正独立JVM kill；97160全套JVM806／Desktop46和跨平台檢查成功12s、零fail/error、shared6skip。來源legacy／staging保留，不啟用production、不改userprefs、无HTTP。下一步利用Published receipt接activation／runtime routing及真正legacy exclusion，再跨檔ACK settlement與fresh review→Apply；不重做已驗publication coordinator，詳APP_DRAFT_JOURNAL_DURABILITY_PLAN。

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
