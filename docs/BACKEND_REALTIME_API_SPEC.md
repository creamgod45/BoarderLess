# 即時協作 APP／Backend 合約草案

日期：2026-10-02。對應 Priority 2／BAI-009。

狀態：**Proposal／待後端 agent 確認與實作**。本文中的新 endpoint、訊息及 revision 均不是現有服務能力；APP 不得因本文件存在就啟用 socket。後端由其他 agent 維護，本輪沒有修改／啟動後端。素材端到端驗收仍是 Priority 1；效能測試維持最後。

## 1. 現有基線與不可混用的資料

唯讀對照：`backend/src/schemas/operation.schema.ts`、`snapshot.schema.ts`、`services/operation.service.ts` 與 `models/operation.ts`；APP 對照 `BackendContract.kt`、`WorkspaceRemoteNotifications.kt`、`WorkspaceRepository.kt`。

- REST POST `/api/v1/workspaces/:workspaceId/operations` 使用 protocolVersion、clientId、transactionId、baseVersion、operations；accepted／duplicate 有完整 committed operations，conflict 使用 409。
- REST GET 同一路徑使用 **afterSeq**，不是 afterServerSeq；`lastServerSeq` 是伺服器 head，不是本頁已套用 checkpoint。`hasMore=true` 時不得直接把 head 保存為本機已套用序號。
- REST GET `/api/v1/workspaces/:workspaceId/state` 回傳 projection 與 throughServerSeq。它沒有本文要求的 metadataRevision，角色／標題仍需正式 Workspace／membership 查詢。
- APP 現有 `CommittedOperationDto` 只有活動摘要；真正 fan-out 必須新增完整 committed DTO 與 reducer，不能拿 summary DTO 當可重播 operation。
- APP 已另加 `CommittedWorkspaceOperationDto`，對照現有 REST 的完整欄位並驗證 submit accepted／duplicate transaction 的 scope／IDs／順序／range／schema，再保存 client sequence。這是 REST ack 邊界，不是已交付 WebSocket、fan-out reducer、pending outbox 或重送 reconciliation；活動摘要 DTO 仍只供紀錄 UI 使用。
- PendingWorkspaceSubmissionStore 已有完整 wire request 的 scoped、分段、checksum／manifest-last 保存基礎；尚未呼叫於 submit／啟動恢復，不改目前 mapper 每次重新配 ID 的行為。repository retry、scope 換轉、衝突／撤權、UI 未提交草稿與 chunk GC 必須一併接入後才可宣稱 outbox 完成。
- repository 另有 explicit retryPendingSubmission 入口，可送原已保存的 request，核對 scope／client／目前 edit role，已驗證 ack 才清除記錄，client sequence 不回退；不是自動 initial prepare／startup recovery。未來 UI 必須刷新權威 projection，舊 duplicate ack 不能覆蓋已前移的 session／畫布。UI 未提交草稿、衝突／撤權與 chunk GC 尚待接入，不代表真正重連完成。
- `observeRemoteChanges` 目前是 optional notification，Backend 實作為 null。通知只 wake REST refresh，不套用內容、不認領 ack、不前移 checkpoint。
- 2026-10-03 APP 更新：普通 submit 已先保存 scoped wire request，APP 提供明確重送／二次確認停止本機重送，重送前後刷新權威 state；前述尚未接線敘述為歷史基線。未送出 queue tail、斷電耐久／GC 與真實 dedup 仍未交付，不是完整 outbox。
- 同日新增可供 REST／未來 fan-out 共用的完整 transaction record gate，驗證安全序號、連續 serverSeq／完整 range、1–200 operations、唯一非空 operation ID、同 transaction／actor／client／base／commit version、schema／必要字串；REST ack 已呼叫它。現有後端允許舊 baseVersion 的非衝突 rebase，也沒有強制 clientSeq 連號，故不能把 APP mapper 的連號政策當成所有 peer 的協作合約。此 gate 不解析／套用 payload、不前移 checkpoint；scope／connection epoch、reducer／atomic publication、transport 仍待實作。
- PostgreSQL transaction＋正式 operation log＋outbox 是保存權威。Redis／socket／Presence 不能取代它。

