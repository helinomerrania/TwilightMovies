# How the app is built

Twilight Movies is one Android app that shows TV websites full screen and makes the remote work with them: KurdFilm's first, and more to come (they're listed in `app/src/main/assets/sites.json`). Between the websites and the internet sits an ad blocker, whose rules are made from the big filter lists on GitHub every day.

This file says where everything is and how the parts fit together. For installing and using the app, see [README.md](README.md).

## The picture

```
 TV box ────────────────────────────────────────────────────────────────────────────────
  MainActivity ── Android's events ──> MoviesApp (makes every part once and wires them)

  The WebView (web/PageView) shows one of the websites in assets/sites.json (sites/)
     every address a page loads ──> adblock/AdBlocker: blocked (an empty answer or a stand-in), or loaded
     every page it shows ......... assets/scripts/hide-ads.js asks adblock/CosmeticBridge which boxes to hide
     links, jumps and pop-ups ───> app/Blocking: to the app's websites, or stopped and said on the screen

  The remote ──> remote/RemoteKeys ──> the website (arrows, OK), the pointer (pointer/), films (video/),
                                       the app's panels (menu/, sites/, remote/GuidePanel)

  From GitHub: new versions (the latest release: update/Updater) and new filters (the release
  "filters": adblock/FilterUpdate)
 ────────────────────────────────────────────────────────────────────────────────────────

 GitHub, every day (.github/workflows/filters.yml): the lists in tools/filters/lists.txt ──> the compiler
 (tools/filters/src) ──> filters.bin, checked against tools/filters/checks.txt, published as "filters".
 The same file goes inside every new version of the app.
```

## Where things are

```
app/src/main/AndroidManifest.xml            what Android starts by name
app/src/main/java/com/twilightcafe/movies/  the app, a folder for each job (below)
app/src/main/assets/sites.json              the app's websites
app/src/main/assets/scripts/                the small scripts the app runs inside the websites
app/src/main/res/values/strings.xml         the app's texts in English; values-b+ckb/ has them in Kurdish
tools/filters/                              the ad blocker's lists, its own rules, the checks, and the compiler (src/)
tests/java/                                 the Java tests (run.sh)
tests/js/                                   the JavaScript tests (run.js)
tests/try/                                  trying the app and its websites on GitHub's machines, with screenshots
art/                                        draws the banner, the icon and the start-up picture
.github/workflows/build.yml                 tests and builds every push; from main, publishes a release when the app changed
.github/workflows/filters.yml               makes the filters every day, and publishes them
.github/workflows/try.yml                   tries the app on Android TV emulators and its website in Chromium
```

## The app's parts

Each folder has one job. Each class says what it does in a comment at its top.

