// The remote's arrows in Firefox's engine (app/src/main/assets/gecko/arrows*.js) on KurdFilm's TV page, as Firefox's
// engine lays it out on a TV (its header, its search, a row of films going off the screen): where each arrow takes
// the remote, typing in the search, the website's own use of a key first, and what OK does. (What the remote can't
// go to, windows over the page and fields: arrows-reach.test.js; frames: arrows-frames.test.js.)
'use strict';
const { check, ok } = require('./lib/check');
const { E, page, press, at, called, find, put, own } = require('./lib/page');

// KurdFilm's TV page in Firefox's engine on a 1920 x 1080 TV, as its accessibility tree had it (right to left)
function kurdfilm(typed) {
  const posters = [1585, 1223, 862, 500, 139, -223, -584].map((x, i) => E('a', { name: 'film' + (i + 1),
    attrs: { href: '/movie/' + (i + 1) }, rect: [x, 642, x + 351, 1170] }, [E('img', { name: 'poster' + (i + 1),
    rect: [x + 16, 642, x + 335, 1170] })]));
  return page([
    E('header', { rect: [0, 0, 1920, 174] }, [
      E('a', { name: 'logo', attrs: { href: '/tv' }, rect: [1535, 50, 1843, 122] }),
      E('a', { name: 'films', attrs: { href: '/tv/movies' }, rect: [1238, 36, 1447, 136] }),
      E('a', { name: 'series', attrs: { href: '/tv/series' }, rect: [999, 36, 1208, 136] }),
      E('a', { name: 'sorani', attrs: { href: '/tv/dialect/sorani' }, rect: [295, 36, 480, 136] }),
      E('a', { name: 'badini', attrs: { href: '/tv/dialect/badini' }, rect: [97, 36, 265, 136] }),
    ]),
    E('form', { rect: [0, 286, 1920, 459] }, [
      E('input', { name: 'search', type: 'search', rect: [301, 286, 1920, 459], style: { direction: 'rtl' },
        value: typed || '', caret: (typed || '').length }),
      E('button', { name: 'go', rect: [0, 286, 273, 459] }),
    ]),
    E('a', { name: 'all', attrs: { href: '/tv/movies/new' }, rect: [1414, 556, 1582, 608] }),
    E('div', { name: 'row', rect: [0, 642, 1920, 1170], style: { overflowX: 'auto', overflowY: 'hidden' } }, posters),
  ]);
}

// The header, the search, the row of films
let p = kurdfilm();
let r = press(p, 'ArrowDown');
check('nothing has the remote: down goes to the top of the page (the first of the header)', at(p), 'films');
check('...and the page doesn\'t scroll by it', r.used, true);
check('...shown as the remote\'s, and not jumped to (the arrows scroll it into view)', called(find(p, 'films'), 'focus')[0][1],
  { preventScroll: true, focusVisible: true });
check('...into view as little as it must', called(find(p, 'films'), 'scrollIntoView')[0][1],
  { block: 'nearest', inline: 'nearest', behavior: 'instant' });
ok('...and Android\'s log says where it went', p.notes[0] === 'Twilight Movies, the remote: down to a "films"', p.notes[0]);
press(p, 'ArrowLeft');
check('left, along the header', at(p), 'series');
press(p, 'ArrowLeft');
press(p, 'ArrowLeft');
check('...to its end', at(p), 'badini');
press(p, 'ArrowRight');
check('right, back', at(p), 'sorani');
put(p, 'films');
press(p, 'ArrowDown');
check('down from the header: the search right below it, not the button off to the side', at(p), 'search');
press(p, 'ArrowLeft');
check('left out of the empty search: its button', at(p), 'go');
put(p, 'search');
press(p, 'ArrowDown');
check('down from the search: "see all", straight below', at(p), 'all');
press(p, 'ArrowDown');
check('down: the film below it, of the row', at(p), 'film2');
press(p, 'ArrowRight');
check('right along the row', at(p), 'film1');
put(p, 'film5');
press(p, 'ArrowLeft');
check('left to the film half off the screen', at(p), 'film6');
r = press(p, 'ArrowLeft');
check('...and on to the one the row hides, from inside the row', [at(p), r.used], ['film7', true]);
ok('...which the row scrolls into view', called(find(p, 'film7'), 'scrollIntoView').length === 1);
put(p, 'film6');
press(p, 'ArrowUp');
check('up from the films: the button above (straight up), not the search further over', at(p), 'go');
put(p, 'badini');
r = press(p, 'ArrowLeft');
check('left at the header\'s end: the films the row hides aren\'t reached from outside it', [at(p), r.used], ['badini', false]);
put(p, 'logo');
r = press(p, 'ArrowUp');
check('up at the top, with nothing that way: the page scrolls as it would (the key is the page\'s)', [at(p), r.used], ['logo', false]);
r = press(p, 'ArrowDown', { shiftKey: true });
check('an arrow with shift is the page\'s', [at(p), r.used], ['logo', false]);

