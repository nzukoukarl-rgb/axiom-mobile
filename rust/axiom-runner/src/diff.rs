use std::path::Path;
use std::process::Command;

pub fn worktree_diff(root: &Path, worktree_rel: Option<&str>) -> Result<serde_json::Value, String> {
    let cwd = match worktree_rel {
        Some(p) if !p.is_empty() => root.join(p),
        _ => root.to_path_buf(),
    };
    let stat = Command::new("git")
        .args(["diff", "--stat"])
        .current_dir(&cwd)
        .output()
        .map_err(|e| e.to_string())?;
    let patch = Command::new("git")
        .args(["diff"])
        .current_dir(&cwd)
        .output()
        .map_err(|e| e.to_string())?;
    Ok(serde_json::json!({
        "diffStat": String::from_utf8_lossy(&stat.stdout),
        "diffPatchPreview": String::from_utf8_lossy(&patch.stdout).chars().take(8000).collect::<String>(),
        "isObservation": true
    }))
}
