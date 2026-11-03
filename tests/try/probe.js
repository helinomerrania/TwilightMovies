// What a website's own scripts ask its data feed for, and what it answers (on GitHub, by
// .github/workflows/probe.yml), for the app's readers (app/src/main/assets/gecko/ui/readers/): every request its
// pages make for data, with what they send (data.txt); the places in its scripts that name a data address
// (snippets.txt); its pages' HTML; and the answers to the addresses the readers need, asked from inside the
// website's own page the way its scripts ask (ask-NN.txt).
// Usage: node tests/try/probe.js OUT_DIR site-id
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const players = require('./probe-players');

const [OUT, ID] = process.argv.slice(2);
const UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';
const SITES = require('./probe-sites');
const SITE = SITES[ID];
const API = /\/(api|wp-json|wp-admin\/admin-ajax|ajax|livewire|graphql|feeds)\b/;
const KEEP_HEADERS = /^(accept|content-type|x-[\w-]+|authorization|referer)$/i;

const log = [];
const note = (...a) => { const line = a.join(' '); log.push(line); console.log(line); };
const data = [];
const snippets = new Set();
let step = 'start';

function snip(text) {
  const re = /["'`](?:https?:\/\/[\w.-]+)?\/(?:api|wp-json|wp-admin\/admin-ajax|livewire)\b[^"'`]{0,120}["'`]/g;
  let m;
  while ((m = re.exec(text)) && snippets.size < 400) {
    snippets.add(text.slice(Math.max(0, m.index - 350), m.index + m[0].length + 350).replace(/\s+/g, ' '));
  }
}

function listen(page) {
  page.on('response', async (r) => {
    const q = r.request();
    const kind = q.resourceType();
    if (kind === 'script') return snip(await r.text().catch(() => ''));
    if ((kind !== 'xhr' && kind !== 'fetch') || !API.test(new URL(q.url()).pathname)) return;
    const headers = Object.entries(q.headers()).filter(([k]) => KEEP_HEADERS.test(k)).map(([k, v]) => k + ': ' + v).join(' | ');
    const body = await r.text().catch(() => '');
    data.push('=== ' + [step, q.method(), r.status(), q.url()].join(' | ') + '\n--- sent: ' + headers
      + (q.postData() ? '\n--- body: ' + q.postData().slice(0, 3000) : '') + '\n' + body.slice(0, 30000) + '\n');
  });
  page.on('dialog', (d) => d.dismiss().catch(() => {}));
  page.on('popup', (p) => p.close().catch(() => {}));
}

async function open(page, name, address) {
  step = name;
  await page.goto(address, { waitUntil: 'domcontentloaded', timeout: 45000, referer: SITE.referer })
    .catch((e) => note('goto', e.message.split('\n')[0]));
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(2500);
  const html = await page.content().catch(() => '');
  fs.writeFileSync(path.join(OUT, name + '.html'), html.slice(0, 600000));
  snip(html);
  note('PAGE', name, page.url(), JSON.stringify(await page.title().catch(() => '')), html.length + ' characters');
  if (SITE.players) {
    await page.waitForTimeout(SITE.players);   // the players have this long to start
    note('PLAYERS', await players(page, name));
  }
  return html;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, userAgent: UA, locale: 'ckb-IQ' });
  const page = await context.newPage();
  for (const a of SITE.abort || []) await page.route(a, (r) => r.abort());
  listen(page);
  const home = await open(page, '00-start', SITE.start);
  const nonce = (/"nonce"\s*:\s*"(\w+)"/.exec(home) || [])[1] || '';
  note('NONCE', nonce || '(none)');
  // The addresses the readers need, from inside the website's own page, as its scripts ask
  for (let i = 0; i < SITE.ask.length; i++) {
    const address = new URL(SITE.ask[i].replace('{nonce}', nonce), SITE.start).href;
    const r = await page.evaluate(async (u) => {
      try {
        const res = await fetch(u, { credentials: 'include', headers: { Accept: 'application/json, text/plain, */*', 'X-Requested-With': 'XMLHttpRequest' } });
        return { status: res.status, type: res.headers.get('content-type'), body: (await res.text()).slice(0, 300000) };
      } catch (e) {
        return { status: 0, type: '', body: String(e) };
      }
    }, address);
    const name = 'ask-' + String(i + 1).padStart(2, '0') + '.txt';
    fs.writeFileSync(path.join(OUT, name), address + '\n' + r.status + ' ' + r.type + '\n\n' + r.body);
    note('ASK', name, r.status, address, r.body.length + ' characters');
  }
  for (let i = 0; i < SITE.pages.length; i++) await open(page, String(i + 1).padStart(2, '0') + '-page', SITE.pages[i]);
  await browser.close();
  // Its scripts, where they name a data address or the episodes (the places, each with what's around it)
  const found = [];
  for (const s of SITE.scripts || []) {
    const u = new URL(s, SITE.start).href;
    const body = await fetch(u, { headers: { 'User-Agent': UA } }).then((r) => r.text()).catch((e) => String(e));
    note('SCRIPT', u, body.length + ' characters');
    const re = /\/v1\/|episode|season|api\.kurdsubtitle|\.(get|post|put|patch|delete)\(\s*"\//gi;
    let m;
    let last = -1000;
    while ((m = re.exec(body)) && found.length < 600) {
      if (m.index - last < 300) continue;
      last = m.index;
      found.push(u.split('/').pop() + ': ' + body.slice(Math.max(0, m.index - 300), m.index + 300).replace(/\s+/g, ' '));
    }
  }
  if (found.length) fs.writeFileSync(path.join(OUT, 'scripts.txt'), found.join('\n\n'));
  // Scripts kept whole (the parts that draw a player, or a series' seasons and episodes)
  for (const s of SITE.whole || []) {
    const u = new URL(s, SITE.start).href;
    const body = await fetch(u, { headers: { 'User-Agent': UA } }).then((r) => r.text()).catch((e) => String(e));
    fs.writeFileSync(path.join(OUT, 'whole-' + u.split('/').pop().split('-')[0] + '.txt'), u + '\n\n' + body);
  }
  // Its data feed asked straight, as the app's reader would (addresses its pages ask in the viewer's browser)
  for (let i = 0; i < (SITE.direct || []).length; i++) {
    const u = SITE.direct[i];
    const r = await fetch(u, { headers: { 'User-Agent': UA, Accept: 'application/json, text/plain, */*' } })
      .then(async (x) => ({ status: x.status, type: x.headers.get('content-type'), body: await x.text() }))
      .catch((e) => ({ status: 0, type: '', body: String(e) }));
    const name = 'direct-' + String(i + 1).padStart(2, '0') + '.txt';
    fs.writeFileSync(path.join(OUT, name), u + '\n' + r.status + ' ' + r.type + '\n\n' + r.body.slice(0, 300000));
    note('DIRECT', name, r.status, u, r.body.length + ' characters');
  }
})().catch((e) => note('STOPPED', e.stack)).finally(() => {
  fs.writeFileSync(path.join(OUT, 'data.txt'), data.join('\n'));
  fs.writeFileSync(path.join(OUT, 'snippets.txt'), Array.from(snippets).join('\n\n'));
  fs.writeFileSync(path.join(OUT, 'probe.log'), log.join('\n') + '\n');
  process.exit(0);
});
