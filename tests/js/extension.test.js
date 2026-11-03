// The app's extension in Firefox's engine (app/src/main/assets/gecko): its background script and its page script,
// with a stand-in for the engine's browser API that answers at once (so the checks run in order); and that it
// and web/gecko/Extension.java call the app by the same name, from the same folder. (The arrows: arrows.test.js.)
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');
const { SyncPromise, now, valueOf } = require('./lib/promise');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');
const manifest = JSON.parse(read('manifest.json'));
const NEVER = {};   // an app that never answers

function background(app, frames) {
  const asked = [];
  const posted = [];
  const ran = [];
  const sent = [];
  const pageListeners = [];
  const b = { onRequest: null, filter: null, extra: null, fromPort: null, nativeApp: null,
    fromPage: (m, sender) => pageListeners.reduce((r, f) => (r !== undefined ? r : f(m, sender)), undefined) };
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
      getURL: (path) => 'moz-extension://the-app/' + (path || ''),
      // every script's listener, as the background page has them; the first that answers is the answer
      onMessage: { addListener: (f) => { pageListeners.push(f); } },
    },
    webRequest: { onBeforeRequest: { addListener: (f, filter, extra) => { b.onRequest = f; b.filter = filter; b.extra = extra; } } },
    // frames: {frameId: [the frame around it, what its arrows.js answers (or a function of the message)]}
    webNavigation: { getFrame: (d) => now(frames && frames[d.frameId] ? { parentFrameId: frames[d.frameId][0] } : null) },
    tabs: {
      executeScript: (tab, d) => { ran.push([tab, d]); return b.script(d.code); },
      sendMessage: (tab, m, o) => {
        sent.push([tab, m, o]);
        const f = frames && frames[o.frameId];
        if (!f || f[1] === undefined) return now(new Error('no one there'), true);
        return now(typeof f[1] === 'function' ? f[1](m) : f[1]);
      },
    },
  };
  b.script = () => now(['ok']);
  const timers = [];
  b.timers = timers;
  const ctx = vm.createContext({ browser, JSON, Promise: SyncPromise, URL, console,
    setTimeout: (f) => { timers.push(f); return timers.length; }, clearTimeout: (n) => { timers[n - 1] = null; } });
  for (const f of manifest.background.scripts) vm.runInContext(read(f), ctx);   // one background page, in order
  return Object.assign(b, { asked, posted, ran, sent });
}

check('the manifest: version 2, so requests can be held while the app answers', manifest.manifest_version, 2);
ok('...it may hold and change every request, and talk to the app',
  ['<all_urls>', 'webRequest', 'webRequestBlocking', 'nativeMessaging', 'geckoViewAddons'].every((p) => manifest.permissions.includes(p)));
check('...its background scripts: the app\'s side, (in the app\'s own try only) the data feeds written down, the Twilight screens\' player alone',
  manifest.background.scripts, ['background.js', 'study.js', 'alone.js']);
const cs = manifest.content_scripts[0];
check('...its page scripts: in the page itself (its top frame), from the very start; the player alone', [cs.js, cs.run_at, cs.all_frames],
  [['page.js', 'player-steps.js', 'player-servers.js', 'player.js'], 'document_start', false]);
ok('...and an id of its own', /^[\w.-]+@[\w.-]+$/.test(manifest.browser_specific_settings.gecko.id));
const keys = manifest.content_scripts[1] || {};
check('...the remote\'s arrows: in every frame of the page, from the very start, with their ring',
  [keys.js, keys.css, keys.run_at, keys.all_frames], [['arrows.js', 'arrows-reach.js', 'arrows-move.js', 'arrows-frames.js',
    'arrows-keys.js'], ['arrows.css'], 'document_start', true]);
ok('...and it may follow a page\'s frames, to pass the remote between them', manifest.permissions.includes('webNavigation'));

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
check('nor what the Twilight screens ask the websites for (the app\'s own page)', [x.onRequest({ type: 'xmlhttprequest',
  url: 'https://krd.watch/api/feed', tabId: 3, frameId: 0, originUrl: 'moz-extension://the-app/ui/index.html' }), x.asked.length], [{}, 0]);
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

// The remote between a page's frames (arrows.js): frame 5 (in the top, 0) has arrows.js, 9 is in 5, 12 has none
const player = (m) => (m.arrows === 'in' ? { ok: true, rect: { l: 1, t: 2, r: 3, b: 4 } } : true);
x = background(() => ({}), { 0: [-1, true], 5: [0, player], 9: [5, true], 12: [0] });
r = x.fromPage({ arrows: 'in', frame: 5, way: 'down', wait: 1250 }, { tab: { id: 7 }, frameId: 0 });
check('the remote into a frame: that frame is asked, in the page shown', x.sent[0], [7, { arrows: 'in', way: 'down', wait: 1250 }, { frameId: 5 }]);
check('...and its answer is the one given', valueOf(r), { ok: true, rect: { l: 1, t: 2, r: 3, b: 4 } });
r = x.fromPage({ arrows: 'out', way: 'up', up: true }, { tab: { id: 7 }, frameId: 9 });
check('out of a frame: to the frame around it', [x.sent[1], valueOf(r)], [[7, { arrows: 'out', way: 'up' }, { frameId: 5 }], true]);
r = x.fromPage({ arrows: 'shown', rect: { l: 5, t: 6, r: 7, b: 8 }, up: true }, { tab: { id: 7 }, frameId: 5 });
check('where the remote is, shown in the frame around too', x.sent[2], [7, { arrows: 'shown', rect: { l: 5, t: 6, r: 7, b: 8 } }, { frameId: 0 }]);
r = x.fromPage({ arrows: 'in', frame: 12, way: 'left' }, { tab: { id: 7 }, frameId: 0 });
check('a frame without the arrows (an ad\'s, say): no', valueOf(r), false);
r = x.fromPage({ arrows: 'out', way: 'up', up: true }, { tab: { id: 7 }, frameId: 0 });
check('the top of the page has no frame around it: nobody is asked', [valueOf(r), x.sent.length], [false, 4]);
check('nothing for the arrows from outside a page', valueOf(x.fromPage({ arrows: 'in', frame: 5, way: 'up' }, {})), false);
x = background(() => ({}));
r = x.fromPage({ keyboard: 1 }, { tab: { id: 7 }, frameId: 0 });
check('OK on a field: the app is asked for the on-screen keyboard', [x.asked[0], valueOf(r)], [['twilight', { keyboard: 1 }], true]);
ok('...and the app shows it', /m\.has\("keyboard"\)/.test(java) && /showKeyboard\(\)/.test(java));

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
