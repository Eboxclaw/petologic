#!/usr/bin/env bash
# One-command sign-and-publish for the Paladino protection-content channel.
# Signs the staged content in updates-endpoint/content/ with the offline Ed25519 key and deploys
# the signed manifest, pack artifacts and reputation shards to petologic-updates.eitherbox.workers.dev.
#
# Usage (run from android/):
#   ./scripts/sign-and-publish.sh --key /secure/ed25519-production.pem [--version 2]
#
# Requirements: JDK 17 (javac/java), python3, wrangler on PATH, network. The private key is used
# in-memory only and never copied or committed.
set -euo pipefail
cd "$(dirname "$0")/.."   # android/
KEY=""
VERSION="2"
EXPIRY_DAYS="60"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --key) KEY="$2"; shift 2;;
    --version) VERSION="$2"; shift 2;;
    --expiry-days) EXPIRY_DAYS="$2"; shift 2;;
    *) echo "unknown arg $1" >&2; exit 1;;
  esac
done
[[ -n "$KEY" && -f "$KEY" ]] || { echo "Pass --key <PKCS#8 Ed25519 PEM>"; exit 1; }
[[ -f content/hash-pack.json && -f content/knowledge-pack.json && -f content/reputation-indicators.json ]] \
  || { echo "Run scripts/build-staged-content.py first."; exit 1; }

ENDPOINT_DIR="../updates-endpoint"
BASE="https://petologic-updates.eitherbox.workers.dev"
NOW_MS=$(( $(date +%s) * 1000 ))
EXPIRES_MS=$(( NOW_MS + EXPIRY_DAYS * 24 * 3600 * 1000 ))
COMMIT="17baf44964a77368e9149f4f593eb098beddf1c5"

echo "== 1. Sign the hash pack envelope =="
python3 scripts/sign-pack-envelope.py --payload content/hash-pack.json --private-key "$KEY" \
  --out content/hash-pack.signed.json

echo "== 2. License manifest =="
cat ../android/app/src/main/assets/security/ESET-LICENSE.txt > content/licenses.txt
cat >> content/licenses.txt <<EOF
First-party reviewed campaign descriptions: Paladino (Petologic), BSD-2-Clause.
EOF
LICENSE_SHA=$(shasum -a 256 content/licenses.txt | awk '{print $1}')

echo "== 3. Build and sign the manifest =="
ARTIFACTS="[{\"kind\":\"HASH_SET\",\"path\":\"content/hash-pack.signed.json\",\"url\":\"$BASE/artifacts/v$VERSION/hash-pack.json\",\"recordCount\":158,\"sourceSetVersion\":\"eset-malware-ioc@$COMMIT\",\"licenseManifest\":\"content/licenses.txt\",\"sourceIds\":[\"eset-malware-ioc\"],\"required\":true},{\"kind\":\"KNOWLEDGE\",\"path\":\"content/knowledge-pack.json\",\"url\":\"$BASE/artifacts/v$VERSION/knowledge-pack.json\",\"recordCount\":30,\"sourceSetVersion\":\"eset-malware-ioc@$COMMIT\",\"licenseManifest\":\"content/licenses.txt\",\"sourceIds\":[\"eset-malware-ioc\"],\"required\":false}]"
python3 scripts/build-threat-manifest.py --version "$VERSION" --expires-at "$EXPIRES_MS" \
  --channel stable --artifacts "$ARTIFACTS" --private-key "$KEY" \
  --output content/manifest-stable.json

echo "== 4. Reputation shards =="
python3 scripts/build-reputation-shards.py --indicators content/reputation-indicators.json \
  --sources security-data/source-registry.json --private-key "$KEY" --expires-at "$EXPIRES_MS" \
  --output "$ENDPOINT_DIR/public"

echo "== 5. Publish =="
mkdir -p "$ENDPOINT_DIR/public/artifacts/v$VERSION" "$ENDPOINT_DIR/public/security/manifest/v1"
cp content/hash-pack.signed.json "$ENDPOINT_DIR/public/artifacts/v$VERSION/hash-pack.json"
cp content/knowledge-pack.json "$ENDPOINT_DIR/public/artifacts/v$VERSION/knowledge-pack.json"
cp content/manifest-stable.json "$ENDPOINT_DIR/public/security/manifest/v1/stable"
(cd "$ENDPOINT_DIR" && npx wrangler deploy --config wrangler.jsonc)

echo "== 6. Verify =="
sleep 3
curl -fsS "$BASE/health" >/dev/null && echo "health: ok"
curl -fsS "$BASE/security/manifest/v1/stable" | python3 -c "import json,sys; e=json.load(sys.stdin); assert len(e['signature'])>100; print('manifest: signed envelope ok')"
curl -fsS "$BASE/artifacts/v$VERSION/hash-pack.json" | python3 -c "import json,sys; e=json.load(sys.stdin); assert len(e['signature'])>100; print('artifact: signed pack ok')"
echo "Published manifest version $VERSION. Verify in-app: Protection → Check for updates now."
