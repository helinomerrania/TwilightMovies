// Promises that keep their word at once, so the checks of scripts that wait on Firefox's engine run in order: for
// what a stand-in engine gives, and as the Promise the scripts make themselves.
'use strict';

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
  this.catch = (no) => this.then(undefined, no);
  try { executor((v) => settle(v, false), (e) => settle(e, true)); } catch (e) { settle(e, true); }
}
SyncPromise.resolve = (v) => new SyncPromise((r) => r(v));

/** A promise already kept (or broken). */
const now = (value, broken) => new SyncPromise((res, rej) => (broken ? rej(value) : res(value)));
/** One that's never kept: an engine or app that never answers. */
const never = () => new SyncPromise(() => {});
/** What a promise has given by now (undefined if nothing yet). */
const valueOf = (p) => { let v; p.then((x) => { v = x; }); return v; };

module.exports = { SyncPromise, now, never, valueOf };