## 2. 能力探索與驗證

去重 scope 更新（2026-10-03）：現有後端 unique key 是 `(workspace_id, actor_id, operation_id)`，APP 跨 catch-up window 以 actorId＋operationId 驗證，而非單靠 operationId；跨 actor 相同 ID 合法，同 actor 換 client 不解除重複。完整 transaction 內 actor 一致，原有每交易 operation ID 唯一檢查仍保留。已接 production REST replay 並跨平台回歸；非 WS／真實 dedup 故障驗收。

跨頁接線補充（2026-10-03，取代下方 hasMore 一律 fallback 的舊階段）：APP 依實際接收 record 序號追讀、buffer 跨頁交易，最多 8 頁／8,000 records，完整窗口驗證後才返回 replay session。每頁 advertised head 只驗證範圍／單調性，不能直接作 next cursor 或 applied checkpoint。超過界限／缺段／無法證明交易完整才 state resync；第二頁撤權或 HTTP failure 直接失敗，不發布已收片段。此為現有 REST route 實作，仍未啟用本文 proposal 的 WS 路由。

REST 接線更新（2026-10-03）：repository 已保存 scoped raw projection，refresh 可 replay 既有 `/operations?afterSeq=...&limit=1000` 的完整未切頁結果；缺段／hasMore／無法完整辨識交易／未知種類／較舊 head 走 state fallback。UI 採用沿用既有 scope／單調／編輯 gate，非 socket live state。下方 reducer 尚未接線描述為歷史準備階段；WS scope／epoch／barrier、durable checkpoint 仍待正式 transport 合約。

APP reducer 準備（2026-10-03）：CommittedProjectionReducer 已依現有 backend 正式 payload／JSONB patch 語意實作七種 operation，原子驗證／回傳完整 WorkspaceStateDto；delete_objects 必須包含與當時 projection 相符的 cascadedRelationIds。它不認領 envelope 身分、不連線、不保存 checkpoint；transport caller 仍需做 scope／epoch／transaction boundary、raw projection ownership、optimistic queue 與原子 publication。只有完整正式 records 可傳入，活動 summary 不可用；未知種類／schema／序號 gap 或 projection invariant 失敗走權威 state resync，不 skip 後續事件。

以下為建議新增路由，確切部署 URL、反向代理 path、Origin 與正式 HTTP authentication 尚待後端交付。

| 路由 | 建議語意 |
| --- | --- |
| GET `/api/v1/workspaces/:workspaceId/realtime-capabilities` | 已驗證使用者、membership read gate；回傳支援版本與 transport URL，不含 ticket |
| POST `/api/v1/workspaces/:workspaceId/realtime-ticket` | 綁定目前 authenticated user、workspace、client；換取一次性短效 ticket |
| WS `/api/v1/realtime` | 每條連線只加入一個 workspace；換工作區建立新連線／新 ticket |

capabilities 範例：

```json
{
  "protocolVersions": [1],
  "schemaVersions": [1],
  "webSocketUrl": "wss://qa.example.invalid/api/v1/realtime",
  "operationFanOut": true,
  "presence": true,
  "metadataRevision": 2,
  "maxMessageBytes": 1048576,
  "maxPendingTransactions": 128,
  "heartbeatIntervalMs": 15000,
  "presenceTtlMs": 45000
}
```

- 404／不支援版本：APP 保留 REST，不猜測 `/ws`；401／403／workspace 不存在不可無限重試，不假裝成一般缺乏能力。
- 正式環境只用 WSS／HTTPS；HTTP／WS 僅明確開發模式的 loopback 測試。APP 驗證 URL scheme／origin／部署設定，不自動把 credentials 發往任意 capabilities URL。
- 目前 `x-user-id` 是開發 authentication，**不是 production socket auth**。ticket endpoint 必須使用正式 HTTP auth middleware；開發替身需明確標示，不能由 body 的 userId 決定身分。
- ticket 建議 60 秒、一次性、綁定 user／client／workspace／protocol；server 原子 consume，重連重新取得。response `Cache-Control: no-store`。不寫入 preferences、Workspace、剪貼簿、QA、日誌或 URL query。
- Browser 不依賴任意 WebSocket request headers：使用固定 subprotocol `boarderless.v1`，升級後第一個 `join_workspace` frame 提交 ticket。server 在驗證前不得送內容／Presence，3 秒未完成驗證關閉；限制未驗證 socket 數／frame 大小並檢查 Origin。
- 日誌不得記錄 join frame、ticket、完整 Node payload 或游標選取內容；只保留已驗證的 correlation ID／事件類型／錯誤碼。錯誤 UI 不回顯任意 server message。

