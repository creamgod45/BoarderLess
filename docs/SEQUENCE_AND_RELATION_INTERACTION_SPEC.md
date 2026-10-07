# 時序圖與連線互動規格

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

2026-10-06 鋼筆正式本機畫布插入接線：TextNode 增加可選 structured VectorPath 自訂輪廓（既有 shape 為普通節點 fallback），attributes 包含完整路徑供 CAS／inverse／merge；Desktop FileRepo supportsVectorPaths=true。Pen editor 增加明確插入；捕捉 actor／workspace snapshot／version／serverSeq，queue／recovery／連線與 scope 守衛；预覽、取消無 retainDraft。首次已準備 insertion identity 固定，保存失敗保留 exact operation，editor fields／pointer 暫停；非法／不可見／超大／過度複雜路徑仍可修改後重試。插入沿 execute→retainDraft→history／queue，single CreateObjectsOperation，path 不當 SVG markup／URL／media asset。控制點與 stroke bounds 正規化為非零 viewBox，拒絕 renderer geometry budget 外的路徑。WorkspaceScreen 達 JVM method 上限，鋼筆 consent／capture／retry 元件拆到 PenCanvasDialog，沒有關閉編譯檢查。

自訂輪廓使用現有向量 command renderer／fill-stroke hit test，node move／resize／rotate／group／lock／text label 沿現有操作保留 path；shape presets 是明確轉回 ordinary shape，Undo 恢復 path。primary color 修改 fill 或 stroke，原 style 其餘保留。Auto／manual／loop ports 使用 vector contour；stroke silhouette 仍為 contour／centreline近似，未聲稱 exact stroke offset。selection 升 v6，舊 v1..5 禁止 vector payload；方案檔與本機方案簿支持 v6，路徑 local coordinates 隨 node transform 搬移、preview 畫 vector。Clipboard explicit nulls 必須保存關閉 fill／stroke，否則解碼會誤補預設 paint；本輪 native reopen 測試發現並修正此真實問題。方案簿新增 strict payload／版本一致驗證；v5 loop fixture 改為明確 v5，future rejection 改 v7，不放寬 guard。Backend 在 append／DTO conversion 前拒絕 vector create／delete／attrs（nested 亦拒），projection 含 vectorPath（包括 null）reject，merge 有 VectorPath contract gap。AI graph 標 custom-vector 且 tools 不編輯 unsupported path，pen capability 維持 false。

保存重試補強：Desktop FileRepo retainDraft 現在可核對已發布同 ID／digest／after version／完整 after workspace，再 force 既有 generation 而不追加或重寫；不同內容或任何較新的文件明確拒絕，因此 publication unknown 後原插入可明確恢復，不能 rewind 後續標籤／其他編輯。Native fault test 覆蓋三種檔案 checkpoint、跨 repo 重開、單 activity、同 receipt 重試、同 ID 異內容／較新文件拒絕。vectorPath=null 在 ordinary node／attributes serializer 明確不輸出新欄位，保持既有 operation receipt bytes；有 path 時則完整保存。向量節點零尺寸 transform，以及將 path 附到零尺寸普通節點，皆回傳 rejected，不讓 copy constructor 例外穿過 history。

驗證：48946 最終隔離全套成功2m1s：JVM930（6既有skip）、JS834、Wasm834、Desktop46均0 failures/errors；iOS arm64／Android main compile、新 Desktop app 成功，更新 desktopApp/build/verified/BoarderLess.app 並核 PenCanvasCreation／Dialog／Editor／VectorPath／FileRepo classes。Common PenCanvasCreationTest 8cases：控制點+stroke bounds／serialization、無圖形+超大+foreign拒絕、普通節點不新增null欄位、actor／ACL／完整snapshot／checkpoint守衛、attrs／轉普通shape與Undo／Redo／text／transform／zero-size拒絕+merge vector gap、v6方案檔+fill/stroke null完整保留與舊版本拒絕、Backend零draft／seq／HTTP、contour hit／rotation／relation ports；projection另增vector與null拒絕。Native2cases：preview檔案不變→真history插入→reopen→方案簿→Undo／Redo→reopen，以及三fault的同payload保存確認／不重複activity／異內容或較新canvas拒絕。初次compile漏imports／WorkspaceScreen method超大、fixture API名稱／nullable repo、旧v5 fixture constructor默认升级、null paint丢失均已修正；930無失敗。不是真GUI／真server／OS斷電驗收，六主線 goal 仍 active。

