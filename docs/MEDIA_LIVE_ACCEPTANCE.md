# APP 素材真實服務驗收紀錄

2026-10-07 素材隨方案交付核心：新增 QuickSchemeBundleCodec 的 BLQS0001 串流容器（12-byte magic/manifest-length header＋strict UTF-8 manifest＋declared originals＋whole-container SHA256 footer）。Manifest帶既有 QuickScheme transfer definition與唯一 referenced asset descriptors，不帶signed URL／credential／ownerId／device path；source asset IDs限制plain identifier。引用集合須與media original及thumbnail IDs exact相同、thumbnail必須Image、每mediaKind與mediaType一致；最多64個原檔、每檔沿既有200MiB、容器1GiB、manifest4MiB、read/write chunk≤1MiB。不用Base64或把全部影片讀進memory；同一asset多個MediaNode只交付一份。圖片／GIF／影片都是workspace stored originals，GIPHY provider display資料不當作可快取原檔或暗中下載。

Export核明確來源workspace／fresh scheme與scope，逐asset authorize Ready metadata並核source ID/workspace，每原檔驗exact bytes／SHA256再全container checksum，最後fresh scheme和scope仍相同才commit；失敗abort只清owned partial。Review先strict JSON／Unicode／format/version／refs／metadata／limits，再stream驗全部asset SHA及wholefooter、完整EOF／no trailing，零upload/library/canvas mutation；failure releasesnapshot。Materialize要求caller綁exact review至明確confirmation，先重驗同一immutable bundle，逐次fresh editor/user/client/workspace/version/seq/full snapshot authority，沿原AssetImportCoordinator prepare/upload/confirm/Ready／uncertain recovery。beforeComplete要求hash＆完整讀取並先記目的ID，readImported／recordReady是必備receipt邊界；Ready全部驗原metadata及目的ownership、distinct目的IDs才回完整remapped scheme，original與thumbnail引用都重配，sourceWorkspaceId改目的workspace。尚不自行save library／insert canvas，caller必須再核scope後保存。

JVM新增DesktopSchemeBundleSource選取檔→private immutable snapshot（copy≤1MiB、sourcefileKey/size/mtime及EOF核對），刪／改picker原檔不影響snapshot；release只清owned files。DesktopSchemeBundleDestination force完整partial後以createLink atomically發布NEW檔名，不overwrite／replace舊檔或symlink；publish後directory-force uncertainty時abort也不刪finalfile。當前Posix/macOS adapter，未宣稱Windows／Android／iOS／browser picker已完成。

DesktopSchemeBundleImportReceipts沿既有DesktopAtomicDraftStore，scope=user/client/workspace＋bundleDigest，source descriptor→destinationId pending-before-complete／Ready，private exact-CAS／idempotentconfirm；重開先getAsset核同ID／Ready／full metadata，不將pending/missing/failed lookup當成可重新上傳。expected descriptor或destinationID改變拒絕，不reset損檔。首compile abort推導Unit?不符合interface已加Unit；native測試抓到key不該带prefix及新檔CAS應null而非0，改正並把測試核exact intentional interruption訊息，避免其他失敗冒充Ready後中斷。

新增common7cases＋JVM2cases：2MiB以上原檔stream（1MiB上限、short reads）、shared GIF media dedup/remap／receipt重試不prepare、corrupt original/footer/truncated/trailing拒絕、wrongworkspace/changedscheme/checksum export零publish、scope取消release/abort、撤權/改snapshot/改檔後零prepare、Ready後receipt失敗保留pendingID及原方案；native真正PNG來源import→原檔bundle→刪picker來源→另一native repository/materialize→Ready後故障→receipt/repository重開復用同ID→scheme保存／canvas插入／repo重開→verified download及ImageIO／exact original bytes一致、只一目的asset；file writer新檔visibility、no overwrite／snapshot獨立／abort無partial漏檔。

這是完整串流codec／原生檔案與receipt核心，尚未接GUI bundle picker／匯出匯入確認入口，其他三端binary adapters及physical跨裝置交付也未完成；不能用headless原生驗證宣稱使用者現在已可從APP跨裝置交付媒體方案。六主線goal維持active；不替代自動cloud scheme sync／正式協作／shared背景同步／GIPHY canvas provider交付。未動backend／.env／migration；actual user-dir AI log仍不存在，最後401未排除。

最終隔離 build 8701 成功1m23s：JVM982（6既有skip）、JS877、Wasm877、Desktop48全部0failures/errors；iOS arm64／Android main compile／Desktop distributable通過。ready bundle已更新核 codec/review/JVM source/destination/receipt classes，shared SHA256 3115d54cb51a519d2b68edba8a5a27b7aef2a41c3d68782447e0e6535ed2926d；无QA prefs fixture。這次沒有GUI新入口或GUI驗收，不把包中class存在當作APP媒體bundle交付已可用。git diff --check通過。

