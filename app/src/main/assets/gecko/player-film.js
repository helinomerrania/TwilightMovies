// The Twilight screens' Watch, around its film (player.js asks): the frames its player alone is in told to start
// it (player-play.js, through alone-play.js); and how it ended (player-ends.js, through alone-play.js): its server
// failed, and the next one in the row is pressed; or a series' episode ended, and the next one plays.
'use strict';
var playerFilm = {
  // Its film started by itself (player-play.js, through alone-play.js): in the frames in the player alone, or by its
  // own video in the page; each told once (a frame that comes after, another server's player, asks)
  told: [],
  play: function (el) {
    var tell = function (id) {
      if (playerFilm.told.indexOf(id) >= 0) return;
      playerFilm.told.push(id);
      browser.runtime.sendMessage({play: id});
    };
    if (el.tagName === 'VIDEO' || el.querySelector('video')) tell(0);
    var frames = el.tagName === 'IFRAME' ? [el] : Array.prototype.slice.call(el.querySelectorAll('iframe'));
    frames.forEach(function (f) {
      var id = -1;
      try {
        id = browser.runtime.getFrameId(f);
      } catch (e) {
        // not one the engine knows yet: the next time
      }
      if (id > 0) tell(id);
    });
  },

  // Its server failed (player-ends.js saw it say so): the next one in the row pressed, each once. True if one was.
  tried: [],
  next: function (doc) {
    var row = doc.querySelector('[data-tm-servers]');
    var names = row ? Array.prototype.slice.call(row.querySelectorAll('button')) : [];
    var at = 0;
    for (var i = 0; i < names.length; i++) if (names[i].hasAttribute('data-tm-on')) at = i;
    if (playerFilm.tried.indexOf(at) < 0) playerFilm.tried.push(at);
    for (var k = 1; k < names.length; k++) {
      var j = (at + k) % names.length;
      if (playerFilm.tried.indexOf(j) >= 0) continue;
      playerFilm.tried.push(j);
      names[j].click();
      return true;
    }
    return false;
  },

  // A series' episode ended (player-ends.js): the next, in this one's place in the page's history (BACK goes back to
  // the Twilight screens still); its address only differs after # (Kurd Subtitle's): the page loaded again for it.
  // The background page told first: the next one's page is the Watch's too (its player alone, its film started).
  episode: function (url) {
    var same = url.split('#')[0] === location.href.split('#')[0];
    var go = function () {
      location.replace(url);
      if (same) location.reload();
    };
    browser.runtime.sendMessage({going: url}).then(go, go);
  },
};
