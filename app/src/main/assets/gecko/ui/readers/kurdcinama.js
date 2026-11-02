// Kurd Cinema (kurdcinama.com): films and series with Kurdish subtitles. Its lists, a title in full (TMDB's number
// for it, its Kurdish summary and genres) and a series' seasons and episodes come from its pages (movies.aspx,
// drama.aspx, moves-details.aspx, Episodes.aspx); its search from its own feed (Search.aspx?ajax=1). A film plays
// on its player's page (online.aspx), an episode on its own (Episodes2.aspx).
'use strict';
TM.readers.kurdcinama = (function () {
  const SITE = 'https://kurdcinama.com';
  const one = (re, s) => { const m = re.exec(s); return m ? (m[1] || '').trim() : ''; };
  const all = (re, s) => { const out = []; let m; while ((m = re.exec(s))) out.push(m[1].trim()); return out; };
  const pic = (p) => (!p ? '' : /^https?:/.test(p) ? p : SITE + '/' + p.replace(/^\//, ''));
  const plain = (t) => TM.text(t).replace(/\s*\((\d{4})\)\s*$/, '');   // "Resident Evil (2026)": its name
  const yearIn = (t) => Number(one(/\((\d{4})\)\s*$/, TM.text(t))) || 0;
  const refOf = (url) => (/movieid=(\d+)/i.test(url) ? 'm:' + one(/movieid=(\d+)/i, url) : /type=(\d+)/i.test(url) ? 's:' + one(/type=(\d+)/i, url) : '');
  const pages = {};   // a title's page, read once a while: a series' episodes are on it

  // A film of its list (a "movie-card"), a series of its (a "drama-card")
  function film(a) {
    const name = one(/class="movie-card__title"[^>]*>([\s\S]*?)<\/h3>/, a);
    return TM.item({ site: 'kurdcinama', ref: refOf(a), kind: 'film', title: plain(name), year: yearIn(name),
      rating: Number(one(/movie-card__rating"[^>]*>(?:\s*<i[^>]*><\/i>)?\s*([\d.]+)/, a)) || 0,
      poster: pic(one(/<img[^>]*\bsrc="([^"]+)"/, a)), langs: ['sub'] });
  }
  function show(a) {
    return TM.item({ site: 'kurdcinama', ref: refOf(a), kind: 'series', title: TM.text(one(/class="drama-card__title"[^>]*>([\s\S]*?)<\/h3>/, a)),
      rating: Number(one(/drama-card__rating"[^>]*>(?:\s*<i[^>]*><\/i>)?\s*([\d.]+)/, a)) || 0,
      poster: pic(one(/<img[^>]*\bsrc="([^"]+)"/, a)), langs: ['sub'] });
  }
  const films = (html) => (String(html).match(/<article class="movie-card">[\s\S]*?<\/article>/g) || []).map(film).filter((x) => x.ref);
  const shows = (html) => (String(html).match(/<a href="Episodes\.aspx\?type=\d+" class="drama-card">[\s\S]*?<\/h3>/gi) || []).map(show)
    .filter((x) => x.ref);

  // A film's page: TMDB's number, its name and year, IMDb rating, length, Kurdish genres and summary, its picture
  function filmPage(html, ref) {
    const name = one(/<h1 class="tmdb-title"[^>]*>([\s\S]*?)<\/h1>/, html);
    const wide = one(/id="tmdb-backdrop-img"[^>]*\bsrc="([^"]+)"/, html);
    return TM.item({
      site: 'kurdcinama', ref, kind: 'film', tmdb: Number(one(/id="tmdbId" value="(\d+)"/, html)) || 0, title: plain(name), year: yearIn(name),
      rating: Number(one(/class="tmdb-rating">\s*<img[^>]*>\s*([\d.]+)/, html)) || 0,
      minutes: Number(one(/<i class="bi bi-clock"><\/i>\s*(\d+)/, html)) || 0,
      genresKu: all(/class="tmdb-genre"[^>]*>([^<]+)</g, html).map(TM.decode),
      overviewKu: TM.text(one(/<p class="tmdb-synopsis"[^>]*>([\s\S]*?)<\/p>/, html)),
      poster: pic(one(/class="tmdb-poster-card"[^>]*>\s*<img[^>]*\bsrc="([^"]+)"/, html)) || pic(wide), backdrop: pic(wide), langs: ['sub'],
    });
  }

  // A series' page: TMDB's number, its name, Kurdish summary, poster; its seasons in order, each with its episodes
  function showPage(html, ref) {
    const found = html.split('class="season-block"').slice(1).map((block, i) => ({
      n: i + 1,
      episodes: Array.from(block.matchAll(/href="(Episodes2\.aspx\?[^"]+)" class="episode-btn">[\s\S]*?episode-btn__number">\s*(\d+)/g))
        .map((e) => ({ n: Number(e[2]), url: SITE + '/' + TM.decode(e[1]) })),
    }));
    return TM.item({
      site: 'kurdcinama', ref, kind: 'series', tmdb: Number(one(/data-tmdbid="(\d+)"/, html)) || 0,
      title: TM.text(one(/<h1 class="series-cover__title"[^>]*>([\s\S]*?)<\/h1>/, html)),
      overviewKu: TM.text(one(/<p class="series-cover__summary"[^>]*>([\s\S]*?)<\/p>/, html)),
      poster: pic(one(/class="series-cover__poster"[^>]*>\s*<img[^>]*\bsrc="([^"]+)"/, html)), langs: ['sub'],
      seasons: found.map((s) => ({ n: s.n, count: s.episodes.length })), episodesBySeason: found,
    });
  }

  const pageOf = (ref) => SITE + (ref.charAt(0) === 'm' ? '/moves-details.aspx?movieid=' : '/Episodes.aspx?type=') + ref.slice(2);

  async function read(web, ref) {
    const kept = pages[ref];
    if (kept && Date.now() - kept.at < 10 * 60 * 1000) return kept.title;
    const html = await web.text(pageOf(ref));
    const title = ref.charAt(0) === 'm' ? filmPage(html, ref) : showPage(html, ref);
    pages[ref] = { at: Date.now(), title };
    return title;
  }

  return {
    id: 'kurdcinama', name: 'Kurd Cinema', nameKu: 'کوردسینەما', home: SITE + '/', hosts: ['kurdcinama.com'],

    async rows(web) {
      const [f, s] = await Promise.all([web.text(SITE + '/movies.aspx'), web.text(SITE + '/drama.aspx')]);
      return [{ key: 'newFilms', items: films(f) }, { key: 'newSeries', items: shows(s) }];
    },

    async search(web, q) {
      const r = await web.json(SITE + '/Search.aspx?ajax=1&term=' + encodeURIComponent(q) + '&filter=all');
      return (Array.isArray(r) ? r : []).map((x) => {
        const ref = refOf(String(x.url || ''));
        return ref && TM.item({ site: 'kurdcinama', ref, kind: ref.charAt(0) === 'm' ? 'film' : 'series', title: plain(x.title),
          year: yearIn(x.title), poster: pic(x.photo), langs: ['sub'] });
      }).filter(Boolean);
    },

    async title(web, ref) {
      const t = Object.assign({}, await read(web, ref));
      delete t.episodesBySeason;
      return t;
    },

    async episodes(web, ref, season) {
      const s = ((await read(web, ref)).episodesBySeason || []).find((x) => x.n === Number(season));
      return s ? s.episodes : [];
    },

    /** A film: its player's page; an episode: its own page. */
    watch(ref, ep) {
      return ep && ep.url ? ep.url : ref.charAt(0) === 'm' ? SITE + '/online.aspx?movieid=' + ref.slice(2) : pageOf(ref);
    },
  };
})();
