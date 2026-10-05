# Backend / App Integration Issues

更新日期：2026-10-02

## 目前交付優先順序

1. `BAI-003`：圖片、GIF、影片的 asset upload／download、完成確認與 object storage 合約。
2. `BAI-009`：WebSocket operation fan-out、Presence、Cursor、Selection 與 reconnect catch-up。REST polling 只保留為過渡橋接。
3. `BAI-006`：Quick Scheme 使用者層級跨裝置同步 API。
4. `BAI-005`：共享 `canvasStyle` projection 與可 Undo 的更新 operation。
5. `BAI-004`：OpenAI-compatible、Anthropic-compatible 與 Local AI server transport。

效能測試在上述功能與整合穩定、主要回歸及產品視覺／遮擋簽核完成後才進入最後階段。流程見 [`產品 QA 品質檢驗 SOP.md`](產品%20QA%20品質檢驗%20SOP.md)。

## BAI-001：soft-delete 物件無法以穩定 ID 還原

狀態：Blocking（刪除 Undo、Ungroup Undo）

目前 App 的 inverse operation 會以原本的 object ID 還原被刪除的物件，確保 relation、parent 與歷史參照保持穩定。後端的 `delete_objects` 會 soft-delete projection row，但後續對相同 `objectId` 提交 `create_object` 時，仍把 deleted row 判定為既存物件並回覆：

```text
HTTP 422
code: object_already_exists
```

重現順序：

1. `create_object` 建立 UUID A。
2. `delete_objects` 刪除 UUID A。
3. `create_object` 使用同一 UUID A 與原 snapshot。
4. 第 3 步收到 `object_already_exists`。

期望合約可擇一：

- 新增明確的 `restore_objects` operation；或
- `create_object` 遇到同 Workspace、相同 ID 且已 soft-delete 的 row 時，以受版本／狀態檢查的方式 resurrection。

Relation 也需要相同的 restore 語意，否則刪除節點後還原其 cascaded relations 仍會失敗。

App 在伺服器拒絕時會撤回 optimistic change 並保留正式伺服器狀態；HTTP 4xx 不再被顯示成網路斷線。

## BAI-002：Web client 需要 CORS 或同源 proxy

狀態：Web integration blocked

JS / Wasm client 目前以 `http://localhost:3000` 呼叫 REST API。後端未註冊 CORS，瀏覽器 dev server 與 API 不同 origin 時會阻擋請求。Desktop、Android emulator 與 iOS 不受此限制。

## BAI-003：Asset API 只有 pending metadata，無法上傳或讀取內容

狀態：Blocking（Image Node、GIF、影片素材）

可交付後端實作的詳細合約已整理至 [`BACKEND_MEDIA_API_SPEC.md`](BACKEND_MEDIA_API_SPEC.md)（Media v1／2026-10-02）；以下摘要若有差異，以該文件的現行 APP 相容邊界為準。pending-only abandon 的 DELETE 語意變更仍需後端確認。

後端已有 asset metadata 的建立、列表、讀取與 soft-delete endpoint，但 `POST /workspaces/:workspaceId/assets` 固定回傳 `uploadUrl: null`，服務註解也標示 object storage 尚未串接。App 因此能建立 pending row，卻沒有可用的上傳目的地、下載 URL、完成上傳／processing 狀態轉換或縮圖資源。

原始碼觀察更新（2026-10-02）：上段為初次整合的歷史缺口。另一 agent 現已加入 prepare／complete／content／pending-only abandon routes 及相容 response schema；APP 本輪僅唯讀檢查，未修改／啟動後端，未執行 migration 或真實 storage／worker 驗收。因此 BAI-003 仍為端到端 Integration Pending，不能由 routes 存在宣稱已解除。

Desktop 異常定位（2026-10-02）：使用者確認後端已交付，現有服務 health／OpenAPI GET 均 200，公開首頁回報 DB up、storage=s3；APP 未自行啟動它。本輪定位到 APP 的 MediaRecoveryStore v1 key 在真實 UUID 下長 96 字元，超過 Java Preferences 的 80 字元限制，拋出 Key too long 並阻止 complete；已改成固定 70 字元 SHA-256 scope key，補上真實 Preferences 回歸與舊 key 遷移。這是 APP regression，不是後端未實作的證據。Desktop 需重新啟動新版本，再做真實素材 end-to-end；目前未自動上傳／刪除使用者資料。

