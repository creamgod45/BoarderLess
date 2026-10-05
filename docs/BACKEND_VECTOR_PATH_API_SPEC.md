# 自有向量路徑與鋼筆 Node 合約

狀態：Proposal，2026-10-04。不是已交付 API，不指定新 endpoint；不得因目前 properties 接受任意 JSON 就宣稱已支援。APP 已開始 domain／本機編輯核心，完整畫布／保存／同步未完成。

## 1. 內容範圍與格式

- 使用 BoarderLess 自有 commands，不接收 SVG markup、script、URL、平台 Path 或二進位繪圖資料。
- path schemaVersion=1；viewBox 是正尺寸的本機座標範圍，width／height 各 0.001..100000。點座標須有限、絕對值最多 1000000；允許控制點伸出 viewBox，不可默默 clamp 而改變曲線。
- commands 包含 move(point)、line(point)、quadratic(control, point)、cubic(control1, control2, point)、close。APP 已採同名短 discriminator，不用 Kotlin 類別名稱；正式跨語言 fixtures 仍需核定。未知 command／版本／欄位拒絕，不忽略。
- 每條 path 2..4096 commands、最多 128 subpaths。第一段須 move，每個 subpath 至少一段可繪 segment；close 僅用於已有 segment 的 open contour，close 後下一個 command 只能 move。禁止空 subpath／連續 move／重複 close。
- style 包含 nullable fillColorToken／strokeColorToken（至少一者存在）、strokeWidth、fillRule（NonZero／EvenOdd）。token 僅 `[A-Za-z0-9#_-]{1,64}`，不是任意 CSS／URL；strokeWidth 是有限的 viewBox 本機單位，0 < width <= 128。APP 輸出 token 最終命名與正式 fixtures 一併核定。
- APP stroke renderer／hit helper 採 round caps／round joins；open contour 的 fill 隱式閉合，stroke 不隱式封口。若正式 schema 增加其他 cap／join，需版本化且同步調整 renderer／hit。幾何 approximation tolerance 為本機 viewBox 單位，非共享 viewport 偏好；目前 ray helper 找 contour／centreline，不代表 exact stroke silhouette。
- authoring PenAnchor 保存 point／nullable incoming／outgoing 控制點，最多 1024 anchors、單 contour；closed 至少 2 anchors（兩段曲線可形成 lens／oval，不強迫三角形）。轉 path 時無 handles 用 line、單 handle 用 quadratic、雙 handles 用 cubic；closing segment 同樣保留最後 outgoing 與第一 incoming。這是 APP 編輯資料，不得冒充 Workspace committed operation。

## 2. Workspace 合約需求

- 明確選擇新的自訂向量 object kind／capability，或正式版本化既有 Node 擴充；尚未選定前 APP 不猜 type、不把未知 properties 寫到正式畫布。舊 client 必須 fail-closed／提示升級，不能把向量默默還原為矩形或丟掉 custom path。
- projection／create／path update／style update／delete／restore 都驗證完整型別化資料，限制單 Node、整個 transaction 與 Workspace 的總 payload；server 不保存平台 Path、私密暫存位置或任意可執行內容。
- create／update 沿用同 Workspace ACL、版本衝突、locked、群組階層與 transaction 原子性；path／style 更新支援明確 inverse 與 monotonic object version。刪除 Undo／Redo 必須交付 BAI-011 restore semantics，不以同 ID create 代替。
- relation endpoint 綁穩定 Node ID；幾何外框／ray endpoint 使用實際路徑，不把點擊與連線都當方形。旋轉／縮放／自由移動採既有 world transform，不能重現旋轉後拖曳偏移。

## 3. 保存、複製與同步

- 自訂 path 需走正式 clipboard schema、duplicate、新 ID、群組／快速方案 payload round-trip 及 schema migration；不覆蓋舊 resource、不跳過 unknown kind gate。
- 快速方案 user-level 同步沿用正式 revision／ETag 合約；本機 PenPathHistory 的 100 步 Undo 上限不是 Workspace history、journal durability 或協作同步。
- 冷啟、雙 client、server projection／operation replay 皆需相同 path／style；unknown kind／版本／command 拒絕整體操作，不部分採用。

## 4. 必要交付證據

至少含 line／quadratic／cubic、closed／open／多 subpaths／孔洞填色、stroke、控制點超出 viewBox、有限座標及各上限、非法 command order／未知 schema、ACL／locked／expected version、完整交易 rollback、relation／group、clipboard／方案、delete restore／Undo／Redo、舊 client 的 fixtures。

APP 尚待鋼筆 gesture／Anchor handle UI、繪製／命中／連線幾何共用、Node 操作／模型／projection、clipboard／方案及 capability 接線。視覺、觸控、遮擋與 VoiceOver 由使用者依 QA SOP 簽核；效能最後。domain tests 和 Compose command adapter 不算完整鋼筆交付。
