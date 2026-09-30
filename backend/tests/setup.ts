// bun test preload：測試使用獨立資料庫，避免污染開發資料
process.env.TEST_DATABASE_URL ??= 'postgres://boarderless:boarderless@localhost:5433/boarderless_test'
