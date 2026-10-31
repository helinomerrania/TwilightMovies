// The remote's arrows in Firefox's engine (app/src/main/assets/gecko/arrows*.js), on stand-in pages: what the remote
// can't go to, a window over the page, a player full screen, a link too far away, and the fields and sliders that
// keep the arrows that are theirs.
'use strict';
const { check } = require('./lib/check');
const { E, page, press, at, find, put } = require('./lib/page');

// What the remote can't go to
let p = page([
  E('a', { name: 'from', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('button', { name: 'off', disabled: true, rect: [100, 60, 300, 100] }),
  E('a', { name: 'kept-out', attrs: { href: '/', tabindex: '-1' }, rect: [100, 110, 300, 150] }),
  E('a', { name: 'hidden', attrs: { href: '/' }, rect: [100, 160, 300, 200], style: { visibility: 'hidden' } }),
  E('div', { name: 'unused', attrs: { inert: '' }, rect: [100, 210, 300, 250] },
    [E('a', { name: 'in-unused', attrs: { href: '/' }, rect: [100, 210, 300, 250] })]),
  E('div', { name: 'menu', rect: [100, 260, 300, 260], style: { overflowX: 'hidden', overflowY: 'hidden' } },
    [E('a', { name: 'in-closed-menu', attrs: { href: '/' }, rect: [100, 262, 300, 300] })]),
  E('input', { name: 'secret', type: 'hidden', rect: [100, 310, 300, 350] }),
  E('iframe', { name: 'ad', frameId: 3, rect: [100, 360, 101, 361] }),
  E('a', { name: 'under-veil', attrs: { href: '/' }, rect: [100, 370, 300, 420] }),
  E('div', { name: 'veil', z: 5, rect: [0, 365, 1920, 425], style: { position: 'absolute' } }),
  E('a', { name: 'under-bar', attrs: { href: '/' }, rect: [100, 430, 300, 480] }),
  E('div', { name: 'bar', z: 5, rect: [0, 430, 1920, 470], style: { position: 'fixed' } }),
]);
put(p, 'from');
press(p, 'ArrowDown');
check('down past what\'s switched off, kept out of the keys\' way, hidden, out of use, in a closed menu, a hidden field, '
  + 'a tiny frame (an ad\'s) and what\'s under something: to what\'s only under a bar fixed to the screen', at(p), 'under-bar');

// A window over the page, and full screen
p = page([
  E('a', { name: 'page-link', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('div', { name: 'dialog', attrs: { 'aria-modal': 'true', role: 'dialog' }, rect: [600, 300, 1300, 800] }, [
    E('button', { name: 'yes', rect: [700, 600, 900, 660] }), E('button', { name: 'no', rect: [1000, 600, 1200, 660] })]),
  E('a', { name: 'page-below', attrs: { href: '/' }, rect: [700, 900, 900, 950] }),
]);
press(p, 'ArrowDown');
check('a window over the page: the remote goes into it', at(p), 'yes');
press(p, 'ArrowRight');
check('...and stays in it', at(p), 'no');
let r = press(p, 'ArrowDown');
check('...even where the page has more', [at(p), r.used], ['no', false]);
p = page([
  E('a', { name: 'header-link', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('div', { name: 'player', rect: [0, 0, 1920, 1080] }, [
    E('button', { name: 'play', rect: [100, 950, 160, 1010] }), E('button', { name: 'next', rect: [200, 950, 260, 1010] })]),
]);
p.doc.fullscreenElement = find(p, 'player');
press(p, 'ArrowRight');
press(p, 'ArrowRight');
check('a player full screen: only its own buttons', at(p), 'next');

// A link too far away
p = page([E('a', { name: 'top', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('a', { name: 'far', attrs: { href: '/' }, rect: [100, 1500, 300, 1550] })], { scroll: { scrollHeight: 3000 } });
put(p, 'top');
r = press(p, 'ArrowDown');
check('the next link more than a third of the screen below it: the page scrolls towards it first', [at(p), r.used], ['top', false]);
find(p, 'far').rect = [100, 1300, 300, 1350];
press(p, 'ArrowDown');
check('...and goes to it once it\'s near', at(p), 'far');

// Fields and sliders keep the arrows that are theirs
p = page([
  E('a', { name: 'up-there', attrs: { href: '/' }, rect: [100, 0, 300, 50] }),
  E('textarea', { name: 'note', rect: [100, 100, 600, 300], value: 'one\ntwo', caret: 4 }),
  E('input', { name: 'volume', type: 'range', rect: [100, 400, 600, 440] }),
  E('a', { name: 'down-there', attrs: { href: '/' }, rect: [100, 500, 300, 550] }),
]);
put(p, 'note');
r = press(p, 'ArrowUp');
check('up in a text with the caret inside it: the caret\'s', [at(p), r.used], ['note', false]);
find(p, 'note').selectionStart = find(p, 'note').selectionEnd = 0;
press(p, 'ArrowUp');
check('...at the start: out', at(p), 'up-there');
put(p, 'volume');
r = press(p, 'ArrowLeft');
check('left on a slider: the slider\'s', [at(p), r.used], ['volume', false]);
press(p, 'ArrowDown');
check('...down: out', at(p), 'down-there');
