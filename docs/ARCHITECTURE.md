# Architecture and source migration

## Migration strategy

The native edition retains the existing React/Vite frontend and FastAPI/SQLAlchemy backend as its product implementation. This avoids reimplementing the existing feature set. JavaFX and Rust provide the native host and systems integration around those components.

## Runtime flow

```text
JavaFX desktop
  ├── WebView → original React/Vite UI
  │                └── loopback frontend server
  │                     └── /api proxy → FastAPI sidecar
  │                                        └── SQLAlchemy → local SQLite
  └── Rust native engine → local IPC / device / system services
```

The JavaFX app starts the Rust engine, then the local FastAPI backend and embedded frontend server. The frontend sends same-origin `/api/*` requests; the loopback server proxies those to FastAPI. This avoids permissive CORS and avoids an externally hosted backend URL.

## Migrated source

- `frontend/`: original application shell, navigation, styles, screens, API client, charts, machine profiles, and local storage auth wrapper.
- `backend/app/`: original FastAPI routers, SQLAlchemy models, authentication dependencies, AI assistant, telemetry, analytics/ML, safety logic, audit trail, and exports.
- `firmware/`: original ESP32 example firmware.
- `crates/`: new Rust domain, IPC, persistence, analytics, and device-service foundation.
- `apps/desktop/`: JavaFX lifecycle, local backend supervisor, frontend host/proxy.

## Local data ownership

The local SQLite database is authoritative for the standalone installation and lives in OS application data. The source repository's database is not copied. The first local run initializes a fresh schema.

The current bootstrap disables Supabase and backend login requirements and uses the FastAPI application's local administrator context. It is intended for a single-user installation on the local machine only. LAN hosting is a separate feature and is not currently enabled.

## Intelligence

The source statistical telemetry/anomaly modules and Random Forest risk model are retained. The offline maintenance diagnostic engine is the default assistant. Optional Gemini calls are initiated only when explicitly selected and configured. No local LLM is bundled.

The existing optional TimeRadar/Chronos/Timer source files are preserved, but model download/inference dependencies are not installed by default, and the native launcher disables automatic calls to the existing hosted ML service.

## Safety

No predicted failure risk or advisory result directly actuates machinery. PLC logic, hardwired interlocks, and emergency-stop circuits remain authoritative.
