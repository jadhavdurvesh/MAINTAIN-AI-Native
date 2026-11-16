# MAINTAIN AI Native

**The native desktop edition of MAINTAIN AI / YantraChikitsa.**

This edition migrates the existing application instead of rebuilding its features from scratch. It reuses the original React/Vite screens and FastAPI/SQLAlchemy business logic, hosted inside a JavaFX desktop application with a Rust native engine.

## What is already migrated

- Original React application shell, navigation, pages, components, profiles, and visual styles.
- Original FastAPI routers and SQLAlchemy domain models.
- Machine, maintenance, work-order, fault, alert, spare-part, device, sensor, telemetry, user, audit, analytics, and reporting endpoints.
- Original AI assistant workflow, offline knowledge-base diagnostic engine, and optional Gemini provider.
- Existing local ML modules, including adaptive telemetry/anomaly analysis and Random Forest risk model.
- PDF/Excel report exporters and ESP32 example firmware.

The 131 selected source files are copies in this repository under `frontend/`, `backend/`, and `firmware/`. The original source repository has not been modified. Generated build output, package folders, Python bytecode, the original database, and large PyInstaller build output were not copied.

## Native runtime

- **JavaFX** owns the Windows/Linux desktop lifecycle and window.
- **Original React UI** is built with Vite and displayed inside JavaFX WebView.
- **FastAPI** runs as a local-only sidecar and reuses the original API routes.
- **SQLite** is stored per user in the operating system's application-data directory.
- **Rust** remains the native local engine for IPC, device integration, and system services.
- A loopback-only server hosts the frontend and proxies `/api/*` requests to FastAPI.
- Supabase is disabled in local mode. The single-user desktop uses the existing backend's local administrator context.
- Core workflows do not require a cloud backend or cloud login.

## Intelligence and AI

Local capabilities reuse the original statistical/online telemetry logic and Random Forest model. No local language model is bundled.

The assistant uses the existing offline diagnostic engine by default. Gemini can be enabled by configuring an API key locally in Settings or through the backend's local environment. Provider requests are optional and outbound only.

TimeRadar, Chronos, and Timer forecasting are not enabled by default in this native edition. The copied forecasting source remains available for reference, but automatic forecasting to the old hosted ML endpoint is disabled so telemetry is not sent to a third-party service silently.

## Local data

Windows: `%LOCALAPPDATA%\DMJ Group\MAINTAIN AI Native\`

Linux: `$XDG_DATA_HOME/DMJ Group/MAINTAIN AI Native` or `~/.local/share/DMJ Group/MAINTAIN AI Native`

The main database is `maintain-ai.sqlite3`; backend logs are under `logs/`.

## Development

Prerequisites: JDK 21+, Rust stable, Node.js 20+, npm, Python 3.

From `apps/desktop`:

```bash
gradle run
```

Gradle builds the Rust engine and original React frontend, creates a Python virtual environment, installs `backend/requirements.txt`, starts the local FastAPI service, and opens the migrated UI in the JavaFX window.

For backend-only development:

```bash
cd backend
python -m venv .venv
# Activate the environment, then:
pip install -r requirements.txt
REQUIRE_AUTH=false DATABASE_URL=sqlite:///./maintain_ai.db python run_server.py
```

The heavyweight optional dependencies in `backend/requirements-ml.txt` are not installed by default.

## Important boundary

The local desktop bootstrap is a single-user loopback-only configuration. It is not a LAN host and must not be exposed to other computers. A future LAN mode needs separate authenticated users, TLS, role enforcement, and a reviewed network threat model.

MAINTAIN AI Native is supervisory maintenance software, not a safety controller. Emergency stops, PLC safety logic, hardwired interlocks, and manufacturer-approved machinery protections remain authoritative.
