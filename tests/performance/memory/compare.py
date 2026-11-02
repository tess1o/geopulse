"""Streaming correctness checks and conservative before/after acceptance gates."""

import itertools
import json
import math
import statistics
from collections import defaultdict

MIB = 1024 * 1024


def compare_outputs(reference, candidate, absolute=0.0, relative=0.0):
    result = {"exact_mismatches": 0, "stat_mismatches": 0, "rows": 0, "errors": {}, "examples": []}
    with open(reference) as left, open(candidate) as right:
        for old, new in itertools.zip_longest(left, right):
            result["rows"] += 1
            a, b = json.loads(old) if old else None, json.loads(new) if new else None
            if a is None or b is None or a["kind"] != b["kind"] or a["exact"] != b["exact"]:
                result["exact_mismatches"] += 1
                if len(result["examples"]) < 5:
                    result["examples"].append({"row": result["rows"], "reference": a, "candidate": b})
                continue
            for key in a["stats"].keys() | b["stats"].keys():
                if key not in a["stats"] or key not in b["stats"]:
                    result["stat_mismatches"] += 1
                    continue
                x, y = a["stats"].get(key), b["stats"].get(key)
                if x == y:
                    continue
                if x is None or y is None or not all(isinstance(v, (float, int)) and math.isfinite(v) for v in (x, y)):
                    result["stat_mismatches"] += 1
                    continue
                error = abs(x - y)
                field = result["errors"].setdefault(key, {"changed": 0, "max_absolute": 0.0, "max_relative": 0.0})
                field["changed"] += 1
                field["max_absolute"] = max(field["max_absolute"], error)
                field["max_relative"] = max(field["max_relative"], error / max(abs(x), 1e-12))
                if not math.isclose(x, y, rel_tol=relative, abs_tol=absolute):
                    result["stat_mismatches"] += 1
    result["status"] = "pass" if not result["exact_mismatches"] and not result["stat_mismatches"] else "fail"
    return result


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]


def summarize(rows):
    groups = defaultdict(list)
    for row in rows:
        key = tuple(row[k] for k in ("dataset", "point_count", "cache", "memory_limit", "heap_limit", "scenario"))
        groups[key].append(row)
    reports = []
    for key, runs in sorted(groups.items()):
        old = [r for r in runs if r["variant"] == "baseline"]
        new = [r for r in runs if r["variant"] == "candidate"]
        complete = [r for r in new if r["status"] in ("completed", "recovered")]
        gates = {
            "completion": "pass" if new and len(complete) == len(new) else "fail",
            "capacity": "pass" if new and all(r["peak_container_bytes"] > 0 and r.get("peak_rss_bytes", 0) > 0 and r["oom_kills"] == 0 and r["peak_swap_bytes"] == 0
                and r["peak_container_bytes"] < r["memory_limit_bytes"] for r in new) else "fail",
            "headroom_450mib": ("pass" if new and all(max(r["peak_container_bytes"], r.get("peak_rss_bytes", 0)) <= 450 * MIB
                for r in new) else "fail") if runs[0]["memory_limit_bytes"] == 512 * MIB else "not_applicable",
            "correctness": "pass" if new and all(r["output_comparison"] == "pass" for r in new) else "fail",
        }
        if new and new[0]["scenario"] == "concurrency":
            gates["admission"] = "fail" if any(r.get("max_processing_jobs", 0) > 1 for r in new) else (
                "pass" if all(r.get("queue_observed") for r in new) else "inconclusive")
        if new and new[0]["scenario"] in ("interrupt", "database-failure"):
            gates["atomicity_and_recovery"] = "pass" if all(r.get("rollback_comparison") == "pass"
                and r["status"] == "recovered" for r in new) else "fail"
        times = {}
        for name, samples in (("baseline", old), ("candidate", new)):
            good = [r["elapsed_seconds"] for r in samples if r["status"] == "completed"]
            if good:
                times[name] = {"median": statistics.median(good), "min": min(good), "max": max(good), "n": len(good)}
        # Failed baseline samples are not dropped to manufacture a favourable speed comparison.
        speed = "inconclusive"
        if key[5] != "retention" and len(old) >= 5 and len(new) >= 5 and all(r["status"] == "completed"
                and not r.get("profiled") and not r.get("memory_diagnostics") for r in runs):
            a, b = times["baseline"], times["candidate"]
            if b["median"] > a["median"] * 1.05:
                speed = "fail" if b["min"] > a["max"] else "inconclusive"
            elif b["max"] < a["min"]:
                speed = "improved"
            else:
                speed = "no_detected_regression"
        gates["speed"] = speed
        if new and new[0]["scenario"] == "load":
            a = [r.get("api_p95_seconds") for r in old]
            b = [r.get("api_p95_seconds") for r in new]
            gates["api_latency"] = "inconclusive"
            if len(a) >= 5 and len(b) >= 5 and all(v is not None for v in a + b) and all(r.get("api_samples", 0) >= 20 for r in runs):
                gates["api_latency"] = "pass" if statistics.median(b) <= statistics.median(a) * 1.10 else (
                    "fail" if min(b) > max(a) else "inconclusive")
            if any(r.get("api_errors", 0) for r in new):
                gates["api_latency"] = "fail"
        if new and new[0]["scenario"] == "retention":
            measurements = [r.get("idle_rss_bytes") for r in sorted(new, key=lambda r: r["iteration"])]
            gates["retention"] = "inconclusive"
            if len(measurements) >= 10 and all(v is not None for v in measurements):
                tail = measurements[-5:]
                # RSS includes GC-reserved memory: this is a regression alarm, not proof of a Java leak.
                growing = all(y > x for x, y in zip(tail, tail[1:])) and tail[-1] - tail[0] > 16 * MIB
                gates["retention"] = "fail" if growing else "no_detected_growth"
        measurements = {}
        for name, samples in (("baseline", old), ("candidate", new)):
            if samples:
                measurements[name] = {"peak_container_bytes": max(r["peak_container_bytes"] for r in samples),
                    "peak_rss_bytes": max(r.get("peak_rss_bytes", 0) for r in samples)}
                for field in ("backend_cpu_seconds", "database_cpu_seconds", "sql_calls"):
                    values = [r[field] for r in samples if r.get(field) is not None]
                    measurements[name][field] = statistics.median(values) if values else None
        reports.append({"dataset": key[0], "points": key[1], "cache": key[2], "memory_limit": key[3],
            "heap_limit": key[4], "scenario": key[5], "timing_seconds": times, "measurements": measurements, "gates": gates})
    scaling = []
    series = defaultdict(list)
    for row in rows:
        if row["variant"] == "candidate" and row["scenario"] == "normal" and row["status"] == "completed":
            series[(row["dataset"], row["cache"], row["memory_limit"], row["heap_limit"])].append(row)
    for key, samples in sorted(series.items()):
        by_size = defaultdict(list)
        for row in samples:
            by_size[row["point_count"]].append(max(row["peak_container_bytes"], row.get("peak_rss_bytes", 0)))
        values = sorted((n, statistics.median(v)) for n, v in by_size.items())
        status = "inconclusive"
        if len(values) >= 3 and values[-1][0] >= values[0][0] * 100:
            status = "no_detected_growth" if values[-1][1] - values[-2][1] <= 32 * MIB else "fail"
        scaling.append({"dataset": key[0], "cache": key[1], "memory_limit": key[2], "heap_limit": key[3],
            "peaks_by_size": values, "status": status})
    return {"groups": reports, "scaling": scaling,
        "bounded_buffers": "requires declared buffer-cap instrumentation; scaling alone is not proof",
        "failed": any(value == "fail" for group in reports for value in group["gates"].values())
            or any(item["status"] == "fail" for item in scaling)}


