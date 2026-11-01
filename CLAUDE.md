# DyslectoReader

Android reading-ruler overlay app (Kotlin + Jetpack Compose, `org.dyslectoreader`).

## Versioning & APK naming

- The app follows **semantic versioning** (`MAJOR.MINOR.PATCH`). Current version: **v1.1.1**.
- **Always bump the version when making a new build** that contains code changes
  (patch bump by default; minor/major when the change warrants it).
- The single source of truth is `val appVersion` at the top of [app/build.gradle.kts](app/build.gradle.kts).
  `versionName` comes from it directly and `versionCode` is derived via
  `semverToCode()` (`major * 1_000_000 + minor * 1_000 + patch`).
- Bumping the version means editing `appVersion` only — nothing else hardcodes it.
  Update the "Current version" line above in the same change.
- **Every build renames the APK to `DyslectoReader-v<version>.apk`** (e.g.
  `DyslectoReader-v1.0.2.apk`), configured through the `androidComponents { onVariants { ... } }`
  block in `app/build.gradle.kts`. Output lands in
  `app/build/outputs/apk/<buildType>/DyslectoReader-v<version>.apk`.
- The version is surfaced in the app via `BuildConfig.VERSION_NAME` — shown in the
  Settings → About section and on the About screen.

## Build

```bash
./gradlew assembleDebug     # or assembleRelease
```
