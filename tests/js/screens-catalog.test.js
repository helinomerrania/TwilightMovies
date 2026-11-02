// The Twilight screens' catalogue (ui/merge.js, ui/saved.js, ui/catalog.js): the same title on several websites
// as one, what it shows in each language, which website plays first, what the box keeps (settings, watched, My
// list, titles opened), and the home's rows from every website's lists, a title from each in turn.
'use strict';
const { check, ok } = require('./lib/check');
const { load, pick } = require('./lib/ui');

function storage() {
  const kept = {};
  return { getItem: (k) => (k in kept ? kept[k] : null), setItem: (k, v) => { kept[k] = String(v); } };
}
const TM = load(['tm.js', 'words.js', 'readers/krd-watch.js', 'readers/beenar.js', 'readers/kurd-movie.js',
  'saved.js', 'merge.js', 'catalog.js'], { localStorage: storage() });
const KU = TM.language('ckb');
const EN = TM.language('en');

const krd = { site: 'krd-watch', ref: 'movie:634649', kind: 'film', tmdb: 634649, title: 'Spider-Man: No Way Home',
  titleKu: 'پیاوە جاڵجاڵۆکەیەکە: ڕێگای ماڵەوە نییە', year: 2021, genres: ['Action', 'Adventure'], overview: 'Peter Parker...',
  overviewKu: 'پیتەر پارکەر...', langs: ['sub'], quality: '1080p', backdrop: 'https://image.tmdb.org/t/p/original/a.jpg' };
const km = { site: 'kurd-movie', ref: 'movies/8308', kind: 'film', title: 'Spider-Man No Way Home', year: 2021, langs: ['dub'],
  poster: 'https://image.tmdb.org/t/p/w342/p.jpg' };
const old = { site: 'beenar', ref: 'f:1', kind: 'film', title: 'Spider-Man', year: 2002, langs: ['sub'], quality: '4K' };
const remake = { site: 'beenar', ref: 'f:2', kind: 'film', title: 'Spider Man', year: 2004 };

let p = TM.merge.pile();
[krd, old, km, remake].forEach(p.add);
const all = p.list();
check('the same film on two websites, its names written differently: one title, both websites behind it',
  all.map((e) => e.items.map((x) => x.ref)), [['movie:634649', 'movies/8308'], ['f:1'], ['f:2']]);
p = TM.merge.pile();
[krd, Object.assign({}, km, { year: 2023 })].forEach(p.add);
check('...but not two years apart', p.list().length, 2);
p = TM.merge.pile();
[krd, { site: 'krd-watch', ref: 'movie:634649', kind: 'film', tmdb: 634649, title: 'Spider-Man: No Way Home', minutes: 148 }].forEach(p.add);
check('...the same one again (TMDB\'s number): one, with what came new', [p.list().length, p.list()[0].items[0].minutes], [1, 148]);
check('lists of several websites as one: a title from each in turn', TM.merge.turns([[1, 2, 3], [4], [5, 6]]), [1, 4, 5, 2, 6, 3]);

const e = all[0];
const ku = TM.merge.show(e, KU);
check('a title in Kurdish: its Kurdish name, its English one under it, genres in Kurdish, the Kurdish summary',
  pick(ku, ['name', 'original', 'genres', 'overview', 'overviewDir', 'langs', 'sites']),
  { name: krd.titleKu, original: krd.title, genres: ['ئاکشن', 'سەرکێشی'], overview: 'پیتەر پارکەر...', overviewDir: 'rtl',
    langs: ['sub', 'dub'], sites: 2 });
check('...in English: its English name, nothing under it, the English summary',
  pick(TM.merge.show(e, EN), ['name', 'original', 'genres', 'overview']),
  { name: krd.title, original: '', genres: ['Action', 'Adventure'], overview: 'Peter Parker...' });
check('...its pictures from whichever website has them', [ku.poster, ku.backdrop], [km.poster, krd.backdrop]);

const s = TM.saved.settings();
check('settings at first: the screens in the app\'s language, Twilight\'s look, Kurdish subtitles first, every website on',
  pick(s, ['lang', 'look', 'first', 'order', 'off']), { lang: '', look: 'twilight', first: 'sub', order: ['krd-watch', 'beenar', 'kurd-movie'], off: {} });
check('the website that plays first: subtitles', TM.merge.sources(e, s).map((x) => x.site), ['krd-watch', 'kurd-movie']);
check('...dubbing', TM.merge.sources(e, Object.assign({}, s, { first: 'dub' })).map((x) => x.site), ['kurd-movie', 'krd-watch']);
p = TM.merge.pile();
[Object.assign({}, km, { title: 'Spider-Man', year: 2002, langs: ['sub'] }), old].forEach(p.add);
check('...the best picture (4K before the rest)', TM.merge.sources(p.list()[0], Object.assign({}, s, { first: 'best' })).map((x) => x.site),
  ['beenar', 'kurd-movie']);
check('...never one that\'s off', TM.merge.sources(e, Object.assign({}, s, { off: { 'krd-watch': true } })).map((x) => x.site), ['kurd-movie']);

TM.saved.move('kurd-movie', -2);
TM.saved.toggle('beenar');
check('a website moved up, one turned off: the screens ask the others, in their order',
  TM.saved.readers().map((r) => r.id), ['kurd-movie', 'krd-watch']);
TM.saved.toggle('beenar');
TM.saved.move('kurd-movie', 2);
check('...on again, and back down', TM.saved.readers().map((r) => r.id), ['krd-watch', 'beenar', 'kurd-movie']);

TM.saved.watch(e, km, null);
TM.saved.watch({ kind: 'series', items: [{ site: 'krd-watch', ref: 'tv:1', kind: 'series', title: 'Lupin' }] },
  { site: 'krd-watch', ref: 'tv:1' }, { season: 2, n: 3 });
TM.saved.watch(e, krd, null);
const w = TM.saved.watched();
check('watched: newest first, one line a title (its last)', w.map((x) => x.ref), ['movie:634649', 'tv:1']);
check('...a series, with where it was', pick(w[1], ['season', 'episode', 'ep']), { season: 2, episode: 3, ep: { season: 2, n: 3 } });
ok('...a title known by any of its websites', TM.saved.lastOf({ items: [{ site: 'kurd-movie', ref: 'movies/8308' }] }));
check('My list: in, then out', [TM.saved.toggleMine(e), TM.saved.inMine(e), TM.saved.toggleMine(e), TM.saved.inMine(e)], [true, true, false, false]);
TM.saved.keep(e);
check('a title opened is kept with all its websites (its page after a film)',
  TM.saved.kept('kurd-movie', 'movies/8308').items.map((x) => x.site), ['krd-watch', 'kurd-movie']);

const rows = TM.catalog.lists({
  'kurd-movie': [{ key: 'newFilms', items: [km, remake] }],
  'krd-watch': [{ key: 'newFilms', items: [krd, { site: 'krd-watch', ref: 'movie:2', kind: 'film', title: 'Dune', year: 2021 }] }],
});
check('the home\'s rows: a title from each website in turn (in the websites\' order), each title once',
  rows.rows.newFilms.map((x) => x.items[0].ref), ['movie:634649', 'movie:2', 'f:2']);
check('...a title\'s websites in their order, whichever came first', rows.rows.newFilms[0].items.map((x) => x.site), ['krd-watch', 'kurd-movie']);
