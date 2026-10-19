# Release 打包

`.github/workflows/release-packages.yml` 依 GitHub Actions runner 作業系統產生桌面安裝包與免安裝版，另打 Android、iOS，再收集到同一 GitHub Release。Linux 提供 x64 與 arm64，macOS 與 iOS 目前為 arm64。

## Linux 架構

Linux ARM64 使用 `ubuntu-24.04-arm` runner 原生打包，產生 `BoarderLess-<version>-linux-arm64.deb`、`.rpm` 與 `-portable.tar.gz`；x64 產物保留 `linux-x64` 名稱。兩者會一起上傳 Release。Gradle 依 runner 的架構選用 JRE、Compose／Skia 與 FFmpeg／JavaCPP 原生函式庫。

ARM64 對應 `uname -m` 的 `aarch64`，不包含 32 位元 ARM。Linux runner 與套件以 Ubuntu/glibc 環境打包，不是 Alpine/musl 專用包。

## 啟動

- 推送 `v1.2.3` 格式的 tag 會直接建立公開 Release。
- GitHub Actions → Release Packages → Run workflow：輸入版本 `1.2.3`，預設建立草稿。
- 已存在的 Release 會覆蓋同名附件，保留其草稿狀態與說明。
- 手動執行建立新 tag 時，以 workflow 的實際 commit 為目標。

為符合桌面 MSI／DMG 限制，major 必須為 1..255、minor 為 0..255、patch 為 0..999，無前導零。Android versionCode 與 iOS build number 使用 `major * 1000000 + minor * 1000 + patch`，例如 `1.2.3` → `1002003`。同版本重跑不會增加版號；已上傳商店的版本需遞增。

## Android 簽章（Repository Secrets）

| 名稱 | 值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密碼 |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | 選填；key 密碼不同於 keystore 密碼時才需設定 |

未提供 keystore 時產生未簽署 APK／AAB。有 keystore 時必須設定密碼與 alias；未設定或留空 `ANDROID_KEY_PASSWORD` 時，自動沿用 `ANDROID_KEYSTORE_PASSWORD`，因此兩個密碼相同時只需三個 Secrets。若金鑰密碼不同，必須另填 `ANDROID_KEY_PASSWORD`；Base64／alias／密碼不匹配仍會使簽章失敗。

## iOS 簽章

未提供 Apple 簽章資料時，流程仍會編譯 Release 模擬器 app 與未簽署裝置 archive，不會產生可安裝 IPA。設定完整資料後會額外匯出 IPA，憑證／描述檔錯誤會使工作失敗。

Repository Secrets：

| 名稱 | 值 |
| --- | --- |
| `IOS_CERTIFICATE_BASE64` | 包含私鑰的 `.p12` Base64 |
| `IOS_CERTIFICATE_PASSWORD` | `.p12` 密碼；無密碼可省略 |
| `IOS_PROVISIONING_PROFILE_BASE64` | `.mobileprovision` Base64 |

Repository Variables：

| 名稱 | 值／預設 |
| --- | --- |
| `IOS_TEAM_ID` | Apple Developer Team ID；簽章時必填 |
| `IOS_BUNDLE_ID` | 預設 `cg.creamgod.boarderless.BoarderLess`；須與描述檔相符 |
| `IOS_EXPORT_METHOD` | 預設 `release-testing`；另支援 `app-store-connect`、`debugging`、`enterprise` |
| `IOS_BACKEND_SCHEME` | 可選，例如 `https` |
| `IOS_BACKEND_HOST` | 可選，例如實際部署的 API 網域 |
| `IOS_BACKEND_PORT` | 可選，例如 `443` |

`release-testing` 使用 Apple Distribution 憑證與 Ad Hoc 描述檔，裝置 UDID 必須已登記。`app-store-connect` 使用 Apple Distribution 憑證與 App Store 描述檔；此流程只匯出 IPA，不會自動上傳商店。`debugging` 使用 Apple Development 憑證與 Development 描述檔。`enterprise` 須有對應企業帳號與 In House 描述檔。

腳本檢查描述檔的團隊、Bundle ID、有效期與匯出類型；使用暫時 keychain，結束時移除簽章私鑰並還原 keychain 清單與原有描述檔。不要將簽章資料加入 Git。

未設定 `IOS_BACKEND_*` 時保留 `Config.xcconfig` 的開發預設值：模擬器連 Mac loopback、實機連開發 LAN。對外發佈時請填入實際 HTTPS 後端；後端回傳的媒體 storage 位址也須能從裝置存取。

## 本機打包 iOS

需要 macOS、完整 Xcode（含 iOS 與 iOS Simulator SDK）、JDK 21、Android SDK（Gradle 會設定 Android 模組）。腳本共用 Xcode 的 Kotlin framework build phase，不需 CocoaPods。

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
APP_VERSION=1.2.3 ./scripts/package-ios.sh
```

產物在 `artifacts/`；中間產物與日誌在 `build/ios-package/run.*/`。簽章時透過環境變數提供與 CI 同名的設定。Base64 可用 `base64 -i certificate.p12 | tr -d '\n'` 產生，存入 Repository Secret。

本機桌面：`./gradlew :desktopApp:packageDmg :desktopApp:createDistributable -PappVersion=1.2.3`（其他系統使用對應任務）。
本機 Android：`./gradlew :androidApp:assembleRelease :androidApp:bundleRelease -PappVersion=1.2.3 -PappVersionCode=1002003`。

## 官方參考

- [GitHub-hosted runner 架構與標籤](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)

- [GitHub Actions Apple 憑證與描述檔安裝](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications)
- [Apple：xcodebuild archive／exportArchive](https://developer.apple.com/library/archive/technotes/tn2339/_index.html)
- [Apple：測試與發佈方式](https://developer.apple.com/documentation/Xcode/distributing-your-app-for-beta-testing-and-releases)
