# Météorite

A simple, ad-free weather app for Android. The interface is in French.

## What it does

- Save several cities (search by name, or use "Utiliser ma position"). Each city is a tab at the top.
- 16-day forecast, one card per day: weather icon, max/min temperature, rain in plain words
  (no rain / light / moderate / heavy, plus roughly how many hours), and wind.
- Tap a day to see it hour by hour.
- Home-screen widget: weather now and the next 12 hours for the first saved city, with a short
  sentence about the rain for the rest of the day.

## How it works

Weather and city search come from [Open-Meteo](https://open-meteo.com/): free, no account, no API key.
Cities are stored on the phone. The phone location is only used when you ask for it ("Ma position");
the app refreshes it each time it opens.

## Get the APK

Every push builds the APK with GitHub Actions. Open the **Actions** tab, pick the latest successful run,
download the `meteorite-debug-apk` artifact (a zip containing `app-debug.apk`) and install it on the phone.

## Build locally

JDK 17 and the Android SDK 35 are required:

```
./gradlew assembleDebug
```
