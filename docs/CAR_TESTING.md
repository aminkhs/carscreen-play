# In-car test checklist

Do every on-screen interaction **while parked**. Driving-lock tests need a passenger, or a safe empty area at low speed with someone else operating the phone.

## Before you start

- [ ] Note the phone model, Android version, Android Auto version, app version and build (`github` / `personal`).
- [ ] Note the car or head-unit model, and whether you connect wired or wireless.
- [ ] *Android Auto Setup*: the install card is green (needed for the video screen).
- [ ] Permissions granted: video library, location, car speed.
- [ ] Android Auto developer settings: *Unknown sources* on. Android Auto cache cleared after installing.

## Launcher

- [ ] The video screen entry appears in the Android Auto launcher.
- [ ] The media entry appears (it can be a second icon with the same name).

## Video screen

- [ ] Menu → Test video: moving picture and 440 Hz tone through the car speakers.
- [ ] Phone videos: the list loads; a local MP4 plays.
- [ ] Play video URL: an HTTPS MP4 and an HLS `.m3u8` play.
- [ ] Pause / Play, rewind / forward 10 s and the picture mode button (Full size → Cropped → Stretched).
- [ ] Tap on the video: the player controls appear and react.
- [ ] Display settings: Right side and Left side at 40 / 50 / 60 / 70 %, and the browser on the other side.
- [ ] The › arrow hides the buttons and the video uses the freed space; the ‹ arrow restores them.
- [ ] The camera icon / Menu → Screenshot saves an image in the gallery that shows the car screen, including the video frame.

## Browser

- [ ] YouTube loads. Signed in after signing in on the phone.
- [ ] Taps select videos; pan (✥) + drag scrolls; zoom in / out; Back; Reload.
- [ ] Type: text appears in the YouTube search field (try Persian). *Press Enter* submits.
- [ ] YouTube fullscreen video works and Back leaves fullscreen.
- [ ] Desktop site switches the layout.

## Media card next to the map

- [ ] Play a video, then open Google Maps. The small card shows the title, a still frame and the controls.
- [ ] Pause / next from the card control the same playback.
- [ ] Return to the video screen: the same video continues.

## Driving lock (public build)

- [ ] Parked: the video is visible.
- [ ] Above 30 km/h: the audio-only screen appears within a few seconds and the sound continues.
- [ ] Below 30 km/h: the video returns about 10 s later.
- [ ] Location turned off on the phone: audio only, with a "Turn on location" message.
- [ ] Location permission revoked: audio only, with a permission message.
- [ ] Also check that the lock covers the browser (YouTube) and the side-by-side layout.

## Report

Export *Diagnostics* and attach it to an issue together with car-screen screenshots. Leave out cookies, passwords and private links.
