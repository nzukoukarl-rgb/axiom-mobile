use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

fn resolve(root: &Path, rel: &str) -> Result<PathBuf, String> {
    if rel.contains("..") {
        return Err("PATH_TRAVERSAL_DENIED".into());
    }
    let p = root.join(rel);
    let canon_root = root.canonicalize().map_err(|e| e.to_string())?;
    // For write targets that don't exist yet, check parent
    match p.canonicalize() {
        Ok(c) => {
            if !c.starts_with(&canon_root) {
                return Err("PATH_ESCAPE_DENIED".into());
            }
            Ok(c)
        }
        Err(_) => {
            if let Some(parent) = p.parent() {
                if let Ok(pc) = parent.canonicalize() {
                    if !pc.starts_with(&canon_root) {
                        return Err("PATH_ESCAPE_DENIED".into());
                    }
                }
            }
            Ok(p)
        }
    }
}

pub fn read(root: &Path, rel: &str) -> Result<serde_json::Value, String> {
    let p = resolve(root, rel)?;
    let bytes = std::fs::read(&p).map_err(|e| e.to_string())?;
    let mut hasher = Sha256::new();
    hasher.update(&bytes);
    let hash = hex::encode(hasher.finalize());
    let preview = String::from_utf8_lossy(&bytes[..bytes.len().min(4000)]).to_string();
    Ok(serde_json::json!({
        "path": rel,
        "byteLength": bytes.len(),
        "contentHash": hash,
        "contentPreview": preview,
        "contentIsData": true
    }))
}

pub fn list(root: &Path, rel: Option<&str>) -> Result<serde_json::Value, String> {
    let dir = match rel {
        Some(r) if !r.is_empty() => resolve(root, r)?,
        _ => root.to_path_buf(),
    };
    let mut entries = vec![];
    let rd = std::fs::read_dir(&dir).map_err(|e| e.to_string())?;
    for e in rd.flatten().take(200) {
        let meta = e.metadata().ok();
        entries.push(serde_json::json!({
            "name": e.file_name().to_string_lossy(),
            "isDir": meta.as_ref().map(|m| m.is_dir()).unwrap_or(false),
        }));
    }
    Ok(serde_json::json!({ "entries": entries }))
}

pub fn grep(root: &Path, query: &str) -> Result<serde_json::Value, String> {
    if query.is_empty() {
        return Err("EMPTY_QUERY".into());
    }
    let mut matches = vec![];
    walk_grep(root, root, query, &mut matches, 40);
    Ok(serde_json::json!({
        "query": query,
        "matchCount": matches.len(),
        "matches": matches
    }))
}

fn walk_grep(root: &Path, dir: &Path, query: &str, out: &mut Vec<serde_json::Value>, limit: usize) {
    if out.len() >= limit {
        return;
    }
    let Ok(rd) = std::fs::read_dir(dir) else { return };
    for e in rd.flatten() {
        if out.len() >= limit {
            break;
        }
        let path = e.path();
        let name = e.file_name().to_string_lossy().to_string();
        if name == ".git" || name == ".axiom-worktrees" || name == "node_modules" {
            continue;
        }
        if path.is_dir() {
            walk_grep(root, &path, query, out, limit);
        } else if path.is_file() {
            if let Ok(text) = std::fs::read_to_string(&path) {
                for (i, line) in text.lines().enumerate() {
                    if line.contains(query) {
                        let rel = path.strip_prefix(root).unwrap_or(&path);
                        out.push(serde_json::json!({
                            "path": rel.to_string_lossy(),
                            "line": i + 1,
                            "text": line.chars().take(200).collect::<String>(),
                        }));
                        if out.len() >= limit {
                            return;
                        }
                    }
                }
            }
        }
    }
}

/// Edit only allowed when path is under `.axiom-worktrees/` (Lead: no direct canonical edit in V0).
pub fn edit(root: &Path, rel: &str, content: &str, require_worktree: bool) -> Result<serde_json::Value, String> {
    if require_worktree && !rel.contains(".axiom-worktrees/") && !rel.starts_with(".axiom-worktrees") {
        // Also allow if absolute resolved path contains marker
        let trial = root.join(rel);
        let s = trial.to_string_lossy();
        if !s.contains(".axiom-worktrees") {
            return Err("EDIT_CANONICAL_DENIED_USE_WORKTREE".into());
        }
    }
    let p = resolve(root, rel)?;
    if let Some(parent) = p.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::write(&p, content).map_err(|e| e.to_string())?;
    let mut hasher = Sha256::new();
    hasher.update(content.as_bytes());
    Ok(serde_json::json!({
        "path": rel,
        "contentHash": hex::encode(hasher.finalize()),
        "bytesWritten": content.len()
    }))
}
