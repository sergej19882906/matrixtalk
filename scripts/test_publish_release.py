"""Offline publication regression tests; every GitHub CLI call is mocked."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.dont_write_bytecode = True
SCRIPTS = Path(__file__).resolve().parent


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


publish = load("publish_release", "publish-release.py")
fixtures = load("release_test_fixtures", "test_release_metadata.py")


class PublishReleaseTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.tag = "v1.42.1"
        self.notes = self.root / "notes.md"
        self.notes.write_text("Release fixes.\n", encoding="utf-8")
        self.assets = self.root / "assets"
        self.assets.mkdir()
        with zipfile.ZipFile(self.assets / "MatrixTalk-v1.42.1.apk", "w") as archive:
            archive.writestr("AndroidManifest.xml", fixtures.binary_manifest())
        for name in ("MatrixTalk-1.42.1.msi", "MatrixTalk-1.42.1.dmg", "matrixtalk_1.42.1_amd64.deb"):
            (self.assets / name).write_bytes(b"package fixture\n")
        self.manifest = self.assets / "release-manifest.json"
        self.refresh_manifest()
        self.calls = []
        self.responses = []
        patcher = mock.patch.object(publish.subprocess, "run", side_effect=self.fake_run)
        self.run = patcher.start()
        self.addCleanup(patcher.stop)

    def refresh_manifest(self):
        metadata = {"version": "1.42.1", "version_code": 63, "tag": self.tag}
        manifest = publish._metadata.artifact_manifest(metadata, self.assets)
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

    def fake_run(self, command, **kwargs):
        self.assertEqual("gh", command[0])
        self.assertTrue(kwargs.get("capture_output"))
        self.assertTrue(kwargs.get("text"))
        self.assertNotIn("shell", kwargs)
        self.calls.append(command)
        self.assertTrue(self.responses, "Unexpected CLI call (network is always blocked)")
        response = self.responses.pop(0)
        if isinstance(response, BaseException):
            raise response
        code, stdout, stderr = response
        if code and kwargs.get("check"):
            raise subprocess.CalledProcessError(code, command, stdout, stderr)
        return subprocess.CompletedProcess(command, code, stdout, stderr)

    def response(self, draft=True, include_manifest=True, omit=None, wrong_size=None):
        assets = []
        for path in sorted(self.assets.iterdir()):
            if path == self.manifest and not include_manifest or path.name == omit:
                continue
            assets.append({"name": path.name, "size": path.stat().st_size + (1 if path.name == wrong_size else 0)})
        return (0, json.dumps({"isDraft": draft, "assets": assets}), "")

    def invoke(self):
        with contextlib.redirect_stdout(io.StringIO()):
            publish.publish(self.tag, self.assets, str(self.notes))

    def assert_no_publication(self):
        self.assertFalse(any(command[1:3] == ["release", "edit"] for command in self.calls))

    def test_complete_existing_published_leaves_assets_untouched(self):
        self.responses = [self.response(draft=False, include_manifest=False)]
        before = {path.name: path.read_bytes() for path in self.assets.iterdir()}
        self.invoke()
        self.assertEqual(["release", "view", self.tag], self.calls[0][1:4])
        self.assertEqual(1, len(self.calls))
        self.assertEqual(before, {path.name: path.read_bytes() for path in self.assets.iterdir()})

    def test_existing_draft_upload_then_publish(self):
        self.responses = [self.response(), (0, "", ""), self.response(), (0, "[]", ""), (0, "", "")]
        self.invoke()
        self.assertEqual(["view", "upload", "view", "list", "edit"], [call[2] for call in self.calls])
        upload = self.calls[1]
        self.assertEqual(5, len(upload[4:-1]))
        self.assertIn(str(self.manifest), upload)
        self.assertEqual("--clobber", upload[-1])
        self.assertIn("--draft=false", self.calls[-1])
        self.assertIn("--latest", self.calls[-1])

    def test_latest_numeric_lower_equal_higher_and_nonstable_versions(self):
        for tag, draft, prerelease, expected in (
                ("v1.43.0", False, False, "--latest=false"),
                ("v2.0.0", False, False, "--latest=false"),
                ("v1.9.0", False, False, "--latest"),
                ("v1.42.1", False, False, "--latest"),
                ("v1.43.0", True, False, "--latest"),
                ("v1.43.0", False, True, "--latest"),
                ("v1.43.0-beta", False, False, "--latest")):
            with self.subTest(tag=tag, draft=draft, prerelease=prerelease):
                self.calls.clear()
                listing = [{"tagName": tag, "isDraft": draft, "isPrerelease": prerelease}]
                self.responses = [self.response(), (0, "", ""), self.response(),
                                  (0, json.dumps(listing), ""), (0, "", "")]
                self.invoke()
                self.assertEqual(expected, self.calls[-1][-1])
                self.assertEqual(["gh", "release", "list", "--limit", "1000", "--json", "tagName,isDraft,isPrerelease"], self.calls[-2])

    def test_latest_list_failure_or_invalid_schema_prevents_publication(self):
        for response in ((1, "", "network error"), (0, "{}", ""), (0, "[{}]", ""), (0, "invalid", "")):
            self.calls.clear()
            self.responses = [self.response(), (0, "", ""), self.response(), response]
            with self.subTest(response=response), self.assertRaises((ValueError, subprocess.CalledProcessError)):
                self.invoke()
            self.assert_no_publication()

    def test_not_found_lookup_and_list_create_draft_then_upload(self):
        for message in ("release not found\n", "HTTP 404: Not Found (https://api.github.com/repos/example/releases/tags/v1.42.1)\n"):
            with self.subTest(message=message):
                self.calls.clear()
                self.responses = [(1, "", message), (0, '[{"tagName":"v1.42.0"}]', ""),
                                  (0, "", ""), (0, "", ""), self.response(), (0, "[]", ""), (0, "", "")]
                self.invoke()
                self.assertEqual(["view", "list", "create", "upload", "view", "list", "edit"], [call[2] for call in self.calls])
                self.assertIn("--draft", self.calls[2])
                self.assertIn("--verify-tag", self.calls[2])

    def test_missing_manifest_prevents_any_cli_call(self):
        self.manifest.unlink()
        with self.assertRaises(ValueError):
            self.invoke()
        self.run.assert_not_called()

    def test_invalid_manifest_version_tag_code_hash_and_paths(self):
        original = json.loads(self.manifest.read_text(encoding="utf-8"))
        for field, value in (("version", "1.42.0"), ("tag", "v1.42.0"), ("version_code", True),
                             ("version_code", 0), ("version_code", 2100000001), ("artifacts", [])):
            with self.subTest(field=field, value=value):
                data = dict(original, **{field: value})
                self.manifest.write_text(json.dumps(data), encoding="utf-8")
                with self.assertRaises(ValueError):
                    self.invoke()
                self.run.assert_not_called()
        for field, value in (("path", "../outside.apk"), ("sha256", "0" * 64), ("size", 1), ("type", "dmg")):
            data = json.loads(json.dumps(original))
            data["artifacts"][0][field] = value
            self.manifest.write_text(json.dumps(data), encoding="utf-8")
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.invoke()
            self.run.assert_not_called()

    def test_malformed_or_duplicate_json_prevents_cli(self):
        for text in ("{", "[]", '{"tag":"v1.42.1","tag":"v1.42.0"}'):
            self.manifest.write_text(text, encoding="utf-8")
            with self.assertRaises(ValueError):
                self.invoke()
            self.run.assert_not_called()

    def test_missing_wrong_version_or_tampered_package_prevents_cli(self):
        path = self.assets / "MatrixTalk-1.42.1.msi"
        original = path.read_bytes()
        path.unlink()
        with self.assertRaises(ValueError):
            self.invoke()
        path.write_bytes(original)
        changed = self.assets / "MatrixTalk-1.42.0.msi"
        path.rename(changed)
        with self.assertRaises(ValueError):
            self.invoke()
        changed.rename(path)
        path.write_bytes(b"different content")
        with self.assertRaises(ValueError):
            self.invoke()
        self.run.assert_not_called()

    def test_invalid_tag_and_notes_prevent_cli(self):
        for tag in ("v01.42.1", "v256.0.0", "v1.42.1\n", "--help"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                publish.publish(tag, self.assets, str(self.notes))
        self.notes.unlink()
        with self.assertRaises(ValueError):
            self.invoke()
        self.notes.write_text(" \n", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.invoke()
        self.run.assert_not_called()

    def test_local_paths_passed_to_cli_are_absolute(self):
        files, manifest = publish._checked_files(self.tag, self.assets, self.notes)
        self.assertTrue(all(path.is_absolute() for path in [*files, manifest]))
        self.responses = [self.response(), (0, "", ""), self.response(), (0, "[]", ""), (0, "", "")]
        self.invoke()
        upload = self.calls[1]
        self.assertTrue(all(Path(value).is_absolute() for value in upload[4:-1]))
        edit = self.calls[-1]
        self.assertTrue(Path(edit[edit.index("--notes-file") + 1]).is_absolute())

    def test_auth_and_network_lookup_errors_never_create(self):
        for message in ("HTTP 401: Bad credentials", "connection reset", "HTTP 403: Forbidden", ""):
            self.calls.clear()
            self.responses = [(1, "", message)]
            with self.subTest(message=message), self.assertRaises(ValueError):
                self.invoke()
            self.assertEqual(1, len(self.calls))
            self.assert_no_publication()

    def test_not_found_but_list_contains_tag_prevents_create(self):
        self.responses = [(1, "", "release not found"), (0, json.dumps([{"tagName": self.tag}]), "")]
        with self.assertRaises(ValueError):
            self.invoke()
        self.assertEqual(["view", "list"], [call[2] for call in self.calls])

    def test_list_errors_or_bad_schema_prevent_create(self):
        for response in ((1, "", "auth error"), (0, "{}", ""), (0, "[{}]", ""), (0, "invalid JSON", "")):
            self.calls.clear()
            self.responses = [(1, "", "release not found"), response]
            with self.subTest(response=response), self.assertRaises((ValueError, subprocess.CalledProcessError)):
                self.invoke()
            self.assertEqual(2, len(self.calls))
            self.assert_no_publication()

    def test_create_and_upload_errors_prevent_publish(self):
        for failed_stage in ("create", "upload"):
            self.calls.clear()
            if failed_stage == "create":
                self.responses = [(1, "", "release not found"), (0, "[]", ""), (1, "", "error")]
            else:
                self.responses = [self.response(), (1, "", "error")]
            with self.subTest(stage=failed_stage), self.assertRaises(subprocess.CalledProcessError):
                self.invoke()
            self.assert_no_publication()

    def test_missing_or_size_mismatched_upload_prevents_publish(self):
        for name in ("MatrixTalk-1.42.1.msi", self.manifest.name):
            for issue in ("missing", "size"):
                self.calls.clear()
                response = self.response(omit=name if issue == "missing" else None,
                                         wrong_size=name if issue == "size" else None)
                self.responses = [self.response(), (0, "", ""), response]
                with self.subTest(name=name, issue=issue), self.assertRaisesRegex(ValueError, "Missing or truncated"):
                    self.invoke()
                self.assert_no_publication()

    def test_published_incomplete_or_truncated_assets_are_never_changed(self):
        for response in (self.response(draft=False, omit="MatrixTalk-1.42.1.msi"),
                         self.response(draft=False, wrong_size="MatrixTalk-1.42.1.msi")):
            self.calls.clear()
            self.responses = [response]
            with self.assertRaises(ValueError):
                self.invoke()
            self.assertEqual(1, len(self.calls))

    def test_invalid_remote_state_or_post_upload_public_state_prevents_publish(self):
        for data in ({"isDraft": "false", "assets": []}, {"isDraft": True, "assets": [{}]},
                     {"isDraft": True, "assets": [{"name": "x", "size": 1}, {"name": "x", "size": 1}]}):
            self.calls.clear()
            self.responses = [(0, json.dumps(data), "")]
            with self.assertRaises(ValueError):
                self.invoke()
            self.assert_no_publication()
        self.calls.clear()
        self.responses = [self.response(), (0, "", ""), self.response(draft=False)]
        with self.assertRaises(ValueError):
            self.invoke()
        self.assert_no_publication()


if __name__ == "__main__":
    unittest.main()