尚未 GUI 操作／實機四端／多人與 server vector protocol 驗收；已插入路徑重新開啟錨點編輯器、shape library 及時序 participant／step／block／Mermaid wizard 尚未完成。此交付不代替剩餘六主線要求，實際 Router 401 未排除，goal active。


2026-10-06 Desktop本機連線geometry拖曳接線：選取普通線後顯示path handle，auto的中點只在跨過位移門檻並完成drag後建立manual waypoint；manual已有waypoints逐點拖曳。自循環外側handle沿旋轉後的side normal改extent，保持16..4096與同端點；所選label的實際measured bounds可拖曳，world anchor投影到實際source→target弧長，保存pathFraction／tangent／normal，既有manual與auto均不先跳到固定midpoint。長route／zero segments處理先找最近projection再驗完整placement，不因非最近segment的距離過大提前拒絕。

Pointer boxes只覆蓋handle或所選label區，沒有整張canvas pointer overlay攔截其他objects；handle在label重疊時優先。Touch slop是screen pixels（既有ObjectSelectionGesture），mouse任何實際位移；pointer down／click／未跨slop／拖出又回原點／cancel零operation。gesture捕捉user/client/workspace、完整content snapshot、relation/version、viewport與單一operation ID；move只更新working geometry，renderer／labels讀preview而history／檔案／queue維持原件。其他mutation與workspace change在gesture期间阻擋；撤權／scope／history／viewport／selection／recovery變化清preview，不auto-rebase。up才一次UpdateRelationAttributes→retainDraft→history→queue，Undo沿既有inverse。每個pointer cycle有token，move/end/cancel僅處理同token，第二pointer拒絕新begin；stale回呼不能清別的gesture或重複保存。begin的seed只有actual displacement才render，避免點中點時改auto路徑。

23056最後隔離build成功73s：JVM914（6既有skip）、JS825、Wasm825、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過並更新desktopApp/build/verified/BoarderLess.app，核含gesture／handle classes。Common6cases：auto handle整gesture一history及zoom world delta／out-and-back no-op、manual指定point與越界、旋轉loop extent/clamp/端點、長route/manual labelanchor／degenerate、owner/ACL/snapshot/viewport drift拒絕、screen slop／discard preview零正式變更。Native新增多次move時file不變→single commit→reopen→Undo保存且activity只一新增drag。尚無實機滑鼠／觸控／遮擋／accessibility驗收；formal server geometry支持仍false，沒有新增HTTP或更改其他agent backend。時序metadata／精靈、鋼筆正式canvas object、多人／跨裝置合約及實際Router401驗證仍未完成；最新safe log仍不存在，六主線goal active。原「直接拖曳尚未實作」歷史段落現在僅適用server模式與實機驗收缺口，不再覆蓋此本機接線。


2026-10-06 Desktop本機自循環與geometry接線：FileRepo支持RelationGeometry；選取一個text/media可明確建立自循環，重複loop逐次增加有界extent，不把普通node當時序participant。geometry採boarderless.relation-geometry.v1＋mode union（auto／manual≤32 world points／loop side+extent16..4096；label auto／manual fraction+有界world offsets），constructor及strict decoder拒絕錯schema／type／token／nonfinite／錯endpoint route。CreateRelations只在明確geometry時允許self-loop；零尺寸端點拒絕建立，已有loop的端點不可transform至零尺寸。RelationAttributes包含完整geometry，before-state／version guard與inverse保留路徑，delete/cascade只一份relation，native文件atomic保存與reopen無損。

renderer／hit test／labels共用relationWorldRoute：loop沿真shape外側不同ports並跟隨旋轉／移動／縮放，direction只改箭頭；manual world waypoints不隨node移動，邊界接段重算。Inspector提供四側loop、extent增減、輸入world waypoint list→小預覽→明確Apply、重設auto、manual label offset增減／重設；變更走既有history／queue，未Apply的文字不入文件。manual label按實際source→target弧長與world offsets重算，manual bounds加入auto label避讓。普通parallel edge在本機放寬duplicate guard，不推測時序；自動路徑可能重疊，可明確設定manual路徑，不宣稱parallel自動分流／完全避障。直接拖曳線條waypoint或label尚未實作。

