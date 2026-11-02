// Kartoni Kurdi's reader (app/src/main/assets/gecko/ui/readers/kartonikurde.js), with its pages' saved copies
// (tests/js/fixtures/readers/kartonikurde/: the parts it reads of the pages as they come, from tests/try/probe.js):
// its lists, a film and a series in full from their pages (their Kurdish and English names), a series' episodes,
// and where each plays.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/kartonikurde.js']);
const r = TM.readers.kartonikurde;
const SITE = 'https://www.kartonikurde.com';
const web = () => saved('kartonikurde', [
  [/\/movies$/, 'movies.html'], [/\/series$/, 'series-list.html'], [/\/movie\/frozen$/, 'film.html'], [/\/serie\/grendizer$/, 'series.html'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: its dubbed films and series', [rows.map((x) => x.key), w.asked], [['newFilms', 'newSeries'], ['GET ' + SITE + '/movies', 'GET ' + SITE + '/series']]);
  check('...a film as its list shows it: its Kurdish name, its English one from its address, year, genre, rating, dubbed',
    pick(rows[0].items[0], ['ref', 'kind', 'title', 'titleKu', 'year', 'genresKu', 'rating', 'langs']),
    { ref: 'movie/home_2015', kind: 'film', title: 'Home', titleKu: 'نیشتیمان', year: 2015, genresKu: ['ئەنیمەیشن'], rating: 6.5, langs: ['dub'] });
  ok('...its summary, and its poster', /^نامۆیەک لە ڕاکردندایە/.test(rows[0].items[0].overviewKu)
    && /\/storage\/images\/movies\/optimized\//.test(rows[0].items[0].poster), JSON.stringify(rows[0].items[0]));
  check('...a series', pick(rows[1].items[1], ['ref', 'kind', 'title', 'titleKu', 'year']),
    { ref: 'serie/grendizer', kind: 'series', title: 'Grendizer', titleKu: 'گلیندایزەر', year: 1975 });

  w = web();
  const film = await r.title(w, 'movie/frozen');
  check('a film from its page: both names, year, age, length, IMDb rating, genres in English and Kurdish',
    pick(film, ['title', 'titleKu', 'year', 'age', 'minutes', 'rating', 'genres', 'genresKu']),
    { title: 'Frozen', titleKu: 'فرۆزن', year: 2012, age: '8', minutes: 102, rating: 6.2, genres: ['Animation', 'Family', 'Adventure'],
      genresKu: ['ئەنیمەیشن', 'خێزانی', 'سەرکێشی'] });
  ok('...its summary (not what its signs say)', /^کاتێک ئێلسای شاژنی نوێ/.test(film.overviewKu), film.overviewKu);
  const s = await r.title(w, 'serie/grendizer');
  check('a series: its episodes, in one season', s.seasons, [{ n: 1, count: 3 }]);
  check('...each with its own number', await r.episodes(w, 'serie/grendizer', 1), [{ n: 1, id: '503' }, { n: 2, id: '504' }, { n: 3, id: '505' }]);
  check('...its page read once for both', w.asked.filter((a) => /serie\//.test(a)).length, 1);
  check('where a title plays: its own page', r.watch('serie/grendizer', { n: 2 }), SITE + '/serie/grendizer');

  // Search: the search box's state from a page (its snapshot, the page's token), then the words, to Livewire. The
  // answer here is a stand-in, in the shape the website answers (what it found, as the box shows it)
  w = saved('kartonikurde', [[/\/series$/, 'series.html'], [/\/livewire\/update$/, 'search-frozen.json']]);
  const found = await r.search(w, 'frozen');
  const sent = JSON.parse(w.asked[1].replace(/^POST \S+ /, ''));
  check('search: a page first, then the search box\'s own state with the words', [w.asked.length, sent.components[0].updates,
    /&quot;|search-form/.test(sent.components[0].snapshot), sent._token.length > 10], [2, { searchText: 'frozen' }, true, true]);
  check('...what it found: its Kurdish name, and its English one from the brackets', pick(found[0], ['ref', 'kind', 'title', 'titleKu', 'langs']),
    { ref: 'movie/frozen', kind: 'film', title: 'Frozen', titleKu: 'فرۆزن', langs: ['dub'] });
})();
