// Kurdsaz's reader (app/src/main/assets/gecko/ui/readers/kurdsaz.js), with its pages' saved copies
// (tests/js/fixtures/readers/kurdsaz/: the parts it reads of the pages as they come, from tests/try/probe.js): its
// lists, a series in full from its page, its seasons and episodes from its player's page, and where each plays.
'use strict';
const { check, ok } = require('./lib/check');
const { load, saved, pick } = require('./lib/ui');

const TM = load(['tm.js', 'readers/kurdsaz.js']);
const r = TM.readers.kurdsaz;
const SITE = 'https://kurdsaz.com';
const web = () => saved('kurdsaz', [
  [/\/browse\.php\?type=movie$/, 'movies.html'], [/\/browse\.php\?type=series$/, 'series-list.html'],
  [/\/details\.php\?slug=100-Days-of-Deception$/, 'series.html'], [/\/watch\.php\?slug=100-Days-of-Deception$/, 'watch.html'],
]);

module.exports = (async () => {
  let w = web();
  const rows = await r.rows(w);
  check('its lists: its films and its series pages', [rows.map((x) => x.key), w.asked],
    [['newFilms', 'newSeries'], ['GET ' + SITE + '/browse.php?type=movie', 'GET ' + SITE + '/browse.php?type=series']]);
  check('...a film as its list shows it: its page, year, rating, a poster on the website', pick(rows[0].items[1],
    ['site', 'ref', 'kind', 'title', 'year', 'rating', 'langs', 'poster']),
  { site: 'kurdsaz', ref: 'Apocalypse-Z-The-Beginning-of-the-End', kind: 'film', title: 'Apocalypse Z: The Beginning of the End',
    year: 2024, rating: 6.3, langs: ['sub'], poster: SITE + '/assets/uploads/img_6ae8cc06a89b71.92746001.webp' });
  check('...a name with a space in it, as the website writes it', rows[0].items[0].ref, 'Little Forest');
  check('...a series', pick(rows[1].items[1], ['ref', 'kind', 'year']), { ref: 'Weak-Hero-Class', kind: 'series', year: 2022 });

  w = web();
  const s = await r.title(w, '100-Days-of-Deception');
  check('a series from its page: name, year, rating, age, length, Kurdish genres',
    pick(s, ['kind', 'title', 'year', 'rating', 'age', 'minutes', 'genresKu']),
    { kind: 'series', title: '100 Days of Deception', year: 2026, rating: 8.7, age: '15', minutes: 70,
      genresKu: ['ئاکشن', 'ڕۆمانسی', 'مێژوویی'] });
  ok('...its Kurdish summary', /^لە کۆریای ساڵانی ١٩٣٠/.test(s.overviewKu || ''), s.overviewKu);
  ok('...its poster, and a wide picture', /\/assets\/uploads\/img_6acbd1b397de01/.test(s.poster) && /img_6acbd1b47517a8/.test(s.backdrop || ''),
    s.poster + ' ' + s.backdrop);
  check('...its seasons, from its player\'s page', s.seasons, [{ n: 1, count: 8 }]);
  const eps = await r.episodes(w, '100-Days-of-Deception', 1);
  check('...a season\'s episodes, each with its own number', [eps.length, eps[0], eps[6]], [8, { n: 1, id: '746' }, { n: 7, id: '899' }]);
  check('...its pages read once for both', w.asked.length, 2);

  check('where a film plays', r.watch('Little Forest'), SITE + '/watch.php?slug=Little%20Forest');
  check('...and an episode', r.watch('100-Days-of-Deception', eps[6]), SITE + '/watch.php?slug=100-Days-of-Deception&ep=899');
})();
