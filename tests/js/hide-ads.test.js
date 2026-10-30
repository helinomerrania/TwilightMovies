// Hiding the page's ad boxes (scripts/hide-ads.js), on a stand-in page with a stand-in for the app's bridge: what
// it asks the app, when, and what it puts in the page.
'use strict';
const vm = require('vm');
const { check } = require('./lib/check');
const { run } = require('./lib/scripts');

function el(tag, id, classes, children) {
  const e = { nodeType: 1, tagName: tag, id: id || '', classList: classes || [], children: children || [], parentElement: null };
  e.getElementsByTagName = () => {
    const out = [];
    (function walk(n) { for (const c of n.children) { out.push(c); walk(c); } })(e);
    return out;
  };
  for (const c of e.children) c.parentElement = e;
  return e;
}
function cosmeticPage(tree) {
  const styles = [];
  const calls = { page: [], generic: [] };
  let observer = null;
  const head = { appendChild: (s) => styles.push(s.textContent) };
  const doc = { documentElement: tree, head, createElement: () => ({ textContent: '' }) };
  const bridge = {
    page: (h) => { calls.page.push(h); return '.from-page{display:none!important}\n'; },
    generic: (h, keys) => { calls.generic.push(keys); return keys.split(' ').filter((k) => k === '.ad').map((k) => k + '{display:none!important}\n').join(''); },
  };
  const win = { _bridge: bridge };
  win.MutationObserver = function (cb) { observer = cb; this.observe = function () {}; };
  const ctx = vm.createContext({ window: win, document: doc, location: { hostname: 'films.example' }, setTimeout: (f) => { f(); return 1; } });
  return { ctx, styles, calls, fire: (records) => observer && observer(records), win };
}
const C = (p) => run('hide-ads', ['_bridge'], p.ctx);
let page = cosmeticPage(el('HTML', '', [], [el('BODY', '', [], [el('DIV', 'top', ['ad', 'box']), el('DIV', '', ['film'])])]));
C(page);
check('the page\'s own CSS goes in first', page.styles[0], '.from-page{display:none!important}\n');
check('asked for the site', page.calls.page, ['films.example']);
check('the classes and ids on the page are asked about', page.calls.generic[0].split(' ').sort(), ['#top', '.ad', '.box', '.film']);
check('the generic answer goes in', page.styles[1], '.ad{display:none!important}\n');
C(page);
check('a second time on the same page does nothing', page.calls.page.length, 1);
page.fire([{ type: 'childList', addedNodes: [el('SECTION', 'more', ['ad', 'promo'])] }]);
check('new elements: only new names are asked about', page.calls.generic[1].split(' ').sort(), ['#more', '.promo']);
page.fire([{ type: 'attributes', target: el('DIV', '', ['film', 'late-ad']), addedNodes: [] }]);
check('a changed class is asked about', page.calls.generic[2], '.late-ad');
page.fire([{ type: 'childList', addedNodes: [{ nodeType: 3 }] }]);
check('text changes ask nothing', page.calls.generic.length, 3);
page = cosmeticPage(el('HTML'));
delete page.win._bridge;
let threw = false;
try { C(page); } catch (e) { threw = true; }
check('without the app\'s bridge, nothing happens (and nothing breaks)', [threw, page.styles.length], [false, 0]);
page = cosmeticPage(el('HTML', '', [], [el('DIV', '', ['ad'])]));
page.win._bridge.generic = () => { throw new Error('the app is busy'); };
threw = false;
try { C(page); } catch (e) { threw = true; }
check('an app that can\'t answer breaks nothing', [threw, page.styles.length], [false, 1]);
