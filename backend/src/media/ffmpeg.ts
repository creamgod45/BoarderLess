import { spawn } from 'node:child_process'

/** ffmpeg / ffprobe 無法執行（未安裝、權限）——基礎設施問題，job 應重試而非 reject 素材 */
export class MediaToolUnavailableError extends Error {
  constructor(tool: string, options?: { cause?: unknown }) {
    super(`Media tool unavailable: ${tool}`, options)
    this.name = 'MediaToolUnavailableError'
  }
}

export interface ProbeResult {
  formatName: string
  durationMs: number | null
  video: { width: number; height: number; codec: string } | null
  hasAudio: boolean
}

interface ExecResult {
  code: number | null
  stdout: string
  stderr: string
}

const MAX_OUTPUT = 1024 * 1024

/**
 * 外部媒體工具。每次呼叫都是獨立 child process，不阻塞 event loop；
 * 同時執行數由 worker concurrency 限制（§6）。
 */
export class MediaTools {
  constructor(
    private readonly ffmpegPath: string,
    private readonly ffprobePath: string,
    private readonly timeoutMs = 120_000,
  ) {}

  /** 讀取容器 / stream 資訊；無法解析時回傳 null（內容不合法） */
  async probe(path: string): Promise<ProbeResult | null> {
    const res = await this.exec(this.ffprobePath, [
      '-v', 'error',
      '-print_format', 'json',
      '-show_format',
      '-show_streams',
      path,
    ])
    if (res.code !== 0) return null
    try {
      const data = JSON.parse(res.stdout) as {
        format?: { format_name?: string; duration?: string }
        streams?: { codec_type?: string; codec_name?: string; width?: number; height?: number; duration?: string }[]
      }
      const streams = data.streams ?? []
      const video = streams.find((s) => s.codec_type === 'video' && (s.width ?? 0) > 0 && (s.height ?? 0) > 0)
      const seconds = Number(data.format?.duration ?? video?.duration)
      return {
        formatName: data.format?.format_name ?? '',
        durationMs: Number.isFinite(seconds) && seconds > 0 ? Math.round(seconds * 1000) : null,
        video: video ? { width: video.width!, height: video.height!, codec: video.codec_name ?? '' } : null,
        hasAudio: streams.some((s) => s.codec_type === 'audio'),
      }
    } catch {
      return null
    }
  }

  /** 實際解碼第一個影格；失敗表示內容無法解碼 */
  async canDecode(path: string): Promise<boolean> {
    const res = await this.exec(this.ffmpegPath, [
      '-v', 'error', '-xerror', '-nostdin',
      '-i', path,
      '-map', '0:v:0', '-frames:v', '1',
      '-f', 'null', '-',
    ])
    return res.code === 0
  }

  /** 擷取單一靜態影格並縮到 maxEdge 內（不放大） */
  async extractFrame(
    input: string,
    output: string,
    options: { seekMs: number; maxEdge: number; format: 'png' | 'jpeg' },
  ): Promise<boolean> {
    const scale =
      `scale=w='min(${options.maxEdge},iw)':h='min(${options.maxEdge},ih)'` +
      ':force_original_aspect_ratio=decrease:force_divisible_by=2'
    const args = ['-v', 'error', '-nostdin', '-y']
    if (options.seekMs > 0) args.push('-ss', (options.seekMs / 1000).toFixed(3))
    args.push('-i', input, '-map', '0:v:0', '-frames:v', '1', '-vf', scale)
    if (options.format === 'jpeg') args.push('-c:v', 'mjpeg', '-q:v', '3', '-pix_fmt', 'yuvj420p', '-f', 'image2')
    else args.push('-c:v', 'png', '-f', 'image2')
    args.push(output)
    const res = await this.exec(this.ffmpegPath, args)
    return res.code === 0
  }

  private exec(command: string, args: string[]): Promise<ExecResult> {
    return new Promise((resolve, reject) => {
      const child = spawn(command, args, { stdio: ['ignore', 'pipe', 'pipe'] })
      let stdout = ''
      let stderr = ''
      const timer = setTimeout(() => child.kill('SIGKILL'), this.timeoutMs)
      child.stdout.on('data', (d: Buffer) => {
        if (stdout.length < MAX_OUTPUT) stdout += d.toString()
      })
      child.stderr.on('data', (d: Buffer) => {
        if (stderr.length < MAX_OUTPUT) stderr += d.toString()
      })
      child.on('error', (error) => {
        clearTimeout(timer)
        reject(new MediaToolUnavailableError(command, { cause: error }))
      })
      child.on('close', (code) => {
        clearTimeout(timer)
        resolve({ code, stdout, stderr })
      })
    })
  }
}
