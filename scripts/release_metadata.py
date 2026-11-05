#!/usr/bin/env python3
"""Validate release metadata and assets using only the Python standard library.

Run with ``python -I scripts/release_metadata.py COMMAND --root .``.
The canonical inputs are gradle.properties and RELEASE_NOTES.md at that root.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import sys
import tempfile
import zipfile


class ReleaseMetadataError(ValueError):
    """An input is not safe or consistent enough to publish."""


VERSION_PATTERN = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", re.ASCII)
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
NO_STRING = 0xFFFFFFFF


def read_metadata(root, tag=None):
    """Return validated version, version_code and tag from canonical properties."""
    properties = {}
    text = (Path(root) / "gradle.properties").read_text(encoding="utf-8-sig")
    for number, line in enumerate(text.splitlines(), 1):
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.match(r"(releaseVersion|androidVersionCode)(?:\s*[=:]\s*|\s+)(.*)\Z", line)
        if match:
            key, value = match.groups()
            if key in properties:
                raise ReleaseMetadataError("duplicate {} in gradle.properties (line {})".format(key, number))
            properties[key] = value.strip()
        elif line in ("releaseVersion", "androidVersionCode"):
            raise ReleaseMetadataError("missing value for {}".format(line))
    for key in ("releaseVersion", "androidVersionCode"):
        if key not in properties:
            raise ReleaseMetadataError("missing {} in root gradle.properties".format(key))

    version = properties["releaseVersion"]
    match = VERSION_PATTERN.fullmatch(version)
    if not match:
        raise ReleaseMetadataError("releaseVersion must be three numeric components without leading zeros")
    # MSI ProductVersion limits; length checks also avoid converting enormous integers.
    for component, maximum in zip(match.groups(), (255, 255, 65535)):
        if len(component) > len(str(maximum)) or int(component) > maximum:
            raise ReleaseMetadataError("releaseVersion exceeds MSI limits (255.255.65535)")
    code = properties["androidVersionCode"]
    if not re.fullmatch(r"[1-9][0-9]{0,9}", code, re.ASCII) or int(code) > 2100000000:
        raise ReleaseMetadataError("androidVersionCode must be an integer from 1 to 2100000000")
    expected_tag = "v" + version
    if tag is not None and tag != expected_tag:
        raise ReleaseMetadataError("tag must exactly match {} (got {!r})".format(expected_tag, tag))
    return {"version": version, "version_code": int(code), "tag": expected_tag}


def release_notes(root, version):
    """Extract only the body of the exact level-two version heading.

    Headings inside Markdown fences are not release headings. Duplicate headings
    are rejected rather than selecting an arbitrary section.
    """
    lines = (Path(root) / "RELEASE_NOTES.md").read_text(encoding="utf-8-sig").splitlines()
    headings = []
    fence = None
    for index, line in enumerate(lines):
        marker = re.match(r" {0,3}(`{3,}|~{3,})(.*)\Z", line)
        if fence is not None:
            if marker and marker[1][0] == fence[0] and len(marker[1]) >= fence[1] and not marker[2].strip():
                fence = None
            continue
        if marker:
            fence = (marker[1][0], len(marker[1]))
            continue
        if re.match(r" {0,3}#{1,2}(?:\s|$)", line):
            headings.append((index, line))
    matches = [index for index, line in headings if line == "## " + version]
    if len(matches) != 1:
        raise ReleaseMetadataError("RELEASE_NOTES.md must contain exactly one heading '## {}' (found {})".format(version, len(matches)))
    start = matches[0] + 1
    end = next((index for index, _ in headings if index >= start), len(lines))
    body = "\n".join(lines[start:end]).strip()
    # The history uses a horizontal rule between releases, not as release content.
    if body == "---":
        body = ""
    elif body.endswith("\n---"):
        body = body[:-4].rstrip()
    return body + "\n" if body else ""


def _string_pool(chunk, header_size):
    """Decode the UTF-8/UTF-16 string pool in Android binary XML."""
    if header_size < 28:
        raise ReleaseMetadataError("invalid APK manifest string-pool header")
    count, styles, flags, start, style_start = struct.unpack_from("<IIIII", chunk, 8)
    table_end = header_size + 4 * (count + styles)
    string_end = style_start if style_start else len(chunk)
    if not table_end <= start <= string_end <= len(chunk):
        raise ReleaseMetadataError("invalid APK manifest string-pool bounds")
    strings = []

    def length(position, utf8):
        unit = 1 if utf8 else 2
        if position + unit > string_end:
            raise ReleaseMetadataError("truncated APK manifest string length")
        value = chunk[position] if utf8 else struct.unpack_from("<H", chunk, position)[0]
        position += unit
        high_bit = 0x80 if utf8 else 0x8000
        if value & high_bit:
            if position + unit > string_end:
                raise ReleaseMetadataError("truncated APK manifest string length")
            following = chunk[position] if utf8 else struct.unpack_from("<H", chunk, position)[0]
            value = ((value & (high_bit - 1)) << (8 if utf8 else 16)) | following
            position += unit
        return value, position

    for index in range(count):
        position = start + struct.unpack_from("<I", chunk, header_size + index * 4)[0]
        if not start <= position < string_end:
            raise ReleaseMetadataError("invalid APK manifest string offset")
        utf8 = bool(flags & 0x100)
        size, position = length(position, utf8)
        if utf8:
            size, position = length(position, True)
        else:
            size *= 2
        terminator = 1 if utf8 else 2
        end = position + size
        if end + terminator > string_end or chunk[end:end + terminator] != b"\0" * terminator:
            raise ReleaseMetadataError("invalid APK manifest string terminator")
        strings.append(chunk[position:end].decode("utf-8" if utf8 else "utf-16-le"))
    return strings


def _binary_manifest_debuggable(data):
    """Read application android:debuggable without Android SDK dependencies."""
    if len(data) < 8 or struct.unpack_from("<HHI", data) != (3, 8, len(data)):
        raise ReleaseMetadataError("APK AndroidManifest.xml is not valid Android binary XML")
    strings = None
    stack = []
    applications = 0
    debuggable = False

    def string(index):
        if strings is None or index >= len(strings):
            raise ReleaseMetadataError("invalid APK manifest string reference")
        return strings[index]

    offset = 8
    while offset < len(data):
        if offset + 8 > len(data):
            raise ReleaseMetadataError("truncated APK manifest chunk")
        kind, header_size, size = struct.unpack_from("<HHI", data, offset)
        if not 8 <= header_size <= size or offset + size > len(data):
            raise ReleaseMetadataError("invalid APK manifest chunk bounds")
        chunk = data[offset:offset + size]
        if kind == 1:
            if strings is not None:
                raise ReleaseMetadataError("duplicate APK manifest string pool")
            strings = _string_pool(chunk, header_size)
        elif kind == 0x0102:  # RES_XML_START_ELEMENT_TYPE
            if header_size < 16 or header_size + 20 > size:
                raise ReleaseMetadataError("invalid APK manifest start element")
            _, name_index, attr_start, attr_size, attr_count = struct.unpack_from("<IIHHH", chunk, header_size)
            name = string(name_index)
            attr_offset = header_size + attr_start
            if attr_start < 20 or attr_size < 20 or attr_offset + attr_count * attr_size > size:
                raise ReleaseMetadataError("invalid APK manifest attribute bounds")
            if not stack and name != "manifest":
                raise ReleaseMetadataError("APK manifest root is not manifest")
            if name == "application":
                if stack != ["manifest"] or applications:
                    raise ReleaseMetadataError("APK manifest must contain one application element")
                applications += 1
                found_debuggable = False
                for index in range(attr_count):
                    position = attr_offset + index * attr_size
                    namespace, attribute, raw, value_size, _, value_type, value = struct.unpack_from("<IIIHBBI", chunk, position)
                    attribute_name = string(attribute)
                    namespace_name = None if namespace == NO_STRING else string(namespace)
                    if namespace_name == ANDROID_NAMESPACE and attribute_name == "debuggable":
                        if found_debuggable or value_size != 8:
                            raise ReleaseMetadataError("invalid or duplicate android:debuggable")
                        found_debuggable = True
                        if value_type == 0x12:  # TYPE_INT_BOOLEAN
                            debuggable = value != 0
                        elif value_type == 3 and string(value) in ("true", "false"):
                            debuggable = string(value) == "true"
                        else:
                            raise ReleaseMetadataError("APK android:debuggable is not a literal boolean")
            stack.append(name)
        elif kind == 0x0103:  # RES_XML_END_ELEMENT_TYPE
            if header_size < 16 or header_size + 8 > size:
                raise ReleaseMetadataError("invalid APK manifest end element")
            name = string(struct.unpack_from("<I", chunk, header_size + 4)[0])
            if not stack or stack.pop() != name:
                raise ReleaseMetadataError("unbalanced APK manifest elements")
        offset += size
    if stack or applications != 1:
        raise ReleaseMetadataError("APK manifest must contain one complete application element")
    return debuggable


def apk_debuggable(path):
    """Inspect the compiled manifest; never trust an APK's filename alone."""
    try:
        with zipfile.ZipFile(path) as apk:
            entries = [entry for entry in apk.infolist() if entry.filename == "AndroidManifest.xml"]
            if len(entries) != 1 or entries[0].file_size > 16 * 1024 * 1024:
                raise ReleaseMetadataError("APK must contain one AndroidManifest.xml of at most 16 MiB")
            return _binary_manifest_debuggable(apk.read(entries[0]))
    except (zipfile.BadZipFile, RuntimeError, struct.error, UnicodeError) as error:
        raise ReleaseMetadataError("cannot inspect APK manifest: {}".format(error)) from error


