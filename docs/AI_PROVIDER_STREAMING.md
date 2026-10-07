# AI provider 串流接線

最新預設表達已改 Mermaid flowchart＋短 ID 精簡 inventory，依建立時明確圖型選擇才可使用特殊語法，詳 AI_DIAGRAM_EXPRESSION_SPEC。以下完整 scene JSON 為此前接線紀錄，不是現在默认送出內容；Router 兩方言與逐次批准規則不變。

2026-10-05 新增唯讀「AI 理解驗證」3 步驟 UI：選取／整畫布結構 JSON → 相容 Router endpoint／協定／模型／本次記憶體認證 → 審查同意送出、串流與 inventory 核對。可複製提示詞手動驗證。這不是完整 AI agent 設定持久化與工具執行已完成；詳 [AI_UNDERSTANDING_ACCEPTANCE.md](AI_UNDERSTANDING_ACCEPTANCE.md)。下方「尚無設定 UI」為此前狀態，現有測試入口以本段為準。

## 第一階段範圍與設定精靈（2026-10-05，依使用者收斂）

產品面向補充：AI需理解BoarderLess能力並調用工具完成圖形規劃，不僅文字聊天。下方「tools／自動執行」暫不支援的意思是**目前實作及禁止直接自動改正式畫布**；工具規劃為下一階段必要交付，採隔離方案→預覽／審核→確認交易。詳AI_DIAGRAM_TOOL_WORKFLOW，text-only decoder不可當工具支援。

第一階段以 Local AI Router 為連線入口，只支援 **OpenAI-compatible Chat Completions**、**Claude／Anthropic-compatible Messages** 兩種明確方言。Local 是部署位置，不是第三套原生協定；Router 可路由雲端或本機模型，APP 不猜模型來源，也不直接保管各上游供應商金鑰。不新增其他 local 原生 API、Responses、Realtime、影像／音訊生成、未經確認的畫布自動修改或多供應商 fallback。工具規劃依AI_DIAGRAM_TOOL_WORKFLOW接續。此規範優先於下方早期「三類 provider」敘述；已有兩方言 serializer／adapter／ConfiguredAiProvider 可沿用，不能宣稱任意 Router 已相容。

**目前 APP 確實沒有 AI 設定入口或設定精靈，也沒有可用的 AI 操作 UI。** 底層 composition 測試不等於使用者能設定／使用；下一個 AI 交付先做入口与精靈，再接串流與審核操作。不因縮小協定範圍省略憑證、安全、context 同意與版本檢查。

### UI 交付順序（待實作，非已上線）

1. 畫布主選單新增「AI 設定」入口；未設定時按 AI 操作也進精靈。手機置中 modal 配合 safe area／鍵盤與可捲動內容；不因點 Node 自動開啟。不與 Node 屬性 bottom sheet 互相強制關閉。
2. **Router 連線**：名稱、完整 API endpoint、Local／Remote；本機 HTTP 明確提示明文風險並需主動允許。沒有預設主機／model、偷偷偵測或自動連線；若提供 Base URL 輸入，必須顯示且確認實際完整 endpoint 後才產生 profile。
3. **相容協定**：只選 OpenAI Chat 或 Claude Messages；後者的 API version 明確輸入。不要用「雲端／本地」當第三個協定；也不混用兩種事件格式。
4. **模型與認證**：model ID、輸出 token 上限；Router token 的認證方式明確選擇（支援既有 Authorization／x-api-key，無認證需 Router 明確允許）。上游 OpenAI／Claude key 留在 Router。token 顯示遮罩，可貼上／刪除；輸入框聚焦停用畫布快捷鍵。endpoint／model 非敏感設定可保存在裝置；token 不寫一般 Settings、workspace、operation、QA 報告或同步資料。安全儲存未接好前只允許明確告知的 session-only，不能假裝「記住金鑰」已完成。
5. **測試與完成**：僅使用者按「測試連線」才發一筆小型固定文字測試，明示可能產生費用、不夾帶畫布內容。顯示成功／認證錯誤／不相容／連線錯誤，支持取消、不自動retry或fallback。保存／返回上一步／取消都清楚；取消不覆蓋既有設定。未測試設定顯示未驗證，不能假裝可用。

精靈完成後的真正 AI request 仍需逐次 context 摘要與送出同意；串流文字不能直接當畫布 operation。proposal review、確認後正式提交、fresh ACL／版本及 Undo／Redo另接；不能用固定approve=true替代UI。換帳號／workspace／endpoint及關閉頁面取消請求並清暫態內容。

