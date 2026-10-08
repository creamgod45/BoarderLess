/* Workspace originals only. Handles are transient, never canvas payloads or persistent storage. */
(() => {
    const entries = new Map(), maxBytes = 200 * 1024 * 1024;
    const requireValue = condition => { if (!condition) throw new Error('Image export unavailable'); };
    function orientation(bytes, start, end) {
        if (end - start >= 6 && String.fromCharCode(...bytes.slice(start, start + 4)) === 'Exif') start += 6;
        if (end - start < 8) return 1;
        const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
        const little = bytes[start] === 73 && bytes[start + 1] === 73;
        if (!little && !(bytes[start] === 77 && bytes[start + 1] === 77)) return 1;
        const u16 = offset => view.getUint16(offset, little), u32 = offset => view.getUint32(offset, little);
        if (u16(start + 2) !== 42) return 1;
        const table = start + u32(start + 4);
        if (table < start || table + 2 > end) return 1;
        const count = u16(table);
        if (table + 2 + count * 12 > end) return 1;
        for (let i = 0; i < count; i++) {
            const p = table + 2 + i * 12;
            if (u16(p) === 274 && u16(p + 2) === 3 && u32(p + 4) === 1) {
                const result = u16(p + 8); return result >= 1 && result <= 8 ? result : 1;
            }
        }
        return 1;
    }
    async function header(blob, type, active) {
        const bytes = new Uint8Array(await blob.slice(0, Math.min(blob.size, 1024 * 1024)).arrayBuffer());
        active();
        const view = new DataView(bytes.buffer);
        const text = (p, n) => String.fromCharCode(...bytes.slice(p, p + n));
        if (type === 'image/png') {
            requireValue(bytes.length >= 33 && bytes[0] === 137 && text(1, 3) === 'PNG' && view.getUint32(8) === 13 && text(12, 4) === 'IHDR');
            const width = view.getUint32(16), height = view.getUint32(20);
            let p = 8, rotate = 1, chunks = 0;
            while (p + 12 <= blob.size) {
                active(); requireValue(++chunks <= 65536);
                const head = new Uint8Array(await blob.slice(p, p + 8).arrayBuffer());
                const length = new DataView(head.buffer).getUint32(0), end = p + 12 + length;
                requireValue(end <= blob.size);
                const kind = String.fromCharCode(...head.slice(4, 8));
                if (kind === 'eXIf') {
                    requireValue(length <= 1024 * 1024);
                    const exif = new Uint8Array(await blob.slice(p + 8, p + 8 + length).arrayBuffer()); rotate = orientation(exif, 0, exif.length);
                }
                p = end; if (kind === 'IEND') break;
            }
            return [width, height, rotate];
        }
        if (type === 'image/jpeg') {
            requireValue(bytes[0] === 255 && bytes[1] === 216);
            let p = 2, width = 0, height = 0, rotate = 1;
            const sof = new Set([192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207]);
            while (p + 3 < bytes.length) {
                requireValue(bytes[p++] === 255);
                while (p < bytes.length && bytes[p] === 255) p++;
                const marker = bytes[p++];
                if (marker === 218 || marker === 217) break;
                if (marker === 1 || (marker >= 208 && marker <= 215)) continue;
                requireValue(p + 2 <= bytes.length);
                const length = view.getUint16(p), end = p + length;
                requireValue(length >= 2 && end <= bytes.length);
                if (marker === 225 && text(p + 2, 4) === 'Exif') rotate = orientation(bytes, p + 2, end);
                if (sof.has(marker)) {
                    requireValue(length >= 8); height = view.getUint16(p + 3); width = view.getUint16(p + 5);
                }
                p = end;
            }
            requireValue(width > 0 && height > 0);
            return [width, height, rotate];
        }
        requireValue(type === 'image/webp' && text(0, 4) === 'RIFF' && text(8, 4) === 'WEBP');
        let p = 12, width = 0, height = 0, rotate = 1, chunks = 0;
        while (p + 8 <= blob.size) {
            active(); requireValue(++chunks <= 65536);
            const part = new Uint8Array(await blob.slice(p, p + 30).arrayBuffer());
            const data = new DataView(part.buffer), kind = String.fromCharCode(...part.slice(0, 4));
            const length = data.getUint32(4, true), end = p + 8 + length;
            requireValue(end <= blob.size);
            if (kind === 'VP8X') {
                requireValue(length >= 10); width = 1 + part[12] + part[13] * 256 + part[14] * 65536;
                height = 1 + part[15] + part[16] * 256 + part[17] * 65536;
            } else if (!width && kind === 'VP8 ') {
                requireValue(length >= 10 && part[11] === 157 && part[12] === 1 && part[13] === 42);
                width = data.getUint16(14, true) & 16383; height = data.getUint16(16, true) & 16383;
            } else if (!width && kind === 'VP8L') {
                requireValue(length >= 5 && part[8] === 47);
                const bits = data.getUint32(9, true); width = (bits & 16383) + 1; height = ((bits >>> 14) & 16383) + 1;
            } else if (kind === 'EXIF') {
                requireValue(length <= 1024 * 1024);
                const exif = new Uint8Array(await blob.slice(p + 8, end).arrayBuffer()); rotate = orientation(exif, 0, exif.length);
            }
            p = end + (length & 1);
        }
        requireValue(width > 0 && height > 0);
        return [width, height, rotate];
    }
    globalThis.boarderlessImageExport = {
        begin(id, type, expected) {
            requireValue(!entries.has(id) && ['image/png', 'image/jpeg', 'image/webp'].includes(type) && Number.isSafeInteger(expected) && expected > 0 && expected <= maxBytes);
            entries.set(id, {type, expected, size: 0, parts: [], decoding: false, bitmap: null});
        },
        append(id, base64) {
            const state = entries.get(id); requireValue(state && !state.decoding && base64.length <= 1398104);
            const text = atob(base64); requireValue(text.length > 0 && text.length <= 1048576 && state.size + text.length <= state.expected);
            const bytes = Uint8Array.from(text, character => character.charCodeAt(0));
            state.parts.push(new Blob([bytes])); state.size += bytes.length;
        },
        decode(id, width, height, done) {
            const state = entries.get(id);
            requireValue(state && !state.decoding && state.size === state.expected && width >= 1 && width <= 4096 && height >= 1 && height <= 4096 && width * height <= 4194304);
            state.decoding = true;
            const active = () => requireValue(entries.get(id) === state);
            (async () => {
                let bitmap = null, canvas = null;
                try {
                    const blob = new Blob(state.parts, {type: state.type}); state.parts = [];
                    const [sw, sh, rotate] = await header(blob, state.type, active); active();
                    requireValue(sw >= 1 && sw <= 65535 && sh >= 1 && sh <= 65535 && sw * sh <= 268435456);
                    const ow = rotate >= 5 ? sh : sw, oh = rotate >= 5 ? sw : sh;
                    const fit = Math.min(width / ow, height / oh, 1), w = Math.max(1, Math.floor(ow * fit)), h = Math.max(1, Math.floor(oh * fit));
                    bitmap = await createImageBitmap(blob, {resizeWidth: w, resizeHeight: h, resizeQuality: 'high', imageOrientation: 'from-image'});
                    active(); state.bitmap = bitmap; requireValue(bitmap.width === w && bitmap.height === h);
                    canvas = typeof OffscreenCanvas === 'function' ? new OffscreenCanvas(w, h) : document.createElement('canvas');
                    canvas.width = w; canvas.height = h;
                    const context = canvas.getContext('2d', {colorSpace: 'srgb'}); requireValue(context);
                    context.globalCompositeOperation = 'copy'; context.drawImage(bitmap, 0, 0);
                    const png = canvas.convertToBlob ? await canvas.convertToBlob({type: 'image/png'}) : await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
                    active(); requireValue(png && png.size <= 33554432);
                    const buffer = new Uint8Array(await png.arrayBuffer()); active();
                    let encoded = '';
                    for (let offset = 0; offset < buffer.length; offset += 32768) encoded += String.fromCharCode(...buffer.subarray(offset, offset + 32768));
                    done(btoa(encoded), null);
                } catch (_) { if (entries.get(id) === state) done(null, 'Image export decode failed'); }
                finally {
                    if (bitmap) bitmap.close(); state.bitmap = null;
                    if (canvas) { canvas.width = 0; canvas.height = 0; }
                    if (entries.get(id) === state) entries.delete(id);
                }
            })();
        },
        release(id) {
            const state = entries.get(id); if (!state) return;
            entries.delete(id); state.parts = [];
            if (state.bitmap) { state.bitmap.close(); state.bitmap = null; }
        },
        activeCount() { return entries.size; }
    };
})();
