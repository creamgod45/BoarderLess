-- BAI-007 / BAI-012：parent 階層約束、原提交 receipt 查詢與 fence

-- parent 必須屬於同一 Workspace。NOT VALID：只約束新寫入的 row，不重驗既有資料；
-- parent 為 active group、無循環等規則由應用層在 Workspace row lock 內檢查（FK 擋不住 soft-delete 的 parent）。
ALTER TABLE canvas_objects
  ADD CONSTRAINT canvas_objects_parent_fk
  FOREIGN KEY (workspace_id, parent_id) REFERENCES canvas_objects (workspace_id, object_id) NOT VALID;
CREATE INDEX canvas_objects_parent_idx ON canvas_objects (workspace_id, parent_id) WHERE parent_id IS NOT NULL;

-- 依原 actor + transaction 查 receipt
CREATE INDEX workspace_operations_receipt_idx ON workspace_operations (workspace_id, actor_id, transaction_id);

-- Fence：保證被 fence 的原請求之後永遠不會 commit（settlement，BAI-012）
CREATE TABLE transaction_fences (
  workspace_id  UUID NOT NULL REFERENCES workspaces (id),
  actor_id      UUID NOT NULL REFERENCES users (id),
  fence_kind    TEXT NOT NULL CHECK (fence_kind IN ('transaction', 'operation')),
  fenced_id     UUID NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, actor_id, fence_kind, fenced_id)
);
