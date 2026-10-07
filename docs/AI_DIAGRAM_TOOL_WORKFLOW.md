# AI 圖形規劃與工具流程

2026-10-06 APP工具任務與批次review已接線：Ai理解視窗新增「AI圖形草稿」入口，editor且提交佇列／recovery安全時可用，延續canvas modal／鍵盤隔離。AiDiagramToolDialog提供selection或whole≤128objects、模型／完整endpoint／認證／version／local HTTP、任務，key僅記憶體，沒有設定／key持久化；Router工具支援需真任務驗證，初始capability僅APP既有七shape／preset node colors／group，selfLoop=false，未宣稱pen/media/v2能力。每個真正HTTP都先展示完整不可變body＋endpoint/model並等待新checkbox＋send，拒絕／取消／dispose釋放pending gate；gate依同一review實例而非round值同意，stale／double按鈕不能批准下一輪。session／history／role／queue／recovery變化同步guard並取消清plan；backend fresh check失敗以WorkspaceBackend typed status區分AI本身HTTP。

完成後唯本機preview：call items中文／英文操作名稱、逐項include/exclude→立即重算domain dependencies/version、node shapes/color/group/rotation fit preview與完整文字／geometry／relation direction/label/intent inventory，Double bounds避免大座標Float overflow；小圖連線是中心直線概略，不宣称正式geometry或實機視覺已驗。AiDiagramDraft→loop Ready新增本機logical refs mapping，維持read N及new A等名稱，UUID僅UI、未進任何provider body／safe log。final無proposal顯「未提出可套用變更」，不假稱產生圖形。

Apply必須再次明確checkbox，refresh後pure reviewAiDiagramApply核對user/client/workspace、fresh及live editrole、三session和proposal version、lastServerSeq不退、opened/live/fresh/history完整snapshot一致；不是auto-rebase。合法selected Transaction沿existing execute→retainDraft→history→submissionQueue，標queued由原submission/ACK/recovery/Undo處理，AI UI沒有直接submit／繞journal、沒有在ACK前markCommitted。排除new node但保留依賴edge／empty／scope/permission/version/snapshot/status矛盾拒絕，失敗不改正式畫布；Settings預設durable／native default／unknown ACK完整驗收仍屬六主線待辦，不能聲稱這個UI解決全部保存。成功排隊後關視窗且清key/plan。每round自動安全logger，不需要再讓使用者複製資料。

新增AiDiagramReviewTest5cases在三runtime：exact-instance consumed consent／stale/duplicate、cancel/close、fresh Editor實際selected transaction→history Undo/Redo、foreign/read-only/版本/seq/三snapshot/已使用proposal拒絕、exclude dependency及empty零op。25609 compile與39100初次因漏withTimeout import失敗，補import後18904 focused5＋Desktop classes成功40s。25227第一次全套＋新app image成功59s；追加無變更提示／操作名稱localized、backend preflight typed errors及live snapshot gate後83581最終全套＋打包成功59s；數字如下，不是付費Router或GUI／Android/iOS runtime驗收。交付app image desktopApp/build/compose/binaries/main/app/BoarderLess.app，直接核對包內AI dialog及logger class。仍無新live log，最後使用者實際AI證據仍401。

83581最終結果：jvmTest {'tests': 857, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 790, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 790, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}，iOS arm64／Android main compile及Desktop createDistributable成功。下一步真Router/tools與四端GUI／scope撤權／formal ACK／Undo簽核，同時接續六主線其他缺件；goal active。


64433最終全套成功：jvmTest {'tests': 852, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 785, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 785, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}；新loop10cases在JVM／JS／Wasm各零失敗，iOS arm64／Android main compile成功。不是iOS／Android runtime／真Router或APP人工驗收。


