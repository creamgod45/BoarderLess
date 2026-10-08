# 畫布 PNG／PDF 匯出

2026-10-08 使用者補充匯出修復紀錄已讀並與source核對：[修復 PNG 與 PDF 匯出](https://chatgpt.com/s/cx_6ac6f30ccf608191865c61999775acaf)。macOS固定419萬／1677萬pixels及8192edge門檻不屬產品規格，現行desktopCanvasExportRuntime使用Int／IntArray表示範圍，CanvasExportRaster亦僅表示限制；desktop destination maximumBytes=Long.MAX_VALUE，不沿用scheme包上限。UUID預設檔名避免衝突，已存在文件要求另命名；保存成功顯完整savedLocation並提供revealSavedFile（Finder／資料夾），實際Finder GUI仍未驗收。既有CanvasExportSceneRenderTest含1×／2×完整scene→PNG/PDF實寫測試，已在上一輪JVM1019中通過。此次EXIF修正保留上述當前改動，不恢復舊固定尺寸上限。舊批次尺寸上限記錄僅是歷史狀態，不代表目前macOS規格；其他平台仍需個別核對並避免聲稱同步解除。

2026-10-08 Desktop JPEG EXIF 方向修正：desktopJpegOrientation 只讀 JPEG header／APP1，以JPEG自身segment length逐marker最多64KiB配置解析 little／big-endian TIFF orientation，忽略前置 XMP，截斷／非法marker明確拒絕，不新增總metadata容量門檻。DesktopViewportImageLoader 以方向後尺寸規劃 viewport／export tiles，將 upright region 反投影回原始 sourceRegion，再只旋轉／鏡射已取樣小tile；複製ARGB不做alpha疊加，原檔不整張解碼。方向1..8的 live tiles 與 export 共用同一路徑，原授權／checksum／cache撤權保持。

新真JPEG測試先在舊程式重現錯誤（14666失敗），修正後43538 focused通過，再9190全 Desktop52 tests／0failures/errors/skips及 distributable 成功22s。1024×640四象限JPEG跨512px tile邊界，八種方向逐一驗viewport與export寬高及四角顏色（含方向5..8交換尺寸），fixture涵蓋兩種TIFF byte order及前置XMP；另驗截斷APP1／連續FF marker直到EOF確實拒絕及無EXIF回1。兩次load只download一次、authorize兩次，來源為fixture，native ImageIO／Skia像素真實執行。移除新metadata固定總量門檻後82429最終全Desktop52 tests／0failures/errors/skips及distributable重驗成功21s；ready Desktop bundle更新、diff check通過，未提交/push、未改backend/env。

此證據限Desktop正常JPEG viewport/export路徑，不代普通Skia preview／PNG eXIf／WebP／ICC或其他端live-preview parity與physical GUI。其他原始六主線缺項維持，真AI最後401仍未排除，goal active。

2026-10-08 Web 大圖原檔匯出：新增 BrowserExportImageSink／loadBrowserExportImage 與 runtime.loadExportImage。每次authorize核對asset/workspace/Ready／PNG/JPEG/WebP及1..200MiB，沿原AssetDownloadCoordinator驗size／SHA256，Kotlin不合併整個original，≤1MiB chunks以Base64跨JS/Wasm後逐塊轉native Blob parts；encoded originals只在transient handle，不進IndexedDB／localStorage／canvas payload。checksum成功後才native decode；輸出前從實際檔header規劃完整aspect-fit／EXIF orientation與≤4096每邊、4,194,304pixels，createImageBitmap resize＋from-image，sRGB canvas lossless PNG bridge到Skia。取消移除handle，late bitmap.close，canvas資源finally清理。native瀏覽器內部decode peak不作保證，不聲稱所有codec永不配置原尺寸像素。

header處理PNG IHDR＋eXIf、JPEG SOF＋Exif（忽略XMP APP1，避免把已有Exif方向重設）、WebP VP8X／VP8／VP8L＋EXIF；PNG/WebP可跳過壓縮資料讀metadata，取消逐輪检查。來源header最大65535每邊／268,435,456px；JPEG pre-SOS header／單個EXIF metadata限1MiB、PNG/WebP最多65536 chunks，超限明確拒絕，未默默fallback到小preview。main index.html載入image-export.js，Karma載入同source並追蹤Gradle inputs。

10060 最終驗證成功1m17s：JS913／Wasm913，0 skipped/failures/errors，各新增5 Native Chrome cases：14.4MP PNG→2500×1000完整邊緣8px條紋≥14次轉換、top紅／bottom藍與alpha128；撤權零二次download、foreign workspace零download、corrupt checksum拒絕無handle；取消decode後handle立即釋放及late bitmap只close1次；JPEG EXIF6＋後續XMP APP1長寬交換100×200／top紅bottom藍；33MiB trailing-padded PNG以1MiB重用buffer串流驗SHA後仍可16×8 decode，超過舊32MiB preview限制。取消測試首輪Wasm timeout因fixture以錯receiver呼叫Window API，修正saved.call(globalThis)後保留所有assertions並重驗。gateway為fixture，瀏覽器Blob/bitmap/canvas/Skia是真實執行，不代正式服務。

JS及Wasm production distributions都通過，webApp/build/verified/{js,wasmJs}已更新；逐byte確認artifact image-export.js與當前source一致，index包含script，diff check通過。四端均有export-specific image callback與APP接線；仍待完整APP GraphicsLayer/readback／下載到磁碟、JPEG／WebP／EXIF／ICC live-preview parity、真GIF／video frame及各平台GUI驗收。六條原始主線完整scope維持active，AI最後真401未排除，正式多人／跨裝置／shared背景與GIPHY provider-node／批准未完整交付。未改backend/env、未提交/push。Browser resize／EXIF語意依MDN createImageBitmap官方browser文件：https://developer.mozilla.org/en-US/docs/Web/API/Window/createImageBitmap。

2026-10-08 使用者回報 macOS 1×／2× 匯出及找不到檔案，並明確指出未要求固定匯出尺寸規格。原 4096px／4,194,304pixels 為實作自行加入的工程門檻，不是使用者需求；macOS 已取消這個門檻，也取消本次曾採用的 8192px／16,777,216pixels 替代門檻。Desktop runtime 使用 Int 維度／IntArray 索引的表示能力，不再以固定產品像素預算停用保存。PNG／PDF 編碼容量計算改以 Long 計算，只有超出實際 ByteArray 表示能力才拒絕，避免 Int overflow。Desktop 匯出檔不再繼承方案包 1GiB 配額；其他平台的原有 runtime 預算未在本次 macOS 修復中變更。Desktop 原檔匯出 loader 也移除 display 4096px／4,194,304pixels 與完整影像最多128tiles的門檻；新增5000×2000真圖片解碼／alpha驗證與10000×10000完整tile規劃，避免畫布尺寸解除後仍在素材階段被擋。實際 native render／記憶體分配失敗仍回報失敗，沒有承諾任意大圖一定能在所有設備完成。

Desktop 保存使用隨機預設檔名；若使用者明確選擇既有檔案，選擇器當場提示另選檔名，可繼續選擇，不等到寫入時才回傳籠統失敗。成功後顯示完整本機路徑及「在 Finder／資料夾顯示」按鈕；只有 publish 成功才提供這些結果。保留原子新檔發布、取消／scope guard 與不覆寫既有檔案行為。實際渲染／分配失敗包含 Throwable 回到匯出訊息，CancellationException 保持取消語義。

驗證新增真 Compose 大畫布 1×／2× 預覽 readback、兩倍率 PNG／PDF 實際新檔寫入與 PNG 原生 ImageIO 解碼；另驗 10,000px 長邊編碼及 25,000,000pixels layout 能通過，原方案包配額及不覆寫測試維持。最終使用獨立 build directory、停用 build cache 與 incremental compile 完整重建成功1m40s：JVM1015 tests（6個既有skip）、Desktop50 tests，零 failures／errors，Desktop distributable 與 git diff --check 通過，更新 desktopApp/build/verified/BoarderLess.app。GUI 選擇器／Finder 按鈕尚未完成本輪實際操作驗收，不能將 native render／filesystem tests 當作完整 GUI 驗收。

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

## 實作與保護

- Capture deep-freeze workspace／baseline，檢查 owner、role、user、client、server seq、epoch、read-ready；群組包含 descendants，standalone selection 解除外部 parent 並保持 version，拒絕部分 managed sequence。
- ExportScene 使用實際 TextNodeCard、GroupFrameCard、RelationCanvas、RelationLabel、WorkspaceSequenceScenes；MeasuredRelationLabels／MediaPaintBounds 與 live renderer 共用。測量包含外側文字偏移與旋轉、Bezier control hull／stroke、連線 route／arrow／label；缺少 media frame 明確拒絕。
- Preview 固定量測完整輸出尺寸後縮放顯示，DrawScope GraphicsLayer.record 橋接 draw context，toImageBitmap/readPixels。合成真 Compose fixture 1030×770 與獨立全尺寸 sRGB Scene 在相同 window lighting 下逐 ARGB 完全一致（changed0/max0），包含中文外側標籤、旋轉、超出 viewBox 路徑、自循環、手動連線、圖片與時序圖。
- Raster owns straight sRGB ARGB copy，輸出最大邊4096、最多4,194,304 pixels。PNG RGBA8 使用 stored DEFLATE／zlib／Adler32／CRC；PDF1.4 單頁 RGB XObject、可選 alpha SMask、FlateDecode／xref／Length。PDF 為影像，文字不可搜尋。
- Desktop 使用原子新檔发布與 force，既有檔不覆寫；取消／scope 改變只清私有 partial，已發布檔不删除。保存預設檔名 BoarderLess.png/pdf，可透過系統選擇器指定新檔。

## 尚待交付

各端 destination adapter、原始／tiled 大圖品質與真 GIF／影片 poster 驗收；其他 native 字體與極端 italic／shadow 邊界；selection 實際另存与重新開啟。不能將已完成 Desktop slice 視為完整六主線交付。
