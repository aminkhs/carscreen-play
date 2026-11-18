# How the Android Auto integration works

## 1. Video screen: Car App Library surface

Android Auto does not run ordinary Android activities on the car screen. Third-party apps use the **Car App Library** (`androidx.car.app`, version 1.7.0 here). The app declares:

```xml
<service android:name=".car.PlayCarAppService" android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService"/>
        <category android:name="androidx.car.app.category.NAVIGATION"/>
    </intent-filter>
</service>
```

plus `<uses name="template"/>` in `res/xml/automotive_app_desc.xml`, the `androidx.car.app.NAVIGATION_TEMPLATES` / `ACCESS_SURFACE` permissions and `androidx.car.app.minCarApiLevel = 2`.

The navigation category is the only one whose template (`NavigationTemplate`) gives the app a raw `Surface` covering the car display. `CarSurfaceRenderer` uses that surface like this:

1. `AppManager.setSurfaceCallback()` receives the car `Surface`, its size and its DPI.
2. A private `VirtualDisplay` is created on that surface.
3. A `Presentation` (a window on the virtual display) hosts normal Android views: a Media3 `PlayerView` or a `WebView`.
4. Car touch input arrives as `SurfaceCallback.onClick` and `onScroll`. These are replayed as `MotionEvent` taps and drags on the presentation.
5. `onVisibleAreaChanged` pads the content so it is not hidden under Android Auto's button strips. Collapsing the buttons enlarges the visible area.

The template's action strips (at most 4 buttons each) carry the controls. Text input uses `SearchTemplate`, whose keyboard Android Auto usually offers only while parked.

**Host validation:** release builds accept only the hosts in the Car App Library allowlist (`R.array.hosts_allowlist_sample`, which contains the official Android Auto and Android Automotive hosts). Debug builds accept any host, so the Desktop Head Unit works.

## 2. Media entry: the card next to the map

`CarMediaService` is a Media3 `MediaLibraryService` (`<uses name="media"/>`). It gives Android Auto:

- A browse tree: *Phone videos*, *App playlist*, *Test video*.
- A media session on the **same ExoPlayer** that the video screen uses (`SharedPlayer`).

In Android Auto's split-screen layout, the small panel beside the navigation app shows the active media session. Android Auto renders that card itself, from metadata only: title, artwork and transport controls. **Apps cannot draw into it, so moving video is impossible there.** `SharedPlayer` adds a still video frame (taken with `MediaMetadataRetriever` at about 3 s) as artwork, so the card shows a picture of the current video.

## 3. Why the app may not appear in the Android Auto launcher

Android Auto checks every car app before listing it. Community analysis of Android Auto 17.x describes these checks ([Fermata issue #846](https://github.com/AndreyPavlenko/Fermata/issues/846), [Lapin issue #1](https://github.com/prototux/lapin/issues/1)):

- The **installer**: Android Auto asks who installed the package. A package installed by Google Play, or reported as such by the system package installer, passes.
- **Unknown sources** (Android Auto developer settings) admits sideloaded apps only for some app types: media, notification, SMS and a few others. **Template apps (Car App Library) are not admitted this way.**
- `adb install -i com.android.vending` fails because the *initiating* package is the shell.

What this means for this app:

| Entry | Sideloaded + Unknown sources | Installed with KingInstaller (Shizuku) |
| --- | --- | --- |
| Media entry | Shown | Shown |
| Video screen | **Hidden** | Shown (reported working on Android 16; not guaranteed) |

The **Android Auto Setup** page in the app reads `PackageManager.getInstallSourceInfo()` and shows the installing and initiating package, so you can see which case applies.

If the video screen still does not appear:

1. Check that the setup card is green.
2. Force-stop Android Auto and clear its cache. Rejections are cached per package and version.
3. Make sure *Unknown sources* is on and the app is ticked in *Customize launcher*.
4. Reconnect the phone. Some head units cache the app list until a reconnect.
5. Note your Android Auto version and report it in an issue.

Other ways that bypass the installer check: Google's Desktop Head Unit (for development), or distribution through Google Play (for example an internal testing track).

## 4. Driving lock

See the table in the [README](../README.md#driving-lock-public-build). Implementation:

- `SpeedLockPolicy`: pure rules (30 km/h limit, 10 s release delay, fail closed without permission or with location off). Unit tested.
- `SpeedGuard`: GPS updates every second (with speed computed from consecutive fixes if a fix has no speed), the optional car speed sensor, and a 1 s re-evaluation tick.
- `CarController.onLockState`: covers the car screen with an opaque audio-only overlay. The content keeps running underneath, so sound continues.

`BuildConfig.DRIVING_LOCK` is `true` in the `github` flavor (always on) and `false` in the `personal` flavor (user setting, off by default).

## 5. Display settings

`CarDisplay` (stored in DataStore as one string) holds: position (full / right / left), video width (40–70 %), picture mode (full size / cropped / stretched, mapped to Media3 resize modes FIT / ZOOM / FILL), browser beside the video, buttons hidden, and the driving-lock toggle (personal build only). The phone's setup page and the car's *Display settings* screen edit the same values live through `CarDisplayPrefs`.
