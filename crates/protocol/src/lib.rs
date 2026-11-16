//! Versioned IPC envelope; Protobuf schema is the cross-language target.
use serde::{Deserialize,Serialize};
pub const PROTOCOL_VERSION:u32=1;
#[derive(Debug,Clone,Serialize,Deserialize)]#[serde(tag="kind",content="payload",rename_all="snake_case")]pub enum EngineRequest{Ping,GetSystemStatus}
#[derive(Debug,Clone,Serialize,Deserialize)]#[serde(tag="kind",content="payload",rename_all="snake_case")]pub enum EngineResponse{Pong{protocol_version:u32,engine:String},SystemStatus{ready:bool,database:String},Error{code:String,message:String}}
#[derive(Debug,Clone,Serialize,Deserialize)]pub struct IpcEnvelope<T>{pub request_id:String,pub protocol_version:u32,pub message:T}
impl<T>IpcEnvelope<T>{pub fn new(request_id:impl Into<String>,message:T)->Self{Self{request_id:request_id.into(),protocol_version:PROTOCOL_VERSION,message}}}
#[cfg(test)]mod tests{use super::*;#[test]fn envelope_roundtrip(){let e=IpcEnvelope::new("id-1",EngineRequest::Ping);let s=serde_json::to_string(&e).unwrap();let d:IpcEnvelope<EngineRequest>=serde_json::from_str(&s).unwrap();assert_eq!(d.protocol_version,PROTOCOL_VERSION);}}
