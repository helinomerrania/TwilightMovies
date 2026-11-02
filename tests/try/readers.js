// The app's readers (app/src/main/assets/gecko/ui/readers/) against the live websites, on GitHub's machines (by
// .github/workflows/readers.yml): each one's home lists, a search, a film and a series in full, a season's
// episodes and the addresses that play them, as the app's screens would ask. What each gave goes to
// <site>.json, every address asked (with its answer's start) to asked.txt, the pages (HTML) whole to <site>-page-NN.html,
// and what went wrong to readers.log.
// Usage: node tests/try/readers.js OUT_DIR [site-id ...]
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const OUT = process.argv[2];
const UI = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'gecko', 'ui');
const UA = 'Mozilla/5.0 (Android 9; Mobile; rv:140.0) Gecko/140.0 Firefox/140.0';
const log = [];
const asked = [];
const note = (...a) => { const line = a.join(' '); log.push(line); console.log(line); };

// The readers, in a context of their own, as the app's page loads them
const ctx = vm.createContext({ console, URL, URLSearchParams, setTimeout, clearTimeout, AbortController });
['tm.js'].concat(fs.readdirSync(path.join(UI, 'readers')).map((f) => 'readers/' + f)).forEach((f) => {
  vm.runInContext(fs.readFileSync(path.join(UI, f), 'utf8'), ctx, { filename: f });
});
const TM = vm.runInContext('TM', ctx);

// The websites asked from here, as the app's page asks them (its cookies kept, as a browser keeps them); the
// pages (HTML) kept whole, page-NN.html, for the tests' saved copies
const jar = {};
const pages = {};   // how many of each website's were kept
let site = '';
const web = {
  async get(url, o) {
    o = o || {};
    const host = new URL(url).host;
    const headers = Object.assign({ 'User-Agent': UA, 'Accept-Language': 'ckb,en;q=0.8' }, o.headers || {});
    if (jar[host]) headers.Cookie = jar[host];
    const started = Date.now();
    const res = await fetch(url, { method: o.method || 'GET', headers, body: o.body, signal: AbortSignal.timeout(o.timeout || 20000) });
    const set = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
    if (set.length) jar[host] = set.map((c) => c.split(';')[0]).join('; ');
    const text = await res.text();
    pages[site] = pages[site] || 0;
    if (/^\s*</.test(text) && pages[site] < 8) {
      fs.writeFileSync(path.join(OUT, site + '-page-' + String(++pages[site]).padStart(2, '0') + '.html'), url + '\n' + text);
    }
    asked.push(['=== ' + (o.method || 'GET'), res.status, url, (Date.now() - started) + ' ms', text.length + ' characters'].join(' | ')
      + (o.body ? '\n--- sent: ' + String(o.body).slice(0, 500) : '') + '\n' + text.slice(0, 1500) + '\n');
    if (!res.ok) throw new Error(url + ' answered ' + res.status);
    return o.json ? JSON.parse(text) : text;
  },
  json(url, o) { return web.get(url, Object.assign({ json: true }, o, { headers: Object.assign({ Accept: 'application/json, text/plain, */*' }, (o || {}).headers) })); },
  text(url, o) { return web.get(url, o); },
};

async function step(out, name, f) {
  const started = Date.now();
  try {
    out[name] = await f();
    note('ok  ', out.site, name, (Date.now() - started) + ' ms', JSON.stringify(out[name]).length + ' characters');
  } catch (e) {
    out[name] = { error: String(e && e.message || e) };
    note('FAIL', out.site, name, out[name].error);
  }
  return out[name];
}

const short = (list) => (Array.isArray(list) ? { count: list.length, first: list.slice(0, 3) } : list);

// What each website is searched for (one it has)
const WORD = { kartonikurde: 'frozen', kurdsaz: 'love' };

async function tryReader(r) {
  site = r.id;
  const out = { site: r.id };
  const rows = await step(out, 'rows', () => r.rows(web));
  if (Array.isArray(rows)) out.rows = rows.map((x) => ({ key: x.key, count: x.items.length, first: x.items.slice(0, 3) }));
  const all = Array.isArray(rows) ? [].concat(...rows.map((x) => x.items)) : [];
  out.search = short(await step(out, 'search', () => r.search(web, WORD[r.id] || 'spider')));
  const film = all.find((x) => x.kind === 'film');
  const series = all.find((x) => x.kind === 'series');
  if (film) {
    await step(out, 'film', () => r.title(web, film.ref));
    out.watchFilm = r.watch(film.ref);
  }
  if (series) {
    const t = await step(out, 'series', () => r.title(web, series.ref));
    const first = (t && t.seasons && t.seasons[0]) ? t.seasons[0].n : 1;
    const eps = await step(out, 'episodes', () => r.episodes(web, series.ref, first));
    if (Array.isArray(eps)) out.episodes = { count: eps.length, first: eps.slice(0, 4) };
    out.watchEpisode = r.watch(series.ref, Array.isArray(eps) && eps[0] ? Object.assign({ season: first }, eps[0]) : { season: first, n: 1 });
  }
  fs.writeFileSync(path.join(OUT, r.id + '.json'), JSON.stringify(out, null, 1));
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const ids = process.argv.slice(3).length ? process.argv.slice(3) : Object.keys(TM.readers);
  for (const id of ids) await tryReader(TM.readers[id]);
})().catch((e) => note('STOPPED', e.stack)).finally(() => {
  fs.writeFileSync(path.join(OUT, 'asked.txt'), asked.join('\n'));
  fs.writeFileSync(path.join(OUT, 'readers.log'), log.join('\n') + '\n');
});
