// For the tests of the servers' row (app/src/main/assets/gecko/player-servers.js): a stand-in page it can be made in
// (elements made, put in, taken out; a list's choice told), the row's names and marks, a name pressed, and the
// page's scripts as the Watch loads them (playerServers; the websites' recipes, player.SITES; player).
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { ROOT } = require('./scripts');
const { E } = require('./page');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const read = (f) => fs.readFileSync(path.join(DIR, f), 'utf8');

function able(x) {
  x.listeners = [];
  x.setAttribute = (n, v) => { x.attrs[n] = String(v); };
  x.removeAttribute = (n) => { delete x.attrs[n]; };
  x.querySelector = (q) => x.querySelectorAll(q)[0] || null;
  x.appendChild = (c) => { x.children.push(c); c.parentElement = x; };
  x.removeChild = (c) => { x.children.splice(x.children.indexOf(c), 1); c.parentElement = null; };
  x.addEventListener = (type, f) => x.listeners.push(f);
  x.dispatchEvent = (ev) => { x.calls.push(['event', ev.type, ev.bubbles]); return true; };
  return x;
}

function doc(body) {
  const html = E('html', {}, [E('body', {}, body)]);
  const d = { documentElement: html, body: html.children[0] };
  d.querySelectorAll = (s) => html.querySelectorAll(s);
  d.querySelector = (s) => d.querySelectorAll(s)[0] || null;
  d.createElement = (tag) => able(E(tag, { text: '' }));
  [html].concat(html.querySelectorAll('*')).forEach(able);
  return d;
}

/** More of the website's page, put in later (its other servers, shown) */
function add(parent, kids) {
  kids.forEach((k) => { [k].concat(k.querySelectorAll('*')).forEach(able); parent.appendChild(k); });
}

const row = (d) => d.querySelector('[data-tm-servers]');
const names = (d) => row(d).children.map((b) => b.textContent);
const marked = (d) => row(d).children.map((b) => 'data-tm-on' in b.attrs);
const pressRow = (d, i) => row(d).children[i].listeners.forEach((f) => f());

/** The page's scripts, fresh: {s: playerServers, SITES, player} */
function scripts() {
  const P = vm.createContext({ Array, String, window: {}, location: {},
    Event: function Event(type, o) { this.type = type; this.bubbles = !!(o && o.bubbles); } });
  vm.runInContext(read('player-servers.js'), P);
  vm.runInContext(read('player.js').replace('player.start();', ''), P);
  return { s: vm.runInContext('playerServers', P), SITES: vm.runInContext('player.SITES', P), player: vm.runInContext('player', P) };
}

module.exports = { read, doc, add, row, names, marked, pressRow, scripts };