| Folder | Its job | What's in it |
|---|---|---|
| (top) | What Android starts by name | `MainActivity` (the app's screen: passes Android's events and the remote's keys to MoviesApp), `InstallResult` (Android's question during an update) |
| `app/` | Making the parts and wiring them | `MoviesApp` (makes every part once; the one class that knows them all), and the parts that bring many together: `Navigation` (where the page goes: home, back, again, another website; the error screen and a film full screen around it; the web engine crashing), `PanelActions` (opening the panels, what their buttons do), `MenuActions` (the menu's buttons and labels), `Blocking` (the ad blocker's part in the app: jumps stopped and said, ad boxes hidden, layers cleared before a click, pop-ups), `Updates` (new versions and filters, said on the screen), `PageEvents` (what the website tells the app, handed to the parts it's for) |
| `web/` | The website, in the WebView | `PageView` (the WebView, set up for a TV), `PageClient` and `ChromeClient` (Android's calls, passed on to a `PageListener`), `PageTarget` (the page, or its video full screen: where keys and touches go), `Popups`, `PageScripts` (the scripts in assets/scripts) |
| `sites/` | The app's websites | `Site` (one website: names, address, domains, pointer), `SiteCatalog` (reads sites.json), `Sites` (the one shown, and each one's address and pointer), `SitesPanel` (the Websites panel), `AddressPanel` (typing a website's new address) |
| `remote/` | The remote control | `RemoteKeys` (what each key does), `Keys` (which key is which), `GuidePanel` (the remote guide) |
| `pointer/` | The mouse pointer | `Pointer` (moving it, faster when held; scrolling at the edges), `Touch` (hovering, and taps like a finger's), `CursorView` (its picture) |
| `video/` | Films | `Fullscreen`, `MediaKeys` (play, pause and seek: the website's player first), `KeepAwake` (the TV awake while a film plays) |
| `loading/` | When the website can't be reached | `ErrorScreen` (why, and trying again by itself), `ErrorPanel` (its views), `Backoff` (the waits between tries), `Connection` (whether the box is online, and when the internet is back) |
| `menu/` | The app's menu | `MenuPanel` |
| `ui/` | The app's own screens | `Screen` (what every part on the screen shares), `Views` (buttons, cards, labels and sizes), `Palette` (the colours), `Panel` and `Panels` (the panels BACK closes), `Osd` (messages at the top), `Splash` (the start-up picture), `LoadingBar` |
| `text/` | The app's texts | `Texts` (Kurdish unless English is chosen; numbers and dates in the same language) |
| `settings/` | The app's own settings | `Settings` (the language, the website shown last, each website's address and pointer, the guide shown) |
| `adblock/` | The ad blocker, in the app | `AdBlocker` (between the website and the internet), `SitePolicy` (where it works, and where pages may go), `RequestKind` (what a request is for), `BlockedPage`, `CosmeticBridge` (what the hide-ads script asks), `FilterFiles` (the filter file inside the app, and the one downloaded), `FilterUpdate` (newer filters from GitHub) |
| `adblock/engine/` | The ad blocker's rules: plain Java, shared with the compiler | `FilterSet` (the rules, ready to use), `FilterFile` (the file: written on GitHub, read on the box), `NetworkRules` (which addresses), `RuleGroup` and `NetFilter` (rules filed under a host, a word or a site), `UrlPattern` (address patterns), `CosmeticRules` (which ad boxes to hide), `Hosts` and `PublicSuffix` (hosts and the sites they belong to), `Request` (one address, asked about), `Hash`, `DataIn`, `DataOut` |
| `adblock/surrogates/` | The stand-ins for what's blocked | `Surrogates` (chosen by the rule's redirect=, the address, or its kind), `Stubs` (small scripts in place of ad libraries), `MediaClips` (a picture, a sound, a video, "no ads"), `Base64` |
| `update/` | New versions | `Updater` (finds a newer release; installs it when someone asks), `ApkDownload` (into Android's installer), `Release` (build numbers) |
| `util/` | Small helpers for any part | `Urls`, `Http` (GitHub, with size limits), `Streams` |

The compiler that makes the filter file is in `tools/filters/src` (package `adblock/compiler`): `FilterCompiler` (the command), `ListReader` (one list, by its format), `Preprocessor` and `Condition` (the lists' `!#if` lines), `NetworkRuleParser` and `RuleOptions` (network rules and their options), `CosmeticRuleParser` (hiding rules), `RuleSpec`, `Domains`, `RuleCollection` (every rule read, made into a FilterSet) and `Checks` (checks.txt).

### How they fit together

- **MainActivity only passes things on.** It makes `MoviesApp` and hands it Android's events: start, stop, the remote's keys.
- **MoviesApp is the only place that makes parts.** Each part gets what it needs when it's made, and nothing else, so a part's connections can be seen at a glance and it can be tested on its own. The few parts that bring many others together (those in `app/`) get `MoviesApp` itself; no part outside `app/` knows it.
- **Parts talk through small interfaces**, each defined by the part that needs it: `PageListener`, `ErrorScreen.Host`, `Fullscreen.Listener`, `RemoteKeys.Actions`, `MenuPanel.Actions` and `Status`, `Popups.Rules`, the panels' `Listener`s. So `web/` knows nothing of the ad blocker, and the ad blocker nothing of the screen.
- **The ground floor never reaches up.** `util/` uses nothing else of the app; `adblock/engine/` nothing at all (the compiler shares it, on GitHub); `adblock/surrogates/` only the engine; `adblock/` only those two and `util/`; `settings/` only `util/`; `text/` only the texts; `ui/` only `text/` and `util/`; `update/` only `util/` (and `InstallResult`); `web/` only `util/`. The features (`sites/`, `remote/`, `pointer/`, `video/`, `loading/`, `menu/` and `app/`) use the ground floor and each other. `ArchitectureTest` checks this on every build, and that every folder is in this file.
- **The rules are plain Java, beside the Android code that uses them**, so they're tested on a computer: `SitePolicy`, `RequestKind` and `BlockedPage` beside `AdBlocker`, `Site` beside `Sites`, `Backoff` beside `ErrorScreen`, `Release` beside `Updater`, `Urls`, `Streams` and `Http`, and all of `adblock/engine/` and `adblock/surrogates/`. Such a class says "Plain Java." at its top, and `tests/java/run.sh` compiles it with the tests.
- **Small files.** No file of code is longer than 250 lines. `ArchitectureTest` checks every one.
- **Android 5.0 and newer.** The café's box is old: the app uses no Java library call Android 5 doesn't have (`ArchitectureTest` looks), and the page scripts are plain old JavaScript (`tests/js` looks).
- **Threads.** Anything that touches the screen runs on the main thread (`Screen.main`). The ad blocker answers the WebView on the WebView's own threads, and loads and downloads filters on threads of its own; `Updater` checks and downloads on its own.

## Adding a website

1. Add it to `app/src/main/assets/sites.json`:
   ```json
   {
     "id": "films",
     "name": "Films",
     "address": "https://tv.films.example/",
     "domains": ["films.example", "films-cdn.net"],
     "pointer": true
   }
   ```
   - `id`: small letters, numbers and `-`. Never change it once boxes have it: they keep the website's address and pointer under it.
   - `name`: as the Websites panel shows it; `name_ckb` too, if the name is written differently in Kurdish.
   - `address`: where it opens (https).
   - `domains`: the website's own, with all their subdomains. Its pages go between them freely, and the ad blocker never blocks them. Put its video and picture hosts here too, if they're on another domain. Without `domains`, the address's own is used (`kurdfilm.krd` for `app.kurdfilm.krd`).
   - `pointer`: `true` for a website without a TV interface of its own: the mouse pointer is then on at first (it can be switched in the menu, and the box remembers it for each website).
2. Push. `tests/js/sites.test.js` checks the file before anything is built.

With two or more websites, the menu has **Websites**: the list, each with its address. The website shown last opens at the next start. Each website keeps its own address (**Website address** in the menu) and its own mouse pointer. If a website needs rules of its own (an ad that gets through, a box to hide), they go in `tools/filters/twilight.txt` (see README.md).

## The page scripts

The app runs these inside the websites (`web/PageScripts` names them; each file is one function):

| Script | What it does |
|---|---|
| `scroll.js` | Scrolls what's under the pointer: the innermost box that can scroll that way, then the page; rows that snap move a step at a time |
| `media.js` | The remote's play/pause and seek keys for the page's main video, only when the website's own player didn't already |
| `pause-all.js` | Pauses every video, when the app leaves the screen |
| `playing.js` | Whether a video plays (the TV stays awake) |
| `hide-ads.js` | Hides the ad boxes: the site's rules, then those for the classes and ids the page has, as they appear |
| `ad-layers.js` | Before the pointer clicks: takes away see-through layers that catch the click to open an ad |

## Tests

There are two sets, and GitHub runs both on every push before anything is built. If a check fails, nothing is built or published.

**`node tests/js/run.js`** (Node 18 or newer, nothing to install; `node tests/js/run.js media` runs only some):

| Test | What it checks |
|---|---|
| `scripts` | Each page script is one function, in plain old JavaScript, and PageScripts names every one |
| `media`, `scroll`, `hide-ads`, `ad-layers` | Each script on stand-in pages: the video keys, scrolling, hiding ad boxes, click traps |
| `sites` | sites.json: every website with an id, a name, an https address and its own domains |
| `strings` | The texts: every one the app asks for exists, none is unused, and the Kurdish ones keep each `%1$s` |

**`tests/java/run.sh`** (Java 17 or newer, nothing to install; `tests/java/run.sh SiteTest` runs only one):

| Test | What it checks |
|---|---|
| `ArchitectureTest` | The rules above: small files, the folders' rules, plain Java, Android 5, every folder and class described, every test run |
| `PublicSuffixTest`, `NetworkRulesTest`, `OptionsTest`, `CosmeticRulesTest` | The ad blocker's rules: small lists made into a filter file and read back as the app reads its own, then asked about addresses and pages |
| `CompilerTest` | Reading the lists (`!#if`, `$badfilter`), the file itself (the same lists make the same bytes; a damaged file is refused), checks.txt |
| `SurrogatesTest` | The stand-ins for what's blocked |
| `SitePolicyTest`, `RequestKindTest` | Where the ad blocker works and where pages may go; what a request is for; the blocked page |
| `SiteTest`, `UrlsTest`, `BackoffTest`, `ReleaseTest` | Websites and their domains; addresses and downloads; the error screen's waits; build numbers |

### Trying it on GitHub

GitHub's machines reach the websites, so the app can be tried there as a TV box would show it: push to a branch named `try/...`, or run **Try the app** from the Actions tab (`.github/workflows/try.yml`). It opens the website in Chromium with a TV box's user agent (`tests/try/website.js`), says which web engine its scripts need (`syntax.js`) and what the app's ad blocker would block there (`Blocked.java`), and runs the app itself on two Android TV emulators, one with a web engine from 2018 like the café's box and one with a new one (`emulator.sh`): it goes into the website, opens a film and presses play, the way the remote does. A screenshot of every step, and the logs, go to the pre-release **try** (`publish.sh`).

## Making a change

1. Find the part by its job in the table above; new code goes in the folder of its job. Only what the manifest names goes in the top folder. A new folder goes in the table, and in `ArchitectureTest` (ground floor or feature).
2. A new part is made in `MoviesApp`, and gets what it needs there.
3. A new text goes in `app/src/main/res/values/strings.xml`; its Kurdish one in `values-b+ckb/strings.xml` (until then, English shows).
4. Run `node tests/js/run.js` and `tests/java/run.sh`.
5. Push to any branch other than `main` for a test build. A change to the app pushed to `main` is published as a new version for every box, installed from the menu; a change to only the tests, the documents or the build's own steps isn't.
