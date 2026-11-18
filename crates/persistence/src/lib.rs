//! SQLite bootstrap, migrations, machine registry and telemetry repository.
use anyhow::{anyhow, Context, Result};
use chrono::{DateTime, Utc};
use maintain_app_core::{AnomalySignal, Machine, MachineStatus, Severity, TelemetryRecord};
use rusqlite::{params, Connection};
use std::path::Path;
use uuid::Uuid;

pub fn open_database(path: impl AsRef<Path>) -> Result<Connection> {
    if let Some(parent) = path.as_ref().parent() {
        std::fs::create_dir_all(parent)?;
    }
    let database = Connection::open(path)?;
    database.pragma_update(None, "foreign_keys", "ON")?;
    database.busy_timeout(std::time::Duration::from_secs(5))?;
    migrate(&database)?;
    Ok(database)
}

pub fn migrate(database: &Connection) -> Result<()> {
    database.execute_batch(
        "
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version INTEGER PRIMARY KEY,
            applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS machines (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            asset_tag TEXT NOT NULL UNIQUE,
            machine_type TEXT NOT NULL,
            location TEXT,
            status TEXT NOT NULL,
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS telemetry_samples (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            machine_id TEXT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
            metric TEXT NOT NULL,
            value REAL NOT NULL,
            unit TEXT NOT NULL,
            source TEXT NOT NULL DEFAULT 'manual',
            observed_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_telemetry_machine_metric_time
            ON telemetry_samples(machine_id, metric, observed_at);

        CREATE TABLE IF NOT EXISTS anomaly_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            machine_id TEXT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
            metric TEXT NOT NULL,
            score REAL NOT NULL,
            severity TEXT NOT NULL,
            explanation TEXT NOT NULL,
            observed_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_anomaly_machine_time
            ON anomaly_events(machine_id, observed_at);

        CREATE TABLE IF NOT EXISTS audit_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            actor TEXT NOT NULL,
            action TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            entity_id TEXT,
            details_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        INSERT OR IGNORE INTO schema_migrations(version) VALUES (1);
        "
    )?;

    // Existing databases created by schema v1 have telemetry_samples but no source column.
    // Apply this additive migration safely without dropping or rewriting existing readings.
    let has_source = {
        let mut statement = database.prepare("PRAGMA table_info(telemetry_samples)")?;
        let columns = statement.query_map([], |row| row.get::<_, String>(1))?;
        columns.collect::<rusqlite::Result<Vec<_>>>()?
            .iter()
            .any(|column| column == "source")
    };
    if !has_source {
        database.execute(
            "ALTER TABLE telemetry_samples ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'",
            [],
        )?;
    }
    database.execute(
        "INSERT OR IGNORE INTO schema_migrations(version) VALUES (2)",
        [],
    )?;
    database.execute(
        "INSERT OR IGNORE INTO schema_migrations(version) VALUES (3)",
        [],
    )?;
    Ok(())
}

pub fn insert_machine(database: &Connection, machine: &Machine) -> Result<()> {
    database.execute(
        "INSERT INTO machines (id, name, asset_tag, machine_type, location, status, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![
            machine.id.to_string(),
            machine.name,
            machine.asset_tag,
            machine.machine_type,
            machine.location,
            format!("{:?}", machine.status),
            machine.created_at.to_rfc3339(),
        ],
    )?;
    database.execute(
        "INSERT INTO audit_events (actor, action, entity_type, entity_id, details_json)
         VALUES ('local-user', 'machine.created', 'machine', ?1, ?2)",
        params![machine.id.to_string(), serde_json::json!({
            "asset_tag": machine.asset_tag,
            "name": machine.name
        }).to_string()],
    )?;
    Ok(())
}

pub fn list_machines(database: &Connection) -> Result<Vec<Machine>> {
    let mut statement = database.prepare(
        "SELECT id, name, asset_tag, machine_type, location, status, created_at
         FROM machines ORDER BY name COLLATE NOCASE ASC"
    )?;

    let rows = statement.query_map([], |row| {
        Ok((
            row.get::<_, String>(0)?,
            row.get::<_, String>(1)?,
            row.get::<_, String>(2)?,
            row.get::<_, String>(3)?,
            row.get::<_, Option<String>>(4)?,
            row.get::<_, String>(5)?,
            row.get::<_, String>(6)?,
        ))
    })?;

    let mut machines = Vec::new();
    for row in rows {
        let (id, name, asset_tag, machine_type, location, status, created_at) = row?;
        let status = match status.as_str() {
            "Healthy" => MachineStatus::Healthy,
            "Attention" => MachineStatus::Attention,
            "Critical" => MachineStatus::Critical,
            _ => MachineStatus::Offline,
        };
        let id = Uuid::parse_str(&id).context("invalid machine UUID in local database")?;
        let created_at = DateTime::parse_from_rfc3339(&created_at)
            .context("invalid machine creation timestamp in local database")?
            .with_timezone(&Utc);
        machines.push(Machine {
            id,
            name,
            asset_tag,
            machine_type,
            location,
            status,
            created_at,
        });
    }
    Ok(machines)
}

pub fn record_telemetry(
    database: &Connection,
    machine_id: &str,
    metric: &str,
    value: f64,
    unit: &str,
    source: &str,
    observed_at: Option<&str>,
) -> Result<TelemetryRecord> {
    let machine_id = Uuid::parse_str(machine_id).context("invalid machine ID")?;
    if !value.is_finite() {
        return Err(anyhow!("telemetry value must be finite"));
    }

    let observed_at = match observed_at {
        Some(value) if !value.trim().is_empty() => DateTime::parse_from_rfc3339(value)
            .context("observed_at must be an RFC3339 timestamp")?
            .with_timezone(&Utc),
        _ => Utc::now(),
    };
    let observed_at_text = observed_at.to_rfc3339();

    database.execute(
        "INSERT INTO telemetry_samples (machine_id, metric, value, unit, source, observed_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![
            machine_id.to_string(),
            metric,
            value,
            unit,
            source,
            observed_at_text,
        ],
    )?;

    let id = database.last_insert_rowid();
    database.execute(
        "INSERT INTO audit_events (actor, action, entity_type, entity_id, details_json)
         VALUES ('local-user', 'telemetry.recorded', 'machine', ?1, ?2)",
        params![
            machine_id.to_string(),
            serde_json::json!({
                "metric": metric,
                "value": value,
                "unit": unit,
                "source": source,
                "telemetry_id": id,
            }).to_string(),
        ],
    )?;

    Ok(TelemetryRecord {
        id,
        machine_id,
        metric: metric.to_string(),
        value,
        unit: unit.to_string(),
        source: source.to_string(),
        observed_at,
    })
}

