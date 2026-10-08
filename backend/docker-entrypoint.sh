#!/bin/sh
set -e

# 啟動前套用 migration（advisory lock 保護，多個 replica 同時啟動也安全）
if [ "${MIGRATE_ON_START:-false}" = "true" ]; then
  bun src/db/migrate.ts
fi

exec "$@"
