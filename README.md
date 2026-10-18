# Axiom Mobile — AXIOM-JARVIS-KARL-001

Personal agent installed on Android (Karl Edition).  
**Not** Termux. **Not** a Claude clone. **Not** a PC Jarvis fork.

```
Mobile UX  +  Rust Local Runtime  ↔  Sync  ↔  Axiom Core (Vercel)
```

## Crates
- `axiom-mobile-core` — policy, queue, evidence, capabilities
- `axiom-runner` — filesystem / git / worktree / diff
- `axiom-android` — native bridge (cdylib for jniLibs)

## Operator
Sideload APK. Zero Cargo/Termux required.

## Gate
See `docs/AXIOM-MOBILE-GATE-001.md` and Lead M0–M4 phases.

## Build
See `scripts/build-android-arm64.md`.
