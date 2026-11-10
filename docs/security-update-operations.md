# Security update operations

How protection content reaches installed Paladino apps: build → sign → publish → verify. The app
side (manifest verifier, pack stores, isolated scanner, daily WorkManager check, expedited
re-check on the Protection screen) is fully implemented; this playbook covers the operator side.

Endpoint: `https://petologic-updates.eitherbox.workers.dev` (Cloudflare Worker
`petologic-updates`, source `updates-endpoint/` in this repository). Content lives under
`updates-endpoint/public/` and is deployed atomically with `wrangler deploy --config wrangler.jsonc`.

## One-time custody setup

1. Generate the production Ed25519 signing key offline (`openssl genpkey -algorithm ed25519`).
   The private key never touches this repository or the endpoint host.
2. Derive the raw 32-byte public key and replace
   `android/app/src/main/assets/security/trust-root.bin` in a **release build** (the app pins this
   root; a downloaded pack can never nominate its own key). This ships with the next APK release.
3. Export the public key as PKCS#8 PEM for `build-threat-manifest.py --private-key`.

## Publishing a new pack version

```sh
cd android
python3 scripts/build-threat-manifest.py \
  --version <N — strictly greater than any published version> \
  --expires-at <unix ms; republish before this or clients force a 200> \
  --channel stable \
  --artifacts '[{"kind":"HASH_SET","path":"build/pack-v<N>.json","url":"https://petologic-updates.eitherbox.workers.dev/artifacts/<N>/hash-pack.json","recordCount":<indicators>,"sourceSetVersion":"eset-malware-ioc@<pinned-commit>","licenseManifest":"security-data/licenses/eset.json","sourceIds":["eset-malware-ioc"],"required":true}
                ,{"kind":"YARA_RULES","path":"build/rules-v<N>.json","url":".../artifacts/<N>/rules.json","recordCount":<rule-sources>,"sourceSetVersion":"...","licenseManifest":"...","sourceIds":["..."],"required":false}
                ,{"kind":"KNOWLEDGE","path":"build/knowledge-v<N>.json","url":".../artifacts/<N>/knowledge.json","recordCount":<records>,"sourceSetVersion":"...","licenseManifest":"...","sourceIds":["..."],"required":false}]' \
  --private-key /secure/ed25519-production.pem \
  --output build/manifest-stable.json
```

YARA rule packs must satisfy `YaraRulesVerifier` (≤256 sources, ≤512 KiB each, ≤8 MiB total, rule
declaration required) and compile under the pinned YARA 4.5.8 with includes disabled. Knowledge
packs satisfy `SecurityKnowledgePackVerifier` (≤10k records, ≤30k edges). Only sources with
`productionEligible && redistributionReviewed` in `android/security-data/source-registry.json`
are accepted by the builder.

Publish atomically — artifacts first, then the manifest:

```sh
cp build/artifacts/<N>/* ../updates-endpoint/public/artifacts/<N>/
cp build/manifest-stable.json ../updates-endpoint/public/security/manifest/v1/stable
cd ../updates-endpoint && npx wrangler deploy --config wrangler.jsonc
```

Reputation shards use the same tree: `python3 scripts/build-reputation-shards.py ... --output
../updates-endpoint/public/v1/sha256-root` (mirrored to `public/v1/sha256/<prefix>`), served at
`https://petologic-updates.eitherbox.workers.dev/v1/sha256/<8-hex-prefix>`.
Enable in clients with `-PreputationUrl=https://petologic-updates.eitherbox.workers.dev` and
`-PthreatManifestUrl=https://petologic-updates.eitherbox.workers.dev/security/manifest/v1/stable`
at build time (debug builds can test against `http://127.0.0.1`).

## Verifying in the app

Protection → "Check for updates now" triggers the expedited `ThreatUpdateScheduler.checkNow()`.
Success states (`updated`/`unchanged`) and the new version appear in the update status; failures
record `last_error` and retry with backoff. Drills to run before calling the channel production:
expiry (let a manifest lapse → client strips the conditional header and must receive a fresh
manifest), rollback/replay (re-publish an older version → rejected), revocation (list a version
in `revokedVersions` → pack and its derived vectors disappear immediately), partial download
(truncated artifact → activation never happens), and CDN-cache staleness (304 with an expired
cached manifest → rejected).

## Rules of the channel

- Monotonic versions; artifacts immutable once published; never mutate in place.
- Republish before `expiresAt`; freshness is enforced end-to-end by the signed expiry.
- Revocation is sticky and survives reinstalls of older manifests via the rollback floor.
- The APK self-update channel is independent (GitHub Releases of `Eboxclaw/petologic-downloads`
  via `AppUpdater`; disabled in `playRelease`).