要完成圖片、GIF 與影片物件，至少需要：

- 建立 asset 時回傳短效 signed upload URL，或提供可串流上傳的 API。
- 上傳完成／驗證 endpoint，讓狀態從 `pending` 轉為可用。
- 可授權的原檔／縮圖下載 URL。
- 內容雜湊、media type、大小及圖片尺寸的伺服器端驗證。

建議後端交付的最小 v1 合約（命名可調整，但語意不可缺）：

```text
POST /workspaces/:workspaceId/assets
  request:  { mediaType, byteSize, checksum, width?, height?, durationMs? }
  response: { asset, upload: { method, url, headers, expiresAt } }

POST /workspaces/:workspaceId/assets/:assetId/complete
  request:  { byteSize, checksum }
  response: 202 { asset }          # status=pending；processing 為內部 phase

GET /workspaces/:workspaceId/assets/:assetId
  response: { ...asset, thumbnailAssetId?, rejectionReason? }

GET /workspaces/:workspaceId/assets/:assetId/content
  response: { asset, download: { method: "GET", url, headers, expiresAt } }

GET /workspaces/:workspaceId/assets/:thumbnailAssetId
  response: { ...thumbnailAsset }

GET /workspaces/:workspaceId/assets/:thumbnailAssetId/content
  response: { asset, download: { method: "GET", url, headers, expiresAt } }
```

- Upload／download URL 必須短效且 Workspace ACL 生效；App 不會保存 URL。
- `complete` 必須冪等；同一 asset 重送相同 checksum／size 回相同結果，不得重複建立衍生檔。
- `ready` 只能在 object 存在、server-side MIME／size／checksum 驗證完成後出現。
- GIF 保留動畫原檔並產生靜態 thumbnail；影片至少產生 poster thumbnail，轉碼可延後但狀態需可觀察。
- `rejected`／`missing` 必須是終態並提供機器可讀原因；pending upload 應有過期清理政策。
- Signed request 若需要特定 `Content-Type`、checksum 或 provider headers，必須完整回傳在 `headers`，不得要求 App 推測。

在這個合約完成前，App 不會建立看似成功但實際無內容的 Image Node。

App 端已先完成可安全推進的部分：`MediaNode` projection／operation mapping、asset metadata 唯讀 client、狀態 placeholder、transform／lock／layer／history、clipboard v4／Quick Scheme 可攜格式，以及不依賴 storage provider 的 `prepare → chunk upload → confirm → processing → ready` 協調器。Desktop/JVM 檔案來源採 bounded chunk 與 SHA-256，避免把 200 MB 影片一次讀入記憶體。協調器會驗證 server 回傳 metadata 與來源一致；完成確認尚未請求前，截斷或失敗才嘗試清理已驗證的 pending row，確認已請求後保留可能已接受的資產供狀態回查，且只在 `ready` 後交付可建立 Node 的結果。下載端亦已具授權 ticket、bounded chunk sink、byte count／checksum 驗證、暫存檔清理與 atomic cache publish；signed URL 只存在 transfer ticket，不進 Workspace。Ktor gateway 已實作上列 signed PUT／GET、完成確認、polling、縮圖引用及 provider header 轉送，並有不依賴真實後端的契約測試。現有後端若回傳 `uploadUrl: null`，gateway 僅對符合來源／scope 的 pending preparation 嘗試 5 秒截止的 best-effort cleanup，再回報明確的 transfer unavailable 錯誤，不刪 ready 或陌生 metadata。`ready` 資產在沒有授權 download URL 時仍會明確顯示「等待下載端點」，不會嘗試由 `storageKey` 猜測或直接存取 object storage。

