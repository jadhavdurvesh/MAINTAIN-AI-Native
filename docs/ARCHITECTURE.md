# Architecture

JavaFX is the presentation layer. Rust owns domain operations, persistence, analytics, and device integration. The desktop communicates through versioned local IPC. Initial stream framing is newline-delimited JSON; the Protocol Buffers schema is the cross-language target. Desktop IPC must never be exposed to the network.

The first integration slice starts the engine from the JavaFX lifecycle, pings it over Unix-domain sockets on Linux or a named pipe on Windows, and shuts down the child process when the desktop exits. Rust initializes SQLite before accepting IPC.

Rust owns domain validation, SQLite transactions, telemetry, local analytics, adapter lifecycle, and audit records. JavaFX owns navigation, dashboards, forms, reports, accessibility, and presentation state. The local database is authoritative in standalone mode. Optional AI is outbound-only, user-configured, explicitly disclosed, and not required for core operations.

Parity areas: Dashboard, Machines, Maintenance, Work Orders, Fault Log, AI Assistant, Alerts, Spare Parts, Reports, AI Model Lab, History, Settings, About.

Initial local intelligence is adaptive EWMA/statistical analysis. Random Forest is a planned local inference adapter pending a model/data/version contract. TimeRadar, Chronos-2, Timer, and bundled LLMs are excluded from the initial native baseline.

Prediction and advisory outputs never directly actuate machinery. PLC logic, hardwired interlocks, and emergency-stop circuits remain authoritative.
