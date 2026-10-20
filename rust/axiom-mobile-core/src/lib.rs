//! Axiom Mobile Control plane (local): tasks, capabilities, policy, queue, evidence, workspace.
//! Does NOT know about Android UI. Android is an adapter.

pub mod capability;
pub mod evidence;
pub mod observation;
pub mod policy;
pub mod queue;
pub mod task;
pub mod verification;
pub mod workspace;

pub use capability::{Capability, CapabilityGrant};
pub use evidence::Evidence;
pub use observation::Observation;
pub use policy::{LocalPolicy, PolicyDecision};
pub use queue::{OfflineQueue, TaskState};
pub use task::Task;
pub use verification::VerificationResult;
pub use workspace::{
    m05_capability_status, CapabilityStatus, Workspace, WorkspacePermission, WorkspaceProvider,
    WorkspaceStatus,
};

/// Runner identity advertised to Control Plane / UI.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct RunnerIdentity {
    pub runner_id: String,
    pub runner_version: String,
    pub abi: String,
    pub isolation_level: String,
    /// Declared only — Lead: IMPLEMENTED ≠ VERIFIED
    pub capabilities_declared: Vec<String>,
    pub capabilities_status: Vec<CapabilityStatus>,
}

impl RunnerIdentity {
    pub fn karl_phone_v0() -> Self {
        Self {
            runner_id: "runner-karl-phone".into(),
            runner_version: env!("CARGO_PKG_VERSION").into(),
            abi: "arm64-v8a".into(),
            isolation_level: "PATH_ONLY".into(),
            capabilities_declared: Capability::v0_list()
                .into_iter()
                .map(|c| c.as_str().to_string())
                .collect(),
            capabilities_status: m05_capability_status(),
        }
    }
}
