// One title from many websites: the same film or series on two websites is one card, both websites behind it. The
// same is: TMDB's number, else the name (its letters and numbers, small) and the kind, a year apart at most. What a
// title shows comes from its websites in their order (the first that has it), in the screens' language; which
// website plays first, by the viewer's choice (Kurdish subtitles, dubbing or the best picture), then their order.
'use strict';
TM.merge = (function () {
  const normal = (t) => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/&/g, ' and ').replace(/\b(doblaj|dublaj|dubbed)\b/g, ' ').replace(/[^a-z0-9؀-ۿ]+/g, ' ').trim();
  const yearOf = (e) => (e.items.find((x) => x.year) || {}).year || 0;
  const near = (e, item) => { const y = yearOf(e); return !y || !item.year || Math.abs(y - item.year) <= 1; };

  /** Titles as they come (add), each item with its own title or a new one; list(): in the order they first came. */
  function pile() {
    const all = [];
    const byName = {};
    const byTmdb = {};
    function add(item) {
      if (!item || !item.ref) return null;
      const name = item.kind + ':' + normal(item.title || item.titleKu);
      let e = item.tmdb ? byTmdb[item.kind + ':' + item.tmdb] : null;
      if (!e) e = (byName[name] || []).find((x) => near(x, item));
      if (!e) {
        e = { kind: item.kind, items: [] };
        all.push(e);
        (byName[name] = byName[name] || []).push(e);
      }
      if (item.tmdb) byTmdb[item.kind + ':' + item.tmdb] = e;
      const had = e.items.findIndex((x) => x.site === item.site && x.ref === item.ref);
      if (had < 0) e.items.push(item);
      else e.items[had] = Object.assign({}, e.items[had], item);   // the same again, maybe in full now
      return e;
    }
    return { add, list: () => all };
  }

  /** Lists of several websites as one, a title from each in turn (lists: in the websites' order). */
  function turns(lists) {
    const out = [];
    const longest = Math.max(0, ...lists.map((l) => l.length));
    for (let i = 0; i < longest; i++) lists.forEach((l) => { if (l[i]) out.push(l[i]); });
    return out;
  }

  /** A title kept by the box (TM.saved), as a title again. */
  const again = (kept) => ({ kind: kept.kind, items: (kept.items || []).slice() });

  /** The first of its websites' that has it (the websites in their order). */
  function first(entry, field) {
    for (const x of entry.items) {
      const v = x[field];
      if (v !== undefined && v !== null && v !== '' && !(Array.isArray(v) && !v.length)) return v;
    }
    return undefined;
  }

  const has = (entry, lang) => entry.items.some((x) => (x.langs || []).includes(lang));
  const cartoon = (entry) => entry.items.some((x) => (x.genres || []).includes('Animation')
    || (x.genresKu || []).some((g) => /ئەنیم|کارتۆن/.test(g)));

  /** What a title shows, in the screens' language L (TM.language). */
  function show(entry, L) {
    const titleKu = first(entry, 'titleKu');
    const title = first(entry, 'title');
    const name = (L.ku ? titleKu || title : title || titleKu) || '';
    const english = first(entry, 'genres') || [];
    const genres = L.ku ? (english.length ? english.map((g) => TM.genresKu[g] || g) : first(entry, 'genresKu') || []) : english;
    const overview = (L.ku ? first(entry, 'overviewKu') || first(entry, 'overview')
      : first(entry, 'overview') || first(entry, 'overviewKu')) || '';
    const langs = ['sub', 'dub'].filter((l) => has(entry, l));
    return {
      name, original: L.ku && titleKu && title && normal(titleKu) !== normal(title) ? title : '',
      year: first(entry, 'year'), minutes: first(entry, 'minutes'), rating: first(entry, 'rating'), age: first(entry, 'age'),
      genres: genres.filter((g, i) => genres.indexOf(g) === i).slice(0, 3), overview, tagline: first(entry, 'tagline'),
      overviewDir: /[؀-ۿ]/.test(overview) ? 'rtl' : 'ltr', nameDir: /[؀-ۿ]/.test(name) ? 'rtl' : 'ltr',
      poster: first(entry, 'poster'), backdrop: first(entry, 'backdrop'), langs,
      sites: entry.items.map((x) => x.site).filter((s, i, all) => all.indexOf(s) === i).length,
    };
  }

  // Picture quality, best first, as the websites write it
  const QUALITY = { '4K': 0, '2160p': 0, UHD: 0, QHD: 1, '1440p': 1, '1080p': 1, FHD: 1, HD: 2, '720p': 2 };

  /** Its websites (those on), the one that plays first at the top: by the viewer's choice, then the websites' order. */
  function sources(entry, s) {
    const rank = (x) => s.order.indexOf(x.site);
    const score = (x) => (s.first === 'best' ? (x.quality in QUALITY ? QUALITY[x.quality] : 3)
      : (x.langs || ['sub']).includes(s.first) ? 0 : 1);
    return entry.items.filter((x) => TM.readers[x.site] && !s.off[x.site]).slice()
      .sort((a, b) => score(a) - score(b) || rank(a) - rank(b));
  }

  /** Its websites in their order (what it shows comes from the first): after a title's items came in full. */
  function order(entry, s) {
    entry.items.sort((a, b) => s.order.indexOf(a.site) - s.order.indexOf(b.site));
    return entry;
  }

  return { normal, pile, turns, again, first, has, cartoon, show, sources, order };
})();
