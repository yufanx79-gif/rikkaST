# Known Limitations & Environment Blockers (`KNOWN_LIMITATIONS.md`)

## 1. Release Verdict: `CONDITIONALLY VERIFIED — NOT CLEARED FOR RELEASE`

In accordance with the non-negotiable release rules:
- All host-executable test suites (`node --test scripts/*.test.mjs` both standalone `68/74 PASS, 6 env-gated SKIP` and with `ALL_NEEDS_DIR` `74/74 PASS, 0 FAIL`), Python SQLite Room migration verification (`5/5` paths `PASS`), upstream inventory checks, and security/regex/cache regression tests pass from a clean extraction.
- All 8 defects identified during the independent second-pass audit (`F-01` through `F-08`) have been fixed via TDD (`RED -> Minimal Fix -> GREEN`).
- However, because the current Linux sandbox VM lacks an Android SDK (`ANDROID_HOME`), `javac`/`kotlinc`, outbound network access (`services.gradle.org`), the private release signing keystore, and an attached Android device (`adb`), the Android compilation, unit/instrumentation execution, APK signing, and in-place `adb install -r` gates are **`BLOCKED`**. Therefore, this archive's status is **`CONDITIONALLY VERIFIED — NOT CLEARED FOR RELEASE`**.

## 2. Environment-Blocked Checks (`BLOCKED` / `NOT RUN`)

1. **Gradle Android Build, JUnit Execution, Instrumentation & Lint Gates (`BLOCKED`)**:
   - **Commands Attempted / Affected**:
     - `./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest :workspace:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace`
     - `./gradlew --no-daemon :app:assembleRelease` (`NOT RUN` — no release keystore supplied)
   - **Observed Failure / Blocker**:
     - Invoking `./gradlew` fails immediately with exit code `1` (`java.net.SocketException: Network is unreachable` while downloading `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip`).
     - The container has JRE `17.0.18` (`java` only, no `javac` or `kotlinc`) and no Android SDK (`ANDROID_HOME` unset).
   - **Required Next Step on an Android Build Host**:
     - Run the Gradle command above on a machine with JDK 17+, Android SDK API 36, and Gradle 9.3.1 cached or online, plus `./gradlew :app:connectedDebugAndroidTest` to execute `Migration_29_32_Test.kt` under Android `MigrationTestHelper`.

2. **In-Place APK Upgrade (`adb install -r`) & Public APK Certificate Continuity (`BLOCKED`)**:
   - **Blocker**:
     - Neither `rikkaST-2.5.6-public-20261020 (1).zip` nor `10-22-0-fixed-1.zip` contains a `.apk` binary or the release signing key, and no Android device is connected (`adb` not installed).
     - Historical certificate forensics (`SHA-256: b01fb58500aa307fbba70a0c0f242d949b131435e81b16acbd3e5b365ee45748` from `rikkaST-2.5.7-universal-release.apk`) are documented in `APK_FORENSICS_REPORT.md` and `update.json.historicalArtifacts`.
   - **Required Next Step on an Android Build Host**:
     - Sign the assembled `2.5.9` (`versionCode = 241`) APK with the same release certificate (`b01fb58500aa307fbba70a0c0f242d949b131435e81b16acbd3e5b365ee45748`), verify via `apksigner verify --print-certs`, and run `adb install -r` over a populated `2.5.6` (`versionCode = 238`) installation without clearing app data.

3. **Real Device Long-Floor GPU/Frame & Touch UX Verification (`BLOCKED`)**:
   - **Blocker**:
     - Host benchmarks (`scripts/perf-benchmark.mjs` and 1/50/200/500-floor `TavernChatJsonCache` timings) verify algorithmic cache hit rates and regex/remount bounds in Node V8, but cannot substitute for physical Android WebView `CrRendererMain` frame-pacing and memory telemetry.

## 3. Functional Boundaries & Safe Updater Behavior

1. **Safe `update.json` While Binary Is Pending Build (`F-02`)**:
   - `update.json` has `version = "2.5.9"`, `versionCode = 241`, `artifactStatus = "SOURCE_ONLY_PENDING_SDK_BUILD"`, and **`"downloads": []`**.
   - Historical `2.5.7` APK metadata is isolated under `"historicalArtifacts"` so neither `UpdateChecker.kt` (`info.eligibleDownloads`) nor `UpdateCard.kt` can ever offer a stale `2.5.7` binary as a `2.5.9` update.
   - Even if a remote `update.json` erroneously includes a `historical-*` status or mismatched version URL in `downloads`, `UpdateChecker.validateBeforeDownload` and `UpdateInfo.eligibleDownloads` now block it in code.
2. **SillyTavern Desktop ComfyUI Server Extensions (`/api/sd/comfy/*`)**:
   - ST-iPhonie and LittleWhiteBox support OpenAI-compatible (`/v1/images/generations` via `/proxy/*` or `/api/rikkahub/v1/images/generations`) and NovelAI image generation in the WebView runtime.
   - Desktop-only `/api/sd/comfy/*` endpoints return an explicit HTTP 501 JSON error (`code: "UNSUPPORTED_DESKTOP_COMFY_EXTENSION"`) in `runtime.js` rather than failing silently or forwarding to arbitrary hosts.
3. **CORS Proxy SSRF & Redirect Policy (`F-03`)**:
   - `/proxy/*` and `dispatchProxyHttpRequestAsync` strictly permit only public `http:`/`https:` targets and block loopback (`localhost`, `127.0.0.0/8`, `::1`), Android emulator host aliases (`10.0.2.2`, `10.0.3.2`), RFC1918 private networks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local/cloud metadata (`169.254.0.0/16`, `fe80::/10`), and CGNAT (`100.64.0.0/10`), including across HTTP `301/302/303/307/308` redirects (validated hop-by-hop with `instanceFollowRedirects = false` and cross-origin sensitive header stripping).
