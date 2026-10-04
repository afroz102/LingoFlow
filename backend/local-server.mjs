import { createServer } from 'node:http';
import { Readable } from 'node:stream';
import { fileURLToPath } from 'node:url';
import { openDatabase } from './local-database.mjs';
import { handleRequest, reply } from './translation.mjs';

const apiKey = process.env.GEMINI_API_KEY;
const model = process.env.GEMINI_MODEL ?? 'gemini-3.5-flash-lite';
const port = Number(process.env.PORT ?? 8787);
const host = process.env.HOST ?? '127.0.0.1';
if (!apiKey || !/^[a-z0-9.-]+$/.test(model) || !Number.isInteger(port) || port < 1 || port > 65535) {
  console.error('Backend requires GEMINI_API_KEY, a valid GEMINI_MODEL, and PORT from 1 to 65535.');
  process.exit(1);
}
const store = openDatabase(process.env.DATABASE_PATH ?? fileURLToPath(new URL('./data/usage.sqlite', import.meta.url)));
const server = createServer({ requestTimeout: 10_000, headersTimeout: 10_000, maxHeaderSize: 8192 }, async (incoming, outgoing) => {
  try {
    const method = incoming.method ?? 'GET';
    const request = new Request(`http://localhost${incoming.url}`, {
      method, headers: incoming.headers,
      ...(['GET', 'HEAD'].includes(method) ? {} : { body: Readable.toWeb(incoming), duplex: 'half' }),
    });
    const response = await handleRequest(request, {
      apiKey, model,
      consumeQuota: () => store.reserve(),
      checkDatabase: () => store.database.prepare('SELECT day FROM translation_usage LIMIT 1').all(),
    });
    outgoing.writeHead(response.status, Object.fromEntries(response.headers));
    outgoing.end(Buffer.from(await response.arrayBuffer()));
  } catch {
    if (!outgoing.headersSent) {
      const response = reply(500, { error: 'SERVER_ERROR' });
      outgoing.writeHead(response.status, Object.fromEntries(response.headers));
      outgoing.end(await response.text());
    } else outgoing.destroy();
  }
});
server.listen(port, host, () => console.log(`LingoFlow backend listening on ${host}:${port}; model=${model}`));
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close(() => { store.close(); process.exit(0); }));
