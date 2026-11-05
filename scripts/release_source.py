"""Record the exact recovery source commit in public release notes."""
from pathlib import Path
import re
import sys


def append_source(sha, notes):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid release source SHA")
    path = Path(notes)
    if not path.is_file() or path.is_symlink():
        raise ValueError("Release notes file is missing or unsafe")
    with path.open("a", encoding="utf-8") as stream:
        stream.write(f"\n\n### Build provenance\nSource commit: `{sha}`. For recovery builds the original tag is retained; packages are built from this explicitly recorded descendant commit.\n")


if __name__ == "__main__":
    append_source(*sys.argv[1:])
