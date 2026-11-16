# Security model

- IPC remains local-machine only; production socket permissions and Windows named-pipe ACLs are required.
- LAN mode is opt-in, disabled by default, and must use authenticated TLS endpoints.
- Store provider credentials in the operating system credential store; never commit secrets.
- Online AI is optional. Disclose what leaves the device and send only the necessary selected context.
- Validate and bound telemetry, imports, uploads, and model files.
- Keep an audit trail for privileged configuration and maintenance actions.
- Backups must be restorable and must never overwrite the only existing copy.
- This application is not a safety controller. Advisory outputs must not drive safety actuation.
