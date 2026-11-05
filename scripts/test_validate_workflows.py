"""Small positive/negative workflow tests; PyYAML is optional for discovery."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("validate_workflows", Path(__file__).resolve().with_name("validate_workflows.py"))
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


def release_fixture():
    return {
        "on": {"push": {"tags": ["v*"]}},
        "jobs": {
            "metadata": {"steps": [{"run": 'python -I scripts/release_metadata.py outputs --root . --tag "$RELEASE_TAG"'}]},
            "android-apk": {"needs": "metadata", "steps": [
                {"run": './gradlew verifyReleaseMetadata -PreleaseTag="$RELEASE_TAG" :app:assembleRelease'},
                {"uses": "actions/upload-artifact@v4", "with": {"path": "release-assets/*.apk", "if-no-files-found": "error"}},
            ]},
            "desktop": {"needs": "metadata", "runs-on": "${{ matrix.os }}", "strategy": {"matrix": {"include": [
                {"os": "windows-latest", "task": "packageMsi", "format": "msi"},
                {"os": "ubuntu-latest", "task": "packageDeb", "format": "deb"},
                {"os": "macos-15-intel", "task": "packageDmg", "format": "dmg"},
            ]}}, "steps": [
                {"name": "Prepare macOS signing", "run": "python -I scripts/mac-release.py prepare"},
                {"run": './gradlew verifyReleaseMetadata -PreleaseTag="$RELEASE_TAG" :desktop-app:createDistributable ":desktop-app:$PACKAGE_TASK"'},
                {"name": "Smoke test packaged app", "run": "bash scripts/smoke-desktop.sh"},
                {"name": "Prepare artifact", "run": "cp desktop-app/build/compose/binaries/main/msi/*.msi release-assets/"},
                {"uses": "actions/upload-artifact@v4", "with": {"path": "release-assets/*", "if-no-files-found": "error"}},
            ]},
            "publish": {"needs": ["metadata", "android-apk", "desktop"], "steps": [
                {"run": 'python -I scripts/release_metadata.py artifacts --tag "$RELEASE_TAG" --directory artifacts'},
                {"run": 'python -I scripts/release_metadata.py notes --tag "$RELEASE_TAG" --output notes.md'},
                {"run": 'python -I scripts/publish-release.py "$RELEASE_TAG" artifacts notes.md'},
            ]},
        },
    }


@unittest.skipIf(validator.yaml is None, "PyYAML not installed; standalone validator still requires it")
class WorkflowTests(unittest.TestCase):
    def test_valid_structure_and_github_on_key(self):
        text = "on:\n  push:\n    tags: ['v*']\njobs: {}\n"
        workflow = validator.load_workflow(text)
        self.assertIn("on", workflow)
        self.assertNotIn(True, workflow)
        validator.validate_release(release_fixture())

    def test_duplicate_keys_top_level_and_nested_rejected(self):
        for text in ("jobs: {}\njobs: {}\n", "jobs:\n  desktop:\n    needs: metadata\n    needs: publish\n"):
            with self.subTest(text=text), self.assertRaisesRegex(validator.WorkflowValidationError, "duplicate"):
                validator.load_workflow(text)

    def test_unsafe_tags_merge_keys_and_malformed_yaml_rejected(self):
        for text in ("!!python/object/apply:os.system ['exit']", "jobs: [", "[]", "base: &a {x: 1}\nother: {<<: *a}\n",
                     "? [a, b]\n: value\n"):
            with self.subTest(text=text), self.assertRaises(validator.WorkflowValidationError):
                validator.load_workflow(text)

    def test_exact_jobs_dependencies_and_supported_runner(self):
        for mutation in ("extra-job", "missing-job", "missing-needs", "old-macos", "wrong-runner"):
            workflow = release_fixture()
            jobs = workflow["jobs"]
            if mutation == "extra-job":
                jobs["extra"] = {"steps": [{"run": "true"}]}
            elif mutation == "missing-job":
                del jobs["android-apk"]
            elif mutation == "missing-needs":
                jobs["publish"]["needs"] = ["metadata", "desktop"]
            elif mutation == "old-macos":
                jobs["desktop"]["strategy"]["matrix"]["include"][2]["os"] = "macos-13"
            else:
                jobs["desktop"]["runs-on"] = "ubuntu-latest"
            with self.subTest(mutation=mutation), self.assertRaises(validator.WorkflowValidationError):
                validator.validate_release(workflow)

    def test_missing_or_late_metadata_validation_prevents_package(self):
        for job in ("android-apk", "desktop"):
            for mutation in ("missing", "late", "missing-tag", "ignored"):
                workflow = release_fixture()
                steps = workflow["jobs"][job]["steps"]
                package = next(step for step in steps if "./gradlew" in step.get("run", ""))
                if mutation == "missing":
                    package["run"] = package["run"].replace("verifyReleaseMetadata", "")
                elif mutation == "late":
                    package["run"] = './gradlew :app:assembleRelease verifyReleaseMetadata -PreleaseTag="$RELEASE_TAG"'
                elif mutation == "missing-tag":
                    package["run"] = package["run"].replace('-PreleaseTag="$RELEASE_TAG"', "")
                else:
                    package["continue-on-error"] = "true"
                with self.subTest(job=job, mutation=mutation), self.assertRaises(validator.WorkflowValidationError):
                    validator.validate_release(workflow)

    def test_earlier_unconditional_check_is_valid_guard(self):
        workflow = release_fixture()
        steps = workflow["jobs"]["android-apk"]["steps"]
        steps[0]["run"] = "./gradlew :app:assembleRelease"
        steps.insert(0, {"run": 'python -I scripts/release_metadata.py check --tag "$RELEASE_TAG"'})
        validator.validate_release(workflow)
        steps[0]["continue-on-error"] = "true"
        with self.assertRaises(validator.WorkflowValidationError):
            validator.validate_release(workflow)
        del steps[0]["continue-on-error"]
        steps[0]["if"] = "false"
        with self.assertRaises(validator.WorkflowValidationError):
            validator.validate_release(workflow)

    def test_upload_missing_files_must_error(self):
        for name in ("android-apk", "desktop"):
            for setting in (None, "warn", "ignore"):
                workflow = release_fixture()
                settings = workflow["jobs"][name]["steps"][-1]["with"]
                if setting is None:
                    del settings["if-no-files-found"]
                else:
                    settings["if-no-files-found"] = setting
                with self.subTest(job=name, setting=setting), self.assertRaisesRegex(validator.WorkflowValidationError, "missing files"):
                    validator.validate_release(workflow)

    def test_desktop_prepare_and_upload_reject_android_paths(self):
        for text in ("cp app/build/outputs/apk/release/app-release.apk release-assets/", "./gradlew :app:assembleDebug",
                     "python -I scripts/android-release.py prepare"):
            workflow = release_fixture()
            workflow["jobs"]["desktop"]["steps"][3]["run"] = text
            with self.subTest(text=text), self.assertRaises(validator.WorkflowValidationError):
                validator.validate_release(workflow)
        workflow = release_fixture()
        workflow["jobs"]["desktop"]["steps"][-1]["with"]["path"] = "app/build/outputs/apk/**/*.apk"
        with self.assertRaisesRegex(validator.WorkflowValidationError, "Android"):
            validator.validate_release(workflow)

    def test_desktop_package_must_create_launcher_before_portable_smoke(self):
        for mutation in ("no-distributable", "smoke-before-package", "missing-smoke", "ignored-smoke"):
            workflow = release_fixture()
            steps = workflow["jobs"]["desktop"]["steps"]
            if mutation == "no-distributable":
                steps[1]["run"] = steps[1]["run"].replace(":desktop-app:createDistributable", "")
            elif mutation == "smoke-before-package":
                steps[1], steps[2] = steps[2], steps[1]
            elif mutation == "missing-smoke":
                steps[2]["run"] = "true"
            else:
                steps[2]["continue-on-error"] = "true"
            with self.subTest(mutation=mutation), self.assertRaisesRegex(validator.WorkflowValidationError, "desktop"):
                validator.validate_release(workflow)

    def test_publish_validation_order_and_ignored_failures(self):
        for mutation in ("late", "missing", "ignored", "conditional"):
            workflow = release_fixture()
            steps = workflow["jobs"]["publish"]["steps"]
            if mutation == "late":
                steps.insert(0, steps.pop())
            elif mutation == "missing":
                del steps[0]
            elif mutation == "ignored":
                steps[0]["continue-on-error"] = "true"
            else:
                steps[0]["if"] = "false"
            with self.subTest(mutation=mutation), self.assertRaises(validator.WorkflowValidationError):
                validator.validate_release(workflow)

    def test_directory_validates_all_yaml_and_requires_release(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            release = directory / "release.yml"
            release.write_text(json.dumps(release_fixture()), encoding="utf-8")
            other = directory / "ci.yaml"
            other.write_text("on: push\njobs:\n  build:\n    steps:\n      - run: true\n", encoding="utf-8")
            self.assertEqual(2, validator.validate_directory(directory))
            other.write_text("on: push\njobs: {}\njobs: {}\n", encoding="utf-8")
            with self.assertRaises(validator.WorkflowValidationError):
                validator.validate_directory(directory)
            other.unlink()
            release.unlink()
            with self.assertRaises(validator.WorkflowValidationError):
                validator.validate_directory(directory)

    def test_cli_clean_failure(self):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = validator.main([str(Path(tempfile.gettempdir()) / "nonexistent-matrixtalk-workflows")])
        self.assertEqual(1, result)
        self.assertIn("workflow validation error", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
