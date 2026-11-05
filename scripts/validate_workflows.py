#!/usr/bin/env python3
"""Check workflow YAML safely and assert the release pipeline's critical guards.

Requires PyYAML. Usage: python -I scripts/validate_workflows.py .github/workflows
"""
import argparse
import json
from pathlib import Path
import re
import sys

try:
    import yaml
except ImportError:
    yaml = None


class WorkflowValidationError(ValueError):
    """Workflow syntax or release structure violates a publishing guard."""


if yaml is not None:
    class UniqueBaseLoader(yaml.BaseLoader):
        """Keep GitHub's 'on' key a string, never construct Python objects."""
        def construct_mapping(self, node, deep=False):
            if not isinstance(node, yaml.MappingNode):
                raise yaml.constructor.ConstructorError(None, None, "expected mapping", node.start_mark)
            result = {}
            for key_node, value_node in node.value:
                if not isinstance(key_node, yaml.ScalarNode):
                    raise yaml.constructor.ConstructorError(None, None, "mapping keys must be scalar", key_node.start_mark)
                key = self.construct_object(key_node, deep=deep)
                if key in result or key == "<<":
                    raise yaml.constructor.ConstructorError(None, None, f"duplicate or merged key: {key}", key_node.start_mark)
                result[key] = self.construct_object(value_node, deep=deep)
            return result

        def construct_object(self, node, deep=False):
            if node.tag not in {"tag:yaml.org,2002:" + kind for kind in
                               ("map", "seq", "str", "null", "bool", "int", "float", "timestamp")}:
                raise yaml.constructor.ConstructorError(None, None, "unsupported YAML tag", node.start_mark)
            return super().construct_object(node, deep=deep)


def load_workflow(text, source="workflow"):
    if yaml is None:
        raise WorkflowValidationError("PyYAML is required; install it before running validate_workflows.py")
    try:
        value = yaml.load(text, Loader=UniqueBaseLoader)
    except yaml.YAMLError as error:
        raise WorkflowValidationError(f"{source}: {error}") from error
    if not isinstance(value, dict):
        raise WorkflowValidationError(f"{source}: workflow must be a mapping")
    return value


def require(condition, message):
    if not condition:
        raise WorkflowValidationError(message)


def _needs(job):
    needs = job.get("needs", [])
    if isinstance(needs, str):
        return [needs]
    require(isinstance(needs, list) and all(isinstance(item, str) for item in needs), "needs must be a string or list of strings")
    return needs


def _steps(job, name):
    require(isinstance(job, dict), f"{name}: job must be a mapping")
    steps = job.get("steps")
    require(isinstance(steps, list) and steps, f"{name}: nonempty steps required")
    for step in steps:
        require(isinstance(step, dict), f"{name}: step must be a mapping")
        require(("run" in step) != ("uses" in step), f"{name}: each step needs exactly one of run or uses")
        if "run" in step:
            require(isinstance(step["run"], str), f"{name}: run must be a string")
        if "uses" in step:
            require(isinstance(step["uses"], str), f"{name}: uses must be a string")
    return steps


def _metadata_command(run):
    return re.search(r"\bpython(?:3)?\s+-I\s+scripts/release_metadata\.py\s+(?:check|outputs)\b", run)


def _build_guards(steps, name):
    validated = False
    packages = 0
    for step in steps:
        run = step.get("run", "")
        if _metadata_command(run):
            require("--tag" in run, f"{name}: metadata validation must check the tag")
            require("if" not in step, f"{name}: metadata validation must not be conditional")
            require(step.get("continue-on-error") not in (True, "true"), f"{name}: metadata validation may not ignore failures")
            validated = True
        # Match Android assembly and all desktop distribution/packaging tasks,
        # including tasks passed via PACKAGE_TASK instead of literal names.
        guard_only = re.search(r"\bverifyReleaseMetadata\b", run) and "-PreleaseTag=" in run
        if guard_only and not re.search(r"assemble|package(?:Msi|Dmg|Deb)|createDistributable|\$PACKAGE_TASK|\$\{PACKAGE_TASK\}", run):
            require("if" not in step and step.get("continue-on-error") not in (True, "true"),
                    f"{name}: metadata validation must be unconditional and fail closed")
            validated = True
        package = re.search(r"(?:assemble[A-Za-z0-9_$\{]*|package(?:Msi|Dmg|Deb)|createDistributable|\$PACKAGE_TASK|\$\{PACKAGE_TASK\})", run)
        if not package:
            continue
        packages += 1
        verification = run.find("verifyReleaseMetadata")
        guarded_here = 0 <= verification < package.start() and "-PreleaseTag=" in run
        require(validated or guarded_here, f"{name}: metadata validation must precede packaging")
        require(step.get("continue-on-error") not in (True, "true"), f"{name}: package validation may not ignore failures")
    require(packages > 0, f"{name}: package build is missing")


