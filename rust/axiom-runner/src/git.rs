use std::path::Path;
use std::process::Command;

pub fn status(root: &Path) -> Result<serde_json::Value, String> {
    let out = Command::new("git")
        .args(["status", "--porcelain"])
        .current_dir(root)
        .output()
        .map_err(|e| e.to_string())?;
    Ok(serde_json::json!({
        "exitCode": out.status.code(),
        "porcelain": String::from_utf8_lossy(&out.stdout),
    }))
}
