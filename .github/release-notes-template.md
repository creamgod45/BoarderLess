# BoarderLess {{VERSION}}

| 平台 | 產物 |
| --- | --- |
| Linux x64 | `.deb`、`.rpm`、免安裝 `.tar.gz` |
| Windows x64 | `.msi`、免安裝 `.zip` |
| macOS arm64 | `.dmg`、免安裝 `.tar.gz` |
| Android | `.apk`、`.aab` |
| iOS | arm64 模擬器 `.app.zip`、`.xcarchive.zip`；有 Apple 簽章設定時另附 `.ipa` |

桌面免安裝包內含 JRE。macOS 與 Windows 安裝包尚未設定程式碼簽章／公證。
Android 檔名含 `unsigned` 的 APK 需先簽署才可安裝；AAB 用於 Google Play，需使用上傳金鑰簽署。
iOS 模擬器包僅供 Apple Silicon Mac 的 iOS Simulator；`unsigned-archive` 無法直接安裝到 iPhone。
`release-testing` IPA 僅供描述檔中已登記的裝置；`app-store-connect` IPA 用於後續 App Store Connect／TestFlight 上傳。
iOS 後端連線依打包時的 `IOS_BACKEND_*` 設定；未設定時使用專案的開發連線預設值。
