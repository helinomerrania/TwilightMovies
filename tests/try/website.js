// Tries a website the way a TV box shows it (on GitHub, by .github/workflows/try.yml): opens it in Chromium at a
// TV's size, with an Android TV box's user agent, goes in (past a dialect page, if there is one), opens a film
// and presses play. It keeps a screenshot and the HTML of every step, what the page logged, the state of its
// videos, every address it loaded (requests.tsv, for Blocked.java) and its scripts (for syntax.js).
// Usage: node tests/try/website.js OUT_DIR [address] [user agent]
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const OUT = process.argv[2];
const START = process.argv[3] || 'https://app.kurdfilm.krd/tv';
const UA = process.argv[4] || 'Mozilla/5.0 (Linux; Android 9; AFTKA Build/PS7633; wv) AppleWebKit/537.36 '
  + '(KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.158 Mobile Safari/537.36';
const PLAY = /پەخش|سەیرکردن|سەیربکە|تەماشا|play|watch|▶/i;
const FILM = /\/(movie|movies|film|films|series|show|shows|watch|title|video|videos|episode)s?\/[^/?#]+/i;

fs.mkdirSync(path.join(OUT, 'js'), { recursive: true });
const log = [];
const note = (...a) => { const line = a.join(' '); log.push(line); console.log(line); };
const requests = [];
let jsCount = 0;

async function step(page, name) {
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, name + '.png') }).catch((e) => note('screenshot failed', e.message));
  fs.writeFileSync(path.join(OUT, name + '.html'), await page.content().catch(() => ''));
  note('STEP', name, page.url());
}

async function videos(page, label) {
  for (const f of page.frames()) {
    const v = await f.evaluate(() => Array.from(document.querySelectorAll('video')).map((x) => ({
      src: x.currentSrc || x.src, readyState: x.readyState, paused: x.paused, time: x.currentTime,
      duration: x.duration, error: x.error ? x.error.code + ' ' + (x.error.message || '') : null, network: x.networkState,
    }))).catch(() => []);
    for (const x of v) note('VIDEO', label, f.url(), JSON.stringify(x));
  }
  for (const f of page.frames()) if (f !== page.mainFrame()) note('FRAME', label, f.url());
}

/** The first visible element matching selector whose text (or label) matches re, or any if re is null. */
async function find(page, selector, re) {
  for (const f of page.frames()) {
    const all = f.locator(selector);
    const n = Math.min(await all.count().catch(() => 0), 400);
    for (let i = 0; i < n; i++) {
      const el = all.nth(i);
      if (!(await el.isVisible().catch(() => false))) continue;
      const text = ((await el.innerText().catch(() => '')) + ' ' + (await el.getAttribute('aria-label').catch(() => '') || '')
        + ' ' + (await el.getAttribute('class').catch(() => '') || '')).trim();
      if (!re || re.test(text)) return el;
    }
  }
  return null;
}

async function click(page, el, what) {
  if (!el) {
    note('NOT FOUND', what);
    return false;
  }
  note('CLICK', what, (await el.evaluate((e) => e.outerHTML.slice(0, 300)).catch(() => '')).replace(/\s+/g, ' '));
  await el.click({ timeout: 10000 }).catch((e) => note('click failed', e.message));
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  return true;
}

(async () => {
  const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, userAgent: UA });
  const page = await context.newPage();
  context.on('page', (p) => note('POPUP', p.url()));
  page.on('console', (m) => note('CONSOLE', m.type(), m.text().slice(0, 500), JSON.stringify(m.location())));
  page.on('pageerror', (e) => note('PAGE ERROR', e.message.slice(0, 500)));
  page.on('dialog', (d) => { note('DIALOG', d.type(), d.message()); d.dismiss().catch(() => {}); });
  page.on('framenavigated', (f) => { if (f === page.mainFrame()) note('NAVIGATED', f.url()); });
  page.on('requestfailed', (r) => note('FAILED', r.url().slice(0, 300), r.failure() && r.failure().errorText));
  page.on('request', (r) => {
    const frame = r.frame();
    requests.push([r.url(), r.resourceType(), frame ? frame.url() : '', r.isNavigationRequest() && frame === page.mainFrame() ? 'main' : ''].join('\t'));
  });
  page.on('response', async (r) => {
    if (r.status() >= 400) note('HTTP', r.status(), r.url().slice(0, 300));
    if (r.request().resourceType() !== 'script') return;
    const body = await r.text().catch(() => null);
    if (body === null) return;
    const name = String(++jsCount).padStart(3, '0') + '.js';
    fs.writeFileSync(path.join(OUT, 'js', name), body);
    fs.appendFileSync(path.join(OUT, 'js', 'index.tsv'), name + '\t' + r.url() + '\n');
  });

  await page.goto(START, { waitUntil: 'networkidle', timeout: 45000 }).catch((e) => note('goto', e.message));
  await step(page, '01-start');
  if (await click(page, await find(page, 'a[href*="dialect/sorani"], a[href*="sorani"]', null), 'the Sorani dialect')) {
    await step(page, '02-in');
  }
  const links = await page.$$eval('a[href]', (as) => as.map((a) => a.href + '  ' + a.innerText.replace(/\s+/g, ' ').slice(0, 60)));
  fs.writeFileSync(path.join(OUT, 'links.txt'), links.join('\n'));
  const film = links.map((l) => l.split('  ')[0]).find((h) => FILM.test(h) && h.includes(new URL(START).host));
  if (film) {
    note('FILM', film);
    await page.goto(film, { waitUntil: 'networkidle', timeout: 45000 }).catch((e) => note('goto', e.message));
  } else {
    await click(page, await find(page, 'a[href], [role="button"], [tabindex]', null), 'the first thing to open');
  }
  await step(page, '03-film');
  await click(page, await find(page, 'button, a, [role="button"], [class*="play" i], [id*="play" i]', PLAY), 'play');
  await page.waitForTimeout(20000);
  await step(page, '04-play');
  await videos(page, 'after 20 s');
  await page.waitForTimeout(20000);
  await step(page, '05-later');
  await videos(page, 'after 40 s');
  await browser.close();
})().catch((e) => note('STOPPED', e.stack)).finally(() => {
  fs.writeFileSync(path.join(OUT, 'requests.tsv'), requests.join('\n') + '\n');
  fs.writeFileSync(path.join(OUT, 'website.log'), log.join('\n') + '\n');
});
