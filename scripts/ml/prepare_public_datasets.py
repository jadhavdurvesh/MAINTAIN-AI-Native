"""Validate downloaded PHM data and create a reproducible manifest.

Raw datasets stay outside Git because of size/licensing. Dataset-specific
parsers convert each source into the common training representation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    from backend.app.ml.public_datasets import DATASET_REGISTRY

    parser = argparse.ArgumentParser(
        description="Validate a local public PHM dataset and write a SHA-256 manifest."
    )
    parser.add_argument(
        "--data-dir",
        default=str(REPO_ROOT / "training-data"),
        help="Root folder containing one subfolder per registered dataset.",
    )
    parser.add_argument(
        "--dataset",
        required=True,
        choices=sorted(DATASET_REGISTRY),
        help="Dataset registry key to validate.",
    )
    args = parser.parse_args()

    spec = DATASET_REGISTRY[args.dataset]
    dataset_root = Path(args.data_dir).expanduser().resolve() / args.dataset
    files = sorted(
        path for path in dataset_root.rglob("*")
        if path.is_file() and path.name != "manifest.json"
    )
    if not files:
        raise SystemExit(
            f"No raw files found in {dataset_root}. Download the registered source first."
        )

    manifest = {
        "dataset": args.dataset,
        "category": spec["category"],
        "role": spec["role"],
        "signals": list(spec["signals"]),
        "failure_target": spec["failure_target"],
        "source": spec["source"],
        "source_url": spec["source_url"],
        "files": [
            {
                "path": str(path.relative_to(dataset_root)),
                "sha256": sha256(path),
                "bytes": path.stat().st_size,
            }
            for path in files
        ],
    }
    destination = dataset_root / "manifest.json"
    destination.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"manifest": str(destination), **manifest}, indent=2))


if __name__ == "__main__":
    main()
