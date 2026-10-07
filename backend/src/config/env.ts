import { tmpdir } from 'node:os'
import { resolve } from 'node:path'

export type AuthMode = 'dev'

export interface LocalStorageConfig {
  driver: 'local'
  /** 物件存放目錄 */
  directory: string
  /** 簽名 URL 的對外 base URL（指向本服務的 /storage） */
  publicBaseUrl: string
  signingSecret: string
}

export interface S3StorageConfig {
  driver: 's3'
  /** 伺服器內部存取用 endpoint */
  endpoint: string
  /** 簽發給 client 的 endpoint（未設定時等同 endpoint） */
  publicEndpoint: string
  region: string
  bucket: string
  accessKeyId: string
  secretAccessKey: string
  /** 開發環境啟動時建立 bucket 並設定 CORS */
  ensureBucket: boolean
}

export type StorageConfig = LocalStorageConfig | S3StorageConfig

export interface MediaConfig {
  uploadUrlTtlSeconds: number
  downloadUrlTtlSeconds: number
  /** upload URL 過期後再等多久才由 GC 清理未完成的上傳 */
  uploadGcGraceSeconds: number
  ffmpegPath: string
  ffprobePath: string
  tmpDir: string
}

export interface WorkerConfig {
  concurrency: number
  pollIntervalMs: number
  leaseSeconds: number
  maxAttempts: number
  gcIntervalMs: number
}

export interface AppConfig {
  nodeEnv: string
  host: string
  port: number
  logLevel: string
  databaseUrl: string
  databasePoolMax: number
  authMode: AuthMode
  /** API 允許的 Web origin；`*` 表示全部（僅限開發） */
  corsOrigins: string[]
  storage: StorageConfig
  media: MediaConfig
  worker: WorkerConfig
}

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`Missing required environment variable: ${name}`)
  return value
}

function str(name: string, fallback: string): string {
  const value = process.env[name]
  return value === undefined || value === '' ? fallback : value
}

function int(name: string, fallback: number): number {
  const raw = process.env[name]
  if (raw === undefined || raw === '') return fallback
  const value = Number.parseInt(raw, 10)
  if (!Number.isFinite(value)) throw new Error(`Environment variable ${name} must be an integer`)
  return value
}

function loadStorageConfig(nodeEnv: string, port: number): StorageConfig {
  const driver = str('STORAGE_DRIVER', 'local')
  if (driver === 'local') {
    const signingSecret = process.env.STORAGE_SIGNING_SECRET
    if (!signingSecret && nodeEnv === 'production') {
      throw new Error('STORAGE_SIGNING_SECRET is required when STORAGE_DRIVER=local in production')
    }
    return {
      driver,
      directory: resolve(str('STORAGE_LOCAL_DIR', '.storage')),
      publicBaseUrl: str('PUBLIC_BASE_URL', `http://localhost:${port}`).replace(/\/+$/, ''),
      signingSecret: signingSecret ?? 'boarderless-dev-only-signing-secret',
    }
  }
  if (driver === 's3') {
    const endpoint = required('S3_ENDPOINT').replace(/\/+$/, '')
    return {
      driver,
      endpoint,
      publicEndpoint: str('S3_PUBLIC_ENDPOINT', endpoint).replace(/\/+$/, ''),
      region: str('S3_REGION', 'us-east-1'),
      bucket: required('S3_BUCKET'),
      accessKeyId: required('S3_ACCESS_KEY_ID'),
      secretAccessKey: required('S3_SECRET_ACCESS_KEY'),
      ensureBucket: str('S3_ENSURE_BUCKET', 'false') === 'true',
    }
  }
  throw new Error(`Unsupported STORAGE_DRIVER: ${driver}`)
}

export function loadConfig(): AppConfig {
  const authMode = process.env.AUTH_MODE ?? 'dev'
  if (authMode !== 'dev') throw new Error(`Unsupported AUTH_MODE: ${authMode}`)
  const nodeEnv = process.env.NODE_ENV ?? 'development'
  const port = int('PORT', 3000)

  return {
    nodeEnv,
    host: process.env.HOST ?? '0.0.0.0',
    port,
    logLevel: process.env.LOG_LEVEL ?? 'info',
    databaseUrl: required('DATABASE_URL'),
    databasePoolMax: int('DATABASE_POOL_MAX', 10),
    authMode,
    corsOrigins: str('CORS_ORIGINS', '*')
      .split(',')
      .map((o) => o.trim())
      .filter(Boolean),
    storage: loadStorageConfig(nodeEnv, port),
    media: {
      uploadUrlTtlSeconds: int('UPLOAD_URL_TTL_SECONDS', 900),
      downloadUrlTtlSeconds: int('DOWNLOAD_URL_TTL_SECONDS', 300),
      uploadGcGraceSeconds: int('UPLOAD_GC_GRACE_SECONDS', 600),
      ffmpegPath: str('FFMPEG_PATH', 'ffmpeg'),
      ffprobePath: str('FFPROBE_PATH', 'ffprobe'),
      tmpDir: str('MEDIA_TMP_DIR', tmpdir()),
    },
    worker: {
      concurrency: int('WORKER_CONCURRENCY', 2),
      pollIntervalMs: int('WORKER_POLL_INTERVAL_MS', 1000),
      leaseSeconds: int('JOB_LEASE_SECONDS', 300),
      maxAttempts: int('JOB_MAX_ATTEMPTS', 5),
      gcIntervalMs: int('UPLOAD_GC_INTERVAL_MS', 60_000),
    },
  }
}
