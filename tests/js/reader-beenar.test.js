// Beenar's reader (app/src/main/assets/gecko/ui/readers/beenar.js), with its data feed's saved answers
// (tests/js/fixtures/readers/beenar/, from tests/try/probe.js): its home's lists, search (asked the way its own
// pages ask), a film and a series in full, a season's episodes, and the pages that play them.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/beenar.js']);
const r = TM.readers.beenar;
const web = () => saved('beenar', [
  [/\/api\/client\/main$/, 'main.json'], [/\/api\/client\/movie$/, 'movie-list.json'],
  [/\/api\/client\/title-show\/3057$/, 'title-show-3057.json'], [/\/api\/client\/series-show\/697\?part=\d$/, 'series-show-697.json'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: newest films, newest series, the big pictures', rows.map((x) => x.key), ['newFilms', 'newSeries', 'featured']);
  const film = rows[0].items[0];
  check('...a film as the list gives it: its name, Kurdish summary and genres, IMDb rating, its poster\'s address',
    pick(film, ['site', 'ref', 'kind', 'title', 'year', 'minutes', 'rating', 'langs', 'poster']),
    { site: 'beenar', ref: 'f:3061', kind: 'film', title: 'Animals', year: 2026, minutes: 107, rating: 5.7, langs: ['sub'],
      poster: 'https://beenar.net/storage/media-images/poster/wVafbUZC3RjSmeXLSkkI6xRJ23ApjoYYSj4x5DvI.jpg' });
  ok('...its summary, in Kurdish', /^کاتێک/.test(film.overviewKu), film.overviewKu);
  ok('...its genres, in Kurdish', film.genresKu && film.genresKu.includes('ئاکشن'), JSON.stringify(film.genresKu));
  check('...a series', pick(rows[1].items[0], ['ref', 'kind', 'title']), { ref: 's:697', kind: 'series', title: 'Lupin' });
  check('...a big picture: the title with its wide picture', pick(rows[2].items[0], ['ref', 'backdrop']),
    { ref: 's:697', backdrop: 'https://beenar.net/storage/media-images/backdrops/Dq95PAXx79wVtKRXsdSs5OTjSCh6VvR7N2rmqfG6.jpg' });

  w = web();
  const found = await r.search(w, 'spider');
  check('search: its films and its series, each list asked by name, newest first', w.asked.map((a) => JSON.parse(a.slice(a.indexOf(' {') + 1))).map((b) => [b.part, b.filter_name, b.filter_sort]),
    [['1', 'spider', 'newest'], ['2', 'spider', 'newest']]);
  ok('...asked as its own pages ask (POST, JSON)', w.asked.every((a) => a.startsWith('POST https://beenar.net/api/client/movie {')), w.asked[0]);
  check('...what both gave', found.length, 4);

  w = web();
  const hope = await r.title(w, 'f:3057');
  check('a film in full: its English and Kurdish names, rating, age', pick(hope, ['title', 'titleKu', 'rating', 'age', 'year', 'tagline']),
    { title: 'Hope', titleKu: 'هیوا', rating: 6.8, age: 'R', year: 2026, tagline: 'لە ڕاوچییەوە بوون بە نێچیر' });
  const lupin = await r.title(w, 's:697');
  check('a series in full: its seasons, with how many episodes', lupin.seasons, [{ n: 1, count: 10 }, { n: 2, count: 5 }, { n: 3, count: 7 }, { n: 4, count: 8 }]);
  w = web();
  const eps = await r.episodes(w, 's:697', 1);
  check('a season\'s episodes: the ones the feed names, and numbers for the rest', [eps.length, pick(eps[0], ['n', 'title']), eps[9]],
    [10, { n: 1, title: 'Chapter 1' }, { n: 10 }]);
  ok('...the feed asked a page at a time, no more than it gives', w.asked.length <= 6, w.asked.join(', '));

  check('the page that plays a film', r.watch('f:3057'), 'https://beenar.net/detail-cowatching/3057?series=false');
  check('...and a series', r.watch('s:697', { season: 1, n: 2 }), 'https://beenar.net/detail-cowatching/697?series=true');
})();
