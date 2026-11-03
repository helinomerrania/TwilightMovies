// What the players on a page say (tests/try/probe.js asks it after each page): in every frame of the page, its
// videos (their address, whether they loaded, their error) and the words the frame shows, and the start of each
// video's file (asked as a player asks for it, its first 256 KB), with what it's made in (avc1: H.264, hvc1 or
// hev1: H.265, av01, vp09; mp4a: AAC sound): a box that can't play that kind of film says it can't load it.
'use strict';
const KINDS = ['avc1', 'avc3', 'hvc1', 'hev1', 'av01', 'vp09', 'mp4a', 'ac-3', 'ec-3', 'Opus', 'webm', 'ftyp', '#EXTM3U'];

module.exports = async function players(page, name) {
  const out = [];
  for (const frame of page.frames()) {
    const seen = await frame.evaluate(async (kinds) => {
      const videos = Array.from(document.querySelectorAll('video')).map((v) => ({
        src: v.currentSrc || v.src || (v.querySelector('source') || {}).src || '', ready: v.readyState, network: v.networkState,
        error: v.error ? v.error.code + ' ' + (v.error.message || '') : '' }));
      for (const v of videos) {
        if (!/^https?:/.test(v.src)) continue;
        try {
          const r = await fetch(v.src, { headers: { Range: 'bytes=0-262143' }, credentials: 'include' });
          const bytes = new Uint8Array(await r.arrayBuffer()).slice(0, 262144);
          const text = Array.from(bytes, (b) => (b > 31 && b < 127 ? String.fromCharCode(b) : '.')).join('');
          v.file = r.status + ' ' + r.headers.get('content-type') + ' ' + kinds.filter((k) => text.includes(k)).join(' ');
        } catch (e) {
          v.file = String(e);
        }
      }
      return { url: location.href, videos, words: (document.body ? document.body.innerText : '').replace(/\s+/g, ' ').slice(0, 300) };
    }, KINDS).catch((e) => ({ url: frame.url(), error: String(e).split('\n')[0] }));
    out.push(seen);
  }
  return name + ' ' + JSON.stringify(out);
};
