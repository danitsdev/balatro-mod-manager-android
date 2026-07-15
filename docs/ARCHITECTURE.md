# Architecture

Balatro Mod Manager is a native Android app built with Kotlin and Jetpack Compose.
It follows the desktop Balatro Mod Manager behavior where practical, with Android
Storage Access Framework operations replacing direct filesystem access.

## Main Areas

```text
catalog/      Balatro Mod Index API, cache, search, and deduplication
domain/       catalog/local matching and update rules
installer/    local scanning, ZIP validation, install, update, and removal
settings/     DataStore-backed visual preferences
storage/      persisted SAF folder access and ASET/Mods validation
ui/           Compose screens, cards, themes, and backgrounds
```

`MainViewModel` owns screen state and starts operations. UI components render that
state and optimistically reflect simple actions such as enable, disable, and
removal while storage work finishes.

## Catalog

The app loads its private catalog cache immediately, then refreshes from the
Balatro Mod Index API when that cache is older than one hour or the user pulls to
refresh. A first launch without cache loads directly from BMI. The last valid
response remains available if a later refresh fails. Coil handles cover-image
memory and disk caching.

Catalog deduplication, search, sorting, matching, and version checks live outside
the Compose UI so the same rules can be unit tested.

## Storage

The user grants access to the game build folder with Android's document picker.
The app persists that SAF grant and only operates inside `ASET/Mods`.

Installations are downloaded to private cache, validated as ZIP archives, and then
copied through SAF. The installer rejects unsafe paths, unsupported entries,
conflicts, and oversized archives before committing files.

## Installed Mod States

- **Managed:** installed by this app and backed by a private manifest.
- **Local:** detected in `ASET/Mods` without a matching managed manifest.
- **Get official:** a local mod matches a catalog entry and can be replaced by that entry.
- **Update:** a managed mod has a non-empty catalog version different from its manifest version.

Enable and disable state is read from `.lovelyignore` markers and the Steamodded
blacklist. A refresh always rescans device storage instead of assuming the last UI
state is authoritative.

## Verification

Run the complete local check before a pull request:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Tests cover catalog behavior, desktop-parity matching, dependency resolution,
archive safety, local metadata, and storage validation rules.
