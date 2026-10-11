-- 素材引用的歷史還原（BACKEND_MISSING_IMPLEMENTATION_SPEC 批次 F）
-- 每次 MediaNode 的 assetId / thumbnailAssetId 實際改變時，於同一 transaction 保存完整 before / after 引用。
-- restore_media_references 只依此 provenance 還原，不接受 client 自行提交舊 assetId。
-- 本 migration 之前的變更沒有 provenance：明確回 provenance_not_found，不回推假資料。
CREATE TABLE media_reference_provenance (
  workspace_id               UUID NOT NULL REFERENCES workspaces (id),
  actor_id                   UUID NOT NULL REFERENCES users (id),
  transaction_id             UUID NOT NULL,
  operation_id               UUID NOT NULL,
  object_id                  UUID NOT NULL,
  before_asset_id            UUID NOT NULL,
  before_thumbnail_asset_id  UUID,
  after_asset_id             UUID NOT NULL,
  after_thumbnail_asset_id   UUID,
  created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, actor_id, operation_id)
);
