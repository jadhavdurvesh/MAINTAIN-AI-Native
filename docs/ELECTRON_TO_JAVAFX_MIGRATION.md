# Electron to JavaFX desktop-host migration

The native application does not carry the old Electron package forward. Its desktop host is JavaFX, with the Rust engine retained as a separate local process.

## Responsibility mapping

| Previous Electron responsibility | Native implementation |
|---|---|
| Create and own the desktop window | `apps/desktop/.../App.java` creates the JavaFX stage and WebView |
| Start and stop the local application backend | `BackendSupervisor.java` launches FastAPI and shuts it down with the desktop host |
| Wait for the backend to become healthy | `BackendSupervisor.startAndWait()` polls the loopback health endpoint |
| Serve the built frontend | `LocalFrontendServer.java` hosts the Vite output on loopback |
| Route frontend API calls to the local backend | `LocalFrontendServer.java` proxies `/api/*` to FastAPI |
| Persist data outside the install directory | Backend data directory resolves under the user's OS application-data location |
| Native domain services and device/IPC work | Rust workspace under `crates/` |

## What is deliberately different

- Electron's Chromium/Node desktop shell is replaced with JavaFX WebView and Java process supervision.
- The original UI and FastAPI domain behaviour are reused during the migration rather than rewritten in one risky step.
- The Rust engine is a separate native service; it is not yet the authoritative store for every existing business workflow.

## Packaging status

The default Gradle run builds the Rust engine and JavaFX host only. It does not build React, install Python dependencies or start FastAPI. The optional runCompatibility task prepares the React bundle and Python environment; FastAPI and the compatibility WebView start only when that window is opened. The optional task exists to preserve access to unported workflows during the transition. The native build does **not yet** produce a self-contained Windows installer equivalent to the former Electron/PyInstaller distribution.

Before declaring installer parity, the packaging pipeline must bundle a supported Java runtime, JavaFX modules, the Rust executable, the Python runtime and backend dependencies, frontend assets, and the correct Windows installer/signing resources. It must also verify clean-machine startup without requiring globally installed Node.js or Python. Until that work is completed, run the source development workflow documented in the root README.
