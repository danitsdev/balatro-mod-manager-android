# Android releases

A release must be signed with the same Android keystore used for earlier releases. If that key is lost, users cannot install a new release over an existing one.

## Signing setup

Generate a keystore and keep an offline backup outside the repository:

    keytool -genkeypair -v -keystore bmm-release.jks -alias bmm -keyalg RSA -keysize 4096 -validity 10000

Add these GitHub Actions secrets to the repository:

- ANDROID_KEYSTORE_BASE64: the keystore encoded as a single-line Base64 value.
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

## Publish

1. Update versionCode and versionName in app/build.gradle.kts.
2. Add release notes in docs/releases/<version>.md.
3. Commit and push the changes to the default branch.
4. Push the matching tag, such as v0.2.0.
5. The release workflow checks the tag, runs unit tests and lint, builds and verifies a signed APK, then publishes the APK and SHA-256 file to GitHub Releases.
6. Install the published APK on a clean device and test upgrading from the previous release.

The workflow attaches Balatro-Mod-Manager.apk and Balatro-Mod-Manager.apk.sha256. The latest APK is available at releases/latest/download/Balatro-Mod-Manager.apk.

## Local signed build

Set BMM_KEYSTORE_PATH, BMM_KEYSTORE_PASSWORD, BMM_KEY_ALIAS, and BMM_KEY_PASSWORD, then run:

    ./gradlew assembleRelease

The key path may be absolute. The repository ignores common keystore files, but keep signing material outside the working tree.
