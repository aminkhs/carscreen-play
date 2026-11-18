# Verification: version 1.0.0 (2026-11-18)

This file records checks that were actually run. No physical phone, Android Auto head unit or car was available.

## Build

- Gradle 9.4.1, AGP 9.2.1, JDK 17, compile / target API 37, minimum API 29.
- `assembleGithubRelease`, `assemblePersonalRelease`, `assembleGithubDebug`: **BUILD SUCCESSFUL** (including lint-vital checks for release).
- Both release APKs are verified with `apksigner` (v2 scheme). Certificate SHA-256: `60f973bfc9d627a4190e3ddb5ac88e6608f01a03fe6f7847984455b71061a9fb`.

## Unit tests: 28 passed, 0 failed

| Suite | Tests |
| --- | --- |
| `SpeedLockPolicyTest` (30 km/h limit, 10 s release, stale readings, fail closed) | 7 |
| `CarDisplayTest` (settings storage, cycling) | 3 |
| `CapabilityPolicyTest` | 3 |
| `BrowserUrlPolicyTest` | 4 |
| `BoundedInputTest` | 3 |
| `MirrorProtocolTest` | 4 |
| `PlaylistParserTest` | 4 |

The app-module suites pass in both the `github` and the `personal` flavor.

## Instrumented tests (Android 17 AOSP emulator, x86_64): 6 of 7 passed

Passed: dashboard + Android Auto entry points registered (`CarAppService` in the navigation category, and `MediaBrowserService`), screen capture consent / frames / stop, browser JavaScript / navigation / rotation, HTML5 video fullscreen and pause, Google sign-in loading inside the WebView (no `; wv` in the user agent) and clearing cookies.

Failed: `bundledVideoRendersThenPausesAndSeeks`. The emulator's H.264 decoder reported `MediaCodecVideoRenderer error` (Codec2 BufferPool errors). **The previous build (0.2.0, without any of these changes) fails the same way on the same emulator session**, so this is an emulator decoder problem, not a regression. The same test passed on 2026-11-16 with a different emulator session.

## Manual emulator checks

- Release APKs of both flavors install side by side (`com.cartvlab.android` and `com.cartvlab.android.personal`), and both register the car app service and the media service.
- *Android Auto Setup* shows the install source correctly (adb install → red), the display settings, "Driving lock: always on" (github) and the optional lock switch (personal).
- Display settings persist across app restarts.
- The phone Screenshot button saves a correct PNG to `Pictures/CarScreen Play`.

## Not verified (needs a real phone with Android Auto and a car)

- The video screen appearing in the Android Auto launcher after a KingInstaller install.
- Rendering on the car screen, touch / pan / zoom, collapsible buttons, side-by-side layouts, picture modes and the car screenshot.
- The media card next to the map, including the cover frame.
- The driving lock with real GPS while driving, and the car speed sensor.
- Google / YouTube sign-in with a real account.
