// The remote's play/pause and seek keys, for the page's main video: the one full screen, else one that plays,
// else the biggest. The page gets each key first; the app asks this before and after:
//   'q'  note how the video is (returns 'ok'; or 'none': no video, or, with strict=1, a link, a button or a
//        field has the focus, so OK is theirs)
//   't'  play or pause it, unless the page already did since 'q' (then 'page')
//   's'  seek n seconds, unless the page already did since 'q' (then 'page')
//   'p'  play, 'z' pause
(function (a, n, strict) {
  var d = document, w = window, f = d.fullscreenElement || d.webkitFullscreenElement || null,
    v = null, s = w.__tvm, i, x, r, ar, b = 0, vs;

  function pick() {
    if (f) v = f.tagName === 'VIDEO' ? f : (f.querySelector ? f.querySelector('video') : null);
    if (!v) {
      vs = d.getElementsByTagName('video');
      for (i = 0; i < vs.length; i++) {
        x = vs[i];
        r = x.getBoundingClientRect();
        ar = r.width * r.height;
        if (ar > 0 && !x.paused && !x.ended) {
          v = x;
          break;
        }
        if (ar > b) {
          b = ar;
          v = x;
        }
      }
    }
  }

  function play() {
    var p = v.play();
    if (p && p['catch']) p['catch'](function () {});
  }

  if (a === 'q') {
    pick();
    var ae = d.activeElement;
    if (!v || (strict && ae && ae !== v && ae !== f && /^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(ae.tagName))) {
      w.__tvm = null;
      return 'none';
    }
    w.__tvm = {v: v, p: v.paused, t: v.currentTime, at: Date.now()};
    return 'ok';
  }
  if (a === 't' || a === 's') {
    if (!s || !s.v || !d.contains(s.v)) return 'none';
    v = s.v;
    w.__tvm = null;
    if (a === 't') {
      if (v.paused !== s.p) return 'page';
      if (v.paused || v.ended) play();
      else v.pause();
      return v.paused ? 'paused' : 'playing';
    }
    var el = (Date.now() - s.at) / 1000, exp = s.t + (v.paused ? 0 : el * (v.playbackRate || 1));
    if (Math.abs(v.currentTime - exp) > 2.5) return 'page';
    var t = v.currentTime + n;
    if (isFinite(v.duration) && v.duration > 0) t = Math.min(t, v.duration - 1);
    v.currentTime = Math.max(0, t);
    return 'seeked';
  }
  pick();
  if (!v) return 'none';
  if (a === 'p') {
    if (v.paused) play();
    return 'playing';
  }
  if (a === 'z') {
    if (!v.paused) v.pause();
    return 'paused';
  }
  return 'none';
})
