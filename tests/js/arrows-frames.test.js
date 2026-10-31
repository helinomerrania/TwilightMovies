// The remote's arrows in Firefox's engine (app/src/main/assets/gecko/arrows*.js) between a page and its frames (a
// film's player): into a frame when it has something for the remote, past it when it hasn't or doesn't answer, out
// of it at its edge, and what has the remote in it brought into view in the page around.
'use strict';
const { check } = require('./lib/check');
const { NEVER, E, page, press, at, find, put } = require('./lib/page');

// The page with the player in it
const framed = (answer) => page([
  E('a', { name: 'home', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('iframe', { name: 'player', frameId: 4, rect: [0, 100, 1920, 1000] }),
  E('a', { name: 'below', attrs: { href: '/' }, rect: [100, 1020, 300, 1070] }),
], { answer });
let p = framed((m) => (m.arrows === 'in' ? { ok: true, rect: { l: 10, t: 10, r: 110, b: 60 } } : true));
put(p, 'home');
let r = press(p, 'ArrowDown');
check('down to a frame: it\'s asked whether it has something for the remote, coming in from above', p.sent[0], { arrows: 'in', frame: 4, way: 'down' });
check('...it has: the remote is in it (the page doesn\'t scroll)', [at(p), r.used], ['player', true]);
p = framed((m) => (m.arrows === 'in' ? { ok: false } : true));
put(p, 'home');
press(p, 'ArrowDown');
check('...it hasn\'t: on past it', at(p), 'below');
p = framed(() => NEVER);
put(p, 'home');
press(p, 'ArrowDown');
r = press(p, 'ArrowDown');
check('...no answer yet: the arrows wait', [at(p), r.used, p.sent.length], ['home', true, 1]);
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
