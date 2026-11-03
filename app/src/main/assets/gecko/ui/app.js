// The screens' start, and their pages: the address after # ("" home, part/films, search, title/<site>/<ref>,
// settings/<part>), each a step in the page's history, so the remote's BACK goes back through them (and from a
// website's page back to the title it was played from). The language is the viewer's choice, else the app's
// (?lang=, as the app's menu has it); the look, the viewer's. Where the remote was is kept with each step.
'use strict';
TM.app = (function () {
  const params = new URLSearchParams(location.search);

  function apply() {
    const s = TM.saved.settings();
    TM.lang = TM.language(s.lang || params.get('lang') || 'ckb');
    const html = document.documentElement;
    html.lang = TM.lang.code;
    html.dir = TM.lang.dir;
    html.setAttribute('data-look', s.look);
  }

  /** This step's own: where the remote was (focus), how far down (y), and what its page keeps (a search's words). */
  const state = () => (history.state && typeof history.state === 'object' ? history.state : {});
  const remember = (more) => history.replaceState(Object.assign({}, state(), more), '');
  const was = () => state().focus || '';

  function render() {
    apply();
    const parts = location.hash.replace(/^#\/?/, '').split('/').filter(Boolean).map(decodeURIComponent);
    const [name, ...rest] = parts;
    const root = document.getElementById('screen');
    root.replaceChildren();
    window.scrollTo(0, 0);
    try {
      if (name === 'title' && rest.length >= 2) TM.views.title(root, rest[0], rest.slice(1).join('/'));
      else if (name === 'search') TM.views.search(root);
      else if (name === 'settings') TM.views.settings(root, rest[0]);
      else TM.views.home(root, name === 'part' ? rest[0] : 'home');
    } catch (e) {
      console.error('Twilight Movies, the screens:', e);
    }
    if (state().y) window.scrollTo(0, state().y);
  }

  /** To another page of the screens: a step on (where the remote is now, kept with this one). */
  function go(hash) {
    remember({ focus: TM.focus.now(), y: window.scrollY });
    const to = '#/' + hash;
    if (location.hash === to || (!location.hash && !hash)) return render();
    location.hash = to;
    history.replaceState({ from: 1 }, '');   // came here from a page of the screens: Back is a step back
  }

  /** This page as another, without a step (a part of Settings). */
  function swap(hash) {
    history.replaceState(Object.assign({}, state(), { focus: '' }), '', '#/' + hash);
    render();
  }

  /** The page's own Back: a step back, or home if it was opened by itself. */
  function back() {
    if (state().from) history.back();
    else go('');
  }

  /** To a website's page (to play there): where the remote is, kept for coming back. */
  function leave(url) {
    remember({ focus: TM.focus.now(), y: window.scrollY });
    location.href = url;
  }

  /** What the Watch plays by itself when this episode ends (the background page keeps it): the next ones, or none. */
  function queue(next) {
    if (typeof browser === 'undefined') return Promise.resolve();
    return browser.runtime.sendMessage({ episodes: next }).then(() => {}, () => {});
  }

  // The episode the Watch went on to by itself, after the one played from here: kept as watched, so Continue is it
  function advanced() {
    if (typeof browser === 'undefined') return;
    browser.runtime.sendMessage({ advanced: 1 }).then((a) => {
      if (a && a.key && a.ep && TM.saved.advance(a.key, a.ep)) render();
    }, () => {});
  }

  window.addEventListener('hashchange', render);
  document.addEventListener('DOMContentLoaded', () => {
    advanced();
    render();
  });
  return { go, swap, back, leave, queue, state, remember, was, render, version: params.get('v') || '' };
})();