2026-10-06 工具bounded request loop已實作（AiDiagramToolLoop.kt）：専用client每次invocation ownership/finally close，最多8次HTTP；每一輪以不可變AiDiagramRequestReview實際wire body取得新的approve，再fresh scope validation→fresh credential acquisition→再validation，cheap session guard在每frame／每tool／observer回傳／最後proposal發布前核對。沒有automatic retry／fallback或沿用首輪同意。完整正常response且fresh validation後才能執行本機AiDiagramDraft；tool結果須下一輪另批准才送Router。末輪仍tools、跨輪64calls／args總1MiB超限均在該輪執行前拒絕；duplicate／foreign／closed／unknown／internal tool failures整個task停止，schema／unsupported geometry等safe rejected可在另一次批准的新round修案。任何取消／失敗／拒絕同意清整份隔離draft，不返回可提交proposal；成功Ready仍只AiProposal，要APP一次batch review＋fresh authority／正式transaction，無Repository／prefs／canvas寫入。

HTTP沿既有SSE transport，正常status/type／timeout／UTF-8 parser門禁保持；endpoint/version/credential validators抽為共用helper，text-only ConfiguredAiProvider仍同樣規則與拒tools。每round safe typeddiagnostics保留第一failure／HTTPstatus/contentType，401不被generic decoder error覆蓋，不記body/header/model/endpoint/exception。取消（含外部timeout）向caller傳遞；本機fresh validation自有15s timeout回安全準備失敗。callback failure停止並向caller傳遞，完整tool預算／scope snapshot與capability sets在首個suspension前freeze。

新增AiDiagramToolLoopTest10cases：兩方言真正MockEngine HTTP read/create→tool-result→finaltext→實際proposal preview、exact body approve/credentials/http順序／每roundfresh secrets、deny第二輪零第二HTTP、scope撤權於approve/preflight/secrets/frame/tools/Completed、401/EOF/empty/malformed zero-retry、rejected修案另批准、duplicate/unknown/8requests、approve/secret/HTTP取消與外部timeout、invalid endpoints/headers/preflight/timeouts/observer、跨round64call／1MiB總args與caller context/caps變動freeze。54447／79368 compile成功各10s；57889 focused17共3fail皆client close fixture誤認isCancelled，讀實際Ktor3.6.0 source確認close呼叫clientJob.complete，改join＋isCompleted，不改production釋放語意。78706第二次build在jvmJar遭暫時ZIP並行寫入衝突失敗；35685之後focused17成功5s，最終前直接zip testzip與AiDiagramToolLoopKt class核對artifact完整有效。

仍無新本地live AI紀錄，最新真實證據仍使用者HTTP401；本輪僅MockEngine、不發付費模型／backend请求。APP工具任務入口／每round授權UI／cancel／preview／batch review／fresh versions／submit/ACK/history Undo尚待接線，六主線全部範圍保持active。


13675完整最終回歸成功：jvmTest {'tests': 842, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 775, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 775, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}；新stream8cases在JVM／JS／Wasm各零失敗；iOS arm64／Android main compile成功。不是iOS／Android runtime／真Router或APP人工驗收。


2026-10-06 工具專用OpenAI Chat／Claude Messages串流解碼已實作（AiDiagramToolStream.kt），未放寬舊text-only adapters。Chat單choice／穩定completion ID，支援交錯call index args fragments；call identity/name/type固定、重複index／ID拒絕，正常tool_calls finish後仍必須[DONE]。Claude順序且完整關閉text／tool_use blocks，input_json_delta累積、empty-input無delta回{}；全部正常tool_use stop後仍必須message_stop。不支持thinking/server tool/custom function等模式明確Unsupported，非偷偷執行或降級。僅terminal Completed暴露完整request/session綁定call；text delta只是進度，EOF／length／wrong stop／refusal／錯lifecycle／transport／cancel均無可執行partial calls。

