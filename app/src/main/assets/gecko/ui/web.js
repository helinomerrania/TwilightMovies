// How the readers ask the websites (ui/readers/): straight from this page of the app's extension, which may ask any
// website (its manifest's <all_urls>), with the website's own cookies, as its own pages would; never longer than a
// time limit. A reader gets this as its "web", so the tests can give it the websites' saved answers instead.
'use strict';
TM.web = {
  /** The answer's text (or JSON, with o.json), or an error: not reached, refused, or too slow (o.timeout ms). */
  async get(url, o) {
    o = o || {};
    const stop = new AbortController();
    const timer = setTimeout(() => stop.abort(), o.timeout || 15000);
    try {
      const res = await fetch(url, { method: o.method || 'GET', headers: o.headers, body: o.body, credentials: 'include',
        signal: stop.signal, cache: 'no-cache' });
      if (!res.ok) throw new Error(url + ' answered ' + res.status);
      return o.json ? await res.json() : await res.text();
    } finally {
      clearTimeout(timer);
    }
  },
  json(url, o) {
    return TM.web.get(url, Object.assign({ json: true, headers: { Accept: 'application/json, text/plain, */*' } }, o));
  },
  text(url, o) {
    return TM.web.get(url, o);
  },
};
