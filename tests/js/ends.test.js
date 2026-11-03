// How the Watch's film ends (app/src/main/assets/gecko/player-ends.js in the player's frames, alone-play.js in the
// background page, player-film.js in the page, the screens' catalog.js and saved.js): its server failed (the player
// says the film won't play, or its video can't load), and the next one in the row is pressed, each once; or a series'
// episode played to its end (not an ad: a film is minutes long), and the next one plays, in its place in the page's
// history, the screens keeping it as watched.
'use strict';
const vm = require('vm');
const { check } = require('./lib/check');
const { E } = require('./lib/page');
const { load } = require('./lib/ui');
const { read, video, frame } = require('./lib/frame');

// A server's player that says its film won't play
let f = frame([E('p', { text: 'The system is under maintenance. Please try again later.', attrs: { class: 'maintenance-message' } })],
  { host: 'beenar.uns.bio' });
f.tell();
f.tick(2);
check('a player that says its film won\'t play: not at once (it may still be loading)', f.sent, []);
f.tick(5);
check('...then its server said to have failed, once', [f.sent, f.timers.filter(Boolean).length], [[{ failed: 1 }], 0]);
f = frame([video({ src: 'https://x.example/f.mp4', error: { code: 4 } })]);
f.tell();
f.tick(4);
check('...or its video can\'t load', f.sent, [{ failed: 1 }]);
f = frame([video({}), E('p', { text: 'Loading...' })]);
f.tell();
f.tick(30);
check('...not a player that only takes its time', f.sent, []);

// Its film played to its end
const film = video({ src: 'blob:https://vidmoly.biz/2', duration: 2700 });
f = frame([film]);
f.tell();
f.fire('ended', video({ name: 'ad', duration: 30 }));
check('an ad that ended: nothing (a film is minutes long)', f.sent, []);
f.fire('ended', film);
f.fire('ended', film);
check('the film that ended: said so, once', f.sent, [{ ended: 1 }]);
f = frame([video({ name: 'trailer', duration: 600 }), E('div', { attrs: { 'data-tm-player': '' } }, [video({ name: 'own', duration: 3000 })])],
  { top: true, host: 'krd.watch' });
f.tell();
f.fire('ended', f.doc.querySelectorAll('video')[0]);
check('...in the page itself, not a video outside its player alone', f.sent, []);

// The page: the next server in the row pressed, each once; the next episode, in this one's place
const toldBg = [];
const P = vm.createContext({ Array, String, location: { href: 'https://kurdsubtitle.net/tvshows/1-Lupin#tm-play=2-3' },
  browser: { runtime: { sendMessage: (m) => { toldBg.push(m); return { then: (f) => f() }; } } } });
vm.runInContext(read('player-film.js'), P);
const film2 = vm.runInContext('playerFilm', P);
const names = ['Vidmoly', 'YourUpload', 'SendVid'].map((n, i) => E('button', { name: n, attrs: i === 0 ? { 'data-tm-on': '' } : {} }));
const doc = { querySelector: (s) => (s === '[data-tm-servers]' ? E('div', {}, names) : null) };
check('its server failed: the next one in the row pressed; then the one after; then none (each once)',
  [film2.next(doc), film2.next(doc), film2.next(doc), names.map((b) => b.calls.length)], [true, true, false, [0, 1, 1]]);
const went = [];
P.location.replace = (u) => went.push(['replace', u]);
P.location.reload = () => went.push(['reload']);
film2.episode('https://kurdsubtitle.net/tvshows/1-Lupin#tm-play=2-4');
film2.episode('https://marscinema.net/watch/barry?ep=281');
check('the next episode, in this one\'s place in the history; one only its address\'s end differs for, loaded again; the background '
  + 'page told first (its page is the Watch\'s too)', [went, toldBg], [[['replace', 'https://kurdsubtitle.net/tvshows/1-Lupin#tm-play=2-4'],
  ['reload'], ['replace', 'https://marscinema.net/watch/barry?ep=281']], [{ going: 'https://kurdsubtitle.net/tvshows/1-Lupin#tm-play=2-4' },
  { going: 'https://marscinema.net/watch/barry?ep=281' }]]);

// The background page
const told = [];
const listeners = [];
const bg = vm.createContext({ URL, Promise, OWN: 'moz-extension://the-app/', port: null,
  browser: { runtime: { onMessage: { addListener: (l) => listeners.push(l) } },
    webNavigation: { getAllFrames: () => Promise.resolve([{ frameId: 0, parentFrameId: -1 }]) },
    tabs: { sendMessage: (tab, m, o) => { told.push([tab, m, o.frameId]); return Promise.resolve(true); } } } });
