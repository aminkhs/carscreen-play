# References and dependency versions

Checked 2026-11-18.

## Android Auto

- Car App Library overview and templates: https://developer.android.com/training/cars/apps
- Navigation apps and map surface (`SurfaceCallback`, `NavigationTemplate`): https://developer.android.com/training/cars/apps/navigation
- Car App Library releases (1.7.0 used): https://developer.android.com/jetpack/androidx/releases/car-app
- Media apps for cars (`MediaLibraryService`, browse tree): https://developer.android.com/training/cars/media
- Car hardware data (speed via `CarHardwareManager`): https://developer.android.com/training/cars/apps#access-car-hardware
- Host validation (`HostValidator`, `hosts_allowlist_sample`): https://developer.android.com/reference/androidx/car/app/validation/HostValidator
- Testing with the Desktop Head Unit: https://developer.android.com/training/cars/testing/dhu

Google's official guidance does **not** list video or browser apps for Android Auto. This project uses the navigation-template surface for personal / sideloaded use. It is not eligible for Google Play's Android Auto distribution in this form.

Community analysis of how Android Auto 17.x decides which sideloaded apps to show (installer check, *Unknown sources* scope):

- https://github.com/AndreyPavlenko/Fermata/issues/846
- https://github.com/prototux/lapin/issues/1
- KingInstaller: https://github.com/fcaronte/KingInstaller
- Shizuku: https://shizuku.rikka.app/

## Platform

- Android 17 (API 37) SDK setup: https://developer.android.com/about/versions/17/setup-sdk
- Location (`LocationManager`, permissions): https://developer.android.com/develop/sensors-and-location/location
- MediaProjection: https://developer.android.com/media/grow/media-projection
- Local-network permission (Android 17): https://developer.android.com/privacy-and-security/local-network-permission
- MediaStore and saving images without storage permission: https://developer.android.com/training/data-storage/shared/media

## Pinned dependencies

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1 |
| Kotlin / Compose compiler plugin | 2.3.10 |
| KSP | 2.3.13 |
| Compose BOM | 2026.09.00 |
| Car App Library (`app`, `app-projected`) | 1.7.0 |
| Media3 (exoplayer, hls, ui, session) | 1.11.1 |
| AndroidX WebKit | 1.18.0 |
| AndroidX Browser | 1.10.0 |
| Activity | 1.13.0 |
| Lifecycle | 2.11.0 |
| AndroidX Core | 1.19.1 |
| DataStore | 1.3.0 |
| Hilt | 2.60.1 |
| Coroutines | 1.10.2 |
| JUnit | 4.13.2 |

Dependencies come only from Google Maven, Maven Central and the Gradle Plugin Portal. No alpha or beta libraries are used.
