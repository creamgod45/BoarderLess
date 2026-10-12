# Desktop AI 安全除錯記錄

2026-10-13 本輪再次唯讀核對現存 log，最新仍 2026-10-09T02:29:52.408199Z OpenAiChat Completed；沒有新 AI 失敗事件。理解驗證已補明確讀取工具對話使用的非機密連線設定，開始即清當前金鑰與授權，不查 vault、不送 HTTP、不寫新 AI attempt。Desktop 設定讀取有界 64KiB 並拒非法 UTF-8／symlink／非 regular source；不可讀與缺檔仍同回 null，所以提示保留此不確定性。此為源碼更新，未編譯／驗收。

2026-10-13 授權規則更新（源碼已接、未驗收）：所有工具輪次都須審閱並明確同意該次 exact request，不再提供後續 autoApprove。舊紀錄若提及此選項僅代表歷史行為；沒有為這次改動送出 AI 請求或取得新實測證據。

2026-10-13 源碼補充（尚未編譯／驗收）：理解 probe 以嚴格 UTF-8 code point 累積，上限 128 KiB；跨 delta 的 UTF-16 配對可暫存，未配對／非法配對分類 ResponseFormat，超量 OutputLimit。手動貼回覆同樣嚴格限制，拒絕時原文不變且有提示。安全 log 不記錄這些字元或回覆內容。

本日唯讀檢查現存 log，最新事件仍是 2026-10-09T02:29:52.408199Z OpenAiChat Completed（該 attempt HTTP 200）；沒有新的失敗可歸因。此紀錄不證明最新 APP、Claude 或工具草稿 Apply／Undo 已完成驗收，未為診斷另發 AI 請求。

Desktop 自動寫入 `~/.boarderless/logs/ai-probe.log`，不需要複製視窗內容。檔案為 UTF-8、僅使用者可讀寫；目前檔案上限 256 KiB，輪替保留一份 `.1`。寫入失敗不影響 AI 任務。

送出摘要包含 endpoint 類別、已知模型類別、請求位元組數、messages/tools 數量、stream、reasoning_effort、max_completion_tokens/max_tokens、temperature 與參數名稱類別。未送出的選填數值顯示 Absent；自訂模型、參數名稱和 endpoint 不原樣保存。

回應摘要包含 HTTP 狀態、Content-Type 類別、從準備送出到收到回應標頭的毫秒數、錯誤回應位元組數、解析狀態，以及供應商 type/code/param 與拒絕原因的有限分類。同一次嘗試以本機 attempt UUID 關聯。x-request-id 只有單一且符合 `req_` 加 32 個小寫十六進位字元時才保存；其他格式省略，不表示供應商沒有 request ID。

不寫入 Authorization、API Key、完整 URL、原始 request/response、提示詞、畫布文字、工具參數內容或供應商 error.message。HTTP 錯誤 JSON 僅讀取最多 16 KiB 加一個偵測位元組，逾時、無效 UTF-8、空回應或非 JSON 都保留原 HTTP 失敗分類。除錯不觸發額外 API 呼叫、重試或略過每次請求授權。

理解驗證與圖形草稿的 OpenAI Chat 設定現在可明確選擇輸出上限參數：預設 max_completion_tokens；僅在路由／模型要求舊格式時選 max_tokens。每份 body 最多送出其中一個，上限留空便兩個都不送。Claude Messages 保持 max_tokens，不受 Chat 選擇影響。設定只留在本次視窗，任務開始時凍結並沿用於後續每輪，仍須分別同意每次送出；理解驗證改設定會清除先前授權。不會因 HTTP 400 自動切換欄位或重試，安全 log 原有摘要會反映實際送出的欄位。

欄位定義依 [OpenAI 官方 Chat Completions API Reference](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)：max_tokens 為 deprecated 的舊欄位，不能把手動相容選項當成所有模型都支持。沒有藉此改選模型或呼叫供應商 API。

2026-10-11 唯讀核對本機紀錄：較新的 2026-10-09T02:29:52Z 已有 OpenAiChat／Bearer／ChatCompletions／tools=9／reasoning=None／temperature=Absent 的 HTTP 200、SSE、Completed。10/8 的 HTTP 400 是歷史失敗，不再當成最新狀態。這只證明該次請求完成，不能代簽 Claude、所有設定路由、工具草稿套用／Undo 或未附時間的 401；本輪未代送 AI 請求。
