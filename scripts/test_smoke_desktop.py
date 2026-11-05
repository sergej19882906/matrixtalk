"""Offline subprocess regression tests for the Bash desktop smoke helper."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().with_name("smoke-desktop.sh")
BASH = shutil.which("bash")
if os.name == "nt":
    # Prefer Git Bash over the optional Windows WSL bash launcher.
    for root in (os.environ.get("ProgramFiles", "C:/Program Files"), os.environ.get("LOCALAPPDATA", "")):
        candidate = Path(root) / "Git" / "bin" / "bash.exe"
        if candidate.is_file():
            BASH = str(candidate)
            break


@unittest.skipUnless(BASH, "Bash or Git Bash is required")
class DesktopSmokeTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="matrix smoke fixtures ")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.app_root = self.root / "packaged apps with spaces"
        self.env = dict(os.environ, SMOKE_APP_ROOT=self.app_root.as_posix(), SMOKE_SECONDS="0.4")

    def launcher_path(self, platform):
        relative = {"Windows": "MatrixTalk/MatrixTalk.exe", "macOS": "MatrixTalk.app/Contents/MacOS/MatrixTalk",
                    "Linux": "MatrixTalk/bin/MatrixTalk"}[platform]
        return self.app_root / relative

    def launcher(self, platform, body="exec sleep 10"):
        path = self.launcher_path(platform)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("#!/usr/bin/env bash\n" + body + "\n", encoding="utf-8", newline="\n")
        path.chmod(0o755)
        return path

    def invoke(self, platform):
        environment = dict(self.env, RUNNER_OS=platform)
        return subprocess.run([BASH, SCRIPT.as_posix()], cwd=self.root, env=environment,
                              text=True, capture_output=True, timeout=12, check=False)

    def test_windows_launcher_with_spaces_stays_alive(self):
        self.launcher("Windows")
        result = self.invoke("Windows")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Packaged app smoke test OK", result.stdout)

    def test_macos_launcher_with_spaces_and_no_runner_array(self):
        self.launcher("macOS")
        result = self.invoke("macOS")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Packaged app smoke test OK", result.stdout)
        self.assertNotIn("unbound variable", result.stderr)

    def test_missing_launcher_reports_exact_path_and_required_task(self):
        for platform in ("Windows", "macOS", "Linux"):
            with self.subTest(platform=platform):
                result = self.invoke(platform)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Packaged launcher not found:", result.stderr)
                self.assertIn("packaged apps with spaces", result.stderr)
                self.assertIn(self.launcher_path(platform).name, result.stderr)
                self.assertIn(":desktop-app:createDistributable", result.stderr)

    def test_early_exit_even_success_exit_code_fails(self):
        for platform in ("Windows", "macOS"):
            with self.subTest(platform=platform):
                self.launcher(platform, "printf 'fixture exited normally\\n'\nexit 0")
                result = self.invoke(platform)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Packaged app exited early", result.stderr)
                self.assertIn("fixture exited normally", result.stderr)

    def test_logged_startup_exceptions_fail_while_launcher_alive(self):
        for platform, message in (("Windows", "Exception in thread fixture"),
                                  ("macOS", "NoClassDefFoundError fixture")):
            with self.subTest(platform=platform):
                self.launcher(platform, "printf '%s\\n' '{}'\nexec sleep 10".format(message))
                result = self.invoke(platform)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(message, result.stderr)
                self.assertNotIn("exited early", result.stderr)
                self.assertNotIn("smoke test OK", result.stdout)

    @unittest.skipIf(os.name == "nt", "Linux PATH shim exercised on Unix Bash")
    def test_linux_uses_fake_xvfb_runner_without_application_or_display(self):
        self.launcher("Linux")
        shim_dir = self.root / "fake runner tools"
        shim_dir.mkdir()
        shim = shim_dir / "xvfb-run"
        shim.write_text('#!/usr/bin/env bash\n[ "$1" = -a ] || exit 77\nshift\nexec "$@"\n',
                        encoding="utf-8", newline="\n")
        shim.chmod(0o755)
        self.env["PATH"] = str(shim_dir) + os.pathsep + self.env["PATH"]
        result = self.invoke("Linux")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Packaged app smoke test OK", result.stdout)


if __name__ == "__main__":
    unittest.main()
