use axiom_mobile_core::policy::ActionEnvelope;
use axiom_mobile_core::RunnerIdentity;
use axiom_runner::Runner;
use std::sync::Mutex;

static RUNNER: Mutex<Option<Runner>> = Mutex::new(None);

pub fn init_workspace(path: &str) -> String {
    let mut g = RUNNER.lock().unwrap();
    *g = Some(Runner::new(path));
    serde_json::json!({
        "ok": true,
        "workspace": path,
        "runner": RunnerIdentity::karl_phone_v0()
    })
    .to_string()
}

pub fn identity() -> String {
    let g = RUNNER.lock().unwrap();
    match g.as_ref() {
        Some(r) => serde_json::json!({
            "ok": true,
            "initialized": true,
            "identity": r.identity_json(),
            "workspace": r.workspace_root.to_string_lossy()
        })
        .to_string(),
        None => serde_json::json!({
            "ok": true,
            "initialized": false,
            "identity": RunnerIdentity::karl_phone_v0(),
            "hint": "call nativeInitWorkspace(path)"
        })
        .to_string(),
    }
}

pub fn handle_request(json: &str) -> String {
    let env: ActionEnvelope = match serde_json::from_str(json) {
        Ok(e) => e,
        Err(e) => {
            return serde_json::json!({
                "ok": false,
                "denied": true,
                "error": format!("MALFORMED_ACTION:{e}")
            })
            .to_string();
        }
    };
    let g = RUNNER.lock().unwrap();
    match g.as_ref() {
        Some(r) => serde_json::to_string(&r.execute(env)).unwrap_or_else(|e| e.to_string()),
        None => serde_json::json!({"ok": false, "error": "RUNNER_NOT_INITIALIZED"}).to_string(),
    }
}
