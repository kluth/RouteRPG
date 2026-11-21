'use strict';
// Kilo-Stimmen-Proxy (Spec 016): erzeugt kurze Namens-Clips mit ElevenLabs, cached nach Text-Hash und haelt den API-Schluessel serverseitig.
// Keine Abhaengigkeiten (Node >= 18). Inhalte (Texte) werden nie geloggt, nur Zaehler.
const http = require('http');
const https = require('https');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

function config(env = process.env) {
  return {
    port: Number(env.PORT || 8787),
    token: env.VOICE_TOKEN || '',
    apiKey: env.ELEVEN_API_KEY || '',
    voiceId: env.ELEVEN_VOICE_ID || 'yYTemNXovAWXGGqLADJf',
    model: env.ELEVEN_MODEL || 'eleven_v4',
    cacheDir: env.CACHE_DIR || path.join(__dirname, 'cache'),
    dailyChars: Number(env.DAILY_CHAR_LIMIT || 20000),
    maxLen: Number(env.MAX_TEXT_LENGTH || 40),
    upstream: env.ELEVEN_BASE_URL || 'https://api.elevenlabs.io',
    perMinute: Number(env.RATE_PER_MINUTE || 120),
  };
}

// Erlaubt sind Namen: Buchstaben (auch Umlaute/Akzente), Ziffern, Leerzeichen und - . ' &
const NAME_RE = /^[\p{L}\p{N}][\p{L}\p{N} .'&\-]*$/u;
function cleanText(raw, maxLen) {
  if (typeof raw !== 'string') return null;
  const t = raw.normalize('NFC').replace(/\s+/g, ' ').trim();
  if (t.length === 0 || t.length > maxLen || !NAME_RE.test(t)) return null;
  return t;
}
const LANGS = new Set(['de', 'en']);
const keyOf = (cfg, lang, text) => crypto.createHash('sha256').update([lang, cfg.voiceId, cfg.model, text].join('\u0000')).digest('hex');

function tokenOk(cfg, header) {
  if (!cfg.token) return false;
  const a = Buffer.from(String(header || '')), b = Buffer.from(cfg.token);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function upstreamSpeech(cfg, text) {
  return new Promise((resolve, reject) => {
    const u = new URL(`/v1/text-to-speech/${encodeURIComponent(cfg.voiceId)}?output_format=mp3_44100_64`, cfg.upstream);
    const body = JSON.stringify({ text, model_id: cfg.model });
    const lib = u.protocol === 'http:' ? http : https;
    const req = lib.request(u, { method: 'POST', headers: { 'xi-api-key': cfg.apiKey, 'content-type': 'application/json', 'content-length': Buffer.byteLength(body), accept: 'audio/mpeg' } }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => (res.statusCode === 200 ? resolve(Buffer.concat(chunks)) : reject(new Error('upstream ' + res.statusCode))));
    });
    req.on('error', reject);
    req.setTimeout(30000, () => req.destroy(new Error('timeout')));
    req.end(body);
  });
}

function createServer(cfg = config(), speech = (t) => upstreamSpeech(cfg, t)) {
  fs.mkdirSync(cfg.cacheDir, { recursive: true });
  const stats = { requests: 0, cacheHits: 0, generated: 0, rejected: 0, day: new Date().toISOString().slice(0, 10), chars: 0 };
  const window = []; // Zeitstempel der letzten Minute (einfaches Limit je Server)
  const pending = new Map(); // gleiche Anfragen gleichzeitig nur einmal erzeugen

  const reply = (res, code, obj) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };

  return http.createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/health') return reply(res, 200, { ok: true, requests: stats.requests, cacheHits: stats.cacheHits, generated: stats.generated, charsToday: stats.chars });
    if (req.method !== 'POST' || req.url !== '/tts') return reply(res, 404, { error: 'not found' });
    stats.requests++;
    if (!tokenOk(cfg, req.headers['x-api-token'])) { stats.rejected++; return reply(res, 401, { error: 'unauthorized' }); }
    const now = Date.now();
    while (window.length && now - window[0] > 60000) window.shift();
    if (window.length >= cfg.perMinute) return reply(res, 429, { error: 'rate limit' });
    window.push(now);

    let raw = '';
    for await (const c of req) { raw += c; if (raw.length > 2048) return reply(res, 413, { error: 'too large' }); }
    let data;
    try { data = JSON.parse(raw); } catch { return reply(res, 400, { error: 'bad json' }); }
    const text = cleanText(data.text, cfg.maxLen);
    const lang = String(data.lang || '');
    if (!text || !LANGS.has(lang)) { stats.rejected++; return reply(res, 400, { error: 'bad text or lang' }); }

    const file = path.join(cfg.cacheDir, keyOf(cfg, lang, text) + '.mp3');
    const send = (buf) => { res.writeHead(200, { 'content-type': 'audio/mpeg', 'content-length': buf.length }); res.end(buf); };
    if (fs.existsSync(file)) { stats.cacheHits++; return send(fs.readFileSync(file)); }

    const day = new Date().toISOString().slice(0, 10);
    if (day !== stats.day) { stats.day = day; stats.chars = 0; }
    if (stats.chars + text.length > cfg.dailyChars) return reply(res, 429, { error: 'daily limit' });
    try {
      let p = pending.get(file);
      if (!p) {
        stats.chars += text.length;
        p = speech(text).then((buf) => { fs.writeFileSync(file, buf); stats.generated++; return buf; }).finally(() => pending.delete(file));
        pending.set(file, p);
      }
      send(await p);
    } catch (e) {
      reply(res, 502, { error: 'upstream failed' });
    }
  });
}

if (require.main === module) {
  const cfg = config();
  if (!cfg.token || !cfg.apiKey) { console.error('VOICE_TOKEN und ELEVEN_API_KEY muessen gesetzt sein'); process.exit(1); }
  createServer(cfg).listen(cfg.port, () => console.log('voice proxy on :' + cfg.port));
}
module.exports = { createServer, config, cleanText, keyOf };
