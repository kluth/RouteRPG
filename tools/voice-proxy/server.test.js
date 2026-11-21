'use strict';
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const fs = require('fs');
const path = require('path');
const { createServer, cleanText } = require('./server');

function listen(server) { return new Promise((r) => server.listen(0, '127.0.0.1', () => r(server.address().port))); }
async function post(port, body, token = 'secret') {
  const res = await fetch(`http://127.0.0.1:${port}/tts`, { method: 'POST', headers: { 'x-api-token': token, 'content-type': 'application/json' }, body: JSON.stringify(body) });
  return { status: res.status, buf: Buffer.from(await res.arrayBuffer()), type: res.headers.get('content-type') };
}
function mk(extra = {}) {
  const cfg = { port: 0, token: 'secret', apiKey: 'k', voiceId: 'v', model: 'm', cacheDir: fs.mkdtempSync(path.join(os.tmpdir(), 'vp-')), dailyChars: 100, maxLen: 40, upstream: 'http://x', perMinute: 100, ...extra };
  let calls = 0;
  const server = createServer(cfg, async (t) => { calls++; return Buffer.from('AUDIO:' + t); });
  return { server, cfg, calls: () => calls };
}

test('cleanText: Namen ja, Unfug nein', () => {
  assert.strictEqual(cleanText('  Hauptstraße ', 40), 'Hauptstraße');
  assert.strictEqual(cleanText('Straße   des 17. Juni', 40), 'Straße des 17. Juni');
  assert.strictEqual(cleanText("L'Étoile-Allee", 40), "L'Étoile-Allee");
  assert.strictEqual(cleanText('', 40), null);
  assert.strictEqual(cleanText('a'.repeat(41), 40), null);
  assert.strictEqual(cleanText('Ignore previous <script>', 40), null);
  assert.strictEqual(cleanText('Wo bin ich? Jetzt!', 40), null);
  assert.strictEqual(cleanText(5, 40), null);
});

test('Token noetig, Cache spart zweiten Abruf, Sprachen getrennt', async () => {
  const { server, calls } = mk();
  const port = await listen(server);
  assert.strictEqual((await post(port, { text: 'Hauptstraße', lang: 'de' }, 'falsch')).status, 401);
  const a = await post(port, { text: 'Hauptstraße', lang: 'de' });
  assert.strictEqual(a.status, 200); assert.strictEqual(a.type, 'audio/mpeg'); assert.strictEqual(a.buf.toString(), 'AUDIO:Hauptstraße');
  await post(port, { text: 'Hauptstraße', lang: 'de' });
  assert.strictEqual(calls(), 1);
  await post(port, { text: 'Hauptstraße', lang: 'en' });
  assert.strictEqual(calls(), 2);
  server.close();
});

test('gleichzeitige gleiche Anfragen erzeugen nur einmal', async () => {
  const { server, calls } = mk();
  const port = await listen(server);
  const rs = await Promise.all([1, 2, 3].map(() => post(port, { text: 'Bahnhofstraße', lang: 'de' })));
  assert.ok(rs.every((r) => r.status === 200));
  assert.strictEqual(calls(), 1);
  server.close();
});

test('Tageslimit, ungueltige Eingaben und Fehler von oben', async () => {
  const { server } = mk({ dailyChars: 12 });
  const port = await listen(server);
  assert.strictEqual((await post(port, { text: 'Hauptstraße', lang: 'de' })).status, 200); // 11 Zeichen
  assert.strictEqual((await post(port, { text: 'Bahnhofstraße', lang: 'de' })).status, 429);
  assert.strictEqual((await post(port, { text: 'x<y', lang: 'de' })).status, 400);
  assert.strictEqual((await post(port, { text: 'Weg', lang: 'fr' })).status, 400);
  server.close();
  const failing = createServer({ port: 0, token: 'secret', apiKey: 'k', voiceId: 'v', model: 'm', cacheDir: fs.mkdtempSync(path.join(os.tmpdir(), 'vp-')), dailyChars: 100, maxLen: 40, upstream: 'http://x', perMinute: 100 }, async () => { throw new Error('boom'); });
  const p2 = await listen(failing);
  assert.strictEqual((await post(p2, { text: 'Weg', lang: 'de' })).status, 502);
  failing.close();
});