選取／clipboard升v5，geometry與self-loop保存／複製，manual world points隨整體paste平移且越界不套用；v1..4仍可讀，舊版本不可帶新geometry／self-loop。方案簿native與transfer codec讀v1..5，geometry-aware縮圖包含loop外側路徑；ClipboardJson改strict unknown fields防抹除未識別資料。插入節點以同一route midpoint放置，explicit loop可拆A→N→A，新段重設auto、原loop snapshot／Undo geometry保留。Backend支持旗標維持false：UI關閉新入口／拒絕新clipboard幾何，draft append和DTO conversion前rejectcreate/update/delete/cascade（含nested transaction）；projection帶geometry（包括null）明確contract error，不忽略後繼續同步。AI工具capabilities未開loop／geometry，不能把前端能力當工具已驗。

56839最終隔離build成功62s：JVM907（6既有skip）、JS819、Wasm819、Desktop46均0failure/error；iOS arm64／Android main compile、新Desktop包通過並更新desktopApp/build/verified/BoarderLess.app，核含geometry model／controls／routing classes。Common geometry7cases驗create／delete／cascade UndoRedo、world route／direction／node move／manual anchors、geometry update inverse／stale state、strict bounds/schema/union、v5＋scheme與loop插線、zero-size拒絕、Backend零Settings／零HTTP；projection新增unnegotiated geometry不丟棄。Native新增loop→geometry edit→reopen→node cascade→Undo→reopen。最初fixturebuild方法／HistoryResult undo／projection objects漏參數與舊v4 fixture被constructor升v5影響均修正，沒有放寬production guards。未做GUI／真server v2／OS kill驗收；鋼筆仍preview editor未正式canvas object，sequence participant/step/block metadata／Mermaid wizard未實作。實際AI401仍未排除，完整六主線goal active，不把本機geometry當正式跨端同步完成。


2026-10-06 既有relation合約的「插入節點」已實作：選取連線後在Reuse section按明確入口，不因單擊/選取/拖曳自動新增。RelationInsertionDraft凍結workspace maps與完整original relation，單次配置node／兩relation／三child operations／transaction IDs，修改preview沿用IDs；對endpoint／所有ancestor lock、missing／cycle／selfLoop、版本safe integer餘量／zIndex／有限geometry／UTF-8上限fail closed。預設world point使用與正式renderer一致的orthogonal避障route／clearance midpoint；共用direct parent保留，跨parent不改舊hierarchy。Backward按實際B→N→A且新段Forward；Both兩段均Both並提示非單序；None保留None並提示關係拆分；原label/intent/color在第一段，第二段不複製label/intent。

RelationInsertionDialog明確預覽與confirmation，文字／第一段label/intent／world X/Y/size可改，圖形及端點/新node/附近物件可見；position±1e6／size1..100000／text64KiB，非法geometry有提示。首次queue嘗試後freeze fields，可明確重試同一payload，不能換內容重用identity。workspace/user/client/role／local snapshot／queue／recovery／pending edit/drag同步guard；modal列canvas interaction blocked，close/cancel零retainDraft／HTTP／history。確認後既有execute→retainDraft→single transaction/history/submissionQueue，CreateObjects→DeleteRelations original→CreateRelations2；不採用unsupported端點update／geometry v2 metadata。Server ACK由既有流程處理，UI只標queued，新node選取；未知ACK／native durable切換完整驗收仍待完成。

