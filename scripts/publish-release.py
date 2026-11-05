"""Publish a checked complete set, without replacing already published assets."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys


# -I excludes this directory from sys.path; load only our trusted adjacent helper.
_spec = importlib.util.spec_from_file_location("release_metadata", Path(__file__).resolve().with_name("release_metadata.py"))
_metadata = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_metadata)


def gh(*args):
    return subprocess.run(["gh", *map(str, args)], check=True, capture_output=True, text=True).stdout


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def _json(text):
    return json.loads(text, object_pairs_hook=_unique_object)


def _checked_files(tag, root, notes):
    match = _metadata.VERSION_PATTERN.fullmatch(tag[1:]) if isinstance(tag, str) and tag.startswith("v") else None
    if not match or any(len(part) > len(str(limit)) or int(part) > limit
                        for part, limit in zip(match.groups(), (255, 255, 65535))):
        raise ValueError("Invalid release tag")
    manifest_path = root / "release-manifest.json"
    if manifest_path.is_symlink() or not manifest_path.is_file():
        raise ValueError("Missing or unsafe release-manifest.json")
    manifest = _json(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(manifest, dict) or manifest.get("tag") != tag or manifest.get("version") != tag[1:]:
        raise ValueError("Artifact manifest version/tag does not match")
    code = manifest.get("version_code")
    if type(code) is not int or not 1 <= code <= 2100000000:
        raise ValueError("Artifact manifest has invalid version_code")
    metadata = {"version": tag[1:], "version_code": code, "tag": tag}
    # Recompute filenames, count, compiled APK debug flag, sizes and digests rather
    # than trusting a prior validation step or untrusted paths in the JSON.
    actual = _metadata.artifact_manifest(metadata, root)
    if manifest != actual:
        raise ValueError("Artifact manifest does not match the checked packages")
    if any(Path(asset["path"]).name != asset["path"] for asset in actual["artifacts"]):
        raise ValueError("Publication packages must be directly inside the artifact directory")
    notes = Path(notes)
    if notes.is_symlink() or not notes.is_file() or not notes.read_text(encoding="utf-8").strip():
        raise ValueError("Missing, empty or unsafe release notes")
    return sorted(root / asset["path"] for asset in actual["artifacts"]), manifest_path


def _asset_sizes(info):
    if not isinstance(info, dict) or not isinstance(info.get("assets"), list):
        raise ValueError("Invalid GitHub release assets response")
    sizes = {}
    for item in info["assets"]:
        if (not isinstance(item, dict) or not isinstance(item.get("name"), str)
                or type(item.get("size")) is not int or item["size"] < 0 or item["name"] in sizes):
            raise ValueError("Invalid or duplicate GitHub release asset")
        sizes[item["name"]] = item["size"]
    return sizes


def _check_sizes(paths, sizes):
    for path in paths:
        if sizes.get(path.name) != path.stat().st_size:
            raise ValueError(f"Missing or truncated uploaded package: {path.name}")


def publish(tag, directory, notes):
    root = Path(directory).resolve()
    files, manifest_path = _checked_files(tag, root, notes)
    # Absolute local paths cannot be mistaken for gh options if a directory or
    # notes filename supplied by the caller starts with '-'.
    notes = str(Path(notes).resolve())
    existing = subprocess.run(["gh", "release", "view", tag, "--json", "isDraft,assets"], capture_output=True, text=True)
    if existing.returncode:
        # Only a recognized not-found response permits creation. A successful
        # list after a transient/auth error does not make that error a 404.
        not_found = re.fullmatch(r"(?:release not found|HTTP 404: Not Found(?: \([^\r\n]+\))?)", existing.stderr.strip(), re.IGNORECASE)
        if not not_found:
            raise ValueError("Release lookup failed; refusing to infer absence")
        remote = _json(gh("release", "list", "--limit", "1000", "--json", "tagName"))
        if not isinstance(remote, list) or any(not isinstance(item, dict) or not isinstance(item.get("tagName"), str) for item in remote):
            raise ValueError("Invalid GitHub release list response")
        if any(item["tagName"] == tag for item in remote):
            raise ValueError("Release lookup failed for an existing release")
        gh("release", "create", tag, "--draft", "--verify-tag", "--title", f"MatrixTalk {tag}", "--notes-file", notes)
    else:
        info = _json(existing.stdout)
        if not isinstance(info, dict) or type(info.get("isDraft")) is not bool:
            raise ValueError("Invalid GitHub release draft state")
        sizes = _asset_sizes(info)
        if not info["isDraft"]:
            _check_sizes(files, sizes)
            # A successful publish followed by a failed AppImage dispatch can be
            # rerun safely. Never clobber assets of an already public release.
            print(f"Release {tag} already published with all four packages; leaving assets unchanged")
            return
    gh("release", "upload", tag, *map(str, files), str(manifest_path), "--clobber")
    info = _json(gh("release", "view", tag, "--json", "isDraft,assets"))
    _check_sizes([*files, manifest_path], _asset_sizes(info))
    if info.get("isDraft") is not True:
        raise ValueError("Release is no longer a draft; refusing to change public metadata")
    releases = _json(gh("release", "list", "--limit", "1000", "--json", "tagName,isDraft,isPrerelease"))
    if not isinstance(releases, list):
        raise ValueError("Invalid GitHub latest-release list response")
    versions = []
    for release in releases:
        if (not isinstance(release, dict) or not isinstance(release.get("tagName"), str)
                or type(release.get("isDraft")) is not bool or type(release.get("isPrerelease")) is not bool):
            raise ValueError("Invalid GitHub latest-release list entry")
        if release["isDraft"] or release["isPrerelease"]:
            continue
        match = _metadata.VERSION_PATTERN.fullmatch(release["tagName"][1:]) if release["tagName"].startswith("v") else None
        # Only canonical, MSI-compatible stable tags can be built by this pipeline.
        if match and all(len(part) <= len(str(limit)) and int(part) <= limit
                         for part, limit in zip(match.groups(), (255, 255, 65535))):
            versions.append(tuple(map(int, match.groups())))
    current = tuple(map(int, tag[1:].split(".")))
    latest = "--latest=false" if versions and max(versions) > current else "--latest"
    gh("release", "edit", tag, "--draft=false", "--notes-file", notes, latest)
    print(f"Published {tag} with APK, MSI, DMG and DEB")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 4:
            raise ValueError("Usage: publish-release.py TAG ARTIFACT_DIRECTORY NOTES_FILE")
        publish(*sys.argv[1:])
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release publication failed: {type(error).__name__}: {error if not isinstance(error, subprocess.CalledProcessError) else 'GitHub CLI request failed'}", file=sys.stderr)
        sys.exit(1)
