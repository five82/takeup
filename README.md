# Takeup

Native Android client for the [Loom](../loom) media server.

Takeup connects to a Loom server and provides a phone-focused home screen with
Continue Watching, Recently Added, movie, show, and season poster artwork,
backdrops, title logos, and complete movie and TV show libraries. Shows can be
browsed by season and episode, with runtime, playback progress, and source
video/audio badges shown for each episode. Movie and episode details report
codec, resolution class, dynamic range, audio codec,
and channel layout from Loom's `ffprobe` metadata. Browsing screens support
pull-to-refresh and preserve existing content when a refresh fails. Takeup
direct-plays selected media with Media3 and saves playback progress every 15
seconds and when playback stops.

## Requirements

- Android Studio Quail 3 (2026.1.3) or newer
- Android SDK Platform 37
- A phone running Android 17 for local-network permission testing
- An up-to-date Loom server with technical stream metadata, running on the same
  trusted LAN and listening on a LAN address

Android Studio includes the required JDK. During its first-run setup, install the
Android SDK and then use **Tools > SDK Manager** to install **Android SDK
Platform 37** if it is not already installed.

## Run on a Pixel

1. On the phone, enable **Developer options** by tapping **Build number** seven
   times under **Settings > About phone**.
2. Enable **USB debugging** under **Settings > System > Developer options**.
3. Connect the phone to the Mac with USB and accept its debugging prompt.
4. Open this repository in Android Studio and allow the Gradle sync to finish.
5. Select the phone in the device menu and click **Run**.
6. Grant Takeup's local network permission.
7. Enter Loom's LAN URL, for example `http://192.168.1.20:8097`.

Use the server's LAN address, not `localhost` or `127.0.0.1`: those addresses
refer to the phone itself. Loom has no authentication and currently uses HTTP,
so only use Takeup on a trusted network. Takeup intentionally rejects Loom
responses that predate dynamic-range and channel-layout stream metadata.

## Verify

```bash
./gradlew test lint assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`. Install an update
on a connected phone with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Playback scope

Loom serves original files without transcoding or remuxing. Playback therefore
depends on the container, codecs, embedded tracks, and decoders available to
Media3 and the phone. Playback switches to immersive, sensor-aware landscape
and exposes Media3's standard controls for seeking, audio-track selection, and
embedded subtitles. The player reports selected track labels, provides a visible
subtitle button, and offers Replay, Play Next, and Back to Season when an episode
ends. A Crop/Fit toggle fills the display without distorting the video or returns
to the complete uncropped frame.

Android TV navigation and TV-specific layouts are intentionally deferred.
