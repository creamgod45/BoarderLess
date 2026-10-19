-- Asset preparation v2：固定 prepare 身份、upload ticket lease（docs/BACKEND_ASSET_PREPARATION_V2_SPEC.md）

-- duration 上限為 JSON safe integer（9007199254740991），超過 INTEGER；尺寸維持 INTEGER（APP 為 32-bit Int）
ALTER TABLE assets ALTER COLUMN duration_ms TYPE BIGINT;

-- 1 = Media v1（POST /assets 直接簽票）；2 = 經由 preparation v2 建立，只能走 lease complete
ALTER TABLE assets
  ADD COLUMN preparation_version SMALLINT NOT NULL DEFAULT 1 CHECK (preparation_version IN (1, 2));

-- 原 prepare receipt：authenticated user + workspace + requestId 唯一，永久保留（不設 TTL，不 silent eviction）。
-- abandoned / rejected / missing 時仍保留，作為 tombstone identity；同 key 永遠回同一 asset。
CREATE TABLE asset_preparations (
  workspace_id    UUID NOT NULL REFERENCES workspaces (id),
  actor_id        UUID NOT NULL REFERENCES users (id),
  request_id      UUID NOT NULL,
  asset_id        UUID NOT NULL UNIQUE,
  -- canonical metadata JSON 的 SHA-256 lowercase hex（伺服器自行計算）
  request_digest  TEXT NOT NULL CHECK (request_digest ~ '^[0-9a-f]{64}$'),
  metadata        JSONB NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, actor_id, request_id),
  -- 先寫 receipt 再寫 asset（同 transaction），讓並行的同 key 請求在 receipt 主鍵上序列化
  FOREIGN KEY (workspace_id, asset_id) REFERENCES assets (workspace_id, id) DEFERRABLE INITIALLY DEFERRED
);

-- Upload ticket lease：每張票券固定 generation 與獨立 staging key，complete 只接受當前 generation。
-- 簽名 URL / headers 不落地，只保存 lease identity。
CREATE TABLE asset_upload_leases (
  lease_id           UUID PRIMARY KEY,
  asset_id           UUID NOT NULL REFERENCES assets (id),
  -- APP 先 durable 保存的票券請求 ID；同 ID 冪等回同一 lease
  ticket_request_id  UUID NOT NULL,
  generation         INTEGER NOT NULL CHECK (generation > 0),
  upload_key         TEXT NOT NULL UNIQUE,
  expires_at         TIMESTAMPTZ NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- GC 已回收 staging object；之後不可再用此 lease complete
  collected_at       TIMESTAMPTZ,
  UNIQUE (asset_id, ticket_request_id),
  UNIQUE (asset_id, generation)
);
CREATE INDEX asset_upload_leases_gc_idx ON asset_upload_leases (expires_at) WHERE collected_at IS NULL;

-- 被接受 complete 的 lease（accepted 之後固定，verify job 只讀這個 lease 的 staging key）
ALTER TABLE assets
  ADD COLUMN upload_lease_id UUID REFERENCES asset_upload_leases (lease_id);
