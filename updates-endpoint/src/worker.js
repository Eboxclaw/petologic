// Petologic signed-update endpoint. Static files carry all protection content:
//   /security/manifest/v1/stable   SignedPayload envelope (Ed25519-signed manifest, ETag + 304)
//   /artifacts/<version>/<file>    immutable pack artifacts (hash-pinned by the manifest)
//   /v1/sha256/<8-hex-prefix>      k-anonymous reputation shards
// Content is published by signing with the offline key and redeploying (see
// docs/security-update-operations.md). Absent files return 404, which the app records as a
// failed check and retries later — never a silent downgrade.
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return new Response(JSON.stringify({ ok: true, service: "petologic-updates" }), {
        headers: { "content-type": "application/json", "cache-control": "no-store" },
      });
    }
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("method not allowed", { status: 405 });
    }
    return env.ASSETS.fetch(request);
  },
};
