// One-off (this branch only, then it goes): TMDB's logos page and the logos on it, for the app's About.
'use strict';
const fs = require('fs');
const path = require('path');
const OUT = process.argv[2];
const UA = 'Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0';
const log = [];
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const res = await fetch('https://www.themoviedb.org/about/logos-attribution', { headers: { 'User-Agent': UA } });
  const page = await res.text();
  log.push('page ' + res.status + ' ' + page.length);
  fs.writeFileSync(path.join(OUT, 'logos-page.html'), page);
  const found = [...page.matchAll(/(?:https:\/\/www\.themoviedb\.org)?\/assets\/[^"'\s)]+?\.(?:svg|png)/g)].map((m) => m[0]);
  const urls = [...new Set(found.map((u) => (u.startsWith('http') ? u : 'https://www.themoviedb.org' + u)))]
    .concat(['https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg']);
  for (const u of [...new Set(urls)].slice(0, 40)) {
    try {
      const r = await fetch(u, { headers: { 'User-Agent': UA } });
      const body = Buffer.from(await r.arrayBuffer());
      log.push(r.status + ' ' + body.length + ' ' + u);
      if (r.ok) fs.writeFileSync(path.join(OUT, 'logo-' + path.basename(u).replace(/-[0-9a-f]{20,}/, '') + '.txt'), body);
    } catch (e) {
      log.push('FAILED ' + u + ' ' + e.message);
    }
  }
})().catch((e) => log.push('STOPPED ' + e.stack)).finally(() => fs.writeFileSync(path.join(OUT, 'readers.log'), log.join('\n') + '\n'));