pub fn list_telemetry(
    database: &Connection,
    machine_id: &str,
    metric: Option<&str>,
    limit: u32,
) -> Result<Vec<TelemetryRecord>> {
    let machine_id = Uuid::parse_str(machine_id).context("invalid machine ID")?;
    let limit = limit.clamp(1, 500);
    let mut statement = database.prepare(
        "SELECT id, machine_id, metric, value, unit, source, observed_at
         FROM telemetry_samples
         WHERE machine_id = ?1 AND (?2 IS NULL OR metric = ?2)
         ORDER BY observed_at DESC, id DESC
         LIMIT ?3"
    )?;
    let rows = statement.query_map(
        params![machine_id.to_string(), metric, limit],
        |row| {
            Ok((
                row.get::<_, i64>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, String>(2)?,
                row.get::<_, f64>(3)?,
                row.get::<_, String>(4)?,
                row.get::<_, String>(5)?,
                row.get::<_, String>(6)?,
            ))
        },
    )?;

    let mut records = Vec::new();
    for row in rows {
        let (id, machine_id, metric, value, unit, source, observed_at) = row?;
        records.push(TelemetryRecord {
            id,
            machine_id: Uuid::parse_str(&machine_id)
                .context("invalid machine UUID in telemetry store")?,
            metric,
            value,
            unit,
            source,
            observed_at: DateTime::parse_from_rfc3339(&observed_at)
                .context("invalid telemetry timestamp in local database")?
                .with_timezone(&Utc),
        });
    }
    Ok(records)
}

