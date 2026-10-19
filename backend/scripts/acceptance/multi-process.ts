/**
 * 驗收 CORE-04 / RT-04（預檢）：兩個真實 API process（`bun src/server.ts`）共用同一個 PostgreSQL。
 *
 * - CORE-04：兩個 process 同時對同一 Workspace 提交，serverSeq / workspaceVersion 連續、無重複、無遺失。
 * - RT-04：client 分別連到兩個 process；跨 process fan-out；kill -9 其中一個 process 後，
 *   client 以已套用 checkpoint 重新 join 另一個 process，補齊期間的完整 transaction，無遺失 / 無重複。
 *
 * 使用獨立的資料庫（boarderless_acceptance_mp，執行前重建、結束後刪除），不碰開發 DB。
 * 用法：bun run acceptance:multi-process
 */
import { execFileSync, spawn, type ChildProcess } from 'node:child_process'
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import postgres from 'postgres'
import { envelope, uuid } from '../../tests/helpers.ts'
import { http, TestSocket } from '../../tests/realtime-helpers.ts'

const ROOT = join(import.meta.dirname, '../..')
const RESULTS_DIR = join(ROOT, 'acceptance/results')
const BASE_URL = process.env.ACCEPTANCE_DATABASE_URL ?? 'postgres://boarderless:boarderless@localhost:5433/postgres'
const DB_NAME = 'boarderless_acceptance_mp'
const git = (...args: string[]) => {
  try {
    return execFileSync('git', args, { encoding: 'utf8', cwd: ROOT }).trim()
  } catch {
    return 'unknown'
  }
}
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

const startedAt = new Date()
const result = {
  cases: ['CORE-04', 'RT-04'],
  kind: '兩個真實 API process（bun src/server.ts）+ 同一 PostgreSQL；kill -9 其中一個',
  startedAt: startedAt.toISOString(),
  finishedAt: '',
  environment: {
    backendCommit: git('rev-parse', 'HEAD'),
    uncommittedChanges: git('status', '--porcelain', '--', '.').split('\n').filter(Boolean).length,
    bun: Bun.version,
  },
  checks: [] as Check[],
  status: 'failed' as 'passed' | 'failed',
  error: undefined as string | undefined,
}
const check = (name: string, pass: boolean, detail?: unknown) => result.checks.push({ name, pass, ...(detail === undefined ? {} : { detail }) })

const admin = postgres(BASE_URL, { max: 1, onnotice: () => {} })
const dbUrl = (() => {
  const u = new URL(BASE_URL)
  u.pathname = `/${DB_NAME}`
  return u.toString()
})()
const processes: ChildProcess[] = []
let storageDir = ''

async function startServer(): Promise<{ proc: ChildProcess; base: string; ws: string }> {
  const port = await freePort()
  const proc = spawn('bun', ['src/server.ts'], {
    cwd: ROOT,
    env: {
      ...process.env,
      NODE_ENV: 'test',
      PORT: String(port),
      HOST: '127.0.0.1',
      DATABASE_URL: dbUrl,
      LOG_LEVEL: 'warn',
      STORAGE_DRIVER: 'local',
      STORAGE_LOCAL_DIR: storageDir,
      STORAGE_SIGNING_SECRET: 'acceptance-only',
      REALTIME_ENABLED: 'true',
    },
    stdio: ['ignore', 'ignore', 'inherit'],
  })
  processes.push(proc)
  const base = `http://127.0.0.1:${port}`
  for (let i = 0; i < 100; i++) {
    try {
      if ((await fetch(`${base}/health/ready`)).ok) return { proc, base, ws: `ws://127.0.0.1:${port}/api/v1/realtime` }
    } catch {}
    await Bun.sleep(100)
  }
  throw new Error('server did not become ready')
}

