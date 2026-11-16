//! SQLite bootstrap, migrations, and initial machine registry repository.
use anyhow::{Context, Result};
use chrono::{DateTime, Utc};
use maintain_app_core::{Machine, MachineStatus};
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
            observed_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_telemetry_machine_metric_time
            ON telemetry_samples(machine_id, metric, observed_at);

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn creates_initial_schema() {
        let database = Connection::open_in_memory().unwrap();
        migrate(&database).unwrap();
        let count: i64 = database.query_row(
            "SELECT COUNT(*) FROM schema_migrations WHERE version = 1",
            [],
            |row| row.get(0),
        ).unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn persists_and_lists_machine() {
        let database = Connection::open_in_memory().unwrap();
        migrate(&database).unwrap();
        let machine = Machine::new("Pump 01", "P-001", "Centrifugal pump");
        insert_machine(&database, &machine).unwrap();
        let machines = list_machines(&database).unwrap();
        assert_eq!(machines.len(), 1);
        assert_eq!(machines[0].asset_tag, "P-001");
        assert_eq!(machines[0].status, MachineStatus::Offline);
    }

    #[test]
    fn asset_tags_are_unique() {
        let database = Connection::open_in_memory().unwrap();
        migrate(&database).unwrap();
        let first = Machine::new("Pump 01", "P-001", "Pump");
        let second = Machine::new("Pump 02", "P-001", "Pump");
        insert_machine(&database, &first).unwrap();
        assert!(insert_machine(&database, &second).is_err());
    }
}
