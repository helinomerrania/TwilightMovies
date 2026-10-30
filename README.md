# Twilight Movies

Twilight Movies opens KurdFilm's TV website, [app.kurdfilm.krd/tv](https://app.kurdfilm.krd/tv), full screen on an Android TV or TV box, and makes the remote work with it. It is a separate app from Twilight TV.

It isn't made by KurdFilm or connected to them: it only shows their website, as a browser would.

## Put it on a TV box

1. On the box, install **Downloader** from the box's app store, and open it.
2. Type this address and choose **Go**:
   `https://github.com/helinomerrania/TwilightMovies/releases/latest/download/twilight-movies.apk`
3. When the box asks, allow Downloader to install apps. Then choose **Install**, then **Open**.

Twilight Movies is then in the box's row of apps. A newer build installs over the old one the same way, and keeps the website's sign-in and the app's settings.

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
- **Website address**: if KurdFilm moves, type the new address here. **Use default** goes back to `app.kurdfilm.krd/tv`.
- **Exit**.

If the website can't be reached, the app says so, with **Try again** and **Change address**. Pop-ups from the website are only followed when they stay on KurdFilm's own site; other pop-ups (ads) are blocked.

## On the café's box

Twilight TV keeps the café's box in Twilight TV: any other app opened there is sent away after 10 seconds, except the one other app allowed (My TV Plus). For Twilight Movies to stay open there, it has to be that one app: on Twilight TV's phone page, Settings, **This TV box**, set **The one other app allowed** to `Twilight Movies`. Holding Back for 2 seconds in Twilight TV then opens it. (Unlocking the box for 15 minutes, by holding Back for 10 seconds, is too short for a film.)

The box's own web engine is from 2018 (Twilight TV's notes explain). Twilight Movies uses the box's engine, so if KurdFilm's pages come up blank or broken on that box, the fix is to bring Firefox's engine inside the app, as Twilight TV does.

## How it's built

Every change pushed to GitHub is built there (`.github/workflows/build.yml`): first the tests, then the APK. A change to the app on the main branch is published as the latest release, `twilight-movies.apk`, tagged `build-` and the build's number, which is also the app's version number. Changes to only the tests or the documents aren't published. The newest five builds are kept. If a build fails, the run's notes say what went wrong.

| File | What it is |
|---|---|
| `app/src/main/java/com/twilightcafe/movies/MainActivity.java` | The whole app: the website, the remote, the pointer, full-screen video, the menu and the error screen. `DEFAULT_URL` is the website's address |
| `app/src/main/java/com/twilightcafe/movies/CursorView.java` | Draws the mouse pointer |
| `tests/scripts.test.js` | Tests the small scripts the app runs inside the website (scrolling, and the video keys) on stand-in pages, and that they stay plain enough for old web engines. `node tests/scripts.test.js` |
| `art/make_art.py` | Draws the banner, the icon and the picture shown while the website loads, in Twilight TV's style |
| `app/twilight-movies.keystore` | The key every build is signed with. Android only installs an update signed with the same key, so it stays as it is (in the repository on purpose, like Twilight TV's) |

To build it on a computer: `./gradlew assembleRelease` (with Java 17 and the Android SDK). The APK is then in `app/build/outputs/apk/release/`.
