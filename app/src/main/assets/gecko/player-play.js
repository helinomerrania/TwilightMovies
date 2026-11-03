// The Twilight screens' Watch: the film starts by itself as soon as its player can start it, with no OK pressed (a
// player's first click often opens an ad's window, and the click is lost on it: none is made). In the player
// alone's frames, and in the page itself for a player of its own (alone.js says where: play); never in another
// frame of the page (a trailer's). Its film: the biggest video there, played; or, while it has nothing to play yet
// (a player that loads its film only once it's started), the player started by its own scripts (JW Player's,
// Video.js's), else its own start pressed, else what's in the middle of the player (often a veil over it that takes
// the first press). Once its film plays, it's never started again by itself: the viewer may pause it.
'use strict';
var playerPlay = {
  // The big start of the players the websites use (JW Player, Video.js, Plyr, Flowplayer, Fluid, MediaElement,
  // DPlayer, ArtPlayer; Beenar's own, OK.ru's), and the ones that say so
  STARTS: '.jw-icon-display, .vjs-big-play-button, .plyr__control--overlaid, .fp-play, .fluid_initial_play, ' +
    '.mejs__overlay-button, .dplayer-mobile-play, .art-state, .netflix-play-button, .vp_video_stub, ' +
    '[aria-label="Play"], [aria-label="play"], [title="Play"]',
  on: false,      // told to start the film here
  done: false,    // its film played: never started again by itself
  tries: 0,       // seconds tried
  presses: 0,     // presses made (a few at most: a press on what plays pauses it)

  area: function (el) {
    var r = el.getBoundingClientRect();
    return Math.max(0, r.right - r.left) * Math.max(0, r.bottom - r.top);
  },

  // Where the film is: the frame's whole page; in the page itself, only its player alone
  scope: function (doc, top) {
    return top ? doc.querySelector('[data-tm-player]') : doc.body;
  },

  // The film: the biggest video there
  film: function (scope) {
    var best = scope.tagName === 'VIDEO' ? scope : null;
    Array.prototype.forEach.call(scope.querySelectorAll('video'), function (v) {
      if (!best || playerPlay.area(v) > playerPlay.area(best)) best = v;
    });
    return best;
  },

  // The page's player started as its own scripts would (JW Player's, Video.js's: the page's own, which this script
  // may reach in Firefox's engine): true if one was
  own: function (win) {
    var page = win.wrappedJSObject, done = false;
    if (!page) return false;
    try {
      if (typeof page.jwplayer === 'function' && page.jwplayer() && typeof page.jwplayer().play === 'function') {
        page.jwplayer().play();
        done = true;
      }
      if (page.videojs && typeof page.videojs.getPlayers === 'function') {
        var all = page.videojs.getPlayers();
        Object.keys(all).forEach(function (k) {
          if (all[k] && typeof all[k].play === 'function') {
            all[k].play();
            done = true;
          }
        });
      }
    } catch (e) {
      // not one it can start
    }
    return done;
  },

  // What to press to start it: the player's own start, if one shows; else what's in the middle of the player (the
  // frame's middle; in the page, its player's), not a link (an ad's)
  start: function (doc, scope, top) {
    var list = scope.querySelectorAll(playerPlay.STARTS);
    for (var i = 0; i < list.length; i++) if (playerPlay.area(list[i]) > 0) return list[i];
    var r = top ? scope.getBoundingClientRect() : {left: 0, top: 0, right: window.innerWidth, bottom: window.innerHeight};
    var hit = doc.elementFromPoint((r.left + r.right) / 2, (r.top + r.bottom) / 2);
    if (!hit || (hit !== scope && !scope.contains(hit)) || (hit.closest && hit.closest('a[href]'))) return null;
    return hit;
  },

  // A second's try: true once it's over (it plays, or long enough). Its film played if it has something to play;
  // else, once the frame has loaded, a press every third second (four at most)
  tick: function (doc, top) {
    var p = playerPlay, scope = p.scope(doc, top);
    if (!scope) return ++p.tries > 60;
    var v = p.film(scope);
    if (v && !v.paused && !v.ended) {
      p.done = true;
      console.info('Twilight Movies, play: ' + location.host + ' plays');
      return true;
    }
    p.tries++;
    if (v && (v.currentSrc || v.getAttribute('src')) && p.tries % 2 === 1) {
      var r = v.play();
      if (r && r.catch) r.catch(function (e) { console.info('Twilight Movies, play: ' + location.host + ' ' + e.name); });
    } else if (p.tries % 3 === 1 && p.presses < 4 && doc.readyState === 'complete') {   // once its player is drawn
      p.presses++;
      if (!top && p.own(window)) {
        console.info('Twilight Movies, play: ' + location.host + ' started its player');
        return false;
      }
      var b = p.start(doc, scope, top);
      if (b) {
        console.info('Twilight Movies, play: ' + location.host + ' pressed ' + String(b.tagName).toLowerCase() + '.' +
          String(b.getAttribute('class') || '').split(' ')[0]);
        b.click();
      }
    }
    return p.tries > 60;
  },

  go: function () {
    var p = playerPlay;
    if (p.on || p.done) return;
    p.on = true;
    var timer = setInterval(function () {
      if (document.body && p.tick(document, window.top === window)) clearInterval(timer);
    }, 1000);
  },
};

if (typeof browser !== 'undefined') {
  browser.runtime.onMessage.addListener(function (m) {
    if (m && m.play === true) playerPlay.go();
    return undefined;
  });
  if (window.top !== window) {   // a frame that comes after (a server's player, chosen in the row) asks
    browser.runtime.sendMessage({plays: 1}).then(function (r) {
      if (r && r.play === true) playerPlay.go();
    }, function () {});
  }
}
