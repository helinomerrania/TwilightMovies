// The Twilight screens' Watch, on a website whose player comes only after a few presses (player.js asks it, every two
// seconds, until it says the player is there): Kurd Subtitle's. A series' episode is chosen on its page as the
// viewer would choose it: its season opened, then the episode (the address says which: #tm-play=<season>-<episode>);
// then one of the website's free servers (its own server needs signing in). Each call presses the next thing there is
// to press, if any, and says whether the player is there now, for the episode asked.
'use strict';
var playerSteps = {
  text: function (el) {
    return String((el && el.textContent) || '').replace(/\s+/g, ' ').trim();
  },
  has: function (el, cls) {
    return !!el && (' ' + (el.getAttribute('class') || '') + ' ').indexOf(' ' + cls + ' ') >= 0;
  },
  buttons: function (root, test) {
    return Array.prototype.filter.call(root.querySelectorAll('button'), test);
  },
  // The buttons with a part saying just these words (an episode's "2-3", or "ئەڵقەی 3")
  saying: function (root, words) {
    return playerSteps.buttons(root, function (b) {
      return Array.prototype.some.call(b.querySelectorAll('div'), function (d) { return playerSteps.text(d) === words; });
    });
  },
  opened: 0,   // how often a season was opened (once more if its episodes didn't come)

  'kurdsubtitle.net': function (doc, want) {
    var s = playerSteps;
    if (want) {
      var season = s.buttons(doc, function (b) {   // its number in a part of its own, then "N ئەڵقە"
        return !!b.children[0] && s.text(b.children[0]) === String(want.season) && /ئەڵقە/.test(s.text(b));
      })[0];
      if (!season) return false;
      var block = season.parentElement;
      var ep = s.saying(block, want.season + '-' + want.n)[0] || s.saying(block, 'ئەڵقەی ' + want.n)[0];
      if (!ep) {
        if (s.opened < 2 && !/چاوەڕوانبە/.test(s.text(block))) {   // not while its episodes come
          s.opened++;
          season.click();
        }
        return false;
      }
      if (!s.has(ep, 'text-primary') && !s.has(ep, 'bg-white/15')) {   // not the one chosen yet
        ep.click();
        return false;
      }
    }
    if (doc.getElementById('player-frame')) return true;
    var free = s.buttons(doc, function (b) { return s.text(b) === 'بینینی سێرڤەرە ئاساییەکان' && !b.disabled; })[0]
      || s.buttons(doc, function (b) { return s.has(b, 'py-4') && s.has(b, 'text-right') && s.has(b, 'px-6'); })[0];
    if (free) free.click();
    return false;
  },
};
