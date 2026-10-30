// The app's extension in Firefox's engine (app/src/main/assets/gecko): its background script and its page script,
// with a stand-in for the engine's browser API that answers at once (so the checks run in order); and that it
// and web/gecko/Extension.java call the app by the same name, from the same folder.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');

// Promises that keep their word at once (so the checks run in order): what the stand-in engine gives, and the
// Promise the scripts make themselves.
function SyncPromise(executor) {
  let done = false;
  let value;
  let broken = false;
  const waiting = [];
  const settle = (v, b) => {
    if (done) return;
    if (!b && v && typeof v.then === 'function') return v.then((x) => settle(x, false), (e) => settle(e, true));
    done = true;
    value = v;
    broken = b;
    waiting.splice(0).forEach((f) => f());
  };
  this.then = (yes, no) => new SyncPromise((res, rej) => {
    const run = () => {
      const f = broken ? no : yes;
      if (!f) return broken ? rej(value) : res(value);
      try { res(f(value)); } catch (e) { rej(e); }
    };
    if (done) run(); else waiting.push(run);
  });
  try { executor((v) => settle(v, false), (e) => settle(e, true)); } catch (e) { settle(e, true); }
}
SyncPromise.resolve = (v) => new SyncPromise((r) => r(v));
const now = (value, broken) => new SyncPromise((res, rej) => (broken ? rej(value) : res(value)));
const NEVER = {};   // an app that never answers
const valueOf = (p) => { let v; p.then((x) => { v = x; }); return v; };

function background(app) {
  const asked = [];
  const posted = [];
  const ran = [];
  const b = { onRequest: null, filter: null, extra: null, fromPort: null, fromPage: null, nativeApp: null };
  const port = {
    onMessage: { addListener: (f) => { b.fromPort = f; } },
    onDisconnect: { addListener: () => {} },
    postMessage: (m) => posted.push(m),
  };
  const browser = {
    runtime: {
      sendNativeMessage: (name, m) => {
        asked.push([name, m]);
        const a = app(m);
        if (a === NEVER) return new SyncPromise(() => {});
        return a instanceof Error ? now(a, true) : now(JSON.stringify(a));
      },
      connectNative: (name) => { b.nativeApp = name; return port; },
      onMessage: { addListener: (f) => { b.fromPage = f; } },
    },
    webRequest: { onBeforeRequest: { addListener: (f, filter, extra) => { b.onRequest = f; b.filter = filter; b.extra = extra; } } },
    tabs: { executeScript: (tab, d) => { ran.push([tab, d]); return b.script(d.code); } },
  };
  b.script = () => now(['ok']);
  const timers = [];
  b.timers = timers;
  vm.runInContext(read('background.js'), vm.createContext({ browser, JSON, Promise: SyncPromise,
    setTimeout: (f) => { timers.push(f); return timers.length; }, clearTimeout: (n) => { timers[n - 1] = null; } }));
  return Object.assign(b, { asked, posted, ran });
}

const manifest = JSON.parse(read('manifest.json'));
check('the manifest: version 2, so requests can be held while the app answers', manifest.manifest_version, 2);
ok('...it may hold and change every request, and talk to the app',
  ['<all_urls>', 'webRequest', 'webRequestBlocking', 'nativeMessaging', 'geckoViewAddons'].every((p) => manifest.permissions.includes(p)));
check('...its background script', manifest.background.scripts, ['background.js']);
const cs = manifest.content_scripts[0];
check('...its page script: in the page itself, from the very start', [cs.js, cs.run_at, cs.all_frames], [['page.js'], 'document_start', false]);
ok('...and an id of its own', /^[\w.-]+@[\w.-]+$/.test(manifest.browser_specific_settings.gecko.id));

const java = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'twilightcafe', 'movies', 'web', 'gecko',
  'Extension.java'), 'utf8');
const javaName = (java.match(/String APP = "(\w+)"/) || [])[1];
check('the app is called by the same name on both sides', (read('background.js').match(/var APP = '(\w+)'/) || [])[1], javaName);
check('the app installs it from this folder', (java.match(/"resource:\/\/android\/assets\/([\w-]+)\/"/) || [])[1], path.basename(DIR));

let x = background((m) => (m.check && m.check.includes('ads.example') ? { cancel: true } : {}));
check('it connects to the app at once', x.nativeApp, javaName);
ok('the app answers with JSON text, as the engine needs (a JSONObject never reaches the extension)',
  /answer\.complete\(a\.toString\(\)\)/.test(java));
check('every address of every page is held until the app answers', [x.filter.urls, x.extra], [['<all_urls>'], ['blocking']]);
check('the page itself isn\'t asked about here (the app does as it opens)',
  [x.onRequest({ type: 'main_frame', url: 'https://ads.example/', tabId: 1, frameId: 0 }), x.asked.length], [{}, 0]);