def validate_release(workflow):
    jobs = workflow.get("jobs")
    require(isinstance(jobs, dict), "release jobs must be a mapping")
    require(set(jobs) == {"metadata", "android-apk", "desktop", "publish"},
            "release must contain exactly metadata/android-apk/desktop/publish jobs")
    steps = {name: _steps(job, name) for name, job in jobs.items()}
    require(set(_needs(jobs["publish"])) == {"metadata", "android-apk", "desktop"}, "publish must depend on all build jobs and metadata")
    for name in ("android-apk", "desktop"):
        require(_needs(jobs[name]) == ["metadata"], f"{name} must depend on metadata")
        _build_guards(steps[name], name)
    metadata = [step for step in steps["metadata"] if _metadata_command(step.get("run", ""))]
    require(metadata, "metadata job must validate release metadata")
    require(any("--tag" in step["run"] and "if" not in step and step.get("continue-on-error") not in (True, "true")
                for step in metadata), "metadata validation must be unconditional and check the tag")

    strategy = jobs["desktop"].get("strategy", {})
    require(isinstance(strategy, dict) and isinstance(strategy.get("matrix"), dict), "desktop matrix required")
    include = strategy["matrix"].get("include")
    require(isinstance(include, list) and all(isinstance(item, dict) for item in include), "desktop matrix include must be a list")
    expected = {("windows-latest", "packageMsi", "msi"), ("ubuntu-latest", "packageDeb", "deb"),
                ("macos-15-intel", "packageDmg", "dmg")}
    require(len(include) == 3 and {(item.get("os"), item.get("task"), item.get("format")) for item in include} == expected,
            "desktop matrix must package MSI/DEB/DMG on windows-latest/ubuntu-latest/macos-15-intel")
    require(jobs["desktop"].get("runs-on") == "${{ matrix.os }}", "desktop must use its matrix runner")

    for name in ("android-apk", "desktop"):
        uploads = [step for step in steps[name] if step.get("uses", "").startswith("actions/upload-artifact@")]
        require(uploads, f"{name}: artifact upload required")
        for upload in uploads:
            settings = upload.get("with", {})
            require(isinstance(settings, dict) and settings.get("if-no-files-found") == "error",
                    f"{name}: upload must error on missing files")
            require(isinstance(settings.get("path"), str) and settings["path"].strip(), f"{name}: upload path required")
    for step in steps["desktop"]:
        if "prepare" in step.get("name", "").lower() or step.get("uses", "").startswith("actions/upload-artifact@"):
            contents = json.dumps(step)
            require(not re.search(r"(?<![\w-])app[/\\]+build|:app:|\.apk\b|android-release\.py|assemble(?:Release|Debug)", contents, re.IGNORECASE),
                    "desktop prepare/upload must not reference Android paths or APKs")

    desktop_steps = steps["desktop"]
    smoke = [index for index, step in enumerate(desktop_steps)
             if re.search(r"\bbash\s+scripts/smoke-desktop\.sh\b", step.get("run", ""))]
    require(len(smoke) == 1, "desktop must run the portable smoke-desktop.sh helper exactly once")
    package_steps = [index for index, step in enumerate(desktop_steps)
                     if re.search(r"package(?:Msi|Dmg|Deb)|\$PACKAGE_TASK|\$\{PACKAGE_TASK\}", step.get("run", ""))]
    require(package_steps and all(index < smoke[0] and ":desktop-app:createDistributable" in desktop_steps[index]["run"]
                                  and "if" not in desktop_steps[index] for index in package_steps),
            "desktop package command must explicitly include :desktop-app:createDistributable before smoke")
    require("if" not in desktop_steps[smoke[0]] and desktop_steps[smoke[0]].get("continue-on-error") not in (True, "true"),
            "desktop smoke test must be unconditional and fail closed")

    publish_steps = steps["publish"]
    publication = [index for index, step in enumerate(publish_steps) if "scripts/publish-release.py" in step.get("run", "")]
    validation = [index for index, step in enumerate(publish_steps)
                  if re.search(r"scripts/release_metadata\.py\s+artifacts\b", step.get("run", "")) and "if" not in step]
    notes = [index for index, step in enumerate(publish_steps)
             if re.search(r"scripts/release_metadata\.py\s+notes\b", step.get("run", "")) and "if" not in step]
    require(len(publication) == 1 and validation and notes and max(min(validation), min(notes)) < publication[0],
            "publish must validate artifacts and extract exact notes before publication")
    for index in (min(validation), min(notes)):
        require(publish_steps[index].get("continue-on-error") not in (True, "true"), "publish validation may not ignore failures")


def validate_directory(directory):
    directory = Path(directory)
    require(directory.is_dir(), f"workflow directory does not exist: {directory}")
    paths = sorted([*directory.glob("*.yml"), *directory.glob("*.yaml")])
    require(paths, "no workflow YAML files found")
    releases = []
    for path in paths:
        workflow = load_workflow(path.read_text(encoding="utf-8-sig"), str(path))
        require("on" in workflow and isinstance(workflow.get("jobs"), dict), f"{path}: on and jobs are required")
        for name, job in workflow["jobs"].items():
            _steps(job, f"{path.name}/{name}")
        if path.stem == "release":
            releases.append(path)
            validate_release(workflow)
        elif path.stem == "linux-appimage":
            job = workflow["jobs"].get("appimage")
            _build_guards(_steps(job, "appimage"), "appimage")
    require(len(releases) == 1, "exactly one release.yml or release.yaml is required")
    return len(paths)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, nargs="?", default=Path(".github/workflows"))
    args = parser.parse_args(argv)
    try:
        count = validate_directory(args.directory)
    except (WorkflowValidationError, OSError, UnicodeError) as error:
        print(f"workflow validation error: {error}", file=sys.stderr)
        return 1
    print(f"Workflow validation OK ({count} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
