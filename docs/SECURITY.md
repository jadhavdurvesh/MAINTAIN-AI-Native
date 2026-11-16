# Security model

- The embedded frontend server and FastAPI sidecar bind to loopback only; neither is exposed to the LAN.
- API traffic from WebView uses the local same-origin proxy. CORS is not opened to arbitrary origins.
- Supabase is explicitly disabled in the standalone local process. The local no-login administrator context is suitable only for a single-user desktop installation.
- The Rust engine IPC is local-machine only. Linux socket files use owner-only permissions; production Windows named-pipe ACLs require review.
- Optional Gemini/AI provider credentials must be configured locally and never committed or packaged as plaintext.
- TimeRadar/Chronos/Timer auto-forecast calls and the legacy hosted ML service URL are disabled in the native bootstrap.
- Keep local database files in per-user application data. Back up before schema upgrades.
- Any future LAN host mode needs separate authenticated accounts, role enforcement, TLS, and a reviewed network threat model.
- AI and advisory outputs must never bypass E-stops, PLC safety functions, hardwired interlocks, or manufacturer protections.
