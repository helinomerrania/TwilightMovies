// The player alone with the website's servers (app/src/main/assets/gecko/player-servers.js, in the page): their
// names in the app's own row, on stand-in pages as Beenar's and Kurd Movie's have them; the one playing marked;
// pressing one presses the website's own. In the background page (alone.js): the row's style, and every frame of the
// page told the remote goes up to it (lift). In the player's frames (arrows*.js): up goes out to the row, before the
// player's own use of the key.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');
const { E, page, press, at, find, own } = require('./lib/page');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');

// A stand-in page the row can be made in
function doc(body) {
  const html = E('html', {}, [E('body', {}, body)]);
  const d = { documentElement: html, body: html.children[0] };
  const able = (x) => {
    x.listeners = [];
    x.setAttribute = (n, v) => { x.attrs[n] = String(v); };
    x.removeAttribute = (n) => { delete x.attrs[n]; };
    x.querySelector = (q) => x.querySelectorAll(q)[0] || null;
    x.appendChild = (c) => { x.children.push(c); c.parentElement = x; };
    x.removeChild = (c) => { x.children.splice(x.children.indexOf(c), 1); c.parentElement = null; };
    x.addEventListener = (type, f) => x.listeners.push(f);
    return x;
  };
  d.querySelectorAll = (s) => html.querySelectorAll(s);
  d.querySelector = (s) => d.querySelectorAll(s)[0] || null;
  d.createElement = (tag) => able(E(tag, { text: '' }));
  [html].concat(html.querySelectorAll('*')).forEach(able);
  return d;
}
const row = (d) => d.querySelector('[data-tm-servers]');
const names = (d) => row(d).children.map((b) => b.textContent);
const marked = (d) => row(d).children.map((b) => 'data-tm-on' in b.attrs);
const pressRow = (d, i) => row(d).children[i].listeners.forEach((f) => f());

const P = vm.createContext({ Array, String });
vm.runInContext(read('player-servers.js'), P);
vm.runInContext(read('player.js').replace('player.start();', ''), Object.assign(P, { window: {}, location: {} }));
const s = vm.runInContext('playerServers', P);
const SITES = vm.runInContext('player.SITES', P);

// As Beenar's page has them: its own server first (the one it plays), each with its name and quality
const item = (name, on) => E('button', { name, attrs: { type: 'button', class: 'server-item' + (on ? ' server-item--active' : '') } }, [
  E('img', { attrs: { class: 'server-item-icon' } }), E('span', { text: name, attrs: { class: 'server-item-name' } }),
  E('span', { text: '1080p', attrs: { class: 'server-item-q' } })]);
const servers = [item('Beenar', true), item('Vidmoly'), item('StreamSB')];
let d = doc([E('div', { attrs: { class: 'server-list' } }, servers)]);
let pressed = [];
const shown = s.show(d, SITES['beenar.net'].servers, (i) => pressed.push(i));
check('Beenar: its servers\' names in a row of the app\'s own (not their quality), the one it plays marked; the page marked',
  [shown, names(d), marked(d), 'data-tm-with-servers' in d.documentElement.attrs], [true, ['Beenar', 'Vidmoly', 'StreamSB'], [true, false, false], true]);
pressRow(d, 1);
check('...another pressed: the website\'s own pressed, and marked in the row; the player looked for again',
  [servers.map((x) => x.calls.length), marked(d), pressed], [[0, 1, 0], [false, true, false], [1]]);
s.show(d, SITES['beenar.net'].servers, () => {});
check('...still marked while the website marks it later', [marked(d), d.querySelectorAll('[data-tm-servers]').length], [[false, true, false], 1]);
d.body.children[0].children.pop();
s.show(d, SITES['beenar.net'].servers, () => {});
check('...the website\'s servers changed: the row made again', [names(d), d.querySelectorAll('[data-tm-servers]').length], [['Beenar', 'Vidmoly'], 1]);

// As Kurd Movie's page has them: its trailer first, the one it plays marked "on"
s.chosen = -1;
const option = (nume, title, on) => E('li', { attrs: { id: 'player-option-' + nume, class: 'dooplay_player_option' + (on ? ' on' : ''),
  'data-nume': nume } }, [E('i', { text: '' }), E('span', { text: title, attrs: { class: 'title' } }), E('span', { text: '', attrs: { class: 'loader' } })]);
d = doc([E('ul', {}, [option('trailer', 'Watch trailer'), option('1', 'ok 4K'), option('2', 'abyss 4k', true), option('3', 'streamhg')])]);
s.show(d, SITES['www.kurd-movie.com'].servers, () => {});
check('Kurd Movie: its servers, not its trailer; the one it marks "on"', [names(d), marked(d)],
  [['ok 4K', 'abyss 4k', 'streamhg'], [false, true, false]]);
