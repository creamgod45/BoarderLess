# syntax=docker/dockerfile:1.7
#
# BoarderLess backend
#   target api（預設）：REST API，listen 3000
#   target worker      ：背景 worker（素材驗證 / 縮圖），需要 ffmpeg
#
#   docker build -t boarderless-backend .
#   docker build --target worker -t boarderless-worker .

ARG BUN_VERSION=1.4.2

# ---- 相依套件（只裝 production dependencies）----
FROM oven/bun:${BUN_VERSION}-slim AS deps
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --production --ignore-scripts

# ---- 共用 runtime ----
FROM oven/bun:${BUN_VERSION}-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    MIGRATE_ON_START=true \
    STORAGE_LOCAL_DIR=/data/storage \
    MEDIA_TMP_DIR=/tmp
# STORAGE_DRIVER=local 時的物件目錄（正式環境請改用 s3）
RUN mkdir -p /data/storage && chown -R bun:bun /data
VOLUME ["/data"]
COPY --from=deps --chown=bun:bun /app/node_modules ./node_modules
COPY --chown=bun:bun package.json bun.lock ./
COPY --chown=bun:bun migrations ./migrations
COPY --chown=bun:bun src ./src
COPY --chown=bun:bun docker-entrypoint.sh ./
USER bun
ENTRYPOINT ["./docker-entrypoint.sh"]

# ---- 靜態編譯的 ffmpeg / ffprobe ----
# Debian 的 ffmpeg 套件含 GPU / X11 / 語音合成等用不到的相依（約 420 MB）；
# 靜態執行檔只有兩個檔案，已含所需的 H.264 / HEVC / VP8 / VP9 / AV1(dav1d) 解碼與 PNG / JPEG 編碼。
# 以 digest 固定版本，升級時一併更新。
FROM mwader/static-ffmpeg:8.0@sha256:415a41fa3167b890b9703d20bd0f00bf1e9dab8a4b6c27fef1445b2bf5f1ab4a AS ffmpeg

# ---- worker：ffmpeg / ffprobe 驗證與產生縮圖 ----
FROM runtime AS worker
COPY --from=ffmpeg /ffmpeg /ffprobe /usr/local/bin/
# 由 API 負責 migration，避免兩種 process 同時啟動時重複執行（migration 本身有 advisory lock）
ENV MIGRATE_ON_START=false
CMD ["bun", "src/worker.ts"]

# ---- api（預設 target）----
FROM runtime AS api
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD ["bun", "-e", "fetch(`http://127.0.0.1:${process.env.PORT ?? 3000}/health`).then((r) => process.exit(r.ok ? 0 : 1), () => process.exit(1))"]
CMD ["bun", "src/server.ts"]
