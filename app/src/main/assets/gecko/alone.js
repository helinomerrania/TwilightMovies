// The Twilight screens' Watch (with player.js in the page): the tab whose page the Twilight screens opened, with the
// website it's on, so its player shows alone; and the style that shows it so: everything of the page hidden but the
// player, which fills the screen (below the row of the website's servers, if it has more than one: player-servers.js;
// the remote goes up to it from the player's frames, which are told so: lift). The tab is the Twilight screens' until
// the app opens another page in it (a page the website opens itself, on its own domain, stays so). A third script of
// the background page (manifest.json), after background.js: OWN is the extension's own pages' address.
'use strict';
var alone = {};    // a tab's number: the host its page is on
var lifted = {};   // a tab's number: its page shows its servers' row over the player
var plays = {};    // a tab's number: the frames its player alone is in, where its film starts by itself (alone-play.js)
var next = {};     // a tab's number: a series' episodes after the one playing, from the Twilight screens (alone-play.js)
var advanced = {}; // a tab's number: the episode it went on to by itself, for the screens to keep (alone-play.js)
var going = {};    // a tab's number: the next episode's address its page goes to by itself (alone-play.js): the Watch's still

var ALONE_STYLE = 'html[data-tm-alone], html[data-tm-alone] body { overflow: hidden !important; background: #000 !important; }\n' +
  'html[data-tm-alone] body * { visibility: hidden !important; }\n' +
  'html[data-tm-alone] [data-tm-player], html[data-tm-alone] [data-tm-player] * { visibility: visible !important; }\n' +
  'html[data-tm-alone] [data-tm-player] { position: fixed !important; inset: 0 !important; top: 0 !important; left: 0 !important;' +
  ' width: 100vw !important; height: 100vh !important; max-width: none !important; max-height: none !important;' +
  ' min-width: 0 !important; min-height: 0 !important; margin: 0 !important; padding: 0 !important; border: 0 !important;' +
  ' border-radius: 0 !important; transform: none !important; z-index: 2147483647 !important; background: #000 !important; }\n' +
  'html[data-tm-alone] [data-tm-keep] { transform: none !important; filter: none !important; perspective: none !important;' +
  ' contain: none !important; will-change: auto !important; backdrop-filter: none !important; }\n' +
  'html[data-tm-alone] video[data-tm-player], html[data-tm-alone] [data-tm-player] video { object-fit: contain !important; }\n' +
  'html[data-tm-alone][data-tm-with-servers] [data-tm-player] { top: 7vh !important; height: 93vh !important; }\n' +
  'html[data-tm-alone] [data-tm-servers], html[data-tm-alone] [data-tm-servers] * { visibility: visible !important; }\n' +
  'html[data-tm-alone] [data-tm-servers] { position: fixed !important; top: 0 !important; left: 0 !important; right: 0 !important;' +
  ' height: 7vh !important; margin: 0 !important; padding: 0 2vh !important; box-sizing: border-box !important;' +
  ' display: flex !important; align-items: center !important; justify-content: safe center !important; gap: 1vh !important;' +
  ' overflow-x: auto !important; overflow-y: hidden !important; scrollbar-width: none !important;' +   // many: it scrolls
  ' background: #000 !important; z-index: 2147483647 !important; }\n' +
  'html[data-tm-alone] [data-tm-servers] button { -moz-appearance: none !important; appearance: none !important;' +   // not Android's own look
  ' flex: none !important; margin: 0 !important; padding: 0.9vh 2.2vh !important; border: 0 !important;' +
  ' border-radius: 999px !important; background: transparent !important; color: #8c8c8c !important; font: inherit !important;' +
  ' font-size: 2.6vh !important; line-height: 1.2 !important; white-space: nowrap !important; }\n' +
  'html[data-tm-alone] [data-tm-servers] button[data-tm-on] { color: #fff !important; background: rgba(255, 255, 255, 0.14) !important; }\n';

// Two hosts of the same website (www.kurd-movie.com, kurd-movie.com): their last two names the same
function sameSite(a, b) {
  var last = function (h) { return String(h || '').split('.').slice(-2).join('.'); };
  return !!a && !!b && last(a) === last(b);
}

function hostOf(url) {
  try {
    return new URL(url).host;
  } catch (e) {
    return '';
  }
}

/** A page opening in a tab (background.js, before it's asked about): the Twilight screens', or no longer. */
function aloneFor(d) {
  if (d.tabId < 0) return;
  delete lifted[d.tabId];
  delete plays[d.tabId];
  var from = String(d.originUrl || '');
  var next1 = !!going[d.tabId] && String(d.url).split('#')[0] === going[d.tabId].split('#')[0];
  delete going[d.tabId];
  if (from.indexOf(OWN) === 0 || next1) alone[d.tabId] = hostOf(d.url);
  else if (alone[d.tabId] && !sameSite(hostOf(from), alone[d.tabId])) delete alone[d.tabId];
  if (!alone[d.tabId]) delete next[d.tabId];
  watching(d.tabId);
}

// The app told whether the page shown is the Watch's: a window it opens then is an ad's, dropped without a word
function watching(tab) {
  if (typeof port !== 'undefined' && port) port.postMessage({watch: !!alone[tab]});
}

browser.runtime.onMessage.addListener(function (m, sender) {
  var tab = sender.tab ? sender.tab.id : -1;
  if (typeof m.alone === 'string' && tab >= 0 && typeof studying !== 'undefined' && studying
      && /#tm-(play|alone)\b/.test(String(sender.url || ''))) {
    alone[tab] = m.alone;   // the study build (study.js): an address as the screens open one, opened by the study
    watching(tab);
  }
  if (typeof m.alone === 'string') return Promise.resolve(tab >= 0 && sameSite(m.alone, alone[tab]));
  if (m.lift && tab >= 0 && alone[tab]) {   // the servers' row shows: every frame of the page told
    lifted[tab] = true;
    return browser.tabs.sendMessage(tab, {arrows: 'lift'}).then(function () {
      return true;
    }, function () {
      return false;
    });
  }
  if (m.lifted) return Promise.resolve({lift: tab >= 0 && !!lifted[tab]});   // a frame that came later asks
  if (m.aloneStyle && tab >= 0 && alone[tab]) {
    return browser.tabs.insertCSS(tab, {code: ALONE_STYLE, cssOrigin: 'user', frameId: 0}).then(function () {
      return true;
    }, function () {
      return false;
    });
  }
  return undefined;
});
