// See-through layers that catch the mouse pointer's click to open an ad (scripts/ad-layers.js): which are taken
// away, and all that never is.
'use strict';
const vm = require('vm');
const { check } = require('./lib/check');
const { run } = require('./lib/scripts');

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
const O = (p) => run('ad-layers', [0.5, 0.5], p.ctx);
const clear = { position: 'fixed', opacity: '1', backgroundColor: 'rgba(0, 0, 0, 0)', backgroundImage: 'none' };
const content = layer({ tagName: 'DIV', style: { position: 'static' } });
const trap = layer({ tagName: 'A', style: clear });
let p = overlayPage([trap, content]);
check('a see-through layer over the page is taken away', [O(p), p.removed[0] === trap], [1, true]);
p = overlayPage([layer({ style: clear }), layer({ style: Object.assign({}, clear, { opacity: '0' }) }), content]);
check('two, one over the other', O(p), 2);
p = overlayPage([1, 2, 3, 4, 5, 6, 7].map(() => layer({ style: clear })));
check('at most five', O(p), 5);
check('a video is never taken away', O(overlayPage([layer({ tagName: 'VIDEO', style: clear })])), 0);
check('nor a frame', O(overlayPage([layer({ tagName: 'IFRAME', style: clear })])), 0);
check('nor a small layer', O(overlayPage([layer({ style: clear, w: 300, h: 250 })])), 0);
check('nor one with words', O(overlayPage([layer({ style: clear, textContent: 'Play' })])), 0);
check('nor one with a picture in it', O(overlayPage([layer({ style: clear, querySelector: () => ({}) })])), 0);
check('nor one that can be seen', O(overlayPage([layer({ style: Object.assign({}, clear, { backgroundColor: 'rgb(0, 0, 0)' }) })])), 0);
check('nor one in its place on the page', O(overlayPage([layer({ style: Object.assign({}, clear, { position: 'static' }) })])), 0);
const player = { tagName: 'DIV', querySelector: (s) => (s === 'video' ? {} : null), parentElement: { tagName: 'BODY' } };
check('nor a layer in a video player', O(overlayPage([layer({ style: clear, parentElement: player })])), 0);
