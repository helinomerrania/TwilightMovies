// What the players on a page say (tests/try/probe.js asks it after each page): in every frame of the page, its
// videos (their address, whether they loaded, their error) and the words the frame shows, and what each video's file
// is made in, as a player reads it: its first 256 KB, and its last 4 MB too when what it's made in is kept at its end
// (moov). avc1: H.264 (its profile: 66 baseline, 77 main, 100 high, 110 high 10, which few boxes play; its level: 41 is
// 4.1), hvc1 or hev1: H.265, av01, vp09; mp4a: AAC sound, ac-3 or ec-3: Dolby's; its picture's size. A box that can't
// play that kind of film says it can't load it.
'use strict';
const KINDS = ['avc1', 'avc3', 'hvc1', 'hev1', 'av01', 'vp09', 'mp4a', 'ac-3', 'ec-3', 'Opus', 'fLaC', 'webm', 'ftyp', 'moov',
  '#EXTM3U'];

module.exports = async function players(page, name) {
  const out = [];
  for (const frame of page.frames()) {
    const seen = await frame.evaluate(async (kinds) => {
      const read = (bytes) => {
        const text = Array.from(bytes, (b) => (b > 31 && b < 127 ? String.fromCharCode(b) : '.')).join('');
        const found = kinds.filter((k) => text.includes(k));
        const word = (i) => ((bytes[i] << 24) >>> 0) + (bytes[i + 1] << 16) + (bytes[i + 2] << 8) + bytes[i + 3];
        const avc = text.indexOf('avcC');
        if (avc >= 0) found.push('profile ' + bytes[avc + 5] + ' level ' + bytes[avc + 7]);
        for (let at = text.indexOf('tkhd'); at >= 0; at = text.indexOf('tkhd', at + 4)) {
          const end = at - 4 + word(at - 4);   // its last 8 bytes: the picture's width and height
          if (end <= bytes.length && word(end - 8) > 0) found.push((word(end - 8) >>> 16) + 'x' + (word(end - 4) >>> 16));
        }
        return found;
      };
      const videos = Array.from(document.querySelectorAll('video')).map((v) => ({
        src: v.currentSrc || v.src || (v.querySelector('source') || {}).src || '', ready: v.readyState, network: v.networkState,
        error: v.error ? v.error.code + ' ' + (v.error.message || '') : '' }));
      for (const v of videos) {
        if (!/^https?:/.test(v.src)) continue;
        try {
          const ask = (range) => fetch(v.src, { headers: { Range: 'bytes=' + range }, credentials: 'include' });
          const r = await ask('0-262143');
          const found = read(new Uint8Array(await r.arrayBuffer()).slice(0, 262144));
          const size = Number((/\/(\d+)$/.exec(r.headers.get('content-range') || '') || [])[1] || 0);
          if (!found.includes('moov') && size > 262144) {
            const last = await ask(Math.max(262144, size - 4194304) + '-' + (size - 1));
            found.push('| its end:', ...read(new Uint8Array(await last.arrayBuffer())));
          }
          v.file = r.status + ' ' + r.headers.get('content-type') + ' ' + Math.round(size / 1048576) + ' MB ' + found.join(' ');
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
