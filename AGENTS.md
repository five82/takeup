# AGENTS.md

This file provides guidance when working with code in this repository.

## TL;DR

- Do not create git branches unless explicitly instructed.
- Run `./check-ci.sh` before handing work back.
- Test on the emulator, not the Pixel. Start the emulator yourself if it is not running.
- Debug builds install as `xyz.five82.takeup.debug`. Launch that, not `xyz.five82.takeup`.
- Video playback does not work on the emulator. Anything that needs a playing video - the player screen included - must be verified on the Pixel.

## Project

Takeup is an Android media player for Loom.

Single-developer hobby project - prefer simple, maintainable solutions over clever abstractions.

Loom and Takeup are developed and deployed together for one user. Do not preserve compatibility with older versions of either application; make coordinated changes in both repositories instead of adding compatibility shims.

## Visual Design Philosophy

Takeup should feel closer to Apple TV than to the typical black media-center interface: cinematic, artwork-led, colorful, and dimensional. Dark-only does not mean black-first.

- Do not default large surfaces to black or near-black merely because this is a media app. `Stage` is a deep indigo foundation, not the intended final appearance of every screen. Reserve true black primarily for video, necessary scrims, and other places where it has a concrete purpose.
- Let the current media artwork bring color and depth into a screen. Prefer blurred backdrop color, soft gradients, gauze, and localized light over empty dark fields.
- Prefer the visible landscape backdrop as the source for ambient color. Posters often overrepresent skin tones, reds, cream, and gold; do not let one poster swatch repaint a broad background.
- Preserve the spatial mixture of colors in artwork when possible. A blurred backdrop generally produces richer, more natural atmosphere than a single extracted dominant color.
- Avoid flat full-screen fills from one dynamic swatch. They frequently collapse varied artwork into muddy red-brown. Likewise, do not "fix" a poor dynamic treatment by stripping out the color and making the screen black; correct the source, blend, or treatment while retaining depth.
- Blend artwork with chromatic `Stage` scrims to maintain text contrast and cool muddy imagery without erasing its character. Tune scrims only as dark as readability requires.
- Do not ban warm hues globally. Genuinely warm artwork should remain warm; avoid ugly repetition through better artwork sources and spatial treatments rather than arbitrary hue filters.
- Reuse the established visual language (`BiasCutBackdrop`, `GauzeBackground`, woven accents, and thread lighting) instead of introducing an unrelated generic Material look.
- Judge visual changes on the emulator using real library artwork and the whole composed screen, not isolated components or assumptions. Check that backgrounds support cards, labels, logos, and navigation while still carrying visible color.

## Critical Expectations

- Apply YAGNI ("You Aren't Gonna Need It") and KISS ("Keep It Simple, Stupid"). Build only what the current task requires; do not add abstractions, generality, or future-proofing for needs that do not yet exist. When two approaches work, take the simpler one.
- Prefer self-documenting code and local comments over separate documentation. Comments should explain non-obvious constraints, tradeoffs, invariants, historical context, or surprising decisions rather than restating the code.
- Prefer opinionated defaults over exposing more user-facing configuration. Add configuration only when there is a clear recurring need.
- Coordinate major tradeoffs with the user; never unilaterally defer functionality.
- Keep edits ASCII unless the file already uses extended characters.
- When troubleshooting, gather evidence and test rather than guessing.
- Add focused tests for new behavior and regressions.
- Follow established Android and project conventions. Do not add libraries, frameworks, or architectural layers without a concrete need.

## Build, Test, Lint

Run the canonical local tests, Android lint, and debug build:

```bash
./check-ci.sh
```

Run device tests separately when an emulator or device is available:

```bash
./gradlew connectedCheck
```

## Emulator

The emulator is the default target for everything it can run: UI, layout, navigation, `connectedCheck`, install-and-poke smoke checks, and reproducing bugs. It is faster and less cumbersome to drive than the Pixel. Playback is the one thing it cannot do - see below.

A Pixel connected over USB is not a reason to skip the emulator, and neither is a stopped emulator. If `takeup_pixel10pro` is not running, start it and wait for boot; the one-time boot cost is worth it.

The `takeup_pixel10pro` AVD (API 37, arm64) matches the physical device geometry. Neither `java` nor the SDK is on PATH by default:

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# Start it if emulator-5554 is not already listed by `adb devices`.
emulator -avd takeup_pixel10pro &
# wait-for-device alone returns before boot finishes, so poll sys.boot_completed.
adb -s emulator-5554 wait-for-device shell \
  'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
ANDROID_SERIAL=emulator-5554 ./gradlew installDebug
```

Leave the emulator running between tasks. Only kill it when the user asks:

```bash
adb -s emulator-5554 emu kill
```

Always target a device explicitly with `ANDROID_SERIAL` or `adb -s`; the Pixel is often connected over USB at the same time, so a bare `adb` command or `./gradlew installDebug` can land on the wrong one.

`applicationIdSuffix = ".debug"` means `installDebug` installs alongside the release build rather than over it, under a different package and with its own settings and permission grants:

```bash
adb shell am start -n xyz.five82.takeup.debug/xyz.five82.takeup.ui.MainActivity
```

The activity keeps its unsuffixed name, so the component is `.debug/xyz.five82.takeup.ui.MainActivity`. Launching `xyz.five82.takeup` instead drives the release build, which silently shows none of the changes just installed - it looks exactly like a change that did not work.

Multicast does not cross the emulator NAT, so enter Loom's IP and port rather than an mDNS name.

## Pixel

Video playback does not work on the emulator, so every change that has to be seen playing is verified on the physical Pixel. Say so when you use it.

That covers more than decode, HDR, and audio output: the player screen itself only exists over a playing video, so transport controls, the scrub bar, seeking, track selection, chapters, and the end-of-playback overlay are all Pixel work. Reaching the player is not enough - if the check needs frames on screen, it belongs on the Pixel.

Everything else stays on the emulator.
