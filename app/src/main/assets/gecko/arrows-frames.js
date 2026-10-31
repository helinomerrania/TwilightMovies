// The remote's arrows (arrows.js says how they work): into a frame of the page (a film's player, say) and out of it
// again, through the background script, which knows the frames.
'use strict';
(function (a) {
  a.waiting = false;   // a frame is being asked whether it takes the remote

  // A frame that way: asked whether it has something for the remote coming in that way. If so, the remote is
  // there; if not (or no answer: a frame without these scripts), it goes on past the frame. Meanwhile the arrows
  // wait.
  a.into = function (way, frame, c, from, skip) {
    var id = -1;
    try {
      id = browser.runtime.getFrameId(frame);
    } catch (e) {
      // a frame the engine doesn't know
    }
    if (typeof id !== 'number' || id < 0) return a.go(way, c, from, skip.concat([frame]));
    a.waiting = true;
    var done = false;
    var late = setTimeout(function () {
      answer(null);
    }, 700);
    function answer(r) {
      if (done) return;
      done = true;
      clearTimeout(late);
      a.waiting = false;
      if (r && r.ok) {
        try {
          frame.focus({preventScroll: true});
        } catch (e) {
          // it has the remote already
        }
        show(r.rect);
        a.note(way, frame);
      } else if (!a.go(way, c, from, skip.concat([frame])) && !a.scroll(way, from)) {
        a.out(way);
      }
    }
    a.send({arrows: 'in', frame: id, way: way}).then(answer);
    return true;
  };

  // Nothing more that way in this frame: the page around it takes the remote on from the frame. False at the top.
  a.out = function (way) {
    if (a.top) return false;
    a.up({arrows: 'out', way: way});
    return true;
  };

  // The page around says the remote comes into this frame that way: to the nearest from that edge (frames in this
  // one aren't gone into from outside). {ok, rect} for the page around.
  function comeIn(way) {
    var f = a.focused();
    if (!f) {
      var best = a.nearest(way, a.edge(way), null, [], true);
      if (!best || a.tooFar(way, best.rect)) return {ok: false};
      a.put(best.el, way, true);
      f = best.el;
    }
    return {ok: true, rect: a.box(f)};
  }

  // The remote comes out of a frame of this page, that way: on from the frame, to the nearest past it
  function comeOut(way) {
    var f = a.focused();
    if (!f || !a.isFrame(f)) return;
    if (!a.go(way, a.box(f), f, [f]) && !a.scroll(way, f)) a.out(way);
  }

  // A frame of this page shows what has the remote at rect (in the frame): it comes into view here too, and in the
  // pages around this one
  function show(rect) {
    var f = a.focused();
    if (!f || !a.isFrame(f) || !rect) return;
    var b = a.box(f), s = a.style(f);
    var x = b.l + (f.clientLeft || 0) + (s ? parseFloat(s.paddingLeft) || 0 : 0);
    var y = b.t + (f.clientTop || 0) + (s ? parseFloat(s.paddingTop) || 0 : 0);
    var r = {l: x + rect.l, t: y + rect.t, r: x + rect.r, b: y + rect.b}, v = a.view(), dx = 0, dy = 0;
    if (r.b - r.t < v.b - v.t) dy = r.t < v.t ? r.t - v.t - 8 : r.b > v.b ? r.b - v.b + 8 : 0;
    if (r.r - r.l < v.r - v.l) dx = r.l < v.l ? r.l - v.l - 8 : r.r > v.r ? r.r - v.r + 8 : 0;
    if (dx || dy) window.scrollBy({left: dx, top: dy, behavior: 'instant'});
    if (!a.top) a.up({arrows: 'shown', rect: {l: r.l - dx, t: r.t - dy, r: r.r - dx, b: r.b - dy}});
  }

  // The pages around and inside this frame (through the background script)
  browser.runtime.onMessage.addListener(function (m) {
    if (!m || typeof m.arrows !== 'string') return undefined;
    if (m.arrows === 'in') return Promise.resolve(comeIn(m.way));
    if (m.arrows === 'out') comeOut(m.way);
    else if (m.arrows === 'shown') show(m.rect);
    return Promise.resolve(true);
  });
})(arrows);
