# 本機優先同步：交易與收據整合需求

## 2026-10-11 新本機原素材上傳需要 preparation v2

唯讀核實現存prepare每次randomUUID，closed schema無requestId且無upload-ticket renew，complete冪等不能補prepare unknown ACK。新增[BACKEND_ASSET_PREPARATION_V2_SPEC.md](BACKEND_ASSET_PREPARATION_V2_SPEC.md)交付規格，路由均尚未實作，不能推定Media v1已支援。APP新增PreparedAssetUploadRequest／LocalAssetUploadPreparation模型：scope-bound固定request UUID、canonical metadata body／digest、exact immutable receipt identity；無signed URL／headers／Ready旗標／Node重寫。ready source仍須本機owner／workspace／當前審閱引用與kind，provider body不含local ID／owner／review。collision保留known remote identity但拒graph mapping，不自動另prepare。

本輪僅模型／契約；durable reservation、v2 adapter／capability、票券lease、串流upload與graph re-review仍待接。backend尚缺契約不能自動重POST v1或用checksum list冒認原請求；新local→server素材gap維持。

## 2026-10-11 mapped rebase publication／receipt-fence 已接（現存遠端原素材）

新增applyMaterializedSyncRebaseProposal與awaited adapter：fresh原提交receipt／review head及原檔verification後，原子保存local投影operation、captured mappings、canonical continuation與真original-commit checkpoint。AppliedLocalSyncRebase新增default empty materializations，保留舊無映射記錄；normalized continuation仍source ID，Undo及redo仍local ID。unknown ACK沿原review／operation／archive確認同generation，不transfer或另publication；新history／改target拒絕。封存與rebase記錄核mapping仍在同document。

產品rebase預覽／確認已使用投影目標及原檔fresh驗證，提供verifying／cancel、不使用20s通用timeout。rebase recovery先核原receipt，再確認已發布archive exact；不把latest head当original commit。stageSyncOutgoing不再blanket拒絕captured mappings，而是核canonical target與local operation exact對應。prepareLocalSyncUpload仍要求每個primary／thumbnail存在於fresh server、Ready／exact ID／workspace／kind，且mapped descriptor與保存source exact一致；新local original未materialize到server仍gap拒絕。這是現存server資產重用，不是新增local→server上傳協定。

controlled fixtures已驗canonical prepare→original receipt→send→settle、fence archive保留local intent及mapping、changed descriptor零draft／outgoing，以及mapped rebase unknown ACK→重開→同generation→Undo／Redo→canonical後續交易。fixture原檔／server證據不能代替live ACL／双端／GUI驗收。

## 2026-10-11 mapped rebase canonical 計畫與現存 server 素材檢查

LocalSyncRebaseContext改用captured mapping驗canonical原target與local operation的exact對應，原commit／latest head仍保持server ID。LocalSyncRebasePlan的submitted-intent baseline與later-local輸入先做canonical identity比較，避免local UUID與source ID造成假衝突；真altText等內容衝突仍需選擇。四inputs不覆寫、不把latest head冒充original commit。Stopped receipt archive亦沿同對應檢查。此為計畫層接線；mapped stageSyncOutgoing gate尚未解除，local投影rebase publication／UI仍須後續接。

prepareLocalSyncUpload修正existing-server素材MIME分類（不能用image/png直接比較image），新增thumbnail Ready／exact ID／same workspace／Image驗證與缺失reference gap。不猜素材upload協定，不自動上傳或下載；既有server primary／thumbnail可隨其他已審閱變更準備交易，但wrong thumbnail在outgoing publication／server draft前拒絕。此不代表local→server materialization已完成。

## 2026-10-11 產品素材確認套用已接線

LocalWorkspaceSyncDialog預覽使用reviewedLocalApplyTarget，保持同prepared operation ID。確認時透過同repository endpoint的download gateway呼叫verifyMaterializedApplyOrRecovery；首次重新授權及核private原檔，WorkspaceScreen才执行local history／原子保存。未知ACK只在stored snapshot exact＝原review＋同applied結果時回傳原proof給store confirmDurable，不重transfer或另套用。scope／snapshot／history漂移拒絕。

確認有固定verifying提示及cancel job，materialized驗證不套用20秒通用dialog timeout，cancel／錯誤保留原prepared operation以便明確重試。原素材與thumbnail均列獨立候選；保存／inspection／transfer要求所有引用kind一致，thumbnail只能Image。不新增backend operation，mapped outgoing／rebase仍gate，正式双端與實機GUI尚待驗收。

