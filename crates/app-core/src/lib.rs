//! Shared domain types used by the native desktop and Rust services.
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum Severity {
    Info,
    Warning,
    Critical,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum MachineStatus {
    Healthy,
    Attention,
    Critical,
    Offline,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Machine {
    pub id: Uuid,
    pub name: String,
    pub asset_tag: String,
    pub machine_type: String,
    pub location: Option<String>,
    pub status: MachineStatus,
    pub created_at: DateTime<Utc>,
}

impl Machine {
    pub fn new(
        name: impl Into<String>,
        asset_tag: impl Into<String>,
        machine_type: impl Into<String>,
    ) -> Self {
        Self {
            id: Uuid::new_v4(),
            name: name.into(),
            asset_tag: asset_tag.into(),
            machine_type: machine_type.into(),
            location: None,
            status: MachineStatus::Offline,
            created_at: Utc::now(),
        }
    }
}

/// In-memory telemetry input shared with the local analytics service.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetrySample {
    pub machine_id: Uuid,
    pub metric: String,
    pub value: f64,
    pub unit: String,
    pub observed_at: DateTime<Utc>,
}

/// A telemetry sample returned from the persistent native history store.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetryRecord {
    pub id: i64,
    pub machine_id: Uuid,
    pub metric: String,
    pub value: f64,
    pub unit: String,
    pub source: String,
    pub observed_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnomalySignal {
    pub machine_id: Uuid,
    pub metric: String,
    pub score: f64,
    pub severity: Severity,
    pub explanation: String,
    pub observed_at: DateTime<Utc>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn machine_defaults_offline() {
        let machine = Machine::new("Pump 01", "P-001", "Pump");
        assert_eq!(machine.status, MachineStatus::Offline);
        assert!(!machine.id.is_nil());
    }
}