pub fn record_anomaly(database: &Connection, anomaly: &AnomalySignal) -> Result<()> {
    let severity = match anomaly.severity {
        Severity::Info => "info",
        Severity::Warning => "warning",
        Severity::Critical => "critical",
    };
    let machine_id = anomaly.machine_id.to_string();
    let observed_at = anomaly.observed_at.to_rfc3339();
    database.execute(
        "INSERT INTO anomaly_events (machine_id, metric, score, severity, explanation, observed_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![
            machine_id,
            anomaly.metric,
            anomaly.score,
            severity,
            anomaly.explanation,
            observed_at,
        ],
    )?;
    database.execute(
        "INSERT INTO audit_events (actor, action, entity_type, entity_id, details_json)
         VALUES ('local-analytics', 'telemetry.anomaly_detected', 'machine', ?1, ?2)",
        params![
            anomaly.machine_id.to_string(),
            serde_json::json!({
                "metric": anomaly.metric,
                "score": anomaly.score,
                "severity": severity,
                "explanation": anomaly.explanation,
            }).to_string(),
        ],
    )?;
    Ok(())
}

pub fn list_anomalies(
    database: &Connection,
    machine_id: &str,
    limit: u32,
) -> Result<Vec<AnomalySignal>> {
    let machine_id = Uuid::parse_str(machine_id).context("invalid machine ID")?;
    let limit = limit.clamp(1, 200);
    let mut statement = database.prepare(
        "SELECT machine_id, metric, score, severity, explanation, observed_at
         FROM anomaly_events
         WHERE machine_id = ?1
         ORDER BY observed_at DESC, id DESC
         LIMIT ?2"
    )?;
    let rows = statement.query_map(params![machine_id.to_string(), limit], |row| {
        Ok((
            row.get::<_, String>(0)?,
            row.get::<_, String>(1)?,
            row.get::<_, f64>(2)?,
            row.get::<_, String>(3)?,
            row.get::<_, String>(4)?,
            row.get::<_, String>(5)?,
        ))
    })?;
    let mut anomalies = Vec::new();
    for row in rows {
        let (machine_id, metric, score, severity, explanation, observed_at) = row?;
        let severity = match severity.as_str() {
            "critical" => Severity::Critical,
            "warning" => Severity::Warning,
            _ => Severity::Info,
        };
        anomalies.push(AnomalySignal {
            machine_id: Uuid::parse_str(&machine_id)
                .context("invalid machine UUID in anomaly store")?,
            metric,
            score,
            severity,
            explanation,
            observed_at: DateTime::parse_from_rfc3339(&observed_at)
                .context("invalid anomaly timestamp in local database")?
                .with_timezone(&Utc),
        });
    }
    Ok(anomalies)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_database() -> Connection {
        let database = Connection::open_in_memory().unwrap();
        database.pragma_update(None, "foreign_keys", "ON").unwrap();
        migrate(&database).unwrap();
        database
    }

    fn test_machine() -> Machine {
        Machine::new("Pump 01", "P-001", "Centrifugal pump")
    }

    #[test]
    fn creates_initial_schema_and_telemetry_migration() {
        let database = test_database();
        let versions: i64 = database.query_row(
            "SELECT COUNT(*) FROM schema_migrations WHERE version IN (1, 2)",
            [],
            |row| row.get(0),
        ).unwrap();
        assert_eq!(versions, 2);
    }

    #[test]
    fn upgrades_existing_v1_telemetry_table_without_losing_readings() {
        let database = Connection::open_in_memory().unwrap();
        database.execute_batch(
            "
            CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE machines (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, asset_tag TEXT NOT NULL UNIQUE,
                machine_type TEXT NOT NULL, location TEXT, status TEXT NOT NULL, created_at TEXT NOT NULL
            );
            CREATE TABLE audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor TEXT NOT NULL,
                action TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT,
                details_json TEXT NOT NULL DEFAULT '{}',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE telemetry_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                machine_id TEXT NOT NULL REFERENCES machines(id),
                metric TEXT NOT NULL, value REAL NOT NULL, unit TEXT NOT NULL, observed_at TEXT NOT NULL
            );
            INSERT INTO schema_migrations(version) VALUES (1);
            "
        ).unwrap();
        let machine = test_machine();
        insert_machine(&database, &machine).unwrap();
        database.execute(
            "INSERT INTO telemetry_samples(machine_id, metric, value, unit, observed_at)
             VALUES (?1, 'temperature', 41.0, '°C', '2026-11-16T10:00:00+00:00')",
            [machine.id.to_string()],
        ).unwrap();

        migrate(&database).unwrap();
        let source: String = database.query_row(
            "SELECT source FROM telemetry_samples WHERE metric = 'temperature'",
            [],
            |row| row.get(0),
        ).unwrap();
        assert_eq!(source, "manual");
        migrate(&database).unwrap();
    }

    #[test]
    fn persists_and_lists_machine() {
        let database = test_database();
        let machine = test_machine();
        insert_machine(&database, &machine).unwrap();
        let machines = list_machines(&database).unwrap();
        assert_eq!(machines.len(), 1);
        assert_eq!(machines[0].asset_tag, "P-001");
        assert_eq!(machines[0].status, MachineStatus::Offline);
    }

    #[test]
    fn asset_tags_are_unique() {
        let database = test_database();
        let first = test_machine();
        let second = Machine::new("Pump 02", "P-001", "Pump");
        insert_machine(&database, &first).unwrap();
        assert!(insert_machine(&database, &second).is_err());
    }

    #[test]
    fn records_and_lists_telemetry_with_source_and_order() {
        let database = test_database();
        let machine = test_machine();
        insert_machine(&database, &machine).unwrap();

        let older = record_telemetry(
            &database, &machine.id.to_string(), "temperature", 38.5, "°C", "sensor",
            Some("2026-11-16T10:00:00Z"),
        ).unwrap();
        let newer = record_telemetry(
            &database, &machine.id.to_string(), "temperature", 42.5, "°C", "manual",
            Some("2026-11-16T10:05:00Z"),
        ).unwrap();

        assert!(newer.id > older.id);
        let records = list_telemetry(&database, &machine.id.to_string(), Some("temperature"), 20).unwrap();
        assert_eq!(records.len(), 2);
        assert_eq!(records[0].value, 42.5);
        assert_eq!(records[0].source, "manual");
        assert_eq!(records[1].value, 38.5);
    }

    #[test]
    fn persists_and_lists_anomaly_events() {
        let database = test_database();
        let machine = test_machine();
        insert_machine(&database, &machine).unwrap();
        let anomaly = AnomalySignal {
            machine_id: machine.id,
            metric: "temperature".into(),
            score: 8.2,
            severity: Severity::Critical,
            explanation: "temperature is outside its learned baseline".into(),
            observed_at: DateTime::parse_from_rfc3339("2026-11-18T08:00:00Z")
                .unwrap()
                .with_timezone(&Utc),
        };
        record_anomaly(&database, &anomaly).unwrap();
        let anomalies = list_anomalies(&database, &machine.id.to_string(), 20).unwrap();
        assert_eq!(anomalies.len(), 1);
        assert_eq!(anomalies[0].severity, Severity::Critical);
        assert_eq!(anomalies[0].score, 8.2);
    }

    #[test]
    fn rejects_non_finite_telemetry_values() {
        let database = test_database();
        let machine = test_machine();
        insert_machine(&database, &machine).unwrap();
        assert!(record_telemetry(
            &database, &machine.id.to_string(), "temperature", f64::NAN, "°C", "manual", None,
        ).is_err());
    }

    #[test]
    fn rejects_telemetry_for_unknown_machine() {
        let database = test_database();
        assert!(record_telemetry(
            &database,
            "b9f2f45e-2f18-42d9-9d27-4d90d3b1a2f5",
            "temperature",
            42.5,
            "°C",
            "manual",
            None,
        ).is_err());
    }
}
