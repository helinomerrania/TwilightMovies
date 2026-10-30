// Tests the small scripts the app runs inside the website (MainActivity.java's SCROLL_JS, MEDIA_JS and
// PAUSE_ALL_JS), on stand-in pages: the scrolling under the mouse pointer and the remote's play/pause and
// seek keys. They are read straight from MainActivity.java, so the app and the tests can't drift apart.
// Run: node tests/scripts.test.js   (Node 18 or newer, nothing to install)
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const DIR = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'twilightcafe', 'movies');
const SOURCES = ['MainActivity.java', 'AdBlocker.java'].map((f) => fs.readFileSync(path.join(DIR, f), 'utf8'));

function javaString(name) {
  let m = null;
  for (const src of SOURCES) {
    m = src.match(new RegExp('static final String ' + name + '\\s*=([\\s\\S]*?);\\n'));
    if (m) break;
  }
  if (!m) throw new Error(name + ' not found in MainActivity.java or AdBlocker.java');
  let out = '';
  const lit = /"((?:[^"\\]|\\.)*)"/g;
  let l;
  while ((l = lit.exec(m[1]))) {
    out += l[1].replace(/\\u([0-9a-fA-F]{4})|\\(.)/g, (all, hex, ch) =>
      hex ? String.fromCharCode(parseInt(hex, 16)) : ({ n: '\n', t: '\t', r: '\r' }[ch] || ch));
  }
  return out;
}

const S = {
  SCROLL_JS: javaString('SCROLL_JS'),
  MEDIA_JS: javaString('MEDIA_JS'),
  PAUSE_ALL_JS: javaString('PAUSE_ALL_JS'),
  PLAYING_JS: javaString('PLAYING_JS'),
  COSMETIC_JS: javaString('COSMETIC_JS'),
  OVERLAY_JS: javaString('OVERLAY_JS'),
};
let pass = 0;
let fail = 0;
function check(name, got, want) {
  if (JSON.stringify(got) === JSON.stringify(want)) {
    pass++;
    console.log('  ok   ' + name);
  } else {
    fail++;
    console.log('  FAIL ' + name + ': got ' + JSON.stringify(got) + ', expected ' + JSON.stringify(want));
  }
}

