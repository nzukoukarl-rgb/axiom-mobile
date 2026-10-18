use crate::capability::Capability;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionEnvelope {
    pub action_id: String,
    pub capability: String,
    pub workspace_id: String,
    pub path: Option<String>,
    pub query: Option<String>,
    pub content: Option<String>,
    pub approval: ApprovalState,
    pub expected_runner_id: String,
    pub expires_at: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ApprovalState {
    None,
    Pending,
    Approved,
    Refused,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum PolicyDecision {
    Allow,
    Deny { reason: String },
}

pub struct LocalPolicy {
    pub runner_id: String,
    pub workspace_root_marker: String,
}

impl LocalPolicy {
    pub fn new(runner_id: impl Into<String>) -> Self {
        Self {
            runner_id: runner_id.into(),
            workspace_root_marker: ".axiom-workspace".into(),
        }
    }

    pub fn authorize(&self, env: &ActionEnvelope) -> PolicyDecision {
        if env.expected_runner_id != self.runner_id {
            return PolicyDecision::Deny {
                reason: format!(
                    "DENY_RUNNER_SUBSTITUTION: expected {}, this is {}",
                    env.expected_runner_id, self.runner_id
                ),
            };
        }

        let Some(cap) = Capability::parse(&env.capability) else {
            return PolicyDecision::Deny {
                reason: format!("UNKNOWN_CAPABILITY: {}", env.capability),
            };
        };

        // Forbidden capabilities (explicit)
        let forbidden = [
            "repo.shell",
            "repo.deploy",
            "repo.push",
            "repo.publish",
            "repo.delete",
            "repo.reset",
            "repo.install",
            "repo.network",
        ];
        if forbidden.iter().any(|f| *f == env.capability.as_str()) {
            return PolicyDecision::Deny {
                reason: format!("CAPABILITY_FORBIDDEN: {}", env.capability),
            };
        }

        if cap.requires_approval() && env.approval != ApprovalState::Approved {
            return PolicyDecision::Deny {
                reason: "APPROVAL_REQUIRED".into(),
            };
        }

        if let Some(path) = &env.path {
            if let Err(e) = validate_path(path) {
                return PolicyDecision::Deny { reason: e };
            }
        }

        PolicyDecision::Allow
    }
}

fn validate_path(path: &str) -> Result<(), String> {
    if path.is_empty() {
        return Err("EMPTY_PATH".into());
    }
    if path.contains('\0') {
        return Err("NULL_BYTE".into());
    }
    // Path traversal
    for part in path.replace('\\', "/").split('/') {
        if part == ".." {
            return Err("PATH_TRAVERSAL_DENIED".into());
        }
    }
    if path.starts_with('/') || path.starts_with('~') {
        // Absolute host paths not allowed; workspace-relative only
        return Err("ABSOLUTE_PATH_DENIED".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn denies_traversal() {
        let p = LocalPolicy::new("runner-karl-phone");
        let env = ActionEnvelope {
            action_id: "a1".into(),
            capability: "repo.read".into(),
            workspace_id: "gloria".into(),
            path: Some("../../etc/passwd".into()),
            query: None,
            content: None,
            approval: ApprovalState::None,
            expected_runner_id: "runner-karl-phone".into(),
            expires_at: None,
        };
        match p.authorize(&env) {
            PolicyDecision::Deny { reason } => assert!(reason.contains("TRAVERSAL")),
            _ => panic!("expected deny"),
        }
    }

    #[test]
    fn denies_edit_without_approval() {
        let p = LocalPolicy::new("runner-karl-phone");
        let env = ActionEnvelope {
            action_id: "a2".into(),
            capability: "repo.edit".into(),
            workspace_id: "gloria".into(),
            path: Some("src/a.js".into()),
            query: None,
            content: Some("x".into()),
            approval: ApprovalState::None,
            expected_runner_id: "runner-karl-phone".into(),
            expires_at: None,
        };
        match p.authorize(&env) {
            PolicyDecision::Deny { reason } => assert_eq!(reason, "APPROVAL_REQUIRED"),
            _ => panic!("expected deny"),
        }
    }
}
