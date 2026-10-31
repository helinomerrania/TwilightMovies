// The remote's arrows on the website, in Firefox's engine. The box's own engine (Android's WebView) moves from link
// to link by itself on a TV ("spatial navigation"), and the websites' TV pages count on it; Firefox's engine only
// scrolls. So here an arrow takes the remote to the nearest link, button or field that way, by the box's engine's
// own measure, and scrolls it into view; with nothing near that way, the page scrolls as before. OK on a field
// opens the on-screen keyboard (once something is typed, OK is the field's own: a search); on something the page
// only clicks on, it clicks. What the website does with a key itself comes first (it says so: preventDefault).
// In every frame of the page (a film's player is often one): at a frame's edge the remote goes on in the page around
// it, through the background script. In the extension's own part of the page: the website's scripts never see it.
//
// In five files, which Firefox's engine runs in this order in each frame (manifest.json), all under the name arrows:
//   arrows.js         the measure: boxes on the screen, which way, how far
//   arrows-reach.js   what can have the remote, and the nearest of it that way
//   arrows-move.js    the remote put there, scrolled into view; an arrow
//   arrows-frames.js  the remote into a frame of the page and out of it
//   arrows-keys.js    the keys: the arrows, after the website's own; OK
'use strict';
var arrows = {top: window.top === window};
(function (a) {
  // An element's box on the screen
  a.box = function (el) {
    var r = el.getBoundingClientRect();
    return {l: r.left, t: r.top, r: r.right, b: r.bottom};
  };

  // The screen (this frame's part of it)
  a.view = function () {
    return {l: 0, t: 0, r: window.innerWidth, b: window.innerHeight};
  };

  a.meets = function (p, q) {
    return p.l < q.r && q.l < p.r && p.t < q.b && q.t < p.b;
  };

  a.style = function (el) {
    try {
      return window.getComputedStyle(el);
    } catch (e) {
      return null;
    }
  };

  // Up from an element, and out of a shadow root to its element
  a.parentOf = function (el) {
    if (el.parentElement) return el.parentElement;
    var root = el.getRootNode ? el.getRootNode() : null;
    return root && root.host ? root.host : null;
  };

  a.shadowOf = function (el) {
    return el.openOrClosedShadowRoot || el.shadowRoot || null;
  };

  a.isFrame = function (el) {
    return el.tagName === 'IFRAME' || el.tagName === 'FRAME';
  };

  // What has the remote now (inside shadow roots too), or null
  a.focused = function () {
    var f = document.activeElement, s;
    while (f && (s = a.shadowOf(f)) && s.activeElement) f = s.activeElement;
    return f && f !== document.body && f !== document.documentElement ? f : null;
  };

  // Where an arrow starts when nothing on the screen has the remote: the screen's edge it moves away from
  a.edge = function (way) {
    var v = a.view();
    if (way === 'down') v.b = v.t;
    else if (way === 'up') v.t = v.b;
    else if (way === 'right') v.r = v.l;
    else v.l = v.r;
    return v;
  };

  // r lies that way from c: past c's edge (2 pixels of overlap forgiven, as the box's engine does)
  a.ahead = function (way, c, r) {
    if (way === 'left') return r.r <= c.l + 2 && r.l < c.l;
    if (way === 'right') return r.l >= c.r - 2 && r.r > c.r;
    if (way === 'up') return r.b <= c.t + 2 && r.t < c.t;
    return r.t >= c.b - 2 && r.b > c.b;
  };

  // How far r is from c, going that way: the box's engine's measure (the W3C's, from WICD). The straight line from
  // where the remote leaves c to where it comes into r, plus its length along the way and twice its length across,
  // less the overlap. So what's straight ahead wins over what's off to the side.
  a.distance = function (way, c, r) {
    var across = way === 'left' || way === 'right', x1, x2, y1, y2;
    if (across) {
      x1 = way === 'left' ? c.l : c.r;
      x2 = way === 'left' ? r.r : r.l;
      if (c.t > r.b) {
        y1 = c.t;
        y2 = r.b;
      } else if (r.t > c.b) {
        y1 = c.b;
        y2 = r.t;
      } else {
        y1 = y2 = Math.max(c.t, r.t);
      }
    } else {
      y1 = way === 'up' ? c.t : c.b;
      y2 = way === 'up' ? r.b : r.t;
      if (c.l > r.r) {
        x1 = c.l;
        x2 = r.r;
      } else if (r.l > c.r) {
        x1 = c.r;
        x2 = r.l;
      } else {
        x1 = x2 = Math.max(c.l, r.l);
      }
    }
    var dx = Math.abs(x1 - x2), dy = Math.abs(y1 - y2);
    var w = Math.max(0, Math.min(c.r, r.r) - Math.max(c.l, r.l)), h = Math.max(0, Math.min(c.b, r.b) - Math.max(c.t, r.t));
    return Math.sqrt(dx * dx + dy * dy) + (across ? dx : dy) + 2 * (across ? dy : dx) - Math.sqrt(w * h);
  };

  // More than a third of the screen past its edge, that way: the page scrolls towards it instead (so nothing
  // between is skipped)
  a.tooFar = function (way, r) {
    var v = a.view(), h = (v.b - v.t) / 3, w = (v.r - v.l) / 3;
    if (way === 'down') return r.t - v.b > h;
    if (way === 'up') return v.t - r.b > h;
    if (way === 'right') return r.l - v.r > w;
    return v.l - r.r > w;
  };
})(arrows);