ticket request body 固定為 protocolVersion、clientId；workspace 從 route、user 從 HTTP auth 取得。response 必須提供 ticket、expiresAt、webSocketUrl、protocolVersion，不回傳 refresh token。limits 與 Retry-After 的單位需保持一致。

## 3. 通用 envelope 與連線世代

JSON text frame；二進位不接受。UUID 採現有 REST UUID 規格；整數使用 0..9007199254740991 的安全範圍，避免 JS number 與 Kotlin Long 語意不同。未知 protocol／required event／schema fail closed，不能略過 durable event 繼續宣稱同步完成。

第一個 join 範例（所有值均為合成 fixture）：

```json
{
  "type": "join_workspace",
  "protocolVersion": 1,
  "schemaVersion": 1,
  "workspaceId": "00000000-0000-0000-0000-000000000010",
  "clientId": "00000000-0000-0000-0000-000000000030",
  "connectionEpoch": "00000000-0000-0000-0000-000000000040",
  "lastSeenServerSeq": 8,
  "metadataRevision": 2,
  "ticket": "fixture-only-not-a-real-ticket"
}
```

authenticated server event 必須帶 type、protocolVersion、workspaceId、connectionEpoch；server 回傳／綁定 join epoch，不能由另一 peer 冒充。APP 丟棄非目前 user／client／workspace／epoch 的訊息，離開／背景／重新驗證時取消 observer 與 pending commands。此 epoch 是連線世代，不是保存序號或 operation ID。

控制訊息字段清單（皆加上通用 envelope，正式 JSON Schema 尚待確認）：

| type | 額外必要欄位／語意 |
| --- | --- |
| join_accepted | server connectionId、self userId／role、barrierServerSeq、workspaceVersion、metadataRevision；仍非 Live |
| snapshot_required | barrierServerSeq、workspaceVersion、metadataRevision、固定 reason code；不攜帶任意外站 snapshot URL |
| catch_up_applied | client 實際 appliedServerSeq、workspaceVersion、metadataRevision；不認領 pending ack |
| live_ready | throughServerSeq、workspaceVersion、metadataRevision、connectionId；須已完成 barrier |
| heartbeat／heartbeat_ack | 本連線遞增 heartbeatSeq；只延續 TTL，不修改保存 checkpoint |
| presence_snapshot／presence_delta | roomEpoch、roomRevision、peer 集合或 upserts／removedConnectionIds |
| workspace_metadata | metadataRevision、授權可見 title／selfRole；需與正式 REST metadata 一致 |
| access_revoked | 固定 reason code；清 Presence／停止讀寫並關閉 |
| resync_required | reason code、最新 durable head；只要求重新同步，不表示 client 已套用 |
| protocol_error | code、可選 retryAfterMs；不回顯任意 message／原始 frame |

固定錯誤碼至少包含：authentication_required、ticket_expired、ticket_used、scope_mismatch、unsupported_protocol_version、unsupported_schema_version、access_revoked、invalid_message、message_too_large、rate_limited、sequence_gap、resync_required。401／403／撤銷／不支援版本需人工重新驗證或更新，不作一般網路 backoff；暫態 rate_limited 依 server cooldown，不能 tight loop。

## 4. Join、catch-up 與 live barrier

狀態：Connecting → Authenticating → CatchingUp → Live；中斷進入 Disconnected。只在已套用完整 catch-up 且 server 確認 live 後顯示「即時協作」，不能用 TCP／WebSocket 已開啟作判斷。