## 2026-10-11 materialized 素材套用交易（底層已接，產品入口待接）

新增applyMaterializedSyncProposal：opaque當次驗證結果及固定local operation，snapshot／checkpoint／proposal／mapping仍exact時，將local workspace、captured mapping、applied digest與本機operation receipt同一generation發布。operation.after保留local引用，proposal.target及遠端receipt保持canonical引用；AppliedLocalSyncProposal新增預設empty materializations，舊無映射記錄維持原規則。持久化與superseded archive重新計projection要求exact對應，不能塞入任意local target。

未知ACK的explicit retry核同操作／captured evidence／實際workspace，confirm原generation，不新增交易。重開可恢復local Undo；Undo／Redo不改mapping。比較與pending判斷只把實際使用的local destination投影回source ID；merge仍以raw local snapshot digest作CAS。read-only settlement保持canonical baseline，故正確cache引用不產生假upload pending。尚未接產品確認按鈕，且涉及captured mapping的stageSyncOutgoing仍明確拒絕，直到canonical rebase／receipt路徑完整對接；不宣稱服務端上傳或完整雙向素材完成。

## 2026-10-11 APP 素材 identity 邊界（尚未接正式套用）

新增verifyMaterializedTarget作explicit pre-apply seam：僅current未套用proposal且無outgoing／rebase，exact local snapshot及fresh remote head，所有foreign primary／thumbnail必須有保存mapping；兩次remote original authorize驗exact descriptor，本機原檔bounded重讀SHA-256／size（不是只信Ready marker），才產生localTarget。不得prepare／upload／confirm／重新download remote original／写画布。既有local引用另驗Ready／owner／workspace／kind；返回值只為當次ephemeral verification，不是未來的durable授權。正式local publication與canonical review／wire／Undo雙重表示仍未接，原gate維持。

MaterializedAssetProjection只以已保存的scope-bound一對一mapping，在明確local／canonical邊界改MediaNode原素材與thumbnail引用，保持其他欄位與版本exact；不更改原receipt／canonical review，不新增server operation。source與local namespace不得交集，拒絕foreign user/client/workspace/binding、重複與錯media kind。未映射引用不猜測或刪除；後續apply必須另驗bytes／fresh auth與完整所有引用，不能把mapping或Pending視為Ready。原檔mapping不足以認定thumbnail已materialize。比較／wire／local Undo與durable publication仍須一起對接；目前產品素材gate保持原狀。

狀態：APP 整合需求／待交付契約，不代表後端已實作。2026-10-10。

沿用既有 Workspace operations、ACL、transaction、receipts／fences；不新增可繞過版本檢查的整份 state 覆寫 API。一般啟動及本機編輯不依賴 HTTP。

## 1. 現有 APP 與交易身分

APP durable 文件保存 binding（server scope fingerprint、user、workspace）、三方 baseline、明確審閱 proposal、原子本機套用與 Undo，以及待送 domain transaction。domain operation IDs 不等於現有 adapter 配置的 wire IDs／clientSeq。

上傳 worker 必須先保存不可變的 wire envelope，並以 durable mapping 綁定 proposal／domain transaction 與 server transaction／全部 wire operation IDs；取得 durable ACK 後才能 HTTP。unknown response 先查原請求收據，不能重新產生 IDs 當新提交。原請求若未知，僅經明確授權的 fence 成功後才能重新審閱替代提交。

結算應保存完整交易範圍的 committed evidence、baseline version／seq，在同一文件原子保存；此間新增的本機編輯保留且仍 pending。只讀回內容相同不能當作原上傳已 committed。

## 2. 尚缺的標題原子性

需求：標題變更須能與物件、關係、背景／時序 metadata 在同一 operations transaction 內提交。另一個 rename REST 請求不能提供此原子性。建議 operation 名稱 `update_workspace_title`，正式名稱及 schema revision 待後端確認；APP 尚無此 wire operation，不會自行送出。

- expected title revision 與 before-state 檢查；同交易失敗時標題及 canvas 全部不變。
- 正式標題 revision 的單調規則、與 workspaceVersion／serverSeq 的關係須在 state、committed records、廣播及 fixtures 一致呈現。
- receipt／fence 應涵蓋標題操作，不可出現 canvas committed 而標題未知的半成功。
- 沿用 content edit ACL；未知 operation／舊 capability client 明確拒絕或要求升級，不靜默丟棄。
- fixtures：標題＋物件成功、標題 stale／其他子操作 stale 全部回滾、unknown ACK 收據恢復、fence 與 commit 競爭、兩端並行、Unicode 邊界及 Undo／Redo。

