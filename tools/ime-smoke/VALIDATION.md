# Validation record — 2026-11-09 (Asia/Seoul)

Current base: merged `main` at `baf52651e66985eaff0ba000be1683f27fcd6a66`
(PRs #9–#13), plus this smoke-test change. PR #8 remains open and unchanged;
it is not included in this branch. Current raw evidence is retained locally
under `build/ime-smoke-validation/` in `ime-smoke-main-20261109`.

| Check | Result | Evidence |
| --- | --- | --- |
| Full existing unit suite | PASS: 285 tests, zero failures/errors/skips | `current-main-build.log`, `app/build/test-results/testRunTestsUnitTest/` |
| x86_64 keyboard, editor and instrumentation APKs | PASS | `current-main-build.log`, `fixture-rebuild.log` |
| API 35/default/x86_64 Pixel 2 emulator, matching CI image/profile | PASS: enable/select IME, real q/w/e touches produce exactly `qwe`, settings menu visible | `api35-adb/result.json`, `instrumentation.txt`, `device-evidence/` |
| Same API 35 test with Android CLI diagnostics | PASS: test plus CLI layout JSON and screenshot, no diagnostic/cleanup errors | `api35-cli/` |
| IME/settings restoration and removal of newly installed apps | PASS for both real tests and the injected failure | Each run's `result.json` and `commands.log` |
| Controlled instrumentation timeout | PASS: runner exits 1, preserves partial output, restores real device settings and packages | `inject-timeout.py`, `negative-timeout/`; only the timeout was injected, not a real 180-second hang |
| Missing device | PASS: exit 1, FAIL JSON, no device mutation | `negative-device/` |
| Workflow, Python, XML, whitespace | PASS | actionlint 1.7.12, Python AST, XML parsing, `git diff --check` |

API 35 fingerprint:
`Android/sdk_phone64_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`.
System image package: `system-images;android-35;default;x86_64`, revision 2.
The official Android CLI installed that image and command-line tools; standard
`avdmanager` created the Pixel 2 AVD. WHPX provides host virtualization.
The dedicated ADB server on 5038 and emulator on 5556 preserve shared physical
device sessions. Screenshots were inspected: the editor displays `qwe` and the
settings screen displays Languages & Layouts.

Keyboard APK SHA-256:
`5aa184abb6761e0e221193ae24543dce8c56a94126e75892c4189b0996170a53`.
All three APK hashes, unique evidence run IDs and original settings are in the
per-run JSON files. Timeout diagnostics and current-run evidence isolation were
added during this continuation.

Hosted validation is tracked in [PR #15](https://github.com/GPLaider/KeliBoard/pull/15).
The [first hosted run](https://github.com/GPLaider/KeliBoard/actions/runs/42505387801)
passed the unit-test job, then failed before the device test because
`sdkmanager` was not on PATH. The workflow now explicitly initializes Android
SDK tools with `android-actions/setup-android` before package installation.
See the PR's latest checks for the hosted result; local API 35 results alone
do not establish a hosted CI pass. No physical device was
used in this continuation; no physical typing/settings pass is claimed. No
Google Cloud authentication, billing setup or remote device reservation occurred.

## Earlier validation — 2026-11-01 (Asia/Seoul)

Base checkout: `590febd5c4e955c0bcd7470b1a37e52dab2f68a5`, plus the working
tree changes implementing this smoke test. Raw evidence is retained locally
under `build/ime-smoke-validation/` (ignored build output).

## Verified

| Check | Result | Evidence |
| --- | --- | --- |
| Existing `testRunTestsUnitTest`, after accessibility fix | PASS: 266 tests, 0 failures, 0 errors, 0 skipped | `gradle-final.log`, `app/build/test-results/testRunTestsUnitTest/` |
| Keyboard + fixture + instrumentation builds, ARM64 and x86_64 | PASS | `gradle-jdk21.log`, `gradle-x86_64.log`, `gradle-fixture-api23.log` |
| Enable/select KeliBoard IME | PASS on local API 36 x86_64 emulator | `emulator-final/result.json` |
| Touch visible q/w/e keys; assert exact editor text `qwe` | PASS | `emulator-final/device-evidence/01-keyboard-input.{png,xml}` |
| Launch main settings; assert its localized language/layout menu | PASS | `emulator-final/device-evidence/02-settings.{png,xml}` |
| Additional Android CLI layout JSON and screenshot | PASS, no diagnostic errors | `emulator-final/android-layout.json`, `android-screen.png` |
| Restore original selected/enabled IMEs and hardware-keyboard setting; remove newly installed test apps | PASS, no cleanup errors | `emulator-final/result.json`, `commands.log` |
| ADB-only execution immediately after CLI diagnostics | PASS; no UiAutomation conflict | `emulator-adb-pass/` |
| Missing serial fails without device mutation | PASS: exit 1 and FAIL JSON | `negative-missing-device/` |
| Assertions fail despite `am instrument` exiting 0; evidence and cleanup still run | PASS: script exits 1 | `emulator-adb/`, `phone-third/` |
| Workflow static validation | PASS with actionlint 1.7.12 | `.github/workflows/build-test-auto.yml` |
| Python syntax, fixture XML, whitespace | PASS | `ast.parse`, XML parsing, `git diff --check` |

Environment: Windows with WHPX, Temurin JDK 21.0.13, Gradle 8.14, AGP 8.13.2,
SDK 36, NDK 28.0.13004108, Python 3.12, Android CLI 1.1.16717390, official
Codex plugin `android-cli@android-cli-plugin` 1.0.16625370 (installed/enabled).
The plugin's bundled skill was read and its CLI workflow used in this session;
new Codex sessions load installed plugin skills normally.

Emulator: `medium_phone`, API 36, x86_64 Google Play image, fingerprint
`google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys`.
Android CLI created the AVD and downloaded the local system image. The shared
ADB server ran under another Windows account, so its key did not match the
emulator's host account. The successful run used a separate local ADB server
on port 5038 and emulator serial `emulator-5556`, preserving the existing
physical-device server. The test script inherits standard ADB environment
variables; most machines can use the default server and `emulator-5554`.

Representative commands (PowerShell, with JDK 21 and SDK configured):

```powershell
.\gradlew.bat testRunTestsUnitTest :app:assembleSmoke :tools:ime-smoke:assembleDebug :tools:ime-smoke:assembleDebugAndroidTest '-Pandroid.injected.build.abi=x86_64'
# Only for this session's separate local ADB server:
$env:ANDROID_ADB_SERVER_PORT = '5038'
$env:ADB_SERVER_SOCKET = 'tcp:localhost:5038'
python tools/ime-smoke/run.py --serial emulator-5556 --android-cli D:/AgentWork/cache/android-cli/android.exe
```

The final recorded run started at `2026-10-31T17:39:25Z` (Nov 1 in Korea),
returned exit 0 and `OK (1 test)`, completed all three checks, and reported
empty cleanup/diagnostic error lists. Screenshots were visually inspected:
`qwe` is visible in the fixture and the KeliBoard Settings menu is rendered.
APK hashes are recorded in that run's `result.json`.

## Issues found and repaired

- The original CI JDK 17 could not create Robolectric's API 36 sandbox
  (`Android SDK 36 requires Java 21`). The test workflow now uses JDK 21;
  production bytecode remains Java/Kotlin 17. No unit-test assertions were removed.
- The original accessibility provider cached the keyboard's screen origin while
  it was still at `(0,0)`. The failing emulator evidence shows `q` at
  `[0,15][109,176]` while its host was at `[0,1607][1080,2400]`. Real key touches
  missed the keyboard and the exact-text assertion failed. Refreshing the host
  position for each virtual-key query made that same touch test pass.
- CLI `layout` leaves an instrumentation helper connected. Running UI Automator
  while it remains active caused `UiAutomationService ... already registered`.
  The optional CLI path releases that helper before and after diagnostics;
  the subsequent ADB-only run passed.
- The fixture handles empty accessibility text being `null`, declares package
  visibility for the isolated keyboard, and avoids Java stream APIs so its
  advertised API 23 minimum does not depend on API 24 library methods.

## Limits

- Physical A063/API 36 runs enabled and selected the isolated IME, but system
  emergency-alert dialogs blocked the editor focus. These runs failed clearly
  and restored the original IMEs/settings; physical typing/settings success is
  **not verified**. The other connected phone was locked and was not tested.
  Existing production and `.debug` app data was preserved.
- GitHub Actions was implemented and statically checked; no commit was pushed
  and no hosted workflow was dispatched. The configured API 35/default x86_64
  CI image has **not** been executed in this session. Runtime success here is
  on the local API 36 Google Play emulator.
- Lower API levels, other screen configurations, localized settings other than
  the emulator's English locale, Korean composition, gesture input, and minified
  debug/release variants have not been validated by this smoke test.
- No remote reservation, Google Cloud authentication, billing setup, or paid
  remote device operation was performed.

## Cleanup

New smoke packages were removed after successful and failed device runs. The
temporary emulator and the separate ADB server were stopped after validation.
The reusable AVD, SDK, CLI, JDK, current APKs, original failure logs and visual
evidence were retained for reproduction. Duplicate JDK/actionlint installer ZIPs
were removed through the host's Safe-Cleanup harness: 207,900,346 file bytes;
the observed D: free-space increase was 207,904,768 bytes (about 198 MiB).
`build/ime-smoke-validation/cleanup.json` records the measurement.
