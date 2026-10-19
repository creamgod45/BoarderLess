# 本機建置清理

本機只保留專案，最後 APP／APK／Web／iOS 打包成品也刪除；正式下載成品由 GitHub Actions 統一打包發佈。

雙擊專案根目錄 `Clean BoarderLess Build.command`，或執行：

```bash
python3 scripts/clean-build-artifacts.py --apply
```

查看範圍與估計用量而不刪除：

```bash
python3 scripts/clean-build-artifacts.py --dry-run
```

本機建置完成後自動清理（建置失敗也會嘗試清理，保留原建置錯誤碼）：

```bash
./scripts/build-and-clean.sh :shared:jvmTest
```

此入口預設加上 --no-build-cache，避免本專案新的編譯輸出再累積到全域 Gradle build cache；共用相依套件下載仍可重用。

清理根專案、settings.gradle.kts 宣告的 module 及 iosApp 下的 build、.gradle、.kotlin、.cxx、.externalNativeBuild，包括 build/verified 最後成品、Web Gradle 產生的 node_modules、iOS package staging 與測試輸出。artifacts 中已知 BoarderLess 打包成品也清除；包含非已知成品時整次清理拒絕執行，不刪憑證。Xcode DerivedData 只清除 info.plist 的 WorkspacePath 確認屬於本專案的項目。

額外清理此前本專案的三個已知隔離建置根目錄（boarderless-ai-reasoning-build、boarderless-verified-build、boarderless-export-verify），以及 boarderless-media-validation.* 中確認結構的 build／gradle-cache。此前三種具名 Desktop QA 工作目錄中、bundle ID 確認為 BoarderLess 的 APP 副本也移除；QA 文件與原始素材不刪。

Karma 瀏覽器測試的 webpack 輸出也納入範圍。往後輸出改放到 Gradle 產生的 JS／Wasm test package 下的 karma-webpack，與 build 一起刪除。舊版遺留的 _karma_webpack_數字 暫存資料夾只在 commons.js 同時含 BoarderLess-shared-test module 與 cg.creamgod.boarderless 標記、內容全為目前使用者擁有的已知 webpack 平面成品時清理；其他專案、無法辨識的空資料夾、混入非成品或 symbolic link 的目錄保留。執行中的 Gradle wrapper／Karma 也會阻止清理。

刪除前檢查專案名稱、canonical path、使用者所有權及 Git tracked files。頂層 symbolic link、Git 追蹤檔案、建置中或正在使用待刪路徑的程序會阻止清理；內部 symbolic link 只移除連結，不循連結刪其他目錄。Gradle --status 確認沒有 BUSY daemon 後執行 --stop，再重新檢查範圍。

保留原始碼、Git／IDE 設定、APP 使用者資料、backend runtime dependencies／資料／Docker volumes。~/.gradle/caches、wrapper distributions、JDK、~/.konan、Android SDK、其他專案的 Xcode DerivedData 與 Docker 共用 cache 不是可可靠歸屬單一專案的資料，不會用全域 prune 刪除。專案自己的 .gradle 與獨立 GRADLE_USER_HOME 暫存會清理。Git 追蹤的 kotlin-js-store yarn.lock 保留。下次建置需要重新產生專案輸出與編譯快取，工具鏈及共用下載仍可重用。

請等本機建置完成並關閉從待刪 build 路徑啟動的 APP 後再使用；腳本不會強制結束正在使用的程序。

2026-10-08 已實際執行：專案／隔離 build 與 cache 約 8.1 GiB，加上 artifacts 及另外兩份 QA APP 約 411.5 MiB；合計約 8.5 GiB 建置資料。最後預覽為 0 B。11 項 disposable fixture 保護測試與 shell 語法檢查通過，包含建置失敗時仍清理並保留原錯誤碼。

2026-10-10：新增 Karma 成品清理並實際刪除 105 個已確認屬於 BoarderLess 的舊 webpack 目錄，加上本次隔離 build 回收約 5.3 GiB；重新辨識的本專案 Karma 暫存為 0。往後兩端輸出已確認位於各自 Gradle test package，下次與 build 一起刪除。清理保護測試共 12 項通過。