APP 補充（2026-10-02）：已在物件庫加入工作區素材 metadata 分頁，復用既有 list／GET asset；重新插入前重新讀取 metadata 與檢查目前身分／角色，不再次上傳或刪除 ready 原始 asset。AssetDto 已有的 optional thumbnailAssetId 保留到 domain，影片卡片只用不同 ID poster、不讀影片原檔作縮圖；沒有檔名欄位時以既有 Node altText／asset ID 顯示。此入口可讓「ready 但未插入 Node」資產在 metadata 可讀時被重新使用，但 mock／model tests 與編譯不證明上列 binary lifecycle、object storage／signed URL／縮圖 ACL 已交付。後端保持關閉，未修改或自行啟動；端到端依 QA SOP 5.23 留 Pending。

完成確認安全補充（2026-10-02）：complete 的取消／503／遺失回應或 processing timeout 不能證明伺服器未 accepted，APP 不再對此資產送無條件 DELETE；Rejected／Missing 也保留 metadata 供查核，不建立 Node。RecoveryRequired 僅攜 asset ID 到本機 UI，導向素材庫重新 GET metadata，不自動重送 complete 或再次 upload。後端仍需提供 pending-only 原子 abandon（與 accepted completion 競爭時不得刪除 ready／processing）、明確的 processing／uploading phase、冪等 complete／重試規則、expiry／orphan cleanup 與可驗證 durable commit 的故障 fixture。APP 的 coroutine timeout／mock tests 不是 server-side concurrency 或 object storage 證據；跨重啟 recovery journal 尚未交付。後端保持關閉且未修改，QA SOP 5.24 的真實回應遺失／worker 完成／清理競爭仍 Pending。

恢復索引更新（2026-10-02）：APP 已在 complete 前保存 user／Workspace 隔離的本機 asset ID 提醒，正常重開可從素材庫手動確認或移除提醒；不保存票券／來源檔案，也不自動重試、刪除或續傳。這補上手動跨重開 reconcile index，不取代上述 server 原子 abandon、durable completion、GC 或正式 retry 合約。見 QA SOP 5.26 與素材規格書。

## BAI-004：AI Cowork provider adapters 與串流合約尚未交付

狀態：Protocol families decided / Blocking（M6 AI Cowork runtime）

App 已具備不依賴供應商的 context snapshot、stream event、proposal preview、部分接受、拒絕、版本衝突與 undo transaction 模型。產品方向已確認支援 OpenAI-compatible API server、Anthropic-compatible API server 與 Local AI server，但後端目前尚無統一 AI job／stream endpoint、provider profile 或 secret reference 合約。

採用下列產品邊界：

- 遠端 OpenAI-compatible／Anthropic-compatible 端點預設經 BoarderLess backend gateway；明文 secret 不進 App Workspace、operation、日誌或同步偏好。
- Provider profile 包含 protocol family、base URL、model、能力與 secret reference；不得把 secret 回傳給 client。
- Local AI server endpoint 是明確 opt-in 的裝置層設定，可不帶金鑰或使用平台 secure storage，且不得隨 Workspace 分享給協作者。
- 三類 adapter 都轉換成相同的 BoarderLess stream event 與 proposal operation，不讓 provider-specific payload 滲入 domain。

App 至少需要：

- 建立 AI request/job，payload 明確包含 context scope、Workspace/version 與 selected object versions。
- SSE 或 WebSocket 串流文字 delta、結構化 proposal item、完成與錯誤事件。
- 可冪等取消 endpoint；取消或失敗不得提交正式 Workspace operation。
- Proposal operation schema 沿用 Workspace operation 語意，且 commit 前由 App 再做版本預覽／確認。
- Provider policy 與資料保留資訊，可在送出前顯示給使用者。

## BAI-009：多人即時協作 transport 尚未交付

APP REST ack 已改讀完整 committed records，核對原 request 的 actor／client／transaction／operation ID、seq、kind、base／workspace version、schema 與完整 range，成功後才更新 client sequence；malformed／無關 HTTP 200 不視為接受。尚無 WebSocket adapter、operation reducer、durable pending wire outbox 或真正 reconnect／Presence，不解除此項 Blocking。

狀態：Priority 2 / Blocking（Realtime Collaboration）

合約交付草案：[BACKEND_REALTIME_API_SPEC.md](BACKEND_REALTIME_API_SPEC.md)。本文新增路由與欄位均待後端 agent 確認，不是目前 server capability；列出 ticket／join、原子 transaction、snapshot／live barrier、ack reconciliation、Presence／roomEpoch、metadataRevision／revocation、REST fallback 與必交 fixtures。不得因已有規格就把 BAI-009 改成已完成或讓 APP 猜測 endpoint。

