// The remote's arrows (arrows.js says how they work): the remote put on what's nearest that way and scrolled into
// view, or the page scrolled when nothing's near; an arrow, start to end.
'use strict';
(function (a) {
  // The bar fixed over the page at x, y, if it's over el there
  function barOver(el, x, y) {
    var v = a.view();
    if (y < v.t || y >= v.b) return null;
    var hit = document.elementFromPoint(x, y);
    if (!hit || hit === el || el.contains(hit) || hit.contains(el)) return null;
    return a.bar(hit);
  }

  // Into view, the boxes around it scrolling as little as they must, and out from under a bar fixed over the page
  function reveal(el) {
    try {
      el.scrollIntoView({block: 'nearest', inline: 'nearest', behavior: 'instant'});
    } catch (e) {
      // it stays where it is
    }
    var r = a.box(el), v = a.view();
    if (r.b - r.t >= v.b - v.t) return;
    var x = Math.min(Math.max((r.l + r.r) / 2, v.l + 1), v.r - 2);
    var over = barOver(el, x, r.t + 2), under = barOver(el, x, r.b - 2);
    if (over) window.scrollBy({top: -(a.box(over).b - r.t + 8), behavior: 'instant'});
    else if (under) window.scrollBy({top: r.b - a.box(under).t + 8, behavior: 'instant'});
  }

  // In Android's log (adb logcat), where the remote went: for whoever makes a website work with it
  a.note = function (way, el) {
    var words = (el.getAttribute('aria-label') || el.getAttribute('title') || el.textContent || '').slice(0, 200);
    words = words.replace(/\s+/g, ' ').trim().slice(0, 40);
    console.info('Twilight Movies, the remote: ' + way + ' to ' + String(el.tagName).toLowerCase() +
      (words ? ' "' + words + '"' : ''));
  };

  // The remote on el: shown, in view (and in view in the pages around this frame, unless quiet: they're told)
  a.put = function (el, way, quiet) {
    try {
      el.focus({preventScroll: true, focusVisible: true});
    } catch (e) {
      el.focus();
    }
    reveal(el);
    if (!a.top && !quiet) a.up({arrows: 'shown', rect: a.box(el)});
    a.note(way, el);
  };

  // To the background script: its answer, or null
  a.send = function (m) {
    try {
      return browser.runtime.sendMessage(m).then(function (r) {
        return r;
      }, function () {
        return null;
      });
    } catch (e) {
      return Promise.resolve(null);
    }
  };

  // To the page around this frame (the background script knows which it is)
  a.up = function (m) {
    m.up = true;
    return a.send(m);
  };

  // The remote to the nearest that way from c: true if it went there (or is going: into a frame), false if
  // nothing's near enough
  a.go = function (way, c, from, skip) {
    var best = a.nearest(way, c, from, skip, false);
    if (!best || a.tooFar(way, best.rect)) return false;
    if (a.isFrame(best.el)) return a.into(way, best.el, c, from, skip);
    a.put(best.el, way);
    return true;
  };

  function pageBox() {
    return document.scrollingElement || document.documentElement;
  }

  // Room to scroll that way (right-to-left boxes count from 0 down to minus as far as they go)
  function room(p, way, page) {
    if (way === 'up') return p.scrollTop > 0;
    if (way === 'down') return p.scrollTop + (page ? window.innerHeight : p.clientHeight) < p.scrollHeight - 1;
    var max = p.scrollWidth - (page ? window.innerWidth : p.clientWidth);
    if (max <= 1) return false;
    var s = a.style(p), rtl = !!s && s.direction === 'rtl';
    var at = rtl ? -p.scrollLeft : p.scrollLeft;
    return (rtl ? way === 'left' : way === 'right') ? at < max - 1 : at > 1;
  }

  // The box around from (or the page) that can scroll that way, or null
  a.scroller = function (way, from) {
    var page = pageBox();
    for (var p = from ? a.parentOf(from) : null, n = 0; p && p !== page && n < 60; p = a.parentOf(p), n++) {
      var s = a.style(p), o = s && (way === 'up' || way === 'down' ? s.overflowY : s.overflowX);
      if (a.scrolls(o) && room(p, way, false)) return p;
    }
    return page && room(page, way, true) ? page : null;
  };

  // That box scrolls a quarter of itself that way: false if nothing can
  a.scroll = function (way, from) {
    var p = a.scroller(way, from);
    if (!p) return false;
    var page = p === pageBox(), vertical = way === 'up' || way === 'down';
    var size = vertical ? (page ? window.innerHeight : p.clientHeight) : (page ? window.innerWidth : p.clientWidth);
    var step = Math.max(40, Math.round(size / 4)) * (way === 'up' || way === 'left' ? -1 : 1);
    var o = vertical ? {top: step, behavior: 'instant'} : {left: step, behavior: 'instant'};
    if (page) window.scrollBy(o);
    else p.scrollBy(o);
    return true;
  };

  // An arrow: true if it's used here (the page doesn't scroll by it). While a frame is being asked, they wait.
  a.arrow = function (way) {
    if (a.waiting) return true;
    var from = a.focused();
    var c = from && a.meets(a.box(from), a.view()) ? a.box(from) : a.edge(way);
    if (a.go(way, c, from, [])) return true;
    if (a.top || a.scroller(way, from)) return false;   // the page scrolls, as it always has
    return a.out(way);
  };
})(arrows);