共同限制：每event262144bytes、8192events、wire總4MiB；text128KiB UTF-8、64calls、單args64KiB／總1MiB。完整JSONobject嚴格重複key含escaped aliases／depth／decoded Unicode surrogate驗證，status失敗只safe enum，不外洩原錯誤。wire builder亦改用同一strict parser驗round-trip，避免Claude parse後last-key-wins。Flow在terminal停止upstream，CancellationException保留並finally關decoder清buffer，零HTTP／credential／執行／prefs／真正canvas。新增AiDiagramToolStreamTest8cases：Chat interleave完整args、Claude text＋tool次序、多種EOF／空白／異常停止、duplicate／malformed／surrogate／provider error、wrongdelta／thinking／lifecycle、UTF-8／event上限、flow cancel/transport/terminal與實際draft只執行一次→兩方言tool results。87378 production JVMcompile成功10s；37086 focused stream8＋wire6測試成功17s。

仍無新真Router紀錄，最後已知為使用者HTTP401；逐次批准的有界request loop、APP工具任務／預覽審核／fresh authority／正式submit與Undo尚待交付，六主線goal保持active。本輪不發付費AI／backend請求。


94445最終全套成功40s：jvmTest {'tests': 834, 'failures': 0, 'errors': 0, 'skipped': 6}；jsBrowserTest {'tests': 767, 'failures': 0, 'errors': 0, 'skipped': 0}；wasmJsBrowserTest {'tests': 767, 'failures': 0, 'errors': 0, 'skipped': 0}；test {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}，新wire6cases在JVM／JS／Wasm各零失敗；iOS arm64與Android main編譯成功。不是iOS／Android runtime或真Router驗收。


2026-10-06 OpenAI Chat／Claude Messages八工具wire catalog與完整歷史request body已實作（AiDiagramToolWire.kt）：兩方言使用同一份完整JSON schema，additionalProperties=false／必填欄位／有界geometry／logical IDs／实际shape和color subset，未宣稱strict provider能力或強制tool_choice；純建body，沒有HTTP／credential／scope擴張／canvas mutation。Chat以assistant tool_calls保留原args string，配對role=tool／tool_call_id；Claude以assistant tool_use/input物件，緊接單user消息全部tool_result並is_error標識拒絕。結果均status＋committed=false＋safe failure enum，untrusted文字只在user／tool資料，不升system。request/session綁定，唯一call ID，結果必須逐一同序配對；unknown tools／malformed root／extra或少結果／cross scope拒絕。最多8歷史turn／64calls、args每64KiB／總1MiB、text128KiB、data256KiB、最後escaped UTF-8 body1MiB；model/output budget明確，無default model、自动retry或fallback。

新增AiDiagramToolWireTest6cases核對八schema完整subset、Chat exact args與result pairing、Claude多tool立即配對及rejected flag、實際AiDiagramDraft read＋create→兩wire round-trip／private IDs排除／原workspace不變、foreign／duplicate／malformed history拒絕與UTF-8／escaped final wire／call／depth上限。37366 JVM production compile成功11s；82286初次測試compile因fixture Workspace漏title失敗，補fixture必填欄位後50558 focused6成功4s。沒有真Router／付費AI請求，text-only adapters仍拒tool calls。本地安全log尚無新的實際紀錄，最新已知仍HTTP401；包含logger的Desktop app image已於上一輪重新打包。串流完整call解碼、有界可取消且逐次批准的request loop、APP review／fresh authority／正式submit／Undo仍待接線，六主線goal active。


2026-10-06 中立AI diagram工具schema／隔離executor已實作：AiDiagramDraft支持list_diagram_capabilities／read_approved_graph／create_nodes／create_groups／create_relations／update_nodes／layout_nodes／validate_diagram八個完整call工具。能力subset由caller明確提供APP／後端已確認shape/color，selfLoop默认false，不聲稱media/pen/未交付geometry可建立；read只批准objects及雙端在scope的relations，N/E logical IDs、parent/transform/locked/versions／media altText，不回原UUID／asset ID／thumbnail／private scene。fullbaseline僅本機freeze／private預覽，APP配置UUID，模型不得生成operation identity／sequence；call綁request/session/call ID，完全parse／strict字段後才atomic發布草稿與AiProposalItem。所有寫工具返回Proposed／committed=false，無HTTP、prefs、server／live canvas mutation。

