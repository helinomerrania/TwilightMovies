// The titles' original pictures (the poster, and the wide one) from TMDB, The Movie Database (themoviedb.org), for a
// title none of its websites gives TMDB's for (KRD WATCH and Kurd Movie give them; the others, pictures of their
// own): TMDB asked once a title, by its English name, kind and year, and what it found kept on the box (one it
// didn't find: asked again a week later). Only with the app's TMDB key (keys.js), a few at a time. What a title
// shows (merge.js) takes these before a website's own.
'use strict';
TM.pictures = (function () {
  const API = 'https://api.themoviedb.org/3/search/';
  const IMG = 'https://image.tmdb.org/t/p/w500';
  const TMDB = /^https:\/\/image\.tmdb\.org\//;
  const WEEK = 7 * 24 * 3600 * 1000;   // one TMDB didn't find: asked again after a week
  const MOST = 2000;                   // titles kept at most (the ones asked longest ago go)
  const AT_ONCE = 4;
  const kept = TM.store.get('pictures', {});   // a title's key: [poster, wide picture (TMDB's paths, '' none), when]
  const asked = {};     // the titles asked since the screens opened (one not answered: not again till they open again)
  const jobs = {};      // the titles being asked, or waiting to be: what to do once they're found
  const waiting = [];
  let busy = 0;
  let refused = false;  // TMDB refused the key: nothing more asked till the screens open again
  let saving = 0;

  const key = () => (TM.keys && TM.keys.tmdb) || '';
  const english = (entry) => (entry.items.find((x) => x.title && !/[؀-ۿ]/.test(x.title)) || {}).title || '';
  const yearOf = (entry) => (entry.items.find((x) => x.year) || {}).year || 0;
  const tmdbs = (entry) => entry.items.some((x) => TMDB.test(x.poster || ''));

  /** A title's key: its kind, English name and year (TMDB is asked once for it, whichever websites it's on). */
  function keyOf(entry) {
    const name = TM.merge.normal(english(entry));
    return name ? entry.kind + ':' + name + ':' + yearOf(entry) : '';
  }

  /** Its pictures as TMDB has them, if it was asked already and found it: {poster, backdrop} (addresses), else null. */
  function known(entry) {
    const x = kept[keyOf(entry)];
    return x && x[0] ? { poster: IMG + x[0], backdrop: x[1] ? IMG + x[1] : '' } : null;
  }

  /**
   * The one of TMDB's that is this title (results: its search's), else null: one with this name (any of its names, as
   * merge.js writes them) a year apart at most; else, the year the same, one whose name has this one in it or is in
   * it ("Breaking Dawn Part 2": "The Twilight Saga: Breaking Dawn - Part 2"). Only one with a poster.
   */
  function pick(results, name, year, tv) {
    const n = TM.merge.normal(name);
    const names = (x) => [x.title, x.original_title, x.name, x.original_name].filter(Boolean).map(TM.merge.normal);
    const yr = (x) => Number(String((tv ? x.first_air_date : x.release_date) || '').slice(0, 4)) || 0;
    const near = (x) => !year || !yr(x) || Math.abs(yr(x) - year) <= 1;
    const within = (a, b) => (' ' + a + ' ').includes(' ' + b + ' ');
    const all = (results || []).filter((x) => x && x.poster_path);
    return all.find((x) => near(x) && names(x).includes(n))
      || (year ? all.find((x) => yr(x) === year && names(x).some((m) => within(m, n) || within(n, m))) : null) || null;
  }

  /** TMDB asked for this title (web: TM.web, or the try's): its search, with its year, else without; what it found. */
  async function ask(web, entry) {
    const tv = entry.kind === 'series';
    const name = english(entry);
    const year = yearOf(entry);
    const k = key();
    const token = /^eyJ/.test(k);   // TMDB's "Read Access Token" works as well as its "API Key"
    const o = { timeout: 10000, headers: Object.assign({ Accept: 'application/json' }, token ? { Authorization: 'Bearer ' + k } : {}) };
    const find = (y) => web.json(API + (tv ? 'tv' : 'movie') + '?query=' + encodeURIComponent(name) + '&include_adult=false'
      + '&language=en-US' + (y ? (tv ? '&first_air_date_year=' : '&year=') + y : '') + (token ? '' : '&api_key=' + encodeURIComponent(k)), o)
      .then((r) => pick(r && r.results, name, year, tv));
    return (year && await find(year)) || find(0);
  }

  /** Its pictures asked of TMDB, if they may be (a key, an English name, none of TMDB's) and aren't known: done() once found. */
  function want(entry, done) {
    const k = keyOf(entry);
    if (!key() || refused || !k || tmdbs(entry)) return;
    const x = kept[k];
    if (x && (x[0] || Date.now() - x[2] < WEEK)) return;
    if (!jobs[k]) {
      if (asked[k]) return;
      asked[k] = true;
      jobs[k] = { entry, done: [] };
      waiting.push(k);
    }
    if (done) jobs[k].done.push(done);
    next();
  }

  function next() {
    while (busy < AT_ONCE && waiting.length && !refused) {
      const k = waiting.shift();
      const job = jobs[k];
      busy++;
      ask(TM.web, job.entry).then((found) => {
        kept[k] = [found ? found.poster_path : '', found ? found.backdrop_path || '' : '', Date.now()];
        save();
        if (found) job.done.forEach((f) => { try { f(); } catch (e) { /* its card is gone */ } });
      }, (e) => { if (/ answered 401$/.test(String(e && e.message))) refused = true; })
        .then(() => { delete jobs[k]; busy--; next(); });
    }
  }

  // What was found, kept on the box a moment later (many come at once)
  function save() {
    if (saving) return;
    saving = setTimeout(() => {
      saving = 0;
      const keys = Object.keys(kept);
      if (keys.length > MOST) keys.sort((a, b) => kept[a][2] - kept[b][2]).slice(0, keys.length - MOST).forEach((k) => { delete kept[k]; });
      TM.store.set('pictures', kept);
    }, 2000);
  }

  return { known, want, ask, pick, keyOf };
})();
