"""Offline macOS release gates; native tools are mocked on every platform."""
import base64
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("mac_release", Path(__file__).with_name("mac-release.py"))
mac = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mac)


class MacReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="matrix-mac-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.env_file = self.root / "github-env"
        self.environment = mock.patch.dict(os.environ, {"RUNNER_TEMP": str(self.root), "GITHUB_ENV": str(self.env_file)}, clear=True)
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.platform = mock.patch.object(mac.sys, "platform", "darwin")
        self.platform.start()
        self.addCleanup(self.platform.stop)
        self.stdout = io.StringIO()
        self.stderr = io.StringIO()
        for redirect in (contextlib.redirect_stdout(self.stdout), contextlib.redirect_stderr(self.stderr)):
            redirect.__enter__()
            self.addCleanup(redirect.__exit__, None, None, None)
        self.native = mock.patch.object(mac.subprocess, "run", side_effect=AssertionError("Native signing/network tools are forbidden"))
        self.native.start()
        self.addCleanup(self.native.stop)
        self.calls = []
        self.search_list = ["/Users/runner/Library/Keychains/login.keychain-db", "/private/path with spaces/extra.keychain-db"]
        self.identity = "Developer ID Application: Test Company (TEAM123456)"
        self.identities = [self.identity]
        self.fail_operation = None
        self.info = {"CFBundleShortVersionString": "1.39.0", "CFBundleVersion": "1.39.0", "CFBundleExecutable": "MatrixTalk"}
        self.app_count = 1
        self.create_binary = True
        self.architectures = b"x86_64\n"
        self.signature_details = ("Authority=" + self.identity + "\nTeamIdentifier=TEAM123456\n"
                                  "CodeDirectory v=20500 size=123 flags=0x10000(runtime) hashes=1\n").encode()
        self.notary_json = b'{"status":"Accepted"}'
        self.detach_failure = False
        self.mounted_after_failure = False
        self.mountpoint = None

    def credentials(self):
        os.environ.update({name: "secret-" + name for name in mac.MAC_SECRETS})
        os.environ["MAC_CERTIFICATE_BASE64"] = base64.b64encode(b"mock-p12").decode()
        os.environ["APPLE_TEAM_ID"] = "TEAM123456"

    def published_environment(self):
        return dict(line.split("=", 1) for line in self.env_file.read_text().splitlines())

    def fake_tool(self, args, **kwargs):
        self.calls.append(list(args))
        operation = tuple(args[:2])
        if operation == self.fail_operation:
            raise mac.ReleaseError(Path(args[0]).name + " failed")
        out = b""
        err = b""
        code = 0
        if args == ["security", "list-keychains", "-d", "user"]:
            out = "\n".join('    "' + path + '"' for path in self.search_list).encode()
        elif operation == ("security", "create-keychain"):
            Path(args[-1]).touch()
        elif operation == ("security", "delete-keychain"):
            Path(args[-1]).unlink()
        elif operation == ("security", "find-identity"):
            out = "\n".join(f'  {index}) ' + "A" * 40 + f' "{identity}"' for index, identity in enumerate(self.identities, 1)).encode()
        elif operation == ("hdiutil", "attach"):
            self.mountpoint = Path(args[args.index("-mountpoint") + 1])
            for index in range(self.app_count):
                app = self.mountpoint / f"MatrixTalk{index}.app"
                (app / "Contents" / "MacOS").mkdir(parents=True)
                (app / "Contents" / "Info.plist").write_bytes(plistlib.dumps(self.info))
                if self.create_binary:
                    (app / "Contents" / "MacOS" / "MatrixTalk").touch()
        elif operation == ("hdiutil", "detach"):
            code = int(self.detach_failure)
        elif operation == ("hdiutil", "info"):
            entities = [{"mount-point": str(self.mountpoint)}] if self.mounted_after_failure else []
            out = plistlib.dumps({"images": [{"system-entities": entities}]})
        elif args[0] == "lipo":
            out = self.architectures
        elif operation == ("codesign", "--display"):
            err = self.signature_details
        elif args[:3] == ["xcrun", "notarytool", "submit"]:
            out = self.notary_json
        return subprocess.CompletedProcess(args, code, out, err)

    def artifact(self, signed=True):
        package = self.root / "release with spaces.dmg"
        package.touch()
        os.environ["MATRIX_MAC_SIGN"] = "true" if signed else "false"
        if signed:
            self.credentials()
        return package

    def assert_no_notary_or_staple(self):
        self.assertFalse(any("notarytool" in call or "stapler" in call for call in self.calls))

    def test_zero_secrets_explicit_unsigned_prepare(self):
        with mock.patch.object(mac, "run_tool") as tools:
            mac.prepare()
        tools.assert_not_called()
        self.assertEqual(self.published_environment(), {"MATRIX_MAC_SIGN": "false", "MATRIX_MAC_IDENTITY": "",
                                                       "MATRIX_MAC_KEYCHAIN": "", "MATRIX_MAC_STATE": ""})
        self.assertIn("explicitly unsigned", self.stdout.getvalue())

    def test_partial_secret_sets_rejected_without_values(self):
        for name in mac.MAC_SECRETS:
            with self.subTest(name=name), mock.patch.dict(os.environ, {name: "never-show-secret"}):
                with self.assertRaisesRegex(mac.ReleaseError, "Incomplete.*missing") as failure:
                    mac.prepare()
                self.assertNotIn("never-show-secret", str(failure.exception))
        for missing in mac.MAC_SECRETS:
            with self.subTest(missing=missing):
                self.credentials()
                os.environ.pop(missing)
                with self.assertRaisesRegex(mac.ReleaseError, "Incomplete.*missing"):
                    mac.prepare()
                for name in mac.MAC_SECRETS:
                    os.environ.pop(name, None)
        self.assertFalse(self.env_file.exists())

    def test_prepare_private_random_keychain_full_identity_and_restore(self):
        self.credentials()
        original_tool = self.fake_tool
        def tracked_tool(args, **kwargs):
            if args[:2] == ["security", "create-keychain"]:
                values = self.published_environment()
                state = json.loads(Path(values["MATRIX_MAC_STATE"]).read_text())
                self.assertEqual(state["search_list"], self.search_list)
                self.assertTrue((Path(values["MATRIX_MAC_STATE"]).parent / "certificate.p12").is_file())
            return original_tool(args, **kwargs)
        with mock.patch.object(mac, "run_tool", side_effect=tracked_tool):
            mac.prepare()
            values = self.published_environment()
            self.assertEqual(values["MATRIX_MAC_SIGN"], "true")
            self.assertEqual(values["MATRIX_MAC_IDENTITY"], self.identity)
            self.assertTrue(Path(values["MATRIX_MAC_KEYCHAIN"]).is_file())
            self.assertFalse(list(self.root.rglob("*.p12")))
            created = next(call for call in self.calls if call[:2] == ["security", "create-keychain"])
            unlocked = next(call for call in self.calls if call[:2] == ["security", "unlock-keychain"])
            self.assertGreaterEqual(len(created[3]), 48)
            self.assertNotIn(created[3], os.environ.values())
            self.assertEqual(created[3], unlocked[3])
            self.assertEqual(unlocked[-1], values["MATRIX_MAC_KEYCHAIN"])
            mac.cleanup_state(values["MATRIX_MAC_STATE"])
            mac.cleanup_state(values["MATRIX_MAC_STATE"])
        self.assertEqual(self.calls[-2], ["security", "list-keychains", "-d", "user", "-s", *self.search_list])
        self.assertFalse(Path(values["MATRIX_MAC_STATE"]).parent.exists())
        self.assertFalse(any("default-keychain" in call or "lock-keychain" in call for call in self.calls))
        for name in ("MAC_CERTIFICATE_PASSWORD", "APPLE_APP_SPECIFIC_PASSWORD"):
            self.assertNotIn(os.environ[name], self.stdout.getvalue() + self.stderr.getvalue())

    def test_import_failure_cleans_certificate_keychain_and_restores_search_list(self):
        self.credentials()
        self.fail_operation = ("security", "import")
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            with self.assertRaisesRegex(mac.ReleaseError, "security failed"):
                mac.prepare()
        self.assertFalse(list(self.root.glob("matrix-mac-sign-*")))
        self.assertIn(["security", "list-keychains", "-d", "user", "-s", *self.search_list], self.calls)

    def test_identity_requires_exactly_one_valid_identity_for_team(self):
        self.credentials()
        for identities in ([], ["Developer ID Application: Other Team (OTHER12345)"], [self.identity, self.identity]):
            with self.subTest(identities=identities):
                self.identities = identities
                with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
                    with self.assertRaisesRegex(mac.ReleaseError, "exactly one valid Developer ID"):
                        mac.prepare()
                self.assertFalse(list(self.root.glob("matrix-mac-sign-*")))

    def test_invalid_certificate_existing_session_and_missing_github_env(self):
        self.credentials()
        os.environ["MAC_CERTIFICATE_BASE64"] = "invalid-private-value!"
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            with self.assertRaisesRegex(mac.ReleaseError, "not valid base64"):
                mac.prepare()
        self.assertFalse(list(self.root.glob("matrix-mac-sign-*")))
        os.environ["MATRIX_MAC_STATE"] = "previous-session"
        with self.assertRaisesRegex(mac.ReleaseError, "must be cleaned up"):
            mac.prepare()
        os.environ.pop("MATRIX_MAC_STATE")
        os.environ.pop("GITHUB_ENV")
        with self.assertRaisesRegex(mac.ReleaseError, "GITHUB_ENV is required"):
            mac.prepare()

    def test_cleanup_deletes_p12_even_if_restore_fails_and_retains_retry_state(self):
        directory = self.root / "matrix-mac-sign-cleanup"
        directory.mkdir()
        keychain = directory / "signing.keychain-db"
        keychain.touch()
        p12 = directory / "certificate.p12"
        p12.write_bytes(b"fixture")
        state_path = directory / "state.json"
        state_path.write_text(json.dumps({"keychain": str(keychain), "search_list": self.search_list}))
        self.fail_operation = ("security", "list-keychains")
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            with self.assertRaises(mac.ReleaseError):
                mac.cleanup_state(str(state_path))
        self.assertFalse(p12.exists())
        self.assertTrue(state_path.exists())
        self.assertTrue(keychain.exists())

    def test_invalid_cleanup_state_does_not_execute_tools(self):
        state_path = self.root / "state.json"
        state_path.write_text(json.dumps({"keychain": "/unrelated.keychain-db", "search_list": []}))
        with mock.patch.object(mac, "run_tool") as tool:
            with self.assertRaisesRegex(mac.ReleaseError, "Invalid.*cleanup state"):
                mac.cleanup_state(str(state_path))
        tool.assert_not_called()

    def test_signed_embedded_dmg_metadata_architecture_codesign_and_accepted_notary(self):
        package = self.artifact()
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            mac.verify("1.39.0", str(package))
        attach = next(call for call in self.calls if call[:2] == ["hdiutil", "attach"])
        for option in ("-readonly", "-nobrowse", "-noautoopen"):
            self.assertIn(option, attach)
        self.assertEqual(attach[-1], str(package))
        self.assertTrue(any(call[:5] == ["codesign", "--verify", "--deep", "--strict", str(self.mountpoint / "MatrixTalk0.app")] for call in self.calls))
        self.assertIn(["xcrun", "stapler", "staple", str(package)], self.calls)
        self.assertIn(["xcrun", "stapler", "validate", str(package)], self.calls)
        self.assertLess(next(i for i, call in enumerate(self.calls) if call[:2] == ["hdiutil", "detach"]),
                        next(i for i, call in enumerate(self.calls) if "notarytool" in call))
        self.assertFalse(list(self.root.glob("matrix-mac-verify-*")))
        self.assertNotIn(os.environ["APPLE_APP_SPECIFIC_PASSWORD"], self.stdout.getvalue() + self.stderr.getvalue())

    def test_unsigned_checks_real_embedded_metadata_without_codesign_or_notary(self):
        package = self.artifact(signed=False)
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            mac.verify("1.39.0", str(package))
        self.assert_no_notary_or_staple()
        self.assertFalse(any(call[0] == "codesign" for call in self.calls))
        self.assertIn("explicit unsigned release", self.stdout.getvalue())

    def test_both_embedded_versions_must_match_not_detached_app_metadata(self):
        package = self.artifact()
        for key in ("CFBundleVersion", "CFBundleShortVersionString"):
            with self.subTest(key=key):
                self.info[key] = "1.38.0"
                self.calls.clear()
                with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
                    with self.assertRaisesRegex(mac.ReleaseError, key):
                        mac.verify("1.39.0", str(package))
                self.assert_no_notary_or_staple()
                self.assertTrue(any(call[:2] == ["hdiutil", "detach"] for call in self.calls))
                self.assertFalse(list(self.root.glob("matrix-mac-verify-*")))
                self.info[key] = "1.39.0"

    def test_exact_intel_architecture_required(self):
        package = self.artifact()
        for architectures in (b"arm64\n", b"x86_64 arm64\n", b"i386\n", b""):
            with self.subTest(architectures=architectures):
                self.architectures = architectures
                self.calls.clear()
                with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
                    with self.assertRaisesRegex(mac.ReleaseError, "x86_64 only"):
                        mac.verify("1.39.0", str(package))
                self.assert_no_notary_or_staple()

    def test_app_count_executable_name_and_binary_presence_gates(self):
        package = self.artifact()
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            for count in (0, 2):
                self.app_count = count
                with self.subTest(count=count), self.assertRaisesRegex(mac.ReleaseError, "exactly one app"):
                    mac.verify("1.39.0", str(package))
            self.app_count = 1
            for executable in ("", "../launcher", "folder\\launcher", ".", ".."):
                self.info["CFBundleExecutable"] = executable
                with self.subTest(executable=executable), self.assertRaisesRegex(mac.ReleaseError, "Invalid embedded CFBundleExecutable"):
                    mac.verify("1.39.0", str(package))
            self.info["CFBundleExecutable"] = "MatrixTalk"
            self.create_binary = False
            with self.assertRaisesRegex(mac.ReleaseError, "launcher does not exist"):
                mac.verify("1.39.0", str(package))
        self.assert_no_notary_or_staple()

    def test_signature_failure_wrong_authority_team_or_missing_runtime_blocks_notary(self):
        package = self.artifact()
        self.fail_operation = ("codesign", "--verify")
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            with self.assertRaisesRegex(mac.ReleaseError, "codesign failed"):
                mac.verify("1.39.0", str(package))
        self.assert_no_notary_or_staple()
        self.fail_operation = None
        good = self.signature_details
        for bad in (good.replace(b"Developer ID Application:", b"Apple Development:"),
                    good.replace(b"TeamIdentifier=TEAM123456", b"TeamIdentifier=OTHER12345"),
                    good.replace(b"(runtime)", b"(none)")):
            self.signature_details = bad
            self.calls.clear()
            with self.subTest(details=bad), mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
                with self.assertRaisesRegex(mac.ReleaseError, "Developer ID signing"):
                    mac.verify("1.39.0", str(package))
            self.assert_no_notary_or_staple()

    def test_notary_json_must_be_accepted_before_stapling(self):
        package = self.artifact()
        for response in (b'{"status":"Invalid"}', b'{"status":"In Progress"}', b'{}', b'[]', b'not-json-secret'):
            self.notary_json = response
            self.calls.clear()
            with self.subTest(response=response), mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
                with self.assertRaises(mac.ReleaseError) as failure:
                    mac.verify("1.39.0", str(package))
                self.assertNotIn("not-json-secret", str(failure.exception))
            self.assertFalse(any("stapler" in call for call in self.calls))

    def test_notary_and_stapler_native_failures_propagate(self):
        package = self.artifact()
        for operation in (("xcrun", "notarytool"), ("xcrun", "stapler")):
            self.fail_operation = operation
            with self.subTest(operation=operation), mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
                with self.assertRaisesRegex(mac.ReleaseError, "xcrun failed"):
                    mac.verify("1.39.0", str(package))

    def test_stapler_validate_failure_fails_after_successful_staple(self):
        package = self.artifact()
        def validate_failure(args, **kwargs):
            if args[:3] == ["xcrun", "stapler", "validate"]:
                self.calls.append(list(args))
                raise mac.ReleaseError("xcrun failed")
            return self.fake_tool(args, **kwargs)
        with mock.patch.object(mac, "run_tool", side_effect=validate_failure):
            with self.assertRaisesRegex(mac.ReleaseError, "xcrun failed"):
                mac.verify("1.39.0", str(package))
        self.assertIn(["xcrun", "stapler", "staple", str(package)], self.calls)
        self.assertIn(["xcrun", "stapler", "validate", str(package)], self.calls)

    def test_attach_failure_does_not_delete_a_still_mounted_volume(self):
        package = self.artifact()
        self.fail_operation = ("hdiutil", "attach")
        self.detach_failure = True
        self.mounted_after_failure = True
        def partial_attach(args, **kwargs):
            if args[:2] == ["hdiutil", "attach"]:
                self.mountpoint = Path(args[args.index("-mountpoint") + 1])
            return self.fake_tool(args, **kwargs)
        with mock.patch.object(mac, "run_tool", side_effect=partial_attach):
            with self.assertRaisesRegex(mac.ReleaseError, "temporary mount retained"):
                mac.verify("1.39.0", str(package))
        self.assertTrue(self.mountpoint.exists())
        self.assert_no_notary_or_staple()

    def test_detach_failure_retains_mount_and_blocks_notary(self):
        package = self.artifact()
        self.detach_failure = True
        self.mounted_after_failure = True
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            with self.assertRaisesRegex(mac.ReleaseError, "temporary mount retained"):
                mac.verify("1.39.0", str(package))
        self.assertTrue(self.mountpoint.exists())
        self.assert_no_notary_or_staple()

    def test_detach_failure_without_mount_can_remove_temporary_directory(self):
        package = self.artifact(signed=False)
        self.detach_failure = True
        with mock.patch.object(mac, "run_tool", side_effect=self.fake_tool):
            mac.verify("1.39.0", str(package))
        self.assertFalse(self.mountpoint.exists())

    def test_mode_version_platform_and_package_path_gates(self):
        package = self.artifact(signed=False)
        with mock.patch.object(mac, "run_tool") as tool:
            for version in ("v1.39.0", "1.39", "1.039.0", "1.39.0-beta", "1.39.0\n"):
                with self.subTest(version=version), self.assertRaisesRegex(mac.ReleaseError, "Expected version"):
                    mac.verify(version, str(package))
            for mode in (None, "TRUE", "true"):
                if mode is None:
                    os.environ.pop("MATRIX_MAC_SIGN", None)
                else:
                    os.environ["MATRIX_MAC_SIGN"] = mode
                with self.subTest(mode=mode), self.assertRaisesRegex(mac.ReleaseError, "must match"):
                    mac.verify("1.39.0", str(package))
            os.environ["MATRIX_MAC_SIGN"] = "false"
            with self.assertRaisesRegex(mac.ReleaseError, "one existing DMG"):
                mac.verify("1.39.0", str(self.root / "missing.dmg"))
            with mock.patch.object(mac.sys, "platform", "win32"):
                with self.assertRaisesRegex(mac.ReleaseError, "requires macOS"):
                    mac.verify("1.39.0", str(package))
        tool.assert_not_called()

    def test_tool_failures_suppress_credentials_and_bound_time(self):
        for error in (OSError("private-password"), subprocess.TimeoutExpired("private-password", 120)):
            with self.subTest(error=type(error).__name__), mock.patch.object(mac.subprocess, "run", side_effect=error):
                with self.assertRaisesRegex(mac.ReleaseError, "could not complete") as failure:
                    mac.run_tool(["security", "private-password"])
                self.assertNotIn("private-password", str(failure.exception))
        result = subprocess.CompletedProcess([], 1, b"private-password", b"private-password")
        with mock.patch.object(mac.subprocess, "run", return_value=result) as native:
            with self.assertRaisesRegex(mac.ReleaseError, "security failed"):
                mac.run_tool(["security", "private-password"])
            self.assertEqual(native.call_args.kwargs["timeout"], 120)
            self.assertEqual(mac.run_tool(["security"], check=False).returncode, 1)


if __name__ == "__main__":
    unittest.main()
