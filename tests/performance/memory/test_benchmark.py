import json
import io
from argparse import ArgumentTypeError
from pathlib import Path
import tempfile
import unittest
import zipfile
import subprocess
from unittest.mock import Mock, patch

from benchmark import ApiLoad, Sandbox, TIMELINE_READ, memory_bytes, parse_sample, recorded_payload
from compare import MIB, compare_outputs, summarize
from workloads import DATASETS, seed_sql, snapshot_sql


class SnapshotTests(unittest.TestCase):
    def compare(self, a, b, **kwargs):
        with tempfile.TemporaryDirectory() as directory:
            left, right = Path(directory) / "a", Path(directory) / "b"
            left.write_text("".join(json.dumps(value) + "\n" for value in a))
            right.write_text("".join(json.dumps(value) + "\n" for value in b))
            return compare_outputs(left, right, **kwargs)

    def row(self, **stats):
        return {"kind": "trip", "exact": {"movement_type": "CAR", "trip_duration": 500}, "stats": stats}

    def test_approximation_only_applies_to_statistics(self):
        a, b = self.row(avg_gps_speed=10), self.row(avg_gps_speed=10.01)
        result = self.compare([a], [b], absolute=.02)
        self.assertEqual("pass", result["status"])
        self.assertAlmostEqual(.01, result["errors"]["avg_gps_speed"]["max_absolute"])
        b["exact"]["movement_type"] = "FLIGHT"
        self.assertEqual("fail", self.compare([a], [b], absolute=1000)["status"])

    def test_extra_missing_and_null_rows_fail(self):
        self.assertEqual("fail", self.compare([self.row()], [])["status"])
        self.assertEqual("fail", self.compare([], [self.row()])["status"])
        self.assertEqual("fail", self.compare([self.row(speed=None)], [self.row(speed=0)])["status"])

    def test_statistical_error_and_strict_default(self):
        self.assertEqual("fail", self.compare([self.row(speed=10)], [self.row(speed=10.01)])["status"])
        self.assertEqual("pass", self.compare([self.row(speed=10)], [self.row(speed=10.01)], relative=.002)["status"])

    def test_small_differences_never_hide_missing_fields(self):
        self.assertEqual("fail", self.compare([self.row(speed=10)], [self.row()], absolute=100)["status"])
        self.assertEqual("fail", self.compare([self.row(speed=None)], [self.row()], absolute=100)["status"])


def run_row(variant="candidate", **changes):
    row = {"variant": variant, "dataset": "trip", "point_count": 100000, "cache": "hit",
        "memory_limit": "512m", "heap_limit": "192m", "memory_limit_bytes": 512 * MIB,
        "scenario": "normal", "status": "completed", "peak_container_bytes": 300 * MIB, "peak_rss_bytes": 250 * MIB,
        "peak_swap_bytes": 0, "oom_kills": 0, "elapsed_seconds": 10,
        "output_comparison": "pass", "iteration": 0}
    return {**row, **changes}


class GateTests(unittest.TestCase):
    def gates(self, rows):
        return summarize(rows)["groups"][0]["gates"]

    def test_oom_is_a_failure_even_with_low_sampled_memory(self):
        self.assertTrue(summarize([run_row(status="oom", oom_kills=1)])["failed"])

    def test_failed_baseline_does_not_create_a_speedup(self):
        rows = [run_row("baseline", status="oom", oom_kills=1)] * 5 + [run_row()] * 5
        self.assertEqual("inconclusive", self.gates(rows)["speed"])

    def test_profiled_runs_are_not_scored_for_speed(self):
        rows = [run_row("baseline", profiled=True)] * 5 + [run_row(profiled=True)] * 5
        self.assertEqual("inconclusive", self.gates(rows)["speed"])

    def test_memory_diagnostics_are_not_scored_for_speed(self):
        rows = [run_row("baseline", memory_diagnostics=True)] * 5 + [run_row(memory_diagnostics=True)] * 5
        self.assertEqual("inconclusive", self.gates(rows)["speed"])

    def test_five_separated_samples_demonstrate_speedup(self):
        rows = [run_row("baseline", elapsed_seconds=20)] * 5 + [run_row(elapsed_seconds=10)] * 5
        self.assertEqual("improved", self.gates(rows)["speed"])

    def test_no_samples_or_unknown_correctness_cannot_pass(self):
        gates = self.gates([run_row(peak_container_bytes=0, output_comparison="unavailable")])
        self.assertEqual("fail", gates["capacity"])
        self.assertEqual("fail", gates["correctness"])

    def test_noise_is_inconclusive_not_a_claim_of_regression(self):
        rows = [run_row("baseline", elapsed_seconds=t) for t in [8, 9, 10, 11, 12]]
        rows += [run_row(elapsed_seconds=t) for t in [9, 10, 11, 12, 13]]
        self.assertEqual("inconclusive", self.gates(rows)["speed"])

    def test_overlapping_api_latency_ranges_do_not_manufacture_a_regression(self):
        rows = [run_row("baseline", scenario="load", api_p95_seconds=v, api_samples=30)
            for v in [.01, .02, .03, .04, .05]]
        rows += [run_row(scenario="load", api_p95_seconds=v, api_samples=30)
            for v in [.02, .03, .04, .05, .06]]
        self.assertEqual("inconclusive", self.gates(rows)["api_latency"])
        for row in rows:
            if row["variant"] == "candidate": row["api_p95_seconds"] = .1
        self.assertEqual("fail", self.gates(rows)["api_latency"])

    def test_large_memory_profile_is_labelled_and_keeps_same_heap(self):
        rows = [run_row(), run_row(memory_limit="2g", memory_limit_bytes=2048 * MIB)]
        self.assertEqual(2, len(summarize(rows)["groups"]))
        self.assertEqual("not_applicable", self.gates([run_row(memory_limit="2g", memory_limit_bytes=2048 * MIB,
            peak_container_bytes=800 * MIB)])["headroom_450mib"])

    def test_plateau_needs_three_sizes_covering_100_fold_input_growth(self):
        rows = [run_row(point_count=n) for n in [100000, 1000000, 10000000]]
        self.assertEqual("no_detected_growth", summarize(rows)["scaling"][0]["status"])
        rows[-1]["peak_container_bytes"] += 40 * MIB
        self.assertEqual("fail", summarize(rows)["scaling"][0]["status"])

    def test_retention_growth_is_an_alarm(self):
        rows = [run_row(scenario="retention", iteration=i, idle_rss_bytes=(200 + i * 5) * MIB) for i in range(10)]
        self.assertEqual("fail", self.gates(rows)["retention"])

    def test_retention_is_not_five_independent_performance_samples(self):
        rows = [run_row(variant, scenario="retention", iteration=i, idle_rss_bytes=200 * MIB)
            for variant in ("baseline", "candidate") for i in range(10)]
        self.assertEqual("inconclusive", self.gates(rows)["speed"])


