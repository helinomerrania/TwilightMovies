// The Twilight screens' Watch, the player alone (player.js): on a website with more than one server, their names in
// a thin row at the top of the screen, the app's own, the one playing marked (Beenar's own server may be down for
// maintenance while its others play). As many as the website shows for that film or episode, read from its page each
// time (two, or ten: the row scrolls). Pressing one presses the website's own (or chooses it in the website's list):
// its player comes, alone again. The remote goes up to the row from the player (alone.js: lift).
'use strict';
var playerServers = {
  has: function (el, cls) {
    return !!el && !!el.getAttribute && (' ' + (el.getAttribute('class') || '') + ' ').indexOf(' ' + cls + ' ') >= 0;
  },
  text: function (el) {
    return String((el && el.textContent) || '').replace(/\s+/g, ' ').trim();
  },

  // The website's servers, by its recipe (servers: their tag, a class of their own or of the box they're in, words
  // they say, an attribute's value they have or haven't: [its name, a pattern]): not a trailer (Kurd Movie's comes
  // first), nor one the website keeps for its paying members (notAttr)
  list: function (doc, how) {
    var s = playerServers, ins = [].concat(how.in || []);
    var value = function (el, a) { return a ? el.getAttribute(a[0]) || '' : ''; };
    return Array.prototype.filter.call(doc.querySelectorAll(how.tag), function (el) {
      return (!how.cls || s.has(el, how.cls)) && (!ins.length || ins.some(function (c) { return s.has(el.parentElement, c); }))
        && (!how.words || s.text(el).indexOf(how.words) >= 0) && (!how.attr || how.attr[1].test(value(el, how.attr)))
        && !(how.notAttr && how.notAttr[1].test(value(el, how.notAttr)))
        && !/trailer/i.test((el.getAttribute('id') || '') + ' ' + (el.getAttribute('data-nume') || ''));
    });
  },

  // Its name: the part with its name (how.name, a class), else all it says; not the website's number before it
  name: function (el, how) {
    var s = playerServers;
    var part = how.name ? Array.prototype.filter.call(el.querySelectorAll('*'), function (x) { return s.has(x, how.name); })[0] : null;
    return s.text(part || el).replace(/^\d+\s*-\s*/, '') || '?';
  },

  // One pressed: a button clicked; a choice of the website's list chosen, as the viewer would (the list told: change)
  press: function (el) {
    if (el.tagName !== 'OPTION') return el.click();
    var list = el.parentElement;
    while (list && list.tagName !== 'SELECT') list = list.parentElement;
    if (!list) return undefined;
    list.value = el.value;
    return list.dispatchEvent(new Event('change', {bubbles: true}));
  },

  // The one playing: the one pressed in the row, else the one the website marks (how.on, a class; aria-pressed; its
  // list's choice), else the first (the one the Watch pressed)
  chosen: -1,
  playing: function (list, how) {
    if (playerServers.chosen >= 0 && playerServers.chosen < list.length) return playerServers.chosen;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if ((how.on && playerServers.has(el, how.on)) || el.getAttribute('aria-pressed') === 'true' || el.selected === true) return i;
    }
    return 0;
  },

  // The row, made (or made again, when the website's servers changed: another episode's), with the one playing
  // marked; none with fewer than two. A website that keeps some behind a button (how.reveal: the words it says): it's
  // pressed, once. pressed(i): one of them was pressed. True if the row shows.
  revealed: false,
  show: function (doc, how, pressed) {
    var s = playerServers;
    var more = how.reveal && !s.revealed ? s.list(doc, {tag: 'button', words: how.reveal})[0] : null;
    if (more) {
      s.revealed = true;
      more.click();
    }
    var list = s.list(doc, how);
    var names = list.map(function (el) { return s.name(el, how); });
    var row = doc.querySelector('[data-tm-servers]');
    if (list.length < 2) return false;
    if (!row || row.getAttribute('data-tm-names') !== names.join('|')) {
      if (row && row.parentElement) row.parentElement.removeChild(row);
      s.chosen = -1;
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
          s.press(now[i]);
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
