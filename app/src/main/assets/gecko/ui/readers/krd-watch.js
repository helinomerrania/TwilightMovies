// KRD WATCH (krd.watch): films and series with Kurdish subtitles. Everything comes from its own data feed (/api/...),
// which numbers titles as TMDB does: its lists (/api/discover, newest first), what was added lately (/api/feed),
// search (/api/search), a title in full with its Kurdish name, summary and genres (/api/title), and a season's
// episodes (/api/season). It plays in its own player (/watch/...).
'use strict';
TM.readers['krd-watch'] = (function () {
  const SITE = 'https://krd.watch';
  const pic = (p) => (!p ? '' : p.charAt(0) === '/' ? SITE + p : p);
  const split = (g) => String(g || '').split(',').map((s) => s.trim()).filter(Boolean);
  const playable = (x) => x && x.playable !== false;
  const round = (n) => (n ? Math.round(n * 10) / 10 : 0);

  // A title as its lists and its search give it
  function item(x) {
    const series = x.kind === 'tv';
    return TM.item({
      site: 'krd-watch', ref: (series ? 'tv:' : 'movie:') + x.tmdbId, kind: series ? 'series' : 'film', tmdb: x.tmdbId,
      title: x.title, titleKu: x.titleKu, year: x.year, rating: round(x.rating), minutes: x.runtime, genres: split(x.genre),
      overview: x.overview, overviewKu: x.overviewKu, poster: TM.size(pic(x.poster), 'w342'), backdrop: pic(x.backdrop),
      langs: ['sub'],
    });
  }

  // What was added lately: a series once, however many of its episodes came
  function added(feed) {
    const seen = {};
    const out = [];
    (feed.items || []).forEach((x) => {
      const m = /^\/tv\/(\d+)/.exec(x.href || '');
      if (!m || seen[m[1]]) return;
      seen[m[1]] = true;
      out.push(TM.item({ site: 'krd-watch', ref: 'tv:' + m[1], kind: 'series', tmdb: Number(m[1]), title: x.title,
        year: x.year, poster: TM.size(x.poster, 'w342'), langs: ['sub'], note: x.label }));
    });
    return out;
  }

  return {
    id: 'krd-watch', name: 'KRD WATCH', home: SITE + '/', hosts: ['krd.watch'],

    async rows(web) {
      const [films, feed] = await Promise.all([web.json(SITE + '/api/discover?kind=movie&page=1'), web.json(SITE + '/api/feed')]);
      return [
        { key: 'newFilms', items: (films.items || []).filter(playable).map(item) },
        { key: 'newSeries', items: added(feed) },
      ];
    },

    async search(web, q) {
      const r = await web.json(SITE + '/api/search?q=' + encodeURIComponent(q));
      return (r.results || []).filter(playable).map(item);
    },

    // A title in full: its name, summary and genres in Kurdish (the English name: originalTitle), a series' seasons
    async title(web, ref) {
      const [kind, id] = ref.split(':');
      const x = await web.json(SITE + '/api/title/' + kind + '/' + id);
      const english = x.originalTitle || x.title;
      return Object.assign(item({ kind: x.kind || kind, tmdbId: x.tmdbId || Number(id), title: english, year: x.year,
        titleKu: x.title !== english ? x.title : '', rating: x.rating, runtime: x.runtime, poster: x.poster,
        backdrop: x.backdrop, overviewKu: x.overview }), TM.item({
        genresKu: x.genres, tagline: x.tagline, age: x.certification,
        seasons: (x.seasons || []).map((s) => ({ n: s.season, count: (s.episodes || []).length })),
      }));
    },

    async episodes(web, ref, season) {
      const list = await web.json(SITE + '/api/season/' + ref.split(':')[1] + '/' + season);
      return (Array.isArray(list) ? list : []).map((e) => TM.item({ n: e.episode, title: e.name, minutes: e.runtime,
        overview: e.overview, overviewKu: e.overviewKu }));
    },

    /** The address that plays it: a film, or a series' episode (season and episode numbers). */
    watch(ref, ep) {
      const [kind, id] = ref.split(':');
      if (kind !== 'tv') return SITE + '/watch/' + id;
      return SITE + '/watch/tv/' + id + '?s=' + ((ep && ep.season) || 1) + '&e=' + ((ep && ep.n) || 1);
    },
  };
})();
