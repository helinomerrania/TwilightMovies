// The Twilight screens' scripts (app/src/main/assets/gecko/ui/), each file in a context of its own as the page
// loads them, and websites that answer from their saved copies (tests/js/fixtures/readers/<site>/), saying what
// they were asked.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { ROOT } = require('./scripts');

const UI = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko', 'ui');
const FIXTURES = path.join(__dirname, '..', 'fixtures', 'readers');

/** The scripts named (paths under ui/), loaded in order into one context: its TM. */
function load(files, extra) {
  const ctx = vm.createContext(Object.assign({ console, URL, URLSearchParams, setTimeout, clearTimeout }, extra || {}));
  for (const f of files) vm.runInContext(fs.readFileSync(path.join(UI, f), 'utf8'), ctx, { filename: f });
  return vm.runInContext('TM', ctx);
}

/** A website answering from its saved copies: routes are [pattern, file]; asked: what it was asked, in order. */
function saved(site, routes) {
  const asked = [];
  const answer = (url, o) => {
    asked.push(((o && o.method) || 'GET') + ' ' + url + (o && o.body ? ' ' + o.body : ''));
    const r = routes.find(([re]) => re.test(url));
    if (!r) return Promise.reject(new Error('no saved answer for ' + url));
    return Promise.resolve(fs.readFileSync(path.join(FIXTURES, site, r[1]), 'utf8'));
  };
  return { asked, text: (url, o) => answer(url, o), json: (url, o) => answer(url, o).then(JSON.parse) };
}

/** A copy without the fields not looked at, so a check reads plainly. */
const pick = (o, keys) => keys.reduce((out, k) => { if (o && o[k] !== undefined) out[k] = o[k]; return out; }, {});

module.exports = { UI, load, saved, pick };
