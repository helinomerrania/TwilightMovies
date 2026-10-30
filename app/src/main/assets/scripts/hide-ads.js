// Hides the page's ad boxes: first the site's own rules and the ones for every page, then the rules for the
// classes and ids the page has (".ad-box", "#banner"), asked for as they appear, a few dozen at a time.
// B: the name of the app's bridge (different on every start, so pages can't look for it). It answers with CSS
// only. Once a page, however often the app puts it in.
(function (B) {
  var w = window, d = document;
  if (!d || !d.documentElement || w.__twc === d) return;
  w.__twc = d;
  var b = w[B];
  if (!b) return;
  var done = {}, q = [], t = 0, h = location.hostname;

  function add(css) {
    if (!css) return;
    var s = d.createElement('style');
    s.textContent = css;
    (d.head || d.documentElement).appendChild(s);
  }

  function key(k) {
    if (!done[k]) {
      done[k] = 1;
      q.push(k);
    }
  }

  function one(e) {
    var i, c = e.classList;
    if (e.id) key('#' + e.id);
    if (c) for (i = 0; i < c.length; i++) key('.' + c[i]);
  }

  function soon() {
    if (!t) {
      t = 1;
      setTimeout(flush, 50);
    }
  }

  function scan(n) {
    if (!n || n.nodeType !== 1) return;
    one(n);
    var l = n.getElementsByTagName('*');
    for (var i = 0; i < l.length; i++) one(l[i]);
    soon();
  }

  function flush() {
    t = 0;
    if (!q.length) return;
    var k = q.join(' ');
    q = [];
    try {
      add(b.generic(h, k));
    } catch (x) {
      // the app is busy or gone: the boxes stay
    }
  }

  try {
    add(b.page(h));
  } catch (x) {
    // as above
  }
  scan(d.documentElement);
  if (w.MutationObserver) {
    new w.MutationObserver(function (m) {
      for (var i = 0; i < m.length; i++) {
        var r = m[i];
        if (r.type === 'attributes') {
          if (r.target.nodeType === 1) {
            one(r.target);
            soon();
          }
        } else {
          for (var j = 0; j < r.addedNodes.length; j++) scan(r.addedNodes[j]);
        }
      }
    }).observe(d.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id']});
  }
})
