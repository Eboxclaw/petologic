#!/usr/bin/env python3
"""Wrap a protection-content JSON payload into the SignedPayload envelope the app verifies.

Uses the same offline Ed25519 key as build-threat-manifest.py (PKCS#8 PEM). Requires a JDK 17
javac on PATH (macOS LibreSSL cannot sign Ed25519; Java's JCA can).

Usage:
  python3 scripts/sign-pack-envelope.py --payload ../updates-endpoint/content/hash-pack.json \
      --private-key /secure/ed25519-production.pem \
      --out ../updates-endpoint/content/hash-pack.signed.json
"""
import argparse, base64, json, pathlib, subprocess, sys, tempfile

SIGNER_JAVA = '''import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
public final class PaladinoPackEnvelopeSigner {
  public static void main(String[] args) throws Exception {
    String pem=Files.readString(Path.of(args[0]));
    String body=pem.replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "").replaceAll("\\\\s", "");
    PrivateKey key=KeyFactory.getInstance("Ed25519").generatePrivate(
      new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    Signature signer=Signature.getInstance("Ed25519");
    signer.initSign(key); signer.update(Files.readAllBytes(Path.of(args[1])));
    Files.write(Paths.get(args[2]), signer.sign());
  }
}
'''

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--payload", required=True)
    ap.add_argument("--private-key", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    payload = pathlib.Path(args.payload).read_bytes()
    with tempfile.TemporaryDirectory() as directory:
        root = pathlib.Path(directory)
        source = root / "PaladinoPackEnvelopeSigner.java"
        source.write_text(SIGNER_JAVA)
        def run(cmd):
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode != 0:
                detail = (result.stderr or result.stdout or "").strip()
                sys.exit(f"command {cmd[0]} failed (exit {result.returncode}): {detail[:500]}")
        run(["javac", "-d", str(root), str(source)])
        signature = root / "signature.bin"
        run(["java", "-cp", str(root), "PaladinoPackEnvelopeSigner",
             args.private_key, args.payload, str(signature)])
        sig = signature.read_bytes()
        if len(sig) != 64:
            sys.exit(f"unexpected signature length {len(sig)}")
    envelope = {"payload": base64.b64encode(payload).decode(),
                "signature": base64.b64encode(sig).decode()}
    pathlib.Path(args.out).write_text(json.dumps(envelope) + "\n")
    print(f"signed envelope written: {args.out} (payload {len(payload)} bytes)")

if __name__ == "__main__":
    main()
