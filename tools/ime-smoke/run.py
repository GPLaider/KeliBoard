#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run the same isolated IME smoke test on a local emulator, USB, or ADB TCP device."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
KEYBOARD = "io.github.gplaider.keliboard.smoke"
FIXTURE = "io.github.gplaider.keliboard.imesmoke"
IME = KEYBOARD + "/helium314.keyboard.latin.LatinIME"
RUNNER = FIXTURE + ".test/androidx.test.runner.AndroidJUnitRunner"
CLI_HELPER = "com.android.cli.interact.instrumentation"


def built_apk(directory, package):
    # AGP puts ABI-injected builds under intermediates/apk, regular builds under outputs/apk.
    candidates = [ROOT / directory / "output-metadata.json",
                  ROOT / directory.replace("/outputs/", "/intermediates/") / "output-metadata.json"]
    available = [path for path in candidates if path.is_file()]
    if not available:
        raise RuntimeError(f"Build the smoke APKs first; no APK metadata in {directory}")
    metadata_path = max(available, key=lambda path: path.stat().st_mtime_ns)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if metadata["applicationId"] != package or len(metadata["elements"]) != 1:
        raise RuntimeError(f"Expected one APK for {package}: {directory}")
    apk = metadata_path.parent / metadata["elements"][0]["outputFile"]
    if not apk.is_file():
        raise RuntimeError(f"Missing APK: {apk}")
    return apk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--android-cli", help="Optional Android CLI executable for extra layout/screen evidence")
    parser.add_argument("--output", type=Path, default=ROOT / "build/ime-smoke" /
                        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    parser.add_argument("--keep-installed", action="store_true", help="Retain newly installed smoke packages")
    args = parser.parse_args()
    if not args.adb:
        for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
            candidate = Path(os.environ.get(name, "")) / "platform-tools" / (
                "adb.exe" if os.name == "nt" else "adb")
            if candidate.is_file():
                args.adb = str(candidate.resolve())
                break
    if not args.adb:
        parser.error("ADB missing: use --adb, PATH, or ANDROID_HOME")
    args.output = args.output.resolve()
    if args.output.exists() and any(args.output.iterdir()):
        parser.error("--output must be empty so stale artifacts cannot look like a new pass")
    args.output.mkdir(parents=True, exist_ok=True)
    result = {"status": "FAIL", "run_id": uuid.uuid4().hex,
              "started_utc": datetime.now(timezone.utc).isoformat(),
              "checks": [], "cleanup_errors": [], "diagnostic_errors": []}
    transcript = (args.output / "commands.log").open("w", encoding="utf-8")
    logcat = None
    log_file = None
    snapshot = None
    fresh_packages = []
    instrumentation_started = False

    def log(message):
        print(message, flush=True)
        transcript.write(message + "\n")
        transcript.flush()

    def run(command, timeout=45, check=True, output_file=None):
        log("$ " + subprocess.list2cmdline([str(arg) for arg in command]))
        timed_out = False
        try:
            completed = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                       errors="replace", timeout=timeout)
            output = completed.stdout + completed.stderr
        except subprocess.TimeoutExpired as error:
            timed_out = True
            # TimeoutExpired can contain bytes even when text=True. Preserve the last step/trace.
            output = "".join(part.decode("utf-8", errors="replace") if isinstance(part, bytes)
                             else part or "" for part in (error.stdout, error.stderr))
        transcript.write(output + "\n")
        transcript.flush()
        if output_file:
            output_file.write_text(output + "\n", encoding="utf-8")
        if timed_out:
            raise RuntimeError(f"Command timed out after {timeout}s; partial output is in commands.log")
        if check and completed.returncode:
            raise RuntimeError(f"Command exited {completed.returncode}: {output.strip()}")
        return completed.returncode, output.strip()

    def adb(*command, **kwargs):
        return run([args.adb, "-s", args.serial, *command], **kwargs)

    def shell(*command, **kwargs):
        return adb("shell", *command, **kwargs)[1]

    def setting(key):
        return shell("settings", "--user", snapshot["user"], "get", "secure", key)

    try:
        _, devices = run([args.adb, "devices"])
        ready = [line.split()[0] for line in devices.splitlines()
                 if len(line.split()) == 2 and line.split()[1] == "device"]
        if not args.serial:
            if len(ready) != 1:
                raise RuntimeError(f"Select exactly one ready device with --serial; found {ready}")
            args.serial = ready[0]
        if args.serial not in ready:
            raise RuntimeError(f"Device {args.serial} is not ready/authorized; ready devices: {ready}")
        result["serial"] = args.serial
        result["device"] = {key: shell("getprop", key) for key in
                            ("ro.product.model", "ro.build.version.sdk", "ro.build.fingerprint")}
        if int(result["device"]["ro.build.version.sdk"]) < 23:
            raise RuntimeError("The UI Automator smoke fixture requires API 23+")
        if shell("getprop", "sys.boot_completed") != "1":
            raise RuntimeError("Device has not finished booting")
        apks = [(KEYBOARD, built_apk("app/build/outputs/apk/smoke", KEYBOARD)),
                (FIXTURE, built_apk("tools/ime-smoke/build/outputs/apk/debug", FIXTURE)),
                (FIXTURE + ".test", built_apk("tools/ime-smoke/build/outputs/apk/androidTest/debug", FIXTURE + ".test"))]
        result["apks"] = {package: {"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                          for package, path in apks}
        user = shell("am", "get-current-user")
        snapshot = {"user": user}
        snapshot["default_input_method"] = setting("default_input_method")
        snapshot["show_ime_with_hard_keyboard"] = setting("show_ime_with_hard_keyboard")
        snapshot["enabled"] = shell("ime", "list", "-s", "--user", user).splitlines()
        result["original_settings"] = snapshot.copy()
        if snapshot["default_input_method"] in ("", "null"):
            raise RuntimeError("No original default IME; select a system keyboard before running")

        log_file = (args.output / "logcat.txt").open("w", encoding="utf-8")
        logcat = subprocess.Popen([args.adb, "-s", args.serial, "logcat", "-T", "1", "-v", "threadtime",
                                   "ActivityManager:I", "AndroidRuntime:E", "DEBUG:F", "libc:F",
                                   "ImeSmoke:I", "LatinIME:W", "*:S"], stdout=log_file, stderr=log_file)
        for package, path in apks:
            installed = shell("pm", "path", "--user", user, package, check=False)
            if not installed.startswith("package:"):
                fresh_packages.append(package)
            _, install_output = adb("install", "--user", user, "-r", "-t", str(path), timeout=180)
            if "Success" not in install_output:
                raise RuntimeError(f"APK install did not succeed: {install_output}")
        shell("settings", "--user", user, "put", "secure", "show_ime_with_hard_keyboard", "1")
        shell("ime", "enable", "--user", user, IME)
        shell("ime", "set", "--user", user, IME)
        if setting("default_input_method") != IME:
            raise RuntimeError("ime set returned without selecting KeliBoard")
        result["checks"].append("IME enabled and selected")
        if args.android_cli:
            # Android CLI layout leaves its own UiAutomation instrumentation running.
            # Android permits only one UiAutomation owner at a time on a device.
            shell("am", "force-stop", "--user", user, CLI_HELPER)
        log("Running real keyboard touch and settings assertions (up to 180 seconds)")
        instrumentation_started = True
        _, instrumentation = adb("shell", "am", "instrument", "--user", user, "-w", "-r",
                                 "-e", "targetPackage", KEYBOARD, "-e", "class",
                                 FIXTURE + ".ImeSmokeTest", "-e", "evidenceRun", result["run_id"],
                                 RUNNER, timeout=180, output_file=args.output / "instrumentation.txt")
        log(instrumentation)
        # am instrument can exit 0 even when assertions fail or the runner crashes.
        if not re.search(r"OK \(1 test\)", instrumentation) or any(marker in instrumentation for marker in
                ("FAILURES!!!", "INSTRUMENTATION_FAILED", "shortMsg=", "INSTRUMENTATION_STATUS_CODE: -2")):
            raise RuntimeError("Instrumentation did not report exactly one passing test")
        result["checks"] += ["Visible q/w/e key touches produced exactly qwe", "Main settings content visible"]
        result["status"] = "PASS"
    except (Exception, KeyboardInterrupt) as error:
        result["error"] = str(error) or type(error).__name__
        log("FAIL: " + result["error"])
    finally:
        if snapshot and "enabled" in snapshot:
            # Collect evidence before restoring settings or removing disposable packages.
            for command, filename in [(("dumpsys", "input_method"), "input-method.txt"),
                                      (("dumpsys", "activity", "activities"), "activities.txt"),
                                      (("dumpsys", "accessibility"), "accessibility.txt")]:
                try:
                    (args.output / filename).write_text(shell(*command), encoding="utf-8")
                except Exception as error:
                    result["diagnostic_errors"].append(str(error))
            if instrumentation_started:
                try:
                    code, output = adb("pull", f"/sdcard/Android/data/{FIXTURE}/files/evidence/{result['run_id']}",
                                       str(args.output / "device-evidence"))
                    if code or "pulled" not in output:
                        raise RuntimeError("Could not pull this run's screenshots and UI hierarchies")
                    if result["status"] == "PASS":
                        for name in ("01-keyboard-input", "02-settings"):
                            for extension in ("png", "xml"):
                                evidence = args.output / "device-evidence" / f"{name}.{extension}"
                                if not evidence.is_file() or not evidence.stat().st_size:
                                    raise RuntimeError(f"Missing passing evidence: {evidence.name}")
                except Exception as error:
                    result["diagnostic_errors"].append(str(error))
                    if result["status"] == "PASS":
                        result["status"] = "FAIL"
                        result["error"] = "Passing test evidence could not be retrieved"
            if args.android_cli and instrumentation_started:
                for command in [("layout", "--full", "--output", str(args.output / "android-layout.json")),
                                ("screen", "capture", "--output", str(args.output / "android-screen.png"))]:
                    try:
                        run([args.android_cli, "--no-metrics", *command, "--device", args.serial], timeout=60)
                    except Exception as error:
                        result["diagnostic_errors"].append("Optional Android CLI: " + str(error))
                try:
                    shell("am", "force-stop", "--user", snapshot["user"], CLI_HELPER)
                except Exception as error:
                    result["cleanup_errors"].append("Could not release Android CLI UI automation: " + str(error))
            # Restore each setting independently, even if another restoration fails.
            user = snapshot["user"]
            restore_commands = [("am", "force-stop", "--user", user, FIXTURE)]
            original = snapshot["default_input_method"]
            if original not in ("", "null"):
                restore_commands.append(("ime", "set", "--user", user, original))
            if IME not in snapshot["enabled"]:
                restore_commands.append(("ime", "disable", "--user", user, IME))
            hardware = snapshot["show_ime_with_hard_keyboard"]
            restore_commands.append(("settings", "--user", user, "delete", "secure", "show_ime_with_hard_keyboard")
                                    if hardware == "null" else ("settings", "--user", user, "put", "secure",
                                                               "show_ime_with_hard_keyboard", hardware))
            for command in restore_commands:
                try:
                    shell(*command)
                except Exception as error:
                    result["cleanup_errors"].append(str(error))
            try:
                if setting("default_input_method") != original or setting("show_ime_with_hard_keyboard") != hardware:
                    raise RuntimeError("Original IME/settings were not restored")
                if set(shell("ime", "list", "-s", "--user", user).splitlines()) != set(snapshot["enabled"]):
                    raise RuntimeError("Enabled IME list was not restored")
            except Exception as error:
                result["cleanup_errors"].append(str(error))
            if not args.keep_installed:
                for package in reversed(fresh_packages):
                    try:
                        _, output = adb("uninstall", "--user", user, package)
                        if "Success" not in output:
                            raise RuntimeError(f"Could not uninstall {package}: {output}")
                    except Exception as error:
                        result["cleanup_errors"].append(str(error))
        if logcat:
            try:
                if logcat.poll() is not None:
                    raise RuntimeError("Logcat capture exited before test completion")
                logcat.terminate()
                logcat.wait(timeout=10)
            except Exception as error:
                result["cleanup_errors"].append(str(error))
                if logcat.poll() is None:
                    logcat.kill()
                    logcat.wait(timeout=10)
            log_file.close()
            logs = (args.output / "logcat.txt").read_text(encoding="utf-8", errors="replace")
            if re.search(r"(?:Process: |>>> |ANR in )" + re.escape(KEYBOARD) + r"(?:\b|:)", logs):
                result["status"] = "FAIL"
                result["error"] = "KeliBoard crash or ANR in logcat; inspect logcat.txt"
        if result["cleanup_errors"]:
            result["status"] = "FAIL"
        result["finished_utc"] = datetime.now(timezone.utc).isoformat()
        (args.output / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        log(f"{result['status']}: artifacts at {args.output}")
        transcript.close()
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
