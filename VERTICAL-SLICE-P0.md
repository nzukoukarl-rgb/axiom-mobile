# AXIOM MOBILE — Vertical Slice P0 (Karl Edition)

## Deliverables order

1. **Prebuilt runner aarch64** (CI) — same contract as axiom-runner JSON protocol
2. **APK shell** — chat history local, runner status, workspace picker (SAF)
3. **Offline task queue** — SQLite: pending / running / done / failed
4. **Sync Wi-Fi** — push Evidence + pull Context when online
5. **P1** worktree/edit/diff UI + Gloria + secrets vault + Evidence list

## Acceptance (device real)

Install APK → open workspace → read file via runner → queue offline task →
(optional) edit with approval → diff → evidence row → sync when Wi-Fi.

## Out of scope P0

Playwright, LSP, Device Owner by default, full social publish, local LLM.
