use std::path::{Path, PathBuf};
use std::process::Command;

pub fn create(root: &Path, task_id: &str) -> Result<serde_json::Value, String> {
    let safe: String = task_id
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
        .take(48)
        .collect();
    let base = root.join(".axiom-worktrees");
    std::fs::create_dir_all(&base).map_err(|e| e.to_string())?;
    let wt = base.join(format!("task-{safe}"));
    if wt.exists() {
        return Ok(serde_json::json!({
            "worktreePath": wt.display().to_string(),
            "taskId": safe,
            "reused": true
        }));
    }
    // Prefer git worktree when repo
    let branch = format!("axiom/{safe}");
    let status = Command::new("git")
        .args(["worktree", "add", "-b", &branch, wt.to_str().unwrap()])
        .current_dir(root)
        .status();
    if status.map(|s| s.success()).unwrap_or(false) {
        return Ok(serde_json::json!({
            "worktreePath": wt.display().to_string(),
            "taskId": safe,
            "method": "git_worktree"
        }));
    }
    // Fallback: directory copy marker (non-git workspace)
    std::fs::create_dir_all(&wt).map_err(|e| e.to_string())?;
    Ok(serde_json::json!({
        "worktreePath": wt.display().to_string(),
        "taskId": safe,
        "method": "directory"
    }))
}

pub fn worktree_path(root: &Path, task_id: &str) -> PathBuf {
    root.join(".axiom-worktrees").join(format!("task-{task_id}"))
}
