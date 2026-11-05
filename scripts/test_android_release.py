"""Offline tests: python -I -m unittest discover -s scripts -p 'test_*release.py'."""
import base64
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("android_release", Path(__file__).with_name("android-release.py"))
android = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(android)


class AndroidReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="matrix-android-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, previous)
        self.sdk = self.root / "sdk"
        self.sdk.mkdir()
        self.environment = mock.patch.dict(os.environ, {"ANDROID_SDK_ROOT": str(self.sdk)}, clear=True)
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.stdout = io.StringIO()
        self.stderr = io.StringIO()
        self.out_patch = contextlib.redirect_stdout(self.stdout)
        self.err_patch = contextlib.redirect_stderr(self.stderr)
        self.out_patch.__enter__()
        self.err_patch.__enter__()
        self.addCleanup(self.out_patch.__exit__, None, None, None)
        self.addCleanup(self.err_patch.__exit__, None, None, None)
        self.native = mock.patch.object(android.subprocess, "run", side_effect=AssertionError("Native tools are forbidden in tests"))
        self.native.start()
        self.addCleanup(self.native.stop)

    def credentials(self):
        os.environ.update({"KEYSTORE_BASE64": base64.b64encode(b"mock-keystore").decode(),
                           "KEYSTORE_ALIAS": "test-alias", "KEYSTORE_PASSWORD": "private-store-password",
                           "KEY_PASSWORD": "private-key-password"})

    def tools(self, version="35.0.0", names=("aapt", "apksigner")):
        directory = self.sdk / "build-tools" / version
        directory.mkdir(parents=True, exist_ok=True)
        for name in names:
            (directory / (name + (".exe" if os.name == "nt" and name == "aapt" else ".bat" if os.name == "nt" else ""))).touch()
        return directory

    def apk(self, signed=False):
        variant = "release" if signed else "debug"
        path = Path(f"app/build/outputs/apk/{variant}/app-{variant}.apk")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"mock-apk")
        self.tools()
        return path

    def badging(self, *, version="1.39.0", code="13900", package="com.matrix.messenger", debug=False):
        return (f"package: name='{package}' versionCode='{code}' versionName='{version}' platformBuildVersionName='15'\n"
                "application: label='MatrixTalk'\n" + ("application-debuggable\n" if debug else ""))

    def test_zero_credentials_prepare_is_explicit_debug_and_cleanup_noop(self):
        for name in android.SIGNING_FILES:
            Path(name).write_bytes(b"preexisting")
        android.prepare()
        android.cleanup()
        self.assertIn("debug APK", self.stdout.getvalue())
        for name in android.SIGNING_FILES:
            self.assertEqual(Path(name).read_bytes(), b"preexisting")
        self.assertFalse(Path(android.STATE_DIRECTORY).exists())

    def test_partial_credentials_rejected_by_prepare_and_verify_without_values(self):
        for name in android.SECRET_NAMES:
            with self.subTest(name=name), mock.patch.dict(os.environ, {name: "never-print-this-secret"}):
                self.assertEqual(android.main(["prepare"]), 1)
                self.assertEqual(android.main(["verify", "1.39.0", "13900", "v1.39.0"]), 1)
        for missing in android.SECRET_NAMES:
            with self.subTest(missing=missing):
                self.credentials()
                os.environ.pop(missing)
                with self.assertRaisesRegex(android.ReleaseError, "Incomplete"):
                    android.prepare()
                with self.assertRaisesRegex(android.ReleaseError, "Incomplete"):
                    android.verify("1.39.0", "13900", "v1.39.0")
                for name in android.SECRET_NAMES:
                    os.environ.pop(name, None)
        self.assertIn("Incomplete Android signing secrets", self.stderr.getvalue())
        self.assertNotIn("never-print-this-secret", self.stderr.getvalue())
        self.assertFalse(Path(android.STATE_DIRECTORY).exists())

    def test_complete_credentials_owned_files_cleanup_without_secrets(self):
        self.credentials()
        original_write = android.private_write
        calls = []
        def tracked_write(path, contents):
            calls.append(Path(path).name)
            if Path(path).name in android.SIGNING_FILES:
                state = json.loads((Path(android.STATE_DIRECTORY) / "state.json").read_text())
                self.assertEqual(state, android.STATE_CONTENT)
            original_write(path, contents)
        with mock.patch.object(android, "private_write", side_effect=tracked_write):
            android.prepare()
        self.assertEqual(calls[0], "state.json")
        self.assertEqual(Path("matrix-messenger.jks").read_bytes(), b"mock-keystore")
        self.assertIn("keystore.alias=test-alias", Path("local.properties").read_text())
        for name in android.SIGNING_FILES:
            self.assertTrue(os.path.samefile(Path(name), Path(android.STATE_DIRECTORY) / name))
        for name in android.SECRET_NAMES:
            os.environ.pop(name)
        android.cleanup()
        android.cleanup()
        self.assertFalse(Path(android.STATE_DIRECTORY).exists())
        self.assertTrue(all(not Path(name).exists() for name in android.SIGNING_FILES))

    def test_preexisting_files_never_overwritten_or_deleted_with_secrets(self):
        self.credentials()
        for name in android.SIGNING_FILES:
            with self.subTest(name=name):
                Path(name).write_bytes(b"owned-by-user")
                with self.assertRaisesRegex(android.ReleaseError, "Refusing to overwrite"):
                    android.prepare()
                android.cleanup()
                self.assertEqual(Path(name).read_bytes(), b"owned-by-user")
                self.assertFalse(Path(android.STATE_DIRECTORY).exists())
                Path(name).unlink()

    def test_preexisting_dangling_symlink_not_deleted(self):
        self.credentials()
        try:
            Path("matrix-messenger.jks").symlink_to("missing-user-keystore")
        except OSError:
            self.skipTest("Creating symlinks is not available")
        with self.assertRaisesRegex(android.ReleaseError, "Refusing to overwrite"):
            android.prepare()
        android.cleanup()
        self.assertTrue(Path("matrix-messenger.jks").is_symlink())

    def test_file_created_after_preflight_is_not_overwritten_or_deleted(self):
        self.credentials()
        real_link = android.os.link
        def racing_link(source, destination):
            if str(destination) == "local.properties":
                Path(destination).write_bytes(b"created-by-other-process")
            real_link(source, destination)
        with mock.patch.object(android.os, "link", side_effect=racing_link):
            with self.assertRaisesRegex(android.ReleaseError, "Refusing to overwrite"):
                android.prepare()
        android.cleanup()
        self.assertEqual(Path("local.properties").read_bytes(), b"created-by-other-process")
        self.assertFalse(Path("matrix-messenger.jks").exists())
        self.assertFalse(Path(android.STATE_DIRECTORY).exists())

    def test_exclusive_state_directory_collision_not_cleaned(self):
        self.credentials()
        real_mkdir = Path.mkdir
        def racing_mkdir(path, *args, **kwargs):
            if str(path) == android.STATE_DIRECTORY:
                real_mkdir(path)
                (path / "someone-elses-state").write_bytes(b"preserve")
            return real_mkdir(path, *args, **kwargs)
        with mock.patch.object(Path, "mkdir", racing_mkdir):
            with self.assertRaisesRegex(android.ReleaseError, "ownership state"):
                android.prepare()
        self.assertEqual((Path(android.STATE_DIRECTORY) / "someone-elses-state").read_bytes(), b"preserve")

    def test_prepare_io_failure_removes_only_owned_files(self):
        self.credentials()
        real_write = android.private_write
        def broken_write(path, contents):
            real_write(path, contents)
            if Path(path).name == "local.properties":
                raise OSError("private-store-password")
        with mock.patch.object(android, "private_write", side_effect=broken_write):
            self.assertEqual(android.main(["prepare"]), 1)
        self.assertNotIn("private-store-password", self.stderr.getvalue())
        self.assertIn("credential diagnostics suppressed", self.stderr.getvalue())
        self.assertTrue(all(not Path(name).exists() for name in android.SIGNING_FILES))
        self.assertFalse(Path(android.STATE_DIRECTORY).exists())

    def test_cleanup_preserves_replaced_destination(self):
        self.credentials()
        android.prepare()
        Path("local.properties").unlink()
        Path("local.properties").write_bytes(b"replacement")
        android.cleanup()
        self.assertEqual(Path("local.properties").read_bytes(), b"replacement")
        self.assertFalse(Path("matrix-messenger.jks").exists())

    def test_invalid_marker_cleanup_fails_closed(self):
        self.credentials()
        directory = Path(android.STATE_DIRECTORY)
        directory.mkdir()
        (directory / "state.json").write_text('{"files":["../../user-file"]}')
        for name in android.SIGNING_FILES:
            Path(name).write_bytes(b"user-files")
        with self.assertRaisesRegex(android.ReleaseError, "ownership marker"):
            android.cleanup()
        for name in android.SIGNING_FILES:
            self.assertEqual(Path(name).read_bytes(), b"user-files")

    def test_invalid_base64_and_missing_sdk_leave_no_signing_files(self):
        self.credentials()
        os.environ["KEYSTORE_BASE64"] = "private-bad-base64!"
        self.assertEqual(android.main(["prepare"]), 1)
        self.assertIn("not valid base64", self.stderr.getvalue())
        self.assertNotIn("private-bad-base64!", self.stderr.getvalue())
        self.credentials()
        os.environ.pop("ANDROID_SDK_ROOT")
        with self.assertRaisesRegex(android.ReleaseError, "ANDROID_SDK_ROOT or ANDROID_HOME"):
            android.prepare()
        self.assertFalse(Path(android.STATE_DIRECTORY).exists())

    def test_properties_escaping(self):
        self.assertEqual(android.escape_property(" a:b=c\\d\n\r\t\f"), r"\ a\:b\=c\\d\n\r\t\f")
        self.assertEqual(android.escape_property("é😀"), r"\u00e9\ud83d\ude00")

    def test_sdk_numeric_versions_fallback_and_missing_tool(self):
        old = self.tools("9.0.0")
        new = self.tools("35.0.0")
        self.tools("99.0.0-rc1")
        self.assertEqual(Path(android.sdk_tool("aapt")).parent, new)
        shutil_names = list(new.iterdir())
        for path in shutil_names:
            if path.stem == "apksigner":
                path.unlink()
        self.assertEqual(Path(android.sdk_tool("apksigner")).parent, old)
        os.environ["ANDROID_HOME"] = os.environ.pop("ANDROID_SDK_ROOT")
        self.assertEqual(Path(android.sdk_tool("aapt")).parent, new)
        for directory in (old, new):
            for path in directory.iterdir():
                path.unlink()
        with self.assertRaisesRegex(android.ReleaseError, "tool not found.*apksigner"):
            android.sdk_tool("apksigner")
        with self.assertRaisesRegex(android.ReleaseError, "Unsupported"):
            android.sdk_tool("../evil")

    def test_signed_and_debug_apk_verified_and_named_exactly(self):
        for signed in (False, True):
            with self.subTest(signed=signed):
                if signed:
                    self.credentials()
                apk = self.apk(signed)
                with mock.patch.object(android, "run", side_effect=[self.badging(debug=not signed), "Verified"]) as tools:
                    android.verify("1.39.0", "13900", "v1.39.0")
                self.assertEqual(tools.call_count, 2)
                self.assertEqual(tools.call_args_list[0].args[1:], ("dump", "badging", str(apk)))
                self.assertEqual(tools.call_args_list[1].args[1:], ("verify", "--verbose", str(apk)))
                suffix = "" if signed else "-debug"
                self.assertEqual(Path(f"release-assets/MatrixTalk-v1.39.0{suffix}.apk").read_bytes(), b"mock-apk")

    def test_metadata_mismatches_block_signature_and_upload(self):
        self.apk()
        for changes, field in (({"version": "1.38.0"}, "versionName"),
                               ({"version": "1.39.0-extra"}, "versionName"),
                               ({"code": "139000"}, "versionCode"),
                               ({"package": "com.matrix.messenger.other"}, "name")):
            with self.subTest(changes=changes), mock.patch.object(android, "run", return_value=self.badging(debug=True, **changes)) as tool:
                with self.assertRaisesRegex(android.ReleaseError, "embedded " + field):
                    android.verify("1.39.0", "13900", "v1.39.0")
                self.assertEqual(tool.call_count, 1)
        self.assertFalse(Path("release-assets").exists())

    def test_debug_flag_must_match_exact_variant(self):
        for signed in (False, True):
            if signed:
                self.credentials()
            self.apk(signed)
            for badging in (self.badging(debug=signed), self.badging(debug=False) + "application-debuggable-pretend\n"):
                if signed and "application-debuggable-pretend" in badging:
                    continue
                with self.subTest(signed=signed), mock.patch.object(android, "run", return_value=badging) as tool:
                    with self.assertRaisesRegex(android.ReleaseError, "Unexpected APK debug flag"):
                        android.verify("1.39.0", "13900", "v1.39.0")
                    self.assertEqual(tool.call_count, 1)
        self.assertFalse(Path("release-assets").exists())

    def test_apksigner_debug_checks_accept_unrelated_similar_marker(self):
        self.credentials()
        self.apk(signed=True)
        with mock.patch.object(android, "run", side_effect=[
                self.badging(debug=False) + "application-debuggable-pretend\n", "Verified"]):
            android.verify("1.39.0", "13900", "v1.39.0")
        self.assertTrue(Path("release-assets/MatrixTalk-v1.39.0.apk").exists())

    def test_aapt_native_failure_is_readable_and_suppresses_output(self):
        self.apk()
        result = subprocess.CompletedProcess([], 1, "private-output", "private-password")
        with mock.patch.object(android.subprocess, "run", return_value=result):
            self.assertEqual(android.main(["verify", "1.39.0", "13900", "v1.39.0"]), 1)
        self.assertIn("APK metadata inspection (aapt) failed", self.stderr.getvalue())
        self.assertNotIn("private-output", self.stderr.getvalue())
        self.assertNotIn("private-password", self.stderr.getvalue())
        self.assertFalse(Path("release-assets").exists())

    def test_missing_apksigner_blocks_metadata_tools_and_upload(self):
        self.apk()
        for path in (self.sdk / "build-tools").rglob("*"):
            if path.is_file() and path.stem == "apksigner":
                path.unlink()
        with mock.patch.object(android, "run") as tools:
            with self.assertRaisesRegex(android.ReleaseError, "tool not found.*apksigner"):
                android.verify("1.39.0", "13900", "v1.39.0")
        tools.assert_not_called()
        self.assertFalse(Path("release-assets").exists())

    def test_artifact_copy_failure_removes_only_partial_output(self):
        self.apk()
        assets = Path("release-assets")
        assets.mkdir()
        (assets / "keep-other-artifact").write_bytes(b"keep")
        with mock.patch.object(android, "run", side_effect=[self.badging(debug=True), "Verified"]), \
                mock.patch.object(android.shutil, "copyfileobj", side_effect=OSError("private-error")):
            self.assertEqual(android.main(["verify", "1.39.0", "13900", "v1.39.0"]), 1)
        self.assertFalse((assets / "MatrixTalk-v1.39.0-debug.apk").exists())
        self.assertEqual((assets / "keep-other-artifact").read_bytes(), b"keep")
        self.assertNotIn("private-error", self.stderr.getvalue())

    def test_signature_failure_has_readable_error_no_secret_or_artifact(self):
        self.apk()
        def native(args, **kwargs):
            if "badging" in args:
                return subprocess.CompletedProcess(args, 0, self.badging(debug=True), "")
            return subprocess.CompletedProcess(args, 1, "private-key-password", "private-store-password")
        with mock.patch.object(android.subprocess, "run", side_effect=native):
            self.assertEqual(android.main(["verify", "1.39.0", "13900", "v1.39.0"]), 1)
        self.assertIn("APK signature verification failed", self.stderr.getvalue())
        self.assertNotIn("private-key-password", self.stderr.getvalue())
        self.assertNotIn("private-store-password", self.stderr.getvalue())
        self.assertFalse(Path("release-assets").exists())

    def test_native_failures_are_bounded_and_sanitized(self):
        for error in (OSError("secret-native-error"), subprocess.TimeoutExpired("secret-native-command", 120)):
            with self.subTest(error=type(error).__name__), mock.patch.object(android.subprocess, "run", side_effect=error):
                with self.assertRaisesRegex(android.ReleaseError, "metadata inspection.*could not complete"):
                    android.run("aapt", "dump", "badging", "test.apk")
        with mock.patch.object(android.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "ok", "")) as native:
            self.assertEqual(android.run("aapt", "dump"), "ok")
            self.assertEqual(native.call_args.kwargs["timeout"], 120)

    def test_invalid_tags_codes_missing_apk_and_sdk_tools(self):
        for version, code, tag in (("1.39.0", "13900", "v1.38.0"), ("1.039.0", "13900", "v1.039.0"),
                                   ("1.39.0", "0", "v1.39.0"), ("1.39.0", "01", "v1.39.0"),
                                   ("1.39.0", "2100000001", "v1.39.0")):
            with self.subTest(version=version, code=code), self.assertRaises(android.ReleaseError):
                android.verify(version, code, tag)
        with self.assertRaisesRegex(android.ReleaseError, "debug APK is missing"):
            android.verify("1.39.0", "13900", "v1.39.0")
        self.apk()
        for path in (self.sdk / "build-tools").rglob("*"):
            if path.is_file():
                path.unlink()
        with self.assertRaisesRegex(android.ReleaseError, "tool not found.*aapt"):
            android.verify("1.39.0", "13900", "v1.39.0")

    def test_duplicate_metadata_and_existing_artifact_fail_closed(self):
        self.apk()
        for badging in ("", self.badging(debug=True) * 2,
                        self.badging(debug=True).replace("package:", "package: versionName='wrong'")):
            with self.subTest(badging=badging), mock.patch.object(android, "run", return_value=badging):
                with self.assertRaises(android.ReleaseError):
                    android.verify("1.39.0", "13900", "v1.39.0")
        assets = Path("release-assets")
        assets.mkdir()
        artifact = assets / "MatrixTalk-v1.39.0-debug.apk"
        artifact.write_bytes(b"preexisting-artifact")
        with mock.patch.object(android, "run", side_effect=[self.badging(debug=True), "Verified"]):
            with self.assertRaisesRegex(android.ReleaseError, "overwrite.*artifact"):
                android.verify("1.39.0", "13900", "v1.39.0")
        self.assertEqual(artifact.read_bytes(), b"preexisting-artifact")


if __name__ == "__main__":
    unittest.main()
