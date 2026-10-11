-- ASSET-08：declared（asset_preparations.metadata，永不改寫）與 verified（worker 實際解析值）分離。
-- 只有 worker 成功解析內容後才寫入；即使因 declared_metadata_mismatch 被 rejected 也保留實際值供診斷。
ALTER TABLE asset_preparations
  ADD COLUMN verified JSONB,
  ADD COLUMN verified_at TIMESTAMPTZ;
