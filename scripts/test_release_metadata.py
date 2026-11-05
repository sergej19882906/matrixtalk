#!/usr/bin/env python3
"""Standard-library release checks: python -I scripts/test_release_metadata.py."""

import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


# Explicit file loading also works with -I, which excludes scripts/ from sys.path.
sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().with_name("release_metadata.py")
SPEC = importlib.util.spec_from_file_location("release_metadata", SCRIPT)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def binary_manifest(debuggable=False, utf8=True, value_type=0x12, include_attribute=True):
    """Build a minimal Android binary XML fixture, not a plain XML mock."""
    strings = ["manifest", "application", release.ANDROID_NAMESPACE, "debuggable", "true", "false"]
    offsets = []
    encoded = bytearray()
    for text in strings:
        offsets.append(len(encoded))
        if utf8:
            raw = text.encode("utf-8")
            encoded.extend(bytes((len(text), len(raw))) + raw + b"\0")
        else:
            raw = text.encode("utf-16-le")
            encoded.extend(struct.pack("<H", len(text)) + raw + b"\0\0")
    while len(encoded) % 4:
        encoded.append(0)
    start = 28 + len(strings) * 4
    pool = (struct.pack("<HHIIIIII", 1, 28, start + len(encoded), len(strings), 0,
                        0x100 if utf8 else 0, start, 0)
            + struct.pack("<{}I".format(len(strings)), *offsets) + encoded)

    def node(kind, extension):
        return struct.pack("<HHIII", kind, 16, 16 + len(extension), 1, release.NO_STRING) + extension

    def opening(name, attributes=b""):
        extension = struct.pack("<IIHHHHHH", release.NO_STRING, name, 20, 20,
                                len(attributes) // 20, 0, 0, 0) + attributes
        return node(0x0102, extension)

    def closing(name):
        return node(0x0103, struct.pack("<II", release.NO_STRING, name))

    value = (4 if debuggable else 5) if value_type == 3 else (0xFFFFFFFF if debuggable else 0)
    attribute = struct.pack("<IIIHBBI", 2, 3, release.NO_STRING, 8, 0, value_type, value)
    body = pool + opening(0) + opening(1, attribute if include_attribute else b"") + closing(1) + closing(0)
    return struct.pack("<HHI", 3, 8, len(body) + 8) + body


class ReleaseMetadataTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.properties()
        self.notes_text("# History\n\n## 1.42.1\nRelease fixes.\n\n### Build\nCode 63.\n\n---\n\n## 1.42.0\nOld release.\n")
        self.assets = self.root / "assets"
        self.assets.mkdir()

    def properties(self, version="1.42.1", code="63", extra=""):
        (self.root / "gradle.properties").write_text(
            "# ignored\norg.gradle.jvmargs=-Xmx2048m\nreleaseVersion={}\nandroidVersionCode={}\n{}".format(version, code, extra),
            encoding="utf-8")

    def notes_text(self, text):
        (self.root / "RELEASE_NOTES.md").write_text(text, encoding="utf-8")

    def metadata(self, tag="v1.42.1"):
        return release.read_metadata(self.root, tag)

    def apk(self, debug=False, filename=None, manifest=None):
        path = self.assets / (filename or "MatrixTalk-v1.42.1{}.apk".format("-debug" if debug else ""))
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", binary_manifest(debug) if manifest is None else manifest)
        return path

    def all_assets(self, debug=False, nested=False):
        self.apk(debug)
        for name in ("MatrixTalk-1.42.1.msi", "MatrixTalk-1.42.1.dmg", "matrixtalk_1.42.1_amd64.deb"):
            directory = self.assets / "desktop" if nested else self.assets
            directory.mkdir(exist_ok=True)
            (directory / name).write_bytes(b"fixture package\n")

    def invoke(self, *arguments, env=None):
        return subprocess.run([sys.executable, "-I", str(SCRIPT), *arguments, "--root", str(self.root)],
                              capture_output=True, text=True, env=env, check=False)

    def call_main(self, *arguments):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = release.main([*arguments, "--root", str(self.root)])
        return result, stdout.getvalue(), stderr.getvalue()

    def test_canonical_metadata_and_optional_tag(self):
        expected = {"version": "1.42.1", "version_code": 63, "tag": "v1.42.1"}
        self.assertEqual(expected, self.metadata())
        self.assertEqual(expected, release.read_metadata(self.root))
        self.assertEqual(0, self.invoke("check").returncode)
        self.assertEqual(0, self.invoke("check", "--tag", "v1.42.1").returncode)

    def test_version_boundaries(self):
        for version in ("0.0.0", "255.255.65535", "1.0.65535"):
            with self.subTest(version=version):
                self.properties(version=version)
                self.assertEqual(version, release.read_metadata(self.root)["version"])

    def test_reject_invalid_versions(self):
        for version in ("1.42", "1.42.1.0", "v1.42.1", "01.42.1", "1.042.1", "1.42.01", "-1.42.1",
                        "+1.42.1", "1.42.1-beta", "1.42.1+build", "256.0.0", "1.256.0", "1.0.65536",
                        "１.42.1", "1 .42.1", "1.42.1 # comment", "", "9" * 5000 + ".0.0"):
            with self.subTest(version=version):
                self.properties(version=version)
                with self.assertRaises(release.ReleaseMetadataError):
                    release.read_metadata(self.root)

    def test_version_code_boundaries(self):
        for code in ("1", "2100000000"):
            self.properties(code=code)
            self.assertEqual(int(code), self.metadata()["version_code"])

    def test_reject_invalid_version_codes(self):
        for code in ("0", "-1", "+63", "063", "63.0", "63 # comment", "", "٢", "2100000001", "9" * 5000):
            with self.subTest(code=code):
                self.properties(code=code)
                with self.assertRaises(release.ReleaseMetadataError):
                    self.metadata()

    def test_reject_nonexact_tags(self):
        for tag in ("1.42.1", "V1.42.1", "v1.42.0", "v1.42.10", "refs/tags/v1.42.1", "v1.42.1\n", "v1.42.1\nkey=bad"):
            with self.subTest(tag=tag):
                with self.assertRaises(release.ReleaseMetadataError):
                    self.metadata(tag)

    def test_missing_and_duplicate_properties(self):
        for text in ("releaseVersion=1.42.1\n", "androidVersionCode=63\n", "releaseVersion\nandroidVersionCode=63\n"):
            (self.root / "gradle.properties").write_text(text, encoding="utf-8")
            with self.assertRaises(release.ReleaseMetadataError):
                self.metadata()
        for extra in ("releaseVersion=1.42.1\n", "androidVersionCode=64\n"):
            self.properties(extra=extra)
            with self.assertRaisesRegex(release.ReleaseMetadataError, "duplicate"):
                self.metadata()

    def test_properties_comments_whitespace_and_bom(self):
        (self.root / "gradle.properties").write_text(
            "\ufeff! comment\n# releaseVersion=99.0.0\n releaseVersion : 1.42.1 \nandroidVersionCode 63\n", encoding="utf-8")
        self.assertEqual(63, self.metadata()["version_code"])

    def test_notes_extract_exact_section(self):
        self.assertEqual("Release fixes.\n\n### Build\nCode 63.\n", release.release_notes(self.root, "1.42.1"))
        output = self.root / "generated" / "notes.md"
        result = self.invoke("notes", "--tag", "v1.42.1", "--output", str(output))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"Release fixes.\n\n### Build\nCode 63.\n", output.read_bytes())

    def test_missing_prefix_and_duplicate_notes_headings(self):
        for text in ("## 1.42.10\nwrong\n", "## 1.42.1 extra\nwrong\n", "## v1.42.1\nwrong\n",
                     "### 1.42.1\nwrong\n", "## 1.42.1 \nwrong\n", "## 1.42.1\na\n## 1.42.1\nb\n"):
            with self.subTest(text=text):
                self.notes_text(text)
                result = self.invoke("check")
                self.assertEqual(1, result.returncode)
                self.assertIn("heading", result.stderr)
                self.assertNotIn("Traceback", result.stderr)

    def test_notes_fenced_fake_heading_and_top_level_boundary(self):
        self.notes_text("```markdown\n## 1.42.1\n```\n## 1.42.1\nFix.\n~~~\n## 8.0.0\n~~~\n# Appendix\nExclude.\n")
        self.assertEqual("Fix.\n~~~\n## 8.0.0\n~~~\n", release.release_notes(self.root, "1.42.1"))
        self.notes_text("```\n## 1.42.1\n```\n")
        with self.assertRaises(release.ReleaseMetadataError):
            release.release_notes(self.root, "1.42.1")

    def test_notes_last_section_unicode_crlf(self):
        (self.root / "RELEASE_NOTES.md").write_bytes("\ufeff## 1.42.1\r\nИсправления.\r\n".encode("utf-8"))
        self.assertEqual("Исправления.\n", release.release_notes(self.root, "1.42.1"))

    def test_valid_release_and_debug_artifacts(self):
        for debug in (False, True):
            with self.subTest(debug=debug):
                for path in self.assets.rglob("*"):
                    if path.is_file():
                        path.unlink()
                self.all_assets(debug=debug, nested=True)
                (self.assets / "readme.txt").write_text("ignored", encoding="utf-8")
                manifest = release.artifact_manifest(self.metadata(), self.assets)
                self.assertEqual("v1.42.1", manifest["tag"])
                self.assertEqual(["apk", "msi", "dmg", "deb"], [asset["type"] for asset in manifest["artifacts"]])
                self.assertEqual(debug, manifest["artifacts"][0]["debuggable"])
                for asset in manifest["artifacts"]:
                    data = (self.assets / asset["path"]).read_bytes()
                    self.assertEqual(len(data), asset["size"])
                    self.assertEqual(hashlib.sha256(data).hexdigest(), asset["sha256"])
                    self.assertNotIn("\\", asset["path"])

    def test_manifest_written_atomically_and_printed(self):
        self.all_assets()
        output = self.root / "generated" / "manifest.json"
        result = self.invoke("artifacts", "--tag", "v1.42.1", "--directory", str(self.assets), "--manifest-output", str(output))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(json.loads(result.stdout), json.loads(output.read_text(encoding="utf-8")))
        self.assertEqual([output], list(output.parent.iterdir()))

    def test_artifact_stdout_without_manifest_file(self):
        self.all_assets()
        result = self.invoke("artifacts", "--tag", "v1.42.1", "--directory", str(self.assets))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(4, len(json.loads(result.stdout)["artifacts"]))
        self.assertFalse(list(self.root.rglob("*.json")))

    def test_missing_artifacts(self):
        self.all_assets()
        for suffix in (".apk", ".msi", ".dmg", ".deb"):
            path = next(self.assets.glob("*" + suffix))
            data = path.read_bytes()
            path.unlink()
            with self.subTest(suffix=suffix), self.assertRaisesRegex(release.ReleaseMetadataError, "exactly one"):
                release.artifact_manifest(self.metadata(), self.assets)
            path.write_bytes(data)

    def test_duplicate_artifacts_in_nested_directories(self):
        self.all_assets()
        directory = self.assets / "duplicate"
        directory.mkdir()
        for suffix in (".apk", ".msi", ".dmg", ".deb"):
            original = next(self.assets.glob("*" + suffix))
            copy = directory / original.name
            copy.write_bytes(original.read_bytes())
            with self.subTest(suffix=suffix), self.assertRaisesRegex(release.ReleaseMetadataError, "exactly one"):
                release.artifact_manifest(self.metadata(), self.assets)
            copy.unlink()

    def test_release_and_debug_apks_together_rejected(self):
        self.all_assets()
        self.apk(debug=True)
        with self.assertRaisesRegex(release.ReleaseMetadataError, "exactly one .apk"):
            release.artifact_manifest(self.metadata(), self.assets)

    def test_wrong_version_architecture_or_case_filename(self):
        self.all_assets()
        for old, new in (("MatrixTalk-v1.42.1.apk", "MatrixTalk-v1.42.0.apk"),
                         ("MatrixTalk-1.42.1.msi", "MatrixTalk-1.42.0.msi"),
                         ("MatrixTalk-1.42.1.dmg", "MatrixTalk-1.42.10.dmg"),
                         ("matrixtalk_1.42.1_amd64.deb", "matrixtalk_1.42.1_arm64.deb"),
                         ("MatrixTalk-1.42.1.msi", "MatrixTalk-1.42.1.MSI")):
            original, renamed = self.assets / old, self.assets / new
            original.rename(renamed)
            with self.subTest(filename=new), self.assertRaisesRegex(release.ReleaseMetadataError, "unexpected artifact filename"):
                release.artifact_manifest(self.metadata(), self.assets)
            renamed.rename(original)

    def test_reject_debug_apk_mislabeled_as_release_and_inverse(self):
        self.all_assets()
        path = self.assets / "MatrixTalk-v1.42.1.apk"
        path.unlink()
        self.apk(debug=True, filename=path.name)
        with self.assertRaisesRegex(release.ReleaseMetadataError, "debuggable does not match"):
            release.artifact_manifest(self.metadata(), self.assets)
        path.unlink()
        self.apk(debug=False, filename="MatrixTalk-v1.42.1-debug.apk")
        with self.assertRaisesRegex(release.ReleaseMetadataError, "debuggable does not match"):
            release.artifact_manifest(self.metadata(), self.assets)

    def test_apk_binary_utf16_string_boolean_and_default_false(self):
        for debug in (True, False):
            for utf8 in (True, False):
                for value_type in (0x12, 3):
                    with self.subTest(debug=debug, utf8=utf8, value_type=value_type):
                        path = self.apk(manifest=binary_manifest(debug, utf8=utf8, value_type=value_type))
                        self.assertEqual(debug, release.apk_debuggable(path))
        self.assertFalse(release.apk_debuggable(self.apk(manifest=binary_manifest(include_attribute=False))))

    def test_reject_invalid_or_ambiguous_apk_manifests(self):
        for data in (b"", b"<manifest><application/></manifest>", binary_manifest()[:-1],
                     binary_manifest(value_type=1)):
            with self.subTest(data=data[:12]), self.assertRaises(release.ReleaseMetadataError):
                release.apk_debuggable(self.apk(manifest=data))
        path = self.assets / "invalid.apk"
        path.write_bytes(b"not a zip")
        with self.assertRaises(release.ReleaseMetadataError):
            release.apk_debuggable(path)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("other.xml", "not a manifest")
        with self.assertRaises(release.ReleaseMetadataError):
            release.apk_debuggable(path)
        with contextlib.redirect_stderr(io.StringIO()), mock.patch("warnings.warn"):
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", binary_manifest())
                archive.writestr("AndroidManifest.xml", binary_manifest(True))
        with self.assertRaises(release.ReleaseMetadataError):
            release.apk_debuggable(path)

    def test_malformed_binary_chunks_do_not_escape_as_tracebacks(self):
        good = binary_manifest()
        mutations = [good[:offset] for offset in range(0, len(good), 7)]
        # Restore outer length to exercise truncated nested chunks rather than just the outer header.
        for data in mutations:
            if len(data) >= 8:
                data = data[:4] + struct.pack("<I", len(data)) + data[8:]
            with self.subTest(length=len(data)), self.assertRaises(release.ReleaseMetadataError):
                release.apk_debuggable(self.apk(manifest=data))

    def test_missing_directory_and_empty_assets(self):
        with self.assertRaises(release.ReleaseMetadataError):
            release.artifact_manifest(self.metadata(), self.root / "missing")
        self.all_assets()
        (self.assets / "MatrixTalk-1.42.1.msi").write_bytes(b"")
        with self.assertRaisesRegex(release.ReleaseMetadataError, "empty"):
            release.artifact_manifest(self.metadata(), self.assets)

    def test_artifact_named_directory_rejected(self):
        self.all_assets()
        (self.assets / "other.apk").mkdir()
        with self.assertRaisesRegex(release.ReleaseMetadataError, "regular"):
            release.artifact_manifest(self.metadata(), self.assets)

    def test_failed_validation_preserves_existing_manifest_and_notes(self):
        output = self.root / "output.txt"
        output.write_text("keep", encoding="utf-8")
        result = self.invoke("artifacts", "--tag", "v1.42.1", "--directory", str(self.assets), "--manifest-output", str(output))
        self.assertEqual(1, result.returncode)
        self.assertEqual("keep", output.read_text(encoding="utf-8"))
        self.notes_text("## 1.42.0\nOld.\n")
        result = self.invoke("notes", "--tag", "v1.42.1", "--output", str(output))
        self.assertEqual(1, result.returncode)
        self.assertEqual("keep", output.read_text(encoding="utf-8"))

    def test_manifest_cannot_overwrite_artifact(self):
        self.all_assets()
        output = self.assets / "MatrixTalk-1.42.1.msi"
        original = output.read_bytes()
        result = self.invoke("artifacts", "--tag", "v1.42.1", "--directory", str(self.assets), "--manifest-output", str(output))
        self.assertEqual(1, result.returncode)
        self.assertIn("overwrite", result.stderr)
        self.assertEqual(original, output.read_bytes())

    def test_atomic_write_replace_failure_cleans_temporary(self):
        output = self.root / "output.txt"
        output.write_text("keep", encoding="utf-8")
        with mock.patch.object(release.os, "replace", side_effect=OSError("fixture failure")):
            with self.assertRaises(OSError):
                release.atomic_write(output, "new")
        self.assertEqual("keep", output.read_text(encoding="utf-8"))
        self.assertFalse(list(self.root.glob(".output.txt.*.tmp")))

    def test_outputs_stdout_and_github_output_append(self):
        expected = "version=1.42.1\nversion_code=63\ntag=v1.42.1\n"
        environment = dict(os.environ)
        environment.pop("GITHUB_OUTPUT", None)
        result = self.invoke("outputs", env=environment)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(expected, result.stdout)
        output = self.root / "github-output"
        output.write_bytes(b"existing=value\n")
        environment["GITHUB_OUTPUT"] = str(output)
        result = self.invoke("outputs", "--tag", "v1.42.1", env=environment)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("existing=value\n" + expected, output.read_text(encoding="utf-8"))
        explicit = self.root / "explicit-output"
        result = self.invoke("outputs", "--output", str(explicit), env=environment)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(expected, explicit.read_text(encoding="utf-8"))
        self.assertEqual("existing=value\n" + expected, output.read_text(encoding="utf-8"))

    def test_outputs_validation_failure_does_not_append(self):
        output = self.root / "github-output"
        output.write_text("keep\n", encoding="utf-8")
        result = self.invoke("outputs", "--tag", "v1.42.1\nkey=bad", "--output", str(output))
        self.assertEqual(1, result.returncode)
        self.assertEqual("keep\n", output.read_text(encoding="utf-8"))

    def test_missing_input_and_io_errors_report_cleanly(self):
        (self.root / "gradle.properties").unlink()
        result = self.invoke("check")
        self.assertEqual(1, result.returncode)
        self.assertIn("release metadata error", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.properties()
        (self.root / "RELEASE_NOTES.md").unlink()
        self.assertEqual(1, self.invoke("check").returncode)

    def test_required_arguments_and_help(self):
        for arguments in (("notes",), ("artifacts",), ("artifacts", "--tag", "v1.42.1")):
            self.assertEqual(2, self.invoke(*arguments).returncode)
        self.assertEqual(0, self.invoke("--help").returncode)


if __name__ == "__main__":
    unittest.main()