def write_report(rows, destination):
    report = summarize(rows)
    (destination / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    lines = ["# Timeline memory benchmark", "", "Results are measured observations, not claims of bounded memory.", "",
        "| Dataset | Points | Cache | Limit / heap | Scenario | Baseline median | Candidate median | Baseline cgroup / RSS | Candidate cgroup / RSS | Gates |",
        "|---|---:|---|---|---|---:|---:|---:|---:|---|"]
    for group in report["groups"]:
        timing = group["timing_seconds"]
        def median(name):
            if name not in timing:
                return "unavailable"
            value = timing[name]
            return f"{value['median']:.3f}s [{value['min']:.3f}–{value['max']:.3f}] (n={value['n']})"
        gates = "; ".join(f"{k}: {v}" for k, v in group["gates"].items())
        def memory(name):
            sample = group["measurements"].get(name)
            return f"{sample['peak_container_bytes'] / MIB:.1f} / {sample['peak_rss_bytes'] / MIB:.1f} MiB" if sample else "unavailable"
        lines.append(f"| {group['dataset']} | {group['points']} | {group['cache']} | "
            f"{group['memory_limit']} / {group['heap_limit']} | {group['scenario']} | {median('baseline')} | {median('candidate')} | {memory('baseline')} | {memory('candidate')} | {gates} |")
    lines.extend(["", "## Scaling", "", "```json", json.dumps(report["scaling"], indent=2), "```", "",
        "Inconclusive gates need more evidence. Admission is inferred from observed job progress; buffer-cap assertions require engine instrumentation.",
        "Shared image pages may be charged to another cgroup. RSS and cgroup peaks are reported separately; a lower cgroup charge alone is not a demonstrated memory saving.",
        "API cancellation is not implemented by GeoPulse; the interrupt scenario tests process termination and recovery instead.",
        "Allocation/GC profiling is separate from acceptance timing. No profiler-based savings are inferred here.", ""])
    (destination / "report.md").write_text("\n".join(lines))
    return report
