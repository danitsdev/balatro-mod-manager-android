# Balatro Mod Manager for Android

Balatro Mod Manager for Android is an open-source mobile app for finding,
installing, updating, and managing Balatro mods on Android.

[![GPL-3.0 license](https://img.shields.io/badge/license-GPL--3.0-4c8bf5)](LICENSE)
[![Android CI](https://github.com/danitsdev/balatro-mod-manager-android/actions/workflows/android.yml/badge.svg)](https://github.com/danitsdev/balatro-mod-manager-android/actions/workflows/android.yml)
![Android 8 or newer](https://img.shields.io/badge/platform-Android%208%2B-3DDC84)
![Beta status](https://img.shields.io/badge/status-beta-f2b84b)

[![Download latest APK](https://img.shields.io/badge/download-latest%20APK-2ea44f?logo=android)](https://github.com/danitsdev/balatro-mod-manager-android/releases/latest/download/Balatro-Mod-Manager.apk)

This project is directly inspired by
[Balatro Mod Manager by Skyline](https://github.com/skyline69/balatro-mod-manager)
and brings its familiar mod-management workflow to a mobile-first, native Android
interface. Mod discovery and metadata come from the open
[Balatro Mod Index](https://github.com/skyline69/balatro-mod-index) and its BMI API.

This is an independent community project, not an official upstream Android port.

## Features

- Browse, search, filter, and sort the online Balatro mod catalog.
- Install, update, remove, enable, and disable mods.
- Detect both catalog mods and local mods already present on the device.
- Replace a local copy with the catalog version through **Get official**.
- Resolve common dependencies such as Steamodded and Amulet during installation.
- View mod covers, authors, download counts, tags, repositories, and descriptions.
- Use light or dark themes, an optional animated background, and adjustable card sizes.
- Refresh the catalog and installed-mod state with pull to refresh.

## Install

1. [Download the latest APK](https://github.com/danitsdev/balatro-mod-manager-android/releases/latest/download/Balatro-Mod-Manager.apk).
2. Open the APK on your Android device and allow installation from that source if Android asks.
3. Launch Balatro Mod Manager and select the folder that contains your build's `ASET` directory.
4. Grant folder access. The app validates `ASET/Mods` before making any changes.

The current beta requires:

- Android 8.0 or newer.
- A user-created Balatro Android build prepared with
  [Lovely Mobile Maker](https://github.com/WilsontheWolf/lovely-mobile-maker).
- An `ASET/Mods` folder available through Android's document picker.
- Internet access for the catalog, covers, and mod downloads.

> This is beta software. Keep a backup of important saves and mods while testing
> installation, update, and removal flows.

## Build From Source

The app is built with Kotlin, Jetpack Compose, Java 17, and the Android Gradle plugin.
Open the project in Android Studio or use the included Gradle wrapper:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On Linux or macOS, use `./gradlew`. The debug APK is generated at
`app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```text
app/src/main/java/com/balatromodmanager/
  catalog/      Balatro Mod Index synchronization, search, and deduplication
  domain/       mod matching and update rules
  installer/    ZIP validation, installation, update, and removal
  settings/     persisted app preferences
  storage/      Android Storage Access Framework access and validation
  ui/           catalog, installed mods, settings, themes, and backgrounds
docs/           architecture and Android release guide
```

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the technical overview, or
start with [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

Balatro Mod Manager for Android is free and open-source software licensed under
the [GNU General Public License v3.0](LICENSE). You may study, modify, and
redistribute the code under the terms of that license.

Upstream and dependency attribution is recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE.md](NOTICE.md).

## Disclaimer

This repository does not contain Balatro, game APKs, or game assets, and it does
not patch the official Play Store release. Balatro is a trademark of LocalThunk.
This fan project is not affiliated with or endorsed by LocalThunk, Playstack, or
the maintainers of the desktop Balatro Mod Manager.
