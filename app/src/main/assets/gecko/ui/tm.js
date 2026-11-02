// The Twilight screens, in Firefox's engine: a page of the app's extension (ui/index.html) that shows the films and
// series of the websites the app reads (ui/readers/), as one catalogue in one plain look (ARCHITECTURE.md: The
// Twilight screens). This file: the namespace every part adds to, and the small things they all use.
'use strict';
var TM = { readers: {}, views: {} };

/** What the box keeps for the screens (settings, what was watched, what the websites last had), as JSON. */
TM.store = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem('tm.' + key);
      return v === null ? fallback : JSON.parse(v);
    } catch (e) {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem('tm.' + key, JSON.stringify(value));
    } catch (e) { /* full, or not allowed here: kept for this time only */ }
  },
};

/** Words the websites write with HTML's codes for letters (&amp;, &#8217;...), as plain text. */
TM.decode = function (s) {
  return String(s || '').replace(/&#(\d+);/g, (m, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([\da-f]+);/gi, (m, n) => String.fromCharCode(parseInt(n, 16)))
    .replace(/&quot;/g, '"').replace(/&#039;|&apos;/g, '\'').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&');
};

/** An HTML piece's text: no tags, letters decoded, spaces as one. */
TM.text = (html) => TM.decode(String(html || '').replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim();

/** A TMDB picture at another size ("w342": a poster for the rows), or the address as it is. */
TM.size = (url, size) => String(url || '').replace(/\/t\/p\/(w\d+|original)\//, '/t/p/' + size + '/');

/**
 * What every reader gives for a film or a series (the fields a website doesn't have are left out):
 * site, ref (its own name for it, for title() and watch()), kind ('film' | 'series'), title (as the website writes
 * it, mostly English), titleKu, year, rating, minutes, genres (English), genresKu, overview, overviewKu, tagline,
 * poster, backdrop, langs (['sub'] Kurdish subtitles, ['dub'] Kurdish dubbing), quality, age, tmdb,
 * seasons ([{n, count}]), and an episode: {n, title, minutes, overview, overviewKu, url}.
 */
TM.item = function (fields) {
  const out = {};
  Object.keys(fields).forEach((k) => {
    const v = fields[k];
    if (v !== undefined && v !== null && v !== '' && v !== 0 && !(Array.isArray(v) && !v.length)) out[k] = v;
  });
  return out;
};
