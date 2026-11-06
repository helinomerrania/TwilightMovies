// The titles' original pictures (ui/pictures.js; merge.js's show): TMDB's first, as one of a title's websites gives
// them, else as TMDB gives them when it's asked (only with the app's key; once a title, a few at a time; what it
// found kept on the box, what it didn't asked again a week later), else the first website's own. TMDB's search: by
// the title's English name, kind and year; of what it finds, the one that is this title, or none.
'use strict';
const { check } = require('./lib/check');
const { load } = require('./lib/ui');

const kept = {};
const storage = { getItem: (k) => (k in kept ? kept[k] : null), setItem: (k, v) => { kept[k] = String(v); } };
let now = Date.UTC(2026, 10, 6);
const timers = [];
const flush = () => timers.splice(0).forEach((f) => f());
const settle = () => new Promise((r) => setImmediate(r));
function screens() {
  const TM = load(['tm.js', 'keys.js', 'words.js', 'merge.js', 'pictures.js'], { localStorage: storage,
    Date: Object.assign(function () {}, { now: () => now }), setTimeout: (f) => timers.push(f) });
  TM.asked = [];
  TM.answers = {};
  TM.web = { json: (url, o) => {
    TM.asked.push([url.replace(/^https:\/\/api\.themoviedb\.org\/3\/search\//, ''), (o.headers || {}).Authorization || '']);
    const a = Object.keys(TM.answers).find((q) => url.includes(q));
    return a === undefined ? new Promise(() => {}) : TM.answers[a] instanceof Error ? Promise.reject(TM.answers[a])
      : Promise.resolve({ results: TM.answers[a] });
  } };
  return TM;
}
const film = (title, year, extra) => ({ kind: 'film', items: [Object.assign({ site: 'kurdcinama', ref: title, kind: 'film', title, year,
  poster: 'https://kurdcinama.com/Wenekan_KS/' + title + '.webp' }, extra)] });

let TM = screens();
const P = TM.pictures;
const frozen = [{ title: 'Frozen', release_date: '2010-02-05', poster_path: '/f10.jpg' },
  { title: 'Frozen', release_date: '2013-11-20', poster_path: '/f13.jpg', backdrop_path: '/b13.jpg' }];
check('TMDB\'s search: the one with its name and year', P.pick(frozen, 'Frozen', 2013, false).poster_path, '/f13.jpg');
check('...its name written another way (as merge.js writes it); a year apart',
  P.pick([{ title: 'Spider-Man: No Way Home', release_date: '2021-12-15', poster_path: '/s.jpg' }], 'Spider Man No Way Home', 2022, false).poster_path, '/s.jpg');
check('...a series by its own name (or its original one) and its first year',
  P.pick([{ name: 'Money Heist', original_name: 'La casa de papel', first_air_date: '2017-05-02', poster_path: '/m.jpg' }], 'La Casa de Papel', 2017, true).poster_path, '/m.jpg');
const dawn = [{ title: 'The Twilight Saga: Breaking Dawn - Part 2', release_date: '2012-11-13', poster_path: '/d.jpg' }];
check('...the year the same, a name that has this one in it', P.pick(dawn, 'Breaking Dawn Part 2', 2012, false).poster_path, '/d.jpg');
check('...but not another year, nor two years apart, nor one with no poster, nor another name',
  [P.pick(dawn, 'Breaking Dawn Part 2', 2011, false), P.pick(frozen, 'Frozen', 2016, false),
    P.pick([{ title: 'Frozen', release_date: '2013-11-20' }], 'Frozen', 2013, false), P.pick(frozen, 'The Snow Queen', 2013, false)],
  [null, null, null, null]);
check('...a name with more words than TMDB\'s, the year the same (a website\'s "Extended Cut")',
  P.pick(frozen, 'Frozen Extended Cut', 2013, false).poster_path, '/f13.jpg');

module.exports = (async () => {
  const ice = film('Frozen', 2013);
  P.want(ice);
  check('no key (the project\'s own build): TMDB not asked', TM.asked, []);

  TM.keys.tmdb = '0123456789abcdef0123456789abcdef';
  TM.answers = { 'query=Frozen&': frozen };
  let drawn = 0;
  P.want(ice, () => drawn++);
  P.want(ice, () => drawn++);
  await settle();
  check('with the key: TMDB asked once for a title two cards want, by its name, kind and year (the key with it)',
    TM.asked, [['movie?query=Frozen&include_adult=false&language=en-US&year=2013&api_key=0123456789abcdef0123456789abcdef', '']]);
  check('...both cards drawn again, its pictures TMDB\'s now', [drawn, TM.merge.show(ice, TM.language('en')).poster,
    TM.merge.show(ice, TM.language('en')).backdrop], [2, 'https://image.tmdb.org/t/p/w500/f13.jpg', 'https://image.tmdb.org/t/p/w500/b13.jpg']);
  flush();
  check('...kept on the box', JSON.parse(kept['tm.pictures'])['film:frozen:2013'], ['/f13.jpg', '/b13.jpg', now]);

  TM.asked.length = 0;
  const krd = { kind: 'film', items: [{ site: 'kurdcinama', ref: 'k', kind: 'film', title: 'Coco', year: 2017, poster: 'https://kurdcinama.com/c.webp' },
    { site: 'krd-watch', ref: 'movie:354912', kind: 'film', title: 'Coco', year: 2017, poster: 'https://image.tmdb.org/t/p/w342/coco.jpg' }]};
  P.want(krd);
  P.want({ kind: 'film', items: [{ site: 'kartonikurde', ref: 'x', kind: 'film', titleKu: 'ماڤکا' }] });
  check('not asked: a title one of its websites gives TMDB\'s pictures for (its poster: that one, before the first website\'s own); '
    + 'one with no English name', [TM.asked, TM.merge.show(krd, TM.language('ckb')).poster], [[], 'https://image.tmdb.org/t/p/w342/coco.jpg']);
  const own = film('Ava', 2025);
  check('...nor known: the first website\'s own', TM.merge.show(own, TM.language('ckb')).poster, 'https://kurdcinama.com/Wenekan_KS/Ava.webp');

  TM.answers = { 'query=Ava&include_adult=false&language=en-US&year=': [], 'query=Ava&include_adult=false&language=en-US&api_key': [] };
  P.want(own);
  await settle();
  await settle();
  check('one TMDB doesn\'t find with its year: asked again without it', TM.asked.map((a) => a[0].replace(/&api_key=.*/, '')),
    ['movie?query=Ava&include_adult=false&language=en-US&year=2025', 'movie?query=Ava&include_adult=false&language=en-US']);
  flush();
  TM = screens();
  TM.keys.tmdb = '0123456789abcdef0123456789abcdef';
  TM.pictures.want(film('Ava', 2025));
  TM.pictures.want(film('Frozen', 2013));
  check('the screens opened again: what TMDB found known at once, what it didn\'t, not asked again for a week',
    [TM.asked, TM.merge.show(film('Frozen', 2013), TM.language('en')).poster], [[], 'https://image.tmdb.org/t/p/w500/f13.jpg']);
  now += 8 * 24 * 3600 * 1000;
  TM.pictures.want(film('Ava', 2025));
  check('...a week later, asked again', TM.asked.length, 1);

  TM = screens();
  TM.keys.tmdb = 'eyJhbGciOiJIUzI1NiJ9.e30.x';
  ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'].forEach((t) => TM.pictures.want({ kind: 'series', items: [{ site: 'beenar', ref: t, kind: 'series', title: t }] }));
  check('a few at a time (four); a series by TMDB\'s search for series; its Read Access Token sent as one',
    [TM.asked.length, TM.asked[0]], [4, ['tv?query=A1&include_adult=false&language=en-US', 'Bearer eyJhbGciOiJIUzI1NiJ9.e30.x']]);

  TM = screens();
  TM.keys.tmdb = 'ffffffffffffffffffffffffffffffff';
  TM.answers = { query: new Error('https://api.themoviedb.org/3/search/movie answered 401') };
  ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'].forEach((t) => TM.pictures.want(film(t, 2020)));
  await settle();
  await settle();
  TM.pictures.want(film('B7', 2020));
  check('TMDB refuses the key: nothing more asked', TM.asked.length, 4);
})();
