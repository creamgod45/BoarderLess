import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { FastifyInstance } from 'fastify'
import postgres from 'postgres'
import { buildApp, type BuildAppConfig } from '../src/app.ts'
import { createSql } from '../src/db/client.ts'
import { migrate } from '../src/db/migrate.ts'
import type { JobContext } from '../src/jobs/context.ts'
import { JobRunner } from '../src/jobs/runner.ts'
import { MediaTools } from '../src/media/ffmpeg.ts'
import { Database } from '../src/repositories/index.ts'
import { LocalObjectStorage } from '../src/storage/local.storage.ts'
import type { ObjectStorage } from '../src/storage/types.ts'

const DEFAULT_TEST_URL = 'postgres://boarderless:boarderless@localhost:5433/boarderless_test'

/** 建立測試 DB（若不存在）並套用 migrations；DB 無法連線時回傳 null */
export async function setupTestDatabase(): Promise<postgres.Sql | null> {
  const TEST_URL = process.env.TEST_DATABASE_URL ?? DEFAULT_TEST_URL
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

export const hasMediaTools = ['ffmpeg', 'ffprobe'].every(
  (tool) => spawnSync(tool, ['-version'], { stdio: 'ignore' }).status === 0,
)

export const testConfig: BuildAppConfig = {
  logLevel: 'silent',
  corsOrigins: ['*'],
  media: {
    uploadUrlTtlSeconds: 900,
    downloadUrlTtlSeconds: 300,
    uploadGcGraceSeconds: 600,
    ffmpegPath: 'ffmpeg',
    ffprobePath: 'ffprobe',
    tmpDir: tmpdir(),
  },
  worker: { maxAttempts: 3 },
}

export const silentLog = { info() {}, warn() {}, error() {} }

export interface TestEnv {
  app: FastifyInstance
  storage: ObjectStorage
  db: Database
  jobContext: JobContext
  runner: JobRunner
  close(): Promise<void>
}

/** App + 獨立的本機 storage 目錄 + in-process worker（以 runner.drain() 同步執行 job） */
export async function createTestEnv(sql: postgres.Sql, options: { storage?: ObjectStorage } = {}): Promise<TestEnv> {
  const storageDir = await mkdtemp(join(tmpdir(), 'boarderless-storage-'))
  const storage = options.storage ?? new LocalObjectStorage(storageDir, 'http://storage.test', 'test-secret')
  const app = await buildApp({ config: testConfig, sql, storage, logger: false })
  const db = new Database(sql)
  const jobContext: JobContext = {
    db,
    storage,
    tools: new MediaTools('ffmpeg', 'ffprobe'),
    media: testConfig.media,
    jobMaxAttempts: testConfig.worker.maxAttempts,
    log: silentLog,
  }
  const runner = new JobRunner(jobContext, { concurrency: 1, pollIntervalMs: 50, leaseSeconds: 60, gcIntervalMs: 60_000 })
  return {
    app,
    storage,
    db,
    jobContext,
    runner,
    async close() {
      await app.close()
      await rm(storageDir, { recursive: true, force: true })
    },
  }
}

/** 舊測試相容：只需要 app 時使用 */
export async function createTestApp(sql: postgres.Sql): Promise<FastifyInstance> {
  return (await createTestEnv(sql)).app
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

export function envelope(
  baseVersion: number,
  operations: { kind: string; payload: unknown; expectedObjectVersions?: Record<string, number> }[],
  clientId = uuid(),
) {
  return {
    protocolVersion: 1,
    clientId,
    transactionId: uuid(),
    baseVersion,
    operations: operations.map((op, i) => ({ operationId: uuid(), clientSeq: i + 1, ...op })),
  }
}

// ---- media helpers ----

export interface MediaFixture {
  file: string
  mediaType: string
  byteSize: number
  checksum: string
  width: number | null
  height: number | null
  durationMs: number | null
  hasAudio: boolean
}

const FIXTURE_DIR = join(import.meta.dirname, 'fixtures/media')

export const mediaFixtures: MediaFixture[] = JSON.parse(
  await readFile(join(FIXTURE_DIR, 'manifest.json'), 'utf8'),
) as MediaFixture[]

export const fixture = (file: string) => mediaFixtures.find((f) => f.file === file)!
export const readFixture = (file: string) => readFile(join(FIXTURE_DIR, file))
export const sha256 = (bytes: Uint8Array) => `sha256:${createHash('sha256').update(bytes).digest('hex')}`

interface Directive {
  method: string
  url: string
  headers: Record<string, string>
}

/** 對本機 storage 的 signed URL 送出請求（不帶 API 身分 header） */
export async function transfer(app: FastifyInstance, directive: Directive, body?: Uint8Array, headers?: Record<string, string>) {
  const url = new URL(directive.url)
  const res = await app.inject({
    method: directive.method as 'PUT' | 'GET',
    url: `${url.pathname}${url.search}`,
    headers: { ...directive.headers, ...headers },
    ...(body ? { payload: Buffer.from(body) } : {}),
  })
  return { status: res.statusCode, bytes: res.rawPayload, headers: res.headers }
}
