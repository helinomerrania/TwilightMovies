// The Twilight screens' Watch, the player alone (player.js): on a website with more than one server, their names in
// a thin row at the top of the screen, the app's own, the one playing marked (Beenar's own server may be down for
// maintenance while its others play). Pressing one presses the website's own: its player comes, alone again. The
// remote goes up to the row from the player: the arrows in the player's frames are told so (alone.js: lift).
'use strict';
var playerServers = {
  has: function (el, cls) {
    return !!el && !!el.getAttribute && (' ' + (el.getAttribute('class') || '') + ' ').indexOf(' ' + cls + ' ') >= 0;
  },
  text: function (el) {
    return String((el && el.textContent) || '').replace(/\s+/g, ' ').trim();
  },

  // The website's servers, by its recipe (servers: their tag, a class of their own or of the box they're in): not a
  // trailer (Kurd Movie's comes first)
  list: function (doc, how) {
    var s = playerServers;
    return Array.prototype.filter.call(doc.querySelectorAll(how.tag), function (el) {
      return (!how.cls || s.has(el, how.cls)) && (!how.in || s.has(el.parentElement, how.in))
        && !/trailer/i.test((el.getAttribute('id') || '') + ' ' + (el.getAttribute('data-nume') || ''));
    });
  },

  // Its name: the part with its name (how.name, a class), else all it says
  name: function (el, how) {
    var s = playerServers;
    var part = how.name ? Array.prototype.filter.call(el.querySelectorAll('*'), function (x) { return s.has(x, how.name); })[0] : null;
    return s.text(part || el) || '?';
  },

  // The one playing: the one pressed in the row, else the one the website marks (how.on, a class; or aria-pressed),
  // else the first (the one the Watch pressed)
  chosen: -1,
  playing: function (list, how) {
    if (playerServers.chosen >= 0 && playerServers.chosen < list.length) return playerServers.chosen;
    for (var i = 0; i < list.length; i++) {
      if ((how.on && playerServers.has(list[i], how.on)) || list[i].getAttribute('aria-pressed') === 'true') return i;
    }
    return 0;
  },

  // The row, made (or made again, when the website's servers changed), with the one playing marked; none with fewer
  // than two. pressed(i): one of them was pressed. True if the row shows.
  show: function (doc, how, pressed) {
    var s = playerServers;
    var list = s.list(doc, how);
    var names = list.map(function (el) { return s.name(el, how); });
    var row = doc.querySelector('[data-tm-servers]');
    if (list.length < 2) return false;
    if (!row || row.getAttribute('data-tm-names') !== names.join('|')) {
      if (row && row.parentElement) row.parentElement.removeChild(row);
      row = doc.createElement('div');
      row.setAttribute('data-tm-servers', '');
      row.setAttribute('data-tm-names', names.join('|'));
      names.forEach(function (n, i) {
        var b = doc.createElement('button');
        b.setAttribute('type', 'button');
        b.textContent = n;
        b.addEventListener('click', function () {
          var now = s.list(doc, how);
          if (!now[i]) return;
          s.chosen = i;
          now[i].click();
          s.mark(row, i);
          pressed(i);
        });
        row.appendChild(b);
      });
      doc.body.appendChild(row);
      doc.documentElement.setAttribute('data-tm-with-servers', '');
    }
    s.mark(row, s.playing(list, how));
    return true;
  },

  mark: function (row, at) {
    Array.prototype.forEach.call(row.querySelectorAll('button'), function (b, i) {
      if (i === at) b.setAttribute('data-tm-on', '');
      else b.removeAttribute('data-tm-on');
    });
  },
};
