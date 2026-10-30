// The app's websites (app/src/main/assets/sites.json): each one with an id, a name, a web address and its own
// domains, ids that differ, nothing misspelt, and only what the app reads (sites/SiteCatalog.java).
'use strict';
const fs = require('fs');
const path = require('path');
const { check, ok } = require('./lib/check');
const { ROOT } = require('./lib/scripts');

const FILE = path.join(ROOT, 'app', 'src', 'main', 'assets', 'sites.json');
const CATALOG = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'twilightcafe', 'movies', 'sites', 'SiteCatalog.java');
const KEYS = ['id', 'name', 'name_ckb', 'address', 'domains', 'pointer'];
const HOST = /^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,62}$/;

let doc = null;
try {
  doc = JSON.parse(fs.readFileSync(FILE, 'utf8'));
} catch (e) {
  ok('sites.json is JSON', false, e.message);
}
const sites = doc && Array.isArray(doc.sites) ? doc.sites : [];
ok('sites.json has at least one website', sites.length > 0);
ok('sites.json has only "about" and "sites"', doc && Object.keys(doc).every((k) => k === 'about' || k === 'sites'),
  doc && Object.keys(doc).join(', '));

const ids = new Set();
for (const s of sites) {
  const id = s && s.id;
  const name = 'website "' + (id || '?') + '"';
  ok(name + ': an id of small letters, numbers and -', typeof id === 'string' && /^[a-z0-9][a-z0-9-]*$/.test(id));
  ok(name + ': an id no other website has', !ids.has(id));
  ids.add(id);
  const unknown = Object.keys(s).filter((k) => !KEYS.includes(k));
  ok(name + ': only what the app reads (' + KEYS.join(', ') + ')', unknown.length === 0, 'not: ' + unknown.join(', '));
  ok(name + ': a name', typeof s.name === 'string' && s.name.trim() !== '');
  ok(name + ': name_ckb, if there is one, is a name', s.name_ckb === undefined || (typeof s.name_ckb === 'string' && s.name_ckb.trim() !== ''));
  let url = null;
  try {
    url = new URL(s.address);
  } catch (e) {
    url = null;
  }
  ok(name + ': a web address', url && (url.protocol === 'https:' || url.protocol === 'http:') && HOST.test(url.hostname), s.address);
  ok(name + ': https, so nothing on the way can change the page', url && url.protocol === 'https:', s.address);
  ok(name + ': domains, if given, are a list of hosts', s.domains === undefined
    || (Array.isArray(s.domains) && s.domains.length > 0 && s.domains.every((d) => typeof d === 'string' && HOST.test(d))),
  JSON.stringify(s.domains));
  if (url && Array.isArray(s.domains)) {
    const h = url.hostname;
    ok(name + ': its address is on one of its domains', s.domains.some((d) => h === d || h.endsWith('.' + d)), h);
  }
  ok(name + ': pointer, if given, is true or false', s.pointer === undefined || typeof s.pointer === 'boolean');
}

const catalog = fs.readFileSync(CATALOG, 'utf8');
const read = KEYS.filter((k) => catalog.includes('"' + k + '"'));
check('SiteCatalog.java reads every key a website may have', read, KEYS);
