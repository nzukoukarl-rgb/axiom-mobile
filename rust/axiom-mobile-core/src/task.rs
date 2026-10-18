use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Task {
    pub id: String,
    pub created_at: String,
    pub state: crate::queue::TaskState,
    pub intent: String,
    pub context: serde_json::Value,
    pub required_capabilities: Vec<String>,
    pub network_requirement: NetworkRequirement,
    pub payload: serde_json::Value,
    pub sync_state: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum NetworkRequirement {
    None,
    Preferred,
    Required,
}