class InfrastructureTests(unittest.TestCase):
    def test_restore_refreshes_planner_statistics_before_measurement(self):
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory) / "fixture.dump"
            snapshot.write_bytes(b"fixture")
            sandbox = Sandbox("postgres", "python", Path(directory), 2)
            events = []
            def restored(*args, **kwargs):
                events.append("restore")
                return subprocess.CompletedProcess([], 0, b"", b"")
            sandbox.sql = Mock(side_effect=lambda *args, **kwargs: events.append("analyze"))
            with patch("benchmark.docker"), patch("benchmark.subprocess.run", side_effect=restored):
                sandbox.restore(snapshot)
            self.assertEqual(["restore", "analyze"], events)
            sandbox.sql.assert_called_once_with("ANALYZE;", timeout=1800)

    def test_light_load_reuses_k6_read_shape_and_checks_the_response(self):
        sandbox = Mock(base="http://benchmark")
        sandbox.sql.return_value = b'["2010-01-01T00:00:00Z","2010-01-08T00:00:00Z"]'
        load = ApiLoad(sandbox, "token")
        self.assertTrue(load.path.startswith(TIMELINE_READ["path"] + "?from="))
        with patch("benchmark.request", return_value={field: [] for field in TIMELINE_READ["requiredFields"]}):
            load.read()
        self.assertEqual(1, len(load.latencies))
        with patch("benchmark.request", return_value={"wrong_response": []}):
            load.read()
        self.assertEqual(1, load.errors)

    def test_memory_units(self):
        self.assertEqual(512 * MIB, memory_bytes("512m"))
        self.assertEqual(2 * 1024 * MIB, memory_bytes("2g"))
        for invalid in ("-1m", "0", "512mb", "512m;echo unsafe"):
            with self.assertRaises(ArgumentTypeError):
                memory_bytes(invalid)

    def test_cgroup_and_rss_units_and_oom_counter(self):
        raw = """memory.current
104857600
memory.peak
209715200
memory.swap.current
0
memory.events
oom 1
oom_kill 1
cpu.stat
usage_usec 2500000
process
VmRSS: 10000 kB
VmHWM: 20000 kB
"""
        sample = parse_sample(raw)
        self.assertEqual(200 * MIB, sample["peak"])
        self.assertEqual(20000 * 1024, sample["rss_peak"])
        self.assertEqual(1, sample["oom_kills"])
        self.assertEqual(2.5, sample["cpu_seconds"])

    def test_no_destructive_cleanup_of_an_unowned_container(self):
        sandbox = Sandbox("postgres", "python", Path("/tmp"), 2)
        response = type("Result", (), {"returncode": 0, "stdout": json.dumps([{"Config": {"Labels": {}}}]).encode()})()
        with patch("benchmark.docker", return_value=response) as docker_mock:
            with self.assertRaisesRegex(RuntimeError, "not owned"):
                sandbox.remove("some-existing-container")
            self.assertEqual(1, docker_mock.call_count)

    def test_recorded_fixtures_only_enable_boat_when_water_evidence_is_available(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("timeline_config.json", '{"boatEnabled":true,"staypointRadiusMeters":900}')
                archive.writestr("gps_data.json", "[]")
            self.assertEqual(path.read_bytes(), recorded_payload(path, True))
            with zipfile.ZipFile(io.BytesIO(recorded_payload(path, False))) as archive:
                config = json.loads(archive.read("timeline_config.json"))
                self.assertFalse(config["boatEnabled"])
                self.assertEqual(900, config["staypointRadiusMeters"])
                self.assertEqual(b"[]", archive.read("gps_data.json"))

    def test_seeds_use_server_side_generation_and_validate_inputs(self):
        for dataset in DATASETS:
            sql = seed_sql(dataset, 10000000, "hit")
            self.assertIn("generate_series(0,9999999)", sql)
            self.assertLess(len(sql), 4000)
        with self.assertRaises(ValueError):
            seed_sql("invalid", 10, "hit")
        with self.assertRaises(ValueError):
            seed_sql("stay", 0, "miss")

    def test_large_comparisons_use_a_cursor_and_normalize_ongoing_gaps(self):
        sql = snapshot_sql()
        self.assertIn("\\set FETCH_COUNT 1000", sql)
        self.assertIn("'ongoing'", sql.replace('"ongoing"', "'ongoing'"))
        self.assertIn("- 'id'", sql)
        self.assertIn("timeline_edit_applications", sql)


if __name__ == "__main__":
    unittest.main()
