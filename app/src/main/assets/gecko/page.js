// In each page the app shows, in Firefox's engine, in this extension's own part of the page (the page's own
// scripts never see it): says the page is here (so the app's scripts run in it), and puts the app's bridge
// where the ad-hiding script looks for it, once the app has said its name. Its answers come as promises.
'use strict';
(function hello(tries) {
  browser.runtime.sendMessage({hello: 1}).then(function (r) {
    if (!r || !r.bridge) {
      if (tries < 5) setTimeout(function () { hello(tries + 1); }, 1000);
      return;
    }
    window[r.bridge] = {
      ask: function (what, a, b) {
        return browser.runtime.sendMessage({ask: String(what), a: String(a || ''), b: String(b || '')});
      }
    };
  }, function () {});
})(0);