App 目前以 REST operation log 每 3 秒 catch up，可安全取得遠端正式狀態，但沒有 WebSocket join、即時 operation fan-out、presence、cursor 或 selection 訊息。

APP 準備進度（2026-10-02）：Repository 已增加 nullable、authenticated/session-scoped 的 `observeRemoteChanges` 通知入口，WorkspaceScreen 接上 join／commit／metadata 訊號觸發的權威 refresh；未提供 stream、stream 結束或失敗仍保留 3 秒 REST polling。通知只作 wakeup，不套用 operation、不前移 durable checkpoint、不認領 pending ack；離開 user／client／workspace 時取消 observer。BackendWorkspaceRepository 目前仍回傳預設 null，沒有自行猜測 WebSocket URL 或把 `x-user-id` 當成正式 socket auth，因此此狀態仍是 Blocking，不是已完成即時協作。

後端交付時需另提供確切 WebSocket URL、短效 token／ticket 取得方式（含 Browser 限制）、join／catch-up／snapshot／live／ack／error／revocation message fixtures、sequence transaction 邊界與 protocol version。APP notification adapter 與真正 operation apply、Presence／Cursor／Selection、重連 backoff 尚待這些合約；UI 不會把通知即時 refresh 的準備入口宣稱為 operation fan-out。

APP refresh gate 更新（2026-10-02）：REST／notification-triggered refresh 增加 user＋client＋Workspace 身分匹配、content version 與 durable sequence 均不可倒退；request 期間目前 checkpoint／role／title 已變時不採用舊回應。取消前／返回後／一般錯誤後檢查 owner coroutine，避免舊 scope 的錯誤改動新 session。這是 APP 競爭保護，沒有提供 socket transport；尤其 role／title 的正式 metadata revision／廣播順序仍需後端合約，不能用本 gate 取代 server-side ACL 或宣稱能判定所有同 sequence metadata 的新舊。

最低合約需求：

- 版本化 WebSocket handshake，包含 authenticated user、Workspace、client ID、last seen server sequence 與 protocol version。
- 正式 operation 沿用 REST envelope、operation ID、Workspace version 與 server sequence；REST 與 WebSocket 不能產生兩套語意。
- accepted／duplicate／conflict／rejected ack，支援遺失 ack 後以 operation ID reconcile。
- 重連時從 durable operation log catch up；重複及亂序事件不重複套用。
- Presence、cursor、viewport 與 selection 使用可丟失的暫態通道，不寫入 PostgreSQL operation history。
- 成員權限變更或撤銷後，server 立即停止 mutation 與 subscription。
- 多 process fan-out 的暫態 broker 中斷不得造成已回覆 accepted 的正式內容遺失。

## BAI-005：Canvas 正式樣式沒有可同步、可撤銷的 operation

狀態：Blocking（共享 Canvas 背景／網格樣式的保存與 Undo）

合約草案：[BACKEND_CANVAS_STYLE_API_SPEC.md](BACKEND_CANVAS_STYLE_API_SPEC.md)。已拆開正式背景／gridStyle 與個人 showGrid／snap／viewport，補 projection／snapshot、CAS operation、單調版本 Undo／Redo 與 remote state guard、Preview／Cancel、舊資料 migration／checksum、capability rollout 與双端 fixtures。後端／APP 均待正式實作；grid world-unit／density 規則須共同確認，不能以草案或本機背景測試宣稱共享完成。

App 目前把 viewport、是否顯示網格、吸附開關與 `backgroundToken` 保存於裝置本機偏好。這能在同一裝置重開時恢復，但 `GET /workspaces/:id/state` 不包含 Canvas 樣式，operation protocol 也沒有修改 Workspace／Canvas 設定的 operation。因此背景無法跨裝置或協作者同步，也無法以正式 history operation Undo。

建議把語意拆開：

- viewport、`showGrid`、`snapToGrid` 屬於使用者／裝置檢視偏好，維持本機，不進正式 operation log。
- `backgroundToken` 與未來的正式 `gridStyle` 屬於 Workspace 內容，應出現在 projection、snapshot 與 operation log。

