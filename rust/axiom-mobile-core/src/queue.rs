use crate::task::Task;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TaskState {
    LocalCreated,
    LocalReady,
    WaitingNetwork,
    Syncing,
    RemoteAccepted,
    Executing,
    Completed,
    Failed,
    Conflict,
}

#[derive(Debug, Default)]
pub struct OfflineQueue {
    tasks: VecDeque<Task>,
}

impl OfflineQueue {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn enqueue(&mut self, mut task: Task) {
        if task.network_requirement == crate::task::NetworkRequirement::Required {
            task.state = TaskState::WaitingNetwork;
        } else {
            task.state = TaskState::LocalReady;
        }
        self.tasks.push_back(task);
    }

    pub fn len(&self) -> usize {
        self.tasks.len()
    }

    pub fn pending_local(&self) -> impl Iterator<Item = &Task> {
        self.tasks
            .iter()
            .filter(|t| matches!(t.state, TaskState::LocalReady | TaskState::LocalCreated))
    }

    pub fn waiting_network(&self) -> impl Iterator<Item = &Task> {
        self.tasks
            .iter()
            .filter(|t| t.state == TaskState::WaitingNetwork)
    }

    pub fn mark_completed(&mut self, id: &str) {
        if let Some(t) = self.tasks.iter_mut().find(|t| t.id == id) {
            t.state = TaskState::Completed;
        }
    }
}
