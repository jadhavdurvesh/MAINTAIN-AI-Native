# Security model

- IPC remains local-machine only. Linux socket files are created with owner-only permissions; production Windows named-pipe ACLs must be reviewed and restricted.
- The engine rejects protocol-version mismatches and request lines larger than 64 KiB.
- LAN mode is opt-in, disabled by default, and must use authenticated TLS endpoints. Desktop IPC is never exposed to the network.
- Store provider credentials in the operating system credential store; never commit secrets.
- Online AI is optional. Disclose what leaves the device and send only the necessary selected context.
- Validate and bound telemetry, imports, uploads, and model files.
- Keep an audit trail for privileged configuration and maintenance actions.
- Backups must be restorable and must never overwrite the only existing copy.
- This application is not a safety controller. Advisory outputs must not drive safety actuation.
