// In the app's own try only (its pages' messages in Android's log, never on the boxes: the app says so, study, when it
// connects): what each website's page asks its data feeds, with what it sends and the start of each answer, one line
// each in the log ("Twilight Movies, data: {...}"), for the Twilight screens' readers (tests/try/study.sh). The
// answers go on to the page as they came. A second script of the background page (manifest.json), after
// background.js: OWN is its own pages' address.
'use strict';
var studying = false;

function studyOn() {
  if (studying) return;
  studying = true;
  browser.webRequest.onBeforeRequest.addListener(studied, {urls: ['<all_urls>'], types: ['xmlhttprequest']}, ['blocking', 'requestBody']);
  console.info('Twilight Movies, data: the websites\' data feeds are written down');
}

function sentWith(d) {
  var b = d.requestBody;
  if (!b) return '';
  if (b.formData) return JSON.stringify(b.formData).slice(0, 800);
  if (b.raw && b.raw[0] && b.raw[0].bytes) return new TextDecoder('utf-8').decode(b.raw[0].bytes).slice(0, 800);
  return '';
}

function studied(d) {
  if (d.tabId < 0 || String(d.originUrl || '').indexOf(OWN) === 0) return {};
  var sent = sentWith(d);
  var f = browser.webRequest.filterResponseData(d.requestId);
  var text = new TextDecoder('utf-8');
  var answer = '';
  f.ondata = function (e) {
    if (answer.length < 2000) answer += text.decode(e.data, {stream: true});
    f.write(e.data);
  };
  f.onstop = function () {
    f.close();
    console.info('Twilight Movies, data: ' + JSON.stringify({page: d.documentUrl || d.originUrl || '', method: d.method, url: d.url,
      sent: sent, answer: answer.slice(0, 2000)}));
  };
  f.onerror = function () {
    console.info('Twilight Movies, data: ' + JSON.stringify({url: d.url, error: f.error}));
  };
  return {};
}
