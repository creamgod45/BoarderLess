# Desktop 快速方案重開與強制結束驗收

2026-10-08，以目前 `desktopApp/build/verified/BoarderLess.app` 的副本操作實際 Compose Desktop GUI。

## 隔離方式

- 測試 APP：`/var/folders/w7/qr3xxvk92z9b6xsjcyjbgqlw0000gn/T/boarderless-scheme-qa-reelt0dc/BoarderLess Scheme QA.app`。
- JVM `user.home` 指向同一 root 下的 `user`；沒有更改 shell HOME 或正式使用者資料。
- 測試副本使用獨立 bundle ID 與 ad-hoc signature。QA-only MemoryPreferencesFactory 僅隔離 UI preferences；快速方案及畫布仍使用正式 native file repositories，沒有替換成記憶體保存。
- 未呼叫 AI、未連線正式 backend。驗收完已關閉自有測試 APP。

## 實際 GUI 結果

1. 從物件庫插入樹狀圖，畫布呈現根節點、分支 A/B、葉節點 A1/B1，選取 5 個物件並保存為方案 1。
2. 方案簿顯示方案 1：5 個物件、4 條連結、v6。正常退出 APP，確認程序消失；使用相同 user.home 重開，方案仍存在且可插入畫布，節點由 5 增為 10。
3. 再保存新選取的 5 個物件為方案 2。方案簿同時顯示方案 1、2，各 5 個物件、4 條連結、v6。
4. 確認 durable record 的 generation=2、nextId=3、兩個原 ID/name，以及 BLDS0001 長度和 SHA-256 驗證均正確，才強制結束已識別的自有 QA PID 36346（SIGKILL）。確認程序消失後重開相同 APP。
5. 重開後實際方案簿仍呈現方案 1、2；選取方案 2、開啟預覽操作並插入，畫布由 10 增為 15 個節點。
6. 點擊畫布選單的復原，新增 5 個節點消失，回到 10；點擊重做，回到 15。這驗證的是 UI 按鈕，鍵盤快捷鍵未在此次驗收中確認。
7. 正常退出後再次確認自有程序消失；方案 library 的 SHA-256 與強制結束前一致。

## 檔案證據與範圍

自有 fixture `crash-evidence.json` 保留在上述 root，記錄 record 路徑、generation、nextId、ID/name、digest 與 GUI 結果。方案 record digest 為 `127f15ab048413b7b282117e53e0f9167571f6ea90a0721ea80fe7ca2ea2dd33`。

這證明 Desktop APP 已確認保存後，可經正常退出及程序 SIGKILL 恢復、插入、復原與重做。不涵蓋保存中途 SIGKILL、OS 電源中斷、正式 Preferences 保存、Web storage grant/eviction、行動端 process restart、跨裝置素材完整交付或 cloud scheme 同步。六條主線 goal 仍未完成。
