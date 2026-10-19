/**
 * 驗收 CORE-08 / ASSET-03：真 PostgreSQL 重啟（不是新連線池 / 新 app 模擬）。
 *
 * 在一次性的 postgres:18.6-alpine 容器（隨機 port，不碰開發 DB）上建立資料，
 * 依序執行「正常重啟（docker restart）」與「程序被殺（docker kill → start）」，
 * 每次都以同一個 app / 連線池等待服務恢復，再核對 receipt、fence、tombstone、
 * preparation identity、projection 與 durable job。
 *
 * 用法：bun run acceptance:db-restart
 * 結果：acceptance/results/<時間>-db-restart.json（不含連線字串、簽名 URL 或 token）
 */
import { execFileSync } from 'node:child_process'
import { createServer } from 'node:net'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import postgres from 'postgres'
import { createSql } from '../../src/db/client.ts'
import { migrate } from '../../src/db/migrate.ts'
import { client, createTestEnv, createUser, envelope, readFixture, sha256, transfer, uuid } from '../../tests/helpers.ts'

const IMAGE = process.env.ACCEPTANCE_PG_IMAGE ?? 'postgres:18.6-alpine'
const CONTAINER = `boarderless-acceptance-pg-${process.pid}`
const RESULTS_DIR = join(import.meta.dirname, '../../acceptance/results')

const docker = (...args: string[]) => execFileSync('docker', args, { encoding: 'utf8' }).trim()
const git = (...args: string[]) => {
  try {
    return execFileSync('git', args, { encoding: 'utf8', cwd: join(import.meta.dirname, '../..') }).trim()
  } catch {
    return 'unknown'
  }
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/** 固定 host port：docker 的隨機 port 在 restart 後會改變，重連會連到舊 port */
const freePort = () =>
  new Promise<number>((resolve, reject) => {
    const server = createServer()
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address() as { port: number }
      server.close(() => resolve(port))
    })
  })

interface Check {
  name: string
  pass: boolean
  detail?: unknown
}

async function waitFor<T>(label: string, fn: () => Promise<T | null>, timeoutMs = 60_000): Promise<{ value: T; ms: number }> {
  const started = Date.now()
  let lastError: unknown
  while (Date.now() - started < timeoutMs) {
    try {
      const value = await fn()
      if (value !== null) return { value, ms: Date.now() - started }
    } catch (error) {
      lastError = error
    }
    await sleep(250)
  }
  throw new Error(`${label} did not recover within ${timeoutMs}ms`, { cause: lastError })
}

const startedAt = new Date()
const result = {
  cases: ['CORE-08', 'ASSET-03'],
  kind: '真 DB 重啟（一次性容器；同一 app 與連線池）',
  startedAt: startedAt.toISOString(),
  finishedAt: '',
  environment: {
    backendCommit: git('rev-parse', 'HEAD'),
    uncommittedChanges: git('status', '--porcelain', '--', '.').split('\n').filter(Boolean).length,
    postgresImage: IMAGE,
    postgresVersion: '',
    migrations: [] as string[],
    bun: Bun.version,
  },
  rounds: [] as { mode: string; recoveryMs: number; checks: Check[] }[],
  final: [] as Check[],
  status: 'failed' as 'passed' | 'failed',
  error: undefined as string | undefined,
}

