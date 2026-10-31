// The remote's arrows (arrows.js says how they work): what can have the remote, and the nearest of it that way.
'use strict';
(function (a) {
  // What can have the remote: what the keyboard can go to, as in the box's engine
  var ITEMS = 'a[href],area[href],button,input,select,textarea,iframe,frame,summary,[tabindex],[contenteditable],' +
    'audio[controls],video[controls]';

  // Everything in scope that can have the remote, in shadow roots too (not on a huge page)
  function items(scope) {
    var out = [];
    gather(scope, out, 0);
    return out;
  }

  function gather(root, out, depth) {
    var found = root.querySelectorAll(ITEMS), i;
    for (i = 0; i < found.length; i++) out.push(found[i]);
    if (depth > 2) return;
    var all = root.querySelectorAll('*');
    if (all.length > 6000) return;
    for (i = 0; i < all.length; i++) {
      var s = a.shadowOf(all[i]);
      if (s) gather(s, out, depth + 1);
    }
  }

  // It can have the remote by what it is (a link, a button, a field), not only by a tabindex
  function own(el) {
    var tag = el.tagName;
    if (tag === 'A' || tag === 'AREA') return el.hasAttribute('href');
    if ((tag === 'AUDIO' || tag === 'VIDEO') && el.hasAttribute('controls')) return true;
    return /^(BUTTON|INPUT|SELECT|TEXTAREA|IFRAME|FRAME|SUMMARY)$/.test(tag);
  }

  // It can have the remote at all: not switched off, not kept out of the keys' way (tabindex -1), not a hidden
  // field, not a tiny frame (an ad's), not in a part of the page out of use (inert)
  function takes(el) {
    try {
      if (el.matches(':disabled')) return false;
    } catch (e) {
      // not a field
    }
    var t = el.getAttribute('tabindex');
    if (t !== null) {
      var n = parseInt(t, 10);
      if (n < 0 || (isNaN(n) && !own(el))) return false;
    }
    if (el.tagName === 'INPUT' && String(el.type).toLowerCase() === 'hidden') return false;
    if (el.hasAttribute('contenteditable') && !el.isContentEditable && t === null && !own(el)) return false;
    if (a.isFrame(el)) {
      var r = a.box(el);
      if (r.r - r.l < 24 || r.b - r.t < 24) return false;
    }
    return !(el.closest && el.closest('[inert]'));
  }

  function hides(o) {
    return o === 'hidden' || o === 'clip';
  }

  a.scrolls = function (o) {
    return o === 'auto' || o === 'scroll';
  };

  // Out of the remote's reach: outside a box that hides what's outside it (overflow hidden), so nothing brings it in
  // (a closed menu, a slider's hidden part); or hidden in a box that scrolls (a row of films) while the remote is
  // outside that box. As in the box's engine, the remote goes into a row where the row shows, and on from there to
  // what the row scrolls into view.
  function outOfReach(el, r, from) {
    var e = {l: r.l, t: r.t, r: r.r, b: r.b};
    for (var p = a.parentOf(el), n = 0; p && n < 60; p = a.parentOf(p), n++) {
      var s = a.style(p);
      if (!s || (s.overflowX === 'visible' && s.overflowY === 'visible')) continue;
      var b = a.box(p);
      var outX = e.r <= b.l || e.l >= b.r, outY = e.b <= b.t || e.t >= b.b;
      if ((outX && hides(s.overflowX)) || (outY && hides(s.overflowY))) return true;
      var inX = outX && a.scrolls(s.overflowX), inY = outY && a.scrolls(s.overflowY);
      if (!inX && !inY) continue;
      if (!from || !p.contains(from)) return true;
      if (inX) {
        e.l = Math.max(b.l, Math.min(e.l, b.r - 1));   // where the box brings it in, for the boxes around this one
        e.r = e.l + 1;
      }
      if (inY) {
        e.t = Math.max(b.t, Math.min(e.t, b.b - 1));
        e.b = e.t + 1;
      }
    }
    return false;
  }

  // The bar fixed to the screen that el is in (a page's header), or null. Not a veil over the whole screen.
  a.bar = function (el) {
    for (var p = el, n = 0; p && n < 30; p = a.parentOf(p), n++) {
      var s = a.style(p);
      if (s && (s.position === 'fixed' || s.position === 'sticky')) {
        var b = a.box(p), v = a.view();
        return (b.r - b.l) * (b.b - b.t) < 0.5 * (v.r - v.l) * (v.b - v.t) ? p : null;
      }
    }
    return null;
  };

  // Something else is on top of it wherever it's on the screen (a window over the page, its veil). A bar fixed to the
  // screen doesn't count: the page scrolls it out from under it. Off the screen, nothing can be.
  function covered(el, r) {
    var v = a.view(), tried = 0;
    var spots = [[(r.l + r.r) / 2, (r.t + r.b) / 2], [r.l + (r.r - r.l) / 4, r.t + (r.b - r.t) / 4],
      [r.r - (r.r - r.l) / 4, r.b - (r.b - r.t) / 4]];
    var root = el.getRootNode ? el.getRootNode() : document;
    if (!root || !root.elementFromPoint) root = document;
    for (var i = 0; i < spots.length; i++) {
      var x = spots[i][0], y = spots[i][1];
      if (x < v.l || y < v.t || x >= v.r || y >= v.b) continue;
      tried++;
      var hit = root.elementFromPoint(x, y);
      if (!hit || hit === el || el.contains(hit) || hit.contains(el) || a.bar(hit)) return false;
    }
    return tried > 0;
  }

  // It shows, for the remote coming from from: not hidden, within reach, not under something else
  function shown(el, r, from) {
    var s = a.style(el);
    return !!s && s.visibility === 'visible' && !outOfReach(el, r, from) && !covered(el, r);
  }

  // The window over the page (a dialog) that keeps the remote, if one is open
  function dialog(from) {
    var sel = 'dialog:modal,[aria-modal="true"]', list;
    try {
      if (from && from.closest && from.closest(sel)) return from.closest(sel);
      list = document.querySelectorAll(sel);
    } catch (e) {
      list = document.querySelectorAll('[aria-modal="true"]');
    }
    for (var i = list.length - 1; i >= 0; i--) {
      var r = a.box(list[i]), s = a.style(list[i]);
      if (r.r > r.l && r.b > r.t && s && s.visibility === 'visible') return list[i];
    }
    return null;
  }

  // Where the remote can go: what's full screen, else a window over the page, else the whole page
  function scopeOf(from) {
    var full = document.fullscreenElement;
    if (full && !a.isFrame(full)) return full;
    return dialog(from) || document;
  }

  // The nearest that way from c (a box on the screen) that shows, in scope: not from (what has the remote) or the
  // boxes around it, nor what's in skip; with noFrames, no frames. {el, rect}, or null.
  a.nearest = function (way, c, from, skip, noFrames) {
    var all = items(scopeOf(from)), list = [], i;
    for (i = 0; i < all.length; i++) {
      var el = all[i];
      if (el === from || skip.indexOf(el) >= 0 || (noFrames && a.isFrame(el))) continue;
      if ((from && el.contains(from)) || !takes(el)) continue;
      var r = a.box(el);
      if (r.r - r.l < 1 || r.b - r.t < 1 || !a.ahead(way, c, r)) continue;
      list.push({el: el, rect: r, d: a.distance(way, c, r)});
    }
    list.sort(function (p, q) {
      return p.d - q.d;
    });
    for (i = 0; i < list.length && i < 30; i++) if (shown(list[i].el, list[i].rect, from)) return list[i];
    return null;
  };
})(arrows);
