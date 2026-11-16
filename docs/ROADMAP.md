# Roadmap

## Foundation
- [x] Rust workspace and crate boundaries
- [x] Shared domain model starter
- [x] SQLite bootstrap schema
- [x] Local EWMA detector starter
- [x] Versioned IPC envelope and Protobuf schema
- [x] JavaFX shell with product navigation and visual direction
- [x] Desktop starts the Rust engine and confirms local IPC ping
- [ ] Validate JavaFX launch on Windows and Linux

## Slice 1 — Engine lifecycle
- [x] Start engine from JavaFX lifecycle and stop child on desktop close
- [x] Ping over Unix socket / Windows named pipe
- [ ] Robust single-instance ownership and restart recovery
- [ ] Java-side engine status polling and diagnostics

## Slice 2 — Asset registry
- [ ] Machine CRUD, validation, sensor configuration, detail view
- [ ] SQLite repository layer and migrations

## Slice 3 — Telemetry and alerts
- [ ] Serial/USB, MQTT, and Modbus adapters
- [ ] Historical queries, retention, threshold rules, alert acknowledgement

## Slice 4 — Maintenance operations
- [ ] Maintenance plans and schedules; work orders and assignments
- [ ] Fault log, audit history, spare parts, stock movements

## Slice 5 — Intelligence
- [ ] Random Forest local adapter and model validation
- [ ] Model lab and optional online AI provider gateway

## Production readiness
- [ ] Local users and roles; secure secrets; backup/restore
- [ ] Authenticated LAN host mode and TLS
- [ ] Windows installer and Linux packages
- [ ] Upgrade, migration, rollback, accessibility and end-to-end tests
