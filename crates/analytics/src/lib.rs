//! Local adaptive statistical detector; no cloud or language model needed.
use maintain_app_core::{AnomalySignal, Severity, TelemetrySample};
use std::collections::HashMap;
#[derive(Debug, Clone)] pub struct EwmaDetector { alpha:f64, mean:Option<f64>, variance:f64, warmup:usize, observations:usize }
impl EwmaDetector {
 pub fn new(alpha:f64,warmup:usize)->Self { Self { alpha:alpha.clamp(0.001,1.0),mean:None,variance:0.0,warmup:warmup.max(2),observations:0 } }
 pub fn update(&mut self,value:f64)->Option<f64> { if !value.is_finite(){return None;} self.observations+=1; match self.mean {
 None=>{self.mean=Some(value);None}, Some(mean)=>{let d=value-mean;self.mean=Some(mean+self.alpha*d);self.variance=(1.0-self.alpha)*(self.variance+self.alpha*d*d);if self.observations<self.warmup{None}else{Some(d.abs()/self.variance.sqrt().max(1e-9))}} } }
}
#[derive(Default)] pub struct LocalAnalytics { detectors:HashMap<(String,String),EwmaDetector> }
impl LocalAnalytics { pub fn inspect(&mut self,s:&TelemetrySample)->Option<AnomalySignal>{let key=(s.machine_id.to_string(),s.metric.clone());let d=self.detectors.entry(key).or_insert_with(||EwmaDetector::new(0.12,12));let score=d.update(s.value)?;if score<3.0{return None;}let severity=if score>=6.0{Severity::Critical}else{Severity::Warning};Some(AnomalySignal{machine_id:s.machine_id,metric:s.metric.clone(),score,severity,explanation:format!("{} is {:.1} standard deviations from its adaptive baseline",s.metric,score),observed_at:s.observed_at})} }
#[cfg(test)] mod tests {use super::*;#[test]fn rejects_nan(){let mut d=EwmaDetector::new(0.1,3);assert_eq!(d.update(f64::NAN),None);}#[test]fn flags_shift(){let mut d=EwmaDetector::new(0.1,5);for _ in 0..20{d.update(10.0);}assert!(d.update(100.0).is_some_and(|x|x>3.0));}}
