// The Twilight screens' Watch: on the website's page that plays the film, only its player, full screen; the rest of
// the page hidden (its player plays as on its own page, which is where it works). Only for a page the Twilight
// screens opened (the background script knows: alone.js), in this extension's own part of the page, its top frame.
// Each website's player: its frame, or its own video in the box that shows what goes over the film (subtitles);
// else the biggest video or frame there is that isn't a trailer. A website that loads its player only once one of
// its servers is chosen: the first one, chosen. A website with more than one server: their names over the player
// (player-servers.js), to choose another.
'use strict';
var player = {
  // host: what its player is (tag; src: its address; in: the class of the box it's in; id; box: the class of the box
  // around its video), what to press first if it isn't there yet (press: its tag, with a class of its own or in a box
  // with a class: the website's first server, its player's start), its servers (player-servers.js says how)
  SITES: {
    'krd.watch': {tag: 'VIDEO', box: /(^|\s)fixed(\s|$)/},
    'beenar.net': {tag: 'IFRAME', in: 'iframe-container',
      servers: {tag: 'button', cls: 'server-item', name: 'server-item-name', on: 'server-item--active'}},
    'www.kurd-movie.com': {tag: 'IFRAME', press: {tag: 'li', cls: 'dooplay_player_option'},
      servers: {tag: 'li', cls: 'dooplay_player_option', name: 'title', on: 'on'}},
    'www.kartonikurde.com': {tag: 'IFRAME', servers: {tag: 'button', attr: ['wire:click', /^selectServer\(/],
      notAttr: ['wire:click', /"requires_vip":\s*true/], on: 'theme-primary-bg'}},
    'kurdsaz.com': {id: 'player-wrapper', servers: {tag: 'button', cls: 'server-btn', on: 'active'}},
    'kurdcinama.com': {servers: {tag: 'option', in: ['quality-select', 'server-select']}},   // its list of servers
    // its own server's start; once its free hour of the day is over, the free servers it offers instead
    'marscinema.net': {tag: 'IFRAME', box: /(^|\s)aspect-backdrop(\s|$)/,
      press: [{tag: 'button', in: 'aspect-backdrop'}, {tag: 'button', words: 'سەیرکردن بە سێرڤەرە ئاساییەکان'}],
      servers: {tag: 'button', cls: 'group/server', name: 'truncate', reveal: 'بینینی سێرڤەرە ئاساییەکان'}},
    'kurdsubtitle.net': {id: 'player-frame', box: /(^|\s)rounded-2xl(\s|$)/, steps: true,   // its presses: player-steps.js
      servers: {tag: 'button', cls: 'text-right', in: 'basis-[25%]'}},
  },
  TRAILER: /\/\/(www\.)?(youtube\.com|youtube-nocookie\.com|youtu\.be)\//,

  area: function (el) {
    var r = el.getBoundingClientRect();
    return Math.max(0, r.right - r.left) * Math.max(0, r.bottom - r.top);
  },

  // The player on this page by its website's recipe (its own name, or what it is), else the biggest video or frame
  // (not a trailer, not a speck); in the box its website shows over it in, if it has one
  find: function (doc, host) {
    var site = player.SITES[host] || {};
    var best = null;
    if (site.id) best = doc.getElementById ? doc.getElementById(site.id) : null;
    else Array.prototype.forEach.call(doc.querySelectorAll('video, iframe'), function (el) {
      var src = el.getAttribute('src') || '';
      if (site.tag && el.tagName !== site.tag) return;
      if (site.src && !site.src.test(src)) return;
      if (site.in && !playerServers.has(el.parentElement, site.in)) return;
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

  // A website's first server, or its player's start, pressed (the player comes once it is): the first of its recipes
  // there is something for; not a trailer, which can come first (Kurd Movie's does: data-nume="trailer")
  press: function (doc, host) {
    var hows = [].concat((player.SITES[host] || {}).press || []);
    for (var i = 0; i < hows.length; i++) {
      var first = playerServers.list(doc, hows[i])[0];   // as its servers are found
      if (first) {
        first.click();
        return true;
      }
    }
    return false;
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

  // The episode the address asks for (#tm-play=<season>-<episode>), for a website where it's chosen on the page
  want: function (hash) {
    var m = /tm-play=(\d+)-(\d+)/.exec(hash || '');
    return m ? {season: Number(m[1]), n: Number(m[2])} : null;
  },

  // From the page's start: asked whether it's the Twilight screens'; then looked for twice a second for two minutes
  // (players come late), and again whenever the website puts another in its place. A website with steps of its own
  // (player-steps.js): those first, every two seconds, until they say its player is there. Once it shows, its film
  // started by itself (play), its servers' row (player-servers.js), and the player's frames told the remote goes up
  // to it (lift). Another server pressed there: its player looked for two minutes more, and nothing pressed for it.
  start: function () {
    if (window.top !== window || typeof browser === 'undefined') return;
    var host = location.host;
    var site = player.SITES[host] || {};
    browser.runtime.sendMessage({alone: host}).then(function (yes) {
      if (!yes) return;
      browser.runtime.sendMessage({aloneStyle: 1});
      browser.runtime.onMessage.addListener(function (m) {   // how its film ended (alone-play.js)
        if (m && m.serverFailed) playerFilm.next(document);
        if (m && typeof m.nextEpisode === 'string') playerFilm.episode(m.nextEpisode);
        return undefined;
      });
      var ticks = 0, shown = null, timer = null, lifted = false, chosen = false;
      var study = false;   // the study build (study.js): the page as drawn, now and then
      browser.runtime.sendMessage({studying: 1}).then(function (yes) { study = !!yes; }, function () {});
      var snapshot = function (step) {
        if (study) browser.runtime.sendMessage({snapshot: {url: location.href, step: step, html: document.documentElement.outerHTML}});
      };
      var again = function () {
        chosen = true;
        ticks = 0;
        if (!timer) timer = setInterval(tick, 500);
      };
      var tick = function () {
        if (++ticks > 240) {   // two minutes
          clearInterval(timer);
          timer = null;
        }
        if (ticks === 20 || ticks === 60 || ticks === 120 || ticks === 240) snapshot('after ' + ticks / 2 + ' seconds');
        if (!document.body) return;
        if (shown && document.contains(shown)) {
          playerFilm.play(shown);
          if (site.servers && playerServers.show(document, site.servers, again) && !lifted) {
            lifted = true;
            browser.runtime.sendMessage({lift: 1});
          }
          return;
        }
        if (site.steps && !chosen && !(ticks % 4 === 0 && playerSteps[host](document, player.want(location.hash)))) return;
        shown = player.find(document, host);
        if (shown) player.isolate(document, shown);
        else if (!chosen && (ticks === 8 || ticks === 30)) player.press(document, host);   // four seconds, no player: its first server
      };
      timer = setInterval(tick, 500);
    }, function () {});
  },
};
player.start();
