// The Twilight screens' Watch, the player alone (app/src/main/assets/gecko/player.js in the page, alone.js in the
// background page): which tab is the Twilight screens' (a page they opened, and the website's own pages after it;
// not once the app opens another), each website's player found on stand-in pages (its frame, its video's box, its
// own box; not a trailer, not a speck), a website's first server chosen when its player isn't there yet, and the
// player marked to show alone, the style that does it from the background page.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');
const { E } = require('./lib/page');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');

// The background page's side
let listener = null;
const css = [];
const browser = { runtime: { onMessage: { addListener: (f) => { listener = f; } } },
  tabs: { insertCSS: (tab, o) => { css.push([tab, o]); return Promise.resolve(); } } };
const bg = vm.createContext({ browser, URL, Promise, OWN: 'moz-extension://the-app/' });
vm.runInContext(read('alone.js'), bg);
const opening = (tab, url, from) => vm.runInContext('aloneFor', bg)({ type: 'main_frame', tabId: tab, url, originUrl: from });
const asks = (tab, m) => listener(m, { tab: { id: tab } });

module.exports = (async () => {
  opening(3, 'https://beenar.net/detail-cowatching/3057?series=false', 'moz-extension://the-app/ui/index.html#/title/beenar/f%3A3057');
  check('a page the Twilight screens opened: its player alone', await asks(3, { alone: 'beenar.net' }), true);
  check('...not another tab\'s', await asks(4, { alone: 'beenar.net' }), false);
  opening(3, 'https://beenar.net/detail-cowatching/3057?series=true', 'https://beenar.net/detail-cowatching/3057?series=false');
  check('...nor after a page the website opens itself, on its own domain', await asks(3, { alone: 'beenar.net' }), true);
  await asks(3, { aloneStyle: 1 });
  check('...the style that shows it alone: the user\'s own (over the website\'s), in the page itself', css.map(([t, o]) => [t, o.cssOrigin, o.frameId]),
    [[3, 'user', 0]]);
  ok('...everything hidden but the player, which fills the screen', /body \* \{ visibility: hidden !important/.test(css[0][1].code)
    && /\[data-tm-player\] \{ position: fixed !important; inset: 0/.test(css[0][1].code));
  opening(3, 'https://krd.watch/', undefined);
  check('the app opens another page in the tab: the website as it is again', await asks(3, { alone: 'krd.watch' }), false);
  opening(5, 'https://www.kurd-movie.com/movies/onslaught/', 'moz-extension://the-app/ui/index.html');
  check('...a website\'s hosts are one (www. or not)', await asks(5, { alone: 'kurd-movie.com' }), true);
  opening(5, 'https://ads.example/landing', 'https://www.kurd-movie.com/movies/onslaught/');
  check('...a page of another website: no longer', await asks(5, { alone: 'ads.example' }), false);

  // The page's side, on stand-in pages
  const P = vm.createContext({ window: {}, location: { host: '' }, Array, setInterval, clearInterval });
  P.window.top = P.window;
  vm.runInContext(read('player.js'), P);
  const player = vm.runInContext('player', P);
  function doc(body) {
    const html = E('html', {}, [E('body', {}, body)]);
    const d = { documentElement: html, body: html.children[0], activeElement: null, fire: () => {},
      querySelectorAll: (s) => html.querySelectorAll(s), getElementById: (id) => html.querySelectorAll('*').find((x) => x.attrs.id === id) || null };
    for (const x of [html].concat(html.querySelectorAll('*'))) {
      x.doc = d;
      x.setAttribute = (n, v) => { x.attrs[n] = String(v); };
      x.querySelector = (q) => x.querySelectorAll(q)[0] || null;
    }
    return d;
  }
  const big = [100, 100, 1060, 640];
  let d = doc([E('iframe', { name: 'tracker', attrs: { src: 'https://ads.example/t' }, rect: [0, 0, 1, 1] }),
    E('div', {}, [E('iframe', { name: 'player', attrs: { src: 'https://beenar.uns.bio/#edltgq' }, rect: big })])]);
  check('Beenar: its player\'s frame', player.find(d, 'beenar.net').name, 'player');
  d = doc([E('iframe', { name: 'trailer', attrs: { src: 'https://www.youtube.com/embed/x8UAUAuKNcU' }, rect: [0, 0, 1920, 1080] }),
    E('iframe', { name: 'speck', attrs: { src: 'https://pxdrop.lijit.com/a' }, rect: [0, 0, 0, 0] }),
    E('iframe', { name: 'player', attrs: { src: 'https://cdnwish.com/e/0azfw2ac7y26' }, rect: big })]);
  check('Kurd Movie: the film\'s frame, not its trailer nor a speck', player.find(d, 'www.kurd-movie.com').name, 'player');
  const box = E('div', { name: 'box', attrs: { class: 'fixed inset-0 z-[70] select-none bg-black' } }, [E('div', {}, [
    E('video', { name: 'video', attrs: { src: '/api/v/Au7e' }, rect: [0, 0, 0, 0] })])]);
  d = doc([E('main', { attrs: { class: 'min-h-screen' } }, [box])]);
  check('KRD WATCH: its own video, in the box that shows what goes over it (subtitles)', player.find(d, 'krd.watch').name, 'box');
  d = doc([E('div', { name: 'wrapper', attrs: { id: 'player-wrapper' } }, [E('video', { rect: big })])]);
  check('Kurdsaz: its player\'s own box', player.find(d, 'kurdsaz.com').name, 'wrapper');
  d = doc([E('video', { name: 'small', rect: [0, 0, 400, 300] }), E('video', { name: 'large', rect: big })]);
  check('another website: its biggest video', player.find(d, 'kurdcinama.com').name, 'large');

  // As Kurd Movie's page has them: its trailer first, then its servers
  const options = [E('li', { attrs: { id: 'player-option-trailer', class: 'dooplay_player_option', 'data-nume': 'trailer' } }),
    E('li', { attrs: { id: 'player-option-1', class: 'dooplay_player_option', 'data-nume': '1' } }),
    E('li', { attrs: { id: 'player-option-2', class: 'dooplay_player_option', 'data-nume': '2' } })];
  d = doc([E('ul', {}, options)]);
  check('no player yet: Kurd Movie\'s first server chosen, not its trailer', [player.find(d, 'www.kurd-movie.com'),
    player.press(d, 'www.kurd-movie.com'), options.map((o) => o.calls)], [null, true, [[], [['click']], []]]);
  check('...nothing pressed where there\'s nothing to press', player.press(d, 'beenar.net'), false);

  const frame = E('iframe', { name: 'frame', attrs: { src: 'https://beenar.uns.bio/' }, rect: big });
  const around = E('div', { name: 'around' }, [frame]);
  d = doc([around]);
  player.isolate(d, frame);
  check('the player alone: the page, the player and the boxes around it marked; the remote in the player',
    [d.documentElement.hasAttribute('data-tm-alone'), frame.hasAttribute('data-tm-player'), around.hasAttribute('data-tm-keep'),
      d.body.hasAttribute('data-tm-keep'), frame.calls[0][0]], [true, true, true, true, 'focus']);
})();
