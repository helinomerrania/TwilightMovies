// When the app leaves the screen: every video on the page pauses.
(function () {
  var v = document.getElementsByTagName('video');
  for (var i = 0; i < v.length; i++) {
    try {
      v[i].pause();
    } catch (e) {
      // a video that can't be paused now
    }
  }
})
