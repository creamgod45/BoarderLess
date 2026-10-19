// Opt-in QA helper. Install mermaid@12.1.0 and jsdom@26.1.0 in a separate directory.
// Never downloads packages, renders SVG, or evaluates node text as program instructions.
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const runtime = process.env.BOARDERLESS_MERMAID_RUNTIME;
if (!runtime) throw new Error('Set BOARDERLESS_MERMAID_RUNTIME to the isolated dependency directory');
const require = createRequire(resolve(runtime, 'package.json'));
const { JSDOM } = require('jsdom');
const dom = new JSDOM('');
globalThis.window = dom.window;
globalThis.document = dom.window.document;
const { default: mermaid } = await import(pathToFileURL(require.resolve('mermaid')).href);
mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' });
let source = '';
for await (const chunk of process.stdin) {
    source += chunk;
    if (Buffer.byteLength(source) > 64 * 1024) throw new Error('Diagram exceeds QA size limit');
}
const result = await mermaid.parse(source);
if (!['flowchart', 'flowchart-v2'].includes(result.diagramType)) throw new Error('Unexpected diagram type');
process.stdout.write(result.diagramType);
