-- 即時協作（docs/BACKEND_MISSING_IMPLEMENTATION_SPEC.md 批次 A、BACKEND_REALTIME_API_SPEC.md）

-- metadataRevision：workspace title / membership / 有效角色的 durable 版本，與 canvas workspaceVersion / serverSeq 分開
ALTER TABLE workspaces ADD COLUMN metadata_revision BIGINT NOT NULL DEFAULT 1;

-- 一次性 join ticket：只存 SHA-256，原子 consume；綁定 user + client + workspace + protocol
CREATE TABLE realtime_tickets (
  ticket_hash       TEXT PRIMARY KEY CHECK (ticket_hash ~ '^[0-9a-f]{64}$'),
  workspace_id      UUID NOT NULL REFERENCES workspaces (id),
  user_id           UUID NOT NULL REFERENCES users (id),
  client_id         UUID NOT NULL,
  protocol_version  INTEGER NOT NULL,
  expires_at        TIMESTAMPTZ NOT NULL,
  consumed_at       TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX realtime_tickets_expiry_idx ON realtime_tickets (expires_at);

-- outbox publisher 依 id 順序 claim；published 後由各 gateway 從 operation log 讀完整 transaction
CREATE INDEX transaction_outbox_workspace_idx ON transaction_outbox (workspace_id, id);

-- Presence：暫態，不進畫布 snapshot / Undo。UNLOGGED：DB crash 後清空，room 以新 roomEpoch 重建。
CREATE UNLOGGED TABLE presence_rooms (
  workspace_id   UUID PRIMARY KEY,
  room_epoch     UUID NOT NULL,
  room_revision  BIGINT NOT NULL DEFAULT 0
);

CREATE UNLOGGED TABLE presence_peers (
  connection_id        UUID PRIMARY KEY,
  workspace_id         UUID NOT NULL,
  user_id              UUID NOT NULL,
  client_id            UUID NOT NULL,
  display_name         TEXT NOT NULL,
  role                 TEXT NOT NULL,
  cursor               JSONB,
  selected_object_ids  UUID[] NOT NULL DEFAULT '{}',
  presence_seq         BIGINT NOT NULL DEFAULT 0,
  gateway_id           UUID NOT NULL,
  expires_at           TIMESTAMPTZ NOT NULL,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX presence_peers_workspace_idx ON presence_peers (workspace_id);
CREATE INDEX presence_peers_expiry_idx ON presence_peers (expires_at);
