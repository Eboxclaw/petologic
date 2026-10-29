#!/usr/bin/env python3
"""Build and sign a Paladino threat manifest without exposing the signing key.

The artifact descriptor is a JSON array. Each entry contains kind, path, url,
recordCount, sourceSetVersion, licenseManifest, sourceIds and optional required.
Only production-eligible, redistribution-reviewed sources may enter a release.
"""
import argparse, base64, hashlib, json, pathlib, shutil, subprocess, tempfile, time

KINDS={"HASH_SET","URL_SET","YARA_RULES","KNOWLEDGE","REVOCATION"}
MAX_ARTIFACTS=16
MAX_ARTIFACT_BYTES=64*1024*1024

def digest(path):
    h=hashlib.sha256()
    with open(path,"rb") as handle:
        for chunk in iter(lambda:handle.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()

def sign_ed25519(private_key, payload):
    """Sign with the JDK 17 Ed25519 provider used by the Android build.

    macOS ships an older LibreSSL whose pkeyutl cannot sign Ed25519 input.
    A tiny source-file Java program keeps this tool dependency-free and behaves
    the same on macOS and Linux build hosts.
    """
    java=shutil.which("java")
    if not java:
        raise SystemExit("JDK 17+ is required to sign a threat manifest")
    source='''
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
public final class PaladinoEd25519Signer {
  public static void main(String[] args) throws Exception {
    String pem=Files.readString(Path.of(args[0]));
    String body=pem.replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "").replaceAll("\\\\s", "");
    PrivateKey key=KeyFactory.getInstance("Ed25519").generatePrivate(
      new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    Signature signer=Signature.getInstance("Ed25519");
    signer.initSign(key); signer.update(Files.readAllBytes(Path.of(args[1])));
    Files.write(Path.of(args[2]), signer.sign());
  }
}
'''
    with tempfile.TemporaryDirectory() as directory:
        root=pathlib.Path(directory)
        source_path=root/"PaladinoEd25519Signer.java"
        payload_path=root/"manifest.json"
        signature_path=root/"manifest.sig"
        source_path.write_text(source)
        payload_path.write_bytes(payload)
        try:
            subprocess.run([java,str(source_path),str(private_key),str(payload_path),str(signature_path)],
                           check=True,capture_output=True,text=True)
        except subprocess.CalledProcessError as error:
            detail=(error.stderr or error.stdout).strip()
            raise SystemExit(f"Ed25519 signing failed: {detail}") from error
        signature=signature_path.read_bytes()
    if len(signature)!=64:
        raise SystemExit("unexpected Ed25519 signature size")
    return signature

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--version",type=int,required=True)
    parser.add_argument("--expires-at",type=int,required=True,help="Unix milliseconds")
    parser.add_argument("--channel",choices=("stable","preview"),default="stable")
    parser.add_argument("--artifacts",type=pathlib.Path,required=True)
    parser.add_argument("--sources",type=pathlib.Path,default=pathlib.Path(__file__).parents[1]/"security-data/source-registry.json")
    parser.add_argument("--private-key",type=pathlib.Path,required=True,help="Ed25519 PEM key outside the repository")
    parser.add_argument("--output",type=pathlib.Path,required=True)
    args=parser.parse_args()
    now=int(time.time()*1000)
    if args.version<1 or args.expires_at<=now: raise SystemExit("version and future expiry are required")
    registry=json.loads(args.sources.read_text())["sources"]
    sources={item["id"]:item for item in registry}
    descriptors=json.loads(args.artifacts.read_text())
    if not 1<=len(descriptors)<=MAX_ARTIFACTS: raise SystemExit("artifact count is outside release bounds")
    artifacts=[]
    for item in descriptors:
        kind=item["kind"];path=pathlib.Path(item["path"]);license_path=pathlib.Path(item["licenseManifest"])
        if kind not in KINDS: raise SystemExit(f"unsupported artifact kind: {kind}")
        if not item["url"].startswith("https://"): raise SystemExit(f"artifact URL must use HTTPS: {item['url']}")
        size=path.stat().st_size
        if not 1<=size<=MAX_ARTIFACT_BYTES: raise SystemExit(f"artifact size is outside bounds: {path}")
        for source_id in item["sourceIds"]:
            source=sources.get(source_id)
            if not source or not source["productionEligible"] or not source["redistributionReviewed"]:
                raise SystemExit(f"source is not approved for release: {source_id}")
        if kind=="KNOWLEDGE":
            knowledge=json.loads(path.read_text())
            if knowledge.get("schema")!=1 or knowledge.get("version")!=args.version:
                raise SystemExit("knowledge artifact schema/version does not match manifest")
        artifacts.append({
            "kind":kind,"url":item["url"],"sha256":digest(path),"size":size,
            "recordCount":int(item["recordCount"]),"sourceSetVersion":item["sourceSetVersion"],
            "licenseManifestSha256":digest(license_path),"required":bool(item.get("required",True))
        })
    manifest={"schema":1,"channel":args.channel,"version":args.version,"issuedAt":now,
              "expiresAt":args.expires_at,"minEngine":1,"revokedVersions":[],"artifacts":artifacts}
    payload=json.dumps(manifest,separators=(",",":"),sort_keys=True).encode()
    signature=sign_ed25519(args.private_key,payload)
    envelope={"payload":base64.b64encode(payload).decode(),"signature":base64.b64encode(signature).decode()}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(envelope,separators=(",",":"))+"\n")

if __name__=="__main__": main()
