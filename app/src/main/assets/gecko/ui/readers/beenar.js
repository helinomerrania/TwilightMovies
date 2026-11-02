// Beenar (beenar.net): films and series with Kurdish subtitles, from its own data feed (/api/client/...): the home's
// lists (main: newest films, newest series, the big pictures), search (the list of films or series, by name),
// a title in full (title-show, series-show) with Kurdish names, summaries and taglines, IMDb ratings and each
// season's episodes. It plays on its own page (/detail-cowatching/...).
'use strict';
TM.readers.beenar = (function () {
  const SITE = 'https://beenar.net';
  const pic = (p) => (!p ? '' : /^https?:/.test(p) ? p : SITE + '/' + String(p).replace(/^\//, ''));
  const year = (d) => Number(String(d || '').slice(0, 4)) || 0;
  const genres = (x) => (x.genres || []).map((g) => (g.genre ? g.genre.name : g.name)).filter(Boolean);
  function imdb(x) {
    const r = (x.nmra || []).find((n) => n.nmra_relation && n.nmra_relation.name === 'IMDb');
    return r ? Number(r.nmra) || 0 : 0;
  }

  // A title as the lists, search and the title's own page give it ("name_en" holds its Kurdish name)
  function item(x) {
    const series = !!Number(x.is_series);
    return TM.item({
      site: 'beenar', ref: (series ? 's:' : 'f:') + x.id, kind: series ? 'series' : 'film', title: x.name,
      titleKu: x.name_en && x.name_en !== x.name ? x.name_en : '', year: year(x.release_date), rating: imdb(x),
      minutes: Number(x.runtime) || 0, genresKu: genres(x), overviewKu: x.description, tagline: x.tagline,
      poster: pic(x.path_poster || x.poster), backdrop: pic(x.path_backdrop || x.backdrop), langs: ['sub'],
      quality: x.is_4k ? '4K' : '', age: x.certification ? x.certification.name : '',
    });
  }

  // Its list of films (part 1) or series (part 2), newest first, as its own pages ask for it
  function list(web, part, name) {
    const body = { filter_genres: [], filter_years: [], filter_name: name || '', filter_country: null, filter_sort: 'newest',
      filter_runtime: null, filter_status: null, filter_4k: false, page: '1', part: String(part), type: null, lang: null };
    return web.json(SITE + '/api/client/movie', { method: 'POST', body: JSON.stringify(body), headers: {
      Accept: 'application/json, text/plain, */*', 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' } })
      .then((r) => ((r && r.data && r.data.data) || []).map(item));
  }

  // A series' page of the feed, with the episodes it lists for each season
  const series = (web, id, part) => web.json(SITE + '/api/client/series-show/' + id + '?part=' + part).then((r) => (r && r.data) || {});

  return {
    id: 'beenar', name: 'Beenar', nameKu: 'بینەر', home: SITE + '/', hosts: ['beenar.net'],

    async rows(web) {
      const m = await web.json(SITE + '/api/client/main');
      return [
        { key: 'newFilms', items: (m.newest_movie || []).map(item) },
        { key: 'newSeries', items: (m.newest_serise || []).map(item) },
        { key: 'featured', items: (m.slide || []).map((s) => s.title).filter(Boolean).map(item) },
      ];
    },

    async search(web, q) {
      const [films, shows] = await Promise.all([list(web, 1, q), list(web, 2, q)]);
      return films.concat(shows);
    },

    async title(web, ref) {
      const [k, id] = ref.split(':');
      const x = k === 's' ? await series(web, id, 1)
        : await web.json(SITE + '/api/client/title-show/' + id).then((r) => (r && r.data) || {});
      return Object.assign(item(x), TM.item({
        seasons: (x.seasons || []).map((s) => ({ n: Number(s.number), count: Number(s.episodes_count) || (s.episodes || []).length })),
      }));
    },

    // A season's episodes: the feed lists them a page at a time ("part"); numbered ones where it gives none
    async episodes(web, ref, season) {
      const id = ref.split(':')[1];
      const found = {};
      let count = 0;
      for (let part = 1; part <= 6; part++) {
        const x = await series(web, id, part);
        const s = (x.seasons || []).find((y) => Number(y.number) === Number(season));
        if (!s) break;
        count = Number(s.episodes_count) || count;
        const before = Object.keys(found).length;
        (s.episodes || []).forEach((e) => { found[Number(e.episode_number)] = TM.item({ n: Number(e.episode_number), title: e.name, id: e.id }); });
        if (Object.keys(found).length >= count || Object.keys(found).length === before) break;
      }
      const out = [];
      for (let n = 1; n <= Math.max(count, Object.keys(found).length); n++) out.push(found[n] || { n });
      return out;
    },

    /** Its page that plays the film, or the series (its page has the episodes). */
    watch(ref) {
      const [k, id] = ref.split(':');
      return SITE + '/detail-cowatching/' + id + '?series=' + (k === 's' ? 'true' : 'false');
    },
  };
})();
