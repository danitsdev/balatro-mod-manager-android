# Android Release Procedure

GitHub Releases must use a stable signing key. Never commit the keystore or its
passwords. Losing this key prevents users from installing future versions over an
existing release.

## One-time setup

1. Generate and securely back up an Android keystore:

   ```text
   keytool -genkeypair -v -keystore bmm-release.jks -alias bmm -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add these GitHub Actions repository secrets:

   - `ANDROID_KEYSTORE_BASE64`: the complete keystore encoded as single-line Base64.
   - `ANDROID_KEYSTORE_PASSWORD`: keystore password.
   - `ANDROID_KEY_ALIAS`: key alias, such as `bmm`.
   - `ANDROID_KEY_PASSWORD`: private-key password.

3. Store an offline backup of the keystore and credentials in a separate secure location.

## Publish a beta

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `./gradlew testDebugUnitTest lintDebug assembleRelease` with signing variables configured.
3. Commit the release changes and push the default branch.
4. Create and push a matching tag, for example `v0.1.0-beta.1`.
5. Confirm that the `Release Android APK` workflow passes.
6. Test the APK attached to the generated GitHub release on a clean device and as an update.

The workflow publishes `Balatro-Mod-Manager.apk` and `Balatro-Mod-Manager.apk.sha256`.
Because every release uses the same asset names, the latest APK always remains at
`releases/latest/download/Balatro-Mod-Manager.apk`. The
release job fails instead of producing an unsigned APK when signing secrets are missing.

## Local signed build

Set the following environment variables before running `assembleRelease`:

```text
BMM_KEYSTORE_PATH
BMM_KEYSTORE_PASSWORD
BMM_KEY_ALIAS
BMM_KEY_PASSWORD
```

The keystore path may be absolute. Files matching `*.jks`, `*.keystore`, and
`keystore.properties` are ignored by Git, but contributors should still keep keys
outside the repository directory.