def artifact_manifest(metadata, directory):
    """Validate exactly one asset per platform and return a SHA-256 manifest."""
    directory = Path(directory)
    if not directory.is_dir():
        raise ReleaseMetadataError("artifact directory does not exist: {}".format(directory))
    version = metadata["version"]
    expected = {
        ".apk": ("MatrixTalk-v{}.apk".format(version), "MatrixTalk-v{}-debug.apk".format(version)),
        ".msi": ("MatrixTalk-{}.msi".format(version),),
        ".dmg": ("MatrixTalk-{}.dmg".format(version),),
        ".deb": ("matrixtalk_{}_amd64.deb".format(version),),
    }
    candidates = {suffix: [] for suffix in expected}
    for path in directory.rglob("*"):
        if path.suffix.lower() in candidates:
            if path.is_symlink() or not path.is_file():
                raise ReleaseMetadataError("artifact must be a regular non-symlink file: {}".format(path))
            candidates[path.suffix.lower()].append(path)
    assets = []
    for suffix, names in expected.items():
        paths = candidates[suffix]
        if len(paths) != 1:
            raise ReleaseMetadataError("expected exactly one {} artifact, found {}".format(suffix, len(paths)))
        path = paths[0]
        if path.name not in names:
            raise ReleaseMetadataError("unexpected artifact filename {!r}; expected {}".format(path.name, " or ".join(names)))
        if not path.stat().st_size:
            raise ReleaseMetadataError("artifact is empty: {}".format(path))
        asset = {"path": path.relative_to(directory).as_posix(), "type": suffix[1:]}
        if suffix == ".apk":
            debug = apk_debuggable(path)
            if debug != path.name.endswith("-debug.apk"):
                raise ReleaseMetadataError("APK android:debuggable does not match its filename: {}".format(path.name))
            asset["debuggable"] = debug
        digest = hashlib.sha256()
        size = 0
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
                size += len(block)
        asset.update(size=size, sha256=digest.hexdigest())
        assets.append(asset)
    return dict(metadata, artifacts=assets)


