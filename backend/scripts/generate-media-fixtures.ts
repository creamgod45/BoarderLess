/**
 * 產生六種 allowlist MIME 的真實 binary fixture 與 manifest（SHA-256、大小、尺寸、duration）。
 * 需要 ffmpeg / ffprobe 與 cwebp。用法：bun run fixtures:media
 */
import { spawnSync } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { MediaTools } from '../src/media/ffmpeg.ts'
import { hashFile } from '../src/media/files.ts'

const dir = join(import.meta.dirname, '../tests/fixtures/media')
const run = (cmd: string, args: string[]) => {
  const res = spawnSync(cmd, args, { stdio: ['ignore', 'ignore', 'pipe'] })
  if (res.status !== 0) throw new Error(`${cmd} failed: ${res.stderr}`)
}
const ff = (...args: string[]) => run('ffmpeg', ['-v', 'error', '-y', ...args])

const fixtures = [
  { file: 'image.png', mediaType: 'image/png', make: (p: string) => ff('-f', 'lavfi', '-i', 'testsrc=size=64x48:rate=1', '-frames:v', '1', p) },
  { file: 'image.jpg', mediaType: 'image/jpeg', make: (p: string) => ff('-f', 'lavfi', '-i', 'testsrc=size=64x48:rate=1', '-frames:v', '1', '-q:v', '4', p) },
  {
    file: 'image.webp',
    mediaType: 'image/webp',
    make: (p: string) => run('cwebp', ['-quiet', '-q', '80', join(dir, 'image.png'), '-o', p]),
  },
  {
    file: 'animation.gif',
    mediaType: 'image/gif',
    make: (p: string) => ff('-f', 'lavfi', '-i', 'testsrc=size=48x32:rate=10:duration=1', p),
  },
  {
    file: 'video.mp4',
    mediaType: 'video/mp4',
    make: (p: string) =>
      ff(
        '-f', 'lavfi', '-i', 'testsrc=size=64x48:rate=15:duration=1',
        '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
        '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest', '-movflags', '+faststart', p,
      ),
  },
  {
    file: 'video.webm',
    mediaType: 'video/webm',
    make: (p: string) =>
      ff(
        '-f', 'lavfi', '-i', 'testsrc=size=64x48:rate=15:duration=1',
        '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
        '-c:v', 'libvpx-vp9', '-b:v', '100k', '-c:a', 'libopus', '-shortest', p,
      ),
  },
]

await mkdir(dir, { recursive: true })
const tools = new MediaTools('ffmpeg', 'ffprobe')
const manifest = []
for (const fixture of fixtures) {
  const path = join(dir, fixture.file)
  fixture.make(path)
  const probe = await tools.probe(path)
  const { byteCount, checksum } = await hashFile(path)
  const animated = fixture.mediaType === 'image/gif' || fixture.mediaType.startsWith('video/')
  manifest.push({
    file: fixture.file,
    mediaType: fixture.mediaType,
    byteSize: byteCount,
    checksum,
    width: probe?.video?.width ?? null,
    height: probe?.video?.height ?? null,
    durationMs: animated ? (probe?.durationMs ?? null) : null,
    hasAudio: probe?.hasAudio ?? false,
  })
}
await writeFile(join(dir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
console.log(manifest)