有界政策：approved128objects／256relations、new128objects、總256logicalrelations、64calls／32proposalitems、每args64KiB UTF-8／總1MiB、read／working批准graph256KiB、private workspace snapshot6MiB／depth gate，保留版本safe integer加操作餘量，最終方案expanded semantic operation count≤200。嚴格拒絕unknown tools／extra fields／duplicate含escaped key、quoted numeric fields／NaN／Infinity／零負／過量geometry、未知shape/color/direction、duplicate logical IDs、scope外引用、locked或locked ancestor、影響unapproved group descendants、群組cycle與無能力selfloop。rejected call消耗ID/budget但不改draft／mapping／proposal；duplicate call不重執行。proposal整批／排除項目沿既有preview驗依賴與版本，Close清context／draft／ID mappings。validate只證local draft合法，不是fresh server ACL／version或committed proof。

36590首次JVM／Desktop compile成功13s；88688 focused6cases全fail，原因snapshot Json未開allowStructuredMapKeys，APP的CanvasObjectId map key需結構化表示；修codec後31994 focused6cases成功11s，未放寬scope／schema門禁。85600加入snapshot freeze／本機Undo Redo後JVM全套及Desktop／JS／Wasm／iOS arm64／Android main compile成功31s；80056 JS／Wasm actual browser tests成功33s。追加Close清approved IDs後27128最終JVM／Desktop／JS／Wasm actual tests與iOS arm64／Android compile成功43s，完整內容與各平台AiDiagramDraftTest7cases零fail/error/skip。沒有provider／付費AI或backend請求。native routing先前進度保留，不把缺legacy exclusion/catalog當整個goal停工理由。

下一步兩方言完整tool schemas／request+stream adapter、tool-result round-trip及有界可取消loop→APP草稿預覽／批次審核→fresh ACL／scope/version／正式transaction與Undo。現有text-only adapters仍拒tools，不直接移除其門禁，沒有router tool能力驗收或APP工具UI接通。HTTP401仍是最後使用者實際AI失敗；本地log尚無新檔。六主線goal active，native default bootstrap／ACK settlement與其餘cross端等缺件仍待完成。


更新：2026-10-06。唯讀理解及工具wizard／wire／stream／bounded loop／APP batch review與既有submissionQueue已接；真Router及formal ACK／四端GUI尚未驗收。

## 產品定位

AI 是會使用 BoarderLess 服務能力的圖形規劃助理，不只是文字聊天。使用者描述流程／架構／組織／關係，AI 得知可用圖形、群組、連線、版面與限制，經明確工具呼叫形成可操作的圖形方案。第一階段仍只支援 Local AI Router 暴露的 OpenAI Chat-compatible／Claude Messages-compatible API，不增加第三套 Local 協定。

Router／model 必須另通過 **tool calling** 能力驗收；文字串流相容不等於工具相容。現有 OpenAiChatEventAdapter、AnthropicEventAdapter 是 text-only，會拒絕 tool_calls／tool_use；AiProposal domain model 存在也不表示工具呼叫已接通。不能直接刪掉拒絕規則就宣稱支援。

## 執行流程

使用者任務 → 確認可讀 context → AI 接收工具定義與能力 → 讀取工具 → 建圖工具在隔離草稿執行 → tool result 回饋 AI、可繼續規劃 → 畫布預覽與方案審核 → 使用者確認 → 最新權限／版本檢查 → 正式 transaction → ACK／同步／Undo。

讀取工具只能取得批准的選取範圍，工具呼叫不能擴大原本 context 授權。第一階段建圖工具修改的是 **方案草稿**，不是每次呼叫即送正式後端；回覆 status=proposed，不假稱committed。使用者可以整批套用或排除部分操作，排除後重新驗依賴關係；不是每個工具呼叫都跳 modal。不能讓 AI 直接呼叫任意 HTTP、讀取憑證、改協作者／ACL、下載外部素材或執行程式碼。

