# Browser and YouTube

## Phone

Open **YouTube & Browser** on the dashboard.

- **Sign in to YouTube (shared with car screen)** opens Google sign-in for YouTube in the embedded browser.
- **Open YouTube** opens `m.youtube.com`.
- **YouTube in Chrome tab (phone only)** opens YouTube in a Custom Tab. That login belongs to your browser and is **not** shared with the car.
- The address field accepts web addresses or search terms (Google search).
- Back / Next / Reload, HTML5 video fullscreen, and history restored after rotation.
- **More** → *Screenshot*, *Open in browser*, *Offline browser test* (bundled test page), *Allow local-network sites* (Android 17), *Clear embedded browsing data* (signs you out).

## Car screen

Menu → **YouTube**, **Search YouTube** or **Web browser**. Back, Type, zoom and reload are in the button strips; pan (✥) then drag to scroll. Menu → *Desktop site* switches to a desktop user agent. In the side-by-side layout, the browser can be shown next to the video (it opens the Google Maps website by default).

## Sign-in and cookies

The phone browser and the car browser are WebViews in the same app process, so they share cookies: **sign in once on the phone and the car is signed in too.**

Google blocks sign-in in many embedded browsers by looking for the WebView marker (`; wv`) in the user agent. `WebSetup` therefore uses the default WebView user agent with that marker and the `Version/x.y` token removed. The result is a normal mobile Chrome user agent. It also turns off the `X-Requested-With` header where WebView supports that. Google may still refuse sign-in in the future; if that happens, use the Chrome-tab option on the phone (not shared with the car).

The app never sees or stores your password. You type it on Google's own page. Cookies stay in the app's WebView storage on the phone. *Clear embedded browsing data* deletes them.

## Security settings

- Only `http` / `https` URLs. `javascript:`, `file:`, `content:`, `data:`, `intent:` and URLs with embedded credentials are blocked.
- No file or content access, no JavaScript-to-native bridge.
- Invalid TLS certificates are rejected.
- Camera, microphone and location requests from websites are denied.
- Safe Browsing is on. Automatic pop-ups are blocked.
- Third-party cookies are accepted (some Google / YouTube sign-in flows need them).
