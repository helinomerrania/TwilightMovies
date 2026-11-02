// Kurd Movie (kurd-movie.com): a WordPress website (the DooPlay theme) with Kurdish subtitles; its dubbed films say
// "doblaj" in their names. Its lists, a title's details and a series' episodes come from its pages (the theme's
// markup, the same on every page); its search from WordPress (/wp-json/dooplay/search/, with the number its pages
// carry). It plays on its own pages: a film's, or an episode's.
'use strict';
TM.readers['kurd-movie'] = (function () {
  const SITE = 'https://www.kurd-movie.com';
  const GENRES = /^(Action|Adventure|Animation|Comedy|Crime|Documentary|Drama|Family|Fantasy|History|Horror|Music|Mystery|Romance|Science Fiction|Thriller|War|Western|Sci-Fi & Fantasy|Action & Adventure)$/;
  const one = (re, s) => { const m = re.exec(s); return m ? m[1] : ''; };
  const all = (re, s) => { const out = []; let m; while ((m = re.exec(s))) out.push(m[1]); return out; };
  const dubbed = (t) => /\b(doblaj|dublaj|dubbed)\b|دۆبلاژ/i.test(t);
  const plain = (t) => TM.text(t).replace(/\s*\b(doblaj|dublaj)\b\s*/i, ' ').trim();
  const refOf = (url) => String(url).replace(/^https?:\/\/[^/]+\//, '').replace(/\/$/, '');   // "movies/onslaught"
  const kindOf = (url) => (/\/tvshows\//.test(url) ? 'series' : 'film');
  const genres = (s) => all(/rel="tag">([^<]+)</g, s).map(TM.decode).filter((g) => GENRES.test(g));
  const pages = {};   // a title's page, read once a while: its episodes are on it

  // One title of a list (an <article> of the theme)
  function article(a) {
    const url = one(/<h3><a href="([^"]+)"/, a);
    if (!url) return null;
    const name = one(/<h3><a href="[^"]+">([\s\S]*?)<\/a>/, a);
    const genreBox = one(/<div class="genres">([\s\S]*?)<\/div>\s*<\/div>/, a);
    return TM.item({
      site: 'kurd-movie', ref: refOf(url), kind: kindOf(url), title: plain(name),
      year: Number(one(/<div class="metadata">\s*<span>(\d{4})<\/span>/, a) || one(/(\d{4})<\/span>\s*<\/div>/, a)) || 0,
      minutes: Number(one(/<span>(\d+) min<\/span>/, a)) || 0, overview: TM.text(one(/<div class="texto">([\s\S]*?)<\/div>/, a)),
      genres: genres(genreBox), poster: TM.size(one(/<img[^>]+src="([^"]+)"/, a), 'w342'),
      quality: TM.text(one(/<span class="dt-resolution-badge">([^<]*)</, a)), langs: [dubbed(name) ? 'dub' : 'sub'],
    });
  }
  const list = (html) => (html.match(/<article id="post-\d+" class="item [^"]*">[\s\S]*?<\/article>/g) || []).map(article).filter(Boolean);

  // A title's own page: its details, and a series' seasons with their episodes
  function page(html, ref) {
    const head = one(/<div class="sheader">([\s\S]*?)<div class="single_tabs">/, html) || html;
    const name = one(/<h1>([\s\S]*?)<\/h1>/, head);
    const backdrop = one(/<div class="g-item"><a href="([^"]+)"/, html);
    const seasons = [];
    const box = /<span class="se-t[^"]*">(\d+)<\/span>[\s\S]*?<ul class="episodios">([\s\S]*?)<\/ul>/g;
    let m;
    while ((m = box.exec(html))) {
      const eps = [];
      const ep = /<div class="numerando">\s*(\d+)\s*-\s*(\d+)\s*<\/div>\s*<div class="episodiotitle">\s*<a href="([^"]+)">([\s\S]*?)<\/a>/g;
      let e;
      while ((e = ep.exec(m[2]))) eps.push(TM.item({ n: Number(e[2]), title: TM.text(e[4]), url: e[3] }));
      seasons.push({ n: Number(m[1]), episodes: eps });
    }
    return TM.item({
      site: 'kurd-movie', ref, kind: kindOf(SITE + '/' + ref + '/'), title: plain(name), tagline: TM.text(one(/<span class="tagline">([\s\S]*?)<\/span>/, head)),
      year: Number(one(/<span class="date"[^>]*>[^<]*?(\d{4})<\/span>/, head)) || 0,
      minutes: Number(one(/<span itemprop="duration" class="runtime">(\d+)/, head)) || 0,
      age: TM.text(one(/<span itemprop="contentRating"[^>]*>([^<]*)</, head)), genres: genres(one(/<div class="sgeneros">([\s\S]*?)<\/div>/, head)),
      overview: TM.text(one(/<div itemprop="description" class="wp-content">\s*<p>([\s\S]*?)<\/p>/, html)),
      rating: Number(one(/<b id="repimdb"><strong>([\d.]+)<\/strong>/, html)) || 0, poster: TM.size(one(/<img itemprop="image" src="([^"]+)"/, head), 'w342'),
      backdrop: backdrop ? TM.size(backdrop, 'w1280') : '', langs: [dubbed(name) ? 'dub' : 'sub'],
      seasons: seasons.map((s) => ({ n: s.n, count: s.episodes.length })), episodesBySeason: seasons,
    });
  }

  async function read(web, ref) {
    const kept = pages[ref];
    if (kept && Date.now() - kept.at < 10 * 60 * 1000) return kept.title;
    const title = page(await web.text(SITE + '/' + ref + '/'), ref);
    pages[ref] = { at: Date.now(), title };
    return title;
  }

  let nonce = { at: 0, value: '' };
  async function searchNumber(web) {
    if (nonce.value && Date.now() - nonce.at < 30 * 60 * 1000) return nonce.value;
    nonce = { at: Date.now(), value: one(/"nonce"\s*:\s*"(\w+)"/, await web.text(SITE + '/')) };
    return nonce.value;
  }

  return {
    id: 'kurd-movie', name: 'Kurd Movie', nameKu: 'کوردموڤی', home: SITE + '/', hosts: ['www.kurd-movie.com', 'kurd-movie.com'],

    async rows(web) {
      const [films, shows] = await Promise.all([web.text(SITE + '/movies/'), web.text(SITE + '/tvshows/')]);
      return [{ key: 'newFilms', items: list(films) }, { key: 'newSeries', items: list(shows) }];
    },

    async search(web, q) {
      const n = await searchNumber(web);
      const r = await web.json(SITE + '/wp-json/dooplay/search/?keyword=' + encodeURIComponent(q) + '&nonce=' + n);
      return Object.keys(r && typeof r === 'object' ? r : {}).map((id) => r[id]).filter((x) => x && x.url).map((x) => TM.item({
        site: 'kurd-movie', ref: refOf(x.url), kind: kindOf(x.url), title: plain(x.title),
        year: Number(String((x.extra && x.extra.date) || '').slice(-4)) || 0, rating: Number(x.extra && x.extra.imdb) || 0,
        poster: TM.size(x.img, 'w342'), langs: [dubbed(x.title) ? 'dub' : 'sub'],
      }));
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

    /** Its page that plays the film, or the episode's (an episode keeps its page's address). */
    watch(ref, ep) {
      return ep && ep.url ? ep.url : SITE + '/' + ref + '/';
    },
  };
})();
