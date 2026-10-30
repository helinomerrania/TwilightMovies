// Scrolls whatever is under the mouse pointer: the innermost box that can scroll that way first, then the
// page. A row that snaps (a carousel) moves a whole step at a time.
// nx, ny: the pointer, and fx, fy: how far, all as fractions of the screen.
// Returns what moved: 'el' (a box), 'snap' (a snapping row), 'wait' (its last step is still moving) or 'win'.
(function (nx, ny, fx, fy) {
  var d = document, w = window, v = w.visualViewport,
    vw = v ? v.width : w.innerWidth, vh = v ? v.height : w.innerHeight,
    x = (v ? v.offsetLeft : 0) + nx * vw, y = (v ? v.offsetTop : 0) + ny * vh,
    dx = fx * vw, dy = fy * vh,
    e = d.elementFromPoint(x, y), re = /(auto|scroll|overlay)/;
  for (; e && e !== d.body && e !== d.documentElement; e = e.parentElement) {
    var s = getComputedStyle(e),
      cx = !!dx && re.test(s.overflowX) && e.scrollWidth > e.clientWidth + 1,
      cy = !!dy && re.test(s.overflowY) && e.scrollHeight > e.clientHeight + 1;
    if (!cx && !cy) continue;
    var sl = e.scrollLeft, st = e.scrollTop, snap = s.scrollSnapType && s.scrollSnapType !== 'none';
    if (snap && e.scrollBy) {
      var now = Date.now();
      if (e.__tvSnap && now - e.__tvSnap < 450) return 'wait';
      e.__tvSnap = now;
      e.scrollBy({
        left: cx ? (dx > 0 ? 1 : -1) * e.clientWidth * 0.8 : 0,
        top: cy ? (dy > 0 ? 1 : -1) * e.clientHeight * 0.8 : 0,
        behavior: 'smooth'
      });
      return 'snap';
    }
    if (cx) e.scrollLeft = sl + dx;
    if (cy) e.scrollTop = st + dy;
    if (e.scrollLeft !== sl || e.scrollTop !== st) return 'el';
    // at its end: the box around it tries
  }
  w.scrollBy(dx, dy);
  return 'win';
})