最低合約需求：

- Workspace state 回傳 `canvasStyle` 與可衝突檢查的 `canvasStyleVersion`。
- 新增 `update_canvas_style` operation，payload 至少包含支援的 `backgroundToken`／`gridStyle`，並驗證 allowlist。
- operation 支援 `expectedCanvasStyleVersion` 或等價的 compare-and-set 語意。
- 舊 Workspace migration 使用穩定 default；catch-up、snapshot 與 replay 得到相同結果。
- owner／editor 可修改，viewer／commenter 的規則需與其他正式內容一致。

在此合約完成前，App 會把背景標示為 per-device preference，不宣稱它是共享或可由 Workspace history 撤銷的正式內容。

## BAI-006：Quick Scheme 沒有使用者層級同步資源

狀態：Blocking（Quick Scheme 跨裝置同步）

合約草案：[BACKEND_QUICK_SCHEME_API_SPEC.md](BACKEND_QUICK_SCHEME_API_SPEC.md)。已拆開 resource envelope v2／selection v4，補 CRUD／ETag／idempotency、snapshot pagination、owner-scoped cache／outbox、migration journal，以及素材 source binding／retention pin／destination materialization 的必要擴充。路由、limits、auth、素材複製政策與共用 fixtures 待後端確認；文件交付不代表 API 已存在。

App 已有 versioned Quick Scheme payload、縮圖摘要、改名、插入與舊版 migration，但目前只保存在平台本機 settings。Workspace API 沒有適合的 user-scoped template collection；把方案塞進任一 Workspace 也會造成所有權與可見範圍錯誤。

最低合約需求：

- 使用者層級的 Quick Scheme list／create／update／delete API，不綁定單一 Workspace。
- 欄位至少包含穩定 ID、名稱、`schemaVersion`、versioned payload、建立／更新時間與 revision／ETag。
- 明確的 payload 大小、每位使用者數量上限與 unsupported schema 錯誤。
- update／delete 具 optimistic concurrency，避免兩台裝置靜默覆蓋。
- 正式 auth 完成前可沿用 dev identity，但 API 的 ownership 必須由伺服器身分決定。

版本需在合約中分開命名：跨裝置資源 envelope 的目標為 v2；目前 APP 內嵌的 `boarderless/selection` 已為 v4（包含素材引用），並可讀取 v1–v4。不能為了使用 v2 envelope 將 selection 降為 v2 或移除素材。後端不應解析 Compose 或平台物件，只保存通過大小與 schema envelope 驗證的可攜資料；resource envelope v2 尚未交付。

APP 本機讀取已統一預覽與插入驗證：缺少 JSON version 時依本機 schemaVersion 讀取，不套用最新版 constructor default；舊 Settings 無版本 metadata 而預設為 1 的記錄，仍以 JSON 明示版本為準。非 legacy metadata 與 JSON 版本矛盾、未支援版本或非法物件／關聯皆不預覽、不允許插入，原資料保留供刪除或未來修復。此為 read-only 相容處理，不代表已實作遠端 migration、revision 或跨裝置同步。

## BAI-007：後端未完整維護 Group parent 階層 invariant

狀態：Blocking（跨 client 群組樹完整性）

App 的 Create／reparent operation 已原子拒絕不存在的 parent、非 Group parent、自我 parent 與 ancestor cycle；projection 載入也會拒絕這些狀態。但後端目前只確認 `parentId` 指向 active object，`update_object` 額外排除直接指向自己，沒有確認 parent 的 `objectType == group`，也沒有檢查間接循環。`canvas_objects.parent_id` 目前亦沒有同 Workspace 的複合 FK。

因此另一個 client 可建立 `text → text` parent 或 `group A → group B → group A`，正式 projection 會成為本版 App 無法安全開啟的狀態。

2026-09-28 已對目前 dev server 實測確認：同一 transaction 建立 `text → text` parent 會回 `accepted`；先建立 `group B.parent = A`，再提交 `A.parent = B` 也會回 `accepted`，state endpoint 隨後回傳完整循環。測試 Workspace 已刪除。

最低修正需求：