d = doc([E('div', { attrs: { class: 'server-list' } }, [item('Beenar', true)])]);
check('one server only: no row', [s.show(d, SITES['beenar.net'].servers, () => {}), row(d), 'data-tm-with-servers' in d.documentElement.attrs],
  [false, null, false]);

// The background page's side
let listener = null;
const told = [];
const css = [];
const browser = { runtime: { onMessage: { addListener: (f) => { listener = f; } } },
  tabs: { sendMessage: (tab, m, o) => { told.push([tab, m, o]); return Promise.resolve(true); },
    insertCSS: (tab, o) => { css.push(o.code); return Promise.resolve(); } } };
const bg = vm.createContext({ browser, URL, Promise, OWN: 'moz-extension://the-app/' });
vm.runInContext(read('alone.js'), bg);
const opening = (tab, url, from) => vm.runInContext('aloneFor', bg)({ type: 'main_frame', tabId: tab, url, originUrl: from });
const asks = (tab, m) => listener(m, { tab: { id: tab } });

module.exports = (async () => {
  opening(3, 'https://beenar.net/detail-cowatching/3057?series=false', 'moz-extension://the-app/ui/index.html#/title/beenar/f%3A3057');
  await asks(3, { aloneStyle: 1 });
  ok('the row\'s style: at the top of the screen, the player below it', /\[data-tm-servers\] \{ position: fixed !important; top: 0/.test(css[0])
    && /\[data-tm-with-servers\] \[data-tm-player\] \{ top: 7vh !important; height: 93vh/.test(css[0]));
  check('a frame of the page asks before the row shows: no', await asks(3, { lifted: 1 }), { lift: false });
  await asks(3, { lift: 1 });
  check('the row shows: every frame of the page told the remote goes up to it', told, [[3, { arrows: 'lift' }, undefined]]);
  check('...a frame that comes later asks: yes; another tab\'s: no', [await asks(3, { lifted: 1 }), await asks(4, { lifted: 1 })],
    [{ lift: true }, { lift: false }]);
  await asks(5, { lift: 1 });
  check('...not a tab the Twilight screens didn\'t open', told.length, 1);
  opening(3, 'https://krd.watch/', undefined);
  check('the tab opens another page: no row there', await asks(3, { lifted: 1 }), { lift: false });

  // In the player's frame (it uses up for its own: the sound)
  const player = (answer) => {
    const p = page([E('button', { name: 'play', rect: [100, 600, 160, 660] })], { frame: true, answer });
    own(p, 'keydown', (e) => { if (e.key === 'ArrowUp') e.preventDefault(); });
    p.doc.activeElement = find(p, 'play');
    return p;
  };
  let p = player();
  let r = press(p, 'ArrowUp');
  check('a player\'s frame, no row over it: up is the player\'s', [r.used, r.stopped, p.sent.length, p.asks], [true, false, 0, [{ lifted: 1 }]]);
  p.fromBackground({ arrows: 'lift' });
  r = press(p, 'ArrowUp');
  check('...the row shows over it: up goes out to it, before the player has it', [r.used, r.stopped, p.sent], [true, true,
    [{ arrows: 'out', way: 'up', up: true }]]);
  p = player((m) => (m.lifted ? { lift: true } : true));
  press(p, 'ArrowUp');
  check('...a frame that comes once the row shows: the same', p.sent, [{ arrows: 'out', way: 'up', up: true }]);
  p = page([E('button', { name: 'settings', rect: [100, 0, 160, 40] }), E('iframe', { name: 'inner', frameId: 9, rect: [0, 50, 1920, 1000] })],
    { frame: true, answer: (m) => (m.lifted ? { lift: true } : true) });
  p.doc.activeElement = find(p, 'inner');
  p.fromBackground({ arrows: 'out', way: 'up' });
  check('...a frame with the player\'s frame in it: on out, not to its own parts', [at(p), p.sent], ['inner', [{ arrows: 'out', way: 'up', up: true }]]);
  p = page([E('button', { name: 'Vidmoly', rect: [860, 0, 1060, 70] }), E('iframe', { name: 'frame', frameId: 4, rect: [0, 76, 1920, 1080] })]);
  p.fromBackground({ arrows: 'lift' });
  p.doc.activeElement = find(p, 'frame');
  p.fromBackground({ arrows: 'out', way: 'up' });
  check('the page: out of the player\'s frame, up, to the row', at(p), 'Vidmoly');
})();
