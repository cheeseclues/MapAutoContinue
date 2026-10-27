# Maps Auto-Continue v0.4 — Android Studio project

Companion app for Google Maps. This version removes the **Detect only** switch entirely: once you deliberately start ride monitoring, the service attempts to press Google Maps' **Next stop** button automatically when the movement and UI checks pass. The Google Maps route and GPS navigation are still handled by Google Maps.

## What changed from v0.3

- Removed the **Detect only (no automatic taps)** checkbox and the associated settings code.
- Removed the detection-only early exit in the Accessibility Service. **This also ignores any old dry-run setting left over from v0.3**, so installing over an existing installation will not silently disable automatic tapping.
- Displays a read-only status message: **Automatic continuation is enabled while ride monitoring is active**.
- Persistent notification explicitly says **Auto-continue on**.
- Kept the manual **Start ride monitoring** and **Stop monitoring** controls, including notification **Stop ride** action.
- Kept all checks in v0.3: Google Maps foreground, a recognized clickable waypoint control, arrival context, fresh/accurate GPS speed above the configured threshold for at least 2 seconds, the button being visible for 2.5 seconds, and a 15-second cooldown between attempts.

## Install in Android Studio (Windows)

1. Extract this ZIP and open the `MapAutoContinue` directory in Android Studio (it contains `settings.gradle.kts`).
2. If the Gradle wrapper JAR is missing, open PowerShell in this directory and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\prepare-wrapper.ps1
   ```

   This downloads the official Gradle wrapper and verifies its checksum. Gradle dependencies will still need to be downloaded during sync.
3. Sync Gradle, select your emulator or connected phone, and click **Run**. The package name has not changed (`fi.cheeseclues.mapautocontinue`), so this installs as an update when signed with the same debug key.
4. Enable the app's Accessibility Service if Android requests it. Grant precise location and notifications when prompted.
5. Open the app, tap **Start ride monitoring**, then open Google Maps. Auto-continue is now active. You can disable it by tapping **Stop monitoring** in the app or **Stop ride** in the notification.

## Test

The v0.3 emulator test from Oct 11, 2026 showed that the app found Google Maps' `Next stop` control and that `ACTION_CLICK` resumed Google Maps navigation. The v0.4 modifications have **not** been compiled or executed in this environment.

Before riding, repeat the test in the emulator with a multi-stop route and simulated GPS speed above the threshold. In Android Studio Logcat filter:

```
tag:MapAutoContinue
```

Successful output includes `Found waypoint 'Next stop' ...` and `ACTION_CLICK sent to 'Next stop' — verify Maps advanced`. Confirm that navigation actually progresses and that it doesn't advance at zero speed or the final destination. Never interact with the device while riding.

## Boundaries and privacy

This app is not affiliated with Google. Its Accessibility Service can inspect Google Maps UI only as configured by Android's accessibility permission. No internet permission, accounts, telemetry, or location uploads. It may stop working if Google Maps changes its accessibility hierarchy. The app still needs to be deliberately started for each monitoring session.

The project currently targets Android SDK 35, uses Kotlin, and includes `prepare-wrapper.ps1` for wrapper setup. It is prototype software: emulator success does not guarantee reliability on a physical phone.
