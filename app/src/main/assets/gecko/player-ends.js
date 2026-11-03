// The Watch's film, how it ends in its player (player-play.js, which starts it, asks): its server failed (the
// player says the film won't play, or its video can't load), and the page presses the next server in the row
// (player.js); or it played to its end, and a series' next episode plays (alone-play.js). Told to the background
// page, once each.
'use strict';
var playerEnds = {
  // What a server's player says when its film won't play (as the players write it; KRD WATCH's, in Kurdish)
  BROKEN: new RegExp(['under maintenance', 'blocked due to', 'cannot be played', 'could not be played', 'trouble playing',
    'error loading media', 'file (was|has been) (deleted|removed)', 'file not found', 'video not found', 'could not be found',
    'video (is )?(not available|unavailable)', 'has been removed', 'service unavailable', 'no server is available',
    'ناتوانرێت ڤیدیۆکە'].join('|'), 'i'),
  failed: false,
  ended: false,

  // Its server failed: its video can't load, or what the player shows says so. Told once; true if it's so.
  fails: function (scope, v) {
    var e = playerEnds;
    if (e.failed) return true;
    var shown = String((scope.innerText !== undefined ? scope.innerText : scope.textContent) || '').slice(0, 4000);
    var said = v && v.error ? 'its video, error ' + v.error.code : (e.BROKEN.exec(shown) || [''])[0];
    if (!said) return false;
    e.failed = true;
    console.info('Twilight Movies, play: ' + location.host + ' failed: ' + said);
    browser.runtime.sendMessage({failed: 1});
    return true;
  },

  // Its film played to its end (where: scope(), the player's); not an ad's (a film is minutes long). Told once.
  watch: function (doc, scope) {
    doc.addEventListener('ended', function (ev) {
      var v = ev.target, where = scope();
      if (playerEnds.ended || !v || v.tagName !== 'VIDEO' || !(v.duration >= 300) || !where || !where.contains(v)) return;
      playerEnds.ended = true;
      console.info('Twilight Movies, play: ' + location.host + ' ended');
      browser.runtime.sendMessage({ended: 1});
    }, true);
  },
};
