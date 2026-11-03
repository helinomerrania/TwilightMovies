// What the box keeps for the screens (TM.store): the viewer's settings (language, the websites on and their order,
// what plays first, the look), what was watched (one line a title, its last: Continue watching, and where a series
// is), My list, and the titles opened with all their websites (so a title's page is the same after a film). A title
// is known by its websites' own names for it ("krd-watch|movie:634649"): any one of them is enough.
'use strict';
TM.saved = (function () {
  const LOOKS = ['twilight', 'midnight', 'ember'];
  const FIRSTS = ['sub', 'dub', 'best'];
  const KEPT = 40;

  function settings() {
    const s = Object.assign({ lang: '', look: 'twilight', first: 'sub', order: [], off: {} }, TM.store.get('settings', {}));
    const ids = Object.keys(TM.readers);   // a website new to the app: at the end, on
    s.order = s.order.filter((id) => ids.includes(id)).concat(ids.filter((id) => !s.order.includes(id)));
    if (!LOOKS.includes(s.look)) s.look = 'twilight';
    if (!FIRSTS.includes(s.first)) s.first = 'sub';
    return s;
  }

  function change(f) {
    const s = settings();
    f(s);
    TM.store.set('settings', s);
    return s;
  }

  // A title as the box keeps it: what its cards need, from each of its websites
  const SMALL = ['site', 'ref', 'kind', 'title', 'titleKu', 'year', 'poster', 'backdrop', 'langs', 'quality', 'tmdb', 'genres'];
  const small = (item) => TM.item(SMALL.reduce((o, k) => Object.assign(o, { [k]: item[k] }), {}));
  const refs = (entry) => entry.items.map((x) => x.site + '|' + x.ref);
  const same = (a, entry) => { const mine = refs(entry); return (a.refs || []).some((r) => mine.includes(r)); };
  const snapshot = (entry) => ({ kind: entry.kind, refs: refs(entry), items: entry.items.map(small) });

  function put(list, line, max) {
    const out = [line].concat(TM.store.get(list, []).filter((x) => !(x.refs || []).some((r) => line.refs.includes(r))));
    TM.store.set(list, out.slice(0, max));
  }

  return {
    LOOKS, FIRSTS, settings,
    set: (key, value) => change((s) => { s[key] = value; }),

    /** The websites the screens show, in their order (the readers of those on). */
    readers() {
      const s = settings();
      return s.order.filter((id) => !s.off[id]).map((id) => TM.readers[id]);
    },
    toggle: (id) => change((s) => { if (s.off[id]) delete s.off[id]; else s.off[id] = true; }),
    move: (id, by) => change((s) => {
      const i = s.order.indexOf(id);
      const j = i + by;
      if (i < 0 || j < 0 || j >= s.order.length) return;
      s.order.splice(i, 1);
      s.order.splice(j, 0, id);
    }),

    /** Watched: the title, from which website, and (a series) which episode; newest first, one line a title. */
    watched: () => TM.store.get('watched', []),
    watch(entry, item, ep) {
      const season = ep ? Number(ep.season) || 1 : 0;
      const episode = ep ? Number(ep.n) || 1 : 0;
      put('watched', Object.assign(snapshot(entry), { site: item.site, ref: item.ref, at: Date.now(), season, episode,
        ep: ep ? TM.item({ season, n: episode, url: ep.url, id: ep.id }) : null }), KEPT);
    },
    lastOf: (entry) => TM.store.get('watched', []).find((w) => same(w, entry)) || null,
    /** An episode the Watch went on to by itself (the next, when one ended): its title's line says it, first. */
    advance(key, ep) {
      const line = TM.store.get('watched', []).find((w) => (w.refs || []).includes(key));
      if (!line) return false;
      const season = Number(ep.season) || 1;
      const episode = Number(ep.n) || 1;
      put('watched', Object.assign({}, line, { at: Date.now(), season, episode,
        ep: TM.item({ season, n: episode, url: ep.url, id: ep.id }) }), KEPT);
      return true;
    },

    /** My list: newest first. */
    mine: () => TM.store.get('mine', []),
    inMine: (entry) => TM.store.get('mine', []).some((m) => same(m, entry)),
    toggleMine(entry) {
      const had = this.inMine(entry);
      if (had) TM.store.set('mine', TM.store.get('mine', []).filter((m) => !same(m, entry)));
      else put('mine', snapshot(entry), 200);
      return !had;
    },

    /** A title opened: all its websites, for its page later (after a film, the box's page comes back new). */
    keep: (entry) => put('opened', snapshot(entry), KEPT),
    kept: (site, ref) => TM.store.get('opened', []).find((x) => (x.refs || []).includes(site + '|' + ref)) || null,
  };
})();
