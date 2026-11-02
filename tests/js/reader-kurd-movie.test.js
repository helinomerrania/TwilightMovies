// Kurd Movie's reader (app/src/main/assets/gecko/ui/readers/kurd-movie.js), with its pages' saved copies
// (tests/js/fixtures/readers/kurd-movie/, from tests/try/probe.js): its lists, search (with the number its pages
// carry), a film and a series in full from their pages, a season's episodes, and the pages that play them.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/kurd-movie.js']);
const r = TM.readers['kurd-movie'];
const SITE = 'https://www.kurd-movie.com';
const web = () => saved('kurd-movie', [
  [/\/movies\/$/, 'movies.html'], [/\/tvshows\/$/, 'tvshows.html'], [/\.com\/$/, 'home.html'],
  [/\/wp-json\/dooplay\/search\//, 'search-spider.json'], [/\/movies\/spider-man-no-way-home\/$/, 'film.html'],
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
  check('...a series', pick(rows[1].items[0], ['kind']), { kind: 'series' });
  ok('...each series on its own page', rows[1].items.every((x) => /^tvshows\//.test(x.ref)), JSON.stringify(rows[1].items.map((x) => x.ref)));

  w = web();
  const found = await r.search(w, 'spider');
  check('search: the number its pages carry first, then WordPress\'s search with it', w.asked,
    ['GET ' + SITE + '/', 'GET ' + SITE + '/wp-json/dooplay/search/?keyword=spider&nonce=23d5ccf3c1']);
  const dubbed = found.find((x) => x.ref === 'movies/8308');
  check('...a dubbed film: "doblaj" taken off its name, and Kurdish dubbing', pick(dubbed, ['title', 'langs', 'year', 'rating', 'kind']),
    { title: 'Spider-Man: Far from Home', langs: ['dub'], year: 2019, rating: 7.3, kind: 'film' });
  await r.search(w, 'batman');
  check('...the number asked for once in a while, not each time', w.asked.filter((a) => a === 'GET ' + SITE + '/').length, 1);

  w = web();
  const film = await r.title(w, 'movies/spider-man-no-way-home');
  check('a film from its page: name, year, length, age, IMDb rating, genres, tagline',
    pick(film, ['title', 'year', 'minutes', 'age', 'rating', 'genres', 'tagline', 'kind']),
    { title: 'Spider-Man: No Way Home', year: 2021, minutes: 148, age: 'PG-13', rating: 8.1,
      genres: ['Action', 'Adventure', 'Science Fiction'], tagline: 'Enter the Multiverse.', kind: 'film' });
  ok('...its summary (not the player that comes after it)', /^Peter Parker is unmasked/.test(film.overview) && !/iframe/.test(film.overview), film.overview);
  ok('...a wide picture from its gallery', /^https:\/\/image\.tmdb\.org\/t\/p\/w1280\//.test(film.backdrop), film.backdrop);

  const series = await r.title(w, 'tvshows/the-lord-of-the-rings-the-rings-of-power');
  check('a series from its page: its seasons, with how many episodes', series.seasons, [{ n: 1, count: 8 }, { n: 2, count: 8 }]);
  const eps = await r.episodes(w, 'tvshows/the-lord-of-the-rings-the-rings-of-power', 2);
  check('...a season\'s episodes, each with its own page', [eps.length, eps[0]],
    [8, { n: 1, title: 'Elven Kings Under the Sky', url: SITE + '/episodes/the-lord-of-the-rings-the-rings-of-power-2x1/' }]);
  check('...its page read once for both', w.asked.filter((a) => /tvshows/.test(a)).length, 1);

  check('the page that plays a film', r.watch('movies/spider-man-no-way-home'), SITE + '/movies/spider-man-no-way-home/');
  check('...and an episode: its own page', r.watch('tvshows/x', eps[0]), eps[0].url);
})();