2026-10-06 本機素材交付更新（覆蓋下方歷史的「local media gateway停用」）：新增DesktopFileAssetGateway並接到Desktop預設本機repository、WorkspaceScreen匯入與Desktop圖片／GIF／影片讀取。素材位於~/.boarderless-storage/local/assets，獨立private原始檔與atomic metadata；每次chunk≤1MiB、上限200MiB，不把原始檔或picker/cache路徑塞入workspace JSON。先完整驗size/checksum、force原始檔、atomic move與directory force，才發布Ready metadata。workspace只保存asset ID；重開素材庫可查詢／重用，刪掉picker原始檔仍能讀回。查詢檢查本機session user/client/workspace；下載經既有AssetDownloadCoordinator與sink checksum驗證，損壞／跨畫布／截斷不能出預覽。原始檔已落盤但Ready尚未發布的中斷，可在明確metadata refresh時驗證並settle同一ID，不重上傳；沒有完整原始檔的Pending不假稱Ready。本機進度使用保存／驗證本機檔案文字，server mode仍用Backend gateway。

DesktopFileAssetGatewayTest4cases：真正PNG來源→import coordinator→media node→retainDraft→新repo重開→刪來源→verified download sink→ImageIO讀回；跨workspace／foreignclient／path ID／checksum corruption拒絕；changed/truncated source取消pending且不留original/part；upload完成而confirm未執行的restart→明確getAsset恢復同一ID。最終34087隔離build成功74s：JVM880（6既有skip）、JS799、Wasm799、Desktop46均0failure/error；iOS arm64與Android main僅compile通過、Desktop新包成功並複製desktopApp/build/verified/BoarderLess.app，直接核含local repository／media gateway classes。尚未GUI操作驗收、未宣稱四端本機素材或跨裝置交付完成；GIPHY沿現有provider display路徑，不當作本機上傳素材。沒有新user-dir AI probe log，最後真Router證據仍HTTP401；完整六主線goal active，不重新引入測試資料遷移前置。


## 最新 iOS 照片／影片來源入口（2026-10-05）

使用者確認iOS只有Files選項，原IosMediaPicker確實只接UIDocumentPicker。現在匯入先顯示中央來源選擇「照片與影片／檔案／取消」，Photos走PHPickerViewController、single selection、image/video filter與Compatible representation；Files保留原picker與格式。UIKit呈現固定Main，來源menu dismiss完成才開下一個picker；scope取消／dispose關閉owned panel，不resume晚到結果。

Photos不查PHAsset／整本照片圖庫、不請求廣泛Photos權限。選取後NSItemProvider file representation在completion內先核對1..200MiB並複製到UUID私有暫存，避免系統在callback返回時刪URL；再交既有IosFileAssetTransferSource immutable snapshot／SHA256／upload，finally刪中介副本，cancel取消NSProgress，resume取消清owned檔，120s export timeout安全失敗。正式支援provider明確提供GIF／PNG／JPEG／WebP／MP4／WebM，GIF優先不變成靜態JPEG。Compatible請求不是所有HEIC／MOV轉碼保證：未提供支援representation時明確unsupported，不偽裝副檔名／mediaType，也沒有新增APP自行轉碼或Live Photo動態支援。