vm.runInContext(read('alone.js') + '\n' + read('alone-play.js'), bg);
const asks = async (tab, m) => {
  for (const l of listeners) {
    const r = l(m, { tab: { id: tab }, frameId: 5 });
    if (r !== undefined) return r;
  }
  return undefined;
};
const opening = (tab, url, from) => vm.runInContext('aloneFor', bg)({ type: 'main_frame', tabId: tab, url, originUrl: from });

// The screens
function storage() {
  const kept = {};
  return { getItem: (k) => (k in kept ? kept[k] : null), setItem: (k, v) => { kept[k] = String(v); } };
}
const TM = load(['tm.js', 'words.js', 'readers/marscinema.js', 'readers/kartonikurde.js', 'saved.js', 'merge.js', 'catalog.js'],
  { localStorage: storage() });
const queued = [];
TM.app = { queue: (q) => { queued.push(q); return Promise.resolve(); }, leave: (url) => queued.push(url) };
const eps = [{ n: 3, id: 281 }, { n: 1, id: 278 }, { n: 2, id: 279 }];
TM.readers.marscinema.episodes = () => Promise.resolve(eps);
TM.readers.kartonikurde.episodes = () => Promise.resolve([{ n: 1 }, { n: 2 }]);
const barry = { site: 'marscinema', ref: 'series/barry', kind: 'series', title: 'Barry' };

module.exports = (async () => {
  await TM.catalog.play({ kind: 'series', items: [barry] }, barry, { season: 1, n: 1, id: 278 });
  check('the screens: an episode played, and the ones after it in its season given, to play by themselves',
    queued, [{ key: 'marscinema|series/barry', list: [
      { season: 1, n: 2, id: 279, url: undefined, watch: 'https://marscinema.net/watch/barry?ep=279' },
      { season: 1, n: 3, id: 281, url: undefined, watch: 'https://marscinema.net/watch/barry?ep=281' }] },
    'https://marscinema.net/watch/barry?ep=278']);
  queued.length = 0;
  const cartoon = { site: 'kartonikurde', ref: 'serie/grendizer', kind: 'series', title: 'Grendizer' };
  await TM.catalog.play({ kind: 'series', items: [cartoon] }, cartoon, { season: 1, n: 1 });
  check('...not where every episode plays on the same page (Kartoni Kurdi\'s: chosen on it)', queued[0].list, []);
  queued.length = 0;
  const filmItem = { site: 'marscinema', ref: 'movie/dark-city', kind: 'film', title: 'Dark City' };
  await TM.catalog.play({ kind: 'film', items: [filmItem] }, filmItem);
  check('...a film: none', queued[0], null);

  opening(2, 'https://marscinema.net/watch/barry?ep=278', 'moz-extension://the-app/ui/index.html#/title/marscinema/series%2Fbarry');
  await asks(2, { episodes: { key: 'marscinema|series/barry', list: [{ season: 1, n: 2, id: 279, watch: 'https://marscinema.net/watch/barry?ep=279' }] } });
  await asks(2, { ended: 1 });
  check('the background page: the episode ended, the page told to play the next', told, [[2, { nextEpisode: 'https://marscinema.net/watch/barry?ep=279' }, 0]]);
  await asks(2, { ended: 1 });
  check('...none after the season\'s last', told.length, 1);
  await asks(2, { going: 'https://kurdsubtitle.net/tvshows/1-Lupin#tm-play=2-4' });
  vm.runInContext('aloneFor', bg)({ type: 'main_frame', tabId: 2, url: 'https://kurdsubtitle.net/tvshows/1-Lupin' });
  check('...the page goes on to the next episode itself (loaded again, from no page the engine names): the Watch\'s still',
    await asks(2, { alone: 'kurdsubtitle.net' }), true);
  const a = await asks(2, { advanced: 1 });
  check('...the screens asked where it went on to: told once', [a.key, a.ep.n, await asks(2, { advanced: 1 })], ['marscinema|series/barry', 2, null]);
  await asks(2, { failed: 1 });
  check('a server failed: the page told to press the next', told.slice(-1), [[2, { serverFailed: true }, 0]]);
  await asks(3, { failed: 1 });
  check('...not a tab the Twilight screens didn\'t open', told.length, 2);
  await asks(2, { episodes: { key: 'k', list: [{ watch: 'https://x.example/2' }] } });
  opening(2, 'https://krd.watch/', undefined);
  await asks(2, { ended: 1 });
  check('the tab opens another page: no next episode there', told.length, 2);

  check('the screens: the episode it went on to kept as watched (Continue is it)', [TM.saved.advance(a.key, a.ep),
    TM.saved.lastOf({ items: [barry] }).episode, TM.saved.advance('other|x', a.ep)], [true, 2, false]);
})();
