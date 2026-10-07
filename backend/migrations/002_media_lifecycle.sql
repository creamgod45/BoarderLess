-- Media v1：素材生命週期、衍生檔與 durable job（docs/BACKEND_MEDIA_API_SPEC.md §5、§6）

-- 內部 phase；公開 status 仍只有 pending / ready / rejected / missing
--   awaiting_upload  已簽發 upload ticket，尚未接受 complete（唯一可 abandon / GC 的 phase）
--   accepted         已接受 complete，等待 worker 驗證（公開 pending）
--   abandoned        client 以 DELETE 放棄（公開 pending + deleted_at）
--   expired          upload ticket 過期後由 GC 回收（公開 pending + deleted_at）
ALTER TABLE assets
  ADD COLUMN phase TEXT NOT NULL DEFAULT 'awaiting_upload'
    CHECK (phase IN ('awaiting_upload', 'accepted', 'ready', 'rejected', 'missing', 'abandoned', 'expired')),
  ADD COLUMN upload_key TEXT,
  ADD COLUMN upload_expires_at TIMESTAMPTZ,
  ADD COLUMN completion_accepted_at TIMESTAMPTZ,
  ADD COLUMN verified_at TIMESTAMPTZ,
  ADD COLUMN rejection_reason TEXT,
  ADD COLUMN thumbnail_asset_id UUID,
  ADD COLUMN source_asset_id UUID,
  ADD COLUMN derivative_type TEXT,
  ADD COLUMN derivative_version INTEGER,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 既有資料：舊 pending row 沒有 upload ticket，視為 15 分鐘後過期
UPDATE assets SET
  phase = CASE
    WHEN status <> 'pending' THEN status
    WHEN deleted_at IS NOT NULL THEN 'abandoned'
    ELSE 'awaiting_upload'
  END,
  upload_expires_at = created_at + interval '15 minutes',
  updated_at = created_at;

ALTER TABLE assets
  ADD CONSTRAINT assets_workspace_asset_key UNIQUE (workspace_id, id),
  -- 縮圖與衍生來源必須屬於同一 Workspace
  ADD CONSTRAINT assets_thumbnail_fk FOREIGN KEY (workspace_id, thumbnail_asset_id) REFERENCES assets (workspace_id, id),
  ADD CONSTRAINT assets_source_fk FOREIGN KEY (workspace_id, source_asset_id) REFERENCES assets (workspace_id, id),
  ADD CONSTRAINT assets_thumbnail_not_self CHECK (thumbnail_asset_id IS NULL OR thumbnail_asset_id <> id),
  ADD CONSTRAINT assets_derivative_fields CHECK (
    (source_asset_id IS NULL AND derivative_type IS NULL AND derivative_version IS NULL)
    OR (source_asset_id IS NOT NULL AND derivative_type IS NOT NULL AND derivative_version IS NOT NULL)
  ),
  ADD CONSTRAINT assets_status_matches_phase CHECK (
    (phase IN ('awaiting_upload', 'accepted', 'abandoned', 'expired') AND status = 'pending')
    OR phase = status
  ),
  ADD CONSTRAINT assets_positive_dimensions CHECK (
    (width IS NULL OR width > 0) AND (height IS NULL OR height > 0) AND (duration_ms IS NULL OR duration_ms >= 0)
  );

-- 衍生檔去重：asset ID + derivative type + version
CREATE UNIQUE INDEX assets_derivative_key ON assets (source_asset_id, derivative_type, derivative_version)
  WHERE source_asset_id IS NOT NULL;
CREATE INDEX assets_upload_gc_idx ON assets (upload_expires_at) WHERE phase = 'awaiting_upload';

-- Durable background job（PostgreSQL queue：lease + SKIP LOCKED，至少一次執行）
CREATE TABLE asset_jobs (
  id                BIGSERIAL PRIMARY KEY,
  -- 去重鍵，例如 verify_original:<assetId>、generate_thumbnail:<assetId>:v1
  job_key           TEXT NOT NULL UNIQUE,
  job_type          TEXT NOT NULL CHECK (job_type IN ('verify_original', 'generate_thumbnail', 'delete_object')),
  asset_id          UUID REFERENCES assets (id),
  payload           JSONB NOT NULL DEFAULT '{}'::jsonb,
  status            TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'running', 'done', 'dead')),
  attempts          INTEGER NOT NULL DEFAULT 0,
  max_attempts      INTEGER NOT NULL DEFAULT 5,
  run_after         TIMESTAMPTZ NOT NULL DEFAULT now(),
  lease_owner       TEXT,
  lease_expires_at  TIMESTAMPTZ,
  last_error        TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at      TIMESTAMPTZ
);
CREATE INDEX asset_jobs_runnable_idx ON asset_jobs (run_after, id) WHERE status IN ('queued', 'running');
CREATE INDEX asset_jobs_asset_idx ON asset_jobs (asset_id);