依 [Apple Photos Picker](https://developer.apple.com/documentation/PhotoKit/selecting-photos-and-videos-in-ios) 與 [NSItemProvider暫存生命週期](https://developer.apple.com/documentation/foundation/nsitemprovider/loadfilerepresentation%28fortypeidentifier%3Acompletionhandler%3A%29) 接線。31981 simulator main首次compile15s成功；15679最後iOS Simulator測試与iosArm64 main compile成功55s，IosPhotoSelectionTest4cases／0fail／0error／0skip：single compatible config、GIF優先／unknown格式不重新標記、provider副本在原檔刪除後可hash/read並清兩份自有副本、empty/missing拒絕無leak。不是PHPicker GUI／iCloud／真實callback取消／硬體upload驗收，亦非Xcode完整APP build；新增boot simulator已恢復shutdown。

產品需重新build/install後驗兩來源／取消再開、PNG/JPEG／動畫GIF／MP4、HEIC/MOV Compatible實際representation及unsupported提示、iCloud離線／export等待取消、背景／換workspace取消、Node保存重開；不得刪user Photos原件。視覺／VoiceOver／safearea遮擋由使用者簽核，效能最後。未修改backend／env／keys，原六主線保持。

## Web 選檔誤判取消修正（2026-10-05）

使用者回報Web選完檔案顯示已取消。production media-import.js 原先在window focus後250ms檢查空files並finish(null)，會早於本機／雲端provider的change，移除input並丟掉後續選擇。新增 deterministic delayed-change case在原程式下確實失敗（9cases中1fail，8pass）；這證明競態存在，不宣稱已重現使用者瀏覽器的完整網路上傳。

移除focus推斷取消，改以input change／cancel或APP explicit cancel完成；清除input listeners／value與deadline，late events不重入。無cancel事件的舊瀏覽器保持待選，使用者可按APP取消；120秒deadline回明確選擇錯誤，不冒充使用者取消。append/click錯誤用安全常數。未改上傳協定、storage／backend、Kotlin來源hash與release。

84426 JS／Wasm Karma與兩種Web development webpack最後成功1m38s，BrowserMediaImportRuntimeTest兩目標各3cases／0fail／0error／0skip。兩種processedResources的media-import.js與修正source SHA-256一致，確認資源已進建置。Karma使用fixture選檔，不當使用者原生picker／實際signed PUT已通過；新focus競態由上述Node case驗證。未改其他平台，未啟動或修改backend、未讀env／keys；完整六項仍未完成。

`node --test webApp/tests/*.test.mjs`20cases／0fail／0skip通過，包含focus先到／延遲change、多次focus、native及explicit取消、deadline／late event、原有binary chunks／File upload／headers／abort／其他Web fixtures；git diff --check通過。JS／Wasm Karma與entry建置結果後補。需重啟／重建Web dev服務並重新載入新media-import.js，再以原瀏覽器重验選檔→prepare→upload→ready；視覺與完整實際storage上傳不由Node fixture代簽。

## iPhone 建置 API 設定修正（2026-10-05）

83922 唯讀 `xcodebuild -showBuildSettings` 證實原 iOS 配置為 `http://127.0.0.1:3000`；Info.plist 的 BoarderLessBackendURL 經 Xcode 展開後優先於 Kotlin fallback，因此實機會連手機自身，而不是目前LAN服務。這是可證實的實機預設配置缺口，但使用者截圖的安裝版本／實機或模擬器／檔案來源尚未確認，不能宣稱已找到該截圖唯一根因。

Config.xcconfig 新增 `BOARDERLESS_BACKEND_HOST[sdk=iphoneos*]=192.168.68.67`，保留 simulator loopback、現有 Info.plist／Local Network 說明與 ATS 設定，不開 NSAllowsArbitraryLoads、不改 backend 或 signed tickets。此位址是目前已驗證的開發LAN服務（health status=ok／database=up），不是 production預設；換網路需改device設定或build override，release部署需明確HTTPS服務。README補上重新建置／安裝與storage可達要求。

新增 `iosApp/tests/backend-config.test.mjs`，直接讀 Xcode 對兩種SDK的Debug解析及plutil讀source，非手寫xcconfig parser：simulator127.0.0.1／device192.168.68.67、scheme/port、Info URL參數、既有localnetwork permission與未開arbitrary loads、explicit HTTPS override優先。69022首輪2cases通過；加generic destination避免無關裝置inventory後78986最後2cases／0fail／0skip通過2.02s，git diff --check通過。只測建置配置，不當完整APP build／實機安裝、權限或素材上傳通過；未啟動模擬器、未覆蓋任何已安裝APP。

重跑配置驗證：

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer node --test iosApp/tests/backend-config.test.mjs
```

下一個產品驗收：使用者重新建置並裝到iPhone，同Wi-Fi且允許區域網路；以小PNG先驗prepare→upload→worker ready→Node→重開，記錄APP build、裝置、檔案来源／格式及最新分階段提示。若仍失敗，依實際stage与console的安全exception class追查；不得記錄API Key／signed query／私有檔案內容。不同Wi-Fi、storage9000不可達、檔案供應者與格式問題仍需各自證據。

## Android 原生載入唯讀驗證（2026-10-05）

`AndroidMediaNativeLoadLiveTest.retainedAssetsDownloadDecodePrepareAndRelease` 在 emulator-5554／API37 實際 instrumentation 通過：7001 `OK (1 test)`、case code0、無 skip，1.379s；Device APK 37369 建置成功10s。只更新既有獨立 `cg.creamgod.boarderless.shared.test` QA APK，不覆蓋使用者 APP。

使用既有六格式 QA workspace `c47bf81e-2a79-4fb0-aea6-2d36d47ffac3`、owner `60e37ef6-1e7a-4066-898b-c1878ae84d33`，GET-only refresh／正式 content ticket／signed GET／Android checksum sink；不 prepare、PUT、complete、提交 operation 或建立 identity。JPEG／WebP 與 GIF／MP4／WebM 的三張正式 poster 經 production AndroidAssetPreviewLoader 原生解碼，尺寸與 metadata 一致；GIF 全部影格48×32、總延遲1000ms；MP4／WebM 經 production loadAndroidVideo／MediaPlayer.prepareAsync，64×48、duration>0、muted、不失敗，pause／mute／seek command 後 idempotent release。各 loader 移除自己的暫存檔，fixture unique cache directory 最後為空並刪除；前後 workspace／version／serverSeq 相同。沒有新增或刪除 server 資料。

影片沒有 attach surface：不當畫面渲染、聲音、實際連續播放或 seek callback／實際落點驗收。GIF 解碼不是播放節奏或視覺簽核。未 opt-in 的另一普通 instrumentation 明確 `AssumptionViolatedException`／case code-4 skip，即使 runner 顯示 OK，也不算 live 通過。只改 AndroidDeviceTest source／文件，未改 production loaders；不重跑未變動的四平台 production suite。

重跑（唯讀、硬性核對上列 scope／base；scope不存在即失敗，不建立替代）：

```sh
/Users/wangfuxian/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r \
  -e class cg.creamgod.boarderless.AndroidMediaNativeLoadLiveTest \
  -e boarderlessMediaReadLive true \
  -e boarderlessMediaBase http://192.168.68.67:3000 \
  -e boarderlessMediaUser 60e37ef6-1e7a-4066-898b-c1878ae84d33 \
  -e boarderlessMediaWorkspace c47bf81e-2a79-4fb0-aea6-2d36d47ffac3 \
  cg.creamgod.boarderless.shared.test/androidx.test.runner.AndroidJUnitRunner
```

使用者最新 iPhone 畫面僅提供 generic import failure toast，未確認實機／模擬器、來源格式與 APP build；Mac readiness=ok 不能證明手機可連 storage9000，Android 此 case 不解除 iOS 問題。待補環境後重現 iOS picker→source→upload，不能猜成 ATS／權限／格式原因。視覺／VoiceOver／遮擋仍由使用者簽核，效能最後。

## Android 裝置實際 LAN 上傳驗證（2026-10-05）

本輪唯讀正式 content directive 已從 localhost:9000 更新成 `http://192.168.68.67:9000`，因此回到素材優先主線，不修改AI介面。後端配置由維護者更新；APP沒有修改／重啟backend，也沒有改寫signed URL。

新增 `AndroidMediaBackendLiveTest.contentUriUploadNodeReloadAndVerifiedNativeSink`：emulator-5554、API37 真正 instrumentation／Android HTTP engine，與測試APK私有 read-only ContentProvider。重用已保留的 QA workspace `1883808a-7a58-4062-9556-9de88bd55e89`／owner `8d9cac0b-a741-447c-9f37-97de230de00d`，明確arguments opt-in才允許server寫入；不讀／改使用者APP prefs、不使用原截圖、不新增identity。這段是本輪例外重用QA scope，早期「每輪新建scope」是各JVM案例歷史範圍。

fixture為backend manifest的PNG（442bytes、64×48、SHA256 `15b8da68f777d7caaab816ede7dc7364cc979cc9bffaab4e93d39e24b624d49d`）。先驗fixture hash，透過真正 ContentResolver query／getType／openInputStream 與 `AndroidUriAssetTransferSource` 產生immutable snapshot／原生bounds，再走正式 prepare／signed PUT／complete／worker ready；Uploading(0)與Ready有斷言。

接著正式CreateObjects→accepted→refresh，新的repository／client再GET載入同Node；正式content ticket斷言LAN host／9000，AndroidFileAssetDownloadSink逐chunk下載／SHA256驗證／publish、逐byte與fixture相同、只有1個ready檔，BitmapFactory原生解碼64×48。不是MockEngine，不是JVM代跑；未驗native picker UI、使用者原200214bytes截圖、OS／Activity重啟、其他五格式Android播放或完整双人雙裝置。

證據：2904首次Device APK compile失敗6s（MatrixCursor intersection／WorkspaceSession參數／reference欄位），已修；17126建置成功10s。APK merged manifest確認獨立package／instrumentation target為 `cg.creamgod.boarderless.shared.test`；本輪新增私有QA provider、INTERNET與cleartext配置只改test manifest，不修改原APP manifest。原APP package `cg.creamgod.boarderless`不覆蓋。安裝前QA package不存在，本輪僅新增test APK。

5118實際instrumentation返回 `OK (1 test)`、2.635s、status code -1／case code0（無skip）：

- 新ready QA asset：`09fd30bd-76bf-4276-9aa4-4742bb8147b5`
- 新QA Node：`edd28f84-0f76-4fc4-9fc2-385df6797236`
- 實際signed storage origin：`http://192.168.68.67:9000`（不保存query／signature）

僅1個新QA asset／Node保留在上述原QA workspace，沒有自動刪除server資料。fixture自有source／snapshot／下載檔與唯一flat temp目錄已清理；獨立QA test APK仍安裝於模擬器，便於重驗，未改使用者文件。

556321普通切片另跑3個instrumentation cases：AndroidMediaBackendLiveTest明確assumption skip（不能当又一次live成功）；AndroidMediaRecoveryDeviceTest真實checked commit／XML／dismiss 1case與GiphyBrandResourceTest原生Res／hash 1case各code0通過，runner `OK (3 tests)`包含1skip。這只解除这兩個從未執行的Device切片，不當完整AndroidDevice全套或APP kill/restart驗收。

重驗（會再新增1個QA asset／Node，請只對指定QA scope明確執行；普通run不要設live旗標）：

```sh
/Users/wangfuxian/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r \
  -e class cg.creamgod.boarderless.AndroidMediaBackendLiveTest \
  -e boarderlessMediaLive true \
  -e boarderlessMediaBase http://192.168.68.67:3000 \
  -e boarderlessMediaUser 8d9cac0b-a741-447c-9f37-97de230de00d \
  -e boarderlessMediaWorkspace 1883808a-7a58-4062-9556-9de88bd55e89 \
  cg.creamgod.boarderless.shared.test/androidx.test.runner.AndroidJUnitRunner
```

這裡為功能驗證時間，不是效能門檻；視覺／VoiceOver／遮擋仍由使用者簽核，效能最後。下方JVM證據與未驗條件保持其原範圍。

更新：2026-10-05。這是 JVM APP data／repository 對已運行本機 backend 的功能整合證據，不是畫面、原生選檔／播放、正式登入或產品放行。

## 執行環境與邊界

- `http://localhost:3000/health/ready` 實際回覆 `status=ok, database=up`；服務公開狀態頁顯示 `storage: s3`。測試未讀 `.env`／簽名金鑰、未啟動／修改 backend 或 worker。未單獨核對 S3 vendor，不能稱 AWS production 驗收。
- 重用 repo `backend/tests/fixtures/media/manifest.json` 與六個真實編碼 fixture。先核對 byteSize／SHA-256，再作任何 API 寫入；非 GIPHY 內容。
- 使用真實 `BackendAssetTransferGateway`、`AssetImportCoordinator`、`AssetDownloadCoordinator`、`BackendWorkspaceRepository`／MediaNode factory，不是 MockEngine。sink 為隔離記憶體 fixture，commit 前核對 SHA-256 與原檔逐 byte 相同，不宣稱正式 Desktop 檔案快取已驗。
- 每次建立新的 owner／viewer／outsider 與 QA 工作區；不讀／改既有 APP preferences，repository 使用 InMemorySettings。只操作該 run 新建資料。撤除的是 fixture viewer membership；沒有自動刪除 QA users／workspaces／ready assets，APP 原本的 pre-completion cleanup 政策不變。
- backend 現在是 dev `x-user-id` 身分機制：ACL 證據是已交付伺服器角色規則，不是正式 token／登入安全已完成。

## 已通過的功能範圍

PNG／JPEG／WebP／GIF／MP4／WebM 六格式各驗：prepare→signed PUT→complete→ready、owner 與 viewer authorize→signed GET→校驗、viewer prepare 回403、outsider content 回404、建立正式 MediaNode operation→accepted→refresh。

GIF／MP4／WebM 另外等待縮圖 metadata，確認縮圖 ready／image MIME，並用 viewer 經正式 authorize／download／checksum 讀取。沒有自動視覺識別或播放判定。

新建第二個 repository 與 clientId、使用 owner／workspace identity 重新載入 server state：六個 Nodes／assetId／mediaKind 都存在，並再校驗下載。這是新 repository／client 的冷載入，不是 kill APP、Desktop OS 冷啟或雙人 UI 協作驗收。最後撤除 viewer，舊 viewer session 的 content 請求回404；不接受新 signed ticket。

404 非成員／撤權是 backend `requireWorkspaceAccess` 的刻意隱藏存在政策；已查實際 response 與 service，不把它改成任意403／404都算通過。

## 可追溯證據

`MediaBackendLiveAcceptanceTest.realStorageWorkerAclInsertAndColdReopen`：

- 79932 首輪 compile 失敗（MapSettings 未在依賴中），改既有 InMemorySettings；未發 API 寫入。
- 12162 失敗（9s）：PNG ready／owner-viewer下載已通，ACL 斷言誤期待 outsider403；核對實際404後只修斷言，不改 server ACL。
- 51149 成功（10s），XML `2026-10-05T01:38:08.049Z`，1 test／0 failures／0 errors／0 skipped。
- 38758 最後 source／Gradle opt-in cache gate 成功（18s），XML `2026-10-05T01:39:31.934Z`，1 test／0 failures／0 errors／0 skipped。實際重跑，不是快取；六個 `LIVE_MEDIA_ACCEPTED` 與 `LIVE_MEDIA_COMPLETE` 記錄已核對。

最後成功 scope：workspace `c47bf81e-2a79-4fb0-aea6-2d36d47ffac3`；owner `60e37ef6-1e7a-4066-898b-c1878ae84d33`；viewer `3451f636-5611-42da-893c-dedf1b761b83`（已撤除）；outsider `1fcf0ef0-02a9-4978-854c-6b27df661e27`。

| MIME | Asset ID | Node ID |
| --- | --- | --- |
| image/png | a3eba4ee-33fe-4e65-babd-79ea530b49a1 | 461f9f29-5743-4fc0-95ea-25ee41a76aec |
| image/jpeg | 92d842e8-d11a-4467-9e59-b36694eb218c | 57162df6-a0e6-4690-9014-93f2290c94f1 |
| image/webp | 8e374c2c-a25c-4ce2-93e9-3f01b08990c8 | 2706da11-d9e6-4576-a684-24daa0351e16 |
| image/gif | fa7a6188-fcbf-48ce-b05d-44cff16acaa7 | d958b19b-57e3-4247-a5d2-c5abb8bed199 |
| video/mp4 | 0c124f64-fdc3-4d54-a536-faff2a64623f | 7b82247c-b826-4851-aacd-aa0424307ecf |
| video/webm | 20adddf8-e685-432a-9142-f8ab992b2019 | de843c82-6d36-4c59-b787-95bc3c3b0b59 |

前兩個 scope 也保留：`7c3386e9-2b84-40a2-8ca2-dc8061e049e6`（PNG partial、未插入 Node）與 `70a434ba-8e9a-4ddc-9962-619e2a834ef5`（六格式成功）。共3個新 QA 工作區／9個新 fixture users；不是使用者原工作區。清理另由使用者選擇，不批次刪除或自行處理 storage object。

## 重新執行

### Desktop 正式檔案 sink 的唯讀 live驗證

67390 XML timestamp `2026-10-05T02:00:39.990Z`，1 test／0 failures／0 errors／0 skipped。78410普通JVM／Desktop回歸成功3s，live四case均skip，Desktop同內容cache；不當新的live證據。

84429首輪成功13s；67390改成GET-only `repository.refresh(seed)` 後最後成功5s，避免openOrCreate的404自動建立fallback。`realDownloadToJvmFileSinkAndConsumerCancellation` 實際signed GET配正式 `JvmFileAssetDownloadSink`，不是記憶體sink：原檔size／checksum與PNG fixture一致才下載；成功發布檔案逐byte一致且無part，後續abort不刪已驗證檔案。另一下載收到第一chunk並寫入part後取消真正consumer coroutine，Ready不emit、part清除、不發布target。這是consumer callback gate，不是TCP半途中斷或Desktop UI播放證據；小PNG可能已全數進入第一chunk。

重用上述cancel-run的workspace／owner／Ready asset，只讀server、未新增scope／Node／素材／身份，累計仍5個QA工作區／11個users。測試自建暫存目錄與檔案已清理，不接觸APP正式cache／prefs。四live cases中的此case需額外明確scope，缺少即skip；其餘三case維持各自原先opt-in建立fixture流程。

唯讀重跑（scope若已清除應失敗，不自動建立替代；先核對是否仍為PNG fixture）：

```sh
BOARDERLESS_MEDIA_LIVE_ACCEPTANCE=true \
BOARDERLESS_MEDIA_LIVE_BASE_URL=http://localhost:3000 \
BOARDERLESS_TEST_REPO_ROOT="$PWD" \
BOARDERLESS_MEDIA_LIVE_DOWNLOAD_USER=8d9cac0b-a741-447c-9f37-97de230de00d \
BOARDERLESS_MEDIA_LIVE_DOWNLOAD_WORKSPACE=1883808a-7a58-4062-9556-9de88bd55e89 \
BOARDERLESS_MEDIA_LIVE_DOWNLOAD_ASSET=4c8c014b-a259-41c2-8084-f69ef7171d29 \
./gradlew :shared:jvmTest --tests '*MediaBackendLiveAcceptanceTest.realDownloadToJvmFileSinkAndConsumerCancellation*'
```

scope env已加入Gradle inputs，live仍禁止cache復用。未opt-in普通suite現在4 cases均skip；不能用普通綠色build冒充本次live。iOS／Android／Web正式sink、Desktop播放器與GUI、TCP傳输中斷、OS重啟／過期票券仍需各自證據。

### 真實服務取消邊界（2026-10-05）

93711 `realCancellationBeforeAndAfterCompletion` 成功10s；XML `2026-10-05T01:55:57.989Z`，1 test／0 failures／0 errors／0 skipped。真實prepare／PUT／complete／worker與下載，僅在APP gateway返回邊界以deferred gate暫停，取消實際子coroutine；不是TCP傳输中斷或UI取消按鈕驗收。

- 完整PUT後、尚未complete即取消：保留CancellationException、不emitReady／RecoveryRequired、不建Node；coordinator執行一次abandon，metadata404、提醒空，重複abandon成功、原ticket complete409不能復活。正常pending-only cleanup僅作用本run新資產；未驗storage刪除job何時完成。
- 真實complete返回後取消：不abandon、不emitReady、不建Node；RecoveryRequired與提醒保留，回查同asset ready並校驗下載。嘗試abandon ready回409，之後仍ready。無重上傳；提醒為InMemorySettings，不宣稱OS durable或殺APP恢復。
- workspace `1883808a-7a58-4062-9556-9de88bd55e89`，owner `8d9cac0b-a741-447c-9f37-97de230de00d`；放棄asset `1b34e250-85f8-4568-831e-a16fded1c445`，保留ready asset `4c8c014b-a259-41c2-8084-f69ef7171d29`。沒有Node，新增scope／身份／ready保留，累計5個QA工作區／11個fixture users。

單獨重跑使用下方env與 `--tests '*MediaBackendLiveAcceptanceTest.realCancellationBeforeAndAfterCompletion*'`。class目前3 cases；全class每次新增3工作區／5身份，一般未opt-in三case皆skip。前文2-case與4scope紀錄是歷史時點，不是最新總量。真實網路傳输途中取消、完整OS重啟、signed URL過期及撤權票券邊界仍Pending。

### 追加失敗邊界證據

26241：`realChecksumRejectionAndLostCompleteResponseRecovery` 實際成功22s，XML timestamp `2026-10-05T01:45:55.749Z`，1 test／0 failures／0 errors／0 skipped。

- 真實PNG bytes搭配錯誤宣告hash，worker回Rejected／checksum_mismatch；APP回RecoveryRequired、不emitReady、不abandon、不建Node；content授權409。
- 第二資產實際complete成功後，APP gateway邊界刻意拋IOException（不是TCP封包遺失）；回查同asset ready及校驗下載，不重新prepare／PUT、不自動建Node。相同ticket重複complete兩次維持asset ID；只證明API結果，未量測內部job數。
- 重建MediaRecoveryStore但沿用InMemorySettings，提醒保留；不是OS durable／APP kill證據。明確建立Node後才dismiss本機提醒，Rejected提醒與server Ready資產保留。
- workspace `d9d59aab-290f-466a-923c-2eafebc1f3bb`，owner `aa83feea-20be-4909-b588-f38f6e1018fc`；Rejected asset `d0b8a971-87a7-4c5c-9d75-307d9bd6f299`，Ready asset `3e348c90-a5c6-42c7-82b6-9ee2da0099ea`，Node `066ea533-28c4-41a0-acb2-3357bc4dd164`。加上上述歷史run，目前共4個QA工作區／10個fixture users；未刪除。

只重跑此邊界時用下方相同env與 `--tests '*MediaBackendLiveAcceptanceTest.realChecksumRejectionAndLostCompleteResponseRecovery*'`，避免重做六格式。整個class現在有2 cases，每次全class執行建立2工作區／4身份；一般suite未opt-in會skip兩case，不能當live通過。

先由 backend 維護者確保 API／DB／storage／worker 已運行。於 repo 根目錄執行（需專案相容 JDK）：

```sh
BOARDERLESS_MEDIA_LIVE_ACCEPTANCE=true \
BOARDERLESS_MEDIA_LIVE_BASE_URL=http://localhost:3000 \
BOARDERLESS_TEST_REPO_ROOT="$PWD" \
./gradlew :shared:jvmTest --tests '*MediaBackendLiveAcceptanceTest'
```

只接受明確 opt-in 和 localhost／127.0.0.1:3000，不提供 production URL 入口。每次會建立新的 QA 資料，先確認可接受。live flag／URL／fixtures 都是 Gradle inputs；live run 禁止 up-to-date／build-cache 復用，必須核對 XML skipped=0 與本次 timestamp／scope。未 opt-in 的一般全套會明確 skipped，不能拿綠色 build 當 live 通過。錯誤輸出只保留 stage／exception class，不公開 response body、signed URLs 或 headers。

## 尚待產品簽核與服務邊界

### Android 恢復保存屏障（開發與裝置簽核分開）

後續76671（1m35s）已修原1532品牌Host fixture：common斷言保留，Host精確讀Gradle供應當次APK entry，缺檔／超量失敗，不從source補檔；JVM／iOS／Web／AndroidDevice仍Res API。完整AndroidHost656cases／0 failures／0 errors／0 skipped，APP與Device APK建置成功。此解除Host runtime-stub路由缺口，不把1532歷史失敗刪除；不代表AndroidDevice／cold launch／OS重啟與產品簽核完成。

70483最後iOS全套成功39s，import24cases通過，新增boot simulator恢復shutdown；common suspend barrier四目標回歸補齊。Android實機保存／cold launch仍Pending；focused Host不解除1532完整suite品牌資源失敗，下一步修其正確artifact／Device route。

1532全套5m39s失敗：AndroidHost 656tests中1個GiphyBrandResourceTest呼叫DefaultAndroidResourceReader，於JVM stub `Log.d not mocked`失敗；未改該測試／放寬assertions／設returnDefaultValues。其他目標import24cases與新增Android保存3cases均通過。22874明確聚焦Android保存3＋import24＋store9及APP／DeviceTest APK建置成功4s；此切片綠色不解除完整Host suite失敗。品牌資源待Host artifact fixture或Device routing，不以本輪成功重跑抹去。iOS最後結果另列。

本次核對multiplatform-settings 1.3.0實際Android sources：Settings預設`${packageName}_preferences`採apply，改commit=true仍忽略boolean。Android recovery factory現以Startup initializer取得同application preferences，僅record／remove改checked commit；false即「結果無法確認」向上傳遞，不以記憶體更新當保存成功、不rollback／自動重試，未搬移legacy或改其他偏好。規範見 [SharedPreferences.Editor](https://developer.android.com/reference/android/content/SharedPreferences.Editor)：apply非同步；commit回傳成功與否，false也可能是未知結果。

beforeComplete改suspend；APP保存／dismiss移到Default dispatcher，畫面內mutex串行處理兩種寫入，不讓同步commit／flush占UI執行緒。取消等待屏障不送complete；保存完成後若取消仍依既有pending-only政策處理，可能留下保守提醒，不自動刪資產。這個畫面mutex不是多process／多window CAS，不當完整durable journal。

AndroidHost新增3個SharedPreferences protocol double cases：commit不用apply、false但memory更新仍fail並保留legacy、不確認dismiss時不retry。DeviceTest新增真實SharedPreferences／XML落盤及dismiss case，只碰unique test檔。現場SDK adb清單無装置，因此DeviceTest即使APK建置成功也不得標成執行通過；process kill／重啟與GUI仍待實機。merged APP manifest已確認Startup provider／recovery metadata與既有SettingsInitializer同時保留。79152首輪43s編譯失敗缺startup import，已明確加入與原runtime相同的startup-runtime1.2.0 compile dependency；最後回歸結果另列。

### Desktop 恢復提示的保存修正（非live API測試）

71953最後iOS全套成功43s，MediaRecoveryStoreTest 9cases通過，新增boot simulator恢復shutdown；這驗common行為及iOS編譯，不證明iOS Settings硬體落盤。普通suite四個live cases皆skip，未以此重做或冒充新API live證據。

本次檢查multiplatform-settings 1.3.0實際JVM sources：預設Settings使用Preferences.userRoot，put／remove只更新Preferences，沒有flush。依 [Java Preferences規範](https://docs.oracle.com/en/java/javase/17/core/preferences-api1.html)，寫入允許非同步；恢復ID必須在complete前確認保存，不能只以記憶體可讀推論重啟可讀。

MediaRecoveryStore的JVM預設改為同userRoot／同key的flush wrapper，不搬移既有資料；putString／remove等待flush，異常向上傳遞，不靜默重試／rollback未知寫入。共用save核對回讀值後才移除legacy key；被忽略的寫入／刪除不能回報成功。beforeComplete既有gate在record失敗時停止complete，僅保留原本pending-only cleanup政策。Android／iOS／Web factory仍是原Settings，未宣稱其他平台相同落盤保證。

62458最後JVM／Desktop／JS／Wasm、Android APK／Web entries成功31s：MediaRecoveryStoreTest三目標各9cases；MediaRecoveryPreferencesTest JVM3cases（flush失敗保留legacy、真實Preferences UUID、3個獨立JVM record→halt→read-dismiss→halt→read-empty）。halt不執行正常shutdown hook，只有隔離的unique QA node，完成後清理；不是硬體斷電、跨process CAS／多writer防覆蓋、GUI或網路取消重開整合驗收。82074首輪1m54s因未傳fixture-root env導致Karma配置失敗，保留紀錄，不當測試通過；補env後62458成功。iOS回歸結果另記。

依「產品 QA 品質檢驗 SOP.md」由使用者在實際 APP／自己的 QA 工作區驗六格式匯入、重開、GIF／影片播放音訊、圖片預覽／viewport解碼、縮圖、連線標註、旋轉拖曳、下載到正式平台sink、小螢幕控制列／遮擋／VoiceOver並截圖。不要把 fixture owner 自動寫入使用者 APP 身分。

Android／iOS／Web 原生 picker／DirectAssetUploadSource／CORS／TLS、實際 worker 重啟／延遲、過期 signed URL、真實TCP斷線／OS重啟／cancel恢復、其他格式checksum拒絕、撤權後已發 ticket 的存活邊界、storage GC／production登入、安全部署仍需各自正式證據；以上有限失敗case不是這些全部完成。GIPHY 授權 modal／保存仍待來源或供應商批准確認，WS／方案簿／背景／AI／鋼筆完整主線保持，效能最後。
