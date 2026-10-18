# Secrets

Flow: UI input → Android Keystore → SecretReference → CapabilityGrant → Policy → Adapter

System sees secretRef only. LLM never sees raw tokens.
Providers: vercel, github, meta, youtube, katabump, telegram — each with scope + expiry + revoke.
