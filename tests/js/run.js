// Runs the tests of the app's JavaScript, texts and websites (tests/js/*.test.js), on a computer with Node 18 or
// newer and nothing to install:   node tests/js/run.js            all of them
//                                 node tests/js/run.js media      only some
'use strict';
const fs = require('fs');
const path = require('path');
const { counts } = require('./lib/check');

const only = process.argv.slice(2);
const files = fs.readdirSync(__dirname).filter((f) => f.endsWith('.test.js')).sort();
for (const f of files) {
  const name = f.replace(/\.test\.js$/, '');
  if (only.length && !only.includes(name)) continue;
  const before = counts.pass + counts.fail;
  console.log(name + ':');
  try {
    require(path.join(__dirname, f));
  } catch (e) {
    counts.fail++;
    console.log('  FAIL ' + name + ' stopped: ' + (e && e.stack ? e.stack : e));
  }
  if (counts.pass + counts.fail === before) {
    counts.fail++;
    console.log('  FAIL ' + name + ' checked nothing');
  }
}
console.log('The JavaScript tests: ' + counts.pass + ' passed, ' + counts.fail + ' failed');
process.exit(counts.fail ? 1 : 0);
