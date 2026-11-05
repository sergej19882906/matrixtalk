"""Isolated configuration regressions. No containers, secrets, or network calls.

Run: python -I docker/tests/test_configuration.py
PyYAML is used as a mock round-trip YAML facade when ruamel is unavailable.
Production always uses ruamel; these tests exercise scanner/config decisions.
"""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
import yaml as pyyaml

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]


class MockYaml:
    def indent(self, **kwargs):
        pass

    def load(self, stream):
        return pyyaml.safe_load(stream)

    def dump(self, data, stream):
        return pyyaml.safe_dump(dict(data), stream, sort_keys=False)


try:
    from ruamel.yaml import YAML
except ImportError:
    package = types.ModuleType("ruamel")
    module = types.ModuleType("ruamel.yaml")
    module.YAML = MockYaml
    package.yaml = module
    sys.modules["ruamel"] = package
    sys.modules["ruamel.yaml"] = module

spec = importlib.util.spec_from_file_location("synapse_configuration_under_test", ROOT / "docker/synapse/configure.py")
configuration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(configuration)


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="matrix-config-test-")
        self.root = Path(self.temp.name)
        self.config = self.root / "homeserver.yaml"
        self.config.write_text("server_name: mock.example.test\napp_service_config_files:\n  - /data/user-appservice.yaml\n", encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def registration(self, bridge, suffix="yaml", valid=True):
        path = self.root / bridge / ("registration." + suffix)
        path.parent.mkdir(exist_ok=True)
        document = dict(id=bridge, url="http://mock-bridge:1234", as_token="MOCK_ONLY_AS",
                        hs_token="MOCK_ONLY_HS", sender_localpart="mockbot", namespaces={})
        if not valid:
            del document["hs_token"]
        path.write_text(pyyaml.safe_dump(document), encoding="utf-8")
        return path.as_posix()

    def apply(self):
        with patch.dict(os.environ, {"POSTGRES_PASSWORD": "MOCK_ONLY_PASSWORD", "TURN_SHARED_SECRET": "",
                                     "SYNAPSE_REGISTRATION_SHARED_SECRET": ""}, clear=True):
            configuration.configure(self.config, self.root)
        return pyyaml.safe_load(self.config.read_text(encoding="utf-8"))

    def test_all_eight_preserve_user_entry_and_idempotency(self):
        expected = [self.registration(bridge, "yml" if bridge == "hookshot" else "yaml")
                    for bridge in configuration.BRIDGES]
        result = self.apply()
        self.assertEqual(["/data/user-appservice.yaml"] + expected, result["app_service_config_files"])
        self.assertEqual(result, self.apply())

    def test_missing_optional_bridges_preserve_user_config(self):
        self.assertEqual(["/data/user-appservice.yaml"], self.apply()["app_service_config_files"])

    def test_invalid_registration_leaves_original_config_unchanged(self):
        before = self.config.read_bytes()
        self.registration("mautrix-meta", valid=False)
        with self.assertRaises(ValueError):
            self.apply()
        self.assertEqual(before, self.config.read_bytes())

    def test_ambiguous_extensions_fail(self):
        self.registration("hookshot", "yaml")
        self.registration("hookshot", "yml")
        with self.assertRaises(ValueError):
            self.apply()

    def test_empty_password_fails(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(ValueError):
            configuration.configure(self.config, self.root)

    def test_compose_has_all_mounts_and_independent_profiles(self):
        services = pyyaml.safe_load((ROOT / "docker-compose.bridges.yml").read_text(encoding="utf-8"))["services"]
        mounts = services["synapse"]["volumes"]
        for bridge in configuration.BRIDGES:
            self.assertIn(f"./server-data/{bridge}:/data/{bridge}:ro", mounts)
        bridge_services = ["telegram-bridge", "whatsapp-bridge", "signal-bridge", "mautrix-meta-bridge",
                           "mautrix-gmessages-bridge", "mautrix-discord-bridge", "heisenbridge", "hookshot"]
        profiles = [services[name]["profiles"][0] for name in bridge_services]
        self.assertEqual(8, len(set(profiles)))
        self.assertNotIn("bridges", profiles)
        self.assertEqual(["push"], services["ntfy"]["profiles"])
        self.assertNotIn("2>/dev/null", str(services["init-bridges-db"]))

    def test_upstream_entrypoints_and_fail_fast_not_binary_guesses(self):
        for bridge in configuration.BRIDGES[:6]:
            script = (ROOT / "docker" / bridge / "entrypoint.sh").read_text(encoding="utf-8")
            self.assertIn('exec /docker-run.sh "$@"', script)
            self.assertIn("exit 64", script)
            self.assertNotIn("|| true", script)
            self.assertNotIn("sed -i", script)
            self.assertIn("CMD []", (ROOT / "docker" / bridge / "Dockerfile").read_text(encoding="utf-8"))
        heisen = (ROOT / "docker/heisenbridge/entrypoint.sh").read_text(encoding="utf-8")
        self.assertIn('-o "$BRIDGE_ADMIN" "$HOMESERVER_ADDRESS"', heisen)
        hookshot = (ROOT / "docker/hookshot/entrypoint.sh").read_text(encoding="utf-8")
        self.assertIn("/bin/matrix-hookshot/App/BridgeApp.js /data/config.yml /data/registration.yml", hookshot)
        for script in (ROOT / "docker").glob("*/entrypoint.sh"):
            subprocess.run(["sh", "-n", str(script)], cwd=ROOT, check=True, capture_output=True)

    def test_ntfy_rejects_missing_and_non_origin_configuration(self):
        script = ROOT / "docker/ntfy/entrypoint.sh"
        for base in ("", "http://mock.example.test", "https://user:password@mock.example.test",
                     "https://mock.example.test/subpath", "https://mock.example.test?query"):
            with patch.dict(os.environ, {"NTFY_BASE_URL": base}, clear=False):
                result = subprocess.run(["sh", str(script)], cwd=ROOT, capture_output=True)
            self.assertEqual(64, result.returncode)

    def test_ntfy_hyphenated_keys(self):
        config = pyyaml.safe_load((ROOT / "docker/ntfy/server.yml").read_text(encoding="utf-8"))
        self.assertIn("cache-file", config)
        self.assertTrue(all("_" not in key for key in config))


if __name__ == "__main__":
    unittest.main()
