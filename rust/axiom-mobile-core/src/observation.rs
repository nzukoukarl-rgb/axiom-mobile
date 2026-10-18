use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Observation {
    pub observation_id: String,
    pub action_id: String,
    pub capability: String,
    pub target: String,
    pub timestamp: String,
    pub payload: serde_json::Value,
    pub content_is_data: bool,
}
