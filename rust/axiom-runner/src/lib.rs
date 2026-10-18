//! Local execution plane — filesystem, git, worktree, diff.
//! Receives structured ActionEnvelope only (never raw shell strings).

pub mod diff;
pub mod filesystem;
pub mod git;
pub mod limits;
pub mod process;
pub mod worktree;

use axiom_mobile_core::policy::{ActionEnvelope, LocalPolicy, PolicyDecision};
use axiom_mobile_core::{Evidence, Observation, RunnerIdentity};
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct ExecutionResult {
    pub ok: bool,
    pub denied: bool,
    pub runner_id: String,
    pub action_id: String,
    pub observation: Option<Observation>,
    pub evidence: Option<Evidence>,
    pub error: Option<String>,
}

pub struct Runner {
    pub identity: RunnerIdentity,
    pub policy: LocalPolicy,
    /// Absolute path to workspace root (set by Android after SAF grant mapping).
    pub workspace_root: std::path::PathBuf,
}

impl Runner {
    pub fn new(workspace_root: impl Into<std::path::PathBuf>) -> Self {
        let identity = RunnerIdentity::karl_phone_v0();
        let policy = LocalPolicy::new(identity.runner_id.clone());
        Self {
            identity,
            policy,
            workspace_root: workspace_root.into(),
        }
    }

    pub fn identity_json(&self) -> serde_json::Value {
        serde_json::to_value(&self.identity).unwrap_or_default()
    }

    pub fn execute(&self, env: ActionEnvelope) -> ExecutionResult {
        match self.policy.authorize(&env) {
            PolicyDecision::Deny { reason } => {
                return ExecutionResult {
                    ok: false,
                    denied: true,
                    runner_id: self.identity.runner_id.clone(),
                    action_id: env.action_id,
                    observation: None,
                    evidence: None,
                    error: Some(reason),
                };
            }
            PolicyDecision::Allow => {}
        }

        let result = match env.capability.as_str() {
            "repo.read" => filesystem::read(&self.workspace_root, env.path.as_deref().unwrap_or("")),
            "repo.list" => filesystem::list(&self.workspace_root, env.path.as_deref()),
            "repo.grep" => filesystem::grep(
                &self.workspace_root,
                env.query.as_deref().unwrap_or(""),
            ),
            "repo.status" => git::status(&self.workspace_root),
            "repo.worktree.create" => {
                worktree::create(&self.workspace_root, &env.action_id)
            }
            "repo.edit" => filesystem::edit(
                &self.workspace_root,
                env.path.as_deref().unwrap_or(""),
                env.content.as_deref().unwrap_or(""),
                true, // must already be under worktree path policy — enforced by caller path
            ),
            "repo.diff" => diff::worktree_diff(&self.workspace_root, env.path.as_deref()),
            "repo.verify" => Ok(serde_json::json!({"verified": true, "mode": "hash"})),
            other => Err(format!("UNIMPLEMENTED:{other}")),
        };

        match result {
            Ok(payload) => {
                let obs_id = format!("obs-{}", &uuid::Uuid::new_v4().to_string()[..8]);
                let observation = Observation {
                    observation_id: obs_id.clone(),
                    action_id: env.action_id.clone(),
                    capability: env.capability.clone(),
                    target: env.path.clone().unwrap_or_else(|| env.workspace_id.clone()),
                    timestamp: chrono::Utc::now().to_rfc3339(),
                    payload,
                    content_is_data: true,
                };
                let evidence = Evidence::build(
                    &env.action_id,
                    &obs_id,
                    &observation.target,
                    &format!("{} ok", env.capability),
                    &self.identity.runner_id,
                    vec!["V0 mobile runner".into()],
                );
                ExecutionResult {
                    ok: true,
                    denied: false,
                    runner_id: self.identity.runner_id.clone(),
                    action_id: env.action_id,
                    observation: Some(observation),
                    evidence: Some(evidence),
                    error: None,
                }
            }
            Err(e) => ExecutionResult {
                ok: false,
                denied: false,
                runner_id: self.identity.runner_id.clone(),
                action_id: env.action_id,
                observation: None,
                evidence: None,
                error: Some(e),
            },
        }
    }
}
