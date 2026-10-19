-- 共用背景（BACKEND_MISSING_IMPLEMENTATION_SPEC 批次 C、BACKEND_CANVAS_STYLE_API_SPEC）
-- canvasStyle / canvasStyleVersion 屬於 workspace projection；既有 Workspace 一律採穩定 default / 0，
-- 不從任何 client 的裝置偏好搬移。canvasStyleVersion 與 workspaceVersion / serverSeq 分開、單調增加。
ALTER TABLE workspaces
  ADD COLUMN canvas_style JSONB NOT NULL
    DEFAULT '{"schemaVersion": 1, "backgroundToken": "default", "gridStyle": {"pattern": "lines", "colorToken": "auto"}}'::jsonb,
  ADD COLUMN canvas_style_version BIGINT NOT NULL DEFAULT 0
    CHECK (canvas_style_version >= 0 AND canvas_style_version <= 9007199254740991);
