// The app inside Firefox's engine (web/gecko/Extension.java is the app's side). Every address a page loads, in
// any of its frames, is asked about first: the app's ad blocker answers load it, nothing, or a stand-in (a data:
// address). The app's scripts run in the page shown (in this extension's own part of it, which the page's
// scripts can't see), and what the ad-hiding script asks goes to the app. The remote's arrows (arrows.js, in
// every frame) go between a page's frames through here, and OK on a field asks the app for the keyboard.
'use strict';
var APP = 'twilight';
var tab = -1;       // the page shown: the app has one
var bridge = '';    // the name the ad-hiding script looks for the app under (page.js puts it there)
var port = null;

// The app answers with JSON text; if it can't answer within ms, {} (so a page never waits for ever)
function ask(m, ms) {
  return new Promise(function (resolve) {
    var t = setTimeout(function () { resolve({}); }, ms || 8000);
    browser.runtime.sendNativeMessage(APP, m).then(function (r) {
      clearTimeout(t);
      try {
        resolve(typeof r === 'string' ? JSON.parse(r) : r || {});
      } catch (e) {
        resolve({});
      }
    }, function () {
      clearTimeout(t);
      resolve({});
    });
  });
}

// The page itself is asked about as it opens (GeckoNavigation); the engine's own (tab -1) isn't the website's, nor
// what the Twilight screens ask the websites for (ui/: their lists, their pictures)
var OWN = browser.runtime.getURL('');
browser.webRequest.onBeforeRequest.addListener(function (d) {
  if (d.type === 'main_frame' || d.tabId < 0 || String(d.originUrl || d.documentUrl || '').indexOf(OWN) === 0) return {};
  var up = d.frameAncestors, page = d.frameId === 0 ? d.documentUrl : (up && up.length ? up[up.length - 1].url : '');
  return ask({check: d.url, type: d.type, frame: d.documentUrl || '', page: page || ''}).then(function (r) {
    return r || {};
  }, function () {
    return {};
  });
}, {urls: ['<all_urls>']}, ['blocking']);

function connect() {
  port = browser.runtime.connectNative(APP);
  port.onMessage.addListener(function (m) {
    if (typeof m.bridge === 'string') bridge = m.bridge;
    if (typeof m.run === 'number') run(m.run, m.code);
  });
  port.onDisconnect.addListener(function () {
    port = null;
    setTimeout(connect, 1000);
  });
}

// A script of the app's, in the page shown: its last value goes back as JSON, as Android's WebView gives it
function run(id, code) {
  function done(v) {
    if (port) port.postMessage({done: id, value: v === undefined ? null : JSON.stringify(v)});
  }
  if (tab < 0) return done(undefined);
  browser.tabs.executeScript(tab, {code: code, frameId: 0, runAt: 'document_start'}).then(function (r) {
    done(r && r.length ? r[0] : undefined);
  }, function () {
    done(undefined);
  });
}

browser.runtime.onMessage.addListener(function (m, sender) {
  if (m.hello) {
    if (sender.tab) tab = sender.tab.id;
    return Promise.resolve({bridge: bridge});
  }
  if (typeof m.ask === 'string') {
    return ask({ask: m.ask, a: m.a || '', b: m.b || ''}).then(function (r) {
      return r && r.value ? r.value : '';
    }, function () {
      return '';
    });
  }
  if (typeof m.arrows === 'string') return pass(m, sender);
  if (m.keyboard) {
    return ask({keyboard: 1}, 3000).then(function () {
      return true;
    });
  }
  return undefined;
});

// The remote from one frame of a page to another (arrows.js): into the frame named (in: is there something for it
// there?), or out to the frame around the sender (up). The frame's answer, or false if there's none.
function pass(m, sender) {
  if (!sender.tab) return Promise.resolve(false);
  var tabId = sender.tab.id;
  var to = !m.up ? Promise.resolve(m.frame) : browser.webNavigation.getFrame({tabId: tabId, frameId: sender.frameId})
    .then(function (f) {
      return f ? f.parentFrameId : -1;
    });
  return to.then(function (frameId) {
    if (typeof frameId !== 'number' || frameId < 0) return false;
    return browser.tabs.sendMessage(tabId, {arrows: m.arrows, way: m.way, rect: m.rect, wait: m.wait}, {frameId: frameId});
  }).then(function (r) {
    return r === undefined || r === null ? false : r;
  }, function (e) {
    console.info('Twilight Movies, the remote: frame ' + (m.up ? 'around ' + sender.frameId : m.frame) + ' not reached: ' + e);
    return false;
  });
}

connect();
