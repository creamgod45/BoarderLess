-- 使用者層級快速方案簿（BACKEND_MISSING_IMPLEMENTATION_SPEC 批次 B、BACKEND_QUICK_SCHEME_API_SPEC）

-- 每位使用者一列：collectionRevision 在 create / update / delete 時 +1；mutation 鎖此列序列化（quota、revision）
CREATE TABLE quick_scheme_collections (
  owner_id             UUID PRIMARY KEY REFERENCES users (id),
  collection_revision  BIGINT NOT NULL DEFAULT 0,
  active_count         INTEGER NOT NULL DEFAULT 0 CHECK (active_count >= 0)
);

-- 方案資源。id 由 client 在 create 前產生（UUID），全域唯一；刪除保留 tombstone（deleted_at），不允許復活
CREATE TABLE quick_schemes (
  id                UUID PRIMARY KEY,
  owner_id          UUID NOT NULL REFERENCES users (id),
  resource_version  SMALLINT NOT NULL CHECK (resource_version = 2),
  name              TEXT NOT NULL,
  schema_version    INTEGER NOT NULL,
  payload           JSONB NOT NULL,
  asset_bindings    JSONB NOT NULL DEFAULT '[]'::jsonb,
  revision          BIGINT NOT NULL CHECK (revision >= 1),
  etag              TEXT NOT NULL,
  -- 最後一次變更時的 collectionRevision（keyset 分頁與快照判斷）
  collection_revision BIGINT NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at        TIMESTAMPTZ
);
CREATE INDEX quick_schemes_owner_idx ON quick_schemes (owner_id, id) WHERE deleted_at IS NULL;

-- 冪等 receipt：authenticated owner + Idempotency-Key；保存 fingerprint（method + 資源 + precondition + body）與完成回應
CREATE TABLE quick_scheme_receipts (
  owner_id          UUID NOT NULL REFERENCES users (id),
  idempotency_key   UUID NOT NULL,
  method            TEXT NOT NULL,
  scheme_id         UUID NOT NULL,
  fingerprint       TEXT NOT NULL,
  response_status   INTEGER NOT NULL,
  response_body     JSONB,
  response_etag     TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at        TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (owner_id, idempotency_key)
);
CREATE INDEX quick_scheme_receipts_expiry_idx ON quick_scheme_receipts (expires_at);

-- 素材保留 pin：方案引用的原檔 / 縮圖。只是保留，不是授權；刪除方案或移除 binding 只移除自己的 pin
CREATE TABLE quick_scheme_asset_pins (
  scheme_id            UUID NOT NULL REFERENCES quick_schemes (id),
  asset_id             UUID NOT NULL REFERENCES assets (id),
  source_workspace_id  UUID NOT NULL REFERENCES workspaces (id),
  kind                 TEXT NOT NULL CHECK (kind IN ('original', 'thumbnail')),
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (scheme_id, asset_id, kind)
);
CREATE INDEX quick_scheme_asset_pins_asset_idx ON quick_scheme_asset_pins (asset_id);
