"""Offline tests for recovery-source provenance; no subprocess or network."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("release_source", Path(__file__).resolve().with_name("release_source.py"))
source = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(source)


class ReleaseSourceTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.notes = self.root / "notes.md"
        self.notes.write_text("Release fixes.\n", encoding="utf-8")

    def test_valid_sha_appends_exact_provenance(self):
        sha = "0123456789abcdef" * 2 + "01234567"
        source.append_source(sha, self.notes)
        text = self.notes.read_text(encoding="utf-8")
        self.assertTrue(text.startswith("Release fixes.\n"))
        self.assertIn("### Build provenance", text)
        self.assertIn("Source commit: `" + sha + "`.", text)
        self.assertIn("original tag is retained", text)

    def test_invalid_sha_does_not_write_or_create(self):
        before = self.notes.read_bytes()
        for sha in ("", "a" * 39, "a" * 41, "A" * 40, "g" * 40, "a" * 40 + "\n", "a" * 40 + "\nkey=bad"):
            with self.subTest(sha=sha), self.assertRaises(ValueError):
                source.append_source(sha, self.notes)
            self.assertEqual(before, self.notes.read_bytes())
        missing = self.root / "missing.md"
        with self.assertRaises(ValueError):
            source.append_source("invalid", missing)
        self.assertFalse(missing.exists())

    def test_missing_notes_with_valid_sha_not_created(self):
        missing = self.root / "missing.md"
        with self.assertRaises(ValueError):
            source.append_source("a" * 40, missing)
        self.assertFalse(missing.exists())


if __name__ == "__main__":
    unittest.main()
