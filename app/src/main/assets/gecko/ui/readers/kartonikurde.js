// Kartoni Kurdi (kartonikurde.com): cartoons, dubbed into Kurdish (some with Kurdish subtitles), by their Kurdish
// names. Its lists (each title a "movie-card" of its own), a title in full (its English name too) and a series'
// episodes come from its pages; its search from the page's own search box (Livewire: the box's state from a page,
// then what it finds for the words). It plays on the title's own page (a series: its episodes chosen there).
'use strict';
TM.readers.kartonikurde = (function () {
  const SITE = 'https://www.kartonikurde.com';
  const one = (re, s) => { const m = re.exec(s); return m ? (m[1] || '').trim() : ''; };
  const all = (re, s) => { const out = []; let m; while ((m = re.exec(s))) out.push(m[1].trim()); return out; };
  const GENRES = { animation: 'Animation', family: 'Family', advanture: 'Adventure', adventure: 'Adventure', action: 'Action',
    comedy: 'Comedy', fantasy: 'Fantasy', drama: 'Drama', music: 'Music', mystery: 'Mystery', 'science fiction': 'Science Fiction' };
  const LINK = /href="https:\/\/www\.kartonikurde\.com\/((movie|serie)\/[^"]+)"/;
  const P = '<p(?:\\s[^>]*)?>';                                   // a <p>, not an <path>
  const bare = (html) => String(html).replace(/<!--[\s\S]*?-->/g, '');   // without its notes (and Livewire's)
  const pages = {};   // a title's page, read once a while: a series' episodes are on it

  // Its name in English, from its address where the page doesn't say ("home_2015": Home)
  const english = (ref) => ref.replace(/^\w+\//, '').replace(/_\d{4}$/, '').replace(/[-_]+/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
  const kindOf = (ref) => (/^serie\//.test(ref) ? 'series' : 'film');

  // One title of a list: a "movie-card" (subtitled: from the lists of its subtitled ones)
  function card(html, langs) {
    const c = bare(html);
    const m = new RegExp(LINK.source + '[^>]*\\baria-label="([^"]*)"').exec(c);
    if (!m) return null;
    const ref = m[1];
    const badges = all(/<span[^>]*>\s*([^<]*?)\s*<\/span>/g, one(/<\/p>\s*<div[^>]*>[\s\S]*?<\/div>\s*<div[^>]*>([\s\S]*?)<\/div>/, c));
    return TM.item({
      site: 'kartonikurde', ref, kind: kindOf(ref), title: english(ref), titleKu: TM.decode(m[3]),
      year: Number(badges.find((b) => /^\d{4}$/.test(b))) || Number(one(/_(\d{4})$/, ref)) || 0,
      genresKu: badges.filter((b) => b && !/^[\d,.]+$/.test(b)), rating: Number(one(/<span>(\d+(?:\.\d+)?)<\/span>/, c)) || 0,
      overviewKu: TM.text(one(new RegExp(P + '([\\s\\S]*?)</p>'), c)), poster: one(/<img[^>]*\bsrc="([^"]+)"/, c), langs,
    });
  }
  const list = (html, langs) => String(html).split('<div wire:snapshot=').filter((c) => /&quot;name&quot;:&quot;movie-card&quot;/.test(c))
    .map((c) => card(c, langs)).filter(Boolean);

  // A title's page: its names, age, year, length, rating, summary and genres; a series' episodes (one season)
  function page(raw, ref) {
    const html = bare(raw);
    const h1 = one(/<h1[^>]*>([\s\S]*?)<\/h1>/, html);
    const facts = all(/<span[^>]*>\s*([^<]*?)\s*<\/span>/g, one(/<\/h1>\s*<\/div>\s*<div[^>]*>([\s\S]*?)<div wire:snapshot/, html));
    const genres = Array.from(html.matchAll(/href="https:\/\/www\.kartonikurde\.com\/genre\/([^"]+)"[^>]*>\s*([^<]+?)\s*</g));
    const eps = Array.from(html.matchAll(/wire:click="selectEpisode\(\{&quot;id&quot;:(\d+),&quot;episode_number&quot;:(\d+)/g))
      .map((e) => ({ n: Number(e[2]), id: e[1] }));
    return TM.item({
      site: 'kartonikurde', ref, kind: kindOf(ref), title: TM.text(one(/<span[^>]*>\(([^)<]+)\)<\/span>/, h1)) || english(ref),
      titleKu: TM.text(h1.replace(/<span[\s\S]*$/, '')), year: Number(facts.find((f) => /^\d{4}$/.test(f))) || 0,
      age: one(/(\d+)/, facts.find((f) => /سەروو/.test(f)) || ''), minutes: parseInt(facts.find((f) => /خولەک/.test(f)), 10) || 0,
      rating: Number(one(/<span>IMDb<\/span>[\s\S]*?<span>([\d.]+)<\/span>/, html)) || 0,
      overviewKu: TM.text(one(new RegExp('</h1>[\\s\\S]*?' + P + '([\\s\\S]*?)</p>'), html)),
      genres: genres.map((g) => GENRES[decodeURIComponent(g[1]).toLowerCase()]).filter(Boolean), genresKu: genres.map((g) => g[2]),
      poster: one(/<img src="(https:\/\/www\.kartonikurde\.com\/storage\/images\/[^"]+)"/, html),
      seasons: eps.length ? [{ n: 1, count: eps.length }] : [], episodes: eps,
    });
  }

  async function read(web, ref) {
    const kept = pages[ref];
    if (kept && Date.now() - kept.at < 10 * 60 * 1000) return kept.title;
    const title = page(await web.text(SITE + '/' + ref), ref);
    pages[ref] = { at: Date.now(), title };
    return title;
  }

  // Search: the search box's own state (from a page: its snapshot and the page's token), then its words, as typed
  async function find(web, q) {
    const html = await web.text(SITE + '/series');
    const snapshot = TM.decode(one(/wire:snapshot="([^"]*&quot;name&quot;:&quot;search-form&quot;[^"]*)"/, html));
    const token = one(/name="_token" value="([^"]+)"/, html) || one(/name="csrf-token" content="([^"]+)"/, html);
    if (!snapshot || !token) return '';
    const r = await web.json(SITE + '/livewire/update', { method: 'POST', headers: { 'Content-Type': 'application/json',
      Accept: 'application/json', 'X-Livewire': '' }, body: JSON.stringify({ _token: token,
      components: [{ snapshot, updates: { searchText: q }, calls: [] }] }) });
    return ((((r && r.components) || [])[0] || {}).effects || {}).html || '';
  }

  return {
    id: 'kartonikurde', name: 'Kartoni Kurdi', nameKu: 'کارتۆنی کوردی', home: SITE + '/', hosts: ['www.kartonikurde.com', 'kartonikurde.com'],

    async rows(web) {
      const [films, shows] = await Promise.all([web.text(SITE + '/movies'), web.text(SITE + '/series')]);
      return [{ key: 'newFilms', items: list(films, ['dub']) }, { key: 'newSeries', items: list(shows, ['dub']) }];
    },

    async search(web, q) {
      const html = await find(web, q);
      const seen = {};
      return Array.from(html.matchAll(new RegExp(LINK.source + '[\\s\\S]*?<img[^>]*\\bsrc="([^"]+)"[^>]*\\balt="([^"]*)"', 'g')))
        .filter((m) => !seen[m[1]] && (seen[m[1]] = true)).map((m) => TM.item({
          site: 'kartonikurde', ref: m[1], kind: kindOf(m[1]), title: english(m[1]), titleKu: TM.decode(m[4]), poster: m[3], langs: ['dub'],
        }));
    },

    async title(web, ref) {
      const t = Object.assign({}, await read(web, ref));
      delete t.episodes;
      return t;
    },

    async episodes(web, ref) {
      return (await read(web, ref)).episodes || [];
    },

    /** The title's own page (a series: its episodes are chosen there, the first at first). */
    watch(ref) {
      return SITE + '/' + ref;
    },
  };
})();
