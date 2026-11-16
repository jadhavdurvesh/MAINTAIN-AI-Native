//! Versioned local IPC contract shared by the desktop and Rust engine.
use maintain_app_core::Machine;
use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION: u32 = 1;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", content = "payload", rename_all = "snake_case")]
pub enum EngineRequest {
    Ping,
    GetSystemStatus,
    ListMachines,
    CreateMachine {
        name: String,
        asset_tag: String,
        machine_type: String,
        location: Option<String>,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "kind", content = "payload", rename_all = "snake_case")]
pub enum EngineResponse {
    Pong { protocol_version: u32, engine: String },
    SystemStatus { ready: bool, database: String },
    MachinesListed { machines: Vec<Machine> },
    MachineCreated { machine: Machine },
    Error { code: String, message: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IpcEnvelope<T> {
    pub request_id: String,
    pub protocol_version: u32,
    pub message: T,
}

impl<T> IpcEnvelope<T> {
    pub fn new(request_id: impl Into<String>, message: T) -> Self {
        Self {
            request_id: request_id.into(),
            protocol_version: PROTOCOL_VERSION,
            message,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn envelope_round_trips() {
        let envelope = IpcEnvelope::new("id-1", EngineRequest::Ping);
        let serialized = serde_json::to_string(&envelope).unwrap();
        let decoded: IpcEnvelope<EngineRequest> = serde_json::from_str(&serialized).unwrap();
        assert_eq!(decoded.protocol_version, PROTOCOL_VERSION);
    }

    #[test]
    fn create_machine_request_round_trips() {
        let request = IpcEnvelope::new(
            "create-1",
            EngineRequest::CreateMachine {
                name: "Pump 01".into(),
                asset_tag: "P-001".into(),
                machine_type: "Centrifugal pump".into(),
                location: Some("Workshop A".into()),
            },
        );
        let serialized = serde_json::to_string(&request).unwrap();
        let decoded: IpcEnvelope<EngineRequest> = serde_json::from_str(&serialized).unwrap();
        assert!(matches!(decoded.message, EngineRequest::CreateMachine { .. }));
    }
}
