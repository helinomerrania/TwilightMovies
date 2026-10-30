// Scrolling under the mouse pointer (scripts/scroll.js), on stand-in pages: boxes that scroll, the page when they
// can't, right-to-left rows, rows that snap, and a zoomed page.
'use strict';
const vm = require('vm');
const { check } = require('./lib/check');
const { run } = require('./lib/scripts');

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
const SC = (ctx, a) => run('scroll', a, ctx);
let c;
let r;
let e;
e = box({ style: { overflowY: 'auto' }, scrollHeight: 2000 }); ({ ctx: c, log: r } = scrollPage(e));
check('a scrolling box under the pointer scrolls', SC(c, [0.5, 0.5, 0, 0.1]), 'el');
check('...by a tenth of the screen', e.scrollTop, 72);
check('...at the pointer', r.at, [640, 360]);
e = box({ style: { overflowY: 'auto' }, scrollHeight: 2000 }); e._t = 1500; ({ ctx: c, log: r } = scrollPage(e));
check('at the end of the box, the page scrolls instead', SC(c, [0.5, 0.5, 0, 0.1]), 'win');
check('...by the same amount', r.win, [[0, 72]]);
const outer = box({ style: { overflowY: 'scroll' }, scrollHeight: 3000 });
const inner = box({ style: { overflowY: 'hidden' }, scrollHeight: 900, parentElement: outer }); ({ ctx: c } = scrollPage(inner));
check('a box at its top hands scrolling up to the page', SC(c, [0.2, 0.9, 0, -0.05]), 'win');
outer._t = 400;
check('the box around it scrolls up', SC(c, [0.2, 0.9, 0, -0.05]), 'el');
check('...by a twentieth of the screen', outer.scrollTop, 364);
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