1. server 驗證 ticket／membership／client／versions，建立訂閱並捕捉 durable barrier B；先註冊訂閱再讀 log，或採等效的原子／watermark 策略，防止「查完 log 才訂閱」遺漏事件。
2. 若 lastSeen 在有效 transaction 邊界且 log 可用，按完整 transaction 送 `catch_up_transaction`，不把 transaction 拆成可單獨發布的半套 projection。
3. 若 checkpoint 位於 transaction 中段、log 已 compact、落後過多或 event 過大，送 `snapshot_required`，APP 以既有授權 REST state／Workspace metadata 取得新權威結果，不套用不相容 operation。
4. APP 完整驗證並原子套用後送 `catch_up_applied`，帶實際 appliedServerSeq、workspaceVersion、metadataRevision。server 檢查不是未來序號且位於完整 transaction 邊界。
5. server 處理 B 後已緩衝的 transaction，無 gap 才送 `live_ready`，並送 Presence snapshot。緩衝超過上限則 resync，不無限保留。

Snapshot REST response 可比 B 更新。APP 以 **實際 snapshot throughServerSeq** 作 checkpoint，忽略已由 snapshot 覆蓋的完整 transactions；server 驗證該 boundary，再補更後面的 log。不得降回 B，也不得只因 snapshot_required 帶了 head 就自行前移 checkpoint。

catch-up／live durable transaction 共用格式（type 分別為 `catch_up_transaction`／`committed_transaction`）：

```json
{
  "type": "committed_transaction",
  "protocolVersion": 1,
  "workspaceId": "00000000-0000-0000-0000-000000000010",
  "connectionEpoch": "00000000-0000-0000-0000-000000000040",
  "transactionId": "00000000-0000-0000-0000-000000000050",
  "fromServerSeq": 9,
  "toServerSeq": 9,
  "workspaceVersion": 4,
  "operations": [{
    "serverSeq": 9,
    "operationId": "00000000-0000-0000-0000-000000000060",
    "transactionId": "00000000-0000-0000-0000-000000000050",
    "actorId": "00000000-0000-0000-0000-000000000020",
    "clientId": "00000000-0000-0000-0000-000000000030",
    "clientSeq": 17,
    "baseVersion": 3,
    "workspaceVersion": 4,
    "operationType": "create_object",
    "payload": {
      "objectId": "00000000-0000-0000-0000-000000000070",
      "objectType": "media",
      "zIndex": 1,
      "locked": false,
      "transform": {"x": 10, "y": 20, "width": 320, "height": 180, "rotationDegrees": 37},
      "properties": {"assetId": "00000000-0000-0000-0000-000000000080", "mediaKind": "video", "altText": "QA demo"}
    },
    "schemaVersion": 1,
    "committedAt": "2026-10-02T00:00:00.000Z"
  }]
}
```

- operation 欄位沿用現有 CommittedOperationSchema，不另發明 mutation；server-generated object／relation version 的 reducer 規則須與 REST projection 一致，交付每種現有 kind 的 fixtures。
- from..to 必須連續，operations 長度、各序號／transactionId／workspaceVersion 必須匹配；同 workspaceVersion 的多 operation 一次原子 apply／publish。
- 本機 applied checkpoint 只在整套驗證／套用成功後保存。重複已套用的相同 ID／內容可忽略；相同 ID／seq 不同內容屬 protocol error，不能 last-write-wins。
- gap、亂序、部分覆蓋、未知 kind 或 reducer 失敗：暫停 incremental apply，要求 catch-up／snapshot；不得跳過失敗資料或靠 Presence 前移 checkpoint。
- v1 每 transaction 沿用 REST 1..200 operations。超出 frame 上限送 resync marker，不切斷 transaction 原子性；REST state 不受此 socket frame 大小限制。

## 5. Submit、ack 與 pending reconciliation

`submit_operations` 沿用 REST body，加 type、workspaceId、connectionEpoch；clientId 必須匹配 ticket。REST 與 WS 呼叫同一 service、ACL／schema／invariant／冪等交易，不能兩套寫入路徑。

