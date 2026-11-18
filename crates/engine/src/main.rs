use anyhow::{Context, Result};
use maintain_app_core::{Machine, TelemetrySample};
use maintain_analytics::LocalAnalytics;
use maintain_protocol::{EngineRequest, EngineResponse, IpcEnvelope, PROTOCOL_VERSION};
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tracing::{error, info};

static LOCAL_ANALYTICS: OnceLock<Mutex<LocalAnalytics>> = OnceLock::new();

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(std::env::var("RUST_LOG").unwrap_or_else(|_| "info".into()))
        .init();

    let data = data_dir()?;
    std::fs::create_dir_all(&data)?;
    let database = data.join("maintain-native.sqlite3");
    let connection = maintain_persistence::open_database(&database)
        .with_context(|| format!("failed to initialize {}", database.display()))?;
    let seed_history = maintain_persistence::recent_telemetry_for_analytics(&connection, 200)
        .context("failed to load recent telemetry for anomaly baseline warm-up")?;
    let mut analytics = LocalAnalytics::default();
    for sample in &seed_history {
        let _ = analytics.inspect(sample);
    }
    let _ = LOCAL_ANALYTICS.set(Mutex::new(analytics));
    info!(
        samples = seed_history.len(),
        "adaptive anomaly detector restored from local telemetry history"
    );
    drop(connection);

    #[cfg(unix)]
    run_unix(data.join("engine.sock"), database).await?;
    #[cfg(windows)]
    run_windows(database).await?;
    Ok(())
}

fn data_dir() -> Result<PathBuf> {
    if let Some(path) = std::env::var_os("MAINTAIN_DATA_DIR") {
        return Ok(PathBuf::from(path));
    }
    Ok(dirs::data_local_dir()
        .context("cannot locate local data directory")?
        .join("DMJ Group")
        .join("MAINTAIN AI Native"))
}

fn handle_request(request: EngineRequest, database: &Path) -> EngineResponse {
    match request {
        EngineRequest::Ping => EngineResponse::Pong {
            protocol_version: PROTOCOL_VERSION,
            engine: "MAINTAIN AI Native Rust Engine".into(),
        },
        EngineRequest::GetSystemStatus => EngineResponse::SystemStatus {
            ready: true,
            database: "sqlite".into(),
        },
        EngineRequest::ListMachines => {
            match maintain_persistence::open_database(database)
                .and_then(|connection| maintain_persistence::list_machines(&connection))
            {
                Ok(machines) => EngineResponse::MachinesListed { machines },
                Err(error) => EngineResponse::Error {
                    code: "storage_error".into(),
                    message: error.to_string(),
                },
            }
        }
        EngineRequest::CreateMachine { name, asset_tag, machine_type, location } => {
            let name = name.trim().to_string();
            let asset_tag = asset_tag.trim().to_string();
            let machine_type = machine_type.trim().to_string();
            if name.is_empty() || asset_tag.is_empty() || machine_type.is_empty() {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "Name, asset tag, and machine type are required.".into(),
                };
            }
            if name.len() > 120 || asset_tag.len() > 80 || machine_type.len() > 120 {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "A machine field exceeds its maximum length.".into(),
                };
            }

            let mut machine = Machine::new(name, asset_tag, machine_type);
            machine.location = location
                .map(|value| value.trim().to_string())
                .filter(|value| !value.is_empty());

            let result = maintain_persistence::open_database(database)
                .and_then(|connection| {
                    maintain_persistence::insert_machine(&connection, &machine)
                });
            match result {
                Ok(()) => EngineResponse::MachineCreated { machine },
                Err(error) => {
                    let message = if error.to_string().contains("UNIQUE constraint failed") {
                        "An asset with this tag already exists.".to_string()
                    } else {
                        error.to_string()
                    };
                    EngineResponse::Error {
                        code: "machine_create_failed".into(),
                        message,
                    }
                }
            }
        }

        EngineRequest::RecordTelemetry {
            machine_id,
            metric,
            value,
            unit,
            source,
            observed_at,
        } => {
            let metric = metric.trim().to_string();
            let unit = unit.trim().to_string();
            let source = source.trim().to_ascii_lowercase();
            if metric.is_empty() || metric.len() > 80 {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "Metric name is required and must be at most 80 characters.".into(),
                };
            }
            if unit.len() > 24 {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "Unit must be at most 24 characters.".into(),
                };
            }
            if !value.is_finite() {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "Telemetry value must be a finite number.".into(),
                };
            }
            if !["manual", "sensor", "import"].contains(&source.as_str()) {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "Telemetry source must be manual, sensor, or import.".into(),
                };
            }

            let result = maintain_persistence::open_database(database).and_then(|connection| {
                maintain_persistence::record_telemetry(
                    &connection,
                    &machine_id,
                    &metric,
                    value,
                    &unit,
                    &source,
                    observed_at.as_deref(),
                )
            });
            match result {
                Ok(record) => {
                    // The detector is process-local but keyed by machine and metric.
                    // Telemetry remains durably stored even if anomaly-event persistence fails.
                    let sample = TelemetrySample {
                        machine_id: record.machine_id,
                        metric: record.metric.clone(),
                        value: record.value,
                        unit: record.unit.clone(),
                        observed_at: record.observed_at.clone(),
                    };
                    let anomaly = {
                        let state = LOCAL_ANALYTICS
                            .get_or_init(|| Mutex::new(LocalAnalytics::default()));
                        let mut analytics = state.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
                        analytics.inspect(&sample)
                    };
                    if let Some(ref signal) = anomaly {
                        if let Err(error) = maintain_persistence::open_database(database)
                            .and_then(|connection| maintain_persistence::record_anomaly(&connection, signal))
                        {
                            error!(error = %error, "telemetry persisted but anomaly event could not be stored");
                        }
                    }
                    EngineResponse::TelemetryRecorded { record, anomaly }
                }
                Err(error) => EngineResponse::Error {
                    code: "telemetry_record_failed".into(),
                    message: error.to_string(),
                },
            }
        }
        EngineRequest::ListTelemetry {
            machine_id,
            metric,
            limit,
        } => {
            let metric = metric
                .map(|value| value.trim().to_string())
                .filter(|value| !value.is_empty());
            if metric.as_ref().is_some_and(|value| value.len() > 80) {
                return EngineResponse::Error {
                    code: "validation_error".into(),
                    message: "Metric filter must be at most 80 characters.".into(),
                };
            }
            let result = maintain_persistence::open_database(database).and_then(|connection| {
                maintain_persistence::list_telemetry(
                    &connection,
                    &machine_id,
                    metric.as_deref(),
                    limit,
                )
            });
            match result {
                Ok(records) => EngineResponse::TelemetryListed { records },
                Err(error) => EngineResponse::Error {
                    code: "telemetry_list_failed".into(),
                    message: error.to_string(),
                },
            }
        }
        EngineRequest::ListAnomalies { machine_id, limit } => {
            let result = maintain_persistence::open_database(database).and_then(|connection| {
                maintain_persistence::list_anomalies(&connection, &machine_id, limit)
            });
            match result {
                Ok(anomalies) => EngineResponse::AnomaliesListed { anomalies },
                Err(error) => EngineResponse::Error {
                    code: "anomaly_list_failed".into(),
                    message: error.to_string(),
                },
            }
        }
    }
}