- `create_object` 與 `update_object` 在 Workspace row lock transaction 內確認 parent 是同 Workspace、active 且 `object_type = group`。
- 以即將提交的 transaction 狀態檢查完整 ancestor chain；同一 transaction 內多個 parent 變更也不得形成 cycle。
- parent 被刪除時必須明確定義：拒絕、連帶刪除 descendants，或先原子 reparent；不可留下 dangling `parent_id`。
- 新增整合測試：non-group parent、直接／間接 cycle、同 transaction cycle、跨 Workspace parent、刪除含 children 的 Group。
- 若加入 DB constraint，需同時處理 soft-delete 語意；僅有 FK 不能防止 parent row 已 soft-delete。

在後端修正前，App 會停止載入非法 projection 並保留最後可讀狀態，避免靜默修補後繼續覆寫正式資料。

## BAI-008：Canvas object payload 尚未使用型別化 schema 驗證

狀態：Blocking（跨 client projection 可讀性與 schema migration）

後端的 `create_object.objectType` 目前接受任意非空字串，`transform`／`properties` 只要求 JSON object；`update_object` 亦直接替換 transform、shallow merge properties，沒有依 object type 驗證更新後完整狀態。這允許正式 projection 出現未知型別、非數字座標、負尺寸或不符合型別的 properties。

2026-09-28 已對目前 dev server 實測確認：`objectType = future_widget`、`transform.x = "not-a-number"`、`width = -1`、`properties.text = 42` 的 `create_object` 會回 `accepted`，state endpoint 原樣回傳。測試 Workspace 已刪除。

最低修正需求：

- 以 discriminated schema 驗證已支援的 object type；目前至少 `text` 與 `group`，未知型別在 capability／protocol 尚未協商前應拒絕。
- 共用 transform schema：`x`／`y`／`width`／`height`／`rotationDegrees` 必須是有限數字，尺寸不得為負。
- `text.properties.text`、`group.properties.title`、color token 與 `text.properties.shapeToken` 使用明確型別、長度及允許值；`shapeToken` 目前允許 `rounded`、`rectangle`、`ellipse`、`diamond`、`pill`、`parallelogram`、`hexagon`。缺省值只能用於欄位不存在，不可掩蓋錯誤型別、未知 token 或 explicit null。
- `update_object` 必須驗證 merge／replacement 後的完整 projection，而不只驗證 patch 外形。
- 新 object type 應提升 schema／protocol capability，並提供舊 client 可辨識的最低版本錯誤；不可讓舊 client 靜默遺失內容。
- 建立 Kotlin／TypeScript contract fixtures，至少覆蓋缺省欄位、邊界數值、錯誤型別、未知型別與 migration。

App projection 邊界目前採 fail-closed：遇到上述資料會保留最後可讀狀態、停止編輯並提示更新或 Retry。

## BAI-012：跨装置備份原提交的權威 reconciliation

狀態：Contract Pending（2026-10-04；唯讀核對現有 REST catch-up／scoped 去重及 v1 備份欄位）。不指定未交付 endpoint，也不改後端。

- v1 DraftReviewBackup 只有 domain operations／本機旗標，不含原 actor、完整 transaction wire manifest 或 committed receipt；mapper 的 wire operationId 是新 UUID，不能拿 domain ID 查 log。hasUnconfirmedSubmission=false、quarantined=false 或新裝置無 pending 不能證明原請求未提交／已完成。
- 現有 GET operations afterSeq 是 catch-up，不是 receipt lookup／原请求 settlement。有限頁沒找到不是「未提交」，最新畫布內容相同亦不能認領原 transaction ack；不能以 activity summary 或備份 current 作權威。
- 所需正式 read contract：以目前 authenticated user＋Workspace ACL 查可辨識的原 actor／transaction／wire operation IDs，最多 200 IDs、完整交易邊界；返回 complete committed receipt（actor／client／transaction／所有 IDs／serverSeq range／commit version）或明確 unknown。跨 actor 的同 operation ID 不可互相認領，不回傳他人私密 payload；錯範圍／撤權直接拒絕。
- unknown／not-found 在指定 head 仍不排除晚到原請求。若允許改建新 merge，需要正式 settlement／fence 或同交易保證，確保原请求不會在新操作之後再 commit；不以 client 清 pending 或人工勾選代替此保證。未交付前保留未知狀態，禁止自動採用／重送／清除備份旗標。
- APP 後續需版本化 v2 provenance／wire manifest／receipt 格式及 migration（不放 token／signed URL），核對來源、本機已保存 wire request 與 receipt；仍以 fresh ACL／version／user confirmation 作新 merge。v1 只能比較與規劃，不把 importSupported=false 改為 true 宣稱完整恢復。
- fixtures 至少含完整 accepted、partial receipt、同 ID 異 actor、late original commit、歷史裁剪、撤權、來源不可辨識／v1 missing manifest與 conflicting version。APP preflight 目前只擋已知 pending／旗標，Checked 明示不是原提交完成證據。

