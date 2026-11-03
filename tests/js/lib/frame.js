// For the tests of the Watch's film (app/src/main/assets/gecko/player-ends.js and player-play.js, in every frame of
// the page): a stand-in video (what it plays, whether it plays), and a stand-in frame (or the page itself, top)
// with these in it (what its page shows: their words), the scripts loaded as the manifest loads them; told to play
// or not, the seconds going by with tick(), an event of the page's (a video's end) with fire(); what it sent, but its
// ask whether it's a player's (plays) apart.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { ROOT } = require('./scripts');
const { E } = require('./page');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');
const now = (v) => ({ then: (f) => { const r = f(v); return r && r.then ? r : now(r); }, catch: () => now(v) });

function video(o) {
  const v = E('video', { name: o.name || 'video', attrs: o.src ? { src: o.src } : {}, rect: o.rect || [0, 0, 1280, 720] });
  Object.assign(v, { paused: true, ended: false, currentSrc: o.src || '', played: 0, duration: o.duration || NaN, error: o.error || null });
  v.play = () => { v.played++; return now(undefined); };
  return v;
}

function frame(body, o) {
  o = o || {};
  const html = E('html', {}, [E('body', { rect: [0, 0, 1280, 720] }, body)]);
  const all = () => [html].concat(html.querySelectorAll('*'));
  const events = [];
  const doc = { documentElement: html, body: html.children[0], readyState: 'complete', querySelectorAll: (s) => html.querySelectorAll(s),
    querySelector: (s) => html.querySelectorAll(s)[0] || null, addEventListener: (type, fn, capture) => events.push({ type, fn, capture }),
    elementFromPoint: (x, y) => all().filter((e) => e.rect && x >= e.rect[0] && x < e.rect[2] && y >= e.rect[1] && y < e.rect[3]).pop() || null };
  Object.defineProperty(doc.body, 'innerText', { get: () => doc.body.querySelectorAll('*').map((e) => e.textContent).join('\n') });
  const f = { doc, timers: [], sent: [], asked: [], notes: [], listener: null };
  const win = { innerWidth: 1280, innerHeight: 720, wrappedJSObject: o.page };
  win.top = o.top ? win : {};
  const browser = { runtime: { onMessage: { addListener: (l) => { f.listener = l; } },
    sendMessage: (m) => { (m.plays ? f.asked : f.sent).push(m); return now(o.answer ? o.answer(m) : undefined); } } };
  const ctx = vm.createContext({ window: win, document: doc, browser, location: { host: o.host || 'vidmoly.biz' },
    console: { info: (s) => f.notes.push(s) }, setInterval: (fn) => { f.timers.push(fn); return f.timers.length; },
    clearInterval: (n) => { f.timers[n - 1] = null; }, Array, String, Math, RegExp });
  vm.runInContext(read('player-ends.js') + '\n' + read('player-play.js'), ctx);
  f.tell = () => f.listener({ play: true });
  f.tick = (n) => { for (let i = 0; i < (n || 1); i++) f.timers.filter(Boolean).forEach((fn) => fn()); };
  f.fire = (type, target) => events.filter((e) => e.type === type).forEach((e) => e.fn({ type, target }));
  return f;
}

module.exports = { read, now, video, frame };
