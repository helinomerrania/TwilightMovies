// The Watch's film started by itself, with no OK pressed (app/src/main/assets/gecko/player-play.js in every frame,
// alone-play.js in the background page, player.js's play in the page): on stand-in frames, its video played when it
// has something to play, else the player's own start pressed, else what's in its middle (not an ad's link); only
// where it's told to; never again once it played (the viewer may pause it). The background page tells the frames in
// the player alone (and the frames in them), never another (a trailer's); a frame that comes later asks.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check } = require('./lib/check');
const { ROOT } = require('./lib/scripts');
const { E } = require('./lib/page');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');
const now = (v) => ({ then: (f) => { const r = f(v); return r && r.then ? r : now(r); }, catch: () => now(v) });

// A video: what it plays (src), and whether it plays
function video(o) {
  const v = E('video', { name: o.name || 'video', attrs: o.src ? { src: o.src } : {}, rect: o.rect || [0, 0, 1280, 720] });
  Object.assign(v, { paused: true, ended: false, currentSrc: o.src || '', played: 0 });
  v.play = () => { v.played++; return now(undefined); };
  return v;
}

// A frame (or the page itself, top) with these in it, told to play or not; the seconds go by with tick()
function frame(body, o) {
  o = o || {};
  const html = E('html', {}, [E('body', { rect: [0, 0, 1280, 720] }, body)]);
  const all = () => [html].concat(html.querySelectorAll('*'));
  const doc = { documentElement: html, body: html.children[0], readyState: 'complete', querySelectorAll: (s) => html.querySelectorAll(s),
    querySelector: (s) => html.querySelectorAll(s)[0] || null,
    elementFromPoint: (x, y) => all().filter((e) => e.rect && x >= e.rect[0] && x < e.rect[2] && y >= e.rect[1] && y < e.rect[3]).pop() || null };
  const f = { doc, timers: [], sent: [], notes: [], listener: null };
  const win = { innerWidth: 1280, innerHeight: 720, wrappedJSObject: o.page };
  win.top = o.top ? win : {};
  const browser = { runtime: { onMessage: { addListener: (l) => { f.listener = l; } },
    sendMessage: (m) => { f.sent.push(m); return now(o.answer ? o.answer(m) : undefined); } } };
  vm.runInContext(read('player-play.js'), vm.createContext({ window: win, document: doc, browser, location: { host: o.host || 'vidmoly.biz' },
    console: { info: (s) => f.notes.push(s) }, setInterval: (fn) => { f.timers.push(fn); return f.timers.length; },
    clearInterval: (n) => { f.timers[n - 1] = null; }, Array, String, Math }));
  f.tell = () => f.listener({ play: true });
  f.tick = (n) => { for (let i = 0; i < (n || 1); i++) f.timers.filter(Boolean).forEach((fn) => fn()); };
  return f;
}

// In the player's frame
let v = video({ src: 'blob:https://vidmoly.biz/1' });
let f = frame([v]);
f.tick(5);
check('a frame not told: nothing done (a trailer\'s frame plays nothing)', [v.played, f.timers.length], [0, 0]);
f.tell();
f.tick();
check('told: its film played as soon as it has something to play', v.played, 1);
v.paused = false;
f.tick();
v.paused = true;   // the viewer pauses it
f.tick(10);
check('...once it plays, never again by itself (the viewer may pause it)', [v.played, f.notes.slice(-1)[0]], [1, 'Twilight Movies, play: vidmoly.biz plays']);

// JW Player, its film loaded only once it's started: its own start pressed, now and then, a few times at most
const jw = E('div', { name: 'start', attrs: { class: 'jw-icon jw-icon-display jw-reset', role: 'button' }, rect: [600, 320, 680, 400] });
v = video({});
f = frame([E('div', { attrs: { class: 'jwplayer' }, rect: [0, 0, 1280, 720] }, [v, E('div', {
  attrs: { class: 'jw-display-icon-container jw-display-icon-display' }, rect: [600, 320, 680, 400] }, [jw])])]);
f.doc.readyState = 'interactive';
f.tell();
f.tick();
check('a player with nothing to play yet, still loading: nothing pressed', jw.calls, []);
f.doc.readyState = 'complete';
f.tick(3);
check('...loaded: its own start pressed (JW Player\'s button itself, not the box around it)', jw.calls, [['click']]);
f.tick(2);
check('...not again at once (a press on what plays pauses it)', jw.calls.length, 1);
f.tick(30);
check('...and four times at most', jw.calls.length, 4);

// JW Player's own scripts, which the frame's page has: they start it
let started = 0;
const start2 = E('div', { attrs: { class: 'jw-icon jw-icon-display' }, rect: [600, 320, 680, 400] });
f = frame([video({}), start2], { page: { jwplayer: () => ({ play: () => { started++; } }) } });
f.tell();
f.tick();
check('...or its own scripts start it, where the page has them (JW Player\'s), rather than a press', [started, start2.calls], [1, []]);

