// A stand-in page for the remote's arrows in Firefox's engine (app/src/main/assets/gecko/arrows*.js, loaded as the
// manifest lists them): elements with boxes on a 1920 x 1080 screen, styles, the focus, what's on top at a spot,
// keys going through the window and the website's own listeners, the background script, timers, Android's log.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { ROOT } = require('./scripts');
const { SyncPromise, now, never } = require('./promise');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const FILES = JSON.parse(fs.readFileSync(path.join(DIR, 'manifest.json'), 'utf8')).content_scripts[1].js;
const SOURCES = FILES.map((f) => [f, fs.readFileSync(path.join(DIR, f), 'utf8')]);
const STYLE = { visibility: 'visible', overflowX: 'visible', overflowY: 'visible', position: 'static', direction: 'ltr',
  paddingLeft: '0px', paddingTop: '0px', pointerEvents: 'auto' };
const CODES = { ArrowLeft: 37, ArrowUp: 38, ArrowRight: 39, ArrowDown: 40, Enter: 13 };
const NEVER = {};   // a frame (or the app) that never answers

// What the stand-in page understands of a selector: tags, [attribute], [attribute="value"], :modal, :disabled
function matches(e, selector) {
  return selector.split(',').some((one) => {
    const m = /^([a-z*]*)(.*)$/i.exec(one.trim());
    if (m[1] && m[1] !== '*' && e.tagName !== m[1].toUpperCase()) return false;
    return (m[2].match(/\[[^\]]+\]|:[a-z-]+/gi) || []).every((p) => {
      if (p === ':modal') return e.modal;
      if (p === ':disabled') return e.disabled;
      const a = /^\[([\w-]+)(?:="([^"]*)")?\]$/.exec(p);
      if (!a) throw new Error('the stand-in page can\'t read ' + p);
      return a[2] === undefined ? a[1] in e.attrs : e.attrs[a[1]] === a[2];
    });
  });
}

function all(e, out) {
  for (const c of e.children) {
    out.push(c);
    all(c, out);
  }
  return out;
}

// An element: its tag, then what it is (name: what the checks call it, attrs, rect [l, t, r, b] on the screen,
// style, z: above the rest, value, type, caret, editable, disabled, modal, frameId, scroll), then what's in it
function E(tag, o, kids) {
  o = o || {};
  const e = {
    tagName: tag.toUpperCase(), name: o.name || tag, attrs: Object.assign({}, o.attrs), rect: o.rect || null,
    css: Object.assign({}, o.style), children: kids || [], parentElement: null, z: o.z || 0, calls: [],
    value: o.value === undefined ? '' : o.value, type: o.type || (tag === 'input' ? 'text' : ''),
    disabled: !!o.disabled, readOnly: !!o.readOnly, isContentEditable: !!o.editable, modal: !!o.modal,
    selectionStart: o.caret === undefined ? null : o.caret, selectionEnd: o.caret === undefined ? null : o.caret,
    frameId: o.frameId, clientLeft: 0, clientTop: 0, textContent: o.text === undefined ? o.name || '' : o.text,
    scrollTop: 0, scrollLeft: 0, clientWidth: 0, clientHeight: 0, scrollWidth: 0, scrollHeight: 0,
  };
  Object.assign(e, o.scroll || {});
  for (const c of e.children) c.parentElement = e;
  e.getAttribute = (n) => (n in e.attrs ? String(e.attrs[n]) : null);
  e.hasAttribute = (n) => n in e.attrs;
  e.getBoundingClientRect = () => {
    const r = e.rect || [0, 0, 0, 0];
    return { left: r[0], top: r[1], right: r[2], bottom: r[3] };
  };
  e.matches = (s) => matches(e, s);
  e.closest = (s) => {
    for (let p = e; p; p = p.parentElement) if (matches(p, s)) return p;
    return null;
  };
  e.contains = (x) => {
    for (let p = x; p; p = p.parentElement) if (p === e) return true;
    return false;
  };
  e.querySelectorAll = (s) => all(e, []).filter((x) => matches(x, s));
  e.getRootNode = () => e.doc;
  e.focus = (opts) => {
    e.calls.push(['focus', opts]);
    e.doc.activeElement = e;
    e.doc.fire('focusin', {});
  };
  e.blur = () => {
    e.calls.push(['blur']);
    if (e.doc.activeElement === e) e.doc.activeElement = e.doc.body;
  };
  e.scrollIntoView = (opts) => e.calls.push(['scrollIntoView', opts]);
  e.click = () => e.calls.push(['click']);
  e.showPicker = () => {
    if (o.noPicker) throw new Error('not here');
    e.calls.push(['picker']);
  };
  e.scrollBy = (opts) => e.calls.push(['scrollBy', opts]);
  return e;
}

