// Before the mouse pointer clicks: takes away see-through layers over the whole page that are there to catch
// the first click and open an ad (up to five, one over the other). Never a video, a frame, a picture or a
// button, a layer with words or pictures in it, or anything in a video player.
// nx, ny: the pointer, as fractions of the screen. Returns how many it took away.
(function (nx, ny) {
  var d = document, w = window, v = w.visualViewport,
    vw = v ? v.width : w.innerWidth, vh = v ? v.height : w.innerHeight,
    x = (v ? v.offsetLeft : 0) + nx * vw, y = (v ? v.offsetTop : 0) + ny * vh,
    n = 0, k, e, s, r, p, see;
  for (k = 0; k < 5; k++) {
    e = d.elementFromPoint(x, y);
    if (!e || e === d.body || e === d.documentElement) break;
    if (/^(VIDEO|IFRAME|EMBED|OBJECT|CANVAS|IMG|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(e.tagName)) break;
    s = w.getComputedStyle(e);
    r = e.getBoundingClientRect();
    if (s.position !== 'fixed' && s.position !== 'absolute') break;   // in its place on the page
    if (r.width * r.height < vw * vh * 0.4) break;                     // small: something to click
    see = parseFloat(s.opacity) < 0.1 ||
      ((s.backgroundColor === 'transparent' || /rgba\(.*,\s*0\)$/.test(s.backgroundColor)) &&
        (!s.backgroundImage || s.backgroundImage === 'none'));
    if (!see) break;
    if (e.querySelector && e.querySelector('video,iframe,img,canvas,svg,button,input,select,textarea')) break;
    if ((e.textContent || '').replace(/\s/g, '').length > 0) break;
    p = e.parentElement;
    while (p && p !== d.body && !(p.querySelector && p.querySelector('video'))) p = p.parentElement;
    if (p && p !== d.body) break;   // in a video player: its own controls
    e.parentNode.removeChild(e);
    n++;
  }
  return n;
})
