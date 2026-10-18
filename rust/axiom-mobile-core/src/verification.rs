use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VerificationResult {
    pub ok: bool,
    pub checks: Vec<String>,
    pub failures: Vec<String>,
}
