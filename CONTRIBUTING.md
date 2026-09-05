# Contributing

Thanks for helping improve Balatro Mod Manager for Android. Bug reports, focused
fixes, UI polish, tests, and documentation improvements are all welcome.

You do not need to understand the entire project before contributing. The root
[README](README.md) explains the app, and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) covers the main boundaries.

## Getting Started

1. Fork and clone the repository.
2. Open it in Android Studio with Java 17, or use the included Gradle wrapper.
3. Make a focused change and add a test when the behavior can be tested locally.
4. Run the checks below before opening a pull request.

```text
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Pull Requests

- Keep changes focused and explain the user-facing result.
- For behavior shared with the desktop
  [Balatro Mod Manager](https://github.com/skyline69/balatro-mod-manager), link the
  upstream file or rule used for comparison.
- Adapt filesystem behavior to Android's Storage Access Framework instead of
  assuming unrestricted file access.
- Do not include game files, downloaded mods, APKs, signing keys, private paths,
  or credentials.
- Keep user-facing text in English for the current beta.

For larger architectural changes, read the architecture document first or open an
issue so the approach can be discussed before implementation.
