# KeliBoard IME smoke test

This small cross-app test enables KeliBoard, touches its actual on-screen `q`,
`w`, `e` keys in a disposable editor, asserts the exact text `qwe`, and opens the
main settings screen and checks its language/layout menu. UI Automator locates
virtual keys by package and accessibility description, so there are no fixed
screen coordinates. It does not inject text, send letter keycodes, set editor
text, or call `InputConnection.commitText` to manufacture the result.

The test exposed stale accessibility key coordinates when the keyboard's root
was queried before layout. `KeyboardAccessibilityNodeProvider` now refreshes the
host's screen position for each key query. The smoke test fails with the old
coordinates and verifies real touch input with the corrected coordinates.

The test uses an ASCII, visible-password editor with suggestions and learning
disabled. It covers the basic IME touch/input connection and settings startup;
it does not cover Korean composition, predictions, gestures, every layout,
release/R8 behavior, or the interactive Android enable-keyboard consent screen.

## Official Android CLI and Codex setup

Checked against Google's documentation on 2026-11-09:

- [Android Dev Summit announcement](https://developer.android.com/blog/posts/android-dev-summit-26-developer-tools-performance)
- [Android CLI overview](https://developer.android.com/tools/agents/android-cli)
- [Official downloads and OS-specific installation](https://developer.android.com/tools/agents/android-cli/download)
- [Official agent plugin instructions](https://developer.android.com/tools/agents/android-cli/agent-plugins)
- [Google's plugin repository](https://github.com/android/android-cli-plugin)
- [OpenAI plugin documentation](https://developers.openai.com/codex/plugins/)

Linux x86-64, using Google's user-local installer:

```sh
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
android --no-metrics --version
```

Windows, from PowerShell, using the official download directly (the documented
installer and `winget install --id Google.AndroidCLI` are also available):

```powershell
New-Item -ItemType Directory -Force "$env:LOCALAPPDATA/AndroidCLI" | Out-Null
Invoke-WebRequest https://dl.google.com/android/cli/latest/windows_x86_64/android.exe -OutFile "$env:LOCALAPPDATA/AndroidCLI/android.exe"
$env:PATH = "$env:LOCALAPPDATA/AndroidCLI;$env:PATH"
android --no-metrics --version
```

The binary and plugin are separate installations. Install Google's plugin with
these exact commands, then start a new Codex session to load its bundled skill:

```sh
codex plugin marketplace add android/android-cli-plugin
codex plugin add android-cli@android-cli-plugin
codex plugin list
```

For an agent without the plugin, `android --no-metrics init` installs the
`android-cli` skill. Installing the plugin already supplies that skill.
Use `android --no-metrics update` to update the CLI when desired.

| Operation | Required tools | Is the Codex plugin required? |
| --- | --- | --- |
| Build keyboard and test APKs; keep existing unit tests | Gradle, JDK, Android SDK/NDK | No |
| Install APKs, enable/select IME, run assertions, restore settings, collect logs/screens/XML | ADB + Android instrumentation/UI Automator | No |
| Obtain additional JSON layout and screenshot evidence with `--android-cli` | Android CLI, which uses the local SDK/ADB | No; the CLI executable is sufficient |
| Create/start a local AVD through `android emulator` | Android CLI and local virtualization | No |
| Give Codex Google's bundled Android CLI instructions | Official Codex plugin | Yes, for that plugin integration; `android init` is an alternative skill route |
| Connect a remote Google lab device | Android CLI, Google account/project and possibly billing | No; separate, explicit user opt-in is needed |

The CI job uses a local runner emulator. Neither the test nor CI calls
`android device remote`, reserves a device, authenticates to Google Cloud, or
sets up billing. A remote device, if a user separately connects one, appears in
`adb devices` and can then be selected with `--serial`.

## Build and run with ADB

Prerequisites: JDK **21** for Robolectric's Android 36 sandbox, Python 3.10+,
Android SDK platform 36, build-tools 36.0.0, NDK 28.0.13004108, platform-tools,
and an unlocked, booted API 23+ device/emulator. The app's Java/Kotlin bytecode
target remains 17 and its minimum Android API remains 21. Set `ANDROID_HOME`
and `JAVA_HOME`, and add SDK `platform-tools` to `PATH` (or pass `--adb`).

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'ndk;28.0.13004108' platform-tools
./gradlew testRunTestsUnitTest
./gradlew :app:assembleSmoke :tools:ime-smoke:assembleDebug :tools:ime-smoke:assembleDebugAndroidTest
adb devices -l
python tools/ime-smoke/run.py --serial emulator-5554
```

In PowerShell, use `.\gradlew.bat` in place of `./gradlew`. For a faster build
for a single ABI, append `'-Pandroid.injected.build.abi=x86_64'` for an x86_64
emulator, or `'-Pandroid.injected.build.abi=arm64-v8a'` for an ARM64 phone. Omit
the property to build all supported ABIs. Keep the quotes in PowerShell.

To add Android CLI diagnostics to that same deterministic test:

```sh
python tools/ime-smoke/run.py --serial emulator-5554 --android-cli android
```

Pass an absolute executable path if `android` or `adb` is not on PATH. Use a
different empty `--output` directory for each run. Without `--serial` or
`ANDROID_SERIAL`, the script runs only if exactly one device is ready.

Android CLI 1.1.16717390 leaves `com.android.cli.interact.instrumentation` running
after `android layout`. It competes with UI Automator for Android's single
UiAutomation connection. With `--android-cli`, the script force-stops that
helper before the test and after optional CLI diagnostics. Do not run other
UI automation on the same serial concurrently. If a previous manual CLI
inspection holds the connection, either use `--android-cli` or stop that helper
before an ADB-only run. An `already registered` failure is a connection conflict,
not a passing test.

The APK names/version are read from AGP's `output-metadata.json` under
`outputs/apk` or, for ABI-injected builds, `intermediates/apk`. The most recently
written metadata is used. Only these
isolated application IDs are accepted:

- Keyboard: `io.github.gplaider.keliboard.smoke` (`:app:assembleSmoke`, unminified).
- Editor: `io.github.gplaider.keliboard.imesmoke`.
- Instrumentation: `io.github.gplaider.keliboard.imesmoke.test`.

No production or `.debug` app data is cleared. The script records the current
Android user, default/enabled IMEs and `show_ime_with_hard_keyboard`, runs the
test, restores and verifies those settings in `finally`, then removes only
smoke packages that were absent for that Android user before the run. `--keep-installed` retains
new smoke packages for investigation while still restoring the IME settings.
Avoid concurrent device tests on the same serial. If the host is forcibly
killed or the device disconnects, restore the values recorded in
`result.json`/`commands.log` after reconnecting:

```sh
adb -s SERIAL shell ime set --user USER ORIGINAL_IME_COMPONENT
adb -s SERIAL shell ime disable --user USER io.github.gplaider.keliboard.smoke/helium314.keyboard.latin.LatinIME
adb -s SERIAL shell settings --user USER put secure show_ime_with_hard_keyboard ORIGINAL_VALUE
```

Only disable the smoke IME if it was previously disabled. For an original
hardware-keyboard value of `null`, use `settings ... delete secure
show_ime_with_hard_keyboard` instead of `put`.

## Start a local emulator

Android CLI can create and boot an AVD without Android Studio:

```sh
android --no-metrics emulator create --list-profiles
android --no-metrics emulator create medium_phone
android --no-metrics emulator list
android --no-metrics emulator start --headless medium_phone
adb devices -l
```

The profile determines the system image/API level; verify it with
`adb -s SERIAL shell getprop ro.build.version.sdk`. The first creation downloads
a system image. To reproduce the CI API 35 image with standard SDK tools:

```sh
sdkmanager emulator 'system-images;android-35;default;x86_64'
echo no | avdmanager create avd --name keliboard-smoke-api35 --package 'system-images;android-35;default;x86_64' --device pixel_2
emulator -avd keliboard-smoke-api35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
```

Start the last command in a separate terminal, wait for
`adb -s SERIAL shell getprop sys.boot_completed` to print `1`, then run the
test. Hardware virtualization is required for practical emulator performance.

## Results and failures

Output defaults to `build/ime-smoke/<UTC timestamp>/`:

- `result.json`: PASS/FAIL, unique run ID, individual completed checks, device fingerprint,
  APK SHA-256 hashes, saved original settings, cleanup and diagnostic errors.
- `commands.log` and `instrumentation.txt`: executed commands, assertion failures,
  stack traces and `[IME SMOKE]` step markers, including partial output on timeout.
- `logcat.txt`: logs from this run filtered to crash/ANR and IME/test tags;
  the device log buffer is never cleared.
- `input-method.txt`, `activities.txt`, `accessibility.txt`: IME binding,
  activity, and UI automation connection diagnostics.
- `device-evidence/`: screenshots and XML hierarchy after text entry, after
  settings launch, and on assertion failure when capture is possible. Device
  evidence uses a unique directory per run, including when apps are retained;
  both passing screenshots and both XML files must be retrieved and nonempty.
- `android-layout.json`, `android-screen.png`: optional CLI diagnostics taken
  after instrumentation and before settings restoration.

Exit **0** requires one passing instrumentation test, retrieved visual evidence,
no observed keyboard crash/ANR, and successful restoration/removal. Exit **1**
means failure, including a missing/unauthorized/ambiguous device, missing APKs,
install error, selection failure, missing visible key, wrong text, missing
settings menu, timeout, runner crash, crash/ANR log, or restoration error.
Argument errors exit **2**. ADB's `am instrument` can return 0 on a failed test;
the script also requires `OK (1 test)` and rejects runner failure markers.
Optional CLI diagnostic errors are recorded but do not invalidate the ADB test.

## GitHub Actions

`.github/workflows/build-test-auto.yml` retains `./gradlew testRunTestsUnitTest`
and adds an `ime-smoke` job after it, on a local API 35 x86_64 emulator. Both
jobs use JDK 21; no assertions are skipped to accommodate the new test.
Relevant PR changes and `workflow_dispatch` trigger the workflow. Smoke
artifacts upload with `always()` and are retained for seven days. The separate
debug APK workflow still builds `assembleDebug` and uploads the debug APK.

See [VALIDATION.md](VALIDATION.md) for results and execution limits from the
implementation session.
