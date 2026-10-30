// Which web engine a website's scripts need (after website.js saved them): for each, the oldest JavaScript it
// parses as, and the Chrome version that brought that syntax. The café's TV box has Chrome 66 inside (2018).
// Usage: node tests/try/syntax.js OUT_DIR      (needs acorn: npm install acorn)
'use strict';
const fs = require('fs');
const path = require('path');
const acorn = require('acorn');

const OUT = process.argv[2];
// the first Chrome with each year's new syntax (the parts websites use most)
const CHROME = { 2015: 51, 2016: 52, 2017: 58, 2018: 64, 2019: 66, 2020: 80, 2021: 85, 2022: 94, 2023: 110, 2024: 117 };
const WHAT = {
  2017: 'async functions', 2018: 'object spread, async iteration', 2019: 'catch without a name',
  2020: '?. and ??', 2021: '&&=, ||=, ??=', 2022: 'class fields, #private, static blocks', 2023: 'hashbang', 2024: 'regex /v',
};

function oldest(src) {
  for (const year of Object.keys(CHROME).map(Number)) {
    for (const sourceType of ['script', 'module']) {
      try {
        acorn.parse(src, { ecmaVersion: year, sourceType, allowHashBang: true, allowReturnOutsideFunction: true });
        return { year, sourceType };
      } catch (e) {
        // newer than that year, or a module
      }
    }
  }
  return null;
}

const index = fs.existsSync(path.join(OUT, 'js', 'index.tsv'))
  ? fs.readFileSync(path.join(OUT, 'js', 'index.tsv'), 'utf8').trim().split('\n').map((l) => l.split('\t')) : [];
let worst = 0;
for (const [file, url] of index) {
  const src = fs.readFileSync(path.join(OUT, 'js', file), 'utf8');
  const o = oldest(src);
  if (!o) {
    console.log('?       ' + url + '  (not JavaScript acorn knows)');
    continue;
  }
  const chrome = o.year <= 2016 ? 0 : CHROME[o.year];
  worst = Math.max(worst, chrome);
  console.log((chrome ? 'Chrome ' + chrome + '+' : 'any    ').padEnd(12) + url + '  (' + o.year + ' ' + o.sourceType
    + (WHAT[o.year] ? ': ' + WHAT[o.year] : '') + ', ' + Math.round(src.length / 1024) + ' KB)');
}
console.log('\nThe newest any script needs: ' + (worst ? 'Chrome ' + worst : 'nothing new') + ' (the café\'s box: Chrome 66)');