// No start of a known player's: what's in the middle (a veil that takes the first press), not an ad's link
const veil = E('div', { name: 'veil', rect: [0, 0, 1280, 720] });
f = frame([video({ rect: [0, 0, 1280, 720] }), veil]);
f.tell();
f.tick();
check('a player of its own: what\'s in its middle pressed', veil.calls, [['click']]);
const ad = E('a', { name: 'ad', attrs: { href: 'https://ads.example/' }, rect: [0, 0, 1280, 720] });
f = frame([video({}), ad]);
f.tell();
f.tick(10);
check('...not an ad\'s link there', ad.calls, []);
f = frame([video({ src: 'https://cdn.example/f.m3u8' })], { answer: (m) => (m.plays ? { play: true } : undefined) });
f.tick();
check('a frame that comes once the film was to start (another server\'s player) asks, and starts its own',
  [f.sent, f.doc.querySelector('video').played], [[{ plays: 1 }], 1]);

// The page itself: a player of its own (KRD WATCH's), only its video in the player alone
const trailer = video({ name: 'trailer', src: 'https://cdn.example/trailer.mp4', rect: [0, 0, 1920, 1080] });
const film = video({ name: 'film', src: '/api/v/Au7e' });
f = frame([trailer, E('div', { attrs: { 'data-tm-player': '' }, rect: [0, 0, 1280, 720] }, [film])], { top: true, host: 'krd.watch' });
f.tell();
f.tick();
check('the page itself: its player\'s own video, not another', [film.played, trailer.played, f.sent], [1, 0, []]);

// The background page: which frames are told
const told = [];
const frames = [{ frameId: 0, parentFrameId: -1 }, { frameId: 3, parentFrameId: 0 }, { frameId: 5, parentFrameId: 0 },
  { frameId: 7, parentFrameId: 5 }, { frameId: 9, parentFrameId: 7 }];
const posted = [];
let listeners = [];
const bg = vm.createContext({ URL, Promise, OWN: 'moz-extension://the-app/', port: { postMessage: (m) => posted.push(m) },
  browser: { runtime: { onMessage: { addListener: (l) => listeners.push(l) } },
    webNavigation: { getAllFrames: () => Promise.resolve(frames) },
    tabs: { sendMessage: (tab, m, o) => { told.push([tab, m, o.frameId]); return Promise.resolve(true); } } } });
vm.runInContext(read('alone.js') + '\n' + read('alone-play.js'), bg);
const asks = async (tab, m, frameId) => {
  for (const l of listeners) {
    const r = l(m, { tab: { id: tab }, frameId });
    if (r !== undefined) return r;
  }
  return undefined;
};
const opening = (tab, url, from) => vm.runInContext('aloneFor', bg)({ type: 'main_frame', tabId: tab, url, originUrl: from });

// The player alone's frames, as player.js tells them: each once
const P = vm.createContext({ window: {}, location: {}, Array, String });
const sentP = [];
P.browser = { runtime: { sendMessage: (m) => { sentP.push(m); return now(true); }, getFrameId: (el) => el.frameId } };
vm.runInContext(read('player-servers.js') + '\n' + read('player.js').replace('player.start();', ''), P);
const player = vm.runInContext('player', P);
const box = E('div', { attrs: { class: 'aspect-backdrop' } }, [Object.assign(E('iframe', {}), { frameId: 5 })]);
player.play(box);
player.play(box);
check('the page: the frame its player alone is in told to start its film, once', sentP, [{ play: 5 }]);
player.play(E('div', {}, [video({ src: '/api/v/x' })]));
check('...a player of its own: the page itself (0)', sentP.slice(-1), [{ play: 0 }]);

module.exports = (async () => {
  opening(4, 'https://beenar.net/detail-cowatching/3057?series=false', 'moz-extension://the-app/ui/index.html#/title/beenar/f%3A3057');
  check('the app told when the page shown is the Watch\'s (a window it opens is an ad\'s)', posted, [{ watch: true }]);
  await asks(4, { play: 5 });
  await new Promise((r) => setTimeout(r, 0));
  check('the background page: the player\'s frame told, and the frames in it; not another of the page (a trailer\'s)',
    told.map((t) => t[2]).sort(), [5, 7, 9]);
  check('...a frame that comes later asks: one in the player\'s, yes; another, no',
    [await asks(4, { plays: 1 }, 9), await asks(4, { plays: 1 }, 3)], [{ play: true }, { play: false }]);
  told.length = 0;
  await asks(4, { play: 0 });
  check('...a player of the page\'s own: the page itself only', told.map((t) => t[2]), [0]);
  told.length = 0;
  await asks(6, { play: 5 });
  check('...nothing for a tab the Twilight screens didn\'t open', told, []);
  opening(4, 'https://krd.watch/', undefined);
  check('the tab opens another page: no frame of it starts its film, and the app is told it isn\'t the Watch\'s',
    [await asks(4, { plays: 1 }, 9), posted.slice(-1)], [{ play: false }, [{ watch: false }]]);
})();
