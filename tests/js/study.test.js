// The extension's study.js (app/src/main/assets/gecko/): in the app's own try only, what each website's page asks its
// data feeds, written down a line each, with what it sends and the answer's start; the answers go on to the page as
// they came; the app's own pages, and the engine's own requests, never.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');

const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'gecko');
const said = [];
const passed = [];
let listener = null;
let options = null;
const filters = {};
const browser = {
  webRequest: {
    onBeforeRequest: { addListener: (f, filter, extra) => { listener = f; options = [filter, extra]; } },
    filterResponseData: (id) => (filters[id] = { write: (d) => passed.push(d), close() { this.closed = true; } }),
  },
};
const ctx = vm.createContext({ browser, TextDecoder, JSON, String, OWN: 'moz-extension://the-app/',
  console: { info: (m) => said.push(m) } });
vm.runInContext(fs.readFileSync(path.join(DIR, 'study.js'), 'utf8'), ctx);

check('nothing written down until the app says it\'s its own try', listener, null);
vm.runInContext('studyOn(); studyOn();', ctx);
check('...then every data request the pages make, held to read its answer, with what it sends', options,
  [{ urls: ['<all_urls>'], types: ['xmlhttprequest', 'main_frame'] }, ['blocking', 'requestBody']]);
const bytes = (t) => new TextEncoder().encode(t);
listener({ requestId: 7, tabId: 2, type: 'xmlhttprequest', method: 'POST', url: 'https://films.example/api/list', documentUrl: 'https://films.example/',
  originUrl: 'https://films.example/', requestBody: { raw: [{ bytes: bytes('{"page":1}').buffer }] } });
filters[7].ondata({ data: bytes('{"items":[1,2') });
filters[7].ondata({ data: bytes(',3]}') });
filters[7].onstop();
check('...the answer goes on to the page as it came', passed.map((d) => new TextDecoder().decode(d)).join(''), '{"items":[1,2,3]}');
check('...and a line in the log: the page, the address, what it sent, how many pieces its answer is in',
  JSON.parse(said[1].replace('Twilight Movies, data 1: ', '')),
  { page: 'https://films.example/', type: 'xmlhttprequest', method: 'POST', url: 'https://films.example/api/list', sent: '{"page":1}', pieces: 1 });
check('...then the answer, in pieces short enough for the log', said.slice(2), ['Twilight Movies, data 1.0: {"items":[1,2,3]}']);
listener({ requestId: 10, tabId: 2, type: 'main_frame', method: 'GET', url: 'https://films.example/api/all', originUrl: 'https://films.example/' });
filters[10].ondata({ data: bytes('x'.repeat(650)) });
filters[10].onstop();
check('...a page itself, long, in three', said.slice(3).map((l) => l.replace(/: x+$/, ': ' + l.split(': ')[1].length)),
  ['Twilight Movies, data 2: {"page":"https://films.example/","type":"main_frame","method":"GET","url":"https://films.example/api/all","sent":"","pieces":3}',
    'Twilight Movies, data 2.0: 300', 'Twilight Movies, data 2.1: 300', 'Twilight Movies, data 2.2: 50']);
check('never the app\'s own pages\' requests, nor the engine\'s own', [
  listener({ requestId: 8, tabId: 2, url: 'https://films.example/api', originUrl: 'moz-extension://the-app/ui/index.html' }),
  listener({ requestId: 9, tabId: -1, url: 'https://films.example/api' })], [{}, {}]);
ok('...nothing held for them', !filters[8] && !filters[9]);
