// Hiding the page's ad boxes (scripts/hide-ads.js), on a stand-in page with a stand-in for the app's bridge: what
// it asks the app, when, and what it puts in the page; answered at once (the box's own web engine) and later, as
// a promise (Firefox's).
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
function answer(question, host, keys) {
  if (question === 'page') return '.from-page{display:none!important}\n';
  return keys.split(' ').filter((k) => k === '.ad').map((k) => k + '{display:none!important}\n').join('');
}
// Firefox's engine answers later: a promise, kept here until the test says it comes (so it's checked in order)
function later(value, waiting) {
  return { then: (ok, fail) => waiting.push(() => (value instanceof Error ? fail(value) : ok(value))) };
}
function cosmeticPage(tree, slow) {
  const waiting = [];
  const styles = [];
  const calls = { page: [], generic: [], args: [] };
  let observer = null;
  const head = { appendChild: (s) => styles.push(s.textContent) };
  const doc = { documentElement: tree, head, createElement: () => ({ textContent: '' }) };
  const bridge = {};
  bridge.ask = function (question, host, keys) {
    calls.args.push(arguments.length);
    calls[question === 'page' ? 'page' : 'generic'].push(question === 'page' ? host : keys);
    const a = answer(question, host, keys);
    return slow ? later(a, waiting) : a;
  };
  const win = { _bridge: bridge };
  win.MutationObserver = function (cb) { observer = cb; this.observe = function () {}; };
  const ctx = vm.createContext({ window: win, document: doc, location: { hostname: 'films.example' }, setTimeout: (f) => { f(); return 1; } });
  const come = () => waiting.splice(0).forEach((f) => f());
  return { ctx, styles, calls, fire: (records) => observer && observer(records), win, come, waiting };
}
const C = (p) => run('hide-ads', ['_bridge'], p.ctx);
let page = cosmeticPage(el('HTML', '', [], [el('BODY', '', [], [el('DIV', 'top', ['ad', 'box']), el('DIV', '', ['film'])])]));
C(page);
check('the page\'s own CSS goes in first', page.styles[0], '.from-page{display:none!important}\n');
check('asked for the site', page.calls.page, ['films.example']);
check('every question has all three words (the WebView finds the method by how many)', page.calls.args, [3, 3]);
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
const bridge = page.win._bridge;
delete page.win._bridge;
let threw = false;
try { C(page); } catch (e) { threw = true; }
check('without the app\'s bridge, nothing happens (and nothing breaks)', [threw, page.styles.length], [false, 0]);
page.win._bridge = bridge;
C(page);
check('once the bridge is there, the next time works', page.styles.length > 0, true);

page = cosmeticPage(el('HTML', '', [], [el('DIV', '', ['ad'])]));
const own = page.win._bridge.ask;
page.win._bridge.ask = (q, h, k) => { if (q !== 'page') throw new Error('the app is busy'); return own(q, h, k); };
threw = false;
try { C(page); } catch (e) { threw = true; }
check('an app that can\'t answer breaks nothing', [threw, page.styles.length], [false, 1]);

page = cosmeticPage(el('HTML', '', [], [el('DIV', 'top', ['ad'])]), true);
C(page);
check('Firefox\'s engine: nothing yet while the app thinks', page.styles.length, 0);
page.come();
check('Firefox\'s engine: the answers go in when they come', page.styles.slice().sort(),
  ['.ad{display:none!important}\n', '.from-page{display:none!important}\n']);
page = cosmeticPage(el('HTML'), true);
page.win._bridge.ask = () => later(new Error('the app is gone'), page.waiting);
threw = false;
try { C(page); page.come(); } catch (e) { threw = true; }
check('Firefox\'s engine: an answer that fails breaks nothing', [threw, page.styles.length], [false, 0]);
