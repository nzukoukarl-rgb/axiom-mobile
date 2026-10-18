use serde::{Deserialize, Serialize};

/// V0 allowlist — Lead §9. Shell/deploy/push forbidden.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Capability {
    RepoStatus,
    RepoRead,
    RepoGrep,
    RepoList,
    RepoWorktreeCreate,
    RepoEdit,
    RepoDiff,
    RepoVerify,
}

impl Capability {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::RepoStatus => "repo.status",
            Self::RepoRead => "repo.read",
            Self::RepoGrep => "repo.grep",
            Self::RepoList => "repo.list",
            Self::RepoWorktreeCreate => "repo.worktree.create",
            Self::RepoEdit => "repo.edit",
            Self::RepoDiff => "repo.diff",
            Self::RepoVerify => "repo.verify",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "repo.status" => Some(Self::RepoStatus),
            "repo.read" => Some(Self::RepoRead),
            "repo.grep" => Some(Self::RepoGrep),
            "repo.list" => Some(Self::RepoList),
            "repo.worktree.create" => Some(Self::RepoWorktreeCreate),
            "repo.edit" => Some(Self::RepoEdit),
            "repo.diff" => Some(Self::RepoDiff),
            "repo.verify" => Some(Self::RepoVerify),
            _ => None,
        }
    }

    pub fn v0_list() -> Vec<Self> {
        vec![
            Self::RepoStatus,
            Self::RepoRead,
            Self::RepoGrep,
            Self::RepoList,
            Self::RepoWorktreeCreate,
            Self::RepoEdit,
            Self::RepoDiff,
            Self::RepoVerify,
        ]
    }

    pub fn requires_approval(self) -> bool {
        matches!(self, Self::RepoEdit | Self::RepoWorktreeCreate)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapabilityGrant {
    pub capability: String,
    pub workspace_id: String,
    pub granted_at: String,
    pub expires_at: Option<String>,
}
