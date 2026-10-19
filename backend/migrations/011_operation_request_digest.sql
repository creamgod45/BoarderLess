-- 冪等重送必須內容相同：保存原 envelope 的 request digest（SHA-256 hex）。
-- 同 transaction / operation ID 但內容不同 → 409 idempotency_mismatch，不回 duplicate 冒充原請求成功。
-- 本 migration 之前提交的 operation 沒有 digest：無法證明內容相同，重送一律視為不一致（不回推）。
ALTER TABLE workspace_operations ADD COLUMN request_digest TEXT CHECK (request_digest IS NULL OR request_digest ~ '^[0-9a-f]{64}$');