let sql: postgres.Sql | undefined
let env: Awaited<ReturnType<typeof createTestEnv>> | undefined
try {
  const port = await freePort()
  docker('run', '-d', '--name', CONTAINER, '-e', 'POSTGRES_USER=acceptance', '-e', 'POSTGRES_PASSWORD=acceptance',
    '-e', 'POSTGRES_DB=acceptance', '-p', `127.0.0.1:${port}:5432`, IMAGE)
  const url = `postgres://acceptance:acceptance@127.0.0.1:${port}/acceptance`
  const probe = async () => {
    const s = postgres(url, { max: 1, connect_timeout: 2, onnotice: () => {} })
    try {
      return (await s`SELECT version() AS v`)[0]!.v as string
    } finally {
      await s.end()
    }
  }
  result.environment.postgresVersion = (await waitFor('postgres startup', probe)).value
  sql = createSql(url, 5)
  result.environment.migrations = await migrate(sql, () => {})
  env = await createTestEnv(sql)
  const api = client(env.app, await createUser(env.app, 'Acceptance'))
  const ws = (await api.post('/workspaces', { title: 'DB restart acceptance' })).body.id as string
  const opsPath = `/workspaces/${ws}/operations`
  const version = async () => (await api.get(`/workspaces/${ws}`)).body.currentVersion as number

  // ---- 重啟前資料 ----
  const [a, b, rel] = [uuid(), uuid(), uuid()]
  const text = (id: string) => ({ kind: 'create_object', payload: { objectId: id, objectType: 'text' } })
  const tx1 = envelope(await version(), [
    text(a),
    text(b),
    { kind: 'create_relation', payload: { relationId: rel, sourceObjectId: a, targetObjectId: b, direction: 'forward', label: null, style: {} } },
  ])
  if ((await api.post(opsPath, tx1)).body.status !== 'accepted') throw new Error('tx1 not accepted')
  const tx2 = envelope(await version(), [{ kind: 'delete_objects', payload: { objectIds: [a] } }])
  if ((await api.post(opsPath, tx2)).body.status !== 'accepted') throw new Error('tx2 not accepted')
  const late = envelope(await version(), [text(uuid())])
  await api.post(`${opsPath}/fences`, { transactionIds: [late.transactionId] })

  const png = await readFixture('image.png')
  const meta = { byteSize: png.length, checksum: sha256(png), durationMs: null, height: null, mediaType: 'image/png', width: null }
  const prepBody = (requestId: string) => ({ schema: 'boarderless.asset-prepare.v1', requestId, metadata: meta })
  const prep = `/workspaces/${ws}/assets/preparations`
  const [accepted, reserved] = [uuid(), uuid()]
  await api.post(prep, prepBody(accepted))
  const ticket = await api.post(`${prep}/${accepted}/upload-tickets`, { ticketRequestId: uuid() })
  await transfer(env.app, ticket.body.upload, png)
  await api.post(`${prep}/${accepted}/complete`, { requestId: accepted, leaseId: ticket.body.upload.leaseId, byteSize: meta.byteSize, checksum: meta.checksum })
  await api.post(prep, prepBody(reserved))

  const snapshot = async () => ({
    state: (await api.get(`/workspaces/${ws}/state`)).body,
    receipts: (await api.post(`${opsPath}/receipts`, { transactionIds: [tx1.transactionId, tx2.transactionId] })).body.receipts,
    operations: (await api.get(`${opsPath}?afterSeq=0&limit=1000`)).body,
    accepted: (await api.get(`${prep}/${accepted}`)).body,
    reserved: (await api.get(`${prep}/${reserved}`)).body,
  })
  const before = await snapshot()
  if (before.accepted.phase !== 'completionAccepted') throw new Error(`unexpected phase ${before.accepted.phase}`)

  // ---- 兩種重啟 ----
  for (const mode of ['restart', 'kill'] as const) {
    if (mode === 'restart') docker('restart', CONTAINER)
    else {
      docker('kill', CONTAINER)
      docker('start', CONTAINER)
    }
    // 同一個 app / 連線池：等到 API 可再讀到 Workspace
    const { ms } = await waitFor(`api after ${mode}`, async () =>
      (await api.get(`/workspaces/${ws}`)).status === 200 ? true : null,
    )
    const after = await snapshot()
    const same = (x: unknown, y: unknown) => JSON.stringify(x) === JSON.stringify(y)
    const lateRes = await api.post(opsPath, late)
    const lateReceipt = await api.post(`${opsPath}/receipts`, { transactionIds: [late.transactionId] })
    const retryReserved = await api.post(prep, prepBody(reserved))
    const recreate = await api.post(opsPath, envelope(await version(), [text(a)]))
    const checks: Check[] = [
      { name: 'projection（state）與重啟前一致', pass: same(after.state, before.state) },
      { name: '原提交 receipt（tx1、tx2）完整保留', pass: same(after.receipts, before.receipts) && after.receipts.length === 2 },
      { name: 'operation log replay 一致', pass: same(after.operations, before.operations) },
      { name: 'fence 保留：晚到原請求 409 transaction_fenced', pass: lateRes.status === 409 && lateRes.body.error?.code === 'transaction_fenced', detail: lateRes.status },
      { name: 'fence receipt 查詢為 fenced', pass: lateReceipt.body.lookups?.[0]?.status === 'fenced' },
      { name: 'tombstone 保留：同 ID create 回 422 並提示 restore_objects', pass: recreate.status === 422 && recreate.body.error?.details?.restoreWith === 'restore_objects' },
      { name: 'preparation identity（accepted）不變', pass: same(after.accepted, before.accepted) },
      { name: 'preparation reservation（prepared）不變', pass: same(after.reserved, before.reserved) },
      { name: '同 requestId 重送回 200 同一 asset', pass: retryReserved.status === 200 && retryReserved.body.asset.id === before.reserved.asset.id },
    ]
    result.rounds.push({ mode, recoveryMs: ms, checks })
  }

  // durable verify job 跨兩次重啟仍在，worker 可完成
  await env.runner.drain()
  const final = (await api.get(`${prep}/${accepted}`)).body
  result.final.push({ name: 'complete 時寫入的 verify job 重啟後仍執行：phase=ready', pass: final.phase === 'ready', detail: final.phase })

  const allChecks = [...result.rounds.flatMap((r) => r.checks), ...result.final]
  result.status = allChecks.every((c) => c.pass) ? 'passed' : 'failed'
} catch (error) {
  result.error = error instanceof Error ? `${error.message}${error.cause ? ` (${String(error.cause)})` : ''}` : String(error)
} finally {
  await env?.close().catch(() => {})
  await sql?.end({ timeout: 1 }).catch(() => {})
  try {
    docker('rm', '-f', CONTAINER)
  } catch {}
  result.finishedAt = new Date().toISOString()
  await mkdir(RESULTS_DIR, { recursive: true })
  const file = join(RESULTS_DIR, `${startedAt.toISOString().replace(/[:.]/g, '-')}-db-restart.json`)
  await writeFile(file, `${JSON.stringify(result, null, 2)}\n`)
  for (const round of result.rounds) {
    console.log(`[${round.mode}] recovered in ${round.recoveryMs}ms`)
    for (const c of round.checks) console.log(`  ${c.pass ? '✓' : '✗'} ${c.name}`)
  }
  for (const c of result.final) console.log(`${c.pass ? '✓' : '✗'} ${c.name}`)
  console.log(`${result.status.toUpperCase()}${result.error ? `: ${result.error}` : ''} → ${file}`)
  process.exitCode = result.status === 'passed' ? 0 : 1
}