`operation_ack` 必須帶 transactionId、原 operationIds、status 與 correlation epoch：

| status | 必要結果 |
| --- | --- |
| accepted | fromServerSeq、toServerSeq、workspaceVersion、完整原子 committed operations；只在 DB commit 後回覆 |
| duplicate | 原 commit 結果與 IDs，不能產生新 sequence／version；確認重送 payload 不同的處理規則 |
| conflict | 現有 conflicts、workspaceVersion、lastServerSeq、missingOperations；缺少 log 時轉 snapshot；正式資料不部分寫入 |
| rejected | 安全 code、可選 retryAfterMs；不回顯 Node／token／storage URL，正式資料未改變 |

目前 service 對 partial duplicate 明確拒絕，WS 不改成默默接受剩餘部分。需補 transactionId／operationIds correlation 到 WS ack，APP 現有 AcceptedOperationsDto 只有摘要，不能據此識別未知 pending transaction。

- pending transaction 的 operation IDs／clientSeq 在斷線重送時保持原值，不重新呼叫會產生 UUID 的 toExpandedDtos；重送同一 wire envelope。
- ack 遺失：先 catch up，再以原 envelope reconcile；server idempotency 必須驗證 actor／workspace／原 IDs，不把另一使用者的 ID 當自己已保存。
- Broadcast 可早於 ack。遠端／自己的 committed event 可更新權威 projection，但只有完整匹配 pending 原 IDs／actor／client／結果的正式證據才能清 pending；未匹配的通知不得宣稱保存完成。
- 保留權威 projection 與 optimistic pending 分層。遠端套用不能清除尚未确认的本機 operation；同物件衝突明確 rebase／rollback，不默默覆蓋。
- Broker 故障不影響已 commit／ack 的資料；outbox 持續保留、復原後重播。跨 gateway 使用同一 DB sequence，不能使用 process-local counter。

## 6. Presence、游標與選取

正式會員清單不等於上線清單。Presence 以 **connectionId** 區分同一使用者的多裝置；公開 peer profile／role 由 server 注入，不接受 client 自報 actor 或角色。

APP 接線備註（2026-10-03）：`WorkspacePresenceSnapshot` 是可信 adapter 輸出的完整正規化 snapshot，不是本節 wire schema。APP 另用本機 subscription nonce 隔離 observer、`leaseSequence` 辨識已確認的存活更新；後者由 adapter 在 authenticated liveness 後單調推進，不要求新增同名後端欄位。相同 roomRevision 僅可續相同內容，變更 peers 必須推進 roomRevision。adapter 不得以 timer、socket connected、peer 自報值或未驗證 heartbeat 續命，delta 必須先按本節規則組成完整 snapshot。正式 URL／認證／liveness 來源仍待確認，預設 repository 不提供此 stream。

送出管線備註（2026-10-03）：`WorkspacePresenceUpdate`／optional `openPresencePublisher` 亦是 APP 正規化介面，不是已交付 wire schema；Backend repository 預設 null。共用 runner 每 100 ms 取最新 intent、一次只等待一筆 publish，選取內容變更至少相隔 250 ms；慢 transport 完成後重新計時，不累積 pointer backlog。初始未分享不送資料，分享關閉送一次空內容；scope／room／權限失效由 owner 回傳 null 停止，不能向舊 room 清除或重送，leave／TTL 由 transport／server 負責。sequence 每 handle 單調遞增、安全整数內且不 wrap；publish 失敗不自動 retry。adapter 必須在真正寫 socket 時再檢查 physical epoch／read ACL／取消狀態，並服從 capabilities 的更低上限。APP 已接預設關閉的分享按鈕、畫布限定 passive pointer observer 與 Live／scope／epoch gate；沒有 authenticated publisher 時按鈕不可用。平台背景 lifecycle flow 尚未完整接入，正式雙向同步／UI／雙人驗收仍未完成。

