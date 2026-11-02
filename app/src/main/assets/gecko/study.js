// In the app's own try only (its pages' messages in Android's log, never on the boxes: the app says so, study, when it
// connects): what each website's page asks its data feeds, with what it sends and each answer (its first 12000
// letters), and each page itself (its first 60000: a website that sends its films in its pages), for the Twilight
// screens' readers (tests/try/study.sh). Each goes to the app, which keeps it in a file (web/gecko/StudyFile), with a
// short line in the log; the answers go on to the page as they came. A second script of the background page
// (manifest.json), after background.js: OWN is its own pages' address, port the app's.
'use strict';
var studying = false;

function studyOn() {
  if (studying) return;
  studying = true;
  browser.webRequest.onBeforeRequest.addListener(studied, {urls: ['<all_urls>'], types: ['xmlhttprequest', 'main_frame']},
    ['blocking', 'requestBody']);
  console.info('Twilight Movies, data: the websites\' data feeds are kept');
}

function sentWith(d) {
  var b = d.requestBody;
  if (!b) return '';
  if (b.formData) return JSON.stringify(b.formData).slice(0, 2000);
  if (b.raw && b.raw[0] && b.raw[0].bytes) return new TextDecoder('utf-8').decode(b.raw[0].bytes).slice(0, 2000);
  return '';
}

function studied(d) {
  if (d.tabId < 0 || String(d.originUrl || '').indexOf(OWN) === 0) return {};
  var sent = sentWith(d);
  var f = browser.webRequest.filterResponseData(d.requestId);
  var text = new TextDecoder('utf-8');
  var answer = '';
  var most = d.type === 'main_frame' ? 60000 : 12000;
  f.ondata = function (e) {
    if (answer.length < most) answer += text.decode(e.data, {stream: true});
    f.write(e.data);
  };
  f.onstop = function () {
    f.close();
    console.info('Twilight Movies, data: ' + d.method + ' ' + String(d.url).slice(0, 200));
    if (port) {
      port.postMessage({studied: {page: d.documentUrl || d.originUrl || '', type: d.type, method: d.method, url: d.url, sent: sent,
        answer: answer.slice(0, most)}});
    }
  };
  f.onerror = function () {
    console.info('Twilight Movies, data: ' + d.method + ' ' + String(d.url).slice(0, 200) + ': ' + f.error);
  };
  return {};
}
