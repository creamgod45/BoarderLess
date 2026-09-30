import type { FastifyInstance } from 'fastify'
import postgres from 'postgres'
import { buildApp } from '../src/app.ts'
import { createSql } from '../src/db/client.ts'
import { migrate } from '../src/db/migrate.ts'

const TEST_URL = process.env.TEST_DATABASE_URL!

/** 建立測試 DB（若不存在）並套用 migrations；DB 無法連線時回傳 null */
export async function setupTestDatabase(): Promise<postgres.Sql | null> {
  const url = new URL(TEST_URL)
  const dbName = url.pathname.slice(1)
  const adminUrl = new URL(TEST_URL)
  adminUrl.pathname = '/postgres'

  const admin = postgres(adminUrl.toString(), { max: 1, onnotice: () => {}, connect_timeout: 3 })
  try {
    const exists = await admin`SELECT 1 FROM pg_database WHERE datname = ${dbName}`
    if (exists.length === 0) await admin.unsafe(`CREATE DATABASE "${dbName}"`)
  } catch {
    return null
  } finally {
    await admin.end()
  }

  const sql = createSql(TEST_URL, 10)
  await migrate(sql, () => {})
  return sql
}

export async function createTestApp(sql: postgres.Sql): Promise<FastifyInstance> {
  return buildApp({ config: { logLevel: 'silent' }, sql, logger: false })
}

export const uuid = () => crypto.randomUUID()

/** 以 app.inject 呼叫 API 的小工具 */
export function client(app: FastifyInstance, userId?: string) {
  const request = async (method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE', url: string, body?: unknown) => {
    const res = await app.inject({
      method,
      url: `/api/v1${url}`,
      headers: userId ? { 'x-user-id': userId } : {},
      ...(body === undefined ? {} : { payload: body as object }),
    })
    return { status: res.statusCode, body: res.body ? (JSON.parse(res.body) as any) : null }
  }
  return {
    get: (url: string) => request('GET', url),
    post: (url: string, body?: unknown) => request('POST', url, body ?? {}),
    put: (url: string, body?: unknown) => request('PUT', url, body ?? {}),
    patch: (url: string, body?: unknown) => request('PATCH', url, body ?? {}),
    delete: (url: string) => request('DELETE', url),
  }
}

export async function createUser(app: FastifyInstance, displayName = 'Tester'): Promise<string> {
  const res = await client(app).post('/users', { displayName })
  return res.body.id
}

export function envelope(baseVersion: number, operations: { kind: string; payload: unknown; expectedObjectVersions?: Record<string, number> }[], clientId = uuid()) {
  return {
    protocolVersion: 1,
    clientId,
    transactionId: uuid(),
    baseVersion,
    operations: operations.map((op, i) => ({ operationId: uuid(), clientSeq: i + 1, ...op })),
  }
}
