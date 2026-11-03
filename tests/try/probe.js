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
  // The study on the emulator saw these two ask /api/titles/... (Mars Cinema) and api.kurdsubtitle.net/api/v1/...
  // (Kurd Subtitle); their pages' own requests and scripts say the rest
  marscinema: {
    start: 'https://marscinema.net/',
    pages: ['https://marscinema.net/movies', 'https://marscinema.net/series', 'https://marscinema.net/search?q=spider'],
    ask: ['/api/titles/live-top', '/api/titles', '/api/titles?type=movie', '/api/titles?type=series',
      '/api/titles/latest', '/api/search?q=spider'],
  },
  kurdsubtitle: {
    // a series' episodes (Lupin's, by its own id and by its address's name), its lists and its search
    direct: ['https://api.kurdsubtitle.net/api/v1/tvshows/60005e7b64d2e47956145ba7/seasons/1/episodes',
      'https://api.kurdsubtitle.net/api/v1/tvshows/467300-Lupin/seasons/1/episodes',
      'https://api.kurdsubtitle.net/api/v1/tvshows/60005e7b64d2e47956145ba7', 'https://api.kurdsubtitle.net/api/v1/movies?page=1',
      'https://api.kurdsubtitle.net/api/v1/tvshows?page=1', 'https://api.kurdsubtitle.net/api/v1/search?q=spider',
      'https://kurdsubtitle.net/movies', 'https://kurdsubtitle.net/tvshows/467300-Lupin'],
    // its series page's scripts: its episodes come in the viewer's browser, from where they say
    scripts: ['/_next/static/chunks/0e5ce63c-c8c4232ec35b660a.js',
      '/_next/static/chunks/1055-295a9ec4eb73d2b7.js',
      '/_next/static/chunks/1255-a64e6a44d24cb2f6.js',
      '/_next/static/chunks/1351-eda1d500cabc9c13.js',
      '/_next/static/chunks/1514-7104fc1d6cd8efb2.js',
      '/_next/static/chunks/1691-0d641d4ab66863e7.js',
      '/_next/static/chunks/16dc9863-4a50cf98c2b27890.js',
      '/_next/static/chunks/1812-508a629d53460619.js',
      '/_next/static/chunks/191-7bfb387751127963.js',
      '/_next/static/chunks/2619-df0b05096f172bf7.js',
      '/_next/static/chunks/2650-46e93507e53e4a62.js',
      '/_next/static/chunks/2660-2f745528300eb612.js',
      '/_next/static/chunks/30a37ab2-9b8acb2a07381dd6.js',
      '/_next/static/chunks/3371-1d9f9eeeb6e18d17.js',
      '/_next/static/chunks/4347-ae6358066554b45c.js',
      '/_next/static/chunks/4457-f04e96331eb7917f.js',
      '/_next/static/chunks/4577-fe0275c9c3d71273.js',
      '/_next/static/chunks/4628-425c7d358f61801c.js',
      '/_next/static/chunks/4823-73e9a41fb7720cce.js',
      '/_next/static/chunks/4988-6de9b8b3c72e8d34.js',
      '/_next/static/chunks/4bd1b696-100b9d70ed4e49c1.js',
      '/_next/static/chunks/5059-30ebec42879984ef.js',
      '/_next/static/chunks/5125-6dea62ec41269dc1.js',
      '/_next/static/chunks/5408-c071d2a30b437863.js',
      '/_next/static/chunks/5414-5ed60d3ea0e4df99.js',
      '/_next/static/chunks/5667-030dacfc3b0a8eb9.js',
      '/_next/static/chunks/5682-f1a55780f73119e2.js',
      '/_next/static/chunks/5907-cb4bd7186620bc0d.js',
      '/_next/static/chunks/7913-27c6b82f80a5284c.js',
      '/_next/static/chunks/8657-5a46d0d1371917b1.js',
      '/_next/static/chunks/8720-620d52e50cfa914f.js',
      '/_next/static/chunks/9138-f5ca7413fbbbd050.js',
      '/_next/static/chunks/94730671-fa9ac1cf0535af1a.js',
      '/_next/static/chunks/9601-7c72b8044ae1bfe3.js',
      '/_next/static/chunks/9825-09a8479b7b5c5cec.js',
      '/_next/static/chunks/a4634e51-c4bd651267baabfd.js',
      '/_next/static/chunks/app/error-f18ab9f02ef7d458.js',
      '/_next/static/chunks/app/global-error-d74b955f20861b49.js',
      '/_next/static/chunks/app/layout-84c9689e2779c953.js',
      '/_next/static/chunks/app/tvshows/%5Bslug%5D/page-1139efafe0247f5c.js',
      '/_next/static/chunks/ed9f2dc4-fb9c3f5325ea0485.js',
      '/_next/static/chunks/f97e080b-a536e50d7fa9f157.js',
      '/_next/static/chunks/main-app-49958bbb287c729c.js',
      '/_next/static/chunks/polyfills-42372ed130431b0a.js',
      '/_next/static/chunks/webpack-37d64f41ae614ed1.js'],
    start: 'https://kurdsubtitle.net/',
    pages: ['https://kurdsubtitle.net/movies', 'https://kurdsubtitle.net/series', 'https://kurdsubtitle.net/search?q=spider'],
    ask: ['https://api.kurdsubtitle.net/api/v1/genres?excludeManga=true',
      'https://api.kurdsubtitle.net/api/v1/watching/most-watched?limit=3', 'https://api.kurdsubtitle.net/api/v1/movies?limit=3',
      'https://api.kurdsubtitle.net/api/v1/series?limit=3', 'https://api.kurdsubtitle.net/api/v1/search?q=spider'],
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