- `presence_snapshot`：目前完整 peer 集合、roomRevision。`presence_delta`：新增／更新／移除 peer、roomRevision。snapshot 後只接受更高 revision；revision gap 重取 snapshot，不能靠合併猜測離線 peer。
- peer 欄位：connectionId、userId、clientId、displayName（長度上限）、role、cursor（nullable）、selectedObjectIds、presenceSeq；不用 client 字串提供任意 CSS 顏色或 HTML。
- `presence_update` 只允許 cursor、selectedObjectIds、presenceSeq；server 驗證 read ACL、connection ownership 與 workspace 範圍。未知／已刪除 selection IDs 丟棄，不能自動建立或鎖定物件。
- cursor 採 **world coordinates**，finite、安全範圍，APP 套各自 viewport.worldToScreen；cursor null 表示離開／隱藏。禁止把螢幕像素當共同座標。
- selection 是暫態選取，不是 mutation、排他鎖或強制切換其他使用者 Inspector；游標與 overlay 不攔截本機 pointer／focus。
- 本機 viewport、zoom、面板位置不發送；原架構提到可選 viewport Presence，依最新產品要求本 v1 不納入。未提交文字、clipboard、key、URL、AI local 設定均不得傳送。
- 上限建議：selection 128 IDs、cursor 10 updates/s、selection 4 updates/s；只保留最新待送值，不在斷線後重播舊游標。限制由 capabilities 確認，不當成效能承諾。
- heartbeat 15 秒、Presence TTL 45 秒為草案預設；server 到期移除、發布新 roomRevision。背景／離開主動移除，異常 kill 由 TTL 收斂；相同 user 其他 connection 不一起刪除。
- Cursor／selection 的分享開關、無障礙呈現與產品預設需由 QA／產品確認。Presence 僅 Live 發布，重連前清掉舊 overlay，不把 socket connected 當 peer online。
- Redis 暫態狀態可丟失；重啟後建立新 snapshot／room incarnation（新增 roomEpoch），不得重用舊 revision 使 client 永久拒絕新 peer。每個 Presence event 均帶 roomEpoch。

## 7. Metadata、ACL 與 revoke

Lifecycle APP 備註（2026-10-03）：Android Activity／iOS application＋controller RESUMED＋所屬 scene active／Web page activity flow 已接，background 或 epoch 變更讓舊 publication owner 停止；送出前直接讀 flow，不只依賴 UI 重組。iOS scene 通知按本機 windowScene 篩選、scene 移转重置 epoch，UIKit 實機仍待驗收。APP handle close 必須由 adapter 移除該 authenticated connection 的暫態資訊，不能刪其他 client；無法送出 leave（kill／freeze）由 server TTL 收斂。這些 APP 測試不是 server leave／TTL 或正式 WS 交付證據。

新增獨立 durable **metadataRevision**，涵蓋 workspace title／membership／目前使用者有效角色；與 canvas workspaceVersion／serverSeq 分開，不用它認領 operation ack。capabilities、join／live、授權 Workspace metadata REST response 與 `workspace_metadata` event 共用 revision。

- server 原子更新 metadata＋revision，並 durable outbox fan-out；同 revision 內容一致，APP 不採用較舊 revision，即使 canvas sequence 相同。
- Metadata event 僅送该 peer 被授權查看的資料，self role 由 server 計算；不在未授權 viewer event 中曝光邀請／帳戶私密資料。
- membership revoked／workspace removed：先阻止新的 read／submit 與 room broadcast，再 best-effort 發 `access_revoked`，關閉 socket、移除 Presence。client 立即停止 mutation、清 overlay／暫態播放，進入存取失效狀態，不自動用舊 ticket 重連。
- ticket 在有效期內也不能繞過 revocation；join、每次 submit、subscription 的撤銷與跨 process cache invalidation 均由 server 管理。
- APP 現有 role／title refresh gate 只能阻止已知的晚到回應，不能取代 metadataRevision 或 server ACL。

## 8. 中斷、REST fallback 與背壓

