// Kurd Movie's reader (app/src/main/assets/gecko/ui/readers/kurd-movie.js), with its pages' saved copies
// (tests/js/fixtures/readers/kurd-movie/: the parts it reads of the pages as they come, from tests/try/readers.js):
// its lists, search (with the number its pages carry), a film and a series in full from their pages, a season's
// episodes, and the pages that play them.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/kurd-movie.js']);
const r = TM.readers['kurd-movie'];
const SITE = 'https://www.kurd-movie.com';
const web = () => saved('kurd-movie', [
  [/\/movies\/$/, 'movies.html'], [/\/tvshows\/$/, 'tvshows.html'], [/\.com\/$/, 'home.html'],
  [/\/wp-json\/dooplay\/search\//, 'search-spider.json'], [/\/movies\/onslaught\/$/, 'film.html'],
  [/\/tvshows\/the-lord-of-the-rings-the-rings-of-power\/$/, 'series.html'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: its films and its series pages', [rows.map((x) => x.key), w.asked], [['newFilms', 'newSeries'], ['GET ' + SITE + '/movies/', 'GET ' + SITE + '/tvshows/']]);
  check('...a film as its list shows it: its page, year, length, genres (not "Kurdish"), picture quality, a bigger poster',
    pick(rows[0].items[0], ['site', 'ref', 'kind', 'title', 'year', 'minutes', 'genres', 'quality', 'langs', 'poster']),
    { site: 'kurd-movie', ref: 'movies/onslaught', kind: 'film', title: 'Onslaught', year: 2026, minutes: 92,
      genres: ['Action', 'Horror', 'Thriller'], quality: 'QHD', langs: ['sub'], poster: 'https://image.tmdb.org/t/p/w342/eAs7G3yFaULpfwfVdGvPo5HmFJS.jpg' });
  ok('...its summary', /^When a rogue squad/.test(rows[0].items[0].overview), rows[0].items[0].overview);
  check('...only the list\'s own titles (not the pictures sliding by above it)', rows.map((x) => x.items.length), [2, 2]);
  check('...a series', pick(rows[1].items[0], ['kind']), { kind: 'series' });
  ok('...each series on its own page', rows[1].items.every((x) => /^tvshows\//.test(x.ref)), JSON.stringify(rows[1].items.map((x) => x.ref)));

  w = web();
  const found = await r.search(w, 'spider');
  check('search: the number its pages carry first, then WordPress\'s search with it', w.asked,
    ['GET ' + SITE + '/', 'GET ' + SITE + '/wp-json/dooplay/search/?keyword=spider&nonce=23d5ccf3c1']);
  const dubbed = found.find((x) => x.ref === 'movies/8308');
  check('...a dubbed film: "doblaj" taken off its name, and Kurdish dubbing', pick(dubbed, ['title', 'langs', 'year', 'rating', 'kind']),
    { title: 'Spider-Man: Far from Home', langs: ['dub'], year: 2019, rating: 7.3, kind: 'film' });
  ok('...no picture where the website has only its stand-in', found.every((x) => !/default\.svg/.test(x.poster || '')), JSON.stringify(found.map((x) => x.poster)));
  await r.search(w, 'batman');
  check('...the number asked for once in a while, not each time', w.asked.filter((a) => a === 'GET ' + SITE + '/').length, 1);

  w = web();
  const film = await r.title(w, 'movies/onslaught');
  check('a film from its page (its attributes in either quotes): name, year, length, genres, tagline',
    pick(film, ['title', 'year', 'minutes', 'age', 'rating', 'genres', 'tagline', 'kind']),
    { title: 'Onslaught', year: 2026, minutes: 92, genres: ['Action', 'Horror', 'Thriller'],
      tagline: 'They unleashed their deadliest weapon on us.', kind: 'film' });
  ok('...its summary, whole', /^When a rogue squad[\s\S]*young daughter\.$/.test(film.overview), film.overview);
  check('...a wide picture from its gallery (the line break in its address left out)', film.backdrop,
    'https://image.tmdb.org/t/p/w1280/pmPXXniQlb4EdYY0gVZO90rf54F.jpg');

  const series = await r.title(w, 'tvshows/the-lord-of-the-rings-the-rings-of-power');
  check('a series from its page: its seasons, with how many episodes', series.seasons, [{ n: 1, count: 8 }, { n: 2, count: 8 }]);
  ok('...its summary', /^Beginning in a time of relative peace/.test(series.overview), series.overview);
  const eps = await r.episodes(w, 'tvshows/the-lord-of-the-rings-the-rings-of-power', 2);
  check('...a season\'s episodes, each with its own page', [eps.length, eps[0]],
    [8, { n: 1, title: 'Elven Kings Under the Sky', url: SITE + '/episodes/the-lord-of-the-rings-the-rings-of-power-2x1/' }]);
  check('...its page read once for both', w.asked.filter((a) => /tvshows/.test(a)).length, 1);

  check('the page that plays a film', r.watch('movies/spider-man-no-way-home'), SITE + '/movies/spider-man-no-way-home/');
  check('...and an episode: its own page', r.watch('tvshows/x', eps[0]), eps[0].url);
})();
