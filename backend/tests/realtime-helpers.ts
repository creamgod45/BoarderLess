import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { FastifyInstance } from 'fastify'
import type postgres from 'postgres'
import { buildApp } from '../src/app.ts'
import { defaultRealtimeConfig, type RealtimeConfig } from '../src/config/env.ts'
import { createSql } from '../src/db/client.ts'
import { LocalObjectStorage } from '../src/storage/local.storage.ts'
import { testConfig } from './helpers.ts'

const TEST_URL = () => process.env.TEST_DATABASE_URL ?? 'postgres://boarderless:boarderless@localhost:5433/boarderless_test'

export interface Gateway {
  app: FastifyInstance
  sql: postgres.Sql
  baseUrl: string
  wsUrl: string
  close(): Promise<void>
}

/** 一個獨立的 API + gateway（自己的連線池與 LISTEN 連線），模擬另一個 process */
export async function startGateway(overrides: Partial<RealtimeConfig> = {}): Promise<Gateway> {
  const sql = createSql(TEST_URL(), 5)
  const storageDir = await mkdtemp(join(tmpdir(), 'boarderless-rt-'))
  const storage = new LocalObjectStorage(storageDir, 'http://storage.test', 'test-secret')
  const realtime = defaultRealtimeConfig({ publishIntervalMs: 50, pollIntervalMs: 300, authTimeoutMs: 1000, ...overrides })
  const app = await buildApp({ config: { ...testConfig, realtime }, sql, storage, logger: false })
  await app.listen({ host: '127.0.0.1', port: 0 })
  const port = (app.server.address() as { port: number }).port
  return {
    app,
    sql,
    baseUrl: `http://127.0.0.1:${port}`,
    wsUrl: `ws://127.0.0.1:${port}/api/v1/realtime`,
    async close() {
      await app.close()
      await sql.end({ timeout: 1 })
      await rm(storageDir, { recursive: true, force: true })
    },
  }
}

export type ServerEvent = { type: string; [key: string]: any }

/** 測試用 WebSocket client：依序收集訊息，可等待特定 type */
export class TestSocket {
  readonly messages: ServerEvent[] = []
  private waiters: { match: (m: ServerEvent) => boolean; resolve: (m: ServerEvent) => void }[] = []
  private cursor = 0
  closeCode: number | null = null
  private closeWaiters: ((code: number) => void)[] = []
  readonly ws: WebSocket

  constructor(url: string, options: { protocols?: string[]; origin?: string } = {}) {
    this.ws = new WebSocket(url, {
      protocols: options.protocols ?? ['boarderless.v1'],
      headers: options.origin ? { origin: options.origin } : {},
    } as unknown as string[])
    this.ws.onmessage = (event) => {
      const message = JSON.parse(String(event.data)) as ServerEvent
      this.messages.push(message)
      this.flush()
    }
    this.ws.onclose = (event) => {
      this.closeCode = event.code
      for (const w of this.closeWaiters) w(event.code)
    }
  }

  opened(): Promise<void> {
    if (this.ws.readyState === WebSocket.OPEN) return Promise.resolve()
    return new Promise((resolve, reject) => {
      this.ws.addEventListener('open', () => resolve(), { once: true })
      this.ws.addEventListener('error', () => reject(new Error('websocket error')), { once: true })
      this.ws.addEventListener('close', () => reject(new Error('websocket closed before open')), { once: true })
    })
  }

  send(message: unknown): void {
    this.ws.send(typeof message === 'string' ? message : JSON.stringify(message))
  }

  private flush(): void {
    for (const waiter of [...this.waiters]) {
      const index = this.messages.findIndex((m, i) => i >= this.cursor && waiter.match(m))
      if (index >= 0) {
        this.cursor = index + 1
        this.waiters.splice(this.waiters.indexOf(waiter), 1)
        waiter.resolve(this.messages[index]!)
      }
    }
  }

  /** 等待下一則（之前未取過的）符合條件的訊息 */
  next(type?: string | ((m: ServerEvent) => boolean), timeoutMs = 3000): Promise<ServerEvent> {
    const match = typeof type === 'function' ? type : (m: ServerEvent) => type === undefined || m.type === type
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`timeout waiting for ${String(typeof type === 'function' ? 'predicate' : type)}; got ${this.messages.slice(this.cursor).map((m) => m.type).join(',')}`)), timeoutMs)
      this.waiters.push({
        match,
        resolve: (m) => {
          clearTimeout(timer)
          resolve(m)
        },
      })
      this.flush()
    })
  }

  /** 尚未被 next() 取走的訊息 */
  pending(type?: string): ServerEvent[] {
    return this.messages.slice(this.cursor).filter((m) => !type || m.type === type)
  }

  closed(timeoutMs = 3000): Promise<number> {
    if (this.closeCode !== null) return Promise.resolve(this.closeCode)
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('timeout waiting for close')), timeoutMs)
      this.closeWaiters.push((code) => {
        clearTimeout(timer)
        resolve(code)
      })
    })
  }

  close(): void {
    this.ws.close()
  }
}

/** REST（真 HTTP）呼叫 */
export function http(baseUrl: string, userId?: string) {
  const request = async (method: string, path: string, body?: unknown) => {
    const res = await fetch(`${baseUrl}/api/v1${path}`, {
      method,
      headers: { ...(userId ? { 'x-user-id': userId } : {}), ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const text = await res.text()
    return { status: res.status, headers: res.headers, body: text ? (JSON.parse(text) as any) : null }
  }
  return {
    get: (p: string) => request('GET', p),
    post: (p: string, b?: unknown) => request('POST', p, b ?? {}),
    put: (p: string, b?: unknown) => request('PUT', p, b ?? {}),
    patch: (p: string, b?: unknown) => request('PATCH', p, b ?? {}),
    delete: (p: string) => request('DELETE', p),
  }
}