## 3. 完整資料能力與素材

本機同步不能為了讓請求通過而省略 vectorPath、textPlacement、relation geometry／自環、sequence bindings、外部媒體 reference 或 canvasStyle。相關正式格式沿用 BACKEND_VECTOR_PATH_API_SPEC、BACKEND_DIAGRAM_RELATION_V2_SPEC、BACKEND_CANVAS_STYLE_API_SPEC、BACKEND_MEDIA_API_SPEC；尚未交付的能力須在準備階段顯示缺口，不能部分套用。

遠端素材 ID 不可直接當本機 asset ID。需驗原檔 checksum／byteSize／媒體型別、目的帳號／空間授權與完成狀態，保存 source→destination identity 後才替換引用。上傳亦须先持久化同一 materialization ticket／identity，未知回應恢復原 ticket，不生成重複資產。

APP落地基礎（2026-10-11）：四平台local gateway提供`PinnedLocalAssetGateway.preparePinned(localSession, pendingDescriptor)`。呼叫者必須先durable保存自己生成的本機UUID及完整source→destination映射，不能拿remote asset ID直接呼叫或每次retry再生UUID；該映射／串流下載／畫布引用替換尚未接產品入口。本機descriptor需exact owner／workspace、可編輯role、Pending、支援的canonical媒體型別、200MiB上限與SHA-256 checksum；不帶遠端thumbnail或rejection資訊。

同UUID不存在時才保存Pending；已存在且完整metadata相符時確認原generation，返回原Pending或已驗證Ready marker，不新增資產／覆寫原檔／把Ready降回Pending。metadata不同拒絕，撤權及跨scope拒絕。Ready marker不是`preparePinned`重新驗證bytes的證明，完整materialization仍須原檔校驗與fresh服務授權，不能在prepare返回後直接改Node／checkpoint。既有一般選檔import仍生成新UUID，不因此自動下載遠端素材或修改共享畫布。

持久化映射（2026-10-11，APP核心已接，產品下載／套用入口未接）：`LocalWorkspaceSyncStore.stageSyncMaterialization`把`LocalAssetMaterialization`保存到同一個document generation。包括binding fingerprint／remote reader／workspace、local owner／client／workspace、origin review ID、完整primary source descriptor（原creator、MIME、bytes／SHA-256、dimensions／duration、createdAt）、local UUID及凍結的本機createdAt；不保存URL／headers／signed tickets／bytes，不保存Ready旗標。source creator可不同於有權讀取的reader，不能據creator不相同而錯拒共享素材。

新reservation須符合當前checkpoint、exact local before及仍有效的saved proposal／rebase target引用與mediaKind；不可為未審閱的asset任意建立映射。每個source原ID在該binding／local workspace只有一份immutable mapping，destination UUID在文件集合中唯一，不使用已在local Node引用的UUID。新來源須生成不同於remote ID的local UUID。最多4096份，超量拒絕而非靜默淘汰。相同record重試只確認現存generation；source metadata、destination、owner／client或binding變更拒絕。已有mapping的exact確認可保留後續本機編輯，不能把舊review当fresh下載授權。

重開先讀mapping再preparePinned；未知保存ACK不得配新UUID。映射保存不改Workspace版本／canvas／baseline／outgoing，也不呼叫HTTP。private audit JSON新增optional `materializations` list（旧文件預設空）；這不是可直接import或replay的授權。下載／verification協調核心見下節，Node引用轉換及新方案確認／產品入口仍缺；現有素材gap gate仍保留。

下載核心追加（2026-10-11，未接產品入口）：`materializeRemoteAsset`只接受已保存mapping。caller提供同一server設定的repository及download gateway；先refresh exact saved review remote head／role，重查source授權及完整primary descriptor，才prepare原local UUID。沒有新ID、abandon、canvas POST或baseline更新。local original已Ready也要fresh source授權及實際private bytes重新計SHA-256，不能只信catalog marker。

下載以單個queued 64KiB piece加至多1MiB consumer buffer串接既有local upload，transport單chunk亦限1MiB，不把200MiB影片整份讀入記憶體。獨立核完整size／EOF／多餘bytes／SHA-256後才confirm，local gateway仍自行驗證原檔；完成後再authorize local original、重讀／hash bytes，再refresh remote／重新authorize source與重讀exact local review後才返回Ready。signed URL／headers不保存。這些是檢查邊界，非跨整個下載的原子ACL lease；撤權／scope／head／local漂移會拒絕結果，但不自動刪除已完整驗證的private original，也不修改Node或映射。

