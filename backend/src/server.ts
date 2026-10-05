import { buildApp } from './app.ts'
import { loadConfig } from './config/env.ts'
import { createSql } from './db/client.ts'
import { createStorage } from './storage/index.ts'

const config = loadConfig()
const sql = createSql(config.databaseUrl, config.databasePoolMax)
const storage = createStorage(config.storage)
await storage.init?.(config.corsOrigins)
const app = await buildApp({ config, sql, storage })

const shutdown = async (signal: string) => {
  app.log.info({ signal }, 'shutting down')
  await app.close()
  await sql.end({ timeout: 5 })
  process.exit(0)
}
process.on('SIGINT', () => void shutdown('SIGINT'))
process.on('SIGTERM', () => void shutdown('SIGTERM'))

await app.listen({ host: config.host, port: config.port })