def atomic_write(path, text):
    """Replace a UTF-8 output only after its full contents have been written."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="\n", dir=path.parent,
                                         prefix="." + path.name + ".", suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("check", "notes", "artifacts", "outputs"):
        command = commands.add_parser(name)
        command.add_argument("--root", type=Path, default=Path("."), help="repository root")
        command.add_argument("--tag", required=name in ("notes", "artifacts"), help="exact vX.Y.Z release tag")
        if name == "notes":
            command.add_argument("--output", type=Path, required=True)
        elif name == "artifacts":
            command.add_argument("--directory", type=Path, required=True)
            command.add_argument("--manifest-output", type=Path)
        elif name == "outputs":
            command.add_argument("--output", type=Path, help="append CI outputs here (defaults to GITHUB_OUTPUT if set)")
    args = parser.parse_args(argv)
    try:
        metadata = read_metadata(args.root, args.tag)
        notes = release_notes(args.root, metadata["version"])
        if args.command == "notes":
            atomic_write(args.output, notes)
        elif args.command == "artifacts":
            manifest = artifact_manifest(metadata, args.directory)
            text = json.dumps(manifest, ensure_ascii=True, indent=2) + "\n"
            if args.manifest_output is not None:
                output = args.manifest_output.resolve()
                if any(output == (args.directory / asset["path"]).resolve() for asset in manifest["artifacts"]):
                    raise ReleaseMetadataError("manifest output must not overwrite an artifact")
                atomic_write(args.manifest_output, text)
            sys.stdout.write(text)
        elif args.command == "outputs":
            text = "version={version}\nversion_code={version_code}\ntag={tag}\n".format(**metadata)
            output = args.output or os.environ.get("GITHUB_OUTPUT")
            if output:
                with Path(output).open("a", encoding="utf-8", newline="\n") as stream:
                    stream.write(text)
            sys.stdout.write(text)
        else:
            print("Release metadata OK: version={version} version_code={version_code} tag={tag}".format(**metadata))
        return 0
    except (ReleaseMetadataError, OSError, UnicodeError, struct.error) as error:
        print("release metadata error: {}".format(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
