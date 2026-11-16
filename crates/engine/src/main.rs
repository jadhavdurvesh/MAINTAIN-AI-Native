use anyhow::{Context, Result};
use maintain_app_core::Machine;
use maintain_protocol::{EngineRequest, EngineResponse, IpcEnvelope, PROTOCOL_VERSION};
use std::path::{Path, PathBuf};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tracing::{error, info};

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(std::env::var("RUST_LOG").unwrap_or_else(|_| "info".into()))
        .init();

    let data = data_dir()?;
    std::fs::create_dir_all(&data)?;
    let database = data.join("maintain-native.sqlite3");
    maintain_persistence::open_database(&database)
        .with_context(|| format!("failed to initialize {}", database.display()))?;

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
