# Architecture

Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines / StateFlow, DataStore, Media3, Car App Library. Minimum Android 10 (API 29), compile / target Android 17 (API 37).

## Modules

- **app**: the phone `MainActivity`, Compose navigation, `LabViewModel`, Hilt composition root (`AppModule`), the *Android Auto Setup* screen, and the Android Auto integration in `app/src/main/java/com/cartvlab/android/car/`:
  - `PlayCarAppService` / `PlaySession`: Car App Library entry point (navigation category). Host allowlist in release builds.
  - `CarController`: owns one car session. Holds the mode (home / video / browser), the side-by-side layout, display settings and the driving lock.
  - `CarSurfaceRenderer`: car `Surface` → `VirtualDisplay` → `Presentation`. Handles touch injection, the visible-area padding, the opaque overlay and screenshots.
  - `CarScreens`: templates. `MainCarScreen` (`NavigationTemplate` with collapsible strips), `MenuScreen`, `DisplaySettingsScreen`, `VideoLibraryScreen`, `InputScreen` (`SearchTemplate`).
  - `CarMedia`: `CarVideoPlayer` (`PlayerView` on the shared player) and `CarBrowser` (car `WebView`).
  - `SharedPlayer`: the one ExoPlayer for the whole process, with video-frame cover art.
  - `CarMediaService`: Media3 `MediaLibraryService`, which provides the browse tree and the card next to the map.
  - `SpeedGuard` / `SpeedLockPolicy`: the GPS (and car sensor) driving lock.
  - `CarDisplay` / `CarDisplayPrefs`: display settings shared live between the phone and the car.
- **core**: models, `SettingsStore` (DataStore), design system, evidence journal, receiver framing, and `Screenshots` (PixelCopy of the window plus `SurfaceView`s, saved to MediaStore `Pictures/CarScreen Play`).
- **player**: the phone player (`Media3PlayerController`), M3U parser and subtitle loading.
- **browser**: the phone `BrowserActivity` / `BrowserController`, `BrowserUrlPolicy`, and `WebSetup`, the WebView configuration shared with the car browser (Chrome mobile user agent, cookies).
- **mirroring**: MediaProjection capture service, encoder and LAN transport (experimental).
- **diagnostics**: device / display / permission facts and JSON / TXT export through the Storage Access Framework.
- **settings**: the phone settings screen.
- **automotive**: capability descriptions shown in diagnostics.
- **receiver**: a separate experimental LAN receiver APK. It is not part of the release.

## Build flavors

`distribution` dimension:

- `github`: `DRIVING_LOCK = true`, app name "CarScreen Play", ID `com.cartvlab.android`.
- `personal`: `DRIVING_LOCK = false`, app name "CarScreen Personal", ID `com.cartvlab.android.personal`.

The app name comes from the `app_name` string defined per flavor, and every manifest label uses it.

## Threading and lifecycle

- Car callbacks, the renderer and the players run on the main thread. MediaStore queries and thumbnail extraction run on `Dispatchers.IO`.
- A car session is created by Android Auto. `CarController.release()` runs when the session is destroyed: it stops the driving lock, releases the virtual display and pauses the shared player.
- `SharedPlayer` lives as long as the process, because the car screen and the media service both use it. The media service releases only its session.
- The WebViews on the phone and in the car run in the same process, so they share the default cookie store.
