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

const [OUT, ID] = process.argv.slice(2);
const UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';
// Each website: where to start, the pages to open (their own requests are kept), the addresses to ask for
// ({nonce}: the one in the start page, which WordPress's search wants)
const SITES = {
  'krd-watch': {
    start: 'https://krd.watch/',
    pages: ['https://krd.watch/movie/634649', 'https://krd.watch/tv/259909', 'https://krd.watch/movies',
      'https://krd.watch/shows', 'https://krd.watch/watch/634649', 'https://krd.watch/watch/tv/259909?s=1&e=1'],
    ask: ['/api/title/movie/634649', '/api/title/tv/259909', '/api/season/259909/1', '/api/discover',
      '/api/discover?kind=movie', '/api/discover?kind=tv', '/api/facets?kind=movie', '/api/lists', '/api/feed',
      '/api/random?kind=movie', '/api/collections?q=spider', '/api/search?q=%D8%B3%D9%BE%D8%A7%DB%8C%D8%AF%DB%95%D8%B1'],
  },
  beenar: {
    start: 'https://beenar.net/',
    pages: ['https://beenar.net/title-detail/3057?series=false', 'https://beenar.net/title-detail/697?series=true',
      'https://beenar.net/title/1?page=1&type&lang', 'https://beenar.net/title/2?page=1&type&lang',
      'https://beenar.net/detail-cowatching/3057?series=false'],
    ask: ['/api/client/main', '/api/client/lists', '/api/client/title-show/3057', '/api/client/series-show/697?part=1'],
  },
  'kurd-movie': {
    start: 'https://www.kurd-movie.com/',
    pages: ['https://www.kurd-movie.com/movies/', 'https://www.kurd-movie.com/tvshows/',
      'https://www.kurd-movie.com/movies/spider-man-no-way-home/',
      'https://www.kurd-movie.com/tvshows/the-lord-of-the-rings-the-rings-of-power/', 'https://www.kurd-movie.com/episodes/1x1/'],
    ask: ['/wp-json/', '/wp-json/wp/v2/types', '/wp-json/wp/v2/movies?per_page=3', '/wp-json/wp/v2/tvshows?per_page=3',
      '/wp-json/wp/v2/episodes?per_page=3', '/wp-json/wp/v2/seasons?per_page=3',
      '/wp-json/dooplay/search/?keyword=spider&nonce={nonce}', '/wp-json/dooplay/glossary/?term=a&nonce={nonce}&type=all'],
  },
  kurdcinama: {
    start: 'https://kurdcinama.com/',
    pages: ['https://kurdcinama.com/movies.aspx', 'https://kurdcinama.com/drama.aspx',
      'https://kurdcinama.com/moves-details.aspx?movieid=18470', 'https://kurdcinama.com/episodes.aspx?type=12',
      'https://kurdcinama.com/Search.aspx'],
    ask: ['/', '/movies.aspx', '/drama.aspx', '/moves-details.aspx?movieid=18470', '/episodes.aspx?type=12',
      '/api/TMDBCache.aspx?action=popular&limit=20', '/api/TMDBCache.aspx?action=movie&id=18470', '/Search.aspx'],
  },
  kurdsaz: {
    start: 'https://kurdsaz.com/',
    pages: ['https://kurdsaz.com/browse?type=movie', 'https://kurdsaz.com/browse?type=series',
      'https://kurdsaz.com/details?slug=Crystal-Lake', 'https://kurdsaz.com/details?slug=100-Days-of-Deception',
      'https://kurdsaz.com/watch?slug=100-Days-of-Deception&ep=899', 'https://kurdsaz.com/search'],
    ask: ['/', '/browse?type=movie', '/browse?type=series', '/details?slug=Crystal-Lake', '/details?slug=100-Days-of-Deception',
      '/watch?slug=100-Days-of-Deception&ep=899', '/search?q=spider'],
  },
  kartonikurde: {
    start: 'https://www.kartonikurde.com/',
    pages: ['https://www.kartonikurde.com/movies', 'https://www.kartonikurde.com/series',
      'https://www.kartonikurde.com/movie/frozen', 'https://www.kartonikurde.com/serie/grendizer'],
    ask: ['/', '/movies', '/series', '/movie/frozen', '/serie/grendizer', '/search?q=frozen'],
  },
};
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
  await page.goto(address, { waitUntil: 'domcontentloaded', timeout: 45000 }).catch((e) => note('goto', e.message.split('\n')[0]));
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(2500);
  const html = await page.content().catch(() => '');
  fs.writeFileSync(path.join(OUT, name + '.html'), html.slice(0, 600000));
  snip(html);
  note('PAGE', name, page.url(), JSON.stringify(await page.title().catch(() => '')), html.length + ' characters');
  return html;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, userAgent: UA, locale: 'ckb-IQ' });
  const page = await context.newPage();
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
})().catch((e) => note('STOPPED', e.stack)).finally(() => {
  fs.writeFileSync(path.join(OUT, 'data.txt'), data.join('\n'));
  fs.writeFileSync(path.join(OUT, 'snippets.txt'), Array.from(snippets).join('\n\n'));
  fs.writeFileSync(path.join(OUT, 'probe.log'), log.join('\n') + '\n');
  process.exit(0);
});
