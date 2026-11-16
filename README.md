# MAINTAIN AI Native

A native, local-first industrial maintenance platform built as an independent Windows and Linux desktop application. It uses Java 21+ / JavaFX for the desktop UI and a Rust engine for domain services, telemetry, local analytics, device integrations, and SQLite persistence.

Core workflows are designed to work offline. An optional online AI assistant can connect to a user-configured provider; no bundled local language model is planned. Local statistical anomaly detection is independent of that assistant. A Random Forest local inference adapter is planned once its model contract is defined.

## Current functional slice

- Native JavaFX shell with product navigation and the dark industrial visual identity.
- Rust engine starts from the desktop and responds to a versioned local IPC ping.
- Machine registry: create and list machines through local IPC and SQLite.
- SQLite schema for machines, telemetry samples, and audit events.
- Adaptive EWMA anomaly detector foundation.

## Architecture

```text
JavaFX Desktop  <── local IPC ──> Rust Engine
                                     ├── SQLite + local files
                                     ├── Local analytics
                                     └── Device adapters (Serial / MQTT / Modbus)

Optional outbound HTTPS: configured AI provider only
```

The IPC bootstrap is newline-framed JSON today; the versioned Protocol Buffers contract is in `proto/engine/v1/engine.proto` as the cross-language target. IPC stays local-machine only.

## Safety boundary

MAINTAIN AI Native is supervisory maintenance software, not a safety controller. Emergency stops, PLC safety logic, hardwired interlocks, and manufacturer-approved protection remain authoritative.

## Development

Prerequisites: Rust stable, JDK 21+, Gradle.

```bash
cargo test --workspace
cargo build --workspace
cd apps/desktop
gradle run
```

The desktop launch task builds the Rust engine before starting JavaFX. CI compiles and tests the Rust workspace on Windows and Ubuntu, and compiles the desktop project on both platforms.

## Repository

This repository is independent of the hosted MAINTAIN AI web application. The existing web repository is not modified by native development.
