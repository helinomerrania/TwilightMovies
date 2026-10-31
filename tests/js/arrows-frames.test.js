// The remote's arrows in Firefox's engine (app/src/main/assets/gecko/arrows*.js) between a page and its frames (a
// film's player, often a frame in a frame): into a frame when it has something for the remote, past it when it
// hasn't or doesn't answer, out of it at its edge, and what has the remote in it brought into view in the page around.
'use strict';
const { check } = require('./lib/check');
const { NEVER, E, page, press, at, called, find, put } = require('./lib/page');

// The page with the player in it
const framed = (answer) => page([
  E('a', { name: 'home', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('iframe', { name: 'player', frameId: 4, rect: [0, 100, 1920, 1000] }),
  E('a', { name: 'below', attrs: { href: '/' }, rect: [100, 1020, 300, 1070] }),
], { answer });
let p = framed((m) => (m.arrows === 'in' ? { ok: true, rect: { l: 10, t: 10, r: 110, b: 60 } } : true));
put(p, 'home');
let r = press(p, 'ArrowDown');
check('down to a frame: the frame gets the remote, and is asked whether it has something for it, coming in from above',
  [called(find(p, 'player'), 'focus').length, p.sent[0]], [1, { arrows: 'in', frame: 4, way: 'down', wait: 1250 }]);
check('...it has: the remote is in it (the page doesn\'t scroll)', [at(p), r.used, p.scrolled.length], ['player', true, 0]);
p = framed((m) => (m.arrows === 'in' ? { ok: false } : true));
put(p, 'home');
press(p, 'ArrowDown');
check('...it hasn\'t: on past it', at(p), 'below');
p = framed(() => NEVER);
put(p, 'home');
press(p, 'ArrowDown');
r = press(p, 'ArrowDown');
check('...no answer yet: the arrows wait', [at(p), r.used, p.sent.length], ['player', true, 1]);
p.timers.filter(Boolean).forEach((f) => f());
check('...and after a while, on past it', at(p), 'below');
p = framed(() => true);
find(p, 'player').frameId = undefined;
put(p, 'home');
press(p, 'ArrowDown');
check('...a frame the engine doesn\'t know: on past it', [at(p), p.sent.length], ['below', 0]);
p = framed(() => true);
p.doc.activeElement = find(p, 'player');
p.fromBackground({ arrows: 'out', way: 'down' });
check('the remote comes out of the frame, going down: to what\'s below the frame', at(p), 'below');
p.doc.activeElement = find(p, 'player');
p.fromBackground({ arrows: 'shown', rect: { l: 10, t: 950, r: 110, b: 1000 } });
check('what has the remote in the frame, off the screen here: the page scrolls to it', p.scrolled[0], { left: 0, top: 28, behavior: 'instant' });

// The player itself, a frame
const player = (o) => page([
  E('button', { name: 'play', rect: [100, 600, 160, 660] }), E('button', { name: 'next', rect: [100, 700, 160, 760] }),
], Object.assign({ frame: true }, o));
p = player();
p.fromBackground({ arrows: 'in', way: 'down' });
check('coming into the frame from above: the first thing from the top', at(p), 'play');
check('...and the page around isn\'t sent where it is (it\'s in the answer)', p.sent.length, 0);
let answer;
p.fromBackground({ arrows: 'in', way: 'down' }).then((a) => { answer = a; });
check('...(asked again: where it is now)', answer, { ok: true, rect: { l: 100, t: 600, r: 160, b: 660 } });
press(p, 'ArrowDown');
check('moving in the frame: the page around is told, to show it', p.sent[0], { arrows: 'shown', rect: { l: 100, t: 700, r: 160, b: 760 }, up: true });
r = press(p, 'ArrowDown');
check('at the frame\'s end, with nothing to scroll: the page around takes the remote on', [p.sent[1], r.used], [{ arrows: 'out', way: 'down', up: true }, true]);
p = player({ scroll: { scrollHeight: 2000 } });
p.fromBackground({ arrows: 'in', way: 'down' });
press(p, 'ArrowDown');
r = press(p, 'ArrowDown');
check('...a frame that scrolls: it scrolls first', [r.used, p.sent.filter((m) => m.arrows === 'out').length], [false, 0]);
p = page([E('div', { name: 'nothing', rect: [0, 0, 100, 100] })], { frame: true });
p.fromBackground({ arrows: 'in', way: 'down' }).then((a) => { answer = a; });
check('a frame with nothing for the remote says so', answer, { ok: false });

// A frame in a frame: the player's page inside KurdFilm's, the player inside that
const outer = (answer) => page([
  E('iframe', { name: 'player', frameId: 9, rect: [0, 50, 1920, 1000] }),
  E('button', { name: 'below-player', rect: [100, 1010, 300, 1060] }),
], { frame: true, answer });
p = outer((m) => (m.arrows === 'in' && m.frame === 9 ? { ok: true, rect: { l: 800, t: 500, r: 1100, b: 580 } } : true));
p.fromBackground({ arrows: 'in', way: 'down', wait: 1250 }).then((a) => { answer = a; });
check('into a frame with a frame in it: that frame gets the remote, and is asked in turn', [at(p), p.sent[0]],
  ['player', { arrows: 'in', frame: 9, way: 'down', wait: 1000 }]);
check('...where its remote is, as the page around sees it', answer, { ok: true, rect: { l: 800, t: 550, r: 1100, b: 630 } });
p = outer((m) => (m.arrows === 'in' && m.frame === 9 ? { ok: false } : true));
p.fromBackground({ arrows: 'in', way: 'down', wait: 1250 }).then((a) => { answer = a; });
check('...the frame inside has nothing for it: on past it', [at(p), answer.ok], ['below-player', true]);

// KurdFilm's player: kept out of the keys' way (tabindex -1), under a see-through layer that catches clicks
p = page([
  E('a', { name: 'home', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('iframe', { name: 'player', frameId: 4, attrs: { tabindex: '-1' }, rect: [0, 100, 1920, 1000] }),
  E('div', { name: 'layer', z: 3, rect: [0, 100, 1920, 1000], style: { position: 'absolute' } }),
], { answer: (m) => (m.arrows === 'in' ? { ok: true, rect: { l: 10, t: 10, r: 110, b: 60 } } : true) });
put(p, 'home');
press(p, 'ArrowDown');
check('a player kept out of the keys\' way, under a see-through layer: gone into all the same', at(p), 'player');
p = page([E('div', { name: 'nothing', rect: [0, 0, 100, 100] })], { frame: true });
p.fromBackground({ arrows: 'in', way: 'down' });
check('...and a frame with nothing for the remote says so in Android\'s log', p.notes[0],
  'Twilight Movies, the remote: nothing for it in films.example, coming down');
