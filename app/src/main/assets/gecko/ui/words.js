// Every word on the Twilight screens, in Kurdish (Sorani, checked with Gemini) and English; numbers in the
// language's own digits; the English genre names the websites give, in Kurdish.
'use strict';
TM.words = {
  ckb: {
    nav: { home: 'سەرەتا', films: 'فیلمەکان', series: 'زنجیرەکان', cartoons: 'کارتۆن', mine: 'دڵخوازەکانم' },
    search: 'گەڕان', settings: 'ڕێکخستنەکان', watch: 'تەماشاکردن', more: 'زانیاری زیاتر',
    addMine: '+ دڵخوازەکانم', inMine: '✓ دڵخوازەکانم', back: 'گەڕانەوە', another: 'ماڵپەڕێکی تر',
    rows: { continue: 'بەردەوامبوون لە تەماشاکردن', newFilms: 'نوێترین فیلمەکان', newSeries: 'نوێترین زنجیرەکان',
      dubbed: 'فیلمە دۆبلاژکراوەکان', cartoons: 'فیلمی کارتۆن', mine: 'دڵخوازەکانم' },
    sub: 'ژێرنووسی کوردی', dub: 'دۆبلاژی کوردی', film: 'فیلم', serie: 'زنجیرە',
    sites: (n) => n + ' ماڵپەڕ', length: (m, num) => num(m) + ' خولەک',
    season: (n) => 'وەرزی ' + n, episode: (n) => 'ئەڵقەی ' + n, se: (s, e) => 'وەرزی ' + s + ' · ئەڵقەی ' + e,
    seasons: (n) => n + ' وەرز', from: (site) => 'لە ' + site, age: (a, num) => (/^\d+$/.test(a) ? 'سەروو ' + num(a) + ' ساڵ' : a),
    availableOn: 'لەم ماڵپەڕانەدا هەیە', firstChoice: 'یەکەم هەڵبژاردە',
    continueEp: (s, e) => 'بەردەوام بە: وەرزی ' + s + '، ئەڵقەی ' + e, startEp: (num) => 'وەرزی ' + num(1) + '، ئەڵقەی ' + num(1),
    searched: (n) => 'گەڕان لە ' + n + ' ماڵپەڕدا کرا', found: (n) => n + ' ئەنجام', noResults: 'هیچ نەدۆزرایەوە',
    space: 'بۆشایی', del: 'سڕینەوە', typeHint: 'ناوی فیلم یان زنجیرەیەک بنووسە، بە کوردی یان ئینگلیزی',
    mineEmpty: 'هێشتا هیچ لێرە نییە. لەسەر فیلم یان زنجیرەیەک، + دڵخوازەکانم داگرە بۆ ئەوەی لێرە هەڵیبگریت.',
    sec: { lang: 'زمان', sites: 'ماڵپەڕەکان', first: 'کامیان لەپێشدا بێت', look: 'ڕووکار', about: 'دەربارە' },
    langTitle: 'زمانی شاشەکان', rtl: 'لە ڕاستەوە بۆ چەپ', ltr: 'لە چەپەوە بۆ ڕاست',
    sitesTitle: 'ماڵپەڕەکان و ڕیزبەندییان',
    sitesNote: 'ئەوەی لە سەرەوەیە لەپێشدا دێت. ئەوەی کوژاوەتەوە، نە لە گەڕاندا دەردەکەوێت و نە لە لیستەکاندا.',
    onOf: (a, n) => a + ' لە ' + n, status: { ok: 'کار دەکات', wait: '…', failed: 'وەڵامی نەبوو' }, off: 'کوژاوەتەوە',
    up: 'بۆ سەرەوە', down: 'بۆ خوارەوە',
    firstTitle: 'کاتێک فیلمێک لە چەند ماڵپەڕێکدا هەیە، کامیان لەپێشدا لێبدرێت؟',
    first: { sub: 'ژێرنووسی کوردی', dub: 'دۆبلاژی کوردی', best: 'باشترین کوالیتی' },
    firstHint: { sub: 'ئەگەر نەبوو، دۆبلاژ', dub: 'ئەگەر نەبوو، ژێرنووس', best: '4K و 1080p پێش ئەوانی تر' },
    lookTitle: 'ڕەنگی شاشەکان', looks: { twilight: 'توایڵایت', midnight: 'نیوەشەو', ember: 'پشکۆ' },
    lookHint: { twilight: 'شینی شەو و پرتەقاڵی، وەک وێنەی بەرنامەکە', midnight: 'ڕەش و سپی', ember: 'قاوەیی و زەرد' },
    adblockOn: 'ڕێگری ڕیکلام: هەڵکراوە', sitesOn: (a, n) => 'ماڵپەڕەکان: ' + a + ' لە ' + n + ' هەڵکراون',
  },
  en: {
    nav: { home: 'Home', films: 'Films', series: 'Series', cartoons: 'Cartoons', mine: 'My list' },
    search: 'Search', settings: 'Settings', watch: 'Watch', more: 'More info',
    addMine: '+ My list', inMine: '✓ My list', back: 'Back', another: 'Another website',
    rows: { continue: 'Continue watching', newFilms: 'New films', newSeries: 'New series', dubbed: 'Dubbed films',
      cartoons: 'Cartoons', mine: 'My list' },
    sub: 'Kurdish subtitles', dub: 'Kurdish dubbing', film: 'Film', serie: 'Series',
    sites: (n) => n + (n === '1' ? ' website' : ' websites'), length: (m) => Math.floor(m / 60) + ' h ' + (m % 60) + ' min',
    season: (n) => 'Season ' + n, episode: (n) => 'Episode ' + n, se: (s, e) => 'S' + s + ' · E' + e,
    seasons: (n) => n + (n === '1' ? ' season' : ' seasons'), from: (site) => 'From ' + site,
    age: (a) => (/^\d+$/.test(a) ? a + '+' : a), availableOn: 'On these websites', firstChoice: 'Plays first',
    continueEp: (s, e) => 'Continue: S' + s + ' E' + e, startEp: () => 'S1 E1',
    searched: (n) => 'searched ' + n + ' websites', found: (n) => n + ' found', noResults: 'Nothing found',
    space: 'Space', del: 'Delete', typeHint: 'Type a film or series name, in Kurdish or English',
    mineEmpty: 'Nothing here yet. On a film or series, press + My list to keep it here.',
    sec: { lang: 'Language', sites: 'Websites', first: 'What plays first', look: 'Look', about: 'About' },
    langTitle: 'The screens’ language', rtl: 'Right to left', ltr: 'Left to right', sitesTitle: 'The websites, in order',
    sitesNote: 'The one on top comes first. One that is off shows nowhere: not in search, not in the rows.',
    onOf: (a, n) => a + ' of ' + n, status: { ok: 'Works', wait: '…', failed: 'Didn’t answer' }, off: 'Off',
    up: 'Move up', down: 'Move down', firstTitle: 'When a film is on several websites, which plays first?',
    first: { sub: 'Kurdish subtitles', dub: 'Kurdish dubbing', best: 'Best picture' },
    firstHint: { sub: 'If there are none, dubbing', dub: 'If there is none, subtitles', best: '4K and 1080p before the rest' },
    lookTitle: 'The screens’ colours', looks: { twilight: 'Twilight', midnight: 'Midnight', ember: 'Ember' },
    lookHint: { twilight: 'Night blue and sunset orange, like the app’s picture', midnight: 'Black and white', ember: 'Brown and amber' },
    adblockOn: 'Ad blocker: on', sitesOn: (a, n) => 'Websites: ' + a + ' of ' + n + ' on',
  },
};

