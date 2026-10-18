# Threat model — Axiom Mobile V0

## Assets
- Workspace files (user-granted via SAF)
- Secrets in Android Keystore
- Evidence integrity
- Runner identity

## Adversaries
- Malicious file content (prompt injection) → treated as DATA
- Path traversal / workspace escape
- Approval bypass
- Runner substitution
- Secret leakage into logs/Evidence/LLM context

## Controls
- Structured ActionEnvelope only (no free shell)
- LocalPolicy path + capability + approval + runner id
- Edit only under `.axiom-worktrees/`
- contentIsData = true on file payloads
- Secrets: Keystore + secretRef never to LLM
