import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import type postgres from 'postgres'
import { createSql } from './client.ts'

const MIGRATIONS_DIR = join(import.meta.dirname, '../../migrations')

/** 依檔名順序套用尚未執行的 SQL migration；全部在同一 transaction 內，失敗則全部回滾。 */
export async function migrate(sql: postgres.Sql, log: (msg: string) => void = console.log): Promise<string[]> {
  const files = (await readdir(MIGRATIONS_DIR)).filter((f) => f.endsWith('.sql')).sort()

  return sql.begin(async (tx) => {
    // 避免多個 process 同時 migrate
    await tx`SELECT pg_advisory_xact_lock(hashtext('boarderless:migrations'))`
    await tx`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        name TEXT PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )
    `
    const applied = new Set((await tx<{ name: string }[]>`SELECT name FROM schema_migrations`).map((r) => r.name))
    const newlyApplied: string[] = []

    for (const file of files) {
      if (applied.has(file)) continue
      const content = await readFile(join(MIGRATIONS_DIR, file), 'utf8')
      await tx.unsafe(content)
      await tx`INSERT INTO schema_migrations (name) VALUES (${file})`
      log(`applied migration ${file}`)
      newlyApplied.push(file)
    }
    return newlyApplied
  })
}

if (import.meta.main) {
  const url = process.env.DATABASE_URL
  if (!url) throw new Error('Missing DATABASE_URL')
  const sql = createSql(url, 1)
  try {
    const applied = await migrate(sql)
    if (applied.length === 0) console.log('database is up to date')
  } finally {
    await sql.end()
  }
}
