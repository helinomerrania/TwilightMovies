// What a website has for the app's own screens, and where its pages get it from (on GitHub, by
// .github/workflows/survey.yml): its first page, a search, a film and its player, a series and one of its
// episodes. Each step keeps a screenshot, the page's HTML as it ends up and its links (NN-step.png, .html,
// -links.txt); data.txt keeps every answer to the page's own requests for data (JSON and the like, as it came,
// with the step, the address and what was sent); api.txt the addresses of data written in its scripts and pages.
// Usage: node tests/try/survey.js OUT_DIR address [words to search for]
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const [OUT, START, WORDS = 'spider'] = process.argv.slice(2);
const UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';
const BASE = new URL(START).hostname.split('.').slice(-2).join('.');
// Where a website keeps its films, series and episodes, and its search, where that's known (else GENERAL's)
const HINTS = {
  'beenar.net': { film: /\/\d+\?series=false/, series: /\/\d+\?series=true/ },
  'kurdcinama.com': { film: /[?&]movieid=\d+/, seriesList: '/drama.aspx', series: /[?&]movieid=\d+/ },
  'kurddoblazh.com': { film: /\/\d{4}\/\d{2}\/[^/]+\.html/, search: '/search?q={q}' },
  'kurdsaz.com': { filmList: '/browse?type=movie', film: /details\?slug=/, seriesList: '/browse?type=series',
    series: /details\?slug=/, episode: /watch\?slug=[^&]+&ep=/ },
  'krd.watch': { film: /\/movie\/\d+/, series: /\/tv\/\d+/, episode: /\/watch\/tv\//, search: '/search?q={q}' },
  'kartonikurde.com': { film: /\/movie\/[^/?#]+$/, series: /\/serie\/[^/?#]+$/ },
  'kurd-movie.com': { film: /\/movies\/[^/?#]+\/?$/, series: /\/tvshows\/[^/?#]+\/?$/,
    episode: /\/episodes\/[^/?#]+\/?$/, search: '/?s={q}' },
};
const GENERAL = {
  film: /\/(movies?|films?|watch|video)\/[^/?#]+|[?&](movie|film)(_?id)?=\w+/i,
  series: /\/(series|serie|tvshows?|tv|shows?|serials?|drama)\/[^/?#]+|[?&](series|show|tv)(_?id)?=\w+/i,
  episode: /\/(episodes?|ep)\/[^/?#]+|[?&](episode|ep)(_?id)?=\w+/i,
};
const hint = Object.assign({}, GENERAL, HINTS[BASE] || {});
const PLAY = /پەخش|سەیرکردن|سەیربکە|تەماشا|play|watch|▶/i;
const SERIES_TEXT = /زنجیرە|series|drama|دراما|tv shows/i;
const EPISODE_TEXT = /ئەڵقە|ئەلقە|episode|\bep\.?\s*\d/i;
const FIELD = 'input[type=search], input[name=q], input[name=s], input[name=search], input[name=query], input[name=keyword], '
  + 'input[id*=search i], input[class*=search i], input[placeholder*=search i], input[placeholder*="گەڕان"]';
const OPENER = 'a[href*=search], [aria-label*=search i], button[class*=search i], [class*=search i] > i, [class*=search-toggle i]';
const API = /["'`]((?:https?:\/\/[\w.-]+)?\/(?:api|wp-json|wp-admin\/admin-ajax|ajax|livewire|graphql|feeds)\b[^"'`\s<>]{0,150})["'`]/g;

const log = [];
const note = (...a) => { const line = a.join(' '); log.push(line); console.log(line); };
const data = [];
const api = new Set();
let size = 0;
let now = '01-home';

function scan(text) {
  for (const m of text.matchAll(API)) api.add(m[1]);
}

function listen(page) {
  page.on('response', async (r) => {
    const q = r.request();
    const kind = q.resourceType();
    if (kind === 'script') return scan(await r.text().catch(() => ''));
    if (kind !== 'xhr' && kind !== 'fetch') return;
    const body = await r.text().catch(() => null);
    if (body === null || size > 8e6) return;
    const sent = q.postData();
    const text = body.slice(0, 60000);
    size += text.length;
    data.push('=== ' + [now, q.method(), r.status(), q.url(), r.headers()['content-type'] || ''].join(' | ')
      + (sent ? '\n--- sent: ' + sent.slice(0, 3000) : '') + '\n' + text + '\n');
  });
  page.on('framenavigated', (f) => note(f === page.mainFrame() ? 'PAGE' : 'FRAME', f.url().slice(0, 300)));
  page.on('pageerror', (e) => note('PAGE ERROR', e.message.slice(0, 300)));
  page.on('dialog', (d) => d.dismiss().catch(() => {}));
  page.on('popup', (p) => { note('POPUP', p.url()); p.close().catch(() => {}); });
}

/** The page as it ends up: screenshot, HTML, links (as [address, text]) */
async function save(page, name) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(OUT, name + '.png') }).catch(() => {});
  const html = await page.content().catch(() => '');
  fs.writeFileSync(path.join(OUT, name + '.html'), html);
  scan(html);
  const links = await page.$$eval('a[href]', (as) => as.map((a) => [a.href,
    (a.innerText || a.title || a.getAttribute('aria-label') || '').replace(/\s+/g, ' ').trim().slice(0, 80)])).catch(() => []);
  fs.writeFileSync(path.join(OUT, name + '-links.txt'), links.map((l) => l.join('  ')).join('\n'));
  note('STEP', name, page.url(), JSON.stringify(await page.title().catch(() => '')));
  return links;
}

async function go(page, name, address) {
  now = name;
  note('GO', name, address);
  await page.goto(address, { waitUntil: 'domcontentloaded', timeout: 45000 }).catch((e) => note('goto', e.message.split('\n')[0]));
  return save(page, name);
}

const own = (h) => { try { return new URL(h).hostname.endsWith(BASE); } catch (e) { return false; } };
/** The first of the website's own links whose address matches re (and text matches o.text), but not o.not or o.except */
function pick(links, re, o) {
  o = o || {};
  const ok = links.find(([h, t]) => own(h) && (!re || re.test(h)) && (!o.text || o.text.test(t))
    && !(o.not && o.not.test(h)) && h !== o.except);
  return ok ? ok[0] : null;
}

async function visible(page, selector, re) {
  for (const f of page.frames()) {
    const all = f.locator(selector);
    const n = Math.min(await all.count().catch(() => 0), 300);
    for (let i = 0; i < n; i++) {
      const el = all.nth(i);
      if (!(await el.isVisible().catch(() => false))) continue;
      if (!re) return el;
      const text = (await el.innerText().catch(() => '')) + ' ' + (await el.getAttribute('aria-label').catch(() => '') || '')
        + ' ' + (await el.getAttribute('class').catch(() => '') || '');
      if (re.test(text)) return el;
    }
  }
  return null;
}

const outer = (el) => el.evaluate((e) => e.outerHTML.slice(0, 300).replace(/\s+/g, ' ')).catch(() => '');

async function search(page) {
  now = '02-search';
  let field = await visible(page, FIELD);
  if (!field) {
    const opener = await visible(page, OPENER);
    if (opener) {
      note('SEARCH OPENER', await outer(opener));
      await opener.click({ timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(2000);
      field = await visible(page, FIELD);
    }
  }
  if (field) {
    note('SEARCH FIELD', await outer(field));
    await field.click({ timeout: 5000 }).catch(() => {});
    await field.pressSequentially(WORDS, { delay: 150 }).catch((e) => note('typing', e.message.split('\n')[0]));
    await page.waitForTimeout(3000);
    await save(page, '02-typed');
    await field.press('Enter').catch(() => {});
    const found = await save(page, '02-search');
    if (!hint.search) return found;
  }
  if (hint.search) return go(page, '02-search-address', new URL(hint.search.replace('{q}', encodeURIComponent(WORDS)), START).href);
  note('NO SEARCH');
  return [];
}

async function play(page, name) {
  now = name;
  const el = await visible(page, 'button, a, [role="button"], [class*="play" i], [id*="play" i]', PLAY);
  if (el) {
    note('PLAY', await outer(el));
    await el.click({ timeout: 8000 }).catch((e) => note('click failed', e.message.split('\n')[0]));
  } else {
    note('NO PLAY BUTTON');
  }
  await page.waitForTimeout(12000);
  await save(page, name);
  const frames = page.frames().filter((f) => f !== page.mainFrame()).map((f) => f.url());
  fs.writeFileSync(path.join(OUT, name + '-frames.txt'), frames.join('\n'));
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, userAgent: UA, locale: 'ckb-IQ' });
  const page = await context.newPage();
  listen(page);
  const home = await go(page, '01-home', START);
  const found = await search(page);
  // A film: from the first page (or the website's list of films), else from what the search found
  const films = hint.filmList ? await go(page, '03-films', new URL(hint.filmList, START).href) : home;
  const film = pick(films, hint.film, { not: hint.series === hint.film ? null : hint.series })
    || pick(found, hint.film, { not: hint.series === hint.film ? null : hint.series });
  if (film) {
    await go(page, '03-film', film);
    await play(page, '04-player');
  } else {
    note('NO FILM');
  }
  // A series: from the first page, else the website's list of series (where it's known, or a link that says so)
  const list = hint.seriesList ? new URL(hint.seriesList, START).href
    : pick(home, hint.series, { except: film }) ? null : pick(home, null, { text: SERIES_TEXT });
  const shows = list ? await go(page, '05-series-list', list) : home;
  const series = pick(shows, hint.series, { except: film });
  if (!series) return note('NO SERIES');
  const seasons = await go(page, '05-series', series);
  const episode = pick(seasons, hint.episode, { except: series }) || pick(seasons, null, { text: EPISODE_TEXT, except: series });
  if (!episode) return note('NO EPISODE');
  await go(page, '06-episode', episode);
  await play(page, '07-episode-player');
  await browser.close();
})().catch((e) => note('STOPPED', e.stack)).finally(() => {
  fs.writeFileSync(path.join(OUT, 'data.txt'), data.join('\n'));
  fs.writeFileSync(path.join(OUT, 'api.txt'), Array.from(api).sort().join('\n') + '\n');
  fs.writeFileSync(path.join(OUT, 'survey.log'), log.join('\n') + '\n');
  process.exit(0);
});
