// The pieces every screen is made of (ui.css draws them): an element made in one line (TM.h), the few drawn signs,
// the top (the app's name, the parts, search, settings), a title's card and a row of them, a title's line of
// details, and where the remote is: put on what it was on before, once that's on the screen again.
'use strict';
TM.h = function (tag, props, ...kids) {
  const el = document.createElement(tag);
  Object.keys(props || {}).forEach((k) => {
    const v = props[k];
    if (v === undefined || v === null || v === false) return;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k === 'svg') el.innerHTML = v;   // only the app's own signs (TM.icons)
    else if (k.slice(0, 2) === 'on') el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  });
  kids.flat(Infinity).forEach((c) => { if (c !== null && c !== undefined && c !== false) el.append(c.nodeType ? c : String(c)); });
  return el;
};

TM.icons = {
  play: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3 L21 12 L6 21 Z" fill="currentColor"/></svg>',
  back: '<svg viewBox="0 0 24 24" aria-hidden="true" class="flip"><path d="M15 5 L8 12 L15 19" fill="none" stroke="currentColor"'
    + ' stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  search: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor"'
    + ' stroke-width="2.2"/><path d="M15.5 15.5 L21 21" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>',
  settings: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.2" fill="none" stroke="currentColor"'
    + ' stroke-width="2"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4'
    + 'l2.1-2.1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  up: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 15 L12 9 L18 15" fill="none" stroke="currentColor" stroke-width="2.4"'
    + ' stroke-linecap="round" stroke-linejoin="round"/></svg>',
  down: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9 L12 15 L18 9" fill="none" stroke="currentColor" stroke-width="2.4"'
    + ' stroke-linecap="round" stroke-linejoin="round"/></svg>',
};

TM.parts = (function () {
  const h = TM.h;
  const PARTS = ['home', 'films', 'series', 'cartoons', 'mine'];

  /** The top: the app's name (home), the parts, search and settings; on: the one shown. */
  function bar(on) {
    const t = TM.lang.w;
    return h('header', { class: 'bar' },
      h('button', { class: 'logo', 'data-k': 'bar:logo', 'aria-label': 'Twilight Movies', onclick: () => TM.app.go('') },
        h('i'), h('span', { dir: 'ltr', text: 'Twilight Movies' })),
      h('nav', { class: 'nav' }, PARTS.map((p) => h('button', { class: p === on ? 'on' : '', 'data-k': 'bar:' + p,
        text: t.nav[p], onclick: () => TM.app.go(p === 'home' ? '' : 'part/' + p) }))),
      h('button', { class: 'icon' + (on === 'search' ? ' on' : ''), 'data-k': 'bar:search', 'aria-label': t.search,
        svg: TM.icons.search, onclick: () => TM.app.go('search') }),
      h('button', { class: 'icon' + (on === 'settings' ? ' on' : ''), 'data-k': 'bar:settings', 'aria-label': t.settings,
        svg: TM.icons.settings, onclick: () => TM.app.go('settings') }));
  }

  /** Where a title's page is (its first website's name for it). */
  const pageOf = (entry) => 'title/' + entry.items[0].site + '/' + encodeURIComponent(entry.items[0].ref);
  const open = (entry) => { TM.saved.keep(entry); TM.app.go(pageOf(entry)); };

  /** A title's card: its poster (wide: its wide picture, its name and a note on it); its name where there's none. */
  function card(entry, k, o) {
    o = o || {};
    const v = TM.merge.show(entry, TM.lang);
    const src = o.wide ? v.backdrop || v.poster : v.poster;
    const el = h('button', { class: 'card' + (o.wide ? ' wide' : ' tall'), 'data-k': k, 'aria-label': v.name,
      onclick: o.onclick || (() => open(entry)) });
    const name = h('span', { class: 'name', dir: v.nameDir }, v.name, o.note ? h('span', { class: 'note', text: o.note }) : null);
    if (src) {
      const img = h('img', { src: TM.size(src, o.wide ? 'w780' : 'w342'), alt: '', loading: 'lazy', decoding: 'async' });
      img.addEventListener('error', () => { img.remove(); el.classList.remove('pictured'); });
      el.classList.add('pictured');
      el.append(img);
    }
    el.append(name);
    return el;
  }

  /** A row: its name, then its cards across (key: the row's own, for where the remote was). */
  function row(key, title, entries, o) {
    if (!entries.length) return null;
    return h('section', { class: 'row' }, h('h2', { text: title }),
      h('div', { class: 'strip' }, entries.map((e, i) => card(e, 'row:' + key + ':' + i, o && o(e)))));
  }

  /** A title's line of details: year, length, genres, rating, age (and more: words to add at its end). */
  function meta(v, more) {
    const L = TM.lang;
    const t = L.w;
    return [v.year ? L.num(v.year) : '', v.minutes ? t.length(v.minutes, L.num) : '', v.genres.join(L.ku ? '، ' : ', '),
      v.rating ? '★ ' + L.num(v.rating) : '', v.age ? t.age(String(v.age), L.num) : ''].concat(more || []).filter(Boolean).join(' · ');
  }

  /** Watch pressed: its words say "…" until the website's page opens (a series asks which episode first). */
  function playing(button, f) {
    const words = button.lastChild;
    const was = words.textContent;
    words.textContent = TM.lang.w.status.wait;
    Promise.resolve().then(f).catch((e) => {
      console.warn('Twilight Movies, playing:', e);
      words.textContent = was;
    });
  }

  /** Kurdish subtitles, dubbing, or both, in words. */
  const kinds = (langs) => langs.map((l) => TM.lang.w[l]).join(' · ');

  return { bar, card, row, meta, kinds, open, pageOf, playing };
})();

/** Where the remote is: what it should go to once that's on the screen (until the viewer moves it themselves). */
TM.focus = (function () {
  let wanted = null;
  let moved = false;   // the viewer moved it since the screen asked for it
  const find = (k) => document.querySelector('[data-k="' + CSS.escape(k) + '"]');
  function put(el) {
    el.focus({ focusVisible: true, preventScroll: true });
    el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'instant' });
  }
  /** Go there now, or as soon as it's on the screen (keys: the first of them there is). */
  function want(...keys) {
    wanted = keys.filter(Boolean);
    moved = false;
    return landed();
  }
  /** Something new on the screen: the remote goes where it was wanted, if that's here now. */
  function landed() {
    if (!wanted) return false;
    const el = wanted.map(find).find(Boolean);
    if (!el) return false;
    wanted = null;
    put(el);
    return true;
  }
  const now = () => (document.activeElement && document.activeElement.getAttribute('data-k')) || '';
  window.addEventListener('keydown', () => { wanted = null; moved = true; }, true);   // the viewer moves it: theirs
  return { want, landed, now, find, put, moved: () => moved };
})();
