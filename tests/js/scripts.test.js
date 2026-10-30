// The page scripts themselves: each is one function the app can call, in plain old JavaScript (the café's TV
// box has a web engine from 2018), and the app's list of them (web/PageScripts.java) is the folder's.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { check, ok } = require('./lib/check');
const { ROOT, source, names } = require('./lib/scripts');

// comments on their own line, or after code with a space before them ("x = 1;   // why")
const withoutComments = (code) => code.replace(/(^|\s)\/\/ ?.*$/gm, '$1');

for (const name of names()) {
  const code = source(name);
  let valid = true;
  try {
    new vm.Script(code);
  } catch (e) {
    valid = false;
  }
  ok(name + '.js is valid JavaScript', valid);
  const body = withoutComments(code).trim();
  ok(name + '.js is one function, (function (...) {...})', /^\(function \([\w, ]*\) \{[\s\S]*\}\)$/.test(body), body.slice(0, 40));
  const newer = body.match(/=>|\blet\b|\bconst\b|\?\.|\?\?|`|\bclass\s+\w+\s*(extends\b|\{)|\basync\s+function\b|\bawait\s|\.\.\./);
  ok(name + '.js uses no newer syntax', !newer, newer && newer[0]);
  ok(name + '.js says what it does at its top', /^\/\/ \S/.test(code));
}

const java = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'twilightcafe', 'movies', 'web',
  'PageScripts.java'), 'utf8');
const listed = [...java.matchAll(/public static final String \w+ = "([\w-]+)";/g)].map((m) => m[1]).sort();
check('PageScripts.java names every script in the folder, and only those', listed, names());
