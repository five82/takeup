# Takeup

Native Android client for the [Loom](../loom) media server.

Milestone 1 connects to a Loom server, lists the first 50 movies, direct-plays a
selected movie with Media3, and saves playback progress every 15 seconds and
when playback stops.

## Requirements

- Android Studio Quail 3 (2026.1.3) or newer
- Android SDK Platform 37
- A phone running Android 17 for local-network permission testing
- Loom running on the same trusted LAN and listening on a LAN address

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
so only use Takeup on a trusted network.

## Verify

```bash
./gradlew test lint assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`.

## Playback scope

Loom serves original files without transcoding or remuxing. Playback therefore
depends on the container, codecs, embedded tracks, and decoders available to
Media3 and the phone. Milestone 1 intentionally exposes Media3's standard player
controls so seeking, audio tracks, and subtitles can be tested against real
library files before the browsing UI is expanded.
