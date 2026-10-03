# Contributing

## Set up

Use JDK 17 and the Android SDK. Open the project in Android Studio or use the Gradle wrapper.

Before opening a pull request, run:

    ./gradlew testDebugUnitTest lintDebug assembleDebug

On Windows, use gradlew.bat with the same tasks.

## Changes

- Keep each pull request focused and describe the user-visible change.
- Follow Android's Storage Access Framework for game-folder access; do not assume unrestricted filesystem access.
- When a change follows behavior in the desktop manager, link the relevant upstream source.
- Keep user-facing copy in English.
- Do not commit APKs, game or mod archives, signing keys, credentials, or private device paths.

See [Architecture](docs/ARCHITECTURE.md) for the app's main boundaries.
