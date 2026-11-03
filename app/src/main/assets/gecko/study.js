// In the app's own study build only (-Pstudy, never on the boxes: the app says so, study, when it
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

// A film's own pieces (a player asks for them as data too): never held, they go straight to it
var MEDIA = /\.(ts|m4s|mp4|m4v|m4a|aac|mp3|webm|mkv|vtt|srt|jpe?g|png|webp|gif|woff2?)(\?|#|$)/i;

function studied(d) {
  if (d.tabId < 0 || String(d.originUrl || '').indexOf(OWN) === 0 || MEDIA.test(d.url)) return {};
  var sent = sentWith(d);
  var f = browser.webRequest.filterResponseData(d.requestId);
  var text = new TextDecoder('utf-8');
  var answer = '';
  var most = d.type === 'main_frame' ? 60000 : 12000;
  var kept = false;
  function keep() {
    if (kept) return;
    kept = true;
    console.info('Twilight Movies, data: ' + d.method + ' ' + String(d.url).slice(0, 200));
    if (port) {
      port.postMessage({studied: {page: d.documentUrl || d.originUrl || '', type: d.type, method: d.method, url: d.url, sent: sent,
        answer: answer.slice(0, most)}});
    }
  }
  // Its start read, and the rest let go by (a long answer, a film's piece, goes on at its own speed)
  f.ondata = function (e) {
    f.write(e.data);
    if (kept) return;
    answer += text.decode(e.data, {stream: true});
    if (answer.length >= most || answer.indexOf('\u0000') >= 0) {
      keep();
      f.disconnect();
    }
  };
  f.onstop = function () {
    keep();
    try {
      f.close();
    } catch (e) { /* let go of already */ }
  };
  f.onerror = function () {
    console.info('Twilight Movies, data: ' + d.method + ' ' + String(d.url).slice(0, 200) + ': ' + f.error);
  };
  return {};
}
