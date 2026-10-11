-- 時序圖 metadata（BACKEND_MISSING_IMPLEMENTATION_SPEC 批次 E、DIAGRAM_RELATION_V2 §6）
-- sequenceDiagrams：boarderless.sequence-canvas.v1 的 canonical 陣列（containerId 唯一）；version 單調、與 workspaceVersion 分開
ALTER TABLE workspaces
  ADD COLUMN sequence_diagrams JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN sequence_diagrams_version BIGINT NOT NULL DEFAULT 0
    CHECK (sequence_diagrams_version >= 0 AND sequence_diagrams_version <= 9007199254740991);
