# Desktop AI 草稿設定與拒絕授權驗收

2026-10-09 使用目前程式碼重新打包的 Desktop 自有 QA 副本，透過 cua_repl 操作真正 Compose 畫面。

## 隔離與範圍

沿用 `/var/folders/w7/qr3xxvk92z9b6xsjcyjbgqlw0000gn/T/boarderless-scheme-qa-reelt0dc` 的 QA user.home 與獨立 bundle ID。QA-only MemoryPreferencesFactory 只隔離 UI preferences；workspace／方案仍為正式 native file repositories。原 fixture 畫布15節點／12連線，未使用正式APP資料、API key或backend。

測試 endpoint 使用 `https://example.invalid/v1/chat/completions`、model為 `fixture-model`、authentication為None。這是刻意無法使用的測試設定，不是建議的Claude endpoint。三次任務皆在審核時拒絕，沒有勾選同意或送出。

## 實際結果

- 畫布選單 → AI 圖形理解驗證 → AI 協助編輯圖形（草稿）可正常開啟；用途說明、0物件空範圍及整張畫布範圍正確。
- 明確選整張畫布後顯示15個 N 代號和12條 E 代號、文字／箭頭方向，捲動後可操作Router、模型、上限與任務欄位。此fixture均為Forward，不把此GUI驗收擴大到所有方向。
- Compose文字欄位不支援AX setValue；改以實際click/paste並讀回AX，確認endpoint/model/2048/task均成功輸入。
- Chat第一輪待審核body包含max_completion_tokens=2048，Claude包含max_tokens=2048與input_schema；兩者同意送出在未勾選時disabled。按拒絕均回到設定。
- GUI發現拒絕後呈現 generic failed/config 錯誤；修正共用diagnostic label/hint及工具task message後重新打包，實際看到「未授權，本次請求未送出；任務已停止。」沒有改安全log的型別化code。相同說明目前三處重複，可後續簡化。
- 最終APP另實際選Chat max_tokens，待審核body確實只顯示max_tokens=2048，沒有自動送出；第三次拒絕後能取消回画布。
- 自有安全log共6行、3個ConsentDenied；零Connecting／HttpResponse。最後一輪開始前後兩份 `.record` SHA-256一致；沒有套用變更或修改方案。正常退出後已確認自有PID消失。

證據摘要保留於同一QA root的 `ai-gui-evidence.json`，record baseline為 `ai-gui-record-baseline.json`；兩者沒有key／prompt／原始provider response。測試APP與隔離build已依使用者要求清除，不可假設仍可重開。

## 驗證與限制

最終四目標shared共用編譯、Desktop distributable與GUI QA task成功，總3m04s含GUI等待。只改顯示文字及分類，未新增會鏡像實作的測試。此前工具迴圈回歸結果另見既有workflow文件，不能當作本輪重新執行。

臨時測試init首輪使用Gradle9已移除的project.exec而失敗，改providers.exec後可啟動。一次Quit後再讀已關閉APP的AX造成QA重開，cleanup按設計拒絕在用路徑；確認並停止該自有PID後再清理。最終Quit後直接確認PID，不再要求已關閉APP的AX。最後build-and-clean清除約553.4MiB，dry-run0B。

未驗真正provider成功、HTTP400修正後成功、第二輪收費授權、真實AI工具提案與最後套用，或mobile/Web GUI。這些仍屬六主線未完成項目；本次只能證明Desktop入口、設定、審核前停留與拒絕操作。