// Typing in the search
p = kurdfilm('abc');
put(p, 'search');
r = press(p, 'ArrowRight');
check('a right-to-left search with text: right moves the caret back (the field\'s own)', [at(p), r.used], ['search', false]);
r = press(p, 'ArrowLeft');
check('...left, at the end of the text, leaves it', [at(p), r.used], ['go', true]);
put(p, 'search');
find(p, 'search').selectionStart = find(p, 'search').selectionEnd = 1;
r = press(p, 'ArrowLeft');
check('...in the middle, it moves the caret', [at(p), r.used], ['search', false]);
press(p, 'ArrowUp');
check('...up and down always leave a search', at(p), 'films');

// The website's own keys first
p = kurdfilm();
own(p, 'keydown', (e) => { if (e.key === 'ArrowDown') e.preventDefault(); });
r = press(p, 'ArrowDown');
check('a website that moves by the arrows itself (it says so): the remote stays where the website puts it', [at(p), r.used], [null, true]);
p = kurdfilm();
press(p, 'ArrowDown');
p.win.addEventListener('keydown', (e) => { if (e.key === 'ArrowLeft') e.preventDefault(); }, false);
press(p, 'ArrowLeft');
check('...even listening on the window, after the arrows did', at(p), 'films');
press(p, 'ArrowRight');
check('...the keys it leaves alone still move the remote', at(p), 'logo');

// OK on the page
p = kurdfilm();
put(p, 'films');
r = press(p, 'Enter');
check('OK on a link: the link\'s own (no click of the app\'s, the page sees it)', [r.used, called(find(p, 'films'), 'click').length], [false, 0]);
put(p, 'search');
let seen = 0;
own(p, 'keydown', (e) => { if (e.key === 'Enter') seen++; });
own(p, 'keyup', (e) => { if (e.key === 'Enter') seen++; });
r = press(p, 'Enter');
check('OK on the search: the on-screen keyboard (the app is asked)', p.sent.filter((m) => m.keyboard), [{ keyboard: 1 }]);
check('...and the page sees neither OK going down nor coming up (no empty search)', [r.used, r.stopped, r.upUsed, seen], [true, true, true, 0]);
find(p, 'search').value = 'batman';
r = press(p, 'Enter');
check('OK once something is typed: the search\'s own (it searches)', [r.used, seen, p.sent.filter((m) => m.keyboard).length], [false, 2, 1]);
find(p, 'films').focus();
find(p, 'search').focus();
press(p, 'Enter');
check('back on the search later: OK opens the keyboard again', p.sent.filter((m) => m.keyboard).length, 2);
p = page([
  E('div', { name: 'card', attrs: { tabindex: '0' }, rect: [100, 100, 400, 500] }),
  E('select', { name: 'list', rect: [500, 100, 700, 160] }),
  E('select', { name: 'old-list', rect: [800, 100, 900, 160], noPicker: true }),
  E('input', { name: 'tick', type: 'checkbox', rect: [1000, 100, 1040, 140] }),
  E('button', { name: 'button', rect: [1100, 100, 1300, 160] }),
]);
put(p, 'card');
r = press(p, 'Enter');
check('OK on a card the website only clicks on: clicked, when OK comes up', [called(find(p, 'card'), 'click').length, r.used], [1, false]);
own(p, 'keydown', (e) => { if (e.key === 'Enter') e.preventDefault(); });
press(p, 'Enter');
check('...unless the website did something with OK itself', called(find(p, 'card'), 'click').length, 1);
p.own.pop();
put(p, 'list');
r = press(p, 'Enter');
check('OK on a list: its picker', [called(find(p, 'list'), 'picker').length, r.used], [1, true]);
put(p, 'old-list');
press(p, 'Enter');
check('...or a click, where there\'s no picker', called(find(p, 'old-list'), 'click').length, 1);
put(p, 'tick');
press(p, 'Enter');
check('OK on a box to tick: ticked', called(find(p, 'tick'), 'click').length, 1);
put(p, 'button');
press(p, 'Enter');
check('OK on a button: the button\'s own', called(find(p, 'button'), 'click').length, 0);