### 驗收與缺件

- 尚需 Local AI Router 的實際 endpoint／支援方言、認證與 CORS／TLS 配置；未取得實際服務規格，不猜預設 path 或測試服務。
- 工程回歸：步驟導航與驗證、取消不保存、敏感欄位不落一般prefs、輸入焦點／貼上／Delete、HTTP／SSE取消、安全錯誤、不重試、不混用方言。
- 真實 Router 兩方言與四平台連線另驗；設定介面、safe area／鍵盤／VoiceOver／遮擋由使用者按QA SOP簽核；效能最後。
- 使用 OpenAI Docs 核對 [Chat Completions](https://developers.openai.com/api/reference/chat-completions/overview) 的協定名稱。本輪只更新範圍與交付順序，未新增UI、讀取金鑰或發送AI請求。

## 最新：configured provider composition（2026-10-05）

最終證據：96784 JVM／Desktop／JS／Wasm／AndroidHost tests與APP／Device APK／Web entries成功1m11s，composition各7 cases零fail/error/skip；72724 iOS成功55s，7cases零fail/error/skip，本次boot恢復shutdown。97780首輪2m46s因取消fixture在response交付前取消而channel斷言失敗；fixture改成首TextDelta確認讀取後取消，仍要求channel關閉、無Completed／Failed／retry。曾嘗試 `--deviceUUID` 被Gradle拒絕（783ms，無執行tests），後續72724正式task成功。實際socket／UI／provider仍Pending。

新增 `ConfiguredAiProvider`，串接已有本文 serializer → scoped HTTP SSE → 明確 Chat／Messages event adapter。上方／下方早期「尚未接 HTTP builder」敘述保留為該輪歷史，最新狀態以本節為準。尚未接 APP 設定／串流／review UI、正式 gateway／secure store、proposal、fresh ACL／版本／Undo；不是完整 AI 交付。

- server profile 必须给完整 endpoint、Remote／Local、body 方言／model／token budget；无默认端点或 path 拼接、auto-detect／云端 fallback。Remote 仅 HTTPS；Local HTTP 必须单独明确允许，包含 LAN 在内可能是明文且不证明主机确在本机／内网，未来 UI 须明示风险。禁止 URL userinfo／query／fragment（包括空 ?/#），不把 key 放 URL。
- Messages 明确给 `anthropic-version`，Chat 不混用；版本字符串只验证日期格式，不证明 server 支持。认证头从批准后的 suspend callback 取得，仅允许单值 Authorization／x-api-key；无 key默认值／secret持久化，也不声称任意 gateway 自定义认证已支持。
- 冷 Flow 每次 collection 复制 selection list、固定 exact wire body，让 approve callback 审核 server／snapshot／body；拒绝时零 HTTP／零 credential callback，取消保持 cancellation。这是必须注入的门禁，不是已经有用户同意 UI；APP 接线不得注入常量 true。
- body／credential／approval preflight 错误安全非 retry 提示；HTTP／event 错误沿原分类，不读 raw error、不自动重试。文字结果只输出 TextDelta／Completed／Failed，不能作为 operation。Chat 拒绝非默认 message 的 named events，不偷偷混用 Responses。
- provider 拥有专用 client，close 释放；各 response 由既有 transport 释放。外部 client/plugins 仍须无 logging／secret外送并符合 transport gate；不保证 client 本身是可信的。provider close／scope change 的 UI 生命周期与持续撤权门禁仍待接。

本轮使用 OpenAI Docs skill 核对 [Chat SSE](https://developers.openai.com/api/docs/guides/streaming-responses) 与 [Messages 请求](https://platform.claude.com/docs/en/api/messages/create)。没有 key provisioning tool，未读现有 key、未发 provider 请求。

ConfiguredAiProviderTest 七 cases 用 MockEngine／真实 SSE channel 串接所有层，覆盖 exact method/body/headers/endpoint、冷请求／重复同意、Chat completion、Messages named lifecycle／explicit Local HTTP、拒绝／preflight零请求、危险 endpoint、consent取消／consumer异常清理、source mutation snapshot／503不重试、等待response body取消关闭channel且没有terminal/retry。55747 首轮六 cases 的 JVM 回歸47s成功；追加第七case与 constructor client gate后最終結果另记。不是实际 socket／TLS／LAN／CORS／模型／UI或 proposal 验收；四目标回归结果另记。

## 請求本文接續（2026-10-05）

98833 iOS最後 regression 成功1m37s，AiRequestBodyTest 5 cases零 fail/error/skip；本次 boot simulator已恢復shutdown。本文共用程式已補齊四目標自動證據，不代表四平台真實provider／UI完成。

30965 跨 JVM／JS／Wasm／AndroidHost regression 成功3m3s，AiRequestBodyTest 各5 cases、零 fail/error/skip；APP／Device APK建置成功（Device未跑）。iOS結果另記。普通素材 live tests 的 skip 不當新live證據，亦未發真實 AI request。

新增 `AiRequestBodyProfile`／`aiRequestBody`，明確指定 Chat Completions 或 Messages 方言、model 與輸出 token budget；Local Server 必須明確選用相同方言，不自動偵測／雲端 fallback。這是 production 可用的純 serializer，尚未接 APP 設定／HTTP builder，不是三類 provider 已上線。

PromptOnly 只送原始 user prompt，不送 workspaceId／version／requestId；矛盾的 scope 與非空選取資料直接拒絕。Selection 額外放一筆 user content JSON，保留 selected object／relation／version，驗唯一 ID、選取內端點與安全整數；不提升成 system instruction。使用者內容可能包含 URL 或其他私密文字，serializer 不假裝已去識別化，仍須送出前摘要／逐次同意。

APP 政策上限：prompt／單物件文字 64 KiB UTF-8、128 objects／256 relations、selection JSON 256 KiB、完整 body 1 MiB；model／ID 256 bytes、maxOutputTokens 1..65536。超限拒絕，不截斷；這些不是 provider 支援能力或 context window 保證。驗證錯誤為安全常數且不可重試。尚無 key／URL／system prompt／tools／媒體 binary／proposal 執行／持久化。

依 OpenAI Docs skill 核對 [Chat 串流](https://developers.openai.com/api/docs/guides/streaming-responses) 與 [token 參數](https://developers.openai.com/api/docs/guides/token-counting)，Chat 使用 `max_completion_tokens`；依 [Anthropic Messages](https://platform.claude.com/docs/en/api/messages/create) 使用 `max_tokens`。舊 Local Server 若只支援 `max_tokens` 的 Chat 變體，仍須明確能力設定後另接，不偷偷退回舊欄位。沒有 API-key provisioning tool，未讀金鑰或發真實請求。

新增 AiRequestBodyTest 五 cases：兩方言／字串跳脫與角色、PromptOnly 隱私、Selection exact data、scope／graph／version 拒絕、UTF-8／總量／profile bounds 與安全非 retry 錯誤。正式 HTTP composition、能力設定、secret／context consent／串流 UI／確認 proposal／Undo、實際四平台 provider 驗收仍是下一步。

更新：2026-10-05。文字事件 adapter、SSE byte framing 與共用 HTTP transport 已實作；正式 request／provider 設定、APP UI 與 proposal review 尚未接線，不是 AI 功能已上線。下方早期 event adapter 段落的 HTTP 未交付敘述是該輪證據範圍，最新狀態以本節為準。

## HTTP 串流層（尚未接 production provider）

跨目標修正：98527 JS／Wasm 各3 cases抓出 `execute` engine dispatcher 與 collector 的 Flow context 不一致。9645 嘗試回 collector context 後 Web通過、JVM各3cases因 UndispatchedMarker 違反context失敗（3m8s）；這個嘗試已替換。現在使用 channelFlow＋RENDEZVOUS＋逐筆 consumed acknowledgement：send 後須等下游 emit 正常返回才解析下一筆，terminal／consumer failure 不 ack，取消producer並關閉body。不靠普通 zero-buffer send 來假設下游已處理，不預讀 terminal 後 bytes。原 terminal／cancel／consumer error 斷言保留，最後回歸另列；不是只驗某一平台。

新增 AiSseHttpTransport，caller 明確注入 request builder（URL／認證／serializer），不猜 endpoint；預設建立專用 client，明確 close 由 transport 負責。冷 Flow 每次 collection 建立新 request／response，不重用 body。使用 scoped `prepareRequest().execute` 加 `bodyAsChannel`，不先 bodyAsText／整包保存。[Ktor 串流回應](https://ktor.io/docs/client-responses.html)

專用 client 必須有 HttpTimeout、不可裝 HttpRedirect／HttpRequestRetry；覆寫 Accept 為 text/event-stream、expectSuccess=false 與 request 120s／connect 10s／socket 30s。這些是 APP 有界生命週期政策，不是效能門檻；JS 無 connect/socket timeout 支援，Darwin 無 connect timeout 支援，仍須實際 engine／網路驗證，不聲稱所有 engine 有相同 idle deadline。[Ktor timeout 與引擎限制](https://ktor.io/docs/client-timeout.html)

只接受 status 200、單一 text/event-stream（無 charset 或 utf-8）、無重複 charset，以及非負整數且不大於 8 MiB 的 declared Content-Length（可缺省，實際讀取仍由 SSE wire 上限約束）。拒絕不讀 error body、不轉向；header gate、terminal、取消／consumer failure 都釋放 body channel。不記錄 URL／key／raw error。外部 builder／plugins 本身仍須符合認證與 logging 規範，transport 不會替它們做端點授權。429／5xx 與網路錯誤可標手動 retry；其他 HTTP／格式／大小錯誤不可 retry，兩個事件 adapter 沿用安全常數，不自動 fallback／retry。

AiSseHttpTransportTest 7 cases 使用 MockEngine＋真實 channel：冷 request／再次 collection、header／method、非200／redirect單次呼叫、type／charset／declared length拒絕、terminal同read停止、waiting body取消／consumer failure、network／response錯誤redaction、unsafe client配置拒絕。不是 socket／timeout elapsed／真實 TLS／LAN／CORS／provider／APP UI證據。97917 首輪 compile失敗（6s，client.config為internal、request expectSuccess缺import），改公開 plugin gate／extension；14178 JVM／Desktop首輪成功（22s），retry分類／client gate修訂後最終結果另列。

## SSE 位元組解析接續

`ByteReadChannel.aiSseEvents()` 已加入：逐位元組保持跨 read 的 UTF-8／CRLF 狀態，完整行才解碼；支援開頭 BOM、CR／LF／CRLF、註解、大小寫敏感欄位、移除一個前導空格、多行 data 與具名 event。空行才派送，EOF 不派送未完成事件。[WHATWG event stream interpretation](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)

讀取採 Ktor `readAvailable`，collector 擁有單次 channel；terminal、取消、格式錯誤或 consumer exception 都關閉它。不是 HTTP request factory，重複訂閱須另建 channel。[Ktor readAvailable](https://api.ktor.io/ktor-io/io.ktor.utils.io/read-available.html)

APP 政策：無效 UTF-8 嚴格拒絕（不同於瀏覽器替代字元解碼）；line／累積 data 各 256 KiB、event name 128 字元、8192 次派送、總 wire 8 MiB，包含 comments／未知欄位／framing。逐筆 emit，使 provider terminal 能停止同一 read 中後續 bytes；沒有 EventSource 自動重連、id／retry 狀態或 Last-Event-ID 儲存。HTTP status／Content-Type／timeout／auth／request 與 APP UI 仍待接線，不宣稱完整瀏覽器 EventSource 相容。

AiSseByteStreamTest 的 7 cases 使用真正 Ktor ByteChannel／ByteReadChannel，但資料仍為合成：單 byte 分片 UTF-8／BOM／CRLF、多行與裸 CR、欄位及 event reset、EOF／invalid UTF-8、line／data／name／派送數上限、provider terminal 同 read 停止、等待讀取時取消與 consumer error cleanup、只有註解的串流恰好 8 MiB／超過上限。不是實際 socket／HTTP／provider E2E。首輪 JVM compile 發現 `cancel` 要求 cause，已改 `cancel(null)`；最後回歸結果另記 APP_PROGRESS。

## Anthropic Messages 文字事件接續

已加入独立 AnthropicEventDecoder／AnthropicEventAdapter，輸入為完整 SSE event name＋data，要求 JSON type 與 event name 一致。依 message start、content block start／delta／stop、message delta、message stop 順序處理；可含 ping 與多個文字 blocks。[官方 streaming 文件](https://platform.claude.com/docs/en/build-with-claude/streaming)

正常 end_turn／stop_sequence 與 max_tokens／tool_use／refusal／pause_turn 等停止理由分開。[官方 stop reasons 文件](https://platform.claude.com/docs/en/build-with-claude/handling-stop-reasons)

BoarderLess 本輪 text-only 政策：順序／block index 不符停止，index 從 0 連續、一次一個 active block、最多 64 blocks；end_turn／stop_sequence 之後仍須 message_stop 才 Completed，EOF 不成功。stop_sequence 須有界且非空；usage／ping 不當完成。未知頂層事件在有界 JSON 驗證後忽略、不改 lifecycle；未知 block／非文字 delta、thinking／tool／fallback 不採用，尚未實作這些能力，不啟動工具或 fallback。沿用 256 KiB/event、8192 events、4 MiB stream、1 MiB text、depth 64，安全常數錯誤與 cancellation／upstream cleanup／consumer exception 透明。沒有 HTTP／auth／Key／request／UI／proposal 接線，不等於 Anthropic 真實服務支援已驗收。

## 官方協定與選擇

Chat Completions 使用 SSE data payload、choices delta；Responses 使用另一套具名事件，不能混用。[官方串流文件](https://developers.openai.com/api/docs/guides/streaming-responses)

finish_reason 區分正常停止、長度限制、內容過濾與工具呼叫；可選 usage chunk 的 choices 為空，出現在 DONE 前。[官方 API reference](https://developers.openai.com/api/reference/resources/chat/subresources/completions)

本輪依 OpenAI Docs skill 核對文件。沒有可用的 API-key provisioning tool，未读取現有金鑰、未發真實 provider 請求；測試只用合成事件。

## 已實作的 APP 邊界

- OpenAiChatEventDecoder 接收**已組合完成的一筆 SSE data payload**，不是任意 bytes／line／HTTP chunk。text-only、單 choice index 0、chat.completion.chunk，穩定且有界 completion ID；只輸出既有 TextDelta／Completed／Failed，不產生 ProposedOperation、不修改 Workspace。
- 以下是 BoarderLess 的保守政策，不宣稱所有 compatible servers 都遵守：正常 stop 之後仍須 DONE 才成功；EOF 即使已 stop 仍不完整。usage-only 只在 stop 後接受。Responses／Anthropic、非正常停止、refusal、非空 tool calls／function call 拒絕，不執行工具／operation。
- 單事件 256 KiB、串流 8192 events／4 MiB payload、文字 1 MiB、JSON depth 64 上限。錯誤只顯示安全常數，不帶原始 provider JSON／URL／key。沒有自動 retry／fallback。
- OpenAiChatEventAdapter 實作 AiCoworkProvider，注入 request→Flow<String> 的 SSE data transport，沒有猜測 gateway URL。terminal 停 upstream，取消保持 CancellationException，consumer exception 不吞掉、不重試。
- 相同 Chat Completions 方言的 Local AI 可沿用，不代表所有 Local AI server 相容或已連上。

## 接續必要交付

1. SSE byte framing／scoped HTTP response gates 已加入，接續正式 request serializer／能力協商、gateway／endpoint／認證設定與 transport 接線；實際 socket timeout／disconnect／TLS／LAN／CORS／取消／生命週期與真實服務 fixtures。
2. Anthropic Messages 文字事件 adapter 已加入，接續完整 request／HTTP／能力及 proposal／工具資料的待確認驗證流程；Responses 如提供，另用明確 dialect，不猜 JSON 格式。
3. 遠端金鑰預設 backend gateway／secret store 保管；正式 gateway 合約未交付，不把遠端明文 key 放 Workspace／operation／共享偏好。Local endpoint／憑證只留裝置，無靜默 cloud fallback。
4. Context 摘要／送出同意、proposal schema／bounds／graph／ID／鎖定驗證；文字不能當 operation，completed 不代表自動接受。
5. APP 串流 UI／取消重試／逐筆 review、fresh ACL／版本／素材／journal／ack、確認後 commit 與 Undo；撤權／scope change 停止並清除暫態私密回應。
6. 真實模型、四平台、LAN／CORS／TLS／key redaction 驗收；視覺／VoiceOver／遮擋由使用者依 SOP 簽核，效能最後。

## 自動證據範圍

AnthropicEventAdapterTest 的 7 cases 驗 named lifecycle、多 text blocks／initial text、ping／unknown metadata、event/type不符、索引／順序／stage／premature stop、abnormal reasons／unsupported blocks、raw error不洩漏／單筆與depth／text總量、EOF／terminal cleanup／transport error、實際取消／consumer exception、event count／aggregate bytes／block count／unknown delta。僅 synthetic Flow，不是 HTTP、auth、真正provider或APP UI證據。

OpenAiChatEventAdapterTest 的 7 cases 驗文字／stop／usage／DONE、EOF／早到 DONE、refusal／tools／abnormal finish、invalid／mixed ID／wrong dialect／單筆 bounds／depth、terminal cleanup／安全 transport error、實際 coroutine cancellation、文字總量／event count／consumer failure。是事件層 synthetic Flow，不證明 HTTP、SSE byte framing、auth、proposal 或 APP UI 可用。
