// The app's page scripts (app/src/main/assets/scripts), read and called the way the app does (web/PageScripts):
// the file's function, then its arguments in brackets.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.join(__dirname, '..', '..', '..');
const DIR = path.join(ROOT, 'app', 'src', 'main', 'assets', 'scripts');

function source(name) {
  return fs.readFileSync(path.join(DIR, name + '.js'), 'utf8').trim();
}

function arg(a) {
  return typeof a === 'string' ? "'" + a.replace(/[\\']/g, '\\$&') + "'" : String(a);
}

/** The script name, called with args, in a stand-in page (a vm context). */
function run(name, args, ctx) {
  return vm.runInContext(source(name) + '(' + args.map(arg).join(',') + ')', ctx);
}

/** The scripts in the app, by name. */
function names() {
  return fs.readdirSync(DIR).filter((f) => f.endsWith('.js')).map((f) => f.slice(0, -3)).sort();
}

module.exports = { ROOT, DIR, source, run, names };
