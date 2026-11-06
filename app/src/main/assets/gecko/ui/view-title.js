// A film or a series: its page. Its wide picture, name (and its English one), details and summary; Watch (a series:
// where it was left, else its first episode) and My list; the websites it's on, the one that plays first marked (a
// film plays from the one pressed; a series shows that one's episodes); a series' seasons and their episodes. It
// shows at once what the box has of it, and more as each website answers.
'use strict';
TM.views.title = function (root, site, ref) {
  const h = TM.h;
  const L = TM.lang;
  const t = L.w;
  const kept = TM.saved.kept(site, ref);
  const entry = kept ? TM.merge.again(kept)
    : { kind: /^(tv:|s:|tvshows\/)/.test(ref) ? 'series' : 'film', items: [{ site, ref }] };
  const st = TM.app.state();   // this page's own: the website chosen (src), the season shown
  const info = h('div', { class: 'info' });
  const seasons = h('section', { class: 'seasons' });
  const pic = h('div', { class: 'pic' });
  root.append(h('div', { class: 'title' }, pic, h('div', { class: 'shade' }), info, seasons));

  const name = (id) => (L.ku ? TM.readers[id].nameKu || TM.readers[id].name : TM.readers[id].name);
  const sources = () => TM.merge.sources(entry, TM.saved.settings());
  const key = (x) => x.site + '|' + x.ref;
  const chosen = () => sources().find((x) => key(x) === st.src) || sources()[0];
  const series = () => entry.kind === 'series';
  const dir = (s) => (/[؀-ۿ]/.test(s || '') ? 'rtl' : 'ltr');

  function choose(x) {
    st.src = key(x);
    TM.app.remember({ src: st.src, season: 0 });
    st.season = 0;
    drawInfo();
    drawSeasons();
  }

  function drawInfo() {
    const v = TM.merge.show(entry, L);
    const all = sources();
    const src = chosen();
    const last = TM.saved.lastOf(entry);
    if (v.backdrop) pic.style.backgroundImage = 'url(' + JSON.stringify(TM.size(v.backdrop, 'w1280')) + ')';
    const count = series() && src && src.seasons ? [t.seasons(L.num(src.seasons.length))] : [];
    const label = !series() ? t.watch : last && last.season ? t.continueEp(L.num(last.season), L.num(last.episode)) : t.startEp(L.num);
    const k = TM.focus.now();
    info.replaceChildren(...[
      h('button', { class: 'back', 'data-k': 'back', onclick: () => TM.app.back() }, h('span', { svg: TM.icons.back }), h('span', { text: t.back })),
      h('h1', { dir: v.nameDir, text: v.name }),
      v.original ? h('div', { class: 'original', dir: 'ltr', text: v.original }) : null,
      h('div', { class: 'meta', text: [].concat(count, TM.parts.meta(v)).filter(Boolean).join(' · ') }),
      v.overview ? h('p', { class: 'about' + (series() ? '' : ' long'), dir: v.overviewDir, text: v.overview }) : null,
      h('div', { class: 'actions' },
        src ? h('button', { class: 'watch', 'data-k': 'watch',
          onclick: (ev) => TM.parts.playing(ev.currentTarget, () => TM.catalog.play(entry, src)) },
          h('span', { svg: TM.icons.play }), h('span', { text: label })) : null,
        h('button', { class: 'text', 'data-k': 'mine', text: TM.saved.inMine(entry) ? t.inMine : t.addMine,
          onclick: (ev) => { ev.currentTarget.textContent = TM.saved.toggleMine(entry) ? t.inMine : t.addMine; } })),
      all.length > 1 ? h('div', { class: 'sources' }, h('div', { class: 'label', text: t.availableOn }),
        all.map((x, i) => h('button', { class: 'source' + (x === src ? ' on' : ''), 'data-k': 'src:' + key(x),
          onclick: () => (series() ? choose(x) : TM.catalog.play(entry, x)) },
          h('i'), h('b', { text: name(x.site) }),
          h('span', { text: [TM.parts.kinds(x.langs || ['sub']), x.quality].filter(Boolean).join(' · ') }),
          i === 0 ? h('em', { text: t.firstChoice }) : null)))
        : src ? h('div', { class: 'meta', text: t.from(name(src.site)) }) : null].filter(Boolean));
    if (k) TM.focus.want(k);
  }

  // A series' seasons (its tabs) and the one shown's episodes, on the website chosen (drawn: for which, and how
  // many seasons it had then; asked: the last season asked for, so an older answer doesn't come over it)
  let drawn = '';
  let asked = 0;
  const shape = () => { const src = chosen(); return src ? key(src) + '|' + (src.seasons || []).length : ''; };
  function drawSeasons() {
    const src = chosen();
    const mine = ++asked;
    drawn = shape();
    if (!series() || !src) return seasons.replaceChildren();
    const list = src.seasons || [];
    const last = TM.saved.lastOf(entry);
    const n = st.season || (last && list.some((s) => s.n === last.season) ? last.season : (list[0] || {}).n);
    if (!n) return seasons.replaceChildren(h('div', { class: 'meta', text: t.status.wait }));
    const eps = h('div', { class: 'episodes' });
    seasons.replaceChildren(h('div', { class: 'tabs' }, list.map((s) => h('button', { class: s.n === n ? 'on' : '',
      'data-k': 'season:' + s.n, text: t.season(L.num(s.n)), onclick: () => {
        st.season = s.n;
        TM.app.remember({ season: s.n });
        drawSeasons();
      } }))), eps);
    TM.catalog.episodes(src, n).then((all) => {
      if (mine !== asked) return;
      eps.replaceChildren(...all.map((ep) => episode(src, n, ep, last)));
      TM.focus.landed();
    }, () => { if (mine === asked) eps.replaceChildren(h('div', { class: 'meta', text: t.status.failed })); });
  }

  function episode(src, season, ep, last) {
    const here = last && last.season === season && last.episode === ep.n;
    const seen = last && (last.season > season || (last.season === season && last.episode > ep.n));
    return h('button', { class: 'episode', 'data-k': 'ep:' + season + ':' + ep.n,
      onclick: () => TM.catalog.play(entry, src, Object.assign({ season }, ep)) },
      h('span', { class: 'n' + (here ? ' now' : ''), text: L.num(ep.n) }),
      h('b', { dir: dir(ep.title), text: ep.title || t.episode(L.num(ep.n)) }),
      ep.minutes ? h('span', { text: t.length(ep.minutes, L.num) }) : null,
      seen ? h('i', { class: 'seen' }) : null);
  }

  // TMDB's wide picture once it comes (pictures.js: a title none of its websites gives TMDB's for)
  const original = () => TM.pictures && TM.pictures.want(entry, () => {
    const b = TM.merge.show(entry, L).backdrop;
    if (b) pic.style.backgroundImage = 'url(' + JSON.stringify(TM.size(b, 'w1280')) + ')';
  });

  drawInfo();
  drawSeasons();
  original();
  const last = TM.saved.lastOf(entry);
  TM.focus.want(TM.app.was(), 'watch', last && last.season ? 'ep:' + last.season + ':' + last.episode : '', 'back');
  TM.catalog.title(entry, () => {
    entry.kind = TM.merge.first(entry, 'kind') || entry.kind;
    TM.merge.order(entry, TM.saved.settings());
    drawInfo();
    if (shape() !== drawn) drawSeasons();
    original();
  }).then(() => TM.saved.keep(entry));
};
