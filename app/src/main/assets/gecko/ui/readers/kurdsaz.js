// Kurdsaz (kurdsaz.com): films and series with Kurdish subtitles, Korean ones above all. Its lists and a title in full
// come from its pages (browse.php, details.php: a title's Kurdish summary and genres, its rating, year, age and
// length; a series' seasons and episodes from its player's page, watch.php), its search from its own feed
// (ajax_search.php). It plays on its own page (watch.php; an episode: &ep=<its number>).
'use strict';
TM.readers.kurdsaz = (function () {
  const SITE = 'https://kurdsaz.com';
  const one = (re, s) => { const m = re.exec(s); return m ? (m[1] || '').trim() : ''; };
  const all = (re, s) => { const out = []; let m; while ((m = re.exec(s))) out.push(m[1].trim()); return out; };
  const pic = (p) => (!p ? '' : /^https?:/.test(p) ? p : SITE + '/' + p.replace(/^\//, ''));
  const series = (word) => /زنجیرە|series/i.test(word || '');
  const pages = {};   // a title's pages, read once a while: a series' episodes are on its player's

  // One title of a list (a "content-card")
  function card(a) {
    const ref = TM.decode(one(/href="details\.php\?slug=([^"]+)"/, a));
    if (!ref) return null;
    const meta = all(/<span[^>]*>([^<]*)<\/span>/g, one(/class="card-meta"[^>]*>([\s\S]*?)<\/div>/, a));
    return TM.item({
      site: 'kurdsaz', ref, kind: meta.some(series) ? 'series' : 'film',
      title: TM.text(one(/class="content-title"[^>]*>([\s\S]*?)<\/h3>/, a)),
      year: Number(meta.find((m) => /^\d{4}$/.test(m))) || 0,
      rating: Number(one(/class="badge"[^>]*>\s*(?:<svg[\s\S]*?<\/svg>)?\s*([\d.]+)/, a)) || 0,
      poster: pic(one(/<img[^>]*\bsrc="([^"]+)"/, a)), langs: ['sub'],
    });
  }
  const list = (html) => (String(html).match(/<a class="content-card" href="details\.php\?slug=[^"]*">[\s\S]*?<\/a>/g) || [])
    .map(card).filter(Boolean);

  // A title's page: its details (each in a label of its own: badge-type, badge-rate...)
  function details(html, ref) {
    const label = (cls) => TM.text(one(new RegExp('\\b' + cls + '\\b[^"]*"[^>]*>[\\s\\S]*?<span[^>]*>([^<]*)</span>'), html));
    const poster = pic(one(/locked-poster-box[^>]*>\s*<img[^>]*\bsrc="([^"]+)"/, html));
    const wide = one(/property="og:image" content="([^"]+)"/, html);
    return TM.item({
      site: 'kurdsaz', ref, kind: series(label('badge-type')) ? 'series' : 'film',
      title: TM.text(one(/hero-main-title[^>]*>\s*<span[^>]*>([\s\S]*?)<\/span>/, html)),
      year: Number(label('badge-year')) || 0, rating: Number(label('badge-rate')) || 0,
      minutes: parseInt(label('badge-time'), 10) || 0, age: label('badge-age').replace(/^\+/, ''),
      genresKu: all(/href="browse\.php\?genre=[^"]*"[^>]*>([^<]+)</g, html),
      overviewKu: TM.text(one(/hero-text-side[\s\S]*?<p(?:\s[^>]*)?>([\s\S]*?)<\/p>/, html)),   // a <p>, not an <path>
      poster, backdrop: wide && wide !== poster ? wide : '', langs: ['sub'],
    });
  }

  // A series' seasons on its player's page, each with its episodes (numbered as they come; each its own number)
  function seasons(html) {
    const out = [];
    const re = /<div id="season-(\d+)" class="season-list"[^>]*>([\s\S]*?)<\/div>/g;
    let m;
    while ((m = re.exec(html))) {
      out.push({ n: Number(m[1]), episodes: all(/changeEpisode\('(\d+)'/g, m[2]).map((id, i) => ({ n: i + 1, id })) });
    }
    return out;
  }

  const address = (page, ref) => SITE + '/' + page + '.php?slug=' + encodeURIComponent(ref);

  async function read(web, ref) {
    const kept = pages[ref];
    if (kept && Date.now() - kept.at < 10 * 60 * 1000) return kept.title;
    const title = details(await web.text(address('details', ref)), ref);
    if (title.kind === 'series') {
      const found = seasons(await web.text(address('watch', ref)));
      title.seasons = found.map((s) => ({ n: s.n, count: s.episodes.length }));
      title.episodesBySeason = found;
    }
    pages[ref] = { at: Date.now(), title };
    return title;
  }

  return {
    id: 'kurdsaz', name: 'Kurdsaz', nameKu: 'کوردساز', home: SITE + '/', hosts: ['kurdsaz.com'],

    async rows(web) {
      const [films, shows] = await Promise.all([web.text(SITE + '/browse.php?type=movie'), web.text(SITE + '/browse.php?type=series')]);
      return [{ key: 'newFilms', items: list(films) }, { key: 'newSeries', items: list(shows) }];
    },

    async search(web, q) {
      const r = await web.json(SITE + '/ajax_search.php?q=' + encodeURIComponent(q));
      return ((r && r.results) || []).filter((x) => x && x.slug).map((x) => TM.item({
        site: 'kurdsaz', ref: x.slug, kind: series(x.type || x.kind) ? 'series' : 'film', title: TM.decode(x.title),
        year: Number(x.year) || 0, rating: Number(x.rating) || 0, poster: pic(x.poster), langs: ['sub'],
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
      return address('watch', ref) + (ep && ep.id ? '&ep=' + ep.id : '');
    },
  };
})();
