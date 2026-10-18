use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Evidence {
    pub id: String,
    pub action_id: String,
    pub observation_id: String,
    pub timestamp: String,
    pub target: String,
    pub result_summary: String,
    pub hash: String,
    pub limitations: Vec<String>,
    pub runner_id: String,
}

impl Evidence {
    pub fn build(
        action_id: &str,
        observation_id: &str,
        target: &str,
        result_summary: &str,
        runner_id: &str,
        limitations: Vec<String>,
    ) -> Self {
        let timestamp = chrono::Utc::now().to_rfc3339();
        let id = format!("E-{}", &uuid::Uuid::new_v4().to_string()[..8]);
        let mut hasher = Sha256::new();
        hasher.update(action_id.as_bytes());
        hasher.update(observation_id.as_bytes());
        hasher.update(target.as_bytes());
        hasher.update(result_summary.as_bytes());
        hasher.update(timestamp.as_bytes());
        let hash = hex::encode(hasher.finalize());
        Self {
            id,
            action_id: action_id.into(),
            observation_id: observation_id.into(),
            timestamp,
            target: target.into(),
            result_summary: result_summary.into(),
            hash,
            limitations,
            runner_id: runner_id.into(),
        }
    }
}