RelationInsertionTest8cases涵蓋四directions／conditions只first／color、同IDs編輯/custom geometry／snapshot gate、共parent／locked/locked ancestor/cycle/missing/self拒絕、atomic changed-original拒絕與remote Undo/Redo exact restore/tombstone versions、UTF-8/geometry/version/zIndex bounds、caller mutable maps freeze、visible障礙繞路midpoint；真正BackendWorkspaceRepository retainDraft→submit MockEngine→四wire kinds/expected old relation version/第二段无条件→ACK8/seq14→pending清除。不是真服務或GUI/四端實機證據。56749 initialcompile成功12s；86159/95453 fixture引用未提供MapSettings，換既有InMemorySettings；62056/99904 fixture committed DTO漏clientSeq/base/head fields，補完整既有契約，未放寬production。59309 focused7＋Desktop成功5s；96812第一次全套＋包成功42s；追加正式避障point/附近context＋第8case後85438最終全套與新包成功52s。

本輪亦重新核對backend/src/routes/api.routes.ts及services：user-level quick schemes/resource／canvasStyle mutation仍不存在，保留BAI-004/005，不猜endpoint／client重傳asset／把本機library冒作跨装置。沒有修改或啟動backend、.env、使用者prefs，沒有新AI live log，最新使用者實際AI證據仍HTTP401。selfLoop／persistent geometry／sequence metadata／正式向量Node仍缺合約和實機，不加入AI已驗capabilities。六主線goal active。

85438最終：jvmTest {'tests': 865, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 798, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 798, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}，iOS arm64／Android main compile及Desktop createDistributable成功52s。直接核包含RelationInsertionDialogKt。


正式交付草案增補：[BACKEND_DIAGRAM_RELATION_V2_SPEC.md](BACKEND_DIAGRAM_RELATION_V2_SPEC.md)，含geometry判別格式、self-loop／平行線、整體替換語意、原子插線與restore/CAS、相容策略及fixtures。路由／capability需後端確認，並非已上線。

2026-10-05，依使用者新增需求。規劃項目不是功能已全部完成。本輪先實作 APP 自動標籤避讓，其餘按下列依賴接入；不修改其他 agent 管理的 backend。

## 互動語意

- 單擊一般線條：選取，顯示「插入步驟」控制點／操作；點明確控制點後建立中間步驟，不因選取或抖動直接新增物件。
- 拖曳線條：調整路徑控制點，不改端點、不新增流程節點。拖曳標籤：調整標籤 anchor 與 offset，不產生節點。取消還原 preview，結束才一次 history／transaction。
- 插入中間節點：A→B 改為 A→N→B，原 relation 軟刪、新 node 及兩条 relations 以同一 transaction 提交。不可把原有 relation 的端點原地改掉（目前 backend unsupported_change）。版本／撤權／鎖定／unknown ACK 沿既有機制處理，Undo 要 restore 原 relation 並移除新物件及新線，Redo 使用 exact provenance。
- 反向線：按實際箭頭 B→A 的順序插入。雙向線：預覽保留雙向但須明確說明是否代表雙向流程；無向線是關係拆分、不宣稱執行次序。預設原標籤／意圖保留在第一段，第二段不自動複製條件；使用者確認並可調整，避免條件語意改變。
- 多條同端點線不再只以 source/target/intent 當唯一性；時序圖同一對參與者可以多次互動，要有不同 step identity。現有 APP duplicate guard 需一併調整。

## 時序圖建立與輔助工具

採 Mermaid sequenceDiagram 表達，不建立新外部 DSL。參與者／生命線、訊息與一般画布節點分清，不能把讀取／持久化動作都當參與者。建立精靈：選圖型→建立參與者／角色與順序→逐步建立訊息→配置 call/return/self-message、分支 alt/else、loop/par→parser 與語意驗證→預覽→確認套用。

輔助工具包含增加參與者、新增呼叫／回傳、自我處理、在前後插入訊息、調整次序、分支／迴圈／並行區塊、缺失端點與未閉合區塊診斷。必須記錄明確 step order／block containment，不能從 x/y、UUID、zIndex 或普通圖循環猜時間與 loop。

同一生命線 self-message（自身內部呼叫）與一般關係圖 self-loop（同一節點自身關係）均需支援，但語意不同。單一節點點「自我處理／自循環」或將連線端拖回本體可建；起點與終點在不同邊界錨點，外側 loop 路徑、箭頭不消失，旋轉／移動／縮放後重新計算。不能拿重疊普通直線假裝自循環。

## 自動與手動標籤布局

