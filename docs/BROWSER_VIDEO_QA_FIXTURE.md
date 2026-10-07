# Browser 影片 codec fixture 與回歸界線

日期：2026-10-05。這是 QA 輸入可靠性修正，不是 production 播放器／實機視覺已修復或簽核。

舊 `recordVideo` helper 用 canvas／MediaRecorder、100ms timer 換色與 requestFrame，沒有 encoded-frame acknowledgement。不能把 timer callback 次數當已編碼影格數；固定白色第一幀的假設不受該 helper 保證。[W3C Media Capture from DOM Elements 工作草案](https://www.w3.org/TR/mediacapture-fromelement/#html-canvas-element-media-capture-extensions)描述 request flag 與 canvas paint 的取樣關係。這支持競態風險的推論，沒有證明歷史 8775 初始幀失敗一定由此造成，也不證明播放器沒有問題。

現在 helper 名称為橋接相容保留，但回傳固定、異步 callback 的 730-byte VP8 WebM：32×32／10 fps，3 個白幀＋6 個藍幀，streaming mux 刻意不含 Duration。沒有改白色／後續色／immutable frame／播放／seek／結束／重播／release assertions，仍由真實 HTML video＋canvas＋Skia decode 測試；新增 initial position=0、bytes／SHA-256 oracle。沒有 MediaRecorder 原生生成測試的聲明。

固定 SHA-256：`ee13b6c257d11c080e0489977bfa9779ad1220f2552189ca8052dec11b7431ab`。來源直接嵌在 shared/src/webTest/resources/media-import-fixture.js，僅測試載入；不作 production asset／下載或 GIPHY 快取。

製作命令（先建立自己的 QA temp 目錄，以下輸出路徑只是示意）：

```sh
ffmpeg -f lavfi -i 'color=c=white:s=32x32:r=10:d=0.3' \
  -f lavfi -i 'color=c=blue:s=32x32:r=10:d=0.6' \
  -filter_complex '[0:v][1:v]concat=n=2:v=1:a=0[v]' -map '[v]' \
  -c:v libvpx -deadline best -g 3 -live 1 /absolute/qa-temp/fixture.webm
```

本輪 FFprobe確認 VP8／32×32／730 bytes／無 format duration；FFmpeg獨立 decode 第0幀 RGBA=`ff ff ff ff`、第4幀=`00 00 ff ff`。重新生成可能有 mux metadata／segment ID差異，不能直接假定 digest相同；更新固定 bytes 前先獨立驗像素／metadata，再明確更新 oracle，不只改 assertion配合錯誤輸入。

Node測試確認 callback異步、兩次相同 bytes／固定digest、不需要 canvas或MediaRecorder。它不是 native codec proof。JS／Wasm full suites須實際執行，重複驗證可用 task-local `--rerun`，確認報告 timestamps／0 skipped，不能拿 Gradle up-to-date 當多輪測試。

保留歷史 timeout／blob revoke／initial-frame 失敗證據；此輪成功只證明固定输入的回歸結果，不等於長期穩定性／真實素材／服務／手機已通過。使用者仍依產品 QA SOP驗下載／播放／聲音／遮擋；效能最後。ledger activation、legacy floor與原六項產品待辦不因此刪減。
