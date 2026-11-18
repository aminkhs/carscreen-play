# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-18

First public release.

### Added
- **Android Auto video screen** (Car App Library, navigation template surface): video player, web browser / YouTube, phone video library, video links (MP4 / MKV / HLS), app playlist and a bundled test video.
- **Android Auto media entry** (Media3 `MediaLibraryService`): browse phone videos and play them with a card next to the map in Android Auto's split screen (sound, still video frame as cover image, controls). It shares one player with the video screen.
- **Driving lock** (always on in the public `github` build): above 30 km/h, measured with the phone's GPS (plus the car's speed sensor when available), the car screen shows only an audio icon while the sound continues. The video returns after 10 s at or below 30 km/h. It fails closed without location permission or with location off.
- **Car screen display settings**, on the car and on the phone: video position (full screen / right / left), width (40–70 %), picture mode (full size / cropped / stretched), browser beside the video, and collapsible control buttons (› / ‹).
- Touch on the car screen (tap, pan-drag, pinch zoom in the browser), car keyboard input into web pages, and desktop-site mode.
- Screenshots of the phone screen and the car screen, saved to `Pictures/CarScreen Play`.
- **Android Auto Setup** page: shows the install source that Android Auto checks, step-by-step setup, permissions and display settings.
- Shared Google / YouTube sign-in between the phone browser and the car browser.
- Build flavors `github` (public, lock always on) and `personal` (lock optional), and release signing through `keystore.properties`.

### Changed
- Google sign-in now happens in the embedded browser (Chrome mobile user agent) instead of a Custom Tab, so the car screen can use the same login.
- Release builds accept only the official Android Auto / Automotive hosts.

### Earlier development versions (0.1 – 0.5, unreleased)
- Phone player (Media3, subtitles, playlists), MediaProjection mirroring lab with an experimental LAN receiver, diagnostics export, and an embedded browser.

[1.0.0]: ../../releases/tag/v1.0.0
