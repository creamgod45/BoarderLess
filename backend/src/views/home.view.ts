import type { ServiceStatus } from '../services/status.service.ts'
import { html } from './html.ts'
import { layout } from './layout.view.ts'

export interface EndpointInfo {
  method: string
  url: string
  summary?: string
}

export function homeView(status: ServiceStatus, endpoints: EndpointInfo[]): string {
  const dbClass = status.database === 'up' ? 'ok' : 'bad'
  const stat = (label: string, value: unknown, cls = '') =>
    html`<div class="card"><div class="label">${label}</div><div class="value ${cls}">${value}</div></div>`

  return layout(
    'BoarderLess Backend',
    html`
      <h1>BoarderLess Backend</h1>
      <p class="sub">Workspace 儲存服務 · v${status.version} · 已運行 ${status.uptimeSeconds}s · <a href="/docs">OpenAPI 文件</a></p>

      <div class="grid">
        ${stat('PostgreSQL', status.database, dbClass)}
        ${stat('Workspaces', status.stats?.workspaces ?? '—')}
        ${stat('Operations', status.stats?.operations ?? '—')}
        ${stat('Outbox backlog', status.stats?.outboxBacklog ?? '—')}
      </div>

      <h2>API</h2>
      <div class="table-wrap">
        <table>
          <thead><tr><th>Method</th><th>Path</th><th>說明</th></tr></thead>
          <tbody>
            ${endpoints.map(
              (e) => html`<tr><td class="method"><code>${e.method}</code></td><td><code>${e.url}</code></td><td>${e.summary ?? ''}</td></tr>`,
            )}
          </tbody>
        </table>
      </div>
    `,
  )
}
