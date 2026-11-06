// The titles' original pictures (app/src/main/assets/gecko/ui/pictures.js) against TMDB itself, on GitHub's machines
// (by .github/workflows/readers.yml, with the project's secret TMDB_KEY), after the readers' try: of each website's
// titles that have pictures of their own (its <site>-titles.json), what TMDB finds for each, by the name and year it
// gives it, so that it can be seen to be the same title. To pictures.json, and how many it found to pictures.log
// (TMDB's addresses in neither: they have the key). Usage: TMDB_KEY=... node tests/try/pictures.js OUT_DIR
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const OUT = process.argv[2];
const UI = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'gecko', 'ui');
const EACH = 25;   // each website's titles asked at most
const log = [];
const note = (...a) => { const line = a.join(' '); log.push(line); console.log(line); };

const ctx = vm.createContext({ console, URL, setTimeout, clearTimeout });
['tm.js', 'keys.js', 'words.js', 'merge.js', 'pictures.js'].forEach((f) => {
  vm.runInContext(fs.readFileSync(path.join(UI, f), 'utf8'), ctx, { filename: f });
});
const TM = vm.runInContext('TM', ctx);
TM.keys.tmdb = (process.env.TMDB_KEY || '').trim();

// TMDB asked as the app's page asks it
const web = {
  async json(url, o) {
    const res = await fetch(url, { headers: (o && o.headers) || {}, signal: AbortSignal.timeout(10000) });
    if (!res.ok) throw new Error('TMDB answered ' + res.status);
    return res.json();
  },
};

(async () => {
  if (!TM.keys.tmdb) {
    note('SKIP no TMDB key (the project\'s secret TMDB_KEY)');
    return;
  }
  const out = [];
  for (const f of fs.readdirSync(OUT).filter((x) => /-titles\.json$/.test(x)).sort()) {
    const site = f.replace(/-titles\.json$/, '');
    const titles = JSON.parse(fs.readFileSync(path.join(OUT, f), 'utf8'))
      .filter((x) => !/^https:\/\/image\.tmdb\.org\//.test(x.poster || '') && TM.pictures.keyOf({ kind: x.kind, items: [x] }))
      .slice(0, EACH);
    let found = 0;
    for (const x of titles) {
      try {
        const t = await TM.pictures.ask(web, { kind: x.kind, items: [x] });
        if (t) found++;
        out.push({ site, kind: x.kind, title: x.title, year: x.year || 0, tmdb: t ? [t.title || t.name,
          String(t.release_date || t.first_air_date || '').slice(0, 4), t.poster_path].join(' · ') : null });
      } catch (e) {
        note('FAIL', site, x.title, e.message);
        if (/ 401$/.test(e.message)) return;   // the key refused: nothing more to ask
      }
    }
    note('ok  ', site + ':', found, 'of', titles.length, 'titles with pictures of their own found on TMDB');
  }
  fs.writeFileSync(path.join(OUT, 'pictures.json'), JSON.stringify(out, null, 1));
})().catch((e) => note('STOPPED', e.stack)).finally(() => fs.writeFileSync(path.join(OUT, 'pictures.log'), log.join('\n') + '\n'));