本輪自動模式：依實際文字測量（含字級與 padding），候選位置在路徑側邊；依穩定 relation ID 排序，避開自身／其他線條、其他標籤與節點旋轉後矩形包圍範圍。長文字換行，不刪標籤內容。密集圖若所有候選有衝突，採最低衝突 best effort，不保證零遮擋。移動／縮放節點及 viewport 後重算；自動布局不送後端、不增加 Undo。

後續手動模式：存 world-space route waypoint 與標籤 path fraction/normal offset（或等價明確 anchor 合約），不存 screen pixels。使用者手動設定優先，提供「重設自動布局」，其他端應一致呈現。自循環需 loop side／size 的有界參數。先確定版本化格式，不能藏進未知 style 並宣稱已同步。

## 後端交付需求／現存阻擋

目前 `backend/src/services/canvas-mutations.ts` create_relation 明確拒絕 sourceObjectId===targetObjectId；APP connectNodes、CreateRelationsOperation、snapshot 解碼與 draft merge 也拒絕，全部需要同步修改与 fixtures。

1. 宣告 self-loop capability／schema 版本；create／restore／state／reducer／draft／clipboard 同時接受同端點，仍檢查端點存在／同 workspace／權限。需自循環建立、刪除、Undo/Redo 還原、並行版本測試與正式 fixtures。
2. 宣告／驗證 relation 路徑與 label placement 格式，有限數值、世界座標、大小／數量上限、unknown field/token 拒絕；create/update/restore/state 全程 round-trip。現有通用 style 接受 JSON 不等於正式圖形語意合約已交付。
3. 提供或定義時序圖 metadata 持久化：參與者順序、訊息順序、message kind 與區塊階層，含整批原子更新與版本／rebase規則。不可只保留 Mermaid 文字而丟失與畫布物件的 identity 對照。
4. 插線建立 node＋刪原線＋建兩新線須原子 transaction；端點 update 不支援時沿 delete/create，Undo exact restore，原提交 receipt／fence 與未知 ACK 不重複送。

## 開發順序與驗收

2026-10-05 隔離核心進展：新增 SelfLoopRelationRouting，四側不同邊界ports／外側run，使用shape polygon外側首交點或曲線contains查界；旋轉整條route繞node中心，移动／縮放重算。25種shape×4side的線段採樣不穿自身、邊界內外、箭頭切線非零、polyline命中與label midpoint測試；零尺寸與extent範圍拒絕。51862曾重現Plus/Top穿自身，修外側查界；31429揭露舊中心ray不是凹形外側邊界oracle，改用實際contains內外檢查保留不穿自身assertion，66432最後focused9tests通過。沒有renderer UI／畫布解碼／保存接線或backend capability，亦未保證避開其他節點，不能當使用者可建自循環已交付。

1. APP 自動標籤避讓（本輪），人工重驗長標籤、多條重疊線、交叉線、旋轉物件、縮放與手機遮擋。
2. 自循環雙端合約與 loop renderer／hit testing／Undo fixtures，再開放 UI。
3. 手動路徑與標籤拖曳、保存同步、取消與重設，含手機 touch-slop。
4. 線上插入步驟的隔離預覽→原子交易→Undo/Redo；单擊只選取／顯示插入入口。
5. 時序圖 metadata／建立精靈／Mermaid converter/parser adapter；以使用者 Transport 案例測試 Stateful 回傳與兩分支，不替使用者補未確認的步驟。

語法驗證與 semantic extraction 分開，優先既有 parser；Mermaid parse API 只驗語法不能假稱提供所有圖型完整 AST。固定版本與支援子集，禁用不需要的 links／click／配置指令，跨端 runtime adapter 另驗，native 不直接依赖 npm API。不能承諾任意圖型無損互轉。

人工作視覺／遮擋／VoiceOver 簽核；效能最後。未完成項目不要在 AI capabilities 中列為已支援。

隔離route最終回歸：89488 JVM全套737tests零fail/error、6skip；JS／Wasm／iOS arm64及Desktop classes成功35s。未執行這個新route的Browser／iOS測試runtime或GUI，仅JVM算術與跨端main編譯；跨平台文字／視覺、geometry正式decode／state／submit及capability仍需後續。
