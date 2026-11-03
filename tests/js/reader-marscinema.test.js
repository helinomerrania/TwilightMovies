// Mars Cinema's reader (app/src/main/assets/gecko/ui/readers/marscinema.js), with its pages' saved copies
// (tests/js/fixtures/readers/marscinema/: the parts it reads of the pages as they come, from tests/try/study.sh and
// probe.js): its home's newest films and series, a film and a series in full from their pages, a series' seasons and
// episodes from its player's page (the page's own data), its search, and where each plays.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/marscinema.js']);
const r = TM.readers.marscinema;
const SITE = 'https://marscinema.net';
const web = () => saved('marscinema', [
  [/\.net\/$/, 'home.html'], [/\/movie\/dark-city$/, 'film.html'], [/\/series\/barry$/, 'series.html'],
  [/\/watch\/barry$/, 'watch.html'], [/\/api\/search\?q=spider$/, 'search-spider.json'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: its home\'s newest films and newest series, read once', [rows.map((x) => x.key), w.asked],
    [['newFilms', 'newSeries'], ['GET ' + SITE + '/']]);
  check('...a film as its home shows it: its page, name, year, Kurdish genre, poster', pick(rows[0].items[1],
    ['site', 'ref', 'kind', 'title', 'year', 'genresKu', 'langs', 'poster']),
  { site: 'marscinema', ref: 'movie/dark-city', kind: 'film', title: 'Dark City', year: 1998, genresKu: ['دراما'], langs: ['sub'],
    poster: 'https://media.marscinema.net/posters/801bbc91-f62f-412b-ae52-debae66a9776.jpg' });
  check('...a name with an apostrophe in it, as the website writes it', rows[0].items[0].title, 'What\'s the Time in Your World');
  check('...its series, not a series the big pictures at its top link to', rows[1].items.map((x) => [x.ref, x.kind, x.year]),
    [['series/visionquest', 'series', 2026], ['series/mobland', 'series', 2025]]);

  w = web();
  const f = await r.title(w, 'movie/dark-city');
  check('a film from its page: name, year, length, age, IMDb\'s rating, Kurdish genres',
    pick(f, ['kind', 'title', 'year', 'minutes', 'age', 'rating', 'genresKu', 'langs']),
    { kind: 'film', title: 'Dark City', year: 1998, minutes: 100, age: 'TV-14', rating: 7.5, genresKu: ['دراما', 'زانستی خەیاڵی'],
      langs: ['sub'] });
  ok('...its Kurdish summary, and its line in quotes', /^جۆن لە هۆتێلێک/.test(f.overviewKu || '') && /^کاتێک مرۆڤ/.test(f.tagline || ''),
    f.overviewKu + ' | ' + f.tagline);
  ok('...its poster, and a wide picture', /\/posters\/801bbc91/.test(f.poster) && /\/backdrops\//.test(f.backdrop || ''), f.poster + ' ' + f.backdrop);
  check('...its page alone', w.asked, ['GET ' + SITE + '/movie/dark-city']);

  w = web();
  const s = await r.title(w, 'series/barry');
  check('a series from its page', pick(s, ['kind', 'title', 'year', 'age', 'rating', 'genresKu']),
    { kind: 'series', title: 'Barry', year: 2018, age: 'TV-14', rating: 8.3, genresKu: ['ئاکشن', 'تاوان', 'دراما'] });
  ok('...not a length (its count of episodes and seasons is where a film\'s length is)', !s.minutes, s.minutes);
  check('...its seasons, from its player\'s page', s.seasons, [{ n: 1, count: 8 }, { n: 2, count: 8 }]);
  const eps = await r.episodes(w, 'series/barry', 2);
  check('...a season\'s episodes, each with its own number, its Kurdish name and length', [eps.length, eps[0], pick(eps[7], ['n', 'id'])],
    [8, { n: 1, id: '418', title: 'پێویستە نمایشەکە بەردەوام بێت', minutes: 30 }, { n: 8, id: '425' }]);
  check('...its pages read once for both', w.asked, ['GET ' + SITE + '/series/barry', 'GET ' + SITE + '/watch/barry']);

  w = web();
  const found = await r.search(w, 'spider');
  check('its search: its films and its series', found.map((x) => [x.ref, x.kind, x.title, x.year]),
    [['movie/ss', 'film', 'Spider-Man: Brand New Day', 2026], ['series/spider-noir-kurdish', 'series', 'Spider-Noir', 2026]]);
  ok('...with their Kurdish summaries and posters', /پیتەر پاركەر/.test(found[0].overviewKu) && /^https:\/\//.test(found[0].poster));

  check('where a film plays', r.watch('movie/dark-city'), SITE + '/watch/dark-city');
  check('...and an episode', r.watch('series/barry', eps[7]), SITE + '/watch/barry?ep=425');
})();
