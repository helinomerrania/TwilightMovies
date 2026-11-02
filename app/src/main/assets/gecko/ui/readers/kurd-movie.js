// Kurd Movie (kurd-movie.com): a WordPress website (the DooPlay theme) with Kurdish subtitles; its dubbed films say
// "doblaj" in their names. Its lists, a title's details and a series' episodes come from its pages (the theme's
// markup, the same on every page, read whichever quotes and order its attributes come in); its search from
// WordPress (/wp-json/dooplay/search/, with the number its pages carry). It plays on its own pages: a film's, or an
// episode's.
'use strict';
TM.readers['kurd-movie'] = (function () {
  const SITE = 'https://www.kurd-movie.com';
  const GENRES = /^(Action|Adventure|Animation|Comedy|Crime|Documentary|Drama|Family|Fantasy|History|Horror|Music|Mystery|Romance|Science Fiction|Thriller|War|Western|Sci-Fi & Fantasy|Action & Adventure)$/;
  const quotes = (h) => String(h || '').replace(/=\s*'([^'<>]*)'/g, '="$1"');   // every attribute in double quotes
  const one = (re, s) => { const m = re.exec(s); return m ? m[1] : ''; };
  const all = (re, s) => { const out = []; let m; while ((m = re.exec(s))) out.push(m[1]); return out; };
  const has = (cls) => '[^>]*\\bclass="[^"]*\\b' + cls + '\\b[^"]*"[^>]*';   // a tag with that class, attributes in any order
  const box = (tag, cls) => new RegExp('<' + tag + has(cls) + '>([\\s\\S]*?)</' + tag + '>');
  const dubbed = (t) => /\b(doblaj|dublaj|dubbed)\b|دۆبلاژ/i.test(t);
  const plain = (t) => TM.text(t).replace(/\s*\b(doblaj|dublaj)\b\s*/i, ' ').trim();
  const refOf = (url) => String(url).replace(/^https?:\/\/[^/]+\//, '').replace(/\/$/, '');   // "movies/onslaught"
  const kindOf = (url) => (/\/tvshows\//.test(url) ? 'series' : 'film');
  const genres = (s) => all(/rel="tag"[^>]*>([^<]+)</g, s).map(TM.decode).filter((g) => GENRES.test(g));
  const pages = {};   // a title's page, read once a while: its episodes are on it

  // One title of a list (an <article> of the theme)
  function article(a) {
    const url = one(/<h3>\s*<a[^>]*\bhref="([^"]+)"/, a);
    if (!url) return null;
    const name = one(/<h3>\s*<a[^>]*>([\s\S]*?)<\/a>/, a);
    return TM.item({
      site: 'kurd-movie', ref: refOf(url), kind: kindOf(url), title: plain(name),
      year: Number(one(new RegExp('<div' + has('metadata') + '>\\s*<span>(\\d{4})</span>'), a)) || 0,
      minutes: Number(one(/<span>(\d+) min<\/span>/, a)) || 0, overview: TM.text(one(box('div', 'texto'), a)),
      genres: genres(one(new RegExp('<div' + has('genres') + '>([\\s\\S]*?)</div>\\s*</div>'), a)),
      poster: TM.size(one(/<img[^>]*\b(?:data-src|src)="([^"]+)"/, a), 'w342'),
      quality: TM.text(one(new RegExp('<span' + has('dt-resolution-badge') + '>([^<]*)<'), a)), langs: [dubbed(name) ? 'dub' : 'sub'],
    });
  }
  const list = (html) => (quotes(html).match(/<article[^>]*\bid="post-\d+"[^>]*>[\s\S]*?<\/article>/g) || []).map(article).filter(Boolean);

  // A series' seasons, each with its episodes and their pages
  function seasons(html) {
    const out = [];
    const each = new RegExp('<span' + has('se-t') + '>\\s*(\\d+)\\s*</span>[\\s\\S]*?<ul' + has('episodios') + '>([\\s\\S]*?)</ul>', 'g');
    const ep = new RegExp('<div' + has('numerando') + '>\\s*(\\d+)\\s*-\\s*(\\d+)\\s*</div>\\s*<div' + has('episodiotitle')
      + '>\\s*<a[^>]*\\bhref="([^"]+)"[^>]*>([\\s\\S]*?)</a>', 'g');
    let m;
    while ((m = each.exec(html))) {
      const eps = [];
      let e;
      ep.lastIndex = 0;
      while ((e = ep.exec(m[2]))) eps.push(TM.item({ n: Number(e[2]), title: TM.text(e[4]), url: e[3] }));
      out.push({ n: Number(m[1]), episodes: eps });
    }
    return out;
  }

  // A title's own page: its details, and a series' seasons with their episodes
  function page(raw, ref) {
    const html = quotes(raw);
    const head = one(new RegExp('<div' + has('sheader') + '>([\\s\\S]*?)<div' + has('single_tabs') + '>'), html) || html;
    const name = one(/<h1[^>]*>([\s\S]*?)<\/h1>/, head);
    const poster = one(/<img([^>]*\bitemprop="image"[^>]*)>/, head);
    const backdrop = one(new RegExp('<div' + has('g-item') + '>\\s*<a[^>]*\\bhref="([^"]+)"'), html);
    const found = seasons(html);
    return TM.item({
      site: 'kurd-movie', ref, kind: kindOf(SITE + '/' + ref + '/'), title: plain(name),
      tagline: TM.text(one(box('span', 'tagline'), head)),
      year: Number(one(new RegExp('<span' + has('date') + '>[^<]*?(\\d{4})\\s*<'), head)) || 0,
      minutes: Number(one(new RegExp('<span' + has('runtime') + '>\\s*(\\d+)'), head)) || 0,
      age: TM.text(one(/<span[^>]*\bitemprop="contentRating"[^>]*>([^<]*)</, head)),
      genres: genres(one(box('div', 'sgeneros'), head)),
      overview: TM.text(one(/<div[^>]*\bitemprop="description"[^>]*>\s*<p>([\s\S]*?)<\/p>/, html).replace(/<iframe[\s\S]*$/, '')),
      rating: Number(one(/\bid="repimdb"[^>]*>\s*<strong>([\d.]+)<\/strong>/, html)) || 0,
      poster: TM.size(one(/\b(?:data-src|src)="([^"]+)"/, poster), 'w342'), backdrop: backdrop ? TM.size(backdrop, 'w1280') : '',
      langs: [dubbed(name) ? 'dub' : 'sub'], seasons: found.map((s) => ({ n: s.n, count: s.episodes.length })), episodesBySeason: found,
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
