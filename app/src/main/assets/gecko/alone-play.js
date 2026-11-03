// The Twilight screens' Watch, its film started by itself (player-play.js, in every frame of the page): where. The
// page says which of its frames its player alone is in (player.js: play, a frame's number; 0: a player of the page's
// own), and they're told (the frames in them too, as the engine lists the tab's frames); a frame that comes later
// (another server's player, chosen in the row) asks (plays). Never another frame of the page: a trailer's. A fourth
// script of the background page (manifest.json), after alone.js: alone, plays.
'use strict';

// These frames, and the frames in them, by the engine's list of the tab's frames
function within(frames, roots) {
  var inside = roots.slice(), more = true;
  while (more) {
    more = false;
    frames.forEach(function (f) {
      if (inside.indexOf(f.frameId) < 0 && inside.indexOf(f.parentFrameId) >= 0) {
        inside.push(f.frameId);
        more = true;
      }
    });
  }
  return inside;
}

browser.runtime.onMessage.addListener(function (m, sender) {
  var tab = sender.tab ? sender.tab.id : -1;
  var no = function () { return false; };
  if (typeof m.play === 'number' && tab >= 0 && alone[tab]) {
    if (m.play === 0) return browser.tabs.sendMessage(tab, {play: true}, {frameId: 0}).then(function () { return true; }, no);
    plays[tab] = (plays[tab] || []).concat([m.play]);
    return browser.webNavigation.getAllFrames({tabId: tab}).then(function (frames) {
      within(frames || [], [m.play]).forEach(function (id) {
        browser.tabs.sendMessage(tab, {play: true}, {frameId: id}).then(null, no);
      });
      return true;
    }, no);
  }
  if (m.plays) {
    var roots = tab >= 0 ? plays[tab] || [] : [];
    if (!roots.length || typeof sender.frameId !== 'number') return Promise.resolve({play: false});
    return browser.webNavigation.getAllFrames({tabId: tab}).then(function (frames) {
      return {play: within(frames || [], roots).indexOf(sender.frameId) >= 0};
    }, function () {
      return {play: false};
    });
  }
  return undefined;
});
