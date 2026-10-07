import { expect, test } from 'bun:test'
import { LocalObjectStorage } from '../../src/storage/local.storage.ts'

const storage = new LocalObjectStorage('/tmp/unused', 'http://storage.test', 'secret')
const query = (url: string) => Object.fromEntries(new URL(url).searchParams)

test('signed URLs are bound to key, method, content type and expiry', async () => {
  const put = await storage.presignPut('uploads/a.png', { contentType: 'image/png', expiresInSeconds: 60, maxBytes: 10 })
  expect(storage.verify('uploads/a.png', query(put.url), 'PUT')).toMatchObject({ contentType: 'image/png', maxBytes: 10 })
  expect(storage.verify('uploads/b.png', query(put.url), 'PUT')).toBeNull()
  expect(storage.verify('uploads/a.png', query(put.url), 'GET')).toBeNull()
  expect(storage.verify('uploads/a.png', { ...query(put.url), ct: 'image/jpeg' }, 'PUT')).toBeNull()
  expect(storage.verify('uploads/a.png', { ...query(put.url), n: '999999' }, 'PUT')).toBeNull()

  const expired = await storage.presignGet('uploads/a.png', { expiresInSeconds: -1 })
  expect(storage.verify('uploads/a.png', query(expired.url), 'GET')).toBeNull()
})

test('rejects path traversal keys', () => {
  expect(() => storage.tempPathFor('../etc/passwd')).toThrow()
  expect(() => storage.tempPathFor('a//b')).toThrow()
})
