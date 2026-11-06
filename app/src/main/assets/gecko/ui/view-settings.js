// Settings: its parts (the language, the websites, what plays first, the look, about), each with what it is now,
// and the part chosen's choices beside them. The websites: each on or off, and moved up or down (the one on top
// comes first everywhere); each says how it answered last.
'use strict';
TM.views.settings = function (root, part) {
  const h = TM.h;
  const L = TM.lang;
  const t = L.w;
  const PARTS = ['lang', 'sites', 'first', 'look', 'about'];
  const TMDB = 'This product uses the TMDB API but is not endorsed or certified by TMDB.';   // in TMDB's words, as it asks
  const COLOURS = { twilight: ['#0C0B1D', '#F4F2FA', '#FFB07A'], midnight: ['#050506', '#F5F5F7', '#FFFFFF'],
    ember: ['#120C09', '#FAF1E8', '#F2B544'] };
  part = PARTS.includes(part) ? part : 'lang';
  const st = TM.app.state();   // the website chosen to move
  const s = TM.saved.settings();
  const on = s.order.filter((id) => !s.off[id]);
  const name = (id) => (L.ku ? TM.readers[id].nameKu || TM.readers[id].name : TM.readers[id].name);
  const again = (k) => { TM.app.swap('settings/' + part); TM.focus.want(k); };

  // A choice: its dot (on: the one chosen), what it is and a word on it
  const choice = (k, chosen, title, note, act, extra) => h('button', { class: 'choice' + (chosen ? ' on' : ''), 'data-k': k, onclick: act },
    h('span', { class: 'dot' }), extra || null, h('span', { class: 'words' }, title, note ? h('span', { text: note }) : null));
  const pick = (setting, value, k) => () => { TM.saved.set(setting, value); again(k); };

  const parts = {
    lang: () => [h('h2', { text: t.langTitle }),
      choice('lang:ckb', L.code === 'ckb', h('b', { dir: 'rtl', text: 'کوردی' }), t.rtl, pick('lang', 'ckb', 'lang:ckb')),
      choice('lang:en', L.code === 'en', h('b', { dir: 'ltr', text: 'English' }), t.ltr, pick('lang', 'en', 'lang:en'))],

    sites: () => [h('h2', { text: t.sitesTitle }), h('div', { class: 'note', text: t.sitesNote }),
      h('div', { class: 'list' }, s.order.map((id, i) => {
        const off = !!s.off[id];
        const said = TM.catalog.health[id];
        const move = (by, sign) => h('button', { class: 'move', 'data-k': 'site:' + id + ':' + sign, 'aria-label': t[sign],
          svg: TM.icons[sign], onclick: () => { TM.saved.move(id, by); again('site:' + id + ':' + sign); } });
        return h('div', { class: 'site' + (off ? ' off' : '') },
          h('span', { class: 'n', text: L.num(i + 1) }),
          h('button', { class: 'name', 'data-k': 'site:' + id, onclick: () => {
            TM.app.remember({ moving: st.moving === id ? '' : id });
            again('site:' + id);
          } }, h('b', { text: name(id) }), h('span', { text: off ? t.off : t.status[said || 'wait'] })),
          st.moving === id ? [move(-1, 'up'), move(1, 'down')] : null,
          h('button', { class: 'switch' + (off ? '' : ' on'), 'data-k': 'site:' + id + ':switch', 'aria-label': name(id),
            'aria-pressed': off ? 'false' : 'true', onclick: () => { TM.saved.toggle(id); again('site:' + id + ':switch'); } }));
      }))],

    first: () => [h('h2', { text: t.firstTitle })].concat(TM.saved.FIRSTS.map((k) =>
      choice('first:' + k, s.first === k, h('b', { text: t.first[k] }), t.firstHint[k], pick('first', k, 'first:' + k)))),

    look: () => [h('h2', { text: t.lookTitle })].concat(TM.saved.LOOKS.map((k) =>
      choice('look:' + k, s.look === k, h('b', { text: t.looks[k] }), t.lookHint[k], pick('look', k, 'look:' + k),
        h('span', { class: 'swatch' }, COLOURS[k].map((c) => { const i = h('i'); i.style.background = c; return i; }))))),

    about: () => [h('h2', { dir: 'ltr', class: 'brand', text: 'Twilight Movies' + (TM.app.version ? ' ' + TM.app.version : '') }),
      h('div', { class: 'note', text: t.adblockOn }), h('div', { class: 'note', text: t.sitesOn(L.num(on.length), L.num(s.order.length)) }),
      h('div', { class: 'credit', dir: 'ltr' }, h('img', { src: 'tmdb.svg', alt: 'TMDB' }), h('span', { text: TMDB }))],
  };

  const values = { lang: L.ku ? 'کوردی' : 'English', sites: t.onOf(L.num(on.length), L.num(s.order.length)),
    first: t.first[s.first], look: t.looks[s.look], about: '' };
  root.append(TM.parts.bar('settings'), h('main', { class: 'settings' },
    h('nav', { class: 'parts' }, h('h1', { text: t.settings }), PARTS.map((p) => h('button', { class: p === part ? 'on' : '',
      'data-k': 'part:' + p, onclick: () => { TM.app.swap('settings/' + p); TM.focus.want('part:' + p); } },
      h('b', { text: t.sec[p] }), values[p] ? h('span', { text: values[p] }) : null))),
    h('section', { class: 'choices' }, parts[part]())));
  TM.focus.want(TM.app.was(), 'part:' + part);
};
