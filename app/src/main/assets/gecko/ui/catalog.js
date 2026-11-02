// The websites asked, as the screens need them (each by its reader, ui/readers/): their home lists, kept a while so
// the home comes at once (asked again after half an hour); a search on all of them, each one's answer shown as it
// comes; a title in full from each of its websites, and a season's episodes (both kept a while: the page comes back
// at once after a film); and playing, on the website. How each website answered last, for Settings.
'use strict';
TM.catalog = (function () {
  const FRESH = 30 * 60 * 1000;
  const health = {};   // a website's id: 'ok', or 'failed'
  const memo = {};     // asked this time

  function ask(site, f) {
    return Promise.resolve().then(f).then((v) => {
      health[site] = 'ok';
      return v;
    }, (e) => {
      health[site] = 'failed';
      console.warn('Twilight Movies, ' + site + ': ' + ((e && e.message) || e));
      throw e;
    });
  }

  // Kept by the box a while (list: its name in TM.store): the newest 40
  function recall(list, key, age) {
    if (memo[list + key]) return memo[list + key];
    const x = TM.store.get(list, {})[key];
    return x && Date.now() - x.at < age ? x.value : null;
  }
  function keep(list, key, value) {
    memo[list + key] = value;
    const all = TM.store.get(list, {});
    all[key] = { at: Date.now(), value };
    Object.keys(all).sort((a, b) => all[b].at - all[a].at).slice(40).forEach((k) => delete all[k]);
    TM.store.set(list, all);
  }

  const kept = () => TM.store.get('home', { at: 0, sites: {} });

  /** The home's lists as kept: each website's; old when it's half an hour old, or a website's are missing. */
  function homeKept() {
    const k = kept();
    const sites = k.sites || {};
    return { sites, old: Date.now() - (k.at || 0) > FRESH || TM.saved.readers().some((r) => !sites[r.id]) };
  }

  /** Every website's home lists asked again, each kept as it comes (each: site, lists); then all of them. */
  function homeFresh(each) {
    return Promise.all(TM.saved.readers().map((r) => ask(r.id, () => r.rows(TM.web)).then((rows) => {
      const k = kept();
      k.sites = Object.assign({}, k.sites, { [r.id]: rows.map((x) => ({ key: x.key, items: x.items.slice(0, 40) })) });
      k.at = Date.now();
      TM.store.set('home', k);
      if (each) each(r.id, rows);
    }, () => {}))).then(() => homeKept().sites);
  }

  /** Websites' lists (sites: id → [{key, items}]) as titles, by list: a title from each website in turn. */
  function lists(sites) {
    const s = TM.saved.settings();
    const ids = TM.saved.readers().map((r) => r.id);
    const p = TM.merge.pile();
    const keys = [];
    ids.forEach((id) => (sites[id] || []).forEach((row) => { if (!keys.includes(row.key)) keys.push(row.key); }));
    const rows = {};
    keys.forEach((key) => {
      const each = ids.map((id) => ((sites[id] || []).find((r) => r.key === key) || { items: [] }).items);
      const seen = new Set();
      rows[key] = TM.merge.turns(each).map(p.add).filter((e) => e && !seen.has(e) && seen.add(e));
    });
    const all = p.list();
    all.forEach((e) => TM.merge.order(e, s));
    return { rows, all };
  }

  let searching = 0;
  /** A search on every website: found(titles, answered, asked) each time one answers (a newer search stops it). */
  function search(q, found) {
    const n = ++searching;
    const readers = TM.saved.readers();
    const got = {};
    let answered = 0;
    readers.forEach((r) => {
      ask(r.id, () => r.search(TM.web, q)).then((items) => { got[r.id] = items; }, () => {}).then(() => {
        answered++;
        if (n !== searching) return;
        const p = TM.merge.pile();
        TM.merge.turns(readers.map((x) => got[x.id] || [])).forEach(p.add);
        found(p.list().map((e) => TM.merge.order(e, TM.saved.settings())), answered, readers.length);
      });
    });
    if (!readers.length) found([], 0, 0);
  }

  /** A title in full from each of its websites (those on): each(entry) as each answers; the entry once all have. */
  function title(entry, each) {
    const s = TM.saved.settings();
    const asked = entry.items.filter((x) => TM.readers[x.site] && !s.off[x.site]).map((x) => {
      const key = x.site + '|' + x.ref;
      const known = recall('titles', key, 6 * 3600 * 1000);
      const full = known ? Promise.resolve(known)
        : ask(x.site, () => TM.readers[x.site].title(TM.web, x.ref)).then((t) => { keep('titles', key, t); return t; });
      return full.then((t) => {
        Object.assign(x, t, { site: x.site, ref: x.ref });
        if (each) each(entry);
      }, () => {});
    });
    return Promise.all(asked).then(() => entry);
  }

  /** A season's episodes on one of its websites. */
  function episodes(item, season) {
    const key = item.site + '|' + item.ref + '|' + season;
    const known = recall('seasons', key, 6 * 3600 * 1000);
    if (known) return Promise.resolve(known);
    return ask(item.site, () => TM.readers[item.site].episodes(TM.web, item.ref, season)).then((eps) => {
      keep('seasons', key, eps);
      return eps;
    });
  }

  /** A series' episode to play on that website: where it was left (that one again), else the first. */
  async function next(entry, item) {
    const last = TM.saved.lastOf(entry);
    let season = last && last.season ? last.season : 0;
    if (!season) {
      await title({ kind: entry.kind, items: [item] });
      season = ((item.seasons || [])[0] || {}).n || 1;
    }
    const eps = await episodes(item, season).catch(() => []);
    const ep = (last && last.season === season && eps.find((e) => e.n === last.episode)) || eps[0];
    return Object.assign({ season }, ep || { n: last && last.season === season ? last.episode : 1 });
  }

  /** Plays it, on the website (the box keeps what was watched first, and the title with all its websites). */
  async function play(entry, item, ep) {
    const episode = entry.kind === 'series' ? ep || await next(entry, item) : null;
    TM.saved.watch(entry, item, episode);
    TM.saved.keep(entry);
    TM.app.leave(TM.readers[item.site].watch(item.ref, episode || undefined));
  }

  return { health, homeKept, homeFresh, lists, search, title, episodes, next, play };
})();