## 第一批工具（擬定，不是現有 API）

| 工具 | 能力與限制 |
| --- | --- |
| `list_diagram_capabilities` | 傳回目前支援的形狀 token、text/group/media、連線方向與可用操作；能力從 APP／後端實際合約取得，不虛構鋼筆 Node 保存或未支援圖形。 |
| `read_approved_graph` | 讀取已批准的 objects／relations、版本、parent、transform、locked；不讀整個帳號／其他畫布。 |
| `create_nodes` | 在草稿批次建立文字／圖形節點；世界座標、有界尺寸、形狀／色彩 token。模型用暫時 logical IDs，APP 配置正式 UUID。 |
| `create_groups` | 草稿建立 group、配置 parent／children，驗同workspace、group parent及無循環。 |
| `create_relations` | 草稿指定兩端、方向、label、intent；兩端必須存在於批准範圍或同方案新節點。 |
| `update_nodes` | 草稿調整文字、色彩及形狀；不能替換後端不支援的objectType。 |
| `layout_nodes` | 草稿排列／移動／縮放（僅允許的transform），不改使用者viewport／zoom；禁止NaN／Infinity／負尺寸與鎖定物件變更。 |
| `validate_diagram` | 回覆完整性、群組／連線依賴、版本與鎖定衝突；可回給AI修方案，不自動提交。 |

第一階段暫不暴露刪除／清空畫布、relation端點替換、素材上傳、快速方案跨裝置或管理員工具。未來要擴充另訂語意與確認規則。

## Tool result 與安全界線

- 每筆call保留request/session/call ID、name、完整args；流式args只累積，**完成且通過schema驗證才執行一次**，不執行半截JSON。兩方言各自decoder，再轉中立ToolCall／ToolResult。
- Tool result需明確區分read／proposed／rejected，返回logical ID mapping、摘要及安全錯誤碼。不返回API key、signed URL或原始後端敏感錯誤。
- 未知工具、額外欄位、錯誤token、重複call ID、大小／深度／次數／輪數超限拒絕；agent loop有硬上限，可取消，不無限自我修復／默默retry。
- 讀取结果、Node文字與素材標註皆是不可信資料，不可當系統指令或增加權限。取消／換scope／撤權停止請求，草稿不自動套用。
- 使用者確認後，由APP把已驗證草稿轉成既有WorkspaceOperation／TransactionOperation，使用fresh versions、pending journal與exact wire提交。冲突要求重新檢查／確認，未知ACK不重新送新交易。成功history inverse沿既有Undo／Redo／restore規則；不由模型生成operation UUID／clientSeq／tombstone版本。

## 接續交付順序

1. AI設定入口與五步精靈，明確Router協定／model／認證／連線测试與工具能力狀態。
2. 中立工具schema、隔離diagram draft與工具executor／validation／result；先用synthetic測試，不改真正畫布。
3. OpenAI Chat tool_calls與Claude tool_use／tool_result的request／stream／round-trip adapters；文字與工具能力明確區分，Router實際相容性另驗。
4. APP任務／執行進度／取消、草稿預覽與一次批次審核、fresh ACL／版本／正式提交與Undo。
5. 真實Router與四平台工具任務驗收：建立流程圖、群組／連線／intent、已有圖排版、錯誤修案、取消／撤權／衝突／未知ACK；人工視覺與遮擋按QA SOP，效能最後。

依 OpenAI Docs 核對 [function calling](https://developers.openai.com/api/docs/guides/function-calling)：模型請求工具，由應用程式執行並傳回result；不是模型取得任意後端權限。本文件工具名稱／草稿政策是BoarderLess規劃，不是OpenAI官方工具或已部署服務。Claude wire schema待工具實作時核對其正式文件。
