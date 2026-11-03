// Kurd Subtitle's reader (app/src/main/assets/gecko/ui/readers/kurdsubtitle.js), with its feed's saved answers
// (tests/js/fixtures/readers/kurdsubtitle/: the fields it reads, as they came, from tests/try/probe.js): its newest
// films and series, its search, a film and a series in full, a season's episodes, and where each plays.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/kurdsubtitle.js']);
const r = TM.readers.kurdsubtitle;
const API = 'https://api.kurdsubtitle.net/api/v1';
const web = () => saved('kurdsubtitle', [
  [/\/movies\?page=1$/, 'movies-1.json'], [/\/tvshows\?page=1$/, 'tvshows-1.json'], [/\/search\?query=spider$/, 'search-spider.json'],
  [/\/movies\/779410-The-Paradise$/, 'film.json'], [/\/tvshows\/467300-Lupin$/, 'series.json'],
  [/\/tvshows\/60005e7b64d2e47956145ba7\/seasons\/2\/episodes$/, 'episodes-2.json'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: its feed\'s newest films and series', [rows.map((x) => x.key), w.asked],
    [['newFilms', 'newSeries'], ['GET ' + API + '/movies?page=1', 'GET ' + API + '/tvshows?page=1']]);
  check('...a film as its list has it: its name, year, length, genres in both, IMDb\'s rating, poster', pick(rows[0].items[0],
    ['site', 'ref', 'kind', 'title', 'year', 'minutes', 'genres', 'genresKu', 'rating', 'langs']),
  { site: 'kurdsubtitle', ref: 'movies/211530-The-Lodger-A-Story-of-the-London-Fog', kind: 'film',
    title: 'The Lodger: A Story of the London Fog', year: 1927, minutes: 91, genres: ['Crime', 'Thriller', 'Mystery'],
    genresKu: ['تاوانکاری', 'هەستبزوێن', 'نهێنی'], rating: 7.3, langs: ['sub'] });
  ok('...its poster on the website', /^https:\/\/images\.kurdsubtitle\.net\//.test(rows[0].items[0].poster), rows[0].items[0].poster);
  check('...a series', pick(rows[1].items[0], ['ref', 'kind', 'year']), { ref: 'tvshows/277497-Final-Table', kind: 'series', year: 2026 });
  ok('...not a series\' length (an episode\'s, as the feed has it)', !rows[1].items[0].minutes, rows[1].items[0].minutes);

  w = web();
  const found = await r.search(w, 'spider');
  check('its search: its films and series, not its collections, members or TMDB\'s', found.map((x) => [x.ref, x.kind]),
    [['movies/198476-Spider-Man-2002', 'film'], ['movies/217011-Spider-Man-No-Way-Home', 'film'],
      ['tvshows/482869-Spider-Noir', 'series'], ['tvshows/441537-Your-Friendly-Neighborhood-Spider-Man', 'series']]);
  ok('...with their Kurdish summaries', /پیتەر پارکەر/.test(found[0].overviewKu || ''), found[0].overviewKu);

  w = web();
  const f = await r.title(w, 'movies/779410-The-Paradise');
  check('a film in full: its name in both, year, length, TMDB\'s number, genres', pick(f,
    ['kind', 'title', 'titleKu', 'year', 'minutes', 'tmdb', 'genres', 'rating']),
  { kind: 'film', title: 'The Paradise', titleKu: 'بەهەشت', year: 2026, minutes: 170, tmdb: 1376856, genres: ['Action', 'Drama'], rating: 8 });
  ok('...its Kurdish summary, no age where the feed says N/A', /^لە ساڵانی ١٩٨٠دا/.test(f.overviewKu || '') && !f.age, f.overviewKu);

  w = web();
  const s = await r.title(w, 'tvshows/467300-Lupin');
  check('a series in full: its seasons with their episodes\' counts, its age', pick(s, ['kind', 'title', 'titleKu', 'tmdb', 'age', 'seasons']),
    { kind: 'series', title: 'Lupin', titleKu: 'لوپین', tmdb: 65494, age: 'TV-MA',
      seasons: [{ n: 1, count: 5 }, { n: 2, count: 5 }, { n: 3, count: 7 }, { n: 4, count: 8 }] });
  const eps = await r.episodes(w, 'tvshows/467300-Lupin', 2);
  check('...a season\'s episodes, by the series\' own number in the feed', [eps, w.asked.slice(-1)],
    [[{ n: 1, title: 'Chapter 1' }, { n: 2, title: 'Chapter 2' }, { n: 3, title: 'Chapter 3' }],
      ['GET ' + API + '/tvshows/60005e7b64d2e47956145ba7/seasons/2/episodes']]);

  check('where a film plays: its own page', r.watch('movies/779410-The-Paradise'), 'https://kurdsubtitle.net/movies/779410-The-Paradise');
  check('...and an episode: the series\' page, the season and episode to choose there', r.watch('tvshows/467300-Lupin', { season: 2, n: 3 }),
    'https://kurdsubtitle.net/tvshows/467300-Lupin#tm-play=2-3');
})();
