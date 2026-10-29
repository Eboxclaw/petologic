#!/usr/bin/env python3
"""Build signed k-anonymous SHA-256 reputation shards from inert indicator metadata.

The input contains hashes and provenance only. This tool never accepts, opens, or downloads a
corresponding file. Each output route reveals one 32-bit prefix and signed suffix candidates.
"""
import argparse, base64, hashlib, importlib.util, json, pathlib, sys, time
sys.dont_write_bytecode=True

PREFIX_LENGTH=8
MAX_ENTRIES_PER_PREFIX=8192

def load_signer():
    path=pathlib.Path(__file__).with_name("build-threat-manifest.py")
    spec=importlib.util.spec_from_file_location("paladino_manifest_builder",path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module.sign_ed25519

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--indicators",type=pathlib.Path,required=True,
                        help="JSON array of sha256, indicatorId and sourceId metadata")
    parser.add_argument("--sources",type=pathlib.Path,
                        default=pathlib.Path(__file__).parents[1]/"security-data/source-registry.json")
    parser.add_argument("--private-key",type=pathlib.Path,required=True,
                        help="Ed25519 PKCS#8 PEM outside the repository")
    parser.add_argument("--expires-at",type=int,required=True,help="Unix milliseconds")
    parser.add_argument("--output",type=pathlib.Path,required=True)
    args=parser.parse_args()
    now=int(time.time()*1000)
    if args.expires_at<=now: raise SystemExit("a future expiry is required")
    registry={item["id"]:item for item in json.loads(args.sources.read_text())["sources"]}
    raw=json.loads(args.indicators.read_text())
    if not isinstance(raw,list) or not raw: raise SystemExit("indicator metadata must be a non-empty array")
    shards={}
    seen=set()
    for item in raw:
        sha=item.get("sha256","")
        if not isinstance(sha,str) or len(sha)!=64 or any(c not in "0123456789abcdef" for c in sha):
            raise SystemExit("every indicator needs one lowercase SHA-256 string")
        if sha in seen: raise SystemExit("duplicate SHA-256 indicator")
        seen.add(sha)
        source=registry.get(item.get("sourceId"))
        if not source or not source["productionEligible"] or not source["redistributionReviewed"]:
            raise SystemExit(f"source is not approved for release: {item.get('sourceId')}")
        indicator_id=item.get("indicatorId","")
        if not isinstance(indicator_id,str) or not 1<=len(indicator_id)<=160:
            raise SystemExit("indicatorId is required and must be at most 160 characters")
        prefix,suffix=sha[:PREFIX_LENGTH],sha[PREFIX_LENGTH:]
        shards.setdefault(prefix,[]).append({"suffix":suffix,"indicatorId":indicator_id,
                                             "source":source["name"],"license":source["license"]})
    signer=load_signer();index=[]
    for prefix,entries in sorted(shards.items()):
        if len(entries)>MAX_ENTRIES_PER_PREFIX: raise SystemExit(f"prefix {prefix} exceeds client bounds")
        entries.sort(key=lambda row:row["suffix"])
        body={"schema":1,"prefix":prefix,"issuedAt":now,"expiresAt":args.expires_at,"entries":entries}
        payload=json.dumps(body,separators=(",",":"),sort_keys=True).encode()
        signature=signer(args.private_key,payload)
        envelope=json.dumps({"payload":base64.b64encode(payload).decode(),
                             "signature":base64.b64encode(signature).decode()},separators=(",",":"))
        destination=args.output/"v1"/"sha256"/prefix
        output_bytes=(envelope+"\n").encode()
        destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(output_bytes)
        index.append({"prefix":prefix,"entries":len(entries),"sha256":hashlib.sha256(output_bytes).hexdigest()})
    (args.output/"index.json").write_text(json.dumps({"schema":1,"issuedAt":now,
      "expiresAt":args.expires_at,"prefixLength":PREFIX_LENGTH,"indicators":len(seen),"shards":index},
      separators=(",",":"),sort_keys=True)+"\n")

if __name__=="__main__": main()
