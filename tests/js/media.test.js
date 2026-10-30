// The remote's play/pause and seek keys (scripts/media.js) on stand-in pages, leaving the app (pause-all.js),
// and keeping the TV awake while a film plays (playing.js).
'use strict';
const vm = require('vm');
const { check } = require('./lib/check');
const { run } = require('./lib/scripts');

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
const M = (ctx, a, n, strict) => run('media', [a, n, strict], ctx);
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
const refusing = video({ paused: true, play() { this.paused = false; return Promise.reject(new Error('no')); } });
c = mediaPage([refusing]);
check('a player that refuses to play breaks nothing', M(c, 'p', 0, 0), 'playing');

const all = [video({ paused: false }), video({ paused: false })];
c = mediaPage(all); run('pause-all', [], c);
check('leaving the app pauses every video', all.map((x) => x.paused), [true, true]);
const stuck = [video({ paused: false, pause() { throw new Error('busy'); } }), video({ paused: false })];
c = mediaPage(stuck); run('pause-all', [], c);
check('...even when one of them refuses', stuck[1].paused, true);

const P = (videos) => run('playing', [], mediaPage(videos));
check('no video: the TV may sleep', P([]), 0);
check('a playing film keeps the TV awake', P([video({ paused: false, readyState: 4 })]), 1);
check('a paused film lets it sleep', P([video({ paused: true, readyState: 4 })]), 0);
check('a film that has ended lets it sleep', P([video({ paused: false, ended: true, readyState: 4 })]), 0);
check('a film still loading (not playing yet) lets it sleep', P([video({ paused: false, readyState: 1 })]), 0);
check('one playing among paused ones keeps it awake', P([video({ readyState: 4 }), video({ paused: false, readyState: 3 })]), 1);
