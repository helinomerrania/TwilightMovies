// Mars Cinema (marscinema.net): films and series with Kurdish subtitles (some dubbed). Its lists come from its home
// page (its newest films and its newest series), a title in full from its page (/movie/<name>, /series/<name>: its
// Kurdish summary and genres, its length, age and IMDb's rating), a series' seasons and episodes from its player's
// page (/watch/<name>: the episodes' own list keeps them all, as the page's data), its search from its own feed
// (/api/search). It plays on its player's page (an episode: ?ep=<its number>), once the player's start is pressed
// (player.js).
'use strict';
TM.readers.marscinema = (function () {
  const SITE = 'https://marscinema.net';
  const one = (re, s) => { const m = re.exec(s); return m ? (m[1] || '').trim() : ''; };
  const all = (re, s) => { const out = []; let m; while ((m = re.exec(s))) out.push(m); return out; };
  const kindOf = (ref) => (/^series\//.test(ref) ? 'series' : 'film');
  const nameOf = (ref) => String(ref).replace(/^(movie|series)\//, '');
  const dubbed = (words) => (/دۆبلاژ/.test(words || '') ? ['dub'] : ['sub']);
  const pages = {};   // a title's pages, read once a while: a series' episodes are on its player's

  // One title of a list: a "poster-card-item" (its page, poster, name, and "year • genre" under it)
  const CARD = /<a href="\/(movie|series)\/([^"?#]+)"[^>]*\bposter-card-item\b[^>]*>([\s\S]*?)<\/a>/g;
  function card(m) {
    const meta = TM.text(one(/<span[^>]*\btruncate\b[^>]*>([\s\S]*?)<\/span>/, m[3])).split('•').map((x) => x.trim());
    return TM.item({
      site: 'marscinema', ref: m[1] + '/' + TM.decode(m[2]), kind: m[1] === 'series' ? 'series' : 'film',
      title: TM.decode(one(/<h3[^>]*\btitle="([^"]*)"/, m[3])), year: Number(meta[0]) || 0,
      genresKu: meta[1] ? [meta[1]] : [], poster: one(/<img[^>]*\bsrc="([^"]+)"/, m[3]), langs: ['sub'],
    });
  }
  // A row of its home page by its heading (the cards up to the next row's)
  function rail(html, name) {
    const at = html.indexOf('id="rail-' + name + '"');
    if (at < 0) return [];
    const end = html.indexOf('id="rail-', at + 1);
    return all(new RegExp(CARD.source, 'g'), html.slice(at, end < 0 ? html.length : end)).map(card);
  }

  // A title's page: its details by the top of its page (from its name to its Watch)
  function details(html, ref) {
    const top = one(/(<h1 class="text-2xl[\s\S]*?)<a href="\/watch\//, html);
    const spans = (cls) => all(new RegExp('<span class="[^"]*' + cls + '[^"]*">([^<]+)</span>', 'g'), top).map((m) => m[1].trim());
    const sized = one(/<span class="[^"]*font-mono text-text-secondary[^"]*">([^<]+)<\/span>/, top);
    return TM.item({
      site: 'marscinema', ref, kind: kindOf(ref), title: TM.decode(one(/<h1[^>]*>([\s\S]*?)<\/h1>/, top)),
      year: Number(one(/font-mono font-bold text-white">(\d{4})</, top)) || 0,
      minutes: Number(one(/^(\d+) خولەک$/, sized)) || 0, age: one(/text-text-muted font-bold">([^<]+)</, top),
      rating: Number(one(/IMDb ([\d.]+)/, top)) || 0, genresKu: spans('text-text-secondary text-\\[11px\\]'),
      tagline: TM.text(one(/\bitalic\b[^"]*">([\s\S]*?)<\/p>/, top)).replace(/^«|»$/g, ''),
      overviewKu: TM.text(one(/md:line-clamp-3[^"]*">([\s\S]*?)<\/p>/, top)),
      poster: one(/id="hero-floating-poster" src="([^"]+)"/, html),
      backdrop: one(/src="(https:\/\/media\.marscinema\.net\/backdrops\/[^"]+)"/, html),
      langs: dubbed(one(/property="og:title" content="([^"]*)"/, html)),
    });
  }

  // The page's own data for one of its parts (Astro's island props: each value [0, it] or [1, a list of them])
  function island(html, part) {
    const at = html.indexOf('component-url="/_astro/' + part + '.');
    const open = html.lastIndexOf('<astro-island', at);
    const props = at < 0 || open < 0 ? '' : one(/\bprops="([^"]*)"/, html.slice(open, html.indexOf('>', at)));
    const value = (v) => (v[0] === 1 ? v[1].map(value) : plain(v[1]));
    const plain = (x) => (x && typeof x === 'object' && !Array.isArray(x)
      ? Object.keys(x).reduce((o, k) => { o[k] = Array.isArray(x[k]) ? value(x[k]) : x[k]; return o; }, {}) : x);
    try {
      return props ? plain(JSON.parse(TM.decode(props))) : {};
    } catch (e) {
      return {};
    }
  }

  // A series' seasons on its player's page, each with its episodes (their own numbers, names and lengths)
  const seasons = (html) => (island(html, 'EpisodeAccordionList').seasons || []).map((s) => ({
    n: Number(s.number), episodes: (s.episodes || []).map((e) => TM.item({ n: Number(e.number), id: String(e.id),
      title: e.name || '', minutes: Number(e.runtime) || 0 })),
  })).filter((s) => s.n);

  async function read(web, ref) {
    const kept = pages[ref];
    if (kept && Date.now() - kept.at < 10 * 60 * 1000) return kept.title;
    const title = details(await web.text(SITE + '/' + ref), ref);
    if (title.kind === 'series') {
      const found = seasons(await web.text(SITE + '/watch/' + nameOf(ref)));
      title.seasons = found.map((s) => ({ n: s.n, count: s.episodes.length }));
      title.episodesBySeason = found;
    }
    pages[ref] = { at: Date.now(), title };
    return title;
  }

  return {
    id: 'marscinema', name: 'Mars Cinema', home: SITE + '/', hosts: ['marscinema.net'],

    async rows(web) {
      const home = await web.text(SITE + '/');
      return [{ key: 'newFilms', items: rail(home, 'نوێترین فیلمەکان') }, { key: 'newSeries', items: rail(home, 'نوێترین زنجیرەکان') }];
    },

    async search(web, q) {
      const r = (await web.json(SITE + '/api/search?q=' + encodeURIComponent(q))) || {};
      return [].concat(r.movies || [], r.series || []).filter((x) => x && x.slug).map((x) => TM.item({
        site: 'marscinema', ref: (x.kind === 'series' ? 'series/' : 'movie/') + x.slug, kind: x.kind === 'series' ? 'series' : 'film',
        title: TM.decode(x.title || x.originalTitle), year: Number(x.year) || 0, overviewKu: x.synopsis || '',
        poster: x.posterKey || '', langs: ['sub'],
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

    /** Its player's page: a film's, or a series' at the episode (by its own number). */
    watch(ref, ep) {
      return SITE + '/watch/' + encodeURIComponent(nameOf(ref)) + (ep && ep.id ? '?ep=' + ep.id : '');
    },
  };
})();
