# Native migration roadmap

This roadmap describes the native JavaFX/Rust product. React/FastAPI compatibility code is a temporary bridge; it is not counted as a completed native migration.

## Completed native foundation
- [x] Independent native repository; the original product repository remains unchanged
- [x] Rust workspace and crate boundaries
- [x] Shared domain types and versioned local IPC envelope
- [x] SQLite bootstrap schema, migrations and repository tests
- [x] Local adaptive EWMA detector foundation
- [x] JavaFX primary desktop window
- [x] Rust engine lifecycle and ping over Unix socket / Windows named pipe
- [x] Default desktop run is JavaFX + Rust; React/Python are not built or started by default
- [x] Training notebooks, data adapters/build scripts and temporal-model metadata migrated into research/reference paths

## Slice 1 — Native machine registry
- [x] List machines from JavaFX through IPC to Rust SQLite
- [x] Create machines through JavaFX, IPC and Rust SQLite
- [x] Validate required fields and maximum lengths in the Rust engine
- [x] Enforce unique asset tags in SQLite and surface duplicate-tag errors
- [ ] Edit/archive machines
- [ ] Machine detail view and component/sensor configuration
- [ ] Data import from the separate compatibility database

## Slice 2 — Telemetry and device integrations
- [ ] Add telemetry write/list commands to the IPC contract and Rust repository
- [ ] Add JavaFX telemetry history and live status views
- [ ] Implement serial/USB device adapters
- [ ] Implement MQTT and Modbus TCP adapters
- [ ] Add validated threshold rules, alert acknowledgement and retention
- [ ] Add end-to-end hardware/simulator tests

## Slice 3 — Maintenance operations
- [ ] Maintenance plans and schedules
- [ ] Work orders, assignment and completion flows
- [ ] Fault log, audit history, spare parts and stock movements
- [ ] Native PDF/CSV report exports

## Slice 4 — Intelligence
- [ ] Port local anomaly/degradation logic into the Rust analytics service
- [ ] Validate and integrate approved local model inference adapters
- [ ] Native model status and optional model manager UI
- [ ] Port AI assistant workflows and provider gateway with explicit opt-in
- [ ] Calibrate risk predictions against real, technician-confirmed outcome data before production claims

## Desktop distribution and operational readiness
- [ ] Robust single-instance ownership, restart recovery and engine diagnostics
- [ ] Self-contained Windows installer with bundled Java runtime, JavaFX, Rust engine and required local services
- [ ] Linux packages
- [ ] Upgrade, rollback and legacy SQLite import tooling
- [ ] Local users/roles, secrets handling and backup/restore
- [ ] Accessibility, clean-machine install tests and end-to-end test coverage
- [ ] Remove the React/FastAPI compatibility bridge only after all required workflows have native parity

## Current compatibility boundary

The optional compatibility workspace retains the copied React/Vite and FastAPI implementations so unported workflows remain usable. It currently stores data in a separate SQLite database from the Rust engine. No cross-store synchronization is implied; migration and import must be explicit and tested.
