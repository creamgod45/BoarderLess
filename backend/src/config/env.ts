export type AuthMode = 'dev'

export interface AppConfig {
  nodeEnv: string
  host: string
  port: number
  logLevel: string
  databaseUrl: string
  databasePoolMax: number
  authMode: AuthMode
}

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`Missing required environment variable: ${name}`)
  return value
}

function int(name: string, fallback: number): number {
  const raw = process.env[name]
  if (raw === undefined || raw === '') return fallback
  const value = Number.parseInt(raw, 10)
  if (!Number.isFinite(value)) throw new Error(`Environment variable ${name} must be an integer`)
  return value
}

export function loadConfig(): AppConfig {
  const authMode = process.env.AUTH_MODE ?? 'dev'
  if (authMode !== 'dev') throw new Error(`Unsupported AUTH_MODE: ${authMode}`)

  return {
    nodeEnv: process.env.NODE_ENV ?? 'development',
    host: process.env.HOST ?? '0.0.0.0',
    port: int('PORT', 3000),
    logLevel: process.env.LOG_LEVEL ?? 'info',
    databaseUrl: required('DATABASE_URL'),
    databasePoolMax: int('DATABASE_POOL_MAX', 10),
    authMode,
  }
}