check('nor the engine\'s own requests (no tab)', [x.onRequest({ type: 'script', url: 'https://ads.example/a.js', tabId: -1 }), x.asked.length], [{}, 0]);
let r = x.onRequest({ type: 'script', url: 'https://ads.example/a.js', tabId: 3, frameId: 0, documentUrl: 'https://films.example/watch' });
check('a script of the page: the app is asked, with the page it\'s for', x.asked[0],
  ['twilight', { check: 'https://ads.example/a.js', type: 'script', frame: 'https://films.example/watch', page: 'https://films.example/watch' }]);
check('...and its answer is the engine\'s', valueOf(r), { cancel: true });
r = x.onRequest({ type: 'image', url: 'https://cdn.example/p.jpg', tabId: 3, frameId: 9, documentUrl: 'https://player.example/embed',
  frameAncestors: [{ url: 'https://player.example/', frameId: 4 }, { url: 'https://films.example/watch', frameId: 0 }] });
check('in a frame: the frame it\'s for, and the page at the top', [x.asked[1][1].frame, x.asked[1][1].page],
  ['https://player.example/embed', 'https://films.example/watch']);
check('...loaded, if the app says so', valueOf(r), {});
x = background(() => new Error('the app is busy'));
r = x.onRequest({ type: 'script', url: 'https://cdn.example/a.js', tabId: 3, frameId: 0, documentUrl: 'https://films.example/' });
check('an app that can\'t answer: it loads (the page never hangs)', valueOf(r), {});
x = background(() => ({ cancel: true }));
r = x.onRequest({ type: 'script', url: 'https://cdn.example/b.js', tabId: 3, frameId: 0, documentUrl: 'https://films.example/' });
check('an answer that comes: it\'s the one used (the wait is called off)', [valueOf(r), x.timers.filter(Boolean).length], [{ cancel: true }, 0]);
x = background(() => NEVER);
r = x.onRequest({ type: 'script', url: 'https://cdn.example/c.js', tabId: 3, frameId: 0, documentUrl: 'https://films.example/' });
check('an answer that never comes: nothing yet...', valueOf(r), undefined);
x.timers.filter(Boolean).forEach((f) => f());
check('...then, after a while, it loads (a page never waits for ever)', valueOf(r), {});

x = background((m) => (m.ask ? { value: '.ad{display:none!important}' } : {}));
x.fromPort({ run: 1, code: '1' });
check('a script before any page is there: no answer, and nothing run', [x.posted, x.ran], [[{ done: 1, value: null }], []]);
x.fromPort({ bridge: '_abc' });
check('a page says hello: it\'s told the bridge\'s name', valueOf(x.fromPage({ hello: 1 }, { tab: { id: 7 } })), { bridge: '_abc' });
x.fromPort({ run: 2, code: "'ok'" });
check('the app\'s script runs in that page, itself, now', x.ran[0], [7, { code: "'ok'", frameId: 0, runAt: 'document_start' }]);
check('...and its value goes back as JSON, as the box\'s own engine gives it', x.posted[1], { done: 2, value: '"ok"' });
x.script = () => now([undefined]);
x.fromPort({ run: 3, code: 'void 0' });
check('nothing to give back: null', x.posted[2], { done: 3, value: null });
x.script = () => now(new Error('no page'), true);
x.fromPort({ run: 4, code: '1' });
check('a page it can\'t run in: null', x.posted[3], { done: 4, value: null });
r = x.fromPage({ ask: 'generic', a: 'films.example', b: '.ad' }, { tab: { id: 7 } });
check('the ad-hiding script\'s question goes to the app', x.asked[0], ['twilight', { ask: 'generic', a: 'films.example', b: '.ad' }]);
check('...and its CSS back to the page', valueOf(r), '.ad{display:none!important}');

function pageScript(answers) {
  const sent = [];
  const timers = [];
  const win = {};
  const browser = { runtime: { sendMessage: (m) => { sent.push(m); return now(answers(m)); } } };
  vm.runInContext(read('page.js'), vm.createContext({ browser, window: win, String, setTimeout: (f) => { timers.push(f); return 1; } }));
  return { sent, timers, win };
}
let p = pageScript((m) => (m.hello ? { bridge: '_abc' } : 'css'));
check('the page script says hello first', p.sent[0], { hello: 1 });
ok('...and puts the bridge under the name it\'s told', p.win._abc && typeof p.win._abc.ask === 'function');
check('the bridge asks the background script, with all three words', [valueOf(p.win._abc.ask('page', 'films.example')), p.sent[1]],
  ['css', { ask: 'page', a: 'films.example', b: '' }]);
p = pageScript(() => ({ bridge: '' }));
check('no name yet: it asks again a second later', [p.timers.length, Object.keys(p.win)], [1, []]);
for (let i = 0; i < 10 && p.timers.length; i++) p.timers.shift()();
check('...five times at most', p.sent.length, 6);
