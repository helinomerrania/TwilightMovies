// The extension's study.js (app/src/main/assets/gecko/): in the app's own try only, what each website's page asks its
// data feeds (and each page itself), with what it sends and the answer, to the app to keep, a short line in the log;
// the answers go on to the page as they came; the app's own pages, and the engine's own requests, never.
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
    filterResponseData: (id) => (filters[id] = { write: (d) => passed.push(d), close() { this.closed = true; },
      disconnect() { this.disconnected = true; } }),
  },
};
const kept = [];
const ctx = vm.createContext({ browser, TextDecoder, JSON, String, OWN: 'moz-extension://the-app/',
  port: { postMessage: (m) => kept.push(m) }, console: { info: (m) => said.push(m) } });
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
check('...and to the app to keep: the page, the address, what it sent, the answer', kept[0], { studied: { page: 'https://films.example/',
  type: 'xmlhttprequest', method: 'POST', url: 'https://films.example/api/list', sent: '{"page":1}', answer: '{"items":[1,2,3]}' } });
check('...a short line in the log', said[1], 'Twilight Movies, data: POST https://films.example/api/list');
listener({ requestId: 10, tabId: 2, type: 'main_frame', method: 'GET', url: 'https://films.example/films', originUrl: 'https://films.example/' });
filters[10].ondata({ data: bytes('<html>' + 'x'.repeat(450000)) });
filters[10].onstop();
check('...a page itself too: its first 400000 letters, and the rest let go by', [kept[1].studied.type, kept[1].studied.answer.length,
  !!filters[10].disconnected], ['main_frame', 400000, true]);
check('a film\'s pieces: never held', [listener({ requestId: 11, tabId: 2, type: 'xmlhttprequest', url: 'https://cdn.example/seg-12.ts?t=1',
  originUrl: 'https://films.example/' }), !!filters[11]], [{}, false]);
check('never the app\'s own pages\' requests, nor the engine\'s own', [
  listener({ requestId: 8, tabId: 2, url: 'https://films.example/api', originUrl: 'moz-extension://the-app/ui/index.html' }),
  listener({ requestId: 9, tabId: -1, url: 'https://films.example/api' })], [{}, {}]);
ok('...nothing held for them', !filters[8] && !filters[9]);
