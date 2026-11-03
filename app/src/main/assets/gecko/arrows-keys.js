// The remote's arrows (arrows.js says how they work): the keys. An arrow after the website's own listeners (unless
// the website used it, or it moves a field's caret); OK before them on a field, for the on-screen keyboard (or a
// list's picker); and OK on what the page only clicks on, a click when it comes up. Up in a player's frame, with
// the website's servers over the player (lift): before them, out to the servers.
'use strict';
(function (a) {
  var WAYS = {ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right'};
  var CODES = {37: 'left', 38: 'up', 39: 'right', 40: 'down'};
  var TYPED = /^(text|search|url|tel|email|password|number)$/;          // fields typed into
  var PICKED = /^(date|time|datetime-local|month|week|color|file)$/;    // fields with a picker of their own
  var typing = null;        // the field the keyboard was opened for
  var pressed = null;       // what OK went down on: it's clicked when OK comes up
  var swallowUp = false;    // OK opened the keyboard: its coming up isn't the page's either

  // A field typed into (not one that's only read)
  function typed(el) {
    if (el.isContentEditable) return true;
    if (el.readOnly) return false;
    if (el.tagName === 'TEXTAREA') return true;
    return el.tagName === 'INPUT' && TYPED.test(String(el.type).toLowerCase());
  }

  function filled(el) {
    return el.isContentEditable ? !!String(el.textContent || '').trim() : !!el.value;
  }

  // A field with a picker of its own: a list, a date
  function picked(el) {
    return el.tagName === 'SELECT' || (el.tagName === 'INPUT' && PICKED.test(String(el.type).toLowerCase()));
  }

  function pick(el) {
    try {
      el.showPicker();
    } catch (e) {
      el.click();
    }
  }

  // Something OK doesn't press by itself, so the remote clicks it: anything the page made able to have the remote
  // (a card, a tab), a box to tick. Links, buttons and fields OK presses by itself.
  function clicked(el) {
    var tag = el.tagName;
    if (tag === 'A' || tag === 'AREA') return !el.hasAttribute('href');
    if (/^(BUTTON|SUMMARY|SELECT|TEXTAREA|IFRAME|FRAME)$/.test(tag)) return false;
    if (tag === 'INPUT') return /^(checkbox|radio)$/.test(String(el.type).toLowerCase());
    return !el.isContentEditable;
  }

  // The caret is at the field's end that way (or the field is empty): the arrow leaves it
  function atEnd(el, way) {
    var v = el.value || '', s, e;
    if (!v) return true;
    try {
      s = el.selectionStart;
      e = el.selectionEnd;
    } catch (x) {
      return false;
    }
    if (typeof s !== 'number' || s !== e) return false;
    var st = a.style(el), rtl = !!st && st.direction === 'rtl';
    var back = way === 'up' || (way === 'left' && !rtl) || (way === 'right' && rtl);
    return back ? s === 0 : s === v.length;
  }

  // The arrow takes the remote away from el: not when it moves a field's caret, a slider, or a video's time
  function leaves(el, way) {
    var tag = el.tagName, across = way === 'left' || way === 'right';
    if (tag === 'INPUT') {
      var type = String(el.type).toLowerCase();
      if (type === 'range') return !across;
      return !TYPED.test(type) || !across || el.readOnly || atEnd(el, way);
    }
    if (tag === 'TEXTAREA') return atEnd(el, way);
    if (el.isContentEditable) return !across;
    return !((tag === 'VIDEO' || tag === 'AUDIO') && across);
  }

  function wayOf(e) {
    return WAYS[e.key] || CODES[e.keyCode] || null;
  }

  function isOk(e) {
    return e.key === 'Enter' || e.keyCode === 13;
  }

  function plain(e) {
    return !e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey;
  }

  function hold(e) {
    e.preventDefault();
    e.stopImmediatePropagation();
    swallowUp = true;
  }

  // Listened for again, so it's the last of the page's listeners: after the website's own, wherever it listens
  function last(type, f) {
    window.removeEventListener(type, f, false);
    window.addEventListener(type, f, false);
  }

  // After the website's own: an arrow, unless the website used it; OK, to click when it comes up
  function afterKeyDown(e) {
    if (!e.isTrusted || e.defaultPrevented || !plain(e)) return;
    var way = wayOf(e);
    if (way) {
      var el = a.focused();
      if ((!el || leaves(el, way)) && a.arrow(way)) e.preventDefault();
    } else if (isOk(e)) {
      var f = a.focused();
      pressed = f && clicked(f) ? f : null;
    }
  }

  function afterKeyUp(e) {
    var f = pressed;
    pressed = null;
    if (e.isTrusted && !e.defaultPrevented && isOk(e) && f && f === a.focused()) f.click();
  }

  // Before the website's own: OK on a field opens the keyboard (or a list's picker), and the page doesn't see it
  window.addEventListener('keydown', function (e) {
    if (!e.isTrusted || (!wayOf(e) && !isOk(e))) return;
    if (a.lift && !a.top && wayOf(e) === 'up' && plain(e)) {   // up to the website's servers, over its player
      e.preventDefault();
      e.stopImmediatePropagation();
      a.out('up');
      return;
    }
    last('keydown', afterKeyDown);
    if (!isOk(e)) return;
    swallowUp = false;
    var el = a.focused();
    if (!el || !plain(e)) return;
    if (typed(el) && !(typing === el && filled(el))) {
      typing = el;
      hold(e);
      a.send({keyboard: 1});
    } else if (picked(el)) {
      hold(e);
      pick(el);
    }
  }, true);

  window.addEventListener('keyup', function (e) {
    if (!e.isTrusted || !isOk(e)) return;
    if (swallowUp) {
      swallowUp = false;
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    last('keyup', afterKeyUp);
  }, true);

  window.addEventListener('focusin', function () {
    if (typing && a.focused() !== typing) typing = null;
  }, true);
})(arrows);