// A page with these in its body: the window, the document, what the background script is sent (answer(message):
// its answer, or NEVER), the timers, Android's log, the page's scrolls; frame: it's a frame in another page
function page(body, o) {
  o = o || {};
  const listeners = [];
  const html = E('html', { rect: [0, 0, 1920, 1080], scroll: o.scroll }, [E('body', { rect: [0, 0, 1920, 1080] }, body)]);
  const doc = {
    documentElement: html, body: html.children[0], scrollingElement: html, fullscreenElement: null,
    querySelectorAll: (s) => [html].concat(all(html, [])).filter((x) => matches(x, s)),
    elementFromPoint: (x, y) => {
      let best = null;
      all(html, []).forEach((e) => {
        const r = e.rect;
        const s = Object.assign({}, STYLE, e.css);
        if (!r || x < r[0] || x >= r[2] || y < r[1] || y >= r[3] || s.pointerEvents === 'none' || s.visibility !== 'visible') return;
        if (!best || e.z >= best.z) best = e;
      });
      return best;
    },
  };
  html.scrollHeight = html.scrollHeight || 1080;
  html.scrollWidth = html.scrollWidth || 1920;
  [html].concat(all(html, [])).forEach((e) => { e.doc = doc; });
  doc.activeElement = doc.body;
  const p = { doc, html, sent: [], timers: [], notes: [], scrolled: [], own: [], listeners, fromBackground: null };
  const win = {
    innerWidth: 1920, innerHeight: 1080,
    getComputedStyle: (e) => Object.assign({}, STYLE, e.css),
    addEventListener: (type, fn, capture) => listeners.push({ type, fn, capture: !!capture }),
    removeEventListener: (type, fn, capture) => {
      const i = listeners.findIndex((l) => l.type === type && l.fn === fn && l.capture === !!capture);
      if (i >= 0) listeners.splice(i, 1);
    },
    scrollBy: (opts) => p.scrolled.push(opts),
  };
  win.top = o.frame ? {} : win;
  p.win = win;
  // An event: the window's listeners that catch it on the way in, the website's own (at the element, and up to the
  // document), then the window's on the way out
  doc.fire = (type, props) => {
    const ev = Object.assign({ type, isTrusted: true, altKey: false, ctrlKey: false, metaKey: false, shiftKey: false,
      defaultPrevented: false, stopped: false }, props);
    ev.preventDefault = () => { ev.defaultPrevented = true; };
    ev.stopImmediatePropagation = () => { ev.stopped = true; };
    const run = (list) => {
      for (const l of list) {
        if (ev.stopped) return;
        l.fn(ev);
      }
    };
    run(listeners.filter((l) => l.type === type && l.capture));
    run(p.own.filter((l) => l.type === type));
    run(listeners.filter((l) => l.type === type && !l.capture));
    return ev;
  };
  const browser = {
    runtime: {
      sendMessage: (m) => {
        p.sent.push(m);
        const a = o.answer ? o.answer(m) : true;
        return a === NEVER ? never() : now(a);
      },
      getFrameId: (target) => {
        if (typeof target.frameId !== 'number') throw new Error('not a frame');
        return target.frameId;
      },
      onMessage: { addListener: (f) => { p.fromBackground = f; } },
    },
  };
  const ctx = vm.createContext({
    window: win, document: doc, browser, Promise: SyncPromise, console: { info: (s) => p.notes.push(s) },
    setTimeout: (f) => p.timers.push(f), clearTimeout: (n) => { p.timers[n - 1] = null; },
  });
  for (const [file, source] of SOURCES) vm.runInContext(source, ctx, { filename: file });
  return p;
}

// A key, down and up, as the remote sends it: what the page did with each
function press(p, key, props) {
  const down = p.doc.fire('keydown', Object.assign({ key, keyCode: CODES[key] }, props));
  const up = p.doc.fire('keyup', Object.assign({ key, keyCode: CODES[key] }, props));
  return { used: down.defaultPrevented, upUsed: up.defaultPrevented, stopped: down.stopped };
}
/** What has the remote (its name), or null */
const at = (p) => (p.doc.activeElement === p.doc.body ? null : p.doc.activeElement.name);
const called = (e, what) => e.calls.filter((c) => c[0] === what);
const find = (p, name) => [p.html].concat(all(p.html, [])).find((e) => e.name === name);
/** The remote on it, as if it had been there all along (no events) */
const put = (p, name) => { p.doc.activeElement = find(p, name); };
/** The website's own listener for a key event */
const own = (p, type, fn) => p.own.push({ type, fn });

module.exports = { FILES, NEVER, E, page, press, at, called, find, put, own };
