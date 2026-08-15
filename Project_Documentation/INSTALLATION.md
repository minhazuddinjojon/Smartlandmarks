# Installation Guide

## Requirements

| Item | Version |
|---|---|
| Android Studio | Ladybug (2024.2.1) or newer |
| JDK | 17 (bundled with Android Studio) |
| Gradle | 8.9 — supplied by the wrapper, do not install separately |
| Android Gradle Plugin | 8.7.2 |
| Compile / Target SDK | 35 |
| Min SDK | 24 (Android 7.0) |

## Steps

1. Extract the archive.
2. In Android Studio: **File → Open**, select the project root (the folder containing
   `settings.gradle.kts`). Do not open the `app` folder.
3. Wait for the Gradle sync. First sync downloads all dependencies and takes a few
   minutes.
4. Select a device or emulator, then **Run**.

Use an emulator image **with Google Play services** — location comes from
`FusedLocationProviderClient`, which is unavailable on plain AOSP images.

## The API key

Already set. `gradle.properties`, one line:

```properties
SMART_LANDMARKS_API_KEY=22301003
```

It is injected into `BuildConfig.API_KEY` at build time and appears nowhere else in the
source. To change it, edit that line and rebuild. An environment variable of the same
name overrides it, which is how CI would supply it without editing files.

## Command line

```bash
./gradlew assembleDebug          # build the APK
./gradlew installDebug           # build and install
./gradlew test                   # JVM unit tests
./gradlew lint                   # Android lint
./gradlew assembleRelease        # minified release build (R8)
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Setting an emulator's location

Extended controls (`...`) → **Location** → enter coordinates → **Send**. Somewhere in
Bangladesh is useful, e.g. 23.7192, 90.3882 (Lalbagh Fort).

## Testing offline behaviour

1. With the app open and data loaded, enable Airplane mode.
2. The offline banner appears; landmarks still render from cache.
3. Record a visit — it appears in Activity as **Queued**.
4. Add a landmark — it is saved locally.
5. Disable Airplane mode. Within seconds the worker runs: the visit goes
   **Queued → Processing → Done** with a distance, and the landmark uploads.

To prove restart survival: queue a visit offline, force-stop the app, restore
connectivity, reopen. The periodic worker picks the queue up.

## Troubleshooting

**Blank map.** osmdroid needs network for tiles. Confirm connectivity; check Logcat for
`osmdroid`. The user agent is set in `SmartLandmarksApp` — tile servers reject the
default, which shows as blank tiles rather than an error.

**Every request returns 403.** The key is wrong, mistyped, or from a previous semester.
Check `gradle.properties`, then rebuild — a Gradle sync alone does not regenerate
`BuildConfig`.

**Location never resolves.** Grant the permission, enable device location, and use a
Play-services emulator image. Indoors, `getCurrentLocation` can return null; the code
falls back to last known location.

**Visits stay "Processing".** Expected for a few seconds. If it persists, the worker is
not running — verify connectivity (its constraint is `CONNECTED`) and check Logcat for
`WM-WorkerWrapper`. Battery optimisation on some OEM builds (Xiaomi, Oppo, Huawei) will
defer WorkManager aggressively; exempt the app when testing on those.

**`Unable to instantiate worker`.** Hilt's `WorkerFactory` is not wired. This needs both
halves: `Configuration.Provider` in `SmartLandmarksApp` **and** the
`tools:node="remove"` entry for `WorkManagerInitializer` in the manifest.

**Build fails on KSP or Hilt.** Run `./gradlew clean`, then **File → Invalidate Caches
and Restart**.
