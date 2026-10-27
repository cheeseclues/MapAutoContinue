# Maps Auto-Continue

An Android companion app that automatically continues Google Maps navigation through intermediate stops without requiring manual interaction.

## Why this app exists

Google Maps allows users to create custom routes by adding multiple stops. However, when reaching an intermediate stop, Google Maps pauses navigation and requires the user to manually press **Next stop** before continuing.

This is particularly inconvenient for motorcyclists who use intermediate stops to create scenic routes rather than visit actual destinations.

**Maps Auto-Continue solves this problem by automatically pressing Next stop when the device is moving and Google Maps displays the appropriate arrival screen.**

You can continue using Google Maps for route planning, navigation, traffic information and voice guidance.

## Features

- Automatically continues Google Maps navigation at intermediate stops.
- Detects the **Next stop** button using Android Accessibility Services.
- Uses GPS movement to determine when automatic continuation is appropriate.
- Requires no interaction while riding.
- Runs alongside Google Maps.
- Provides manual Start and Stop monitoring controls.
- Displays a persistent notification while monitoring.
- No root access required.
- No Google Maps API key or subscription required.
- No accounts, telemetry or location uploads.

## How it works

1. Plan a route in Google Maps with multiple intermediate stops.
2. Open Maps Auto-Continue and enable its required permissions.
3. Press **Start ride monitoring**.
4. Open Google Maps and start navigation.
5. When Google Maps displays **Next stop**, the app checks the arrival screen and GPS movement.
6. If the required conditions are satisfied, the app automatically presses **Next stop**.

Google Maps then continues navigation toward the next destination.

## Requirements

- Android device
- Google Maps installed
- Accessibility Service permission
- Precise location permission
- Notification permission where required

## Installation

### Build with Android Studio

1. Clone or download this repository.
2. Open the project directory in Android Studio.
3. If the Gradle wrapper JAR is missing, run `prepare-wrapper.ps1` on Windows to download and verify the wrapper.
4. Sync the Gradle project.
5. Connect an Android device with USB debugging enabled.
6. Build and run the application.
7. Grant the required permissions.

## Safety and limitations

- The app only attempts automatic continuation when its movement and user-interface conditions are satisfied.
- Monitoring must be started manually.
- Google Maps updates may change its interface and break button detection.
- GPS speed and accuracy can affect behavior.
- Automatic continuation is not guaranteed in every situation.
- Configure the application before riding. Never operate your phone while riding.

## Privacy

Maps Auto-Continue does not require an account or internet permission.

Location information is used locally to evaluate movement conditions. The app does not upload GPS coordinates or navigation history.

Accessibility access is used to detect and interact with relevant Google Maps controls.

## Technical information

- Language: Kotlin
- Platform: Android
- Target SDK: 35
- Uses Android Accessibility Services and location APIs
- Package: `fi.cheeseclues.mapautocontinue`

## Disclaimer

Maps Auto-Continue is an independent project and is not affiliated with, endorsed by, or sponsored by Google.

Google Maps is a trademark of Google LLC.
