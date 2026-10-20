//! WorkspaceRegistry — M1.5 (Lead)
//! root_reference = grant token (SAF URI), never /sdcard path as identity.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum WorkspaceProvider {
    AppPrivate,
    AndroidSaf,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum WorkspaceStatus {
    Authorized,
    Revoked,
    Unavailable,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum WorkspacePermission {
    Read,
    Write,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Workspace {
    pub workspace_id: String,
    pub display_name: String,
    pub provider: WorkspaceProvider,
    pub root_reference: String,
    pub permissions: Vec<WorkspacePermission>,
    pub status: WorkspaceStatus,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapabilityStatus {
    pub capability: String,
    pub status: String,
}

impl Workspace {
    pub fn allows_read(&self) -> bool {
        self.status == WorkspaceStatus::Authorized
            && self
                .permissions
                .iter()
                .any(|p| matches!(p, WorkspacePermission::Read | WorkspacePermission::Write))
    }

    pub fn allows_write(&self) -> bool {
        self.status == WorkspaceStatus::Authorized
            && self
                .permissions
                .iter()
                .any(|p| matches!(p, WorkspacePermission::Write))
    }

    pub fn validate_relative_path(path: &str) -> Result<(), String> {
        if path.is_empty() {
            return Err("EMPTY_PATH".into());
        }
        if path.contains('\0') {
            return Err("NULL_BYTE".into());
        }
        if path.starts_with('/') || path.starts_with('\~') {
            return Err("ABSOLUTE_PATH_DENIED".into());
        }
        for part in path.replace('\\', "/").split('/') {
            if part == ".." {
                return Err("PATH_TRAVERSAL_DENIED".into());
            }
        }
        Ok(())
    }

    pub fn authorize_action(&self, capability: &str, path: Option<&str>) -> Result<(), String> {
        match self.status {
            WorkspaceStatus::Revoked => return Err("WORKSPACE_ACCESS_REVOKED".into()),
            WorkspaceStatus::Unavailable => return Err("WORKSPACE_UNAVAILABLE".into()),
            WorkspaceStatus::Authorized => {}
        }
        let needs_write = matches!(
            capability,
            "repo.edit" | "repo.worktree.create" | "repo.delete"
        );
        if needs_write && !self.allows_write() {
            return Err("PERMISSION_MISMATCH:WRITE_REQUIRED".into());
        }
        if !needs_write && !self.allows_read() {
            return Err("PERMISSION_MISMATCH:READ_REQUIRED".into());
        }
        if let Some(p) = path {
            Self::validate_relative_path(p)?;
        }
        Ok(())
    }
}

pub fn m05_capability_status() -> Vec<CapabilityStatus> {
    vec![
        CapabilityStatus { capability: "repo.list".into(), status: "observed".into() },
        CapabilityStatus { capability: "repo.read".into(), status: "observed".into() },
        CapabilityStatus { capability: "repo.grep".into(), status: "declared".into() },
        CapabilityStatus { capability: "repo.status".into(), status: "declared".into() },
        CapabilityStatus { capability: "repo.worktree.create".into(), status: "declared".into() },
        CapabilityStatus { capability: "repo.edit".into(), status: "declared".into() },
        CapabilityStatus { capability: "repo.diff".into(), status: "declared".into() },
        CapabilityStatus { capability: "repo.verify".into(), status: "declared".into() },
    ]
}
