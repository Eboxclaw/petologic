#!/usr/bin/env python3
"""Fail-closed verifier for Paladino release evidence.

This script only reads reports and signed build artifacts. It never downloads,
opens, installs, or executes an APK, corpus item, URL, or security sample.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import ipaddress
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import urllib.parse
from dataclasses import dataclass, asdict
from typing import Any, Iterable


DISCLAIMER = (
    "Release evidence verification only. This does not execute malware or certify "
    "live-malware detection."
)
HEX64 = re.compile(r"^[0-9a-f]{64}$")
PLACEHOLDER_HOSTS = {
    "example.com", "example.org", "example.net", "localhost",
    "127.0.0.1", "0.0.0.0", "::1",
}


class GateFailure(ValueError):
    pass


@dataclass(frozen=True)
class Gate:
    name: str
    passed: bool
    detail: str


def fail(message: str) -> None:
    raise GateFailure(message)


def load_json(path: pathlib.Path, maximum: int = 5_000_000) -> dict[str, Any]:
    if not path.is_file():
        fail(f"Missing evidence file: {path}")
    if path.stat().st_size > maximum:
        fail(f"Evidence file is too large: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail(f"Invalid JSON evidence {path}: {exc}")
    if not isinstance(value, dict):
        fail(f"Evidence must be a JSON object: {path}")
    return value


def require_string(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        fail(f"{name} must be a non-empty string.")
    return value.strip()


def require_sha256(value: Any, name: str) -> str:
    digest = require_string(value, name).lower()
    if not HEX64.fullmatch(digest):
        fail(f"{name} must be a 64-character SHA-256 digest.")
    return digest


def normalized_cert(value: str) -> str:
    digest = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if not HEX64.fullmatch(digest):
        fail("Certificate fingerprint must be a SHA-256 digest.")
    return digest


def validate_endpoint(value: str, name: str) -> str:
    parsed = urllib.parse.urlsplit(require_string(value, name))
    if parsed.scheme != "https" or not parsed.hostname:
        fail(f"{name} must be an HTTPS URL with a host.")
    if parsed.username or parsed.password or parsed.fragment:
        fail(f"{name} must not contain credentials or a fragment.")
    host = parsed.hostname.rstrip(".").lower()
    if host in PLACEHOLDER_HOSTS or host.endswith((".example.com", ".example.org", ".example.net", ".invalid", ".test", ".example", ".localhost")):
        fail(f"{name} uses a reserved or placeholder host.")
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        address = None
    if address and not address.is_global:
        fail(f"{name} must not use a private, loopback, link-local, or reserved IP address.")
    return urllib.parse.urlunsplit(parsed)


def validate_deterministic_report(report: dict[str, Any], revision: str) -> list[Gate]:
    gates = [
        Gate("deterministic report schema", report.get("schema") == 1, f"schema={report.get('schema')!r}"),
        Gate("deterministic release mode", report.get("mode") == "RELEASE", f"mode={report.get('mode')!r}"),
        Gate("deterministic gates", report.get("passed") is True, f"passed={report.get('passed')!r}"),
        Gate("deterministic revision", report.get("gitRevision") == revision, f"revision={report.get('gitRevision')!r}"),
        Gate("deterministic disclaimer", report.get("disclaimer") == "Inert behavioral benchmark only. It does not execute malware or certify live-malware detection.", "required disclaimer present"),
    ]
    require_sha256(report.get("corpusSha256"), "deterministic corpusSha256")
    case_gates = report.get("gates")
    expected_names = {"case expectations", "known hash recall", "mutated hash matches"}
    for group in ("url", "app_profile", "message_en", "message_pt"):
        expected_names.update({
            f"{group} suspicious recall",
            f"{group} critical high-attention rate",
            f"{group} benign high-attention rate",
            f"{group} benign alert rate",
        })
    actual_names = {g.get("name") for g in case_gates if isinstance(g, dict)} if isinstance(case_gates, list) else set()
    if not isinstance(case_gates, list) or not all(isinstance(g, dict) and g.get("passed") is True for g in case_gates):
        gates.append(Gate("deterministic individual gates", False, "one or more gates are missing or failed"))
    else:
        gates.append(Gate("deterministic individual gates", True, f"{len(case_gates)} gates passed"))
    missing = sorted(expected_names - actual_names)
    gates.append(Gate("deterministic gate coverage", not missing, "complete" if not missing else f"missing={missing}"))
    return gates


def validate_retrieval_report(report: dict[str, Any], revision: str, model_hash: str) -> list[Gate]:
    passes = report.get("passes")
    summaries = report.get("summaries")
    gates = [
        Gate("retrieval report schema", report.get("schema") == 2, f"schema={report.get('schema')!r}"),
        Gate("retrieval gates", report.get("passed") is True, f"passed={report.get('passed')!r}"),
        Gate("retrieval revision", report.get("gitRevision") == revision, f"revision={report.get('gitRevision')!r}"),
        Gate("retrieval model hash", report.get("modelHash") == model_hash, "pinned Embedding-350M digest"),
        Gate("reference device", report.get("deviceModel") == "Pixel 7a", f"device={report.get('deviceModel')!r}"),
        Gate("network disabled", report.get("networkDisabled") is True, f"networkDisabled={report.get('networkDisabled')!r}"),
        Gate("retrieval corpus size", isinstance(report.get("records"), int) and report["records"] >= 10_000, f"records={report.get('records')!r}"),
        Gate("retrieval warm-up", isinstance(report.get("warmupQueries"), int) and report["warmupQueries"] >= 200, f"queries={report.get('warmupQueries')!r}"),
        Gate("device fingerprint", isinstance(report.get("deviceFingerprint"), str) and report["deviceFingerprint"].strip().lower() not in {"", "unknown"}, f"fingerprint={report.get('deviceFingerprint')!r}"),
        Gate("retrieval disclaimer", report.get("disclaimer") == "Inert behavioral benchmark only. It does not execute malware or certify live-malware detection.", "required disclaimer present"),
    ]
    require_sha256(report.get("corpusSha256"), "retrieval corpusSha256")
    if not isinstance(passes, list):
        gates.append(Gate("three measured retrieval passes", False, "passes missing"))
    else:
        valid_passes = all(
            isinstance(item, dict)
            and item.get("locale") in {"en", "pt"}
            and item.get("run") in {1, 2, 3}
            and isinstance(item.get("queries"), int) and item["queries"] >= 100
            and isinstance(item.get("hitsAt5"), int) and 0 <= item["hitsAt5"] <= item["queries"]
            and isinstance(item.get("recallAt5"), (int, float))
            and abs(item["recallAt5"] - item["hitsAt5"] / item["queries"]) < 1e-9
            and isinstance(item.get("p95Millis"), int) and item["p95Millis"] >= 0
            for item in passes
        )
        pairs = {(item.get("locale"), item.get("run")) for item in passes if isinstance(item, dict)}
        gates.append(Gate("three measured retrieval passes", valid_passes and pairs == {(l, r) for l in ("en", "pt") for r in (1, 2, 3)}, f"locale/run pairs={sorted(pairs)}"))
    if not isinstance(summaries, list):
        gates.append(Gate("retrieval locale gates", False, "summaries missing"))
    else:
        by_locale = {item.get("locale"): item for item in summaries if isinstance(item, dict)}
        valid = set(by_locale) == {"en", "pt"} and isinstance(passes, list)
        for locale, item in by_locale.items():
            locale_passes = [p for p in passes if isinstance(p, dict) and p.get("locale") == locale]
            if len(locale_passes) != 3:
                valid = False
                continue
            recalls = sorted(p.get("recallAt5") for p in locale_passes if isinstance(p.get("recallAt5"), (int, float)))
            latencies = [p.get("p95Millis") for p in locale_passes if isinstance(p.get("p95Millis"), int)]
            if len(recalls) != 3 or len(latencies) != 3:
                valid = False
                continue
            median, worst = recalls[1], max(latencies)
            valid = valid and (
                item.get("recallPassed") is True
                and item.get("latencyPassed") is True
                and isinstance(item.get("medianRecallAt5"), (int, float))
                and abs(item["medianRecallAt5"] - median) < 1e-9
                and median >= 0.90
                and isinstance(item.get("worstP95Millis"), int)
                and item["worstP95Millis"] == worst
                and worst <= 200
            )
        gates.append(Gate("retrieval locale gates", valid, "English and Portuguese Recall@5 and p95"))
    return gates


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def command_output(command: list[str]) -> str:
    try:
        return subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        output = getattr(exc, "stdout", "") or ""
        fail(f"Artifact signature verification failed: {output[-500:].strip() or exc}")


def find_apksigner() -> str:
    direct = shutil.which("apksigner")
    if direct:
        return direct
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        candidates = sorted(pathlib.Path(sdk).glob("build-tools/*/apksigner"), reverse=True)
        if candidates:
            return str(candidates[0])
    fail("apksigner was not found. Install Android build-tools or set ANDROID_HOME.")


def verify_apk(path: pathlib.Path, expected: str) -> str:
    if not path.is_file() or path.suffix.lower() != ".apk":
        fail(f"Signed direct APK is missing or has the wrong extension: {path}")
    output = command_output([find_apksigner(), "verify", "--verbose", "--print-certs", str(path)])
    match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", output)
    if not match or normalized_cert(match.group(1)) != expected:
        fail("Direct APK signing certificate does not match the approved fingerprint.")
    return sha256_file(path)


def verify_aab(path: pathlib.Path, expected: str) -> str:
    if not path.is_file() or path.suffix.lower() != ".aab":
        fail(f"Signed Play bundle is missing or has the wrong extension: {path}")
    jarsigner = shutil.which("jarsigner") or fail("jarsigner was not found.")
    keytool = shutil.which("keytool") or fail("keytool was not found.")
    command_output([str(jarsigner), "-verify", "-strict", str(path)])
    output = command_output([str(keytool), "-printcert", "-jarfile", str(path)])
    match = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", output)
    if not match or normalized_cert(match.group(1)) != expected:
        fail("Play bundle signing certificate does not match the approved fingerprint.")
    return sha256_file(path)


def validate_lab_attestation(attestation: dict[str, Any], direct_digest: str) -> list[Gate]:
    report_path = pathlib.Path(require_string(attestation.get("reportPath"), "lab reportPath")).expanduser()
    expected_report_digest = require_sha256(attestation.get("reportSha256"), "lab reportSha256")
    actual_report_digest = sha256_file(report_path) if report_path.is_file() else "missing"
    assessments = attestation.get("assessments")
    required = {"protection", "false_positives", "performance", "usability"}
    return [
        Gate("lab attestation schema", attestation.get("schema") == 1, f"schema={attestation.get('schema')!r}"),
        Gate("independent lab identity", bool(str(attestation.get("provider", "")).strip()), f"provider={attestation.get('provider')!r}"),
        Gate("lab tested release artifact", attestation.get("testedArtifactSha256") == direct_digest, "direct APK digest matches"),
        Gate("lab report integrity", actual_report_digest == expected_report_digest, f"reportSha256={actual_report_digest}"),
        Gate("lab assessment coverage", isinstance(assessments, list) and set(assessments) >= required, f"assessments={assessments!r}"),
        Gate("lab conclusion", attestation.get("passed") is True, f"passed={attestation.get('passed')!r}"),
        Gate("samples stayed external", attestation.get("samplesHandledByExternalLabOnly") is True, "no samples transferred to Paladino developer devices"),
    ]


def write_report(output_dir: pathlib.Path, level: str, revision: str, gates: Iterable[Gate], artifacts: dict[str, str]) -> None:
    gate_list = list(gates)
    passed = bool(gate_list) and all(gate.passed for gate in gate_list)
    output_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "schema": 1,
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "disclaimer": DISCLAIMER,
        "level": level,
        "gitRevision": revision,
        "artifacts": artifacts,
        "gates": [asdict(gate) for gate in gate_list],
        "passed": passed,
    }
    (output_dir / "production-readiness.json").write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    lines = ["# Paladino production readiness", "", DISCLAIMER, "", f"- Level: `{level}`", f"- Result: **{'PASS' if passed else 'FAIL'}**", f"- Git revision: `{revision}`", "", "## Gates", ""]
    lines.extend(f"- {'PASS' if gate.passed else 'FAIL'} — {gate.name}: {gate.detail}" for gate in gate_list)
    (output_dir / "production-readiness.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--level", choices=("release-candidate", "public-antivirus"), required=True)
    parser.add_argument("--git-revision", required=True)
    parser.add_argument("--deterministic-report", type=pathlib.Path, required=True)
    parser.add_argument("--retrieval-report", type=pathlib.Path)
    parser.add_argument("--lab-attestation", type=pathlib.Path)
    parser.add_argument("--direct-apk", type=pathlib.Path, required=True)
    parser.add_argument("--play-aab", type=pathlib.Path, required=True)
    parser.add_argument("--threat-manifest-url", required=True)
    parser.add_argument("--reputation-url", required=True)
    parser.add_argument("--expected-direct-cert-sha256", required=True)
    parser.add_argument("--expected-play-cert-sha256")
    parser.add_argument("--embedding-model-sha256")
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    gates: list[Gate] = []
    artifacts: dict[str, str] = {}
    try:
        revision = require_string(args.git_revision, "git revision")
        if revision.lower() == "unknown" or not re.fullmatch(r"[0-9a-fA-F]{7,64}", revision):
            fail("Git revision must be a concrete 7-64 character hexadecimal commit ID.")
        direct_cert = normalized_cert(args.expected_direct_cert_sha256)
        play_cert = normalized_cert(args.expected_play_cert_sha256 or args.expected_direct_cert_sha256)
        validate_endpoint(args.threat_manifest_url, "threat manifest URL")
        validate_endpoint(args.reputation_url, "reputation URL")
        gates.extend(validate_deterministic_report(load_json(args.deterministic_report), revision))
        artifacts["directApkSha256"] = verify_apk(args.direct_apk, direct_cert)
        artifacts["playAabSha256"] = verify_aab(args.play_aab, play_cert)
        gates.extend([
            Gate("production endpoints", True, "HTTPS non-placeholder threat and reputation services"),
            Gate("direct artifact signature", True, f"certificate={direct_cert}"),
            Gate("Play artifact signature", True, f"certificate={play_cert}"),
        ])
        if args.level == "public-antivirus":
            if not args.retrieval_report or not args.lab_attestation:
                fail("public-antivirus level requires retrieval and independent lab evidence.")
            if not args.embedding_model_sha256:
                fail("public-antivirus level requires the pinned embedding model SHA-256.")
            gates.extend(validate_retrieval_report(load_json(args.retrieval_report), revision, require_sha256(args.embedding_model_sha256, "embedding model SHA-256")))
            gates.extend(validate_lab_attestation(load_json(args.lab_attestation), artifacts["directApkSha256"]))
        passed = all(gate.passed for gate in gates)
        write_report(args.output_dir, args.level, revision, gates, artifacts)
        if not passed:
            print("Production readiness: FAIL. See the generated report.", file=sys.stderr)
            return 1
        print(f"Production readiness: PASS ({args.level}). Report: {args.output_dir / 'production-readiness.json'}")
        return 0
    except GateFailure as exc:
        gates.append(Gate("evidence validation", False, str(exc)))
        write_report(args.output_dir, args.level, args.git_revision, gates, artifacts)
        print(f"Production readiness: FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
