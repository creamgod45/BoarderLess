# Undo／Redo 還原驗收（2026-10-05）

## 已接線範圍

APP 的 Undo／Redo 明確使用 remoteRestoration，不把任意 create 自動轉成 restore。還原送 `restore_objects`／`restore_relations`，只含 IDs 與 tombstone 預期版本；同一批群組及 children 一起還原，再還原 relations。active v → delete tombstone v+1 → restore active v+2；後續 delete 使用目前版本。

送出前把刪除版本、還原快照 SHA-256 與 exact wire 一起寫入既有 pending journal。收到匹配的完整 acknowledgement 後，保存 scoped deletion evidence（API origin／user／client／workspace／entity、transaction ID、serverSeq、版本及快照 digest），成功回讀才清 pending。還原前核對 evidence；缺證據、不同 scope、版本或快照不同，在配置 clientSeq／送 HTTP 前拒絕。不確定提交仍留 pending，不從 refresh 的缺席推定刪除已提交、不自動重送。

這是 Settings 保存，**不是跨 key 原子交易或各平台斷電 fsync 保證**。寫入失敗保留 pending，exact wire explicit retry 可重建證據。Undo stack 尚未跨 APP 重啟保存；旧 pending 沒有刪除證據不授權 restore。receipt／fence 草稿合併、完整多 writer／cold-start 驗收另列待辦。還原權限及版本由後端提交時最終檢查；未增加新的確認 modal。

## 自動測試及真正 HTTP 驗收

- 20001 focused JVM：history 14、restoration plan 3、新 restore 接線 5、正式 fixture 檢查 3，全部通過。
- 36118 最終 JVM 全套：707 tests、0 failures、0 errors、4 skipped；skip 為未 opt-in 的其他 live 測試，不列通過。
- 最終 restore live 1 case、0 skipped、0 failures；實際經 APP repository 對既有 localhost 後端送出 create／delete／restore／refresh，沒有修改、啟動或重啟後端。
- 最終獨立工作區 `09ffed45-d34c-43c9-a52f-434b32c9db3f`；首輪工作區 `3f0b8034-1f03-4186-ba26-14b75d257cdc`，均保留供檢查。未改既有使用者畫布。最終 group／child／relation 還原、child parent 保留，反覆 Undo／Redo 後物件與 relation active version 7、serverSeq 15。
- MockEngine 驗 repository 重建後證據可用、缺證據／不同 user／竄改快照無 HTTP 且不消耗 clientSeq；不當 OS 冷啟或實機 UI 證據。

重跑 live（會新增獨立 QA 畫布）：設定 `BOARDERLESS_RESTORE_LIVE=true`、`BOARDERLESS_RESTORE_BASE=http://127.0.0.1:3000`、`BOARDERLESS_RESTORE_OWNER` 為授權 QA owner UUID，執行 `:shared:jvmTest --tests '*BackendHistoryRestorationLiveTest' --rerun-tasks`。普通 run 明確 skip，必須核對本次 XML timestamp／skipped=0／打印的工作區，不以 Gradle cache 代替。

## 正式 fixtures 的實際問題（需後端維護者處理）

原樣讀取 `backend/tests/fixtures/contract/canvas-v1/`，未修飾錄製資料或修改 backend。

1. `16-delete-cascades-relation-accepted.json`：payload 的匿名化 object ID 與 expectedObjectVersions 的原 UUID 不一致。
2. `20-restore-objects-relations-accepted.json`：restore_objects 的 expectedObjectVersions key 未匿名化；restore_relations 缺 expectedObjectVersions；兩個 committed restoredVersions 的 keys 也沒有對齊匿名化 IDs。
3. `22-state-after-restore.json`：state 可正常 decode，恢復的物件及 relation active version 3。

新增 fixture tests 明確驗 **20 被严格 gate 拒絕**、22 state decode；不是「31 份全部合約通過」。應修錄製正規化規則（含 JSON map keys），補完整 relation CAS 後重新錄製，再把拒絕測試改為正向 wire／ack 契約驗證。APP 不略過版本檢查來配合錯誤範例。

## 產品 QA（由使用者簽核）

重新 build APP，在專用測試畫布：新增物件 → Undo → Redo；刪除含 child 的群組與其連線 → Undo → Redo → Undo。應保留原 ID、內容、parent、relation label／intent／方向，不出現重复物件；刷新或第二個 client 應見同一結果。還原後編輯文字／移動／再刪除，不能因舊版本拒絕。另驗 relation-only delete／Undo、媒體 node（保留原 asset 引用，不重新上傳），與撤權／409 時 pending 保留、無自動重送。iPhone／Android／Web／Desktop UI、快捷鍵、VoiceOver、遮擋和視覺由產品負責人按 QA SOP 記錄，效能最後。
