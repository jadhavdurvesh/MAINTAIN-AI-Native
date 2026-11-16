# MAINTAIN AI Native

**The JavaFX and Rust desktop edition of MAINTAIN AI / YantraChikitsa.**

This is an independent repository for the native Windows/Linux application. The normal desktop window is JavaFX, and the native machine registry communicates with a Rust engine over a versioned local IPC protocol. Existing product functionality is being migrated in vertical slices so behaviour can be preserved and tested without changing the source repository.

## Native path available now

- **JavaFX** owns the primary desktop window and native machine-registry interface.
- **Rust engine** owns local machine-registry commands and the native SQLite store.
- **Versioned IPC** uses a Windows named pipe or a Unix-domain socket.
- **SQLite** stores native machine records and audit events in the user's OS application-data directory.
- **Local device/analytics crates** provide the foundation for the next Rust service migrations.

The first native vertical slice supports listing and registering machines. Telemetry ingestion, maintenance, work orders, faults, alerts, reports, AI services, user management and other workflows are still being ported; they are not yet native Rust/Java features.

## Temporary compatibility workspace

The copied React/Vite and FastAPI/SQLAlchemy applications remain available as a temporary bridge for workflows that have not been migrated. They are **not built or started by the default desktop run**.

From apps/desktop:

```bash
# Native JavaFX + Rust workspace. No Node.js or Python runtime required.
gradle run
```

To include the old screens while migration continues:

```bash
# Optional compatibility build: builds the React assets and prepares the local Python backend.
gradle runCompatibility
```

The compatibility window is opened from the native workspace on demand. Its legacy backend and the Rust engine currently use separate SQLite files; records are not yet synchronized between the two stores. Do not treat the compatibility application as a second editor for data entered into the native registry.

The original source repository has not been modified. The selected source files are copies held in this repository under frontend/, backend/ and firmware/, with training and model-research material under training/ and backend/app/ml/artifacts/.

## Intelligence and research

The native desktop default does not silently call the old hosted ML endpoint, download pretrained checkpoints or install a GPU training stack.

- Research notebooks and training code live under training/.
- Dataset adapters cover NASA C-MAPSS, IMS, CWRU and Paderborn source formats.
- scripts/ml/prepare_public_datasets.py writes local dataset manifests.
- Temporal model metadata and benchmark notes live under backend/app/ml/artifacts/maintain_ai_shared_temporal_v1/.
- Raw datasets and model weights are deliberately excluded from Git and from the desktop build.

See Research and Training (docs/RESEARCH_AND_TRAINING.md) and Forecasting (docs/FORECASTING.md).

## Local data

Windows: %LOCALAPPDATA%\\DMJ Group\\MAINTAIN AI Native\\

Linux: $XDG_DATA_HOME/DMJ Group/MAINTAIN AI Native or ~/.local/share/DMJ Group/MAINTAIN AI Native

The Rust engine's database is maintain-native.sqlite3. When the optional compatibility backend is run, it uses a separate maintain-ai.sqlite3 file under the same application-data directory. Database unification and a reviewed legacy-data import are future migration work.

## Development

### Native workspace

Prerequisites: JDK 21+, Rust stable and Gradle.

From apps/desktop:

```bash
gradle run
```

This builds the Rust engine, compiles JavaFX and opens the native machine registry.

### Compatibility workspace

Additional prerequisites: Node.js 20+, npm and Python 3.

```bash
gradle runCompatibility
```

This optional task builds the React assets and prepares backend/.venv. The FastAPI service starts only when the compatibility window is opened. The heavyweight optional ML dependencies are not installed by default.

Backend-only development remains available:

```bash
cd backend
python -m venv .venv
# Activate the environment, then:
pip install -r requirements.txt
REQUIRE_AUTH=false DATABASE_URL=sqlite:///./maintain_ai.db python run_server.py
```

## Migration status and safety boundary

- JavaFX is the primary host; a self-contained signed Windows installer is still pending.
- Machine registry CRUD is the first end-to-end JavaFX/Rust slice.
- Remaining business workflows are still in the compatibility stack and must be migrated one module at a time.
- The native Rust engine is not yet the source of truth for every business workflow.

See [Electron to JavaFX migration status](docs/ELECTRON_TO_JAVAFX_MIGRATION.md) and the [native migration roadmap](docs/ROADMAP.md).

MAINTAIN AI is supervisory maintenance software, not a safety controller. Emergency stops, PLC safety logic, hardwired interlocks and manufacturer-approved machinery protections remain authoritative.