try {
  await admin.unsafe(`DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE)`)
  await admin.unsafe(`CREATE DATABASE ${DB_NAME}`)
  storageDir = await mkdtemp(join(tmpdir(), 'boarderless-mp-'))
  execFileSync('bun', ['src/db/migrate.ts'], { cwd: ROOT, env: { ...process.env, DATABASE_URL: dbUrl }, stdio: 'ignore' })
  const p1 = await startServer()
  const p2 = await startServer()

  const owner = (await http(p1.base).post('/users', { displayName: 'Owner' })).body.id as string
  const ws = (await http(p1.base, owner).post('/workspaces', { title: 'Multi-process' })).body.id as string
  const text = () => ({ kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } })

  // ---- CORE-04：兩個 process 並行提交 ----
  const submits = Array.from({ length: 40 }, (_, i) =>
    http(i % 2 === 0 ? p1.base : p2.base, owner).post(`/workspaces/${ws}/operations`, envelope(0, [text(), text()])),
  )
  const responses = await Promise.all(submits)
  const accepted = responses.filter((r) => r.body?.status === 'accepted')
  check('40 個並行 transaction（分散兩個 process）全部 accepted', accepted.length === 40, responses.map((r) => r.status).filter((s) => s !== 200))
  const seqs = accepted.flatMap((r) => r.body.operations.map((o: any) => o.serverSeq)).sort((x: number, y: number) => x - y)
  check('serverSeq 連續 1..80、無重複', seqs.length === 80 && seqs.every((s: number, i: number) => s === i + 1))
  const versions = accepted.map((r) => r.body.workspaceVersion).sort((x: number, y: number) => x - y)
  check('workspaceVersion 連續 1..40', versions.every((v: number, i: number) => v === i + 1))
  const ops = (await http(p2.base, owner).get(`/workspaces/${ws}/operations?afterSeq=0&limit=1000`)).body
  check('operation log 與兩個 process 回覆一致', ops.operations.length === 80 && ops.lastServerSeq === 80)

  // ---- RT-04：跨 process fan-out 與 kill -9 ----
  async function joinRealtime(server: { base: string; ws: string }, lastSeen: number) {
    const clientId = uuid()
    const epoch = uuid()
    const ticket = (await http(server.base, owner).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 1, clientId })).body.ticket
    const socket = new TestSocket(server.ws)
    await socket.opened()
    socket.send({ type: 'join_workspace', protocolVersion: 1, schemaVersion: 1, workspaceId: ws, clientId, connectionEpoch: epoch, lastSeenServerSeq: lastSeen, metadataRevision: 1, ticket })
    const joined = await socket.next('join_accepted', 5000)
    let applied = lastSeen
    const received: any[] = []
    while (applied < joined.barrierServerSeq) {
      const tx = await socket.next('catch_up_transaction', 5000)
      received.push(tx)
      applied = tx.toServerSeq
    }
    socket.send({ type: 'catch_up_applied', protocolVersion: 1, workspaceId: ws, connectionEpoch: epoch, appliedServerSeq: applied, workspaceVersion: 0, metadataRevision: 1 })
    await socket.next('live_ready', 5000)
    return { socket, clientId, epoch, applied, received }
  }

  const watcher = await joinRealtime(p1, 80)
  const writer = await joinRealtime(p2, 80)
  const tx = envelope(40, [text()], writer.clientId)
  writer.socket.send({ type: 'submit_operations', protocolVersion: 1, workspaceId: ws, connectionEpoch: writer.epoch, ...tx })
  const ack = await writer.socket.next('operation_ack', 5000)
  const fanned = await watcher.socket.next('committed_transaction', 5000)
  check('P2 的 WS 提交以完整 transaction fan-out 到 P1', ack.status === 'accepted' && fanned.transactionId === tx.transactionId && JSON.stringify(fanned.operations) === JSON.stringify(ack.operations))
  let checkpoint = fanned.toServerSeq as number

  // kill -9 P1：P1 上的連線中斷；期間透過 P2 繼續提交
  p1.proc.kill('SIGKILL')
  const code = await watcher.socket.closed(5000).catch(() => null)
  check('kill -9 後 P1 的 socket 中斷', code !== null, code)
  const during = []
  for (let i = 0; i < 5; i++) {
    during.push((await http(p2.base, owner).post(`/workspaces/${ws}/operations`, envelope(41 + i, [text()]))).body)
  }
  check('P1 停止期間 P2 持續 accepted', during.every((d) => d.status === 'accepted'))

  // 以已套用 checkpoint 重新 join 另一個 process
  const rejoined = await joinRealtime(p2, checkpoint)
  const recovered = rejoined.received.map((t) => t.transactionId)
  check(
    '重新 join：補齊 P1 停止期間的 5 筆完整 transaction，無遺失、無重複',
    JSON.stringify(recovered) === JSON.stringify(during.map((d) => d.operations[0].transactionId)),
    { recovered: recovered.length },
  )
  checkpoint = rejoined.applied
  check('checkpoint 前移到 head，無跳號', checkpoint === 86, checkpoint)

  // 已 accepted 的資料不遺失
  const head = (await http(p2.base, owner).get(`/workspaces/${ws}`)).body
  check('Workspace head 與所有 accepted 結果一致', head.lastServerSeq === 86 && head.currentVersion === 46, { lastServerSeq: head.lastServerSeq, currentVersion: head.currentVersion })
  const [{ n }] = await (async () => {
    const s = postgres(dbUrl, { max: 1, onnotice: () => {} })
    try {
      for (let i = 0; i < 40; i++) {
        const rows = await s<{ n: number }[]>`SELECT count(*)::int AS n FROM transaction_outbox WHERE published_at IS NULL`
        if (rows[0]!.n === 0) return rows
        await Bun.sleep(100)
      }
      return s<{ n: number }[]>`SELECT count(*)::int AS n FROM transaction_outbox WHERE published_at IS NULL`
    } finally {
      await s.end()
    }
  })()
  check('outbox 全部由存活 process 發布（含 P1 被殺時未發布者）', n === 0, n)
  writer.socket.close()
  rejoined.socket.close()

  result.status = result.checks.every((c) => c.pass) ? 'passed' : 'failed'
} catch (error) {
  result.error = error instanceof Error ? error.message : String(error)
} finally {
  for (const p of processes) p.kill('SIGKILL')
  await Bun.sleep(200)
  await admin.unsafe(`DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE)`).catch(() => {})
  await admin.end()
  if (storageDir) await rm(storageDir, { recursive: true, force: true })
  result.finishedAt = new Date().toISOString()
  await mkdir(RESULTS_DIR, { recursive: true })
  const file = join(RESULTS_DIR, `${startedAt.toISOString().replace(/[:.]/g, '-')}-multi-process.json`)
  await writeFile(file, `${JSON.stringify(result, null, 2)}\n`)
  for (const c of result.checks) console.log(`${c.pass ? '✓' : '✗'} ${c.name}`)
  console.log(`${result.status.toUpperCase()}${result.error ? `: ${result.error}` : ''} → ${file}`)
  process.exit(result.status === 'passed' ? 0 : 1)
}
