# Météorite

A simple, ad-free weather app for Android. The interface is in French.

## What it does

- Save several cities (search by name, or use "Utiliser ma position"). Each city is a tab at the top;
  long-press a tab and drag it to reorder. The first tab is the one shown by the widget.
- 16-day forecast, one card per day: weather icon, max/min temperature, rain in plain words
  (no rain / light / moderate / heavy, plus roughly how many hours), snow in centimetres, and wind.
- Tap a day to see it hour by hour.
- Home-screen widget: weather now and the next 12 hours for the first tab, with a short sentence
  about the rain for the rest of the day. Its background follows the weather.
- The last downloaded forecast is kept on the phone, so the app and the widget always show
  something, together with the time of the last update.

## How it works

Weather and city search come from [Open-Meteo](https://open-meteo.com/); the town name of the phone
position comes from OpenStreetMap Nominatim. Both are free, with no account and no API key.
Cities are stored on the phone.

"Ma position" follows the phone: every minute while the app is open, and every ~30 minutes through
the widget even when the app is closed. The latter needs the "Toujours" location permission
(the app shows a banner to grant it). The widget refreshes about every 30 minutes (Android's minimum),
possibly later when the phone saves battery.

## Get the APK

Every push builds the APK with GitHub Actions. Open the **Actions** tab, pick the latest successful run,
download the `meteorite-debug-apk` artifact (a zip containing `app-debug.apk`) and install it on the phone.

## Build locally

JDK 17 and the Android SDK 35 are required:

```
./gradlew assembleDebug
```
