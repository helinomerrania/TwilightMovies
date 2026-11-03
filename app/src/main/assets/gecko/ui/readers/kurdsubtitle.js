// Kurd Subtitle (kurdsubtitle.net): films and series with Kurdish subtitles, by their Kurdish names too, all from its
// own feed (api.kurdsubtitle.net/api/v1): its newest films and series (/movies, /tvshows), a title in full by its
// address's name (/movies/<name>, /tvshows/<name>: its Kurdish summary, genres in both, IMDb's rating, TMDB's number;
// a series' seasons), a season's episodes (/tvshows/<its own number>/seasons/<n>/episodes), its search (/search). It
// plays on the title's own page; a series' episode once its season and episode are chosen there, as the viewer
// would (player.js, by what the address says after #tm-play=).
'use strict';
TM.readers.kurdsubtitle = (function () {
  const SITE = 'https://kurdsubtitle.net';
  const API = 'https://api.kurdsubtitle.net/api/v1';
  const kindOf = (type) => (type === 'tvshow' ? 'series' : 'film');
  const part = (kind) => (kind === 'series' ? 'tvshows' : 'movies');
  const nameOf = (ref) => String(ref).replace(/^(movies|tvshows)\//, '');
  const imdb = (x) => Number(((x.ratings || []).find((r) => /Internet Movie Database/i.test(r.source)) || {}).value) || 0;
  const ids = {};   // a series' own number in the feed (its episodes are asked by it), from its title

  // A title as the feed has it (in a list, a search, in full)
  function item(x) {
    const kind = kindOf(x.type);
    return TM.item({
      site: 'kurdsubtitle', ref: part(kind) + '/' + x.slug, kind, title: TM.decode(x.title), titleKu: x.kurdishTitle || '',
      year: parseInt(x.year, 10) || 0, rating: imdb(x), minutes: kind === 'film' ? Number(x.runtime) || 0 : 0,
      genres: (x.genres || []).map((g) => g.input).filter(Boolean), genresKu: (x.genres || []).map((g) => g.name).filter(Boolean),
      overviewKu: (x.plot || '').trim(), poster: x.poster || '', tmdb: Number(x.tmdbID) || 0,
      age: x.rated && x.rated !== 'N/A' ? x.rated : '', langs: ['sub'],
    });
  }
  const docs = (r) => ((r && r.docs) || []).filter((x) => x && x.slug && (x.type === 'movie' || x.type === 'tvshow'));

  return {
    // its Kurdish name, as the website writes it itself
    id: 'kurdsubtitle', name: 'Kurd Subtitle', nameKu: 'کوردسەبتایتڵ', home: SITE + '/', hosts: ['kurdsubtitle.net'],

    async rows(web) {
      const [films, shows] = await Promise.all([web.json(API + '/movies?page=1'), web.json(API + '/tvshows?page=1')]);
      return [{ key: 'newFilms', items: docs(films).map(item) }, { key: 'newSeries', items: docs(shows).map(item) }];
    },

    async search(web, q) {
      const groups = (await web.json(API + '/search?query=' + encodeURIComponent(q))) || [];
      return [].concat(...groups.filter((g) => g && (g.type === 'movie' || g.type === 'tvshow')).map((g) => docs(g.data)))
        .map(item);
    },

    async title(web, ref) {
      const x = ((await web.json(API + '/' + ref)) || {}).movie || {};
      const t = item(x);
      if (t.kind === 'series') {
        ids[ref] = x._id;
        t.seasons = (x.seasons || []).filter((s) => !s.hidden && Number(s.episodeCount))
          .map((s) => ({ n: Number(s.number), count: Number(s.episodeCount) }));
      }
      return t;
    },

    async episodes(web, ref, season) {
      if (!ids[ref]) await this.title(web, ref);
      const eps = (await web.json(API + '/tvshows/' + ids[ref] + '/seasons/' + Number(season) + '/episodes')) || [];
      return eps.filter((e) => e && !e.hidden).map((e) => TM.item({ n: Number(e.number), title: e.name || '' }));
    },

    /** The title's own page; a series' at the episode (its season and number, for player.js to choose there). */
    watch(ref, ep) {
      return SITE + '/' + part(/^tvshows\//.test(ref) ? 'series' : 'film') + '/' + encodeURIComponent(nameOf(ref))
        + (ep && ep.season && ep.n ? '#tm-play=' + Number(ep.season) + '-' + Number(ep.n) : '');
    },
  };
})();
