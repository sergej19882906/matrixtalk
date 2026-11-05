#!/usr/bin/env python3
"""CI hooks: prepare before packageDmg, verify VERSION DMG, cleanup in always().

Run with python3 -I. Only runner temporary files and GITHUB_ENV are written.
Existing keychains are never unlocked, relocked, reconfigured, or made default.
"""

import argparse
import json
import os
from pathlib import Path
import plistlib
import re
import secrets
import shlex
import shutil
import subprocess
import sys
import tempfile


MAC_SECRETS = (
    "MAC_CERTIFICATE_BASE64",
    "MAC_CERTIFICATE_PASSWORD",
    "APPLE_ID",
    "APPLE_TEAM_ID",
    "APPLE_APP_SPECIFIC_PASSWORD",
)


class ReleaseError(Exception):
    pass


def signing_requested():
    present = [name for name in MAC_SECRETS if os.environ.get(name)]
    if present and len(present) != len(MAC_SECRETS):
        missing = [name for name in MAC_SECRETS if not os.environ.get(name)]
        raise ReleaseError("Incomplete macOS signing secrets; missing: " + ", ".join(missing))
    return bool(present)


def run_tool(args, *, timeout=120, check=True):
    # Never forward diagnostics or arguments: certificate passwords and Apple
    # credentials can occur in both. notarytool JSON is consumed, not printed.
    try:
        result = subprocess.run(args, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ReleaseError(Path(args[0]).name + " could not complete") from exc
    if check and result.returncode:
        raise ReleaseError(Path(args[0]).name + " failed")
    return result


def publish_env(values):
    env_file = os.environ.get("GITHUB_ENV")
    if not env_file:
        raise ReleaseError("GITHUB_ENV is required for the prepare hook")
    for value in values.values():
        if "\n" in value or "\r" in value or "\x00" in value:
            raise ReleaseError("Invalid multiline signing environment value")
    with open(env_file, "a", encoding="utf-8", newline="\n") as stream:
        for name, value in values.items():
            stream.write(name + "=" + value + "\n")


def temporary_root():
    root = os.environ.get("RUNNER_TEMP") or tempfile.gettempdir()
    if not Path(root).is_dir():
        raise ReleaseError("Runner temporary directory does not exist")
    return root


def cleanup_state(state_path):
    path = Path(state_path)
    if not path.exists():
        return  # Idempotent after a prepare failure or an earlier cleanup.
    with path.open(encoding="utf-8") as stream:
        state = json.load(stream)
    directory = path.parent
    keychain = Path(state["keychain"])
    if (path.name != "state.json" or not directory.name.startswith("matrix-mac-sign-")
            or keychain != directory / "signing.keychain-db"):
        raise ReleaseError("Invalid macOS signing cleanup state")
    # Delete certificate material even if restoring the runner search list
    # fails; keep state/keychain so the always() hook can retry restoration.
    (directory / "certificate.p12").unlink(missing_ok=True)
    # Restore the exact original user search list, including paths with spaces.
    # No default-keychain or lock-state changes were made to existing keychains.
    run_tool(["security", "list-keychains", "-d", "user", "-s", *state["search_list"]])
    if keychain.exists():
        run_tool(["security", "delete-keychain", str(keychain)])
    shutil.rmtree(directory)


def prepare():
    requested = signing_requested()
    if os.environ.get("MATRIX_MAC_STATE"):
        raise ReleaseError("An existing signing session must be cleaned up before prepare")
    publish_env({"MATRIX_MAC_SIGN": "false", "MATRIX_MAC_IDENTITY": "",
                 "MATRIX_MAC_KEYCHAIN": "", "MATRIX_MAC_STATE": ""})
    if not requested:
        print("macOS release explicitly unsigned: no signing secrets configured")
        return
    if sys.platform != "darwin":
        raise ReleaseError("Signed macOS preparation requires macOS")
    # Snapshot before creating a keychain; security may add a new keychain to
    # the search list automatically. Do not touch the original default keychain.
    search_list = shlex.split(run_tool(["security", "list-keychains", "-d", "user"]).stdout.decode())
    directory = Path(tempfile.mkdtemp(prefix="matrix-mac-sign-", dir=temporary_root())).resolve()
    os.chmod(directory, 0o700)
    keychain = directory / "signing.keychain-db"
    state_path = directory / "state.json"
    p12 = directory / "certificate.p12"
    password = secrets.token_urlsafe(48)
    try:
        with state_path.open("x", encoding="utf-8") as stream:
            json.dump({"keychain": str(keychain), "search_list": search_list}, stream)
        os.chmod(state_path, 0o600)
        # Publish state before the first keychain side effect so always() can
        # recover even if this process is interrupted during import.
        publish_env({"MATRIX_MAC_STATE": str(state_path)})
        import base64
        try:
            certificate = base64.b64decode(os.environ["MAC_CERTIFICATE_BASE64"], validate=True)
        except ValueError as exc:
            raise ReleaseError("MAC_CERTIFICATE_BASE64 is not valid base64") from exc
        if not certificate:
            raise ReleaseError("MAC_CERTIFICATE_BASE64 decoded to an empty certificate")
        with p12.open("xb") as stream:
            stream.write(certificate)
        os.chmod(p12, 0o600)
        del certificate
        run_tool(["security", "create-keychain", "-p", password, str(keychain)])
        run_tool(["security", "set-keychain-settings", "-lut", "21600", str(keychain)])
        run_tool(["security", "unlock-keychain", "-p", password, str(keychain)])
        run_tool(["security", "list-keychains", "-d", "user", "-s", str(keychain), *search_list])
        try:
            run_tool(["security", "import", str(p12), "-k", str(keychain), "-P",
                      os.environ["MAC_CERTIFICATE_PASSWORD"], "-T", "/usr/bin/codesign"])
        finally:
            p12.unlink(missing_ok=True)
        run_tool(["security", "set-key-partition-list", "-S", "apple-tool:,apple:,codesign:",
                  "-s", "-k", password, str(keychain)])
        identities = run_tool(["security", "find-identity", "-v", "-p", "codesigning", str(keychain)]).stdout.decode()
        candidates = re.findall(r'^\s*\d+\)\s+[0-9A-Fa-f]{40}\s+"(Developer ID Application: [^"\r\n]+)"\s*$',
                                identities, flags=re.MULTILINE)
        candidates = [identity for identity in candidates
                      if identity.endswith(" (" + os.environ["APPLE_TEAM_ID"] + ")")]
        if len(candidates) != 1:
            raise ReleaseError("Expected exactly one valid Developer ID Application identity for APPLE_TEAM_ID")
        publish_env({"MATRIX_MAC_SIGN": "true", "MATRIX_MAC_IDENTITY": candidates[0],
                     "MATRIX_MAC_KEYCHAIN": str(keychain)})
        print("macOS signing keychain prepared; Compose must sign during packaging")
    except BaseException:
        try:
            cleanup_state(str(state_path))
        except Exception:
            print("macOS preparation cleanup failed; run the always() cleanup hook", file=sys.stderr)
        raise


def contained_path(path, root):
    resolved = path.resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ReleaseError("Embedded app contains a path outside the mounted artifact")
    return resolved


def inspect_dmg(package, expected_version, signed):
    directory = Path(tempfile.mkdtemp(prefix="matrix-mac-verify-", dir=temporary_root())).resolve()
    mountpoint = directory / "volume"
    mountpoint.mkdir()
    attach_attempted = False
    try:
        attach_attempted = True
        run_tool(["hdiutil", "attach", "-readonly", "-nobrowse", "-noautoopen", "-plist",
                  "-mountpoint", str(mountpoint), str(package)])
        apps = [entry for entry in mountpoint.iterdir() if entry.name.endswith(".app") and entry.is_dir()]
        if len(apps) != 1:
            raise ReleaseError("Expected exactly one app in the actual DMG")
        app = contained_path(apps[0], mountpoint)
        plist_path = contained_path(app / "Contents" / "Info.plist", app)
        with plist_path.open("rb") as stream:
            info = plistlib.load(stream)
        for key in ("CFBundleShortVersionString", "CFBundleVersion"):
            if info.get(key) != expected_version:
                raise ReleaseError("Embedded " + key + " does not match the release version")
        executable = info.get("CFBundleExecutable")
        if (not isinstance(executable, str) or not executable or executable in (".", "..")
                or "/" in executable or "\\" in executable):
            raise ReleaseError("Invalid embedded CFBundleExecutable")
        binary = contained_path(app / "Contents" / "MacOS" / executable, app)
        if not binary.is_file():
            raise ReleaseError("Embedded app launcher does not exist")
        architectures = run_tool(["lipo", "-archs", str(binary)]).stdout.decode().split()
        if architectures != ["x86_64"]:
            raise ReleaseError("Embedded app launcher must be x86_64 only")
        if signed:
            run_tool(["codesign", "--verify", "--deep", "--strict", str(app)])
            details = run_tool(["codesign", "--display", "--verbose=4", str(app)]).stderr.decode()
            if ("Authority=Developer ID Application:" not in details
                    or ("TeamIdentifier=" + os.environ["APPLE_TEAM_ID"]) not in details.splitlines()
                    or not re.search(r"^CodeDirectory .*flags=.*\(.*runtime.*\)", details, re.MULTILINE)):
                raise ReleaseError("Embedded app must have Developer ID signing, the expected team, and hardened runtime")
        print("DMG embedded app verified: release versions and x86_64" + (", valid signing" if signed else ", unsigned mode"))
    finally:
        # Never recursively delete a directory while its image is mounted.
        if attach_attempted:
            detached = run_tool(["hdiutil", "detach", str(mountpoint)], check=False)
            if detached.returncode:
                # An attach failure can leave no mount; distinguish that from
                # a detach failure using hdiutil's actual mounted-image list.
                images = plistlib.loads(run_tool(["hdiutil", "info", "-plist"]).stdout)
                mounted = any(entity.get("mount-point") == str(mountpoint)
                              for image in images.get("images", [])
                              for entity in image.get("system-entities", []))
                if mounted:
                    raise ReleaseError("Could not detach verification DMG; temporary mount retained")
        shutil.rmtree(directory)


def verify(expected_version, package_path):
    requested = signing_requested()
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", expected_version):
        raise ReleaseError("Expected version must be a numeric major.minor.patch, without v")
    mode = os.environ.get("MATRIX_MAC_SIGN")
    if mode not in ("true", "false") or (mode == "true") != requested:
        raise ReleaseError("MATRIX_MAC_SIGN must match the secrets validated by prepare")
    if sys.platform != "darwin":
        raise ReleaseError("DMG verification requires macOS")
    package = Path(package_path).resolve()
    if not package.is_file() or package.suffix.lower() != ".dmg":
        raise ReleaseError("Package path must identify one existing DMG")
    inspect_dmg(package, expected_version, requested)
    if not requested:
        print("Notarization skipped: explicit unsigned release")
        return
    submission = run_tool(["xcrun", "notarytool", "submit", str(package),
                           "--apple-id", os.environ["APPLE_ID"],
                           "--team-id", os.environ["APPLE_TEAM_ID"],
                           "--password", os.environ["APPLE_APP_SPECIFIC_PASSWORD"],
                           "--output-format", "json", "--wait", "--timeout", "30m"], timeout=1950)
    try:
        result = json.loads(submission.stdout)
    except (ValueError, UnicodeError) as exc:
        raise ReleaseError("notarytool did not return valid JSON") from exc
    if not isinstance(result, dict) or result.get("status") != "Accepted":
        raise ReleaseError("Notarization status is not Accepted")
    run_tool(["xcrun", "stapler", "staple", str(package)])
    run_tool(["xcrun", "stapler", "validate", str(package)])
    print("DMG notarization Accepted; ticket stapled and validated")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare")
    verification = commands.add_parser("verify")
    verification.add_argument("expected_version")
    verification.add_argument("package_path")
    commands.add_parser("cleanup")
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            prepare()
        elif args.command == "verify":
            verify(args.expected_version, args.package_path)
        else:
            state = os.environ.get("MATRIX_MAC_STATE")
            if state:
                if sys.platform != "darwin":
                    raise ReleaseError("Keychain cleanup requires macOS")
                cleanup_state(state)
            print("macOS signing cleanup complete")
    except ReleaseError as exc:
        print("Error: " + str(exc), file=sys.stderr)
        return 1
    except Exception:
        # Avoid exception repr/traceback from decoding, tools, or credential IO.
        print("Error: macOS release helper failed; credential diagnostics suppressed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