// The websites' English genre names, in Kurdish (the ones the websites themselves use, where they have them)
TM.genresKu = {
  Action: 'ئاکشن', Adventure: 'سەرکێشی', 'Action & Adventure': 'ئاکشن و سەرکێشی', 'Science Fiction': 'خەیاڵی زانستی',
  'Sci-Fi & Fantasy': 'خەیاڵی زانستی', Drama: 'دراما', Crime: 'تاوانکاری', Mystery: 'نهێنی', Thriller: 'هەستبزوێن',
  Horror: 'ترسناک', Animation: 'ئەنیمەیشن', Anime: 'ئەنیمی', Family: 'خێزانی', Comedy: 'کۆمیدی', Fantasy: 'خەیاڵی',
  Romance: 'ڕۆمانسی', Music: 'مۆسیقا', History: 'مێژوویی', War: 'جەنگ', Documentary: 'دۆکیومێنتاری', Western: 'ڕۆژئاوایی',
};

/** The screens' language now: its words, its direction, its digits. */
TM.language = function (code) {
  const ku = code !== 'en';
  const num = (v) => (ku ? String(v).replace(/[0-9]/g, (x) => '٠١٢٣٤٥٦٧٨٩'[x]).replace('.', '٫') : String(v));
  return { code: ku ? 'ckb' : 'en', ku, dir: ku ? 'rtl' : 'ltr', w: TM.words[ku ? 'ckb' : 'en'], num };
};
