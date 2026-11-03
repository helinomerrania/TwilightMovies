// The remote's arrows (arrows.js says how they work): into a frame of the page (a film's player, often a frame in a
// frame) and out of it again, through the background script, which knows the frames.
'use strict';
(function (a) {
  a.waiting = false;   // a frame is being asked whether it takes the remote: the arrows wait
  a.lift = false;      // the page around shows its servers over its player (player-servers.js): up goes out to them

  // The frame (which has the remote already), asked through the background script whether it has something for the
  // remote coming in that way: {ok, rect} (where it is, in the frame's own page), or null: no answer within wait
  // milliseconds (a frame without these scripts), or a frame the engine doesn't know
  function ask(frame, way, wait) {
    var id = -1;
    try {
      id = browser.runtime.getFrameId(frame);
    } catch (e) {
      // not one the engine knows
    }
    if (typeof id !== 'number' || id < 0) {
      console.info('Twilight Movies, the remote: a frame the engine doesn\'t know (' + id + ')');
      return Promise.resolve(null);
    }
    return new Promise(function (resolve) {
      var late = setTimeout(function () {
        console.info('Twilight Movies, the remote: frame ' + id + ' didn\'t answer');
        resolve(null);
      }, wait);
      a.send({arrows: 'in', frame: id, way: way, wait: wait - 250}).then(function (r) {
        clearTimeout(late);
        console.info('Twilight Movies, the remote: frame ' + id + (r && r.ok ? ' takes it' : ' has nothing for it'));
        resolve(r);
      });
    });
  }

  // Where rect, in frame's own page, is in this one
  function inHere(frame, rect) {
    var b = a.box(frame), s = a.style(frame);
    var x = b.l + (frame.clientLeft || 0) + (s ? parseFloat(s.paddingLeft) || 0 : 0);
    var y = b.t + (frame.clientTop || 0) + (s ? parseFloat(s.paddingTop) || 0 : 0);
    return {l: x + rect.l, t: y + rect.t, r: x + rect.r, b: y + rect.b};
  }

  // The remote to the nearest that way from c, into frames too: a frame gets the remote first (so what's in it can
  // take it), then is asked; with nothing for it there, the remote goes back where it was and on past the frame.
  // A promise of {ok, rect: where the remote is now, here}, or {ok: false}.
  a.enter = function (way, c, from, skip, wait) {
    var best = a.nearest(way, c, from, skip, false);
    if (!best || a.tooFar(way, best.rect)) return Promise.resolve({ok: false});
    var el = best.el;
    if (!a.isFrame(el)) {
      a.put(el, way, true);
      return Promise.resolve({ok: true, rect: a.box(el)});
    }
    try {
      el.focus({preventScroll: true});
    } catch (e) {
      // it stays where it was
    }
    a.waiting = true;
    return ask(el, way, wait).then(function (r) {
      a.waiting = false;
      if (r && r.ok) {
        a.note(way, el);
        return {ok: true, rect: inHere(el, r.rect)};
      }
      if (from) from.focus({preventScroll: true});
      else if (a.focused() === el) el.blur();
      return a.enter(way, c, from, skip.concat([el]), wait);
    });
  };

  // What has the remote, at r here, comes into view here, and in the pages around this one
  a.shown = function (r) {
    var v = a.view(), dx = 0, dy = 0;
    if (r.b - r.t < v.b - v.t) dy = r.t < v.t ? r.t - v.t - 8 : r.b > v.b ? r.b - v.b + 8 : 0;
    if (r.r - r.l < v.r - v.l) dx = r.l < v.l ? r.l - v.l - 8 : r.r > v.r ? r.r - v.r + 8 : 0;
    if (dx || dy) window.scrollBy({left: dx, top: dy, behavior: 'instant'});
    if (!a.top) a.up({arrows: 'shown', rect: {l: r.l - dx, t: r.t - dy, r: r.r - dx, b: r.b - dy}});
  };

  // Nothing more that way in this frame: the page around it takes the remote on from the frame. False at the top.
  a.out = function (way) {
    if (a.top) return false;
    a.up({arrows: 'out', way: way});
    return true;
  };

  // The page around says the remote comes into this frame that way: what had it here before, or the nearest from
  // that edge (into the frames in this one too). {ok, rect} for the page around.
  function comeIn(way, wait) {
    var f = a.focused();
    if (f) return Promise.resolve({ok: true, rect: a.box(f)});
    return a.enter(way, a.edge(way), null, [], wait).then(function (r) {
      if (!r.ok) console.info('Twilight Movies, the remote: nothing for it in ' + location.host + ', coming ' + way);
      return r;
    });
  }

  // Up to the website's servers over its player (lift): out of the player's frame to the page around; in the page,
  // from the player (or from nothing) to the row above it. True if the remote goes there.
  a.lifting = function () {
    if (!a.lift) return false;
    if (!a.top) return a.out('up');
    var player = document.querySelector('[data-tm-player]'), f = a.focused();
    if (!player || (f && !player.contains(f))) return false;
    return a.go('up', a.box(player), f, f ? [f] : []);
  };

  // The remote comes out of a frame of this page, that way: on from the frame, to the nearest past it
  function comeOut(way) {
    var f = a.focused();
    if (!f || !a.isFrame(f)) return;
    if (a.lift && way === 'up' && a.out(way)) return;   // on out, to the servers, not to the player's own parts
    if (!a.go(way, a.box(f), f, [f]) && !a.scroll(way, f)) a.out(way);
  }

  // The pages around and inside this frame (through the background script)
  browser.runtime.onMessage.addListener(function (m) {
    if (!m || typeof m.arrows !== 'string') return undefined;
    if (m.arrows === 'in') return comeIn(m.way, Math.max(300, m.wait || 1200));
    if (m.arrows === 'lift') a.lift = true;
    var f = a.focused();
    if (m.arrows === 'out') comeOut(m.way);
    else if (m.arrows === 'shown' && f && a.isFrame(f) && m.rect) a.shown(inHere(f, m.rect));
    return Promise.resolve(true);
  });
  if (!a.top) {   // a frame that comes once the servers show (another server's player) asks
    a.send({lifted: 1}).then(function (r) {
      if (r && r.lift === true) a.lift = true;
    });
  }
})(arrows);
