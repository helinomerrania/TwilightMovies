// Kurd Cinema's reader (app/src/main/assets/gecko/ui/readers/kurdcinama.js), with its pages' saved copies
// (tests/js/fixtures/readers/kurdcinama/: the parts it reads of the pages as they come, from tests/try/probe.js): its
// lists, a film and a series in full from their pages (TMDB's numbers for them), a season's episodes, where each plays.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/kurdcinama.js']);
const r = TM.readers.kurdcinama;
const SITE = 'https://kurdcinama.com';
const web = () => saved('kurdcinama', [
  [/\/movies\.aspx$/, 'movies.html'], [/\/drama\.aspx$/, 'drama.html'], [/\/moves-details\.aspx\?movieid=18470$/, 'film.html'],
  [/\/Episodes\.aspx\?type=12$/, 'series.html'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: its films and series pages', [rows.map((x) => x.key), w.asked], [['newFilms', 'newSeries'], ['GET ' + SITE + '/movies.aspx', 'GET ' + SITE + '/drama.aspx']]);
  check('...a film as its list shows it: its name without the year, the year, rating, poster', rows[0].items[0],
    { site: 'kurdcinama', ref: 'm:19786', kind: 'film', title: 'Resident Evil', year: 2026, rating: 7.5,
      poster: SITE + '/Wenekan_KS/56182112026_Resident-Evil-1-copy.webp', langs: ['sub'] });
  check('...a series', pick(rows[1].items[1], ['ref', 'kind', 'title', 'rating']), { ref: 's:282', kind: 'series', title: 'Lupin', rating: 7.7 });

  w = web();
  const film = await r.title(w, 'm:18470');
  check('a film from its page: TMDB\'s number, name, year, IMDb rating, length, Kurdish genres',
    pick(film, ['tmdb', 'title', 'year', 'rating', 'minutes', 'genresKu']),
    { tmdb: 812583, title: 'Wake Up Dead Man: A Knives Out Mystery', year: 2025, rating: 7.9, minutes: 144,
      genresKu: ['تاوان', 'نهێنی', 'چیرۆكی هه‌ستبزوێن'] });
  ok('...its Kurdish summary and its picture', /^مردنێکی سەرسوڕهێنەر/.test(film.overviewKu) && /\/Wenekan_KS\//.test(film.backdrop), JSON.stringify(film));
  const s = await r.title(w, 's:12');
  check('a series from its page: TMDB\'s number, name, its seasons in order with how many episodes',
    pick(s, ['tmdb', 'title', 'seasons']), { tmdb: 66732, title: 'Stranger Things', seasons: [{ n: 1, count: 8 }, { n: 2, count: 9 }] });
  const eps = await r.episodes(w, 's:12', 2);
  check('...a season\'s episodes, each with its own page', [eps.length, eps[0]], [9, { n: 1, url: SITE + '/Episodes2.aspx?type=12&Stype=168&name=01' }]);
  check('...its page read once for both', w.asked.filter((a) => /Episodes\.aspx/.test(a)).length, 1);

  check('where a film plays: its player\'s page', r.watch('m:18470'), SITE + '/online.aspx?movieid=18470');
  check('...and an episode: its own page', r.watch('s:12', eps[0]), eps[0].url);
})();