## BAI-011：草稿合併結構替換與刪除 Undo／Redo 合約

狀態：Contract Pending（2026-10-04；僅唯讀原始碼核對，未啟動或修改後端）。

- `operation.service.ts` 的 create_object／create_relation 查既存 ID；`canvas.repository.ts` 的 findObjects／findRelations 包含 deleted_at rows。因此先 soft-delete 再以同 ID create 仍會拒絕，不能以本機 domain replay 成功作正式證據。
- 最新 APP 已新增 UpdateMediaReferenceOperation，素材引用替換不再 delete＋create，而是既有 update_object.properties；保留 Node／relation ID、版本與 metadata。明確送 thumbnailAssetId=null 清舊縮圖，仍需完整素材引用 ACL、正式提交與雙端驗收。物件型別及 relation endpoint 變更仍採本機 delete＋create，同 ID 路徑未能送出；需正式 operation／capability 或明確不支援錯誤，不偷偷改 ID 丟掉引用。
- 刪除 Undo，以及新增→Undo delete→Redo create，都需要正式 restore semantics。請交付同 Workspace tombstone 授權、expected version、server sequence、恢復版本規則、關聯／群組相依、transaction 原子性及拒絕 fixtures；不能假設 create 等於 restore。APP 後续需依正式版本規則 rebase inverse snapshots。
- 後端 locked object 只允許 lock-only update。APP 已把最終属性修改與鎖定拆開，inverse 可先 lock-only unlock；本機 Undo 測試不是服務 Undo 驗收。
- APP 增加 contract gap assessment 保留上述缺口，沒有接 Apply／POST。空 gap 不表示已驗 edit ACL、素材授權、歷史 tombstone、原提交狀態或最新版本；需再做 fresh authority／二次確認與實際雙端測試。

## BAI-010：GIPHY 外部引用的 Canvas object 合約

狀態：Contract Pending（2026-10-02；後端未修改，GIF Browser 查詢／預覽不等於 Canvas 插入）。完整官方限制見 [GIPHY_API_INTEGRATION_SPEC.md](GIPHY_API_INTEGRATION_SPEC.md)。

- 建議新增 `objectType = external_media`，properties 僅有 `provider = giphy`、`providerId`、`altText`；使用正常 object UUID、transform、parentId、zIndex、locked、version。不得偽造 assetId，亦不得存入 rendition URL、API key、GIF binary 或裝置快取位置。
- create／update／state／delete／restore 及 server sequence 沿用正式 transaction 與 ACL；properties merge 後仍驗證完整 schema，provider 與 providerId 採明確版本化規則。parent 維持同 Workspace、active group、無 cycle；relation endpoint 可指向此 object UUID。
- 後端只同步引用與畫布資料，不呼叫或代理 GIPHY API／媒體。不以素材是否仍可用決定刪除 Node；client 直接依 providerId 解析，缺失／無網路維持可恢復佔位。
- capability／protocol 應明確包含此 kind；不支援的舊 client 要得到可辨識的升級提示，不可靜默丟掉物件或使用未知 type 建立不可讀 projection。
- 後端交付需含 create／update／round-trip state／ACL／刪除復原／relation fixtures，以及拒絕敏感或未知 properties 的測試。APP 接續模型、renderer、CreateObjects／Undo／clipboard／Quick Scheme 與 viewport 批次解析；目前 `resolve(ids)` 已具 100 ID gate、rating=g、缺失處理與共用限額，不代表上述畫布路徑已完成。
