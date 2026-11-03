// Up to the website's servers over its player (app/src/main/assets/gecko/arrows*.js, lift: the row shows,
// player-servers.js): in the player's frame, up goes out to the row before the player has it (players use it for
// their sound); a frame with the player's frame in it passes it on out; the page takes it on to the row. A player of
// the page's own, which takes every key (Kurdsaz's): up goes to the row from it, or from nothing.
'use strict';
const { check } = require('./lib/check');
const { E, page, press, at, find, own } = require('./lib/page');

const usesUp = (p) => own(p, 'keydown', (e) => { if (e.key === 'ArrowUp') e.preventDefault(); });

// In the player's frame (it uses up for its own: the sound)
const player = (answer) => {
  const p = page([E('button', { name: 'play', rect: [100, 600, 160, 660] })], { frame: true, answer });
  usesUp(p);
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

// The page: the row at the top, the player below it
const withRow = (kids) => page([E('div', { name: 'row', rect: [0, 0, 1920, 75] }, [E('button', { name: 'Vidmoly', rect: [860, 4, 1060, 70] }),
  E('button', { name: 'Beenar', rect: [1080, 4, 1280, 70] })])].concat(kids));
p = withRow([E('iframe', { name: 'frame', frameId: 4, attrs: { 'data-tm-player': '' }, rect: [0, 76, 1920, 1080] })]);
p.fromBackground({ arrows: 'lift' });
p.doc.activeElement = find(p, 'frame');
p.fromBackground({ arrows: 'out', way: 'up' });
check('the page: out of the player\'s frame, up, to the row', at(p), 'Vidmoly');

// A player of the page's own, which takes every key (Kurdsaz's own server: its video in the page)
const own_ = () => withRow([E('div', { name: 'wrapper', attrs: { id: 'player-wrapper', 'data-tm-player': '' }, rect: [0, 76, 1920, 1080] }, [
  E('video', { name: 'video', rect: [0, 76, 1920, 1080] }), E('button', { name: 'play', rect: [900, 980, 960, 1040] })])]);
p = own_();
usesUp(p);
r = press(p, 'ArrowUp');
check('a player of the page\'s own, no row: up is its own (its sound)', [r.used, r.stopped, at(p)], [true, false, null]);
p = own_();
usesUp(p);
p.fromBackground({ arrows: 'lift' });
r = press(p, 'ArrowUp');
check('...with the row: up goes to it, from nothing having the remote, before the player has it', [r.stopped, at(p)], [true, 'Vidmoly']);
p = own_();
usesUp(p);
p.fromBackground({ arrows: 'lift' });
p.doc.activeElement = find(p, 'play');
r = press(p, 'ArrowUp');
check('...or from its own button', [r.stopped, at(p)], [true, 'Vidmoly']);
r = press(p, 'ArrowUp');
check('...on the row already: up is as it always was', r.stopped, false);
