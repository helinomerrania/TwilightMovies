// The servers' row (app/src/main/assets/gecko/player-servers.js) on stand-in pages as the other websites have their
// servers: Kurdsaz's buttons, Kartoni Kurdi's (not one for its paying members), Kurd Cinema's list (a choice made in
// it, as the viewer would), Mars Cinema's (its free ones behind a button; and its free ones pressed by the Watch
// once its own server's free hour is over); as many as a film has.
'use strict';
const { check } = require('./lib/check');
const { E } = require('./lib/page');
const { doc, add, names, marked, pressRow, scripts } = require('./lib/row');

let { s, SITES, player } = scripts();

// Kurdsaz: its own server first (the one it plays), then the others
const btn = (name, on) => E('button', { name, attrs: { class: 'server-btn' + (on ? ' active' : '') } });
let d = doc([E('div', { attrs: { id: 'servers-container', class: 'servers-container' } }, [btn('سێرڤەری سەرەکی', true),
  btn('سێرڤەری یەدەگ'), btn('سێرڤەری دووەم'), btn('سێرڤەری سێیەم'), btn('سێرڤەری چوارەم'), btn('سێرڤەری پێنجەم')])]);
s.show(d, SITES['kurdsaz.com'].servers, () => {});
check('Kurdsaz: all six of its servers, the one it plays marked', [names(d).length, names(d)[1], marked(d).indexOf(true)], [6, 'سێرڤەری یەدەگ', 0]);

// Kartoni Kurdi: its servers' tabs (Livewire's), one of them for its paying members
({ s, SITES } = scripts());
const tab = (name, vip, on) => E('button', { name, text: '\n   ' + name + '\n  ', attrs: { class: 'inline-flex px-4' + (on ? ' theme-primary-bg' : ''),
  'wire:click': 'selectServer({"id":26,"name":"' + name + '","link":"https:\\/\\/x","requires_vip":' + vip + ',"sort_order":0})' } });
const tabs = [tab('vidmoly', false, true), tab('abyss', false), tab('vip4k', true), tab('earnvids', false)];
d = doc([E('button', { name: 'trailer', attrs: { 'x-on:click': "$dispatch('open-modal')" } }), E('div', { attrs: { class: 'no-scrollbar' } }, tabs)]);
s.show(d, SITES['www.kartonikurde.com'].servers, () => {});
check('Kartoni Kurdi: its servers, not the one for its paying members (VIP), nor another button; the one it plays marked',
  [names(d), marked(d)], [['vidmoly', 'abyss', 'earnvids'], [true, false, false]]);
pressRow(d, 2);
check('...another pressed: the website\'s own (the third of its free ones)', tabs.map((x) => x.calls.length), [0, 0, 0, 1]);

// Kurd Cinema: a list to choose from (the website's numbers before the names); a choice made, and the list told
({ s, SITES } = scripts());
const choice = (value, text, selected) => Object.assign(E('option', { text, value }), { selected: !!selected });
const select = E('select', { attrs: { class: 'quality-select' } }, [choice('77712', '1- Stream wish', true), choice('77710', '4- vidmoly'),
  choice('77711', '8- File lions')]);
d = doc([select]);
s.show(d, SITES['kurdcinama.com'].servers, () => {});
check('Kurd Cinema: its list\'s servers (without its numbers), its choice marked', [names(d), marked(d)],
  [['Stream wish', 'vidmoly', 'File lions'], [true, false, false]]);
pressRow(d, 1);
check('...another pressed: chosen in its list, and the list told (it loads that server\'s player itself)', [select.value, select.calls],
  ['77710', [['event', 'change', true]]]);
d = doc([E('select', { attrs: { class: 'server-select' } }, [choice('1', 'Femebd', true), choice('2', 'ok'), choice('3', 'Vidmoly'),
  choice('4', 'Online Company')])]);
s.show(d, SITES['kurdcinama.com'].servers, () => {});
check('...an episode\'s list', names(d), ['Femebd', 'ok', 'Vidmoly', 'Online Company']);

// Mars Cinema: its own server, its free ones behind a button
({ s, SITES, player } = scripts());
const mars = (name, pressed) => E('button', { attrs: { type: 'button', class: 'glass glass-interactive group/server relative', 'aria-pressed': String(!!pressed) } },
  [E('span', { text: '', attrs: { class: 'flex w-full' } }, [E('span', { text: name, attrs: { class: 'truncate text-sm' } })])]);
const more = E('button', { text: 'بینینی سێرڤەرە ئاساییەکان (2)', attrs: { type: 'button', class: 'glass glass-interactive self-start' } });
const servers = E('div', { attrs: { class: 'flex flex-col gap-3' } }, [E('section', {}, [mars('MarsHost', true)]), more]);
d = doc([servers]);
const first = s.show(d, SITES['marscinema.net'].servers, () => {});
check('Mars Cinema: its free servers\' button pressed (once); one server shown so far: no row yet', [first, more.calls], [false, [['click']]]);
add(servers, [E('section', {}, [mars('Vidmoly'), mars('YARNVED')])]);
s.show(d, SITES['marscinema.net'].servers, () => {});
check('...then all three, by their names; the one it plays marked; the button not pressed again', [names(d), marked(d), more.calls.length],
  [['MarsHost', 'Vidmoly', 'YARNVED'], [true, false, false], 1]);
const start = E('button', { text: 'کلیک بکە بۆ دەستپێکردنی سەیرکردن' });
d = doc([E('div', { attrs: { class: 'relative aspect-backdrop w-full' } }, [start])]);
player.press(d, 'marscinema.net');
const instead = E('button', { text: 'سەیرکردن بە سێرڤەرە ئاساییەکان (2)' });
const over = E('a', { text: 'کڕینی بەشداری مارس پڵەس', attrs: { href: '/marsplus' } });
const d2 = doc([E('div', { attrs: { class: 'relative aspect-backdrop w-full' } }, [E('div', {}, [E('h3', { text: 'کاتژمێری خۆڕایی تەواو بوو' }),
  E('div', {}, [over, instead])])])]);
player.press(d2, 'marscinema.net');
check('...the Watch: its own server\'s start; once its free hour of the day is over, its free servers (not its paid plan)',
  [start.calls, instead.calls, over.calls], [[['click']], [['click']], []]);

// As many as a film has
({ s, SITES } = scripts());
d = doc([E('div', {}, Array.from({ length: 11 }, (x, i) => btn('سێرڤەری ' + (i + 1), i === 0)))]);
s.show(d, SITES['kurdsaz.com'].servers, () => {});
check('eleven servers: eleven names', names(d).length, 11);
d = doc([E('div', {}, [btn('یەکەم', true), btn('دووەم')])]);
s.show(d, SITES['kurdsaz.com'].servers, () => {});
check('...two: two', names(d), ['یەکەم', 'دووەم']);
