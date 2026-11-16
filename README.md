# MAINTAIN AI Native

A native, local-first industrial maintenance platform built as an independent Windows and Linux desktop application. The intended architecture uses Java 21+ / JavaFX for the desktop UI and a Rust engine for domain services, telemetry, local analytics, device integrations, and SQLite persistence.

Core workflows are designed to operate offline. An optional online AI assistant may connect to a user-configured provider; no bundled local language model is planned. Local statistical anomaly detection is independent of the online assistant. A local Random Forest inference adapter is planned as a separate capability after its model contract is defined.

## Safety boundary
MAINTAIN AI Native is supervisory maintenance software, not a safety controller. Emergency stops, PLC safety logic, hardwired interlocks, and manufacturer-approved protection remain authoritative.

## Architecture

```text
JavaFX desktop UI  <── local IPC ──>  Rust engine
                                      ├── SQLite + local files
                                      ├── Local analytics
                                      └── Device adapters (Serial / MQTT / Modbus)

Optional outbound HTTPS: configured AI provider only
```

## Product areas
Dashboard · Machines · Maintenance · Work Orders · Fault Log · AI Assistant · Alerts · Spare Parts · Reports · AI Model Lab · History · Settings · About

## Visual direction
Preserve the established MAINTAIN AI / YantraChikitsa visual language: graphite surfaces, subtle blue/teal gradients, frosted panels, IBM Plex typography, and clear industrial status colors.

## Development
Prerequisites: Rust stable, JDK 21+, Gradle. Start with `cargo test --workspace` and `cargo build --workspace`. The desktop project is under `apps/desktop`.

The repository is independent of the hosted MAINTAIN AI web application. No existing repository is modified by this project.

## License
All rights reserved unless a license is added explicitly.