// They must be plain old JavaScript: the café's TV box has a web engine from 2018.
for (const [name, code] of Object.entries(S)) {
  let ok = true;
  try { new vm.Script(code); } catch (e) { ok = false; }
  check(name + ' is valid JavaScript', ok, true);
  check(name + ' uses no newer syntax (=>, let, const, ?., ??, `)', /=>|\blet\b|\bconst\b|\?\.|\?\?|`/.test(code), false);
}

// ---------- play/pause and seeking ----------
function video(o) {
  return Object.assign({
    tagName: 'VIDEO', paused: true, ended: false, currentTime: 0, duration: 3600, playbackRate: 1, w: 1280, h: 720,
    getBoundingClientRect() { return { width: this.w, height: this.h }; },
    play() { this.paused = false; return Promise.resolve(); },
    pause() { this.paused = true; },
  }, o);
}
function mediaPage(videos, active, fullscreen) {
  const body = { tagName: 'BODY' };
  const doc = {
    body, documentElement: { tagName: 'HTML' }, activeElement: active || body, fullscreenElement: fullscreen || null,
    getElementsByTagName: (t) => (t === 'video' ? videos : []), contains: (n) => videos.includes(n),
  };
  return vm.createContext({ document: doc, window: { innerWidth: 1280, innerHeight: 720 }, Date, Math, isFinite });
}
const M = (ctx, a, n, strict) => vm.runInContext(S.MEDIA_JS + "('" + a + "'," + n + ',' + strict + ')', ctx);
let v;
let c;
c = mediaPage([]); check('no video on the page', M(c, 'q', 0, 1), 'none');
v = video({ paused: false }); c = mediaPage([v]);
check('a playing video is noted', M(c, 'q', 0, 1), 'ok');
check('OK pauses it when the page ignores the key', M(c, 't', 0, 0), 'paused');
check('...and it is paused', v.paused, true);
v = video({ paused: false }); c = mediaPage([v]); M(c, 'q', 0, 1); v.pause();
check('no second toggle when the page already paused it', M(c, 't', 0, 0), 'page');
check('...so it stays paused', v.paused, true);
v = video({ paused: true }); c = mediaPage([v]); M(c, 'q', 0, 1);
check('OK plays a paused video', M(c, 't', 0, 0), 'playing');
v = video({ paused: false }); c = mediaPage([v], { tagName: 'BUTTON' });
check('OK on a focused button is left to the button', M(c, 'q', 0, 1), 'none');
check('the play/pause key still works with a button focused', M(c, 'q', 0, 0), 'ok');
v = video({ paused: false, currentTime: 100 }); c = mediaPage([v]); M(c, 'q', 0, 0);
check('fast-forward when the page ignores it', M(c, 's', 10, 0), 'seeked');
check('...10 seconds on', v.currentTime, 110);
v = video({ paused: false, currentTime: 100 }); c = mediaPage([v]); M(c, 'q', 0, 0); v.currentTime = 110;
check('no second jump when the page already moved it', M(c, 's', 10, 0), 'page');
check('...so it moved only once', v.currentTime, 110);
v = video({ paused: false, currentTime: 100, duration: 105 }); c = mediaPage([v]); M(c, 'q', 0, 0); M(c, 's', 10, 0);
check('fast-forward stops a second before the end', v.currentTime, 104);
v = video({ paused: false, currentTime: 5 }); c = mediaPage([v]); M(c, 'q', 0, 0); M(c, 's', -10, 0);
check('rewind stops at the start', v.currentTime, 0);
const small = video({ paused: true, w: 200, h: 100 });
const big = video({ paused: true });
const full = video({ paused: true, w: 50, h: 50 });
c = mediaPage([small, big, full], null, { tagName: 'DIV', querySelector: () => full }); M(c, 'q', 0, 0); M(c, 't', 0, 0);
check('the full-screen video is the one controlled', [full.paused, big.paused], [false, true]);
c = mediaPage([small, big]); M(c, 'q', 0, 0); M(c, 't', 0, 0);
check('otherwise the biggest video', [big.paused, small.paused], [false, true]);
const playingSmall = video({ paused: false, w: 200, h: 100 });
const bigPaused = video({ paused: true });
c = mediaPage([bigPaused, playingSmall]); M(c, 'q', 0, 0); M(c, 't', 0, 0);
check('a playing video comes before a bigger paused one', [playingSmall.paused, bigPaused.paused], [true, true]);
v = video({ paused: true }); c = mediaPage([v]);
check('the play key', M(c, 'p', 0, 0), 'playing');
check('the pause key', M(c, 'z', 0, 0), 'paused');
v = video({ paused: false }); const vids = [v]; c = mediaPage(vids); M(c, 'q', 0, 0); vids.length = 0;
check('nothing happens when the video has gone', M(c, 't', 0, 0), 'none');
c = mediaPage([video({ paused: false })]);
check('nothing happens without a note first', M(c, 't', 0, 0), 'none');
const paused = [video({ paused: false }), video({ paused: false })];
c = mediaPage(paused); vm.runInContext(S.PAUSE_ALL_JS, c);
check('leaving the app pauses every video', paused.map((x) => x.paused), [true, true]);

// ---------- keeping the TV awake while a film plays ----------
const P = (videos) => vm.runInContext(S.PLAYING_JS, mediaPage(videos));
check('no video: the TV may sleep', P([]), 0);
check('a playing film keeps the TV awake', P([video({ paused: false, readyState: 4 })]), 1);
check('a paused film lets it sleep', P([video({ paused: true, readyState: 4 })]), 0);
check('a film that has ended lets it sleep', P([video({ paused: false, ended: true, readyState: 4 })]), 0);
check('a film still loading (not playing yet) lets it sleep', P([video({ paused: false, readyState: 1 })]), 0);
check('one playing among paused ones keeps it awake', P([video({ readyState: 4 }), video({ paused: false, readyState: 3 })]), 1);

// ---------- scrolling under the pointer ----------
function box(o) {
  const e = { parentElement: null, clientWidth: 500, clientHeight: 500, scrollWidth: 500, scrollHeight: 500, rtl: false, _l: 0, _t: 0 };
  Object.assign(e, o);
  e.style = Object.assign({ overflowX: 'visible', overflowY: 'visible', scrollSnapType: '' }, o.style || {});
  Object.defineProperty(e, 'scrollLeft', {
    get() { return this._l; },
    set(x) { const m = this.scrollWidth - this.clientWidth; this._l = this.rtl ? Math.min(0, Math.max(-m, x)) : Math.max(0, Math.min(m, x)); },
  });
  Object.defineProperty(e, 'scrollTop', {
    get() { return this._t; },
    set(x) { const m = this.scrollHeight - this.clientHeight; this._t = Math.max(0, Math.min(m, x)); },
  });
  return e;
}
function scrollPage(target, viewport) {
  const log = { win: [], at: null };
  const doc = { body: { tagName: 'BODY' }, documentElement: { tagName: 'HTML' }, elementFromPoint: (x, y) => { log.at = [x, y]; return target; } };
  const win = { innerWidth: 1280, innerHeight: 720, visualViewport: viewport, scrollBy: (x, y) => log.win.push([x, y]) };
  return { ctx: vm.createContext({ document: doc, window: win, getComputedStyle: (e) => e.style, Date, Math }), log };
}
const SC = (ctx, a) => vm.runInContext(S.SCROLL_JS + '(' + a.join(',') + ')', ctx);
let r;
let e;
let p;
let t;
e = box({ style: { overflowY: 'auto' }, scrollHeight: 2000 }); ({ ctx: c, log: r } = scrollPage(e));
check('a scrolling box under the pointer scrolls', SC(c, [0.5, 0.5, 0, 0.1]), 'el');
check('...by a tenth of the screen', e.scrollTop, 72);
check('...at the pointer', r.at, [640, 360]);
e = box({ style: { overflowY: 'auto' }, scrollHeight: 2000 }); e._t = 1500; ({ ctx: c, log: r } = scrollPage(e));
check('at the end of the box, the page scrolls instead', SC(c, [0.5, 0.5, 0, 0.1]), 'win');
check('...by the same amount', r.win, [[0, 72]]);
p = box({ style: { overflowY: 'scroll' }, scrollHeight: 3000 });
t = box({ style: { overflowY: 'hidden' }, scrollHeight: 900, parentElement: p }); ({ ctx: c } = scrollPage(t));
check('a box at its top hands scrolling up to the page', SC(c, [0.2, 0.9, 0, -0.05]), 'win');
p._t = 400;
check('the box around it scrolls up', SC(c, [0.2, 0.9, 0, -0.05]), 'el');
check('...by a twentieth of the screen', p.scrollTop, 364);
e = box({ style: { overflowX: 'auto' }, scrollWidth: 3000, rtl: true }); ({ ctx: c } = scrollPage(e));
check('a right-to-left row (Kurdish pages) scrolls left', SC(c, [0.5, 0.5, -0.05, 0]), 'el');
check('...to a negative position, as right-to-left rows do', e.scrollLeft, -64);
check('...and back right', SC(c, [0.5, 0.5, 0.05, 0]), 'el');
check('...to its start', e.scrollLeft, 0);
check('at its start, the page takes it', SC(c, [0.5, 0.5, 0.05, 0]), 'win');
const calls = [];
e = box({ style: { overflowX: 'auto', scrollSnapType: 'x mandatory' }, scrollWidth: 3000, scrollBy: (o) => calls.push(o) }); ({ ctx: c } = scrollPage(e));
check('a snapping row moves a whole step', SC(c, [0.5, 0.5, 0.05, 0]), 'snap');
check('...of 80% of its width', calls[0], { left: 400, top: 0, behavior: 'smooth' });
check('...and waits for it before the next', SC(c, [0.5, 0.5, 0.05, 0]), 'wait');
({ ctx: c, log: r } = scrollPage(null));
check('with nothing under the pointer, the page scrolls', SC(c, [0.5, 0.5, 0, 0.2]), 'win');
check('...by a fifth of the screen', r.win, [[0, 144]]);
({ ctx: c, log: r } = scrollPage(null, { width: 640, height: 360, offsetLeft: 100, offsetTop: 50 }));
SC(c, [0.5, 0.5, 0, 0.5]);
check('a zoomed page: the pointer is found in what is shown', r.at, [420, 230]);
check('...and scrolls by what is shown', r.win, [[0, 180]]);

// ---------- the ad blocker: hiding ad boxes ----------
function el(tag, id, classes, children) {
  const e = { nodeType: 1, tagName: tag, id: id || '', classList: classes || [], children: children || [], parentElement: null };
  e.getElementsByTagName = () => {
    const out = [];
    (function walk(n) { for (const c of n.children) { out.push(c); walk(c); } })(e);
    return out;
  };
  for (const c of e.children) c.parentElement = e;
  return e;
}
function cosmeticPage(tree) {
  const styles = [];
  const calls = { page: [], generic: [] };
  let observer = null;
  const head = { appendChild: (s) => styles.push(s.textContent) };
  const doc = { documentElement: tree, head, createElement: () => ({ textContent: '' }) };
  const bridge = {
    page: (h) => { calls.page.push(h); return '.from-page{display:none!important}\n'; },
    generic: (h, keys) => { calls.generic.push(keys); return keys.split(' ').filter((k) => k === '.ad').map((k) => k + '{display:none!important}\n').join(''); },
  };
  const win = { _bridge: bridge };
  win.MutationObserver = function (cb) { observer = cb; this.observe = function () {}; };
  const ctx = vm.createContext({ window: win, document: doc, location: { hostname: 'films.example' }, setTimeout: (f) => { f(); return 1; } });
  return { ctx, styles, calls, fire: (records) => observer && observer(records), win };
}
const C = (p) => vm.runInContext(S.COSMETIC_JS + "('_bridge')", p.ctx);
let page = cosmeticPage(el('HTML', '', [], [el('BODY', '', [], [el('DIV', 'top', ['ad', 'box']), el('DIV', '', ['film'])])]));
C(page);
check('the page\'s own CSS goes in first', page.styles[0], '.from-page{display:none!important}\n');
check('asked for the site', page.calls.page, ['films.example']);
check('the classes and ids on the page are asked about', page.calls.generic[0].split(' ').sort(), ['#top', '.ad', '.box', '.film']);
check('the generic answer goes in', page.styles[1], '.ad{display:none!important}\n');
C(page);
check('a second time on the same page does nothing', page.calls.page.length, 1);
page.fire([{ type: 'childList', addedNodes: [el('SECTION', 'more', ['ad', 'promo'])] }]);
check('new elements: only new names are asked about', page.calls.generic[1].split(' ').sort(), ['#more', '.promo']);
page.fire([{ type: 'attributes', target: el('DIV', '', ['film', 'late-ad']), addedNodes: [] }]);
check('a changed class is asked about', page.calls.generic[2], '.late-ad');
page.fire([{ type: 'childList', addedNodes: [{ nodeType: 3 }] }]);
check('text changes ask nothing', page.calls.generic.length, 3);
page = cosmeticPage(el('HTML'));
delete page.win._bridge;
let threw = false;
try { C(page); } catch (e) { threw = true; }
check('without the app\'s bridge, nothing happens (and nothing breaks)', [threw, page.styles.length], [false, 0]);

// ---------- the ad blocker: see-through layers that catch the pointer's click ----------
function layer(o) {
  return Object.assign({
    tagName: 'DIV', textContent: '', parentElement: null, w: 1280, h: 720, style: {},
    getBoundingClientRect() { return { width: this.w, height: this.h }; },
    querySelector() { return null; },
  }, o);
}
function overlayPage(stack) {
  const body = { tagName: 'BODY' };
  const removed = [];
  for (const e of stack) {
    if (!e.parentElement) e.parentElement = body;
    e.parentNode = { removeChild: (x) => { removed.push(x); stack.splice(stack.indexOf(x), 1); } };
  }
  const doc = { body, documentElement: { tagName: 'HTML' }, elementFromPoint: () => stack[0] || body };
  const win = { innerWidth: 1280, innerHeight: 720, getComputedStyle: (e) => e.style };
  return { ctx: vm.createContext({ document: doc, window: win }), removed };
}
const O = (p) => vm.runInContext(S.OVERLAY_JS + '(0.5,0.5)', p.ctx);
const clear = { position: 'fixed', opacity: '1', backgroundColor: 'rgba(0, 0, 0, 0)', backgroundImage: 'none' };
let content = layer({ tagName: 'DIV', style: { position: 'static' } });
let trap = layer({ tagName: 'A', style: clear });
let p2 = overlayPage([trap, content]);
check('a see-through layer over the page is taken away', [O(p2), p2.removed[0] === trap], [1, true]);
p2 = overlayPage([layer({ style: clear }), layer({ style: Object.assign({}, clear, { opacity: '0' }) }), content]);
check('two, one over the other', O(p2), 2);
check('a video is never taken away', O(overlayPage([layer({ tagName: 'VIDEO', style: clear })])), 0);
check('nor a frame', O(overlayPage([layer({ tagName: 'IFRAME', style: clear })])), 0);
check('nor a small layer', O(overlayPage([layer({ style: clear, w: 300, h: 250 })])), 0);
check('nor one with words', O(overlayPage([layer({ style: clear, textContent: 'Play' })])), 0);
check('nor one that can be seen', O(overlayPage([layer({ style: Object.assign({}, clear, { backgroundColor: 'rgb(0, 0, 0)' }) })])), 0);
check('nor one in its place on the page', O(overlayPage([layer({ style: Object.assign({}, clear, { position: 'static' }) })])), 0);
const player = { tagName: 'DIV', querySelector: (s) => (s === 'video' ? {} : null), parentElement: { tagName: 'BODY' } };
check('nor a layer in a video player', O(overlayPage([layer({ style: clear, parentElement: player })])), 0);

console.log('The script tests: ' + pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
