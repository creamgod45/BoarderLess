import { html, SafeHtml } from './html.ts'

const STYLE = `
:root { --bg:#f7f7f5; --panel:#fff; --text:#1d1d1b; --muted:#6b6b66; --line:#e3e3de; --accent:#2f6fde; --ok:#1f8a4c; --bad:#c8372d; }
@media (prefers-color-scheme: dark) {
  :root { --bg:#141414; --panel:#1d1d1d; --text:#ececea; --muted:#9a9a95; --line:#2e2e2c; --accent:#6d9cf0; --ok:#4cc27d; --bad:#ef6b61; }
}
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--text); font:15px/1.55 system-ui,-apple-system,"PingFang TC","Noto Sans TC",sans-serif; }
main { max-width:960px; margin:0 auto; padding:40px 16px 64px; }
h1 { font-size:24px; margin:0 0 4px; } h2 { font-size:16px; margin:32px 0 12px; }
p.sub { color:var(--muted); margin:0; } h2 .sub { color:var(--muted); font-weight:400; }
.grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; margin-top:24px; }
.card { background:var(--panel); border:1px solid var(--line); border-radius:10px; padding:14px 16px; }
.card .label { color:var(--muted); font-size:13px; } .card .value { font-size:22px; font-weight:600; font-variant-numeric:tabular-nums; }
.ok { color:var(--ok); } .bad { color:var(--bad); }
table { width:100%; border-collapse:collapse; background:var(--panel); border:1px solid var(--line); border-radius:10px; overflow:hidden; }
th,td { text-align:left; padding:8px 12px; border-bottom:1px solid var(--line); font-size:14px; vertical-align:top; }
tr:last-child td { border-bottom:0; } th { color:var(--muted); font-weight:500; }
code { font:13px ui-monospace,SFMono-Regular,Menlo,monospace; }
.method { font-weight:600; width:72px; }
a { color:var(--accent); }
.table-wrap { overflow-x:auto; }
`

export function layout(title: string, body: SafeHtml): string {
  return html`<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>${new SafeHtml(STYLE)}</style>
</head>
<body><main>${body}</main></body>
</html>`.value
}
