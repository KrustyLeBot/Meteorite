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
Cities are stored in `Documents/Meteorite/cities.json` (`{"format_version": 1, "cities": [...]}`,
written atomically through a `.tmp` file). That folder needs the "all files access" permission,
which the app only asks for when you set up the Proton Drive sync; until then the list stays in the
app's private storage. "Ma position" is only a placeholder in the file: its coordinates belong to
the phone and stay private, so moving around never rewrites the synced file. An unreadable file is
reported in the app and never overwritten.

## Proton Drive sync (Synchrotron)

When [Synchrotron](https://github.com/KrustyLeBot/Synchrotron) is installed, the app offers to sync
`Documents/Meteorite` with `Apps/Meteorite` on Proton Drive (newer file wins, every 6 hours and on
every change). Each time the app opens it asks Synchrotron for a sync; the city list is read-only
while it runs and is re-read afterwards. On a new phone, the private list moves to the folder only
after that first sync, merged with the saved one, so a fresh install never replaces your cities.

"Ma position" follows the phone: every minute while the app is open, and every ~30 minutes through
the widget even when the app is closed. The latter needs the "Toujours" location permission
(the app shows a banner to grant it). The widget refreshes about every 30 minutes (Android's minimum),
possibly later when the phone saves battery.

## Get the APK

Every push to `main` builds the APK with GitHub Actions and publishes it as the single `latest`
release (`Meteorite.apk`, titled "Build N"; N is also the APK version code). Download it from the
repository's Releases page. Pull requests only upload the APK as a workflow artifact.

In the app, the update button in the top bar compares the installed build with the latest release,
downloads the new APK and opens Android's install screen (the phone asks once to allow installs
from this app). The downloaded file is deleted the next time the updated app starts.
Every build is signed with the same key (`app/debug.keystore`), so updates install over the old app.

## Build locally

JDK 17 and the Android SDK 35 are required:

```
./gradlew assembleRelease
```
