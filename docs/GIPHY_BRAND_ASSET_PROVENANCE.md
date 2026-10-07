# GIPHY attribution 素材來源

取得日期：2026-10-02。此紀錄僅證明使用官方提供的 mark，不代表本 APP 已通過 GIPHY production／法務／產品審核。

- 官方來源：[API Quickstart → Attribution](https://developers.giphy.com/docs/api/quick-start-guide/)。此頁提供 approved official logo marks 的 ZIP 下載連結。
- 原始圖包：[giphy-attribution-marks.zip](https://media.giphy.com/giphy-attribution-marks.zip)。非 API 搜尋結果；是官方提供給整合者使用的品牌標示資產。
- 原始 entry：`Giphy Attribution Marks/Static Logos/Small/Light Backgrounds/PoweredBy_200px-White_HorizLogo.png`。
- APP 檔案：`shared/src/commonMain/composeResources/drawable/giphy_powered_by.png`。
- PNG：200 × 42、RGB、1627 bytes；SHA-256：`839e790cd4c78b5aa3a416a6bfafc6ff3502e6d0447812ab7fcfed182a1ecb2e`。
- 保留原始 bytes／黑色背景／logo／文字，不重畫、裁切、換色或添加動畫；Compose 採 Fit、200 × 42 dp、不設 colorFilter。不同 DPI 的實際清晰度與可見性仍由產品負責人驗收。
- 圖包為 22,183,677 bytes，完整下載於 30 秒到期，未聲稱整包 hash 驗證成功。改從同一官方 URL 取得最後 65,536 bytes 的 HTTP Range（原始 offset 22,118,141），含 central directory 與此 PNG；Info-ZIP 提示 missing prefix 為預期，指定 entry 的 extraction／CRC test 均為 OK，原始 PNG SHA-256 如上。未解開 PSD、其他 zip entry 或執行任何圖包內容。
- 測試讀取 resource 並檢查原始 PNG header／尺寸／hash；native 從封裝讀取，Web 從同源靜態 bundle resource 讀取，不發 GIPHY CDN／API 請求、不消耗 API key 配額。Web 是否支援離線冷啟動仍取決於部署的離線資源策略，不能由打包圖檔推定。這不是將 GIPHY 使用者 GIF 快取到素材庫，兩種用途不得混淆。

必要放行項目：產品確認 dark／light、手機與桌面上的標示顯著且可讀、捲動後位置符合整合需求，並完成整體 GIPHY API 規範及 production 審查。不得從本檔推定其他媒體快取或重託管已獲核准。