#[cfg(unix)]
async fn run_unix(path: PathBuf, database: PathBuf) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    use tokio::net::UnixListener;

    if path.exists() {
        std::fs::remove_file(&path)?;
    }
    let listener = UnixListener::bind(&path)?;
    std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600))?;
    info!(socket = %path.display(), "local IPC ready");

    loop {
        let (stream, _) = listener.accept().await?;
        let database = database.clone();
        tokio::spawn(async move {
            let result = async {
                let (reader, mut writer) = stream.into_split();
                let mut lines = BufReader::new(reader).lines();
                while let Some(line) = lines.next_line().await? {
                    if line.len() > 65_536 {
                        return Err(anyhow::anyhow!("IPC request exceeds 64 KiB"));
                    }
                    let request: IpcEnvelope<EngineRequest> = serde_json::from_str(&line)?;
                    if request.protocol_version != PROTOCOL_VERSION {
                        return Err(anyhow::anyhow!("unsupported IPC protocol version"));
                    }
                    let response = IpcEnvelope::new(
                        request.request_id,
                        handle_request(request.message, &database),
                    );
                    writer.write_all(serde_json::to_string(&response)?.as_bytes()).await?;
                    writer.write_all(b"\n").await?;
                }
                Ok::<(), anyhow::Error>(())
            }.await;
            if let Err(error) = result {
                error!(error = %error, "IPC connection ended");
            }
        });
    }
}

#[cfg(windows)]
async fn run_windows(database: PathBuf) -> Result<()> {
    use tokio::net::windows::named_pipe::ServerOptions;

    let name = r"\\.\pipe\maintain-ai-native";
    info!(pipe = name, "local IPC ready");
    loop {
        let server = ServerOptions::new().create(name)?;
        server.connect().await?;
        let database = database.clone();
        tokio::spawn(async move {
            let mut stream = server;
            let result = async {
                let mut reader = BufReader::new(&mut stream);
                let mut line = String::new();
                while reader.read_line(&mut line).await? > 0 {
                    if line.len() > 65_536 {
                        return Err(anyhow::anyhow!("IPC request exceeds 64 KiB"));
                    }
                    let request: IpcEnvelope<EngineRequest> = serde_json::from_str(&line)?;
                    if request.protocol_version != PROTOCOL_VERSION {
                        return Err(anyhow::anyhow!("unsupported IPC protocol version"));
                    }
                    let response = IpcEnvelope::new(
                        request.request_id,
                        handle_request(request.message, &database),
                    );
                    reader.get_mut().write_all(&serde_json::to_vec(&response)?).await?;
                    reader.get_mut().write_all(b"\n").await?;
                    line.clear();
                }
                Ok::<(), anyhow::Error>(())
            }.await;
            if let Err(error) = result {
                error!(error = %error, "named pipe client ended");
            }
        });
    }
}