Browser partial original恢復使用同token／ID／size取得既有next cursor；依HTTP-independent固定chunks重播來源並逐段比對已保存前綴，只有完全相同才在cursor追加。不同bytes／chunk boundary拒絕，不truncate/reset，不回收used ID或復活abandon；sealed／completionRequested不可resume，需走既有recover。來源仍完整重新下載，不猜測HTTP Range API；物件儲存網路Range／ticket租約若要正式支援須另有合約。產品授權視窗、Node引用／comparison／wire projection及雙端服務交付仍未完成，素材gap gate保留。

產品入口追加（2026-10-11）：LocalWorkspaceSyncDialog的原素材panel已接metadata唯讀檢查→獨立確認保存mapping→另一確認下載／驗證、固定狀態／進度及cancel。候選每頁20，先去重，僅MediaNode原檔；GIPHY external-media不搬入本機catalog。inspection／保存都重新讀exact review head／source metadata／local before，不提供HTTP或自動下載授權。unknown mapping ACK保留原prepared UUID，保存前仍再次重核，不從本機reservation冒充服務權限。Node引用／comparison／wire projection仍未接，不解除素材apply／upload gate。

CanvasWorkspaceRepository.syncAssetGatewayFactory由BackendWorkspaceRepository的同一base URL建立owned gateway，下載helper比對gateway.downloadServerScope與repository.syncServerScope；local gateway借用不關，remote每次operation終止即關。custom endpoint不fallback default。既有Asset.mediaType是MIME，local MediaNode前置驗證改用mediaKindForAssetMediaType分類，不與Node token直接比較。此APP路由不代表正式authenticated／ticket／lease協定已雙端驗收。

## 4. 整合驗收門檻

以完整 authenticated 實際服務測試雙端離線編輯→重連→三方差異／預覽→明確選擇→套用→原提交 receipts／fence→原子 baseline 結算→兩端關閉重開。涵蓋撤權、scope 變更、並行修改、未知 ACK、素材缺失、完整圖形／時序／背景／標題及 Undo／Redo。Mock、domain compiler 或本機 outbox 測試不能代替此交付。

## 5. APP 本機同步記錄匯出 v1（2026-10-10，非後端操作）

明確確認匯出私人JSON；選目的地後重讀同一owner／client／workspace，本機snapshot須與審閱值完全相等。scope失效、取消、非法Unicode、64層深度或16MiB UTF-8上限違反均不交付部分資料。不需要或觸發receipt／fence／canvas POST，匯出不變更本機generation／封存／pending。

- `schema`：`boarderless.local-sync-evidence.v1`；`purpose`：`private-audit-copy-not-submission-or-authorization`。
- `userId`／`clientId`／`workspace`：本機帳號、client及完整私人畫布；不包含APP憑證設定、transport endpoint或素材bytes。畫布內使用者自行輸入的機密內容仍會完整匯出，不當成安全去識別化報告。
- `checkpoint`／`proposal`／`applied`／`outgoing`：保存的baseline、choices／target、實際local operation／digest、原domain交易及prepared wire IDs／clientSeq／requestDigest。沒有對原請求新增授權。
- `stopped`／`rebaseProposal`／`rebases`／`superseded`：完整現存本機封存，不只匯出摘要，不靜默截斷舊資料。
- `materializations`：optional完整source→local identity reservations，舊副本未含此欄位時為空；不能從reservation推論bytes已Ready。
- 使用與本機document一致的Kotlin結構化map key序列化：物件／關聯map為交錯key/value陣列；defaults及null保留。這不是canvas REST state schema。
- 歷史型別化receipt metadata不是raw server receipt／committed log完整副本，也不是fresh ACL／commit proof。不可直接匯入、重播、提交或藉此清除pending。
- Desktop／Android／iOS沿用既有新檔目的地；Web只確認Blob下載已請求。原生選擇器沒有服務20秒timeout；取消或scope失效仍按平台策略釋放資源。OS／provider／真人GUI及browser磁碟保存待人工驗收。

此v1沒有封存清理或驗證後reimport協定；匯出不解除32筆封存上限。後續清理須獨立明確審閱／批准與可靠備份策略，不能把下載請求當作安全刪除證據。
