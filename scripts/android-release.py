"""Prepare signing and verify the actual Android package before upload.

Hooks (from the checkout root): prepare; verify VERSION CODE TAG; cleanup.
Cleanup uses a private ownership marker, never the presence of secrets.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys


SECRET_NAMES = ("KEYSTORE_BASE64", "KEYSTORE_ALIAS", "KEYSTORE_PASSWORD", "KEY_PASSWORD")
SIGNING_FILES = ("matrix-messenger.jks", "local.properties")
STATE_DIRECTORY = ".matrix-android-signing"
STATE_CONTENT = {"version": 1, "files": list(SIGNING_FILES)}


class ReleaseError(ValueError):
    """A fixed, credential-free diagnostic safe to show in CI."""


def signing_requested():
    present = [name for name in SECRET_NAMES if os.environ.get(name)]
    if present and len(present) != len(SECRET_NAMES):
        missing = [name for name in SECRET_NAMES if not os.environ.get(name)]
        raise ReleaseError("Incomplete Android signing secrets; missing: " + ", ".join(missing))
    return bool(present)


def sdk_root():
    configured = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not configured or not Path(configured).is_dir():
        raise ReleaseError("Set ANDROID_SDK_ROOT or ANDROID_HOME to an existing Android SDK")
    return Path(configured)


def sdk_tool(name):
    if name not in ("aapt", "apksigner"):
        raise ReleaseError("Unsupported Android SDK verification tool")
    build_tools = sdk_root() / "build-tools"
    versions = []
    if build_tools.is_dir():
        for directory in build_tools.iterdir():
            if directory.is_dir() and re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", directory.name):
                versions.append(directory)
    versions.sort(key=lambda path: tuple(int(part) for part in path.name.split(".")), reverse=True)
    names = (name + ".exe", name + ".bat", name + ".cmd", name) if os.name == "nt" else (name,)
    for directory in versions:
        for filename in names:
            candidate = directory / filename
            if candidate.is_file():
                return str(candidate.resolve())
    raise ReleaseError("Android SDK tool not found in stable build-tools: " + name)


def run(*args):
    tool = Path(args[0]).stem
    diagnostic = "APK signature verification" if tool == "apksigner" else "APK metadata inspection (aapt)"
    try:
        result = subprocess.run(args, check=False, capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired, UnicodeError) as exc:
        raise ReleaseError(diagnostic + " could not complete") from exc
    if result.returncode:
        # Native diagnostics/arguments may contain secrets. Never relay them.
        raise ReleaseError(diagnostic + " failed")
    return result.stdout


def private_write(path, contents):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(contents)
        stream.flush()
        os.fsync(stream.fileno())


def escape_property(value):
    # java.util.Properties reads ISO-8859-1; Unicode must use UTF-16 escapes.
    replacements = {"\\": "\\\\", "\n": "\\n", "\r": "\\r", "\t": "\\t", "\f": "\\f",
                    " ": "\\ ", "=": "\\=", ":": "\\:"}
    pieces = []
    for character in value:
        if character in replacements:
            pieces.append(replacements[character])
        elif ord(character) < 32 or ord(character) > 126:
            units = character.encode("utf-16-be", errors="surrogatepass")
            pieces.extend("\\u" + units[index:index + 2].hex() for index in range(0, len(units), 2))
        else:
            pieces.append(character)
    return "".join(pieces)


def cleanup():
    directory = Path(STATE_DIRECTORY)
    if not directory.exists() and not directory.is_symlink():
        return
    if directory.is_symlink() or not directory.is_dir():
        raise ReleaseError("Invalid Android signing ownership directory; no files removed")
    marker = directory / "state.json"
    if marker.is_symlink() or not marker.is_file():
        raise ReleaseError("Invalid Android signing ownership marker; no files removed")
    try:
        state = json.loads(marker.read_text(encoding="ascii"))
    except (ValueError, OSError) as exc:
        raise ReleaseError("Invalid Android signing ownership marker; no files removed") from exc
    if state != STATE_CONTENT:
        raise ReleaseError("Invalid Android signing ownership marker; no files removed")
    for name in SIGNING_FILES:
        owned = directory / name
        destination = Path(name)
        # Hard links make ownership durable before/after interruption. A file
        # replaced by another process, or a symlink, is never deleted.
        if owned.is_symlink():
            raise ReleaseError("Invalid Android signing ownership file; no further files removed")
        try:
            owned_stat = owned.stat(follow_symlinks=False)
            destination_stat = destination.stat(follow_symlinks=False)
        except FileNotFoundError:
            continue
        if (stat.S_ISREG(owned_stat.st_mode) and stat.S_ISREG(destination_stat.st_mode)
                and os.path.samestat(owned_stat, destination_stat)):
            destination.unlink()
    # Remove only known files; never recursively delete an arbitrary directory.
    for name in SIGNING_FILES:
        (directory / name).unlink(missing_ok=True)
    marker.unlink()
    directory.rmdir()


def prepare():
    requested = signing_requested()
    if Path(STATE_DIRECTORY).exists() or Path(STATE_DIRECTORY).is_symlink():
        raise ReleaseError("Existing Android signing ownership state must be cleaned up first")
    if not requested:
        print("No signing certificate: building a debug APK, explicitly labelled -debug")
        return
    if any(Path(name).exists() or Path(name).is_symlink() for name in SIGNING_FILES):
        raise ReleaseError("Refusing to overwrite existing local signing files")
    sdk = sdk_root()
    try:
        certificate = base64.b64decode(os.environ["KEYSTORE_BASE64"], validate=True)
    except ValueError as exc:
        raise ReleaseError("KEYSTORE_BASE64 is not valid base64") from exc
    if not certificate:
        raise ReleaseError("KEYSTORE_BASE64 decoded to an empty keystore")
    properties = {
        "sdk.dir": str(sdk),
        "keystore.file": "matrix-messenger.jks",
        "keystore.alias": os.environ["KEYSTORE_ALIAS"],
        "keystore.password": os.environ["KEYSTORE_PASSWORD"],
        "key.password": os.environ["KEY_PASSWORD"],
    }
    property_bytes = "".join(f"{key}={escape_property(value)}\n" for key, value in properties.items()).encode("ascii")
    directory = Path(STATE_DIRECTORY)
    # Exclusive directory creation is the ownership boundary. A losing prepare
    # process must not clean up another process's state or signing files.
    try:
        directory.mkdir(mode=0o700)
    except FileExistsError as exc:
        raise ReleaseError("Existing Android signing ownership state must be cleaned up first") from exc
    try:
        # Persist the intent before writing any credential material or exposing
        # files to Gradle. Stage on the same filesystem for atomic hard linking.
        private_write(directory / "state.json", json.dumps(STATE_CONTENT).encode("ascii"))
        for name, contents in zip(SIGNING_FILES, (certificate, property_bytes)):
            staged = directory / name
            private_write(staged, contents)
            try:
                os.link(staged, name)  # Atomic no-overwrite, even after preflight.
            except FileExistsError as exc:
                raise ReleaseError("Refusing to overwrite existing local signing files") from exc
        print("Android signing files prepared with private ownership state")
    except BaseException:
        try:
            if (directory / "state.json").is_file():
                cleanup()
            else:
                directory.rmdir()
        except Exception:
            print("Android preparation cleanup failed; run the cleanup hook", file=sys.stderr)
        raise


def verify(version, code, tag):
    signed = signing_requested()
    if (tag != f"v{version}" or not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version)):
        raise ReleaseError("Invalid release tag/version; expected v plus numeric major.minor.patch")
    if not re.fullmatch(r"[1-9][0-9]{0,9}", str(code)) or int(code) > 2100000000:
        raise ReleaseError("Invalid Android version code; expected integer 1..2100000000")
    variant = "release" if signed else "debug"
    apk = Path(f"app/build/outputs/apk/{variant}/app-{variant}.apk")
    if not apk.is_file():
        raise ReleaseError("Expected " + variant + " APK is missing")
    aapt = sdk_tool("aapt")
    apksigner = sdk_tool("apksigner")
    badging = run(aapt, "dump", "badging", str(apk))
    packages = [line for line in badging.splitlines() if line.startswith("package:")]
    if len(packages) != 1:
        raise ReleaseError("aapt must report exactly one APK package metadata record")
    pairs = re.findall(r"\b([A-Za-z][A-Za-z0-9]*)='([^']*)'", packages[0])
    metadata = dict(pairs)
    if len(metadata) != len(pairs):
        raise ReleaseError("aapt reported duplicate APK metadata fields")
    expected = {"versionName": version, "versionCode": str(code), "name": "com.matrix.messenger"}
    for key, value in expected.items():
        if metadata.get(key) != value:
            raise ReleaseError("APK embedded " + key + " does not match the release metadata")
    debuggable = any(re.fullmatch(r"application-debuggable\s*", line) for line in badging.splitlines())
    if debuggable != (not signed):
        raise ReleaseError("Unexpected APK debug flag; expected " + ("non-debuggable release" if signed else "debuggable debug APK"))
    run(apksigner, "verify", "--verbose", str(apk))
    assets = Path("release-assets")
    assets.mkdir(exist_ok=True)
    name = f"MatrixTalk-{tag}{'' if signed else '-debug'}.apk"
    destination = assets / name
    try:
        output = destination.open("xb")
    except FileExistsError as exc:
        raise ReleaseError("Refusing to overwrite an existing verified APK artifact") from exc
    try:
        with output, apk.open("rb") as source:
            shutil.copyfileobj(source, output)
    except BaseException:
        destination.unlink(missing_ok=True)
        raise
    print(f"Verified APK {version}/{code}, {'release signature' if signed else 'debug signature'}: {name}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare")
    verification = commands.add_parser("verify")
    verification.add_argument("version")
    verification.add_argument("code")
    verification.add_argument("tag")
    commands.add_parser("cleanup")
    args = parser.parse_args(argv)
    try:
        if args.command == "prepare":
            prepare()
        elif args.command == "verify":
            verify(args.version, args.code, args.tag)
        else:
            cleanup()
    except ReleaseError as error:
        print("Android release validation failed: " + str(error), file=sys.stderr)
        return 1
    except Exception:
        print("Android release helper failed; credential diagnostics suppressed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
