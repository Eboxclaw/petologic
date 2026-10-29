import importlib.util
import pathlib
import sys
import unittest


SCRIPT = pathlib.Path(__file__).parents[1] / "verify-production-readiness.py"
SPEC = importlib.util.spec_from_file_location("production_readiness", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ProductionReadinessTest(unittest.TestCase):
    def test_accepts_real_https_endpoint(self):
        self.assertEqual(
            "https://security.paladino.ai/v1/manifest.json",
            MODULE.validate_endpoint("https://security.paladino.ai/v1/manifest.json", "endpoint"),
        )

    def test_rejects_placeholder_and_private_endpoints(self):
        for value in (
            "http://security.example.org/feed",
            "https://example.com/feed",
            "https://service.invalid/feed",
            "https://127.0.0.1/feed",
            "https://10.0.0.1/feed",
            "https://user:secret@security.paladino.org/feed",
        ):
            with self.subTest(value=value), self.assertRaises(MODULE.GateFailure):
                MODULE.validate_endpoint(value, "endpoint")

    def test_deterministic_report_requires_release_and_matching_revision(self):
        report = {
            "schema": 1,
            "mode": "RELEASE",
            "passed": True,
            "gitRevision": "abcdef1",
            "corpusSha256": "a" * 64,
            "disclaimer": "Inert behavioral benchmark only. It does not execute malware or certify live-malware detection.",
            "gates": ([
                {"name": "case expectations", "passed": True},
                {"name": "known hash recall", "passed": True},
                {"name": "mutated hash matches", "passed": True},
            ] + [
                {"name": f"{group} {gate}", "passed": True}
                for group in ("url", "app_profile", "message_en", "message_pt")
                for gate in ("suspicious recall", "critical high-attention rate", "benign high-attention rate", "benign alert rate")
            ]),
        }
        self.assertTrue(all(g.passed for g in MODULE.validate_deterministic_report(report, "abcdef1")))
        self.assertFalse(all(g.passed for g in MODULE.validate_deterministic_report(report, "fffffff")))

    def test_retrieval_requires_pixel_offline_and_three_passes_per_language(self):
        report = {
            "schema": 2,
            "passed": True,
            "gitRevision": "abcdef1",
            "modelHash": "b" * 64,
            "deviceModel": "Pixel 7a",
            "networkDisabled": True,
            "deviceFingerprint": "google/lynx/pixel7a:16/test",
            "records": 10_000,
            "warmupQueries": 200,
            "corpusSha256": "a" * 64,
            "disclaimer": "Inert behavioral benchmark only. It does not execute malware or certify live-malware detection.",
            "passes": [
                {"locale": locale, "run": run, "queries": 100, "hitsAt5": 95, "recallAt5": 0.95, "p95Millis": 150}
                for locale in ("en", "pt") for run in (1, 2, 3)
            ],
            "summaries": [
                {"locale": locale, "medianRecallAt5": 0.95, "worstP95Millis": 150, "recallPassed": True, "latencyPassed": True}
                for locale in ("en", "pt")
            ],
        }
        self.assertTrue(all(g.passed for g in MODULE.validate_retrieval_report(report, "abcdef1", "b" * 64)))
        report["networkDisabled"] = False
        self.assertFalse(all(g.passed for g in MODULE.validate_retrieval_report(report, "abcdef1", "b" * 64)))


if __name__ == "__main__":
    unittest.main()
