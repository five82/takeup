<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/takeup-logo-dark.png">
    <source media="(prefers-color-scheme: light)" srcset="docs/takeup-logo-light.png">
    <img src="docs/takeup-logo-light.png" alt="Takeup logo" width="220">
  </picture>
</p>

# Takeup

Takeup is a native Android client for [Loom](https://github.com/five82/loom), a personal media server for movies, short films, and TV. It browses a Loom library, plays original media directly, keeps viewing progress in sync, and supports full-file downloads for offline playback.

Takeup and Loom are designed together for a single user on a trusted local network.

## Features

- Home discovery with Continue Watching, Next Up, recently added titles, and rotating recommendations
- Separate movie, short film, and TV libraries
- Browsing by collection and genre
- Search across titles, episodes, cast, and crew, including fuzzy matches
- Movie, series, season, and episode details with credits and technical media information
- Direct playback with resume, chapter seeking, audio and subtitle selection, crop controls, and next-episode navigation
- Watched-state management and automatic playback progress reporting
- Full-file downloads with offline playback and deferred progress sync
- Poster, backdrop, logo, and thumbnail selection from Loom's TMDB artwork options
- Manual Loom library scans from the app

## Requirements

- Android 12 or newer (API 31+)
- A running Loom server reachable over the local network

Loom does not authenticate requests and currently serves trusted-LAN traffic over HTTP. Do not expose it directly to the internet.

Loom streams source files without transcoding or remuxing. Playback support therefore depends on the codecs and containers supported by the Android device. Offline downloads are also full-size copies of those source files.

## Getting started

1. Configure Loom, scan your media libraries, and start its server. Loom listens on port `8097` by default.
2. Install Takeup on the Android device.
3. Grant local-network access if Android prompts for it.
4. Enter the Loom host and port, for example `192.168.1.20:8097`.

The server address can be changed later from **Settings**.

## Building from source

The project requires JDK 17 or newer and an Android SDK with API 37 installed.

Build a debug APK:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install it on a connected device or emulator:

```bash
./gradlew installDebug
```

Run the canonical local checks (unit tests, Android lint, and a debug build):

```bash
./check-ci.sh
```

Instrumented tests can be run separately with an emulator or device available:

```bash
./gradlew connectedCheck
```

## Technology

Takeup is written in Kotlin with Jetpack Compose and Material 3. Playback and downloads use AndroidX Media3; network requests use OkHttp; images are loaded with Coil; and local settings are stored with DataStore.

## License

Takeup is licensed under the [GNU General Public License v3.0](LICENSE). The bundled Google Sans Flex font is distributed under the SIL Open Font License; see [`licenses/google-sans-flex-OFL.txt`](licenses/google-sans-flex-OFL.txt).
