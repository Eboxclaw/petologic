# Update content

This directory is deployed as static assets. The signing workflow drops:

- `security/manifest/v1/stable` — the SignedPayload manifest envelope (atomic replace, monotonic version)
- `artifacts/<version>/...` — immutable artifacts (never mutate in place)
- `v1/sha256/<prefix>` — reputation shards from build-reputation-shards.py

Publishing steps: docs/security-update-operations.md (repo root of Eboxclaw/petologic).
