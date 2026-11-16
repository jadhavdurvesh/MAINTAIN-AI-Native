# Research, datasets and model training

This repository keeps model experimentation separate from the native desktop runtime.

## Training source

The reproducible bootstrap package is under `training/`:

- Six Jupyter notebooks covering baseline training iterations, public-model evaluation, industrial experiments and pretrained-model experiments.
- Dataset-specific adapters for NASA C-MAPSS, IMS, CWRU and Paderborn source formats.
- Shared schema, asset splitting, temporal sequence construction, model, training and evaluation scripts.
- `config.yaml` and a dedicated `requirements.txt`.

Start with [training/README.md](../training/README.md) and [training/COLAB.md](../training/COLAB.md). Use a separate GPU notebook environment such as Google Colab. The training requirements are not part of the JavaFX/Rust desktop installation and must not be installed by the normal Gradle tasks.

## Dataset preparation

`scripts/ml/prepare_public_datasets.py` validates locally downloaded datasets and writes a SHA-256 manifest. Raw datasets are deliberately not copied into this repository: their size, access terms and licensing conditions differ. Read each provider's terms and citation requirements before using a dataset, especially for a commercial product.

Dataset-specific conversion lives in `training/src/adapters/`. The adapters preserve source semantics. For example, a C-MAPSS cycle is not an industrial hour, and bearing-fault datasets are not interchangeable with pump or compressor run-to-failure data.

## Temporal artifact metadata

The V1.1 bootstrap artifact notes are stored at:

`backend/app/ml/artifacts/maintain_ai_shared_temporal_v1/`

The three files describe the model, benchmark metrics and interpretation limits. The checkpoint itself is **not** included in this migration. The published metrics are NASA C-MAPSS results, not production results for industrial machines. The 24h/48h/7d risk heads are documented as untrained; no calibrated failure probability or industrial RUL claim is implied.

## Runtime boundary

The desktop runtime must remain usable without downloading large training datasets or installing a GPU training stack. Runtime inference adapters and optional pretrained checkpoints are managed separately. A future trained artifact must go through size, provenance, license, input-schema, evaluation and safety review before it can be bundled or enabled in production.
