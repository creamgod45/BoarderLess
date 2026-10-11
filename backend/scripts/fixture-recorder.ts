import { mkdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'

const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g
const ISO_RE = /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z/g
const HEX = '123456789abcdef'

const placeholder = (n: number) => {
  if (n >= HEX.length) return `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
  const c = HEX[n]!
  const r = c.repeat(4)
  return `${r}${r}-${r}-4${c.repeat(3)}-8${c.repeat(3)}-${r}${r}${r}`
}

export interface RecordedRequest {
  method: string
  path: string
  body?: unknown
}

export interface RecordedResponse {
  status: number
  body: unknown
}

/**
 * 錄製 request / response fixtures：UUID 依出現順序換成固定 placeholder、時間固定、
 * 簽名 URL 與 requestId 正規化，讓 fixture 可穩定 diff 並跨語言共用。
 */
export class FixtureRecorder {
  private readonly ids = new Map<string, string>()
  private readonly written: { name: string; description: string; status: number }[] = []

  constructor(private readonly outDir: string) {}

  async reset(): Promise<void> {
    await rm(this.outDir, { recursive: true, force: true })
    await mkdir(this.outDir, { recursive: true })
  }

  normId(id: string): string {
    if (!this.ids.has(id)) this.ids.set(id, placeholder(this.ids.size))
    return this.ids.get(id)!
  }

  normalize(value: unknown, key = ''): unknown {
    if (typeof value === 'string') {
      if (key === 'url') {
        return value.includes('/storage/objects/uploads') || value.includes('ct=')
          ? 'https://storage.example.invalid/signed-upload'
          : 'https://storage.example.invalid/signed-download'
      }
      if (key === 'expiresAt') return '2026-10-02T00:15:00.000Z'
      return value.replace(UUID_RE, (id) => this.normId(id)).replace(ISO_RE, '2026-10-02T00:00:00.000Z')
    }
    if (Array.isArray(value)) return value.map((v) => this.normalize(v))
    if (value && typeof value === 'object') {
      // UUID 作為 key（例如 expectedObjectVersions）也要正規化
      return Object.fromEntries(
        Object.entries(value).map(([k, v]) => [k.replace(UUID_RE, (id) => this.normId(id)), this.normalize(v, k)]),
      )
    }
    return value
  }

  /**
   * error envelope 的 requestId 是每次請求的 trace ID：不參與 UUID 編號，直接固定成常數。
   * 其他 requestId 欄位（preparation v2）是業務 identity，照一般 UUID 正規化。
   */
  private normalizeBody(body: unknown): unknown {
    if (body && typeof body === 'object' && 'error' in body && 'requestId' in body) {
      const { requestId: _traceId, ...rest } = body as Record<string, unknown>
      return { ...(this.normalize(rest) as object), requestId: '00000000-0000-4000-8000-000000000000' }
    }
    return this.normalize(body)
  }

  async record(name: string, description: string, request: RecordedRequest, response: RecordedResponse): Promise<void> {
    const doc = {
      description,
      request: {
        method: request.method,
        path: this.normalize(request.path),
        ...(request.body ? { body: this.normalize(request.body) } : {}),
      },
      response: { status: response.status, ...(response.body == null ? {} : { body: this.normalizeBody(response.body) }) },
    }
    await this.writeJson(`${name}.json`, doc)
    this.written.push({ name, description, status: response.status })
  }

  /** 非 API 呼叫（例如 signed PUT）的說明型 fixture */
  async recordRaw(name: string, description: string, doc: object, status: number): Promise<void> {
    await this.writeJson(`${name}.json`, { description, ...doc })
    this.written.push({ name, description, status })
  }

  async writeIndex(contract: string, generatedBy: string): Promise<number> {
    await this.writeJson('index.json', { contract, generatedBy, fixtures: this.written })
    return this.written.length
  }

  private async writeJson(file: string, value: unknown): Promise<void> {
    await writeFile(join(this.outDir, file), `${JSON.stringify(value, null, 2)}\n`)
  }
}
