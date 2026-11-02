// KRD WATCH's reader (app/src/main/assets/gecko/ui/readers/krd-watch.js), with its data feed's saved answers
// (tests/js/fixtures/readers/krd-watch/, from tests/try/probe.js): its lists, search, a film and a series in full,
// a season's episodes, and the addresses that play them.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/krd-watch.js']);
const r = TM.readers['krd-watch'];
const web = () => saved('krd-watch', [
  [/\/api\/discover\?kind=movie/, 'discover-movie.json'], [/\/api\/feed$/, 'feed.json'], [/\/api\/search\?q=/, 'search-spider.json'],
  [/\/api\/title\/movie\//, 'title-movie-634649.json'], [/\/api\/title\/tv\//, 'title-tv-259909.json'],
  [/\/api\/season\/259909\/1$/, 'season-259909-1.json'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: the newest films, and the series that got episodes lately', rows.map((x) => x.key), ['newFilms', 'newSeries']);
  check('...a film as its list gives it (its Kurdish name too; a poster of its own on its address)',
    pick(rows[0].items[0], ['site', 'ref', 'kind', 'title', 'titleKu', 'year', 'minutes', 'langs', 'poster']),
    { site: 'krd-watch', ref: 'movie:1339713', kind: 'film', title: 'Obsession', titleKu: 'چەوساندەوە', year: 2026, minutes: 109,
      langs: ['sub'], poster: 'https://krd.watch/assets/posters_krdwatch/movies/12716_Obsession.jpg' });
  check('...a series once, however many of its episodes came, with what came (TMDB\'s picture, bigger)',
    pick(rows[1].items[0], ['ref', 'kind', 'title', 'year', 'note', 'poster']),
    { ref: 'tv:259909', kind: 'series', title: 'Dexter: Resurrection', year: 2026, note: 'Season 2',
      poster: 'https://image.tmdb.org/t/p/w342/kEHZfSZhZKDot4wqurgIzMUNq1W.jpg' });
  ok('...each series once', new Set(rows[1].items.map((x) => x.ref)).size === rows[1].items.length);
  check('...its data feed asked twice', w.asked, ['GET https://krd.watch/api/discover?kind=movie&page=1', 'GET https://krd.watch/api/feed']);

  w = web();
  const found = await r.search(w, 'سپایدەر man');
  check('search: what it finds, in its order', found.map((x) => x.title).slice(0, 2), ['Spider-Man: Brand New Day', 'Spider-Man: No Way Home']);
  check('...the words asked as they were typed', w.asked, ['GET https://krd.watch/api/search?q=%D8%B3%D9%BE%D8%A7%DB%8C%D8%AF%DB%95%D8%B1%20man']);

  w = web();
  const film = await r.title(w, 'movie:634649');
  check('a film in full: its English and Kurdish names, Kurdish genres and summary',
    pick(film, ['title', 'titleKu', 'genresKu', 'minutes', 'year', 'tagline']),
    { title: 'Spider-Man: No Way Home', titleKu: 'پیاوی جاڵجاڵۆکە: هیچ ڕێگایەک نییە بۆ ماڵەوە', genresKu: ['ئاکشن', 'سەرکێشی', 'زانستی-خەیاڵی'],
      minutes: 148, year: 2021, tagline: 'بەرەو گەردوونە فرەییەکان' });
  ok('...its summary in Kurdish', /^پیتەر پارکەر/.test(film.overviewKu), film.overviewKu);
  check('...no seasons', film.seasons, undefined);

  const series = await r.title(w, 'tv:259909');
  check('a series in full: its seasons, with how many episodes, and its age rating',
    pick(series, ['kind', 'seasons', 'age']), { kind: 'series', seasons: [{ n: 1, count: 10 }, { n: 2, count: 10 }], age: 'TV-MA' });
  const eps = await r.episodes(w, 'tv:259909', 1);
  check('a season\'s episodes: number, name, length, and the summary in Kurdish', pick(eps[0], ['n', 'title', 'minutes', 'overviewKu']),
    { n: 1, title: 'A Beating Heart...', minutes: 52, overviewKu: 'دێکستر مۆرگان لە کۆمایەکی دە هەفتەیی بەئاگا دێتەوە.' });

  check('the address that plays a film', r.watch('movie:634649'), 'https://krd.watch/watch/634649');
  check('...and an episode', r.watch('tv:259909', { season: 2, n: 3 }), 'https://krd.watch/watch/tv/259909?s=2&e=3');
})();
