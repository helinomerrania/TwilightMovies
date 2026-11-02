// Search: the screen's own letters (Kurdish or English, and the key between them), the words typed, then what every
// website found, as each one answers (a title on several websites once). Typing stops a moment: it searches. The
// words and the letters shown are kept with the page, so BACK from a title finds them as they were.
'use strict';
TM.views.search = function (root) {
  const h = TM.h;
  const L = TM.lang;
  const t = L.w;
  const KEYS = { ckb: ['ئابپتجچحخد', 'رڕزژسشعغفڤ', 'قکگلڵمنھەو', 'ۆیێ'], en: ['abcdefghij', 'klmnopqrst', 'uvwxyz1234', '567'] };
  const st = Object.assign({ q: '', abc: !L.ku }, TM.app.state());
  const text = h('span', { dir: 'auto' });
  const typed = h('div', { class: 'typed' }, text, h('span', { class: 'caret' }));
  const pad = h('div', { class: 'pad' });
  const status = h('div', { class: 'status' });
  const grid = h('div', { class: 'grid' });
  root.append(TM.parts.bar('search'), h('main', { class: 'search' },
    h('div', { class: 'keys' }, typed, pad, h('div', { class: 'meta', text: t.typeHint })),
    h('div', { class: 'found' }, status, grid)));

  function key(label, k, act, o) {
    o = o || {};
    const b = h('button', { class: o.word ? 'word' : '', 'data-k': 'key:' + k, 'aria-label': o.name || label, text: label, onclick: act });
    if (o.span) b.style.gridColumn = 'span ' + o.span;
    return b;
  }

  function keys() {
    const rows = KEYS[st.abc ? 'en' : 'ckb'];
    pad.dir = st.abc ? 'ltr' : 'rtl';
    const all = [].concat(...rows.map((r) => Array.from(r).map((ch) => key(ch, ch, () => type(st.q + ch)))));
    all.push(key(t.space, 'space', () => type(st.q + ' '), { span: 4, word: true }),
      key('⌫', 'del', () => type(st.q.slice(0, -1)), { span: 2, name: t.del }),
      key(st.abc ? 'ئ ب' : 'ABC', 'abc', () => {
        st.abc = !st.abc;
        TM.app.remember({ abc: st.abc });
        keys();
        TM.focus.want('key:abc');
      }, { word: true }));
    pad.replaceChildren(...all);
  }

  function show() {
    text.textContent = st.q;
    typed.dir = /[؀-ۿ]/.test(st.q) ? 'rtl' : st.q.trim() ? 'ltr' : L.dir;
  }

  let timer = 0;
  function type(q) {
    st.q = q;
    TM.app.remember({ q });
    show();
    clearTimeout(timer);
    timer = setTimeout(find, 700);
  }

  // Under a title found: how many websites have it (if more than one), and dubbing if it's dubbed
  function caption(e) {
    const v = TM.merge.show(e, L);
    return [v.sites > 1 ? t.sites(L.num(v.sites)) : '', v.langs.includes('dub') ? t.dub : ''].filter(Boolean).join(' · ');
  }

  function find() {
    const q = st.q.trim();
    if (q.length < 2) {
      status.textContent = '';
      grid.replaceChildren();
      return;
    }
    status.textContent = t.status.wait;
    TM.catalog.search(q, (found, answered, asked) => {
      if (!grid.isConnected || q !== st.q.trim()) return;
      const k = TM.focus.now();
      status.textContent = (answered < asked ? '… ' : '') + (found.length ? t.found(L.num(found.length)) : t.noResults)
        + ' · ' + t.searched(L.num(asked));
      grid.replaceChildren(...found.slice(0, 30).map((e, i) => h('figure', {},
        TM.parts.card(e, 'found:' + i), h('figcaption', { text: caption(e) }))));
      if (/^found:/.test(k)) TM.focus.want(k, 'found:0');
      else TM.focus.landed();
    });
  }

  keys();
  show();
  if (st.q.trim()) find();
  TM.focus.want(TM.app.was(), 'key:' + KEYS[st.abc ? 'en' : 'ckb'][0][0]);
};
