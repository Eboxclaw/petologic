#!/usr/bin/env python3
"""Stage signing-ready protection content from the pinned ESET malware-ioc snapshot.

Emits (into --out, and this is the content the signed manifest then publishes):
  hash-pack.json          ThreatPack v2: exact SHA-256 indicators of Android-relevant families
  knowledge-pack.json     SecurityKnowledgePack: one EN + one PT reviewed campaign record per family
  reputation-indicators.json  input for build-reputation-shards.py

Metadata only — hashes and public descriptions, never samples. All content comes from the
registry-approved source `eset-malware-ioc` at the pinned commit recorded in
android/app/src/main/assets/security/SOURCES.md.

Usage:
  python3 scripts/build-staged-content.py --snapshot /tmp/eset-malware-ioc-<commit> \
      --commit 17baf44964a77368e9149f4f593eb098beddf1c5 --version 2 --out ../updates-endpoint/content
"""
import argparse, hashlib, json, pathlib, re, sys

COMMIT_DEFAULT = "17baf44964a77368e9149f4f593eb098beddf1c5"
FAMILIES = ["aridspy", "callphantom", "evilvideo", "ghostchat", "gravityrat", "gref", "kamran",
            "ngate", "promptspy", "prospytospy", "pwa_phishing", "scarcruft", "thewizards",
            "vajraspy", "virtual_invaders"]
MAX_INDICATORS = 10_000
MAX_PACK_BYTES = 2 * 1024 * 1024
MAX_KNOWLEDGE_RECORDS = 10_000
MAX_TEXT = 4096

PT_DESCRIPTIONS = {
 "aridspy": "Campanha Arid Viper: aplicações Android envenenadas com a spyware AridSpy.",
 "callphantom": "CallPhantom usa registos de chamadas falsos para enganar utilizadores Android e obter pagamentos.",
 "evilvideo": "Exploração da vulnerabilidade EvilVideo no Telegram para Android através de vídeos maliciosos.",
 "ghostchat": "Aplicação de encontros falsa usada como isco numa campanha de spyware dirigida no Paquistão.",
 "gravityrat": "GravityRAT para Android procura cópias de segurança do WhatsApp.",
 "gref": "Ferramentas de espionagem BadBazaar distribuídas através de aplicações Signal e Telegram modificadas.",
 "kamran": "Malware Android que espiona falantes de urdu residentes em Gilgit Baltistan.",
 "ngate": "NGate: roubo de credenciais e dados de cartões a partir de navegadores Android.",
 "promptspy": "Primeira ameaça Android conhecida a usar um modelo de IA para automatizar a espionagem.",
 "prospytospy": "Campanhas de spyware dirigidas a utilizadores Android preocupados com a privacidade nos EAU.",
 "pwa_phishing": "Esquemas de phishing instalados como aplicações web progressivas (PWA) no Android.",
 "scarcruft": "Campanha ScarCruft com comprometimento de dispositivos Android como parte do seu conjunto de ferramentas.",
 "thewizards": "Grupo TheWizards usa falsificação SLAAC para ataques de interposição em telemóveis Android.",
 "vajraspy": "VajraSpy: conjunto de aplicações de mensagens falsas usadas para espionagem no Android.",
 "virtual_invaders": "Campanha eXotic Visit: acompanha a atividade dos pacotes de espionagem Virtual Invaders no Android.",
}

def sha256_file(path):
    return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()

def family_title(snapshot: pathlib.Path, family: str) -> str:
    readme = snapshot / family / "README.adoc"
    if readme.exists():
        for line in readme.read_text(errors="replace").splitlines():
            line = line.strip()
            if line.startswith("= ") and line != ":toc:":
                return line[2:].replace(" - Indicators of Compromise", "").replace(" –- Indicators of Compromise", "").strip()
    return family

