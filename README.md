# Twilight Movies

Twilight Movies opens KurdFilm's TV website, [app.kurdfilm.krd/tv](https://app.kurdfilm.krd/tv), full screen on an Android TV or TV box, and makes the remote work with it. It is a separate app from Twilight TV.

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

**Mouse pointer** (in the menu) is for anything on the website the arrows can't reach. The arrows then move a pointer, OK clicks where it points, and pushing it against the edge of the screen scrolls the page (or the row of films under it). The box remembers the choice.

## The menu

- **Home**: the website's first page.
- **Reload**: the page again.
- **Mouse pointer**: on or off.
- **Remote guide**: what each button does.
- **Language**: Kurdish or English (see [Kurdish and English](#kurdish-and-english)).
- **Website address**: if KurdFilm moves, type the new address here. **Use default** goes back to `app.kurdfilm.krd/tv`.
- **Look for a new version**, or **Install the new version** when one is waiting.
- **Exit**.

Under the app's name, the menu shows the page, the app's build number and the box's web engine version: useful when something needs fixing.

## By itself

- **The TV stays awake while a film plays**, inside the page or full screen, so the box's screensaver doesn't start in the middle of a film. When nothing has played for about 40 seconds, the TV may sleep as usual.
- **When the website can't be reached**, the app says why (the box has no internet, the website didn't answer, or the secure connection failed) and keeps trying: after 10 seconds, then 20, 40, and every minute. When the box's internet comes back, it tries again at once. **Try again** and **Change address** are there too.
- **Pop-ups** from the website are only followed when they stay on KurdFilm's own site; others (ads) are blocked, and so are links that try to open other apps.
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

| File | What it is |
|---|---|
| `app/src/main/java/com/twilightcafe/movies/MainActivity.java` | The app: the website, the remote, the pointer, films full screen and the TV kept awake, the menu, the remote guide and the error screen. `DEFAULT_URL` is the website's address |
| `app/src/main/java/com/twilightcafe/movies/Updater.java` | New versions from GitHub's releases (like Twilight TV's), and `InstallResult.java` shows Android's install question |
| `app/src/main/java/com/twilightcafe/movies/CursorView.java` | Draws the mouse pointer |
| `app/src/main/res/values-b+ckb/strings.xml`, `app/src/main/res/values/strings.xml` | Every text the app shows, in Kurdish and in English (see [Kurdish and English](#kurdish-and-english)) |
| `tests/scripts.test.js` | Tests the small scripts the app runs inside the website (scrolling, the video keys, whether a film plays) on stand-in pages, and that they stay plain enough for old web engines |
| `tests/strings.test.js` | Tests the texts and any translation |
| `art/make_art.py` | Draws the banner, the icon and the picture shown while the website loads, in Twilight TV's style |
| `app/twilight-movies.keystore` | The key every build is signed with. Android only installs an update signed with the same key, so it stays as it is (in the repository on purpose, like Twilight TV's) |

To run the tests: `node tests/scripts.test.js` and `node tests/strings.test.js`. To build it on a computer: `./gradlew assembleRelease` (with Java 17 and the Android SDK). The APK is then in `app/build/outputs/apk/release/`; a build made there doesn't look for new versions.
