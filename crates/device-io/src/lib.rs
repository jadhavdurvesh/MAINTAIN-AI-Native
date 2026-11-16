//! Device integration interfaces.
use async_trait::async_trait;
use thiserror::Error;
#[derive(Debug,Error)] pub enum DeviceError { #[error("adapter not implemented: {0}")] NotImplemented(String), #[error("transport error: {0}")] Transport(String) }
#[async_trait] pub trait TelemetrySource:Send+Sync { async fn start(&self)->Result<(),DeviceError>; async fn stop(&self)->Result<(),DeviceError>; }
#[derive(Debug,Clone,Copy,PartialEq,Eq)] pub enum TransportKind { Serial, Mqtt, ModbusTcp }
