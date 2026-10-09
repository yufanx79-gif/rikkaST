# Building rikkaST

rikkaST is an Android application (Kotlin + Jetpack Compose + Chaquopy). This public version contains the SillyTavern-compatible layer and the general tool system; the optional divination/astrology module has been removed.

## Requirements

- JDK 17 or newer (JDK 21 recommended)
- Android SDK with the platforms/build-tools required by the project (`compileSdk = 37`)
- Python 3.12 for Chaquopy (set `chaquopy.python` in `local.properties` or `CHAQUOPY_PYTHON`)
- Node.js + pnpm if you want to rebuild `web-ui` (the repository also contains a prebuilt static web UI under `web/src/main/resources/static`)

## Local configuration

Create `local.properties` in the repository root (it is git-ignored):

```properties
sdk.dir=/path/to/Android/Sdk
chaquopy.python=/path/to/python3.12
```

Release signing is optional for local builds. Add the following only if you build a release APK:

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## Common commands

```bash
# Compile debug Kotlin
./gradlew :app:compileDebugKotlin -x :app:installDebugPythonRequirements

# Run JVM unit tests
./gradlew :app:testDebugUnitTest -x :app:installDebugPythonRequirements

# Build a debug APK
./gradlew :app:assembleDebug -x :app:installDebugPythonRequirements

# Build a release APK (requires signing config)
./gradlew :app:assembleRelease -x :app:installDebugPythonRequirements
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

> Note: the Gradle wrapper uses the official Gradle distribution URL. If your environment needs an offline/local distribution, change `gradle/wrapper/gradle-wrapper.properties` locally (do not commit machine-specific paths).

## License

This project is released under **AGPL-3.0**. See `LICENSE` and `THIRD-PARTY-NOTICES.md`.
