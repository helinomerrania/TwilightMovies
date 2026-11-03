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
  const tried = (tab, url) => listener({ alone: 'kurdsubtitle.net' }, { tab: { id: tab }, url });
  check('...nor an address that says #tm-play= by itself', await tried(7, 'https://kurdsubtitle.net/tvshows/x#tm-play=2-3'), false);
  let studyListener = null;
  const study = vm.createContext({ URL, Promise, OWN: 'moz-extension://the-app/', studying: true,
    browser: { runtime: { onMessage: { addListener: (f) => { studyListener = f; } } }, tabs: browser.tabs } });
  vm.runInContext(read('alone.js'), study);
  check('the study build (study.js): an address as the screens open one (#tm-play=, #tm-alone) shows its player alone, for the study',
    [await studyListener({ alone: 'kurdsubtitle.net' }, { tab: { id: 7 }, url: 'https://kurdsubtitle.net/tvshows/x#tm-play=2-3' }),
      await studyListener({ alone: 'kurdsubtitle.net' }, { tab: { id: 8 }, url: 'https://kurdsubtitle.net/movies/y#tm-alone' }),
      await studyListener({ alone: 'kurdsubtitle.net' }, { tab: { id: 9 }, url: 'https://kurdsubtitle.net/movies/y' })], [true, true, false]);

  // The page's side, on stand-in pages
  const timed = [];
  const scrolled = [];
  const P = vm.createContext({ window: { scrollY: 900, scrollTo: (x, y) => scrolled.push(y) }, location: { host: '' }, Array,
    setInterval, clearInterval, setTimeout: (f, ms) => timed.push([f, ms]) });
  P.window.top = P.window;
  vm.runInContext(read('player-steps.js'), P);
  vm.runInContext(read('player-servers.js'), P);
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
  const beenar = (src) => doc([E('iframe', { name: 'tracker', attrs: { src: 'https://ads.example/t' }, rect: [0, 0, 1, 1] }),
    E('iframe', { name: 'banner', attrs: { src: 'https://ads.example/b' }, rect: [0, 0, 1920, 1080] }),
    E('div', { attrs: { class: 'w-full h-full relative bg-black iframe-container' } }, [E('iframe', { name: 'player', attrs: { src }, rect: big })])]);
  let d = beenar('https://beenar.uns.bio/#edltgq');
  check('Beenar: its player\'s frame (in its box), whichever of its servers plays', [player.find(d, 'beenar.net').name,
    player.find(beenar('https://vidmoly.net/embed-x.html'), 'beenar.net').name], ['player', 'player']);
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
  const start = E('button', { attrs: { type: 'button', class: 'grid size-full place-items-center' } });
  const server = E('button', { attrs: { type: 'button', class: 'server' } });
  d = doc([E('div', { attrs: { class: 'relative aspect-backdrop w-full' } }, [start]), E('div', {}, [server])]);
  check('Mars Cinema: its player\'s start pressed (the button in its box), not another', [player.press(d, 'marscinema.net'),
    start.calls, server.calls], [true, [['click']], []]);
  const marsBox = E('div', { name: 'box', attrs: { class: 'relative aspect-backdrop w-full' } }, [
    E('iframe', { attrs: { src: 'https://marshost.pages.dev/embed-0lz5i847aaw7.html' }, rect: big })]);
  d = doc([marsBox, E('div', {}, [E('button', { attrs: { class: 'server' } })])]);
  check('...then its player: the box its frame came in (what the player shows over it stays)', player.find(d, 'marscinema.net').name, 'box');

  // Kurd Subtitle: a series' episode chosen on its page (its season opened, then the episode), then a free server
  const steps = vm.runInContext('playerSteps', P)['kurdsubtitle.net'];
  check('the episode the address asks for, where it\'s chosen on the page', [player.want('#tm-play=2-3'), player.want('')],
    [{ season: 2, n: 3 }, null]);
  const seasonTwo = E('button', { text: '2 وەرزی دوو 5 ئەڵقە' }, [E('div', { text: '2' }), E('div', { text: 'وەرزی دوو 5 ئەڵقە' })]);
  const seasonOne = E('button', { text: '1 وەرزی یەک 5 ئەڵقە' }, [E('div', { text: '1' }), E('div', { text: 'وەرزی یەک 5 ئەڵقە' })]);
  const block = E('div', { attrs: { class: 'rounded-xl overflow-hidden border' } }, [seasonTwo]);
  const heading = E('h2', { text: 'لوپین - وەرزی 1 - ئەڵقەی 1' });   // what the page plays first
  d = doc([heading, E('div', {}, [seasonOne]), block]);
  const want = { season: 2, n: 3 };
  check('Kurd Subtitle: its episode\'s season opened first, not another', [steps(d, want), seasonTwo.calls, seasonOne.calls],
    [false, [['click']], []]);
  const epTwo = E('button', { text: '2-2 Chapter 2', attrs: { class: 'border-b px-2 hover:bg-primary/5' } }, [E('div', { text: '2-2' })]);
  const epThree = E('button', { text: '2-3 Chapter 3', attrs: { class: 'border-b px-2 hover:bg-primary/5' } }, [E('div', { text: '2-3' })]);
  [epTwo, epThree].forEach((x) => { block.children.push(x); x.parentElement = block; });
  check('...then its episode, once there', [steps(d, want), epThree.calls, epTwo.calls, seasonTwo.calls.length], [false, [['click']], [], 1]);
  heading.textContent = 'لوپین - وەرزی 2 - ئەڵقەی 3';   // its button looks the same, chosen or not
  const later = E('button', { text: 'بینینی سێرڤەرە ئاساییەکان (7)', disabled: true });
  const free = E('button', { text: 'سێرڤەری Vstreamer', attrs: { class: 'md:w-full md:border-b py-4 text-right px-6 shrink-0' } });
  block.children.push(later, free);
  later.parentElement = free.parentElement = block;
  check('...the page plays it (its heading says so): a free server (its own needs signing in), not a button that can\'t be pressed yet, '
    + 'nor the episode again', [steps(d, want), later.calls, free.calls, epThree.calls.length], [false, [], [['click']], 1]);
  const area = E('div', { name: 'area', attrs: { class: 'grow bg-white/10 rounded-2xl overflow-hidden border relative' } }, [
    E('iframe', { name: 'frame', attrs: { id: 'player-frame', src: 'https://streamer.vstreamer.store/stream/17674' }, rect: big })]);
  d = doc([area]);
  check('...its player there: said so, and the box it\'s in is the player alone', [steps(d, null), player.find(d, 'kurdsubtitle.net').name],
    [true, 'area']);

  const frame = E('iframe', { name: 'frame', attrs: { src: 'https://beenar.uns.bio/' }, rect: big });
  const around = E('div', { name: 'around' }, [frame]);
  d = doc([around]);
  frame.scrollIntoView = (o) => frame.calls.push(['scrollIntoView', o, d.documentElement.hasAttribute('data-tm-alone')]);
  player.isolate(d, frame);
  check('the player alone: first scrolled into sight where it is (a player may wait till it\'s seen), then the page, the player '
    + 'and the boxes around it marked; the remote in the player', [frame.calls[0], d.documentElement.hasAttribute('data-tm-alone'),
    frame.hasAttribute('data-tm-player'), around.hasAttribute('data-tm-keep'), d.body.hasAttribute('data-tm-keep'), frame.calls[1][0]],
  [['scrollIntoView', { block: 'center' }, false], true, true, true, true, 'focus']);
  timed.splice(0).forEach(([f]) => f());
  timed.splice(0).forEach(([f]) => f());
  check('...and the page scrolled a little and back, four times (a frame is told it\'s in sight when the page scrolls)',
    scrolled, [899, 899, 899, 899, 900, 900, 900, 900]);
})();
