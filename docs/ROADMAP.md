# Roadmap

## Foundation
- [x] Rust workspace and crate boundaries
- [x] Shared domain model starter
- [x] SQLite bootstrap schema
- [x] Local EWMA detector starter
- [x] Versioned IPC envelope and Protobuf schema
- [x] JavaFX shell with product navigation and visual direction
- [ ] Validate Windows and Linux builds in CI

## Slice 1 — Engine lifecycle
- [ ] Start/stop engine from JavaFX lifecycle
- [ ] Java IPC client for Unix socket and Windows named pipe
- [ ] Ping/status integration and restart handling

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
