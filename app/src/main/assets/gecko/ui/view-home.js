// Home: a new title big (its wide picture, details, summary, Watch), then the rows: Continue watching, the newest
// films and series (a title from each website in turn), dubbed, cartoons, My list. The parts (films, series,
// cartoons, My list): their titles, in a grid. What the websites had last comes at once; they're asked again after
// half an hour (the very first time, each website's titles show as they come).
'use strict';
TM.views.home = function (root, part) {
  const h = TM.h;
  const L = TM.lang;
  const t = L.w;
  const ROW = 40;   // titles in a row at most (the rest: in the part's grid)
  part = ['films', 'series', 'cartoons', 'mine'].includes(part) ? part : 'home';
  const main = h('main', { class: part === 'home' ? 'home' : 'page' });
  root.append(TM.parts.bar(part), main);

  // The big one: the first title the websites show big (else the first new film) that has a wide picture
  function hero(entries) {
    const e = entries.find((x) => TM.merge.show(x, L).backdrop);
    if (!e) return null;
    const v = TM.merge.show(e, L);
    const src = TM.merge.sources(e, TM.saved.settings())[0];
    const pic = h('div', { class: 'pic' });
    pic.style.backgroundImage = 'url(' + JSON.stringify(TM.size(v.backdrop, 'w1280')) + ')';
    return h('section', { class: 'hero' }, pic, h('div', { class: 'shade' }),
      h('div', { class: 'info' },
        h('h1', { dir: v.nameDir, text: v.name }),
        h('div', { class: 'meta', text: TM.parts.meta(v, [TM.parts.kinds(v.langs), t.sites(L.num(v.sites))]) }),
        v.overview ? h('p', { class: 'about', dir: v.overviewDir, text: v.overview }) : null,
        h('div', { class: 'actions' },
          src ? h('button', { class: 'watch', 'data-k': 'hero:watch',
            onclick: (ev) => TM.parts.playing(ev.currentTarget, () => TM.catalog.play(e, src)) },
            h('span', { svg: TM.icons.play }), h('span', { text: t.watch })) : null,
          h('button', { class: 'text', 'data-k': 'hero:more', text: t.more, onclick: () => TM.parts.open(e) }))));
  }

  // Continue watching: a wide card for each, which plays it again where it was (a series: that episode)
  function watching() {
    return TM.saved.watched().map((w) => {
      const e = TM.merge.again(w);
      const item = e.items.find((x) => x.site === w.site && x.ref === w.ref);
      if (!item || !TM.readers[w.site]) return null;
      const ep = w.ep || (w.season ? { season: w.season, n: w.episode } : null);
      return { e, o: { wide: true, note: ep ? t.se(L.num(ep.season), L.num(ep.n)) : '', onclick: () => TM.catalog.play(e, item, ep) } };
    }).filter(Boolean);
  }

  function rows(sites) {
    const got = TM.catalog.lists(sites);
    const news = got.rows.newFilms || [];
    const seen = watching();
    const mine = TM.saved.mine().map(TM.merge.again);
    return [hero((got.rows.featured || []).concat(news)),
      h('div', { class: 'rows' },
        seen.length ? h('section', { class: 'row' }, h('h2', { text: t.rows.continue }),
          h('div', { class: 'strip' }, seen.map((x, i) => TM.parts.card(x.e, 'row:continue:' + i, x.o)))) : null,
        TM.parts.row('newFilms', t.rows.newFilms, news.slice(0, ROW)),
        TM.parts.row('newSeries', t.rows.newSeries, (got.rows.newSeries || []).slice(0, ROW)),
        TM.parts.row('dubbed', t.rows.dubbed, got.all.filter((e) => TM.merge.has(e, 'dub')).slice(0, ROW)),
        TM.parts.row('cartoons', t.rows.cartoons, got.all.filter(TM.merge.cartoon).slice(0, ROW)),
        TM.parts.row('mine', t.rows.mine, mine.slice(0, ROW)))];
  }

  function grid(sites) {
    const got = TM.catalog.lists(sites);
    const list = part === 'mine' ? TM.saved.mine().map(TM.merge.again)
      : got.all.filter(part === 'cartoons' ? TM.merge.cartoon : (e) => e.kind === (part === 'films' ? 'film' : 'series'));
    return list.length ? h('div', { class: 'grid' }, list.map((e, i) => TM.parts.card(e, 'grid:' + i)))
      : h('p', { class: 'empty', text: part === 'mine' ? t.mineEmpty : t.noResults });
  }

  function draw(sites) {
    if (!main.isConnected) return;
    const k = TM.focus.now();
    main.replaceChildren(...[].concat(part === 'home' ? rows(sites) : grid(sites)).filter(Boolean));
    if (k && !TM.focus.find(k)) TM.focus.want(part === 'home' ? 'hero:watch' : 'grid:0');
    else if (k) TM.focus.put(TM.focus.find(k));
    else TM.focus.landed();
  }

  // The remote: where it was, else on the big one's Watch (once it's there); on the part's name if nothing came
  const kept = TM.catalog.homeKept();
  const some = Object.keys(kept.sites).length > 0;
  const nothing = () => { if (!TM.focus.now()) TM.focus.want('bar:' + part); };
  TM.focus.want(TM.app.was(), part === 'home' ? 'hero:watch' : 'grid:0', 'row:continue:0', 'row:newFilms:0');
  if (some || part === 'mine') draw(kept.sites);
  else main.append(h('p', { class: 'empty', text: t.status.wait }));
  if (!kept.old) return nothing();
  const shown = TM.store.get('home', {}).at || 0;
  const late = !some || Date.now() - shown > 6 * 3600 * 1000;   // six hours old: new ones shown as they come
  if (!late) nothing();
  TM.catalog.homeFresh(late ? () => draw(TM.catalog.homeKept().sites) : null).then(nothing);
};