def collect_indicators(snapshot: pathlib.Path):
    seen = {}
    for family in FAMILIES:
        f = snapshot / family / "samples.sha256"
        if not f.exists():
            print(f"warning: {family} has no samples.sha256", file=sys.stderr)
            continue
        for n, line in enumerate(f.read_text(errors="replace").splitlines(), 1):
            h = line.strip().lower()
            if not h:
                continue
            if not re.fullmatch(r"[0-9a-f]{64}", h):
                print(f"warning: {family}:{n} skipped (not a sha256: {h[:70]})", file=sys.stderr)
                continue
            if h in seen:
                continue
            seen[h] = {"sha256": h, "id": f"eset-{family}-{n}", "source": f"eset-malware-ioc@{COMMIT_DEFAULT}",
                       "license": "BSD-2-Clause"}
    return seen

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--snapshot", required=True, help="extracted eset-malware-ioc snapshot root")
    ap.add_argument("--commit", default=COMMIT_DEFAULT)
    ap.add_argument("--version", type=int, required=True)
    ap.add_argument("--expires-days", type=int, default=60)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    import time
    snapshot = pathlib.Path(args.snapshot)
    now = int(time.time() * 1000)
    expires = now + args.expires_days * 24 * 3600 * 1000
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    indicators = collect_indicators(snapshot)
    if not (1 <= len(indicators) <= MAX_INDICATORS):
        sys.exit(f"indicator count out of bounds: {len(indicators)}")
    pack = {"schema": 1, "version": args.version, "issuedAt": now, "expiresAt": expires,
            "minEngine": 1, "indicators": sorted(indicators.values(), key=lambda i: i["sha256"])}
    pack_bytes = json.dumps(pack, sort_keys=True, separators=(",", ":")).encode()
    if len(pack_bytes) > MAX_PACK_BYTES:
        sys.exit(f"pack exceeds {MAX_PACK_BYTES} bytes: {len(pack_bytes)}")
    (out / "hash-pack.json").write_bytes(pack_bytes)

    records, edges = [], []
    rec_ids = set()
    for family in FAMILIES:
        title = family_title(snapshot, family)
        if family not in PT_DESCRIPTIONS:
            print(f"warning: no PT description for {family}", file=sys.stderr)
        expiry = expires
        for lang, text in (("en", f"Android threat campaign — {title}. Indicators of compromise "
                                  f"published by ESET for this campaign are included in the Paladino "
                                  f"signed indicator pack."),
                           ("pt", PT_DESCRIPTIONS.get(family, ""))):
            if not text:
                continue
            rid = f"eset-{family}-{lang}"
            if rid in rec_ids:
                sys.exit(f"duplicate knowledge id {rid}")
            rec_ids.add(rid)
            records.append({"id": rid, "revision": args.version, "kind": "CAMPAIGN", "language": lang,
                            "canonicalText": text[:MAX_TEXT], "source": f"eset-malware-ioc@{args.commit}",
                            "sourceVersion": args.commit, "trust": "REVIEWED", "validUntil": expiry,
                            "evidenceRefs": [f"{family}/README.adoc@{args.commit[:12]}"],
                            "limitations": ["Public campaign description; not a detection verdict.",
                                            "Relevance depends on the user's region and apps."]})
            if lang == "en":
                pt_id = f"eset-{family}-pt"
                edges.append({"fromId": rid, "toId": pt_id, "relation": "translation_of"})
    if not (1 <= len(records) <= MAX_KNOWLEDGE_RECORDS):
        sys.exit(f"knowledge record count out of bounds: {len(records)}")
    knowledge = {"schema": 1, "version": args.version, "issuedAt": now, "expiresAt": expires,
                 "records": records, "edges": edges}
    knowledge_bytes = json.dumps(knowledge, sort_keys=True, separators=(",", ":")).encode()
    (out / "knowledge-pack.json").write_bytes(knowledge_bytes)

    rep = [{"sha256": i["sha256"], "indicatorId": i["id"], "sourceId": "eset-malware-ioc"}
           for i in pack["indicators"]]
    (out / "reputation-indicators.json").write_text(json.dumps(rep, indent=1))

    print(f"hash indicators: {len(indicators)}  ({len(pack_bytes)} bytes)")
    print(f"knowledge records: {len(records)}  ({len(knowledge_bytes)} bytes)")
    print(f"reputation inputs: {len(rep)}")
    print(f"pack sha256: {hashlib.sha256(pack_bytes).hexdigest()}")
    print(f"knowledge sha256: {hashlib.sha256(knowledge_bytes).hexdigest()}")

if __name__ == "__main__":
    main()
