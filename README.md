# Balatro Mod Manager for Android

Install and manage mods on Android builds of Balatro. The catalog and package downloads come from the [Balatro community on Thunderstore](https://thunderstore.io/c/balatro/).

[![GPL-3.0 license](https://img.shields.io/badge/license-GPL--3.0-4c8bf5)](LICENSE)
[![Android CI](https://github.com/danitsdev/balatro-mod-manager-android/actions/workflows/android.yml/badge.svg)](https://github.com/danitsdev/balatro-mod-manager-android/actions/workflows/android.yml)
![Android 8 or newer](https://img.shields.io/badge/platform-Android%208%2B-3DDC84)

[Download the latest published APK](https://github.com/danitsdev/balatro-mod-manager-android/releases/latest/download/Balatro-Mod-Manager.apk)

## What it does

- Browse, search, filter, and sort Balatro packages from Thunderstore.
- Read a package's full README and dependencies, then choose which version to install.
- Install or update Thunderstore mods. Enable, disable, and remove installed mods.
- Import mod and modpack ZIPs from your device.
- Manage mods copied into the game folder, even when they are not listed on Thunderstore.
- Choose a dark or light theme and a small, medium, or large card density. The number of cards per row adapts to the available screen width.
- See a notice when a newer version of this app is published on GitHub.

Manually copied and imported mods do not have automatic updates.

## Set up the game folder

You need Android 8 or newer, internet access, and a Balatro Android build created with [Lovely Mobile Maker](https://lmm.shorty.systems/).

1. Create and install the game build with Lovely Mobile Maker.
2. Open the game once. Its first launch may be rough; opening it creates the `ASET/Mods` folder the manager needs.
3. [Install the latest APK](https://github.com/danitsdev/balatro-mod-manager-android/releases/latest/download/Balatro-Mod-Manager.apk). Allow installation from your browser or file manager if Android asks.
4. Open the manager and select the parent folder containing `ASET`. Grant access with **Use this folder**.

The app stores that folder permission and manages mods inside `ASET/Mods`.

## Import a ZIP

On **Installed**, choose **Import mod / modpack** and select an archive containing mod files. An archive with multiple mod folders imports each one. A Thunderstore manifest by itself only lists packages; it does not contain their files.

Imported mods can be enabled, disabled, or removed from **Installed**. Their updates must be installed manually.

## Build

Use JDK 17 and the Android SDK. Open the project in Android Studio or build with the included Gradle wrapper:

- Linux or macOS: `./gradlew assembleDebug`
- Windows: `gradlew.bat assembleDebug`

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project docs

- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Releasing](docs/RELEASING.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## License

The app is licensed under [GPL-3.0](LICENSE). This project is an independent fan project. It does not include Balatro, game APKs, or game assets and is not affiliated with LocalThunk or Playstack.
