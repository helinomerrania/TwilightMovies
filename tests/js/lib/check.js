// The tests' helper: each check is a sentence saying what it checks, printed "ok" or "FAIL".
'use strict';

const counts = { pass: 0, fail: 0 };

function report(name, passed, detail) {
  if (passed) {
    counts.pass++;
    console.log('  ok   ' + name);
  } else {
    counts.fail++;
    console.log('  FAIL ' + name + (detail ? ': ' + detail : ''));
  }
}

/** got must be want (compared as JSON). */
function check(name, got, want) {
  const passed = JSON.stringify(got) === JSON.stringify(want);
  report(name, passed, passed ? '' : 'got ' + JSON.stringify(got) + ', expected ' + JSON.stringify(want));
}

/** passed must be true; detail says what's wrong when it isn't. */
function ok(name, passed, detail) {
  report(name, !!passed, detail);
}

module.exports = { check, ok, counts };
