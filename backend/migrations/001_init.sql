-- B0 基線：對應 docs/BACKEND_ARCHITECTURE.md §5
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name  TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  disabled_at   TIMESTAMPTZ
);

CREATE TABLE workspaces (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id         UUID NOT NULL REFERENCES users(id),
  title            TEXT NOT NULL,
  current_version  BIGINT NOT NULL DEFAULT 0,
  last_server_seq  BIGINT NOT NULL DEFAULT 0,
  schema_version   INTEGER NOT NULL DEFAULT 1,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ
);

CREATE TABLE workspace_members (
  workspace_id  UUID NOT NULL REFERENCES workspaces(id),
  user_id       UUID NOT NULL REFERENCES users(id),
  role          TEXT NOT NULL CHECK (role IN ('owner', 'editor', 'commenter', 'viewer')),
  joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at    TIMESTAMPTZ,
  PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX workspace_members_user_idx ON workspace_members (user_id) WHERE revoked_at IS NULL;

CREATE TABLE canvas_objects (
  workspace_id    UUID NOT NULL REFERENCES workspaces(id),
  object_id       UUID NOT NULL,
  object_type     TEXT NOT NULL,
  object_version  BIGINT NOT NULL DEFAULT 1,
  parent_id       UUID,
  z_index         INTEGER NOT NULL DEFAULT 0,
  locked          BOOLEAN NOT NULL DEFAULT false,
  transform       JSONB NOT NULL DEFAULT '{}'::jsonb,
  properties      JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by      UUID NOT NULL REFERENCES users(id),
  updated_by      UUID NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ,
  PRIMARY KEY (workspace_id, object_id)
);

CREATE TABLE relations (
  workspace_id      UUID NOT NULL REFERENCES workspaces(id),
  relation_id       UUID NOT NULL,
  relation_version  BIGINT NOT NULL DEFAULT 1,
  source_object_id  UUID NOT NULL,
  target_object_id  UUID NOT NULL,
  direction         TEXT NOT NULL CHECK (direction IN ('none', 'forward', 'backward', 'both')),
  intent            TEXT,
  label             TEXT,
  style             JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at        TIMESTAMPTZ,
  PRIMARY KEY (workspace_id, relation_id),
  -- 來源與目標必須屬於同一 Workspace
  FOREIGN KEY (workspace_id, source_object_id) REFERENCES canvas_objects (workspace_id, object_id),
  FOREIGN KEY (workspace_id, target_object_id) REFERENCES canvas_objects (workspace_id, object_id)
);
CREATE INDEX relations_source_idx ON relations (workspace_id, source_object_id) WHERE deleted_at IS NULL;
CREATE INDEX relations_target_idx ON relations (workspace_id, target_object_id) WHERE deleted_at IS NULL;

CREATE TABLE workspace_operations (
  workspace_id       UUID NOT NULL REFERENCES workspaces(id),
  server_seq         BIGINT NOT NULL,
  operation_id       UUID NOT NULL,
  transaction_id     UUID NOT NULL,
  actor_id           UUID NOT NULL REFERENCES users(id),
  client_id          UUID NOT NULL,
  client_seq         BIGINT NOT NULL,
  base_version       BIGINT NOT NULL,
  -- 此 operation 所屬 transaction commit 後的 Workspace version，用於 conflict 時補齊 baseVersion 之後的 operations
  workspace_version  BIGINT NOT NULL,
  operation_type     TEXT NOT NULL,
  payload            JSONB NOT NULL,
  schema_version     INTEGER NOT NULL,
  committed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, server_seq),
  UNIQUE (workspace_id, actor_id, operation_id)
);
CREATE INDEX workspace_operations_version_idx ON workspace_operations (workspace_id, workspace_version);

CREATE TABLE workspace_snapshots (
  workspace_id        UUID NOT NULL REFERENCES workspaces(id),
  through_server_seq  BIGINT NOT NULL,
  schema_version      INTEGER NOT NULL,
  snapshot            JSONB,
  storage_key         TEXT,
  checksum            TEXT NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, through_server_seq),
  CHECK (snapshot IS NOT NULL OR storage_key IS NOT NULL)
);

CREATE TABLE assets (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id  UUID NOT NULL REFERENCES workspaces(id),
  owner_id      UUID NOT NULL REFERENCES users(id),
  storage_key   TEXT NOT NULL UNIQUE,
  media_type    TEXT NOT NULL,
  byte_size     BIGINT NOT NULL CHECK (byte_size > 0),
  checksum      TEXT NOT NULL,
  width         INTEGER,
  height        INTEGER,
  duration_ms   INTEGER,
  status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'ready', 'rejected', 'missing')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);
CREATE INDEX assets_workspace_idx ON assets (workspace_id) WHERE deleted_at IS NULL;

CREATE TABLE transaction_outbox (
  id             BIGSERIAL PRIMARY KEY,
  workspace_id   UUID NOT NULL REFERENCES workspaces(id),
  server_seq     BIGINT NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ,
  attempt_count  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX transaction_outbox_unpublished_idx ON transaction_outbox (id) WHERE published_at IS NULL;
