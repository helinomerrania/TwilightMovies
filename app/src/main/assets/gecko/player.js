// The Twilight screens' Watch: on the website's page that plays the film, only its player, full screen; the rest of
// the page hidden (its player plays as on its own page, which is where it works). Only for a page the Twilight
// screens opened (the background script knows: alone.js), in this extension's own part of the page, its top frame.
// Each website's player: its frame, or its own video in the box that shows what goes over the film (subtitles);
// else the biggest video or frame there is that isn't a trailer. A website that loads its player only once one of
// its servers is chosen: the first one, chosen.
'use strict';
var player = {
  // host: what its player is (tag; src: its address; id; box: the class of the box around its video), what to press
  // first if it isn't there yet (press: its tag, with a class of its own or in a box with a class: the website's
  // first server, its player's start)
  SITES: {
    'krd.watch': {tag: 'VIDEO', box: /(^|\s)fixed(\s|$)/},
    'beenar.net': {tag: 'IFRAME', src: /\.uns\.bio\//},
    'www.kurd-movie.com': {tag: 'IFRAME', press: {tag: 'li', cls: 'dooplay_player_option'}},
    'www.kartonikurde.com': {tag: 'IFRAME'},
    'kurdsaz.com': {id: 'player-wrapper'},
    'kurdcinama.com': {},
    'marscinema.net': {tag: 'IFRAME', box: /(^|\s)aspect-backdrop(\s|$)/, press: {tag: 'button', in: 'aspect-backdrop'}},
  },
  TRAILER: /\/\/(www\.)?(youtube\.com|youtube-nocookie\.com|youtu\.be)\//,

  area: function (el) {
    var r = el.getBoundingClientRect();
    return Math.max(0, r.right - r.left) * Math.max(0, r.bottom - r.top);
  },

  // The player on this page by its website's recipe, or the biggest video or frame (not a trailer, not a speck)
  find: function (doc, host) {
    var site = player.SITES[host] || {};
    if (site.id) return doc.getElementById ? doc.getElementById(site.id) : null;
    var best = null;
    Array.prototype.forEach.call(doc.querySelectorAll('video, iframe'), function (el) {
      var src = el.getAttribute('src') || '';
      if (site.tag && el.tagName !== site.tag) return;
      if (site.src && !site.src.test(src)) return;
      if (el.tagName === 'IFRAME' && (player.TRAILER.test(src) || !/^(https?:)?\/\//.test(src))) return;
      if (player.area(el) < 320 * 180 && !(el.tagName === 'VIDEO' && site.tag === 'VIDEO')) return;
      if (!best || player.area(el) > player.area(best)) best = el;
    });
    if (!best || !site.box) return best;
    for (var p = best.parentElement; p && p !== doc.body; p = p.parentElement) {
      if (site.box.test(p.getAttribute('class') || '')) return p;
    }
    return best;
  },

  // A website's first server, or its player's start, pressed (the player comes once it is); not a trailer, which can
  // come first (Kurd Movie's does: player-option-trailer, data-nume="trailer")
  press: function (doc, host) {
    var how = (player.SITES[host] || {}).press;
    if (!how) return false;
    var has = function (el, cls) {
      return !!el && (' ' + (el.getAttribute('class') || '') + ' ').indexOf(' ' + cls + ' ') >= 0;
    };
    var first = Array.prototype.filter.call(doc.querySelectorAll(how.tag), function (el) {
      return (!how.cls || has(el, how.cls)) && (!how.in || has(el.parentElement, how.in))
        && !/trailer/i.test((el.getAttribute('id') || '') + ' ' + (el.getAttribute('data-nume') || ''));
    })[0];
    if (first) first.click();
    return !!first;
  },

  // The player alone: it (and what's in it) marked, the boxes around it too (so it can fill the screen), the page
  // marked; the background script's style (alone.js) does the rest
  isolate: function (doc, el) {
    doc.documentElement.setAttribute('data-tm-alone', '');
    el.setAttribute('data-tm-player', '');
    for (var p = el.parentElement; p; p = p.parentElement) p.setAttribute('data-tm-keep', '');
    var into = el.tagName === 'IFRAME' || el.tagName === 'VIDEO' ? el : el.querySelector('video, iframe') || el;
    try {
      into.focus({preventScroll: true});
    } catch (e) { /* not yet */ }
  },

  // From the page's start: asked whether it's the Twilight screens'; then looked for twice a second for two minutes
  // (players come late), and again whenever the website puts another in its place
  start: function () {
    if (window.top !== window || typeof browser === 'undefined') return;
    var host = location.host;
    browser.runtime.sendMessage({alone: host}).then(function (yes) {
      if (!yes) return;
      browser.runtime.sendMessage({aloneStyle: 1});
      var ticks = 0;
      var shown = null;
      var timer = setInterval(function () {
        if (++ticks > 240) clearInterval(timer);   // two minutes
        if (!document.body || (shown && document.contains(shown))) return;
        shown = player.find(document, host);
        if (shown) player.isolate(document, shown);
        else if (ticks === 8 || ticks === 30) player.press(document, host);   // four seconds, no player: its first server
      }, 500);
    }, function () {});
  },
};
player.start();
