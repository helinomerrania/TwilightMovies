// The app's texts (app/src/main/res/values*/strings.xml): every text the code asks for exists, none is left
// unused, and a translation (Kurdish: values-b+ckb) has the same names and keeps each %1$s / %1$d exactly, so a
// translated menu can't break the app or the build.
'use strict';
const fs = require('fs');
const path = require('path');
const { ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');

const RES = path.join(ROOT, 'app', 'src', 'main', 'res');
const JAVA = path.join(ROOT, 'app', 'src', 'main', 'java');

function strings(file) {
  const xml = fs.readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const out = new Map();
  const dupes = [];
  const re = /<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(xml))) {
    if (out.has(m[1])) dupes.push(m[1]);
    out.set(m[1], { text: m[3], translatable: !/translatable="false"/.test(m[2]) });
  }
  return { out, dupes };
}

function placeholders(text) {
  return (text.match(/%(\d+\$)?[sd]|%%/g) || []).sort().join(' ');
}

function unescapedApostrophe(text) {
  const t = text.trim();
  if (t.startsWith('"') && t.endsWith('"')) return false;   // a quoted string may hold ' as it is
  return /(^|[^\\])'/.test(t);
}

function javaFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((d) =>
    d.isDirectory() ? javaFiles(path.join(dir, d.name)) : d.name.endsWith('.java') ? [path.join(dir, d.name)] : []);
}

const base = strings(path.join(RES, 'values', 'strings.xml'));
ok('the English texts have no name twice', base.dupes.length === 0, base.dupes.join(', '));
for (const [name, s] of base.out) {
  if (unescapedApostrophe(s.text)) ok('English "' + name + '" writes its apostrophes as \\\'', false, s.text);
}

const code = javaFiles(JAVA).map((f) => fs.readFileSync(f, 'utf8')).join('\n')
  + fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'AndroidManifest.xml'), 'utf8');
const used = new Set((code.match(/(?<!android\.)R\.string\.(\w+)|@string\/(\w+)/g) || []).map((x) => x.replace(/^R\.string\.|^@string\//, '')));
const missing = [...used].filter((n) => !base.out.has(n));
ok('every text the app asks for is in strings.xml', missing.length === 0, missing.join(', '));
const unused = [...base.out.keys()].filter((n) => !used.has(n));
ok('every text in strings.xml is used by the app', unused.length === 0, unused.join(', '));

const translations = fs.readdirSync(RES).filter((d) => /^values-/.test(d) && fs.existsSync(path.join(RES, d, 'strings.xml')));
console.log('  (translations: ' + (translations.length ? translations.join(', ') : 'none yet') + ')');
for (const dir of translations) {
  const tr = strings(path.join(RES, dir, 'strings.xml'));
  ok(dir + ': no name twice', tr.dupes.length === 0, tr.dupes.join(', '));
  const extra = [...tr.out.keys()].filter((n) => !base.out.has(n));
  ok(dir + ': only names the English file has', extra.length === 0, extra.join(', '));
  ok(dir + ': has language_name (the menu offers the language by it)', tr.out.has('language_name')
    && tr.out.get('language_name').text.trim() !== base.out.get('language_name').text.trim());
  const notThere = [...base.out.keys()].filter((n) => base.out.get(n).translatable && !tr.out.has(n));
  if (notThere.length) console.log('  note ' + dir + ' has no translation (English shows) for: ' + notThere.join(', '));
  for (const [name, s] of tr.out) {
    if (!base.out.has(name)) continue;
    const want = placeholders(base.out.get(name).text);
    const got = placeholders(s.text);
    if (want !== got) ok(dir + ' "' + name + '" keeps ' + (want || 'no placeholders'), false, 'has ' + (got || 'none'));
    if (unescapedApostrophe(s.text)) ok(dir + ' "' + name + '" writes its apostrophes as \\\'', false, s.text);
    if (!s.text.trim()) ok(dir + ' "' + name + '" is not empty', false);
  }
}
