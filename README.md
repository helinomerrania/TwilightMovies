# Twilight Movies

Twilight Movies opens TV websites full screen on an Android TV or TV box, and makes the remote work with them: KurdFilm's, [app.kurdfilm.krd/tv](https://app.kurdfilm.krd/tv), first, and more can be added (see [Adding a website](ARCHITECTURE.md#adding-a-website)). It is a separate app from Twilight TV.

It isn't made by KurdFilm or connected to them: it only shows their website, as a browser would.

## Put it on a TV box

1. On the TV box, open Google Play and install **Downloader** (by AFTVnews).
2. Open Downloader and type this address:
   `https://github.com/helinomerrania/TwilightMovies/releases/latest/download/twilight-movies.apk`
3. When the box asks, allow Downloader to install apps. Then choose **Install**, then **Open**.

You can also copy `twilight-movies.apk` to a USB stick and open it with the box's file manager.

Twilight Movies is then in the box's row of apps. On its first start it shows the **remote guide** once (it's in the menu too), in Kurdish, with a button to switch the app to English. After that, new versions come by themselves: see [New versions](#new-versions).

## The remote

| Button | What it does |
|---|---|
| Arrows | Move between the website's films and buttons |
| OK | Opens what's chosen |
| Back | The page before. On the first page, press it twice to leave the app |
| Hold OK, hold Back, or Menu | The app's menu |
| Play/pause, Fast-forward, Rewind | The film: play and pause, 10 seconds on or back |
| Channel up and down | Scroll the page |

While a film plays full screen, OK pauses and plays it, and Back leaves full screen. Each key goes to the website first: the app only does it itself when the website's player doesn't, so nothing happens twice.

**Mouse pointer** (in the menu) is for anything on the website the arrows can't reach, and for websites without a TV interface of their own. The arrows then move a pointer, OK clicks where it points, and pushing it against the edge of the screen scrolls the page (or the row of films under it). The box remembers the choice for each website.

## The menu

- **Home**: the website's first page.
- **Websites**: once the app has more than one, the list of them, to open another. The one shown last opens at the next start.
- **Reload**: the page again.
- **Mouse pointer**: on or off, for the website shown (the box remembers it for each website).
- **Ad blocker**: on or off for the website shown (see [The ad blocker](#the-ad-blocker)), and **Allow ⟨site⟩** after it stopped a jump.
- **Remote guide**: what each button does.
- **Language**: Kurdish or English (see [Kurdish and English](#kurdish-and-english)).
- **Website address**: if the website moves, type its new address here. **Use default** goes back to its own (for KurdFilm, `app.kurdfilm.krd/tv`).
- **Look for a new version**, or **Install the new version** when one is waiting.
- **Exit**.

Under the app's name, the menu shows the page, the app's build number and the box's web engine version (useful when something needs fixing), and how many rules the ad blocker has and from when.

## The ad blocker

It's built in and on from the start.

- **What it stops:** ads, trackers, pop-up and pop-under ad networks, and known harmful sites. Every address a page loads, in all its frames (the film players other sites put in the page too), is checked against about 320,000 rules: EasyList, EasyPrivacy, uBlock Origin's own lists, Liste AR (ads on Arabic-script sites, which Kurdish sites share) and HaGeZi's lists, with the app's own rules.
- **Pages don't break for want of their ads:** a blocked script gets an empty one, a picture a see-through pixel, a frame a blank page, an ad's video a second of silent black, a player asking for its ads gets "no ads", and the best-known ad scripts (Google's ads, tags and analytics, Amazon's, ad-blocker detectors) get small stand-ins, so a page that waits for them goes on.
- **Ad boxes are hidden** (about 42,000 hiding rules), also the ones that appear later.
- **No jumping away to other sites:** a page can only go to the app's websites. When a page tries to jump to another site (a favourite trick of ads), it's stopped, and a message says where it wanted to go. If it was a real link: hold OK, then **Allow ⟨site⟩**. The page opens, and that site is allowed from then on. Pop-ups to other sites are stopped too.
- **Click traps:** with the mouse pointer, see-through layers over the page that catch the first click to open an ad are taken away before the click lands.
- **If a website doesn't work right with it:** in the menu, choose **Ad blocker: On · N blocked here**. It goes off for that website (the box remembers), and the page loads again. Choose it again to switch it back on.
- **Always up to date:** GitHub makes the filters again every day and publishes them as the release "filters". Each box looks twice a day and downloads the new ones (about 3 MB) once its own are two days old, or at once with **Look for a new version**. Every new version of the app brings that day's filters with it too.

**Your own rules** go in `tools/filters/twilight.txt`. On GitHub: open it, press the pencil, add a rule (the file has examples), and commit. GitHub makes the filters again within minutes, and the boxes get them within a day, or at once with **Look for a new version**. When an ad gets through, or something that must work is blocked, add a line to `tools/filters/checks.txt` as well. Every filter file is checked against it before it's published, so the mistake can't come back.

**More lists** go in `tools/filters/lists.txt`, one line each. HaGeZi's list of harmful sites (about 190,000 more) is there already, behind a `#`: take the `#` away to add it, at about 1.2 MB more for each box to download.

## By itself

- **The TV stays awake while a film plays**, inside the page or full screen, so the box's screensaver doesn't start in the middle of a film. When nothing has played for about 40 seconds, the TV may sleep as usual.
- **When the website can't be reached**, the app says why (the box has no internet, the website didn't answer, or the secure connection failed) and keeps trying: after 10 seconds, then 20, 40, and every minute. When the box's internet comes back, it tries again at once. **Try again** and **Change address** are there too.
- **Pop-ups** from the website are only followed when they stay on the website (or go to another of the app's); others (ads) are blocked, and so are links that try to open other apps.
- **Text size**: the box's own font-size setting is ignored, so the website's TV pages keep their layout.
- If the website's pages need a newer web engine than the box has, the app says so once (see below).

## New versions

Every few hours the app looks for a newer build on GitHub and downloads it in the background (it's about 1 MB). It's never installed on its own: when one is waiting, the app says so once (never over a film), and the menu shows **Install the new version**. Choose it, then **Install** on Android's screen. The first time only, Android asks to allow Twilight Movies to install apps: switch it on and press Back, and the install carries on by itself.

A new version keeps the website's sign-in and the app's settings. Installing with Downloader, as at the start, works too.

## On the café's box

Twilight TV keeps the café's box in Twilight TV: any other app opened there is sent away after 10 seconds, except the one other app allowed (My TV Plus). For Twilight Movies to stay open there, it has to be that one app: on Twilight TV's phone page, Settings, **This TV box**, set **The one other app allowed** to `Twilight Movies`. Holding Back for 2 seconds in Twilight TV then opens it. (Unlocking the box for 15 minutes, by holding Back for 10 seconds, is too short for a film.)

The box's own web engine is from 2018 (Twilight TV's notes explain). Twilight Movies uses the box's engine. If KurdFilm's own scripts are too new for it, the app shows a message saying so, and the menu shows the engine's version. The fix then is to bring Firefox's engine inside the app, as Twilight TV does.

## Kurdish and English

The app's own screens (the menu, the messages, the remote guide and the error screen) are in Kurdish (Sorani), right to left. For English: **Language** in the menu, or the **English** button on the remote guide at the first start. The box remembers the choice. The website has its own language choice.

The Kurdish texts are in `app/src/main/res/values-b+ckb/strings.xml` (translated with Gemini), and the English ones in `app/src/main/res/values/strings.xml`, with the same names. To change a Kurdish word, open that file on GitHub, press the pencil, change it, and commit. The build checks the file first (the same names, every `%1$s` and `%1$d` kept, apostrophes written as `\'`), then publishes a new version, which the box offers to install.

## How it's built

Every change pushed to GitHub is built there (`.github/workflows/build.yml`): first the tests, then the APK. A change to the app on the main branch is published as the latest release, `twilight-movies.apk`, tagged `build-` and the build's number, which is also the app's version number. Changes to only the tests or the documents aren't published. The newest five builds are kept. If a build fails, the run's notes say what went wrong.

How the app is made, part by part, is in [ARCHITECTURE.md](ARCHITECTURE.md): every folder and what it's for, how the parts fit together, the tests, and how to add a website. The files you're most likely to change:

| File | What it is |
|---|---|
| `app/src/main/assets/sites.json` | The app's websites: their names, addresses and domains (see [Adding a website](ARCHITECTURE.md#adding-a-website)) |
| `app/src/main/res/values-b+ckb/strings.xml`, `app/src/main/res/values/strings.xml` | Every text the app shows, in Kurdish and in English (see [Kurdish and English](#kurdish-and-english)) |
| `tools/filters/twilight.txt`, `checks.txt`, `lists.txt` | The ad blocker's own rules, what must be blocked and what must work, and the lists it's made from (see [The ad blocker](#the-ad-blocker)) |
| `app/src/main/java/com/twilightcafe/movies/` | The app, a folder for each job (in ARCHITECTURE.md); no file longer than 250 lines |
| `app/src/main/assets/scripts/` | The small scripts the app runs inside the websites (scrolling, the video keys, hiding ad boxes) |
| `art/make_art.py` | Draws the banner, the icon and the picture shown while the website loads, in Twilight TV's style (`art/landscape.py` draws the landscape) |
| `app/twilight-movies.keystore` | The key every build is signed with. Android only installs an update signed with the same key, so it stays as it is (in the repository on purpose, like Twilight TV's) |

To run the tests: `node tests/js/run.js` and `tests/java/run.sh`. To build it on a computer: `tools/filters/build.sh app/src/main/assets` (the filters), then `./gradlew assembleRelease` (with Java 17 and the Android SDK). The APK is then in `app/build/outputs/apk/release/`; a build made there doesn't look for new versions.