- 即時 transport 暫時失敗：保留本機最後已確認內容，清 Presence，REST 3 秒 catch-up 繼續。無 stream／stream end／failure 都可回到現有 notification fallback；UI 分辨 REST 同步與即時 Live。
- 重連草案：full-jitter exponential backoff，上限 30 秒，每次新 ticket；網路恢復／使用者 Retry 可明確觸發。成功 Live 後重置；離開 scope／背景／401／403／unsupported protocol 取消，不无限排程。
- 重連一定從 **已套用 durable checkpoint** join，不從最後收到、未驗證的廣播 head 開始。先恢復權威狀態，再 reconcile pending，最後才恢復目前的暫態 Presence。
- 慢 consumer：cursor／selection 可合併或丟棄；durable transaction 不可默默丟失。達上限發 resync／關閉並讓 log catch-up，不無限 buffer 或 partial apply。
- Redis／gateway kill：accepted 資料仍在 DB；outbox 至少一次，重複由 IDs／sequence 去重。多 process 相同 Workspace 的 transaction lock、順序與 ACL 必須一致。
- REST 與 WS 若同時到達，APP 只採更高／相同一致的權威 checkpoint，不讓舊 REST snapshot 覆蓋新 socket projection；scope／epoch／metadataRevision 都需檢查。

## 9. 必交 fixtures 與驗收門檻

後端 agent 確認 endpoint 與字段後，交付共用 JSON fixtures＋TypeScript schema validation tests；APP 再用相同 fixtures 建 Kotlin serializers／reducers 與 transport adapter。文件範例僅保證示意 JSON，可讀不是 schema／runtime 通過。

必交：capabilities、ticket 成功／失效／重用／跨 scope、join／live、空 catch-up、單 operation、原子 200-operation transaction、snapshot_required／snapshot ahead of barrier、accepted／duplicate／conflict／rejected／partial_duplicate、重複／gap／亂序／ID 內容衝突、Presence snapshot／delta／TTL／roomEpoch 重建、metadata revision／revoked、unknown protocol／kind／過大 frame／慢 consumer。

功能完成證據：

1. 兩個 APP 連到不同 gateway，文字＋Group＋Image／GIF／Video＋relation 的既有 operations 即時收斂，snapshot 重開一致；不是只收到通知或看到 avatar。
2. ack 前後斷線、broker 停止、gateway kill：已 accepted 不遺失，pending 不重複建立，sequence 無跳躍／回退，partial transaction 不發布。
3. Viewer／commenter 能按權限 read／Presence，不能 mutation；撤銷後既存 socket 不再接收或提交內容，其他裝置 Presence 正確收斂。
4. Cursor／selection world-coordinate overlay 在旋轉、zoom、不同 viewport 下位置正確，不干擾手機本機操作。視覺／遮擋由 QA Workbench 人工簽核。
5. REST-only、WS-only、同時到達與反覆 fallback 得到同一正式內容；不把 durable checkpoint、metadataRevision、presenceSeq 混成同一序號。
6. 真實身份／Origin／proxy 設定、外部日誌 redaction、程序重啟與 storage 素材引用驗證；開發 fake ticket 不代簽正式 auth。

效能、連線數與 p95／p99 壓測維持最後；上面的功能注入／重連測試不等同負載驗收。

## 10. 交付順序與待確認項目

後端：確認此草案 → schema／fixture → ticket／capabilities／join barrier／durable fan-out → metadata revision／revoke → Presence／TTL／roomEpoch → 多 process 故障注入。不得以所有 socket 只發 wakeup 取代正式 operation fan-out。

APP：完整 committed DTO＋原子 reducer → authoritative／pending 分層與原 envelope reconcile → Ktor 跨平台 WS adapter → ticket／backoff／lifecycle → peer store／cursor／selection overlay → REST concurrency／雙端 QA。現有 notification 入口可作過渡，但不是完成定義。

需後端 agent 明確回覆：最終 URL／auth／Origin、完整 schemas／fixtures、transaction 邊界與 snapshot barrier、duplicate payload 不一致的拒絕語意、metadataRevision 儲存／REST exposed fields、roomEpoch／TTL、各 limit 與 error codes、Redis／outbox 部署及可用 QA scope。未回覆前 BAI-009 保持 Blocking／未實作，APP 不自行連線本文範例 URL。
