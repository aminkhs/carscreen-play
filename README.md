# CarScreen Play

Watch your phone's videos, video links and websites (including YouTube) on an **Android Auto** car screen, with a built-in **driving lock** that switches to audio only above 30 km/h.

> **Safety first.** Never watch video while driving. The public build hides the picture above 30 km/h (measured with GPS), but that is a safety net, not permission to look at the screen while the car is moving. You are responsible for complying with the traffic laws where you drive.

> Android and Android Auto are trademarks of Google LLC. This is an independent open-source project. It is not affiliated with, endorsed by, or supported by Google.

---

## Contents

- [What it does](#what-it-does)
- [Two Android Auto entries](#two-android-auto-entries)
- [Driving lock (public build)](#driving-lock-public-build)
- [Installation](#installation)
- [Using it in the car](#using-it-in-the-car)
- [Permissions](#permissions)
- [Known limitations](#known-limitations)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [License](#license)

## What it does

**On the car screen (Android Auto):**

- **Video player.** Play the phone's videos (gallery, Downloads, camera), the playlist opened in the phone app, or any MP4 / MKV / HLS (`.m3u8`) link. Picture modes: *Full size* (whole picture), *Cropped* (fills the area) and *Stretched*.
- **Web browser.** YouTube, Google or any website, with zoom, back and reload. You can type with the car keyboard, and Persian text works too.
- **Layout.** Show the video full screen, or on the right or left side (40–70 % width). The other side can stay empty or show a website such as Google Maps.
- **Collapsible buttons.** The **›** arrow hides the control buttons to free the right side of the screen. The **‹** arrow brings them back.
- **Touch.** Tap the car screen to press buttons on the page. Use the pan (✥) button to scroll by dragging.
- **Screenshot** of the car screen, saved to the phone's gallery.
- **Media card next to the map.** See [Two Android Auto entries](#two-android-auto-entries).

**On the phone:**

- Media3 video player with local files, HTTP(S)/HLS streams, M3U playlists, subtitles (SRT/WebVTT, UTF-8 or Windows-1256, Persian shaping), audio-track selection and resume position.
- Embedded browser for YouTube and Google sign-in. The car-screen browser shares its cookies, so you sign in once on the phone.
- An **Android Auto Setup** page that shows how the app was installed (the thing Android Auto checks) and has all display settings.
- Screenshot button (phone screen → gallery), screen-mirroring lab and diagnostics export.

## Two Android Auto entries

Android Auto gives apps two different kinds of place, and this app uses both:

| Entry | What Android Auto shows | Needed for |
| --- | --- | --- |
| **Video screen** (Car App Library, navigation-template surface) | The app draws the full car screen: video player, browser, side-by-side layout | Watching video / websites |
| **Media entry** (Media3 `MediaLibraryService`) | Android Auto draws a standard music-style UI, including the small **card next to the map** in split screen | Listening while Google Maps / Waze is open |

The card next to the map is drawn by Android Auto itself. It can only show a cover image, a title and play/pause/next buttons, so **no app can show moving video in it**, music apps included. This app fills that card with the current video's sound, a still frame of the video as the cover image, and the controls. Both entries share one player, so you can switch between the full video screen and the map without interrupting playback.

## Driving lock (public build)

The **public build** (the APK on the GitHub Releases page) always enforces this lock:

| Situation | Car screen |
| --- | --- |
| Speed **above 30 km/h** | Picture hidden behind an opaque **audio-only** screen with a speaker icon. Sound keeps playing. Touches on the hidden content are blocked. |
| Speed at or below 30 km/h for **10 seconds** | Picture comes back |
| Location permission not granted | Audio only (fails closed) |
| Location / GPS turned off | Audio only (fails closed) |
| No GPS reading received yet (for example, parked underground) | Picture allowed |
| GPS signal lost while driving (tunnel) | The last reading is kept, so the picture stays hidden |

Details:

- **Speed source:** the phone's GPS (`LocationManager.GPS_PROVIDER`, one update per second). If the car reports its speed through Android Auto, that reading is used as well, and the higher of the two recent readings decides.
- **What it covers:** the whole car screen, meaning the video player, the browser (including YouTube) and the side-by-side layout. The media card next to the map only ever shows a still cover image.
- **What it does not cover:** the phone's own screen. The lock is for the car display.
- The rules live in `SpeedLockPolicy` and are covered by unit tests (`SpeedLockPolicyTest`).

The **personal build** (`personal` flavor, built from source) has the same lock as an optional setting, off by default.

## Installation

Requirements: Android 10 or newer, the Android Auto app, and a car or head unit with Android Auto.

### 1. Download

Download `CarScreenPlay-<version>.apk` from the [Releases](../../releases) page. Check its SHA-256 against `SHA256SUMS.txt` from the same release.

### 2. Install so that Android Auto shows the video screen

Since Android Auto 17, the **video screen is hidden for any app that Android Auto doesn't recognise as installed from Google Play**, even with *Unknown sources* turned on. `adb install -i com.android.vending` no longer helps either, because Android Auto checks who started the installation. The **media entry** is not affected: it works with *Unknown sources* alone.

The method the community currently uses for the video screen:

1. Install **[Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)** and start it with Wireless debugging (no root needed).
2. Install **[KingInstaller](https://github.com/fcaronte/KingInstaller/releases)** from its official GitHub page. Open it, allow Shizuku, select this APK, and install with the Shizuku method.
3. Open the app → **Android Auto Setup**. The top card turns green ("Installed as a Google Play app") when the install source is right.
4. Repeat steps 2–3 for every update.

This relies on third-party tools and on Android Auto behaviour that Google can change at any time. See [docs/ANDROID_AUTO.md](docs/ANDROID_AUTO.md) for how the check works and what to try if it fails.

### 3. Android Auto settings

1. Android Auto settings → scroll down → tap **Version** 10 times → accept developer mode.
2. ⋮ → **Developer settings** → turn on **Unknown sources**.
3. Settings → Apps → Android Auto → **Force stop**, then Storage → **Clear cache**. Android Auto remembers apps it rejected before.
4. Android Auto settings → **Customize launcher** → make sure the app is ticked. It can appear twice: once for the video screen and once for the media entry.
5. Reconnect the phone to the car.

### 4. In the phone app

Open **Android Auto Setup** and:

- Allow the **video library, location and car speed** permissions.
- **Sign in to YouTube** in the app's browser if you want your account on the car screen.
- Choose your **car screen display** settings.

## Using it in the car

- Open the app from the Android Auto launcher → **Menu** → *Phone videos*, *YouTube*, *Search YouTube*, *Web browser*, *Play video URL*, *App playlist*, *Display settings*, *Screenshot*, *Test video*.
- **Video controls:** Play/Pause and picture mode in the top strip; rewind, forward and screenshot icons on the right.
- **Browser controls:** Back and Type in the top strip; zoom in, zoom out and reload on the right.
- **Scrolling:** tap the pan (✥) button, then drag on the screen.
- **Typing:** *Type* opens the car keyboard (usually available only while parked) and enters the text into the focused field on the page.
- **Next to the map:** start a video, then open your navigation app. The media card shows the sound, the cover frame and the controls.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_LOCAL_NETWORK` | Streams, websites, optional LAN streams / receiver |
| `READ_MEDIA_VIDEO` (`READ_EXTERNAL_STORAGE` on Android 12 and older) | List the phone's videos |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | GPS speed for the driving lock. The location is used only on the phone, never stored or sent. |
| `com.google.android.gms.permission.CAR_SPEED` | Optional second speed source from the car |
| `androidx.car.app.NAVIGATION_TEMPLATES`, `androidx.car.app.ACCESS_SURFACE` | Draw on the car screen |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing while the media card is shown |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS` | Optional screen-mirroring lab |

See [PRIVACY.md](PRIVACY.md).

## Known limitations

- **Installing the video screen needs a workaround** (see [Installation](#installation)). Google could block this method in a future Android Auto update.
- **The card next to the map cannot show moving video.** This is an Android Auto limitation.
- **The real Google Maps app cannot share the screen with the video screen.** Android Auto shows one full-screen app at a time; the side-by-side layout can show the Google Maps *website* instead.
- **Google sign-in in the embedded browser:** the browser identifies as mobile Chrome (WebView marker removed) so that Google offers sign-in. Google may still refuse sign-in in embedded browsers at any time.
- **DRM-protected video** (for example Netflix) may play black or not at all.
- **GPS speed** can be inaccurate near tall buildings and takes a moment to settle. The car speed sensor is used when the car provides it.
- **Location while the phone app is in the background:** the lock depends on Android delivering GPS updates while Android Auto shows the app. If no reading ever arrives, the picture stays allowed (see the table above).
- Codec support (HEVC, VP9, AV1, …) depends on the phone.
- Tested on an Android 17 emulator. See [docs/VERIFICATION.md](docs/VERIFICATION.md) for exactly what was tested and what still needs a real car.

## Building from source

Requirements: JDK 17, Android SDK platform 37 (`platforms;android-37.0`), build-tools 36 or newer. The Gradle wrapper (9.4.1) and AGP 9.2.1 are included.

```bash
# Debug builds (no signing setup needed)
./gradlew :app:assembleGithubDebug :app:assemblePersonalDebug

# Unit tests
./gradlew testGithubDebugUnitTest testPersonalDebugUnitTest testDebugUnitTest

# Instrumented tests (emulator or device connected)
./gradlew :app:connectedGithubDebugAndroidTest
```

On Windows use `gradlew.bat`.

### Release builds

Create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then build:

```bash
./gradlew :app:assembleGithubRelease :app:assemblePersonalRelease
```

Without `keystore.properties`, release APKs are built unsigned.

### Build flavors

| Flavor | Application ID | App name | Driving lock |
| --- | --- | --- | --- |
| `github` | `com.cartvlab.android` | CarScreen Play | Always on (`BuildConfig.DRIVING_LOCK = true`) |
| `personal` | `com.cartvlab.android.personal` | CarScreen Personal | Optional setting, off by default |

The two flavors have different application IDs, so both can be installed side by side.

## Project structure

| Module | Contents |
| --- | --- |
| `app` | Activity, navigation, Hilt, **Android Auto** (`car/`: car app service, screens, surface renderer, media service, shared player, speed lock, display settings) |
| `player` | Phone video player (Media3), playlists, subtitles |
| `browser` | Embedded browser, URL policy, shared WebView setup |
| `core` | Models, DataStore settings, design system, screenshot helper |
| `mirroring` | MediaProjection capture and optional LAN streaming |
| `diagnostics` | Device facts, permissions, report export |
| `settings` | Phone settings screen |
| `automotive` | Capability notes shown in diagnostics |
| `receiver` | Separate experimental LAN receiver APK (not part of the release) |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Documentation

- [docs/ANDROID_AUTO.md](docs/ANDROID_AUTO.md): how the car integration works, and how Android Auto decides which apps to show
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): code structure
- [docs/BROWSER.md](docs/BROWSER.md): browser, YouTube and sign-in
- [docs/CAR_TESTING.md](docs/CAR_TESTING.md): in-car test checklist
- [docs/VERIFICATION.md](docs/VERIFICATION.md): what was tested
- [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md): references and dependency versions
- [CHANGELOG.md](CHANGELOG.md) · [PRIVACY.md](PRIVACY.md)

## License

[MIT](LICENSE). The bundled test video and Persian subtitle file are CC0 (see [docs/TEST_ASSET_LICENSE.md](docs/TEST_ASSET_LICENSE.md)).
