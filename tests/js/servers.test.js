// The player alone with the website's servers (app/src/main/assets/gecko/player-servers.js, in the page): their
// names in the app's own row, on stand-in pages as Beenar's and Kurd Movie's have them; the one playing marked;
// pressing one presses the website's own. In the background page (alone.js): the row's style, and every frame of the
// page told the remote goes up to it (lift). The other websites' servers: servers-sites.test.js; up to the row:
// lift.test.js.
'use strict';
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { E } = require('./lib/page');
const { read, doc, row, names, marked, pressRow, scripts } = require('./lib/row');

let { s, SITES } = scripts();

// As Beenar's page has them: its own server first (the one it plays), each with its name and quality
const item = (name, on) => E('button', { name, attrs: { type: 'button', class: 'server-item' + (on ? ' server-item--active' : '') } }, [
  E('img', { attrs: { class: 'server-item-icon' } }), E('span', { text: name, attrs: { class: 'server-item-name' } }),
  E('span', { text: '1080p', attrs: { class: 'server-item-q' } })]);
const servers = [item('Beenar', true), item('Vidmoly'), item('StreamSB')];
let d = doc([E('div', { attrs: { class: 'server-list' } }, servers)]);
const pressed = [];
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
check('...the website\'s servers changed (another episode\'s): the row made again, as many as it has now, the one it marks',
  [names(d), marked(d), d.querySelectorAll('[data-tm-servers]').length], [['Beenar', 'Vidmoly'], [true, false], 1]);

// As Kurd Movie's page has them: its trailer first, the one it plays marked "on"
({ s, SITES } = scripts());
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
  ok('the row\'s style: at the top of the screen, the player below it; its names in the app\'s look, not Android\'s buttons',
    /\[data-tm-servers\] \{ position: fixed !important; top: 0/.test(css[0])
    && /\[data-tm-with-servers\] \[data-tm-player\] \{ top: 7vh !important; height: 93vh/.test(css[0])
    && /\[data-tm-servers\] button \{ -moz-appearance: none !important; appearance: none !important;/.test(css[0]));
  ok('...many names: the row scrolls (each name whole), the first ones in sight', /overflow-x: auto !important/.test(css[0])
    && /justify-content: safe center !important/.test(css[0]) && /button \{[^}]*flex: none !important/.test(css[0]));
  check('a frame of the page asks before the row shows: no', await asks(3, { lifted: 1 }), { lift: false });
  await asks(3, { lift: 1 });
  check('the row shows: every frame of the page told the remote goes up to it', told, [[3, { arrows: 'lift' }, undefined]]);
  check('...a frame that comes later asks: yes; another tab\'s: no', [await asks(3, { lifted: 1 }), await asks(4, { lifted: 1 })],
    [{ lift: true }, { lift: false }]);
  await asks(5, { lift: 1 });
  check('...not a tab the Twilight screens didn\'t open', told.length, 1);
  opening(3, 'https://krd.watch/', undefined);
  check('the tab opens another page: no row there', await asks(3, { lifted: 1 }), { lift: false });
})();
