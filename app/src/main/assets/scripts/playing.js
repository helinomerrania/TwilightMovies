// 1 while a video on the page is playing (the app keeps the TV awake), else 0.
(function () {
  var v = document.getElementsByTagName('video');
  for (var i = 0; i < v.length; i++) {
    var x = v[i];
    if (!x.paused && !x.ended && x.readyState > 2) return 1;
  }
  return 0;
})
