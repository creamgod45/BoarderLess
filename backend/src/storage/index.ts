import type { StorageConfig } from '../config/env.ts'
import { LocalObjectStorage } from './local.storage.ts'
import { S3ObjectStorage } from './s3.storage.ts'
import type { ObjectStorage } from './types.ts'

export * from './types.ts'
export { LocalObjectStorage } from './local.storage.ts'
export { S3ObjectStorage } from './s3.storage.ts'

export function createStorage(config: StorageConfig): ObjectStorage {
  return config.driver === 'local'
    ? new LocalObjectStorage(config.directory, config.publicBaseUrl, config.signingSecret)
    : new S3ObjectStorage(config)
}
