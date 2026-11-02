#!/usr/bin/env python3
"""Native-image A/B benchmark. Only resources created and labelled by this runner are mutated."""

import argparse
import base64
import concurrent.futures
import csv
import hashlib
import http.client
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

from compare import compare_outputs, percentile, write_report
from workloads import DATASETS, EMAIL, PASSWORD, RECORDED, USER_ID, corrections_sql, recorded_user_sql, seed_sql, snapshot_sql

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
LABEL = "cc.geopulse.memory-benchmark"
SECOND_USER = "72cd041b-b2a3-4ceb-837e-48ebcc341c50"
SCENARIOS = ("normal", "load", "retention", "concurrency", "interrupt", "database-failure")
TIMELINE_READ = json.loads((HERE.parent / "k6/utils/timeline-read.json").read_text())


class SetupFailure(Exception):
    pass


def command(*args, input=None, stdout=None, timeout=120, check=True):
    result = subprocess.run(list(args), input=input, stdout=stdout or subprocess.PIPE,
        stderr=subprocess.PIPE, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"{' '.join(args[:5])}: {result.stderr.decode(errors='replace')[-3000:]}")
    return result


def docker(*args, **kwargs):
    return command("docker", *map(str, args), **kwargs)


def inspect(name):
    return json.loads(docker("inspect", name).stdout)[0]


def verify_native(image):
    docker("run", "--rm", "--network", "none", "--read-only", "--entrypoint", "/bin/sh", image,
        "-c", "test -x /work/application")


def memory_bytes(value):
    match = re.fullmatch(r"([1-9][0-9]*)([kmg]?)", value.lower())
    if not match:
        raise argparse.ArgumentTypeError("Use a positive Docker memory value, for example 512m or 2g")
    return int(match[1]) * 1024 ** {"": 0, "k": 1, "m": 2, "g": 3}[match[2]]


def request(base, path, token=None, body=None, timeout=10, content_type="application/json"):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + "/api/v1" + path, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        payload = response.read()
        if not payload:
            return None
        value = json.loads(payload)
        return value.get("data", value) if isinstance(value, dict) else value


def wait_until(action, timeout=120):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = action()
            if result:
                return result
        except (OSError, ValueError, RuntimeError, http.client.HTTPException) as error:
            last = error
        time.sleep(0.25)
    raise TimeoutError(f"Readiness timed out: {last}")


def digest_file(path):
    with open(path, "rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


class Sandbox:
    def __init__(self, postgres_image, geocoder_image, directory, cpus):
        self.directory = directory.resolve()
        self.run_id = uuid.uuid4().hex[:12]
        self.prefix = "gp-memory-" + self.run_id
        self.network = self.prefix
        self.ingress = self.prefix + "-ingress"
        self.db = self.prefix + "-db"
        self.mock = self.prefix + "-geocoder"
        self.backend = None
        self.containers = []
        self.cpus = str(cpus)
        self.postgres_image = postgres_image
        self.geocoder_image = geocoder_image
        self.network_created = False
        self.ingress_created = False

    def __enter__(self):
        try:
            docker("network", "create", "--internal", "--label", f"{LABEL}={self.run_id}", self.network)
            self.network_created = True
            docker("network", "create", "--label", f"{LABEL}={self.run_id}", self.ingress)
            self.ingress_created = True
            self.start(self.db, self.postgres_image, ["postgres", "-c", "shared_preload_libraries=pg_stat_statements",
                "-c", "compute_query_id=on"], ["--network-alias", "db", "--cpus", self.cpus,
                "-e", "POSTGRES_PASSWORD=benchmark", "-e", "POSTGRES_DB=benchmark"])
            wait_until(lambda: docker("exec", self.db, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", "-d", "benchmark", check=False).returncode == 0)
            self.sql("CREATE EXTENSION IF NOT EXISTS pg_stat_statements; CREATE ROLE benchmark_app LOGIN SUPERUSER PASSWORD 'benchmark';")
            self.start(self.mock, self.geocoder_image, ["python", "/benchmark/geocoder.py"],
                ["--publish", "127.0.0.1::8080", "--mount", f"type=bind,src={HERE},dst=/benchmark,readonly"], network=self.ingress)
            docker("network", "connect", "--alias", "geocoder", self.network, self.mock)
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def start(self, name, image, args=(), flags=(), network=None):
        # Explicit names and ownership labels make cleanup independent of other Docker projects.
        self.containers.append(name)
        docker("run", "-d", "--name", name, "--label", f"{LABEL}={self.run_id}",
            "--network", network or self.network, "--restart=no", *flags, image, *args)

    def sql(self, sql, stdout=None, timeout=120):
        return docker("exec", "-i", self.db, "psql", "-XqAt", "-v", "ON_ERROR_STOP=1",
            "-U", "postgres", "-d", "benchmark", input=sql.encode(), stdout=stdout, timeout=timeout).stdout

    def restore(self, snapshot):
        # This database exists solely in this sandbox. Never accept an external DB URL/container name.
        docker("exec", self.db, "psql", "-Xq", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "postgres",
            "-c", "DROP DATABASE benchmark WITH (FORCE)", "-c", "CREATE DATABASE benchmark")
        with open(snapshot, "rb") as source:
            result = subprocess.run(["docker", "exec", "-i", self.db, "pg_restore", "--exit-on-error",
                "-U", "postgres", "-d", "benchmark"], stdin=source, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError(result.stderr.decode()[-3000:])
        # pg_dump snapshots do not preserve the live planner statistics used by these large queries.
        self.sql("ANALYZE;", timeout=1800)

    def dump(self, path):
        with open(path, "wb") as output:
            docker("exec", self.db, "pg_dump", "-Fc", "--no-owner", "-U", "postgres", "-d", "benchmark",
                stdout=output, timeout=1800)

    def start_backend(self, image, memory, heap, scheduled=False, profile=False, memory_diagnostics=False):
        if self.backend:
            self.remove(self.backend)
        self.backend = self.prefix + "-backend-" + uuid.uuid4().hex[:6]
        env = {
            "GEOPULSE_POSTGRES_URL": "jdbc:postgresql://db:5432/benchmark",
            "GEOPULSE_POSTGRES_USERNAME": "benchmark_app", "GEOPULSE_POSTGRES_PASSWORD": "benchmark",
            "GEOPULSE_AI_ENCRYPTION_KEY_LOCATION": "file:/benchmark/key.txt",
            "QUARKUS_SCHEDULER_START_MODE": "normal" if scheduled else "halted",
            "GEOPULSE_GEONAMES_IMPORT_ENABLED": "false", "GEOPULSE_GEONAMES_COUNTRY_IMPORT_ENABLED": "false",
            "GEOPULSE_WARMUP_ENABLED": "false", "GEOPULSE_TIMELINE_SAFETY_NET_ENABLED": "false",
            "GEOPULSE_TIMELINE_JOB_DELAY": "24h", "GEOPULSE_TIMELINE_REGENERATION_CAMPAIGN_DELAY": "24h",
            "GEOPULSE_PROMETHEUS_ENABLED": "true", "GEOPULSE_PROMETHEUS_WORKLOAD_ENABLED": "true",
            "GEOPULSE_PROMETHEUS_MEMORY_ENABLED": "true", "GEOPULSE_AUTH_ACCESS_TOKEN_LIFESPAN": "24h",
            "GEOPULSE_TIMELINE_MEMORY_DIAGNOSTICS_ENABLED": str(memory_diagnostics).lower(),
            "GEOPULSE_GEOCODING_PRIMARY_PROVIDER": "nominatim", "GEOPULSE_GEOCODING_FALLBACK_PROVIDER": "nominatim",
            "GEOPULSE_GEOCODING_NOMINATIM_URL": "http://geocoder:8080",
            "GEOPULSE_GEOCODING_PHOTON_ENABLED": "false", "GEOPULSE_GEOCODING_DELAY_MS": "0",
            "GEOPULSE_TIMELINE_PROCESSING_THREADS": "1", "GEOPULSE_TIMELINE_REGENERATION_CAMPAIGN_MAX_CONCURRENT_TASKS": "1",
            "GEOPULSE_HTTP_ACCESS_LOG_ENABLED": "false",
            "NATIVE_IMAGE_OPTS": f"-Xmx{heap} -XX:MaximumYoungGenerationSizePercent=15",
        }
        if profile:
            env["NATIVE_IMAGE_OPTS"] += " -XX:StartFlightRecording=name=benchmark,filename=/work/benchmark.jfr,dumponexit=true,maxsize=64M"
        flags = ["--memory", memory, "--memory-swap", memory, "--cpus", self.cpus,
            "--network-alias", "backend", "--mount", f"type=bind,src={self.directory},dst=/benchmark,readonly"]
        for key, value in env.items():
            flags.extend(["-e", f"{key}={value}"])
        self.start(self.backend, image, flags=flags)
        def published_port():
            state = inspect(self.backend)
            if not state["State"]["Running"]:
                log = docker("logs", self.backend, check=False)
                raise SetupFailure("Backend exited during startup: " + (log.stdout + log.stderr).decode(errors="replace")[-3000:])
            # Internal Docker networks cannot publish ports. The mock provides host ingress only.
            mapping = inspect(self.mock)["NetworkSettings"]["Ports"].get("8080/tcp")
            return mapping[0]["HostPort"] if mapping else None
        port = wait_until(published_port, 15)
        self.base = "http://127.0.0.1:" + port
        return env

    def ready(self):
        def check():
            if not inspect(self.backend)["State"]["Running"]:
                raise SetupFailure("Backend exited before becoming healthy")
            return request(self.base, "/system/health")
        wait_until(check)

    def login(self, email=EMAIL):
        response = request(self.base, "/auth/api-sessions", body={"email": email, "password": PASSWORD})
        return response["accessToken"]

    def snapshot(self, path):
        with open(path, "wb") as output:
            self.sql(snapshot_sql(), stdout=output, timeout=1800)

    def cpu(self, name):
        raw = docker("exec", name, "cat", "/sys/fs/cgroup/cpu.stat", check=False).stdout.decode()
        return int(dict(line.split() for line in raw.splitlines()).get("usage_usec", 0)) / 1e6

    def calls(self):
        return int(self.sql("SELECT COALESCE(sum(calls),0)::bigint FROM pg_stat_statements "
            "WHERE userid=(SELECT oid FROM pg_roles WHERE rolname='benchmark_app')").strip())

    def geocoding_calls(self):
        with urllib.request.urlopen(self.base + "/benchmark/stats", timeout=10) as response:
            return json.load(response)["calls"]

    def remove(self, name):
        info = docker("inspect", name, check=False)
        if info.returncode:
            return
        if json.loads(info.stdout)[0]["Config"]["Labels"].get(LABEL) != self.run_id:
            raise RuntimeError("Refusing cleanup of a Docker resource not owned by this benchmark")
        docker("rm", "-f", "-v", name)

    def __exit__(self, *args):
        if args and args[0] and self.backend:
            with open(self.directory / (self.prefix + ".startup.log"), "wb") as output:
                docker("logs", self.backend, stdout=output, check=False)
        for name in reversed(self.containers):
            self.remove(name)
        for network, created in ((self.network, self.network_created), (self.ingress, self.ingress_created)):
            if created:
                info = json.loads(docker("network", "inspect", network).stdout)[0]
                if info["Labels"].get(LABEL) != self.run_id:
                    raise RuntimeError("Network ownership changed")
                docker("network", "rm", network)


SAMPLE_SCRIPT = r"""
for name in memory.current memory.peak memory.swap.current memory.events cpu.stat; do
  printf '%s\n' "$name"
  cat "/sys/fs/cgroup/$name"
done
printf 'process\n'
sed -n '/VmRSS:/p; /VmHWM:/p' /proc/1/status
"""


def parse_sample(raw):
    sections = {}
    current = None
    for line in raw.splitlines():
        if line in ("memory.current", "memory.peak", "memory.swap.current", "memory.events", "cpu.stat", "process"):
            current = line
            sections[current] = []
        elif current:
            sections[current].append(line)
    events = dict(line.split() for line in sections["memory.events"])
    cpu = dict(line.split() for line in sections["cpu.stat"])
    process = {line.split()[0].rstrip(":"): int(line.split()[1]) * 1024 for line in sections["process"]}
    return {"memory": int(sections["memory.current"][0]), "peak": int(sections["memory.peak"][0]),
        "swap": int(sections["memory.swap.current"][0]), "oom_kills": int(events.get("oom_kill", 0)),
        "cpu_seconds": int(cpu["usage_usec"]) / 1e6, "rss": process["VmRSS"], "rss_peak": process["VmHWM"]}


class Monitor:
    def __init__(self, sandbox, path):
        self.sandbox = sandbox
        self.path = path
        self.stop_event = threading.Event()
        self.peak = self.rss_peak = self.swap_peak = self.oom = 0
        self.last = None
        self.error = None
        self.thread = threading.Thread(target=self.run, daemon=True)

    def sample(self):
        raw = docker("exec", self.sandbox.backend, "sh", "-c", SAMPLE_SCRIPT, timeout=10).stdout.decode()
        sample = parse_sample(raw)
        self.peak = max(self.peak, sample["peak"])
        self.rss_peak = max(self.rss_peak, sample["rss_peak"])
        self.swap_peak = max(self.swap_peak, sample["swap"])
        self.oom = max(self.oom, sample["oom_kills"])
        self.last = sample
        return sample

    def run(self):
        with open(self.path, "w") as output:
            while not self.stop_event.is_set():
                try:
                    output.write(json.dumps({"time": time.time(), **self.sample()}) + "\n")
                    output.flush()
                except (RuntimeError, KeyError, ValueError, subprocess.TimeoutExpired) as error:
                    self.error = str(error)
                self.stop_event.wait(1)

    def stop(self):
        self.stop_event.set()
        if self.thread.ident is not None:
            self.thread.join(timeout=15)


class ApiLoad:
    def __init__(self, sandbox, token):
        self.sandbox, self.token = sandbox, token
        start, end = json.loads(sandbox.sql(f"SELECT json_build_array(min(timestamp),min(timestamp) + interval '7 days') "
            f"FROM gps_points WHERE user_id='{USER_ID}'"))
        self.path = TIMELINE_READ["path"] + "?" + urllib.parse.urlencode({
            TIMELINE_READ["startParameter"]: start, TIMELINE_READ["endParameter"]: end})
        self.latencies = []
        self.errors = 0
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self.run, daemon=True)

    def run(self):
        # Fixed offered load: one request per second, with no catch-up burst after a slow response.
        next_request = time.monotonic()
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
            pending = set()
            while not self.stop_event.wait(max(0, next_request - time.monotonic())):
                pending = {future for future in pending if not future.done()}
                if len(pending) < 10:
                    pending.add(pool.submit(self.read))
                else:
                    self.errors += 1
                next_request += 1

    def read(self):
        start = time.monotonic()
        try:
            response = request(self.sandbox.base, self.path, self.token)
            if not isinstance(response, dict) or any(field not in response for field in TIMELINE_READ["requiredFields"]):
                raise ValueError("Read workload did not return a timeline")
            self.latencies.append(time.monotonic() - start)
        except (OSError, ValueError, http.client.HTTPException):
            self.errors += 1

    def stop(self):
        self.stop_event.set()
        if self.thread.ident is not None:
            self.thread.join(timeout=15)


def await_jobs(sandbox, jobs, timeout, fault=None):
    deadline = time.monotonic() + timeout
    maximum, queued, injected = 0, False, False
    while time.monotonic() < deadline:
        state = inspect(sandbox.backend)["State"]
        if not state["Running"]:
            raise RuntimeError("Backend exited")
        progress = [request(sandbox.base, "/timeline/jobs/" + job, token) for token, job in jobs]
        maximum = max(maximum, sum(p["status"] == "RUNNING" and 35 <= p["progressPercentage"] < 95 for p in progress))
        queued |= any(p["status"] == "QUEUED" for p in progress)
        if fault and not injected and any(p["status"] == "RUNNING" and p["progressPercentage"] >= 35 for p in progress):
            injected = fault()
            if injected:
                return {"max_processing_jobs": maximum, "queue_observed": queued, "fault_injected": True}
        if all(p["status"] in ("COMPLETED", "FAILED") for p in progress):
            if fault and not injected:
                raise RuntimeError("Job completed before the fault could be injected; use a larger fixture")
            if any(p["status"] != "COMPLETED" for p in progress):
                raise RuntimeError("Timeline failed: " + json.dumps(progress))
            # Verify the generation transaction released its per-user lock before stopping the timer.
            sandbox.sql(f"BEGIN; SET LOCAL lock_timeout='5s'; SELECT id FROM users WHERE id='{USER_ID}' FOR NO KEY UPDATE; COMMIT;")
            return {"max_processing_jobs": maximum, "queue_observed": queued}
        time.sleep(0.25)
    raise TimeoutError("Timeline generation exceeded the benchmark timeout")


def begin_job(sandbox, token):
    return token, request(sandbox.base, "/timeline/jobs", token, body={})["jobId"]


def recorded_payload(path, water_evidence):
    if water_evidence:
        return path.read_bytes()
    # Match TimelineReplayHarness.withoutBoat(): sparse-stop/continuity fixtures have no water evidence.
    output = io.BytesIO()
    with zipfile.ZipFile(path) as source, zipfile.ZipFile(output, "w") as target:
        for entry in source.infolist():
            content = source.read(entry)
            if entry.filename == "timeline_config.json":
                config = json.loads(content)
                config["boatEnabled"] = False
                content = json.dumps(config).encode()
            target.writestr(entry, content)
    return output.getvalue()


def seed_recorded(sandbox, dataset):
    sandbox.sql(recorded_user_sql())
    fixture = ROOT / "backend/src/test/resources/timeline-fixtures" / ("issue-" + dataset.split("-")[1] + "-debug-export.zip")
    # Boat replay uses the archive's water flags, like TimelineReplayHarness.installWaterDataset().
    if dataset == "recorded-631":
        sandbox.sql("INSERT INTO geo_dataset_metadata (dataset_name,source_url,feature_count,imported_at) "
            "VALUES ('water_surface_polygons:replay_harness','test',1,now()) ON CONFLICT DO NOTHING;")
    token = sandbox.login()
    boundary = "benchmark-" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture.zip\"\r\n"
        "Content-Type: application/zip\r\n\r\n").encode() + recorded_payload(fixture, dataset == "recorded-631") + f"\r\n--{boundary}--\r\n".encode()
    job = request(sandbox.base, "/debug-imports", token, body, content_type="multipart/form-data; boundary=" + boundary)["job"]
    def finished():
        state = request(sandbox.base, "/debug-imports/" + job["importJobId"], token)["job"]
        if state["status"].lower() == "failed":
            raise SetupFailure("Recorded fixture import failed: " + json.dumps(state))
        return state["status"].lower() == "completed"
    wait_until(finished, 600)
    sandbox.sql(corrections_sql())
    await_jobs(sandbox, [begin_job(sandbox, token)], 600)


def capture(args):
    destination = Path(args.out).resolve()
    destination.mkdir(parents=True, exist_ok=False)
    (destination / "key.txt").write_text(base64.b64encode(os.urandom(32)).decode() + "\n")
    baseline = json.loads(docker("image", "inspect", args.baseline).stdout)[0]
    verify_native(baseline["Id"])
    postgres = json.loads(docker("image", "inspect", args.postgres_image).stdout)[0]
    geocoder = json.loads(docker("image", "inspect", args.geocoder_image).stdout)[0]
    # Do not infer the application commit from an inherited base-image OCI revision label.
    manifest = {"format": 1, "baseline_image": baseline["Id"], "baseline_commit": args.baseline_commit,
        "baseline_inspect": baseline, "postgres_image": postgres["Id"], "geocoder_image": geocoder["Id"],
        "cpus": args.cpus, "fixture_version": digest_file(HERE / "workloads.py"), "cases": [],
        "environment": {}, "created_at": time.time(),
        "runner_hashes": {name: digest_file(HERE / name) for name in ("benchmark.py", "workloads.py", "compare.py", "geocoder.py", "../k6/utils/timeline-read.json")}}
    (destination / "source-commit.txt").write_bytes(command("git", "-C", str(ROOT), "rev-parse", "HEAD").stdout)
    (destination / "source-diff.patch").write_bytes(command("git", "-C", str(ROOT), "diff", "HEAD").stdout)
    print("Preserving baseline image", flush=True)
    docker("image", "save", "-o", destination / "baseline-image.tar", baseline["Id"], timeout=1800)
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    with Sandbox(postgres["Id"], geocoder["Id"], destination, args.cpus) as sandbox:
        manifest["environment"] = sandbox.start_backend(baseline["Id"], args.prepare_memory, args.prepare_heap, scheduled=True)
        sandbox.ready()
        sandbox.remove(sandbox.backend)
        sandbox.backend = None
        sandbox.dump(destination / "schema.dump")
        for dataset in args.datasets:
            for size in ([0] if dataset in RECORDED else args.sizes):
                for cache in (["recorded"] if dataset in RECORDED else args.caches):
                    case_id = f"{dataset}-{size}-{cache}"
                    print("Preparing " + case_id, flush=True)
                    sandbox.restore(destination / "schema.dump")
                    if dataset in RECORDED:
                        sandbox.start_backend(baseline["Id"], args.prepare_memory, args.prepare_heap, scheduled=True)
                        sandbox.ready()
                        seed_recorded(sandbox, dataset)
                        sandbox.remove(sandbox.backend)
                        sandbox.backend = None
                    else:
                        sandbox.sql(seed_sql(dataset, size, cache), timeout=args.timeout)
                    count = int(sandbox.sql(f"SELECT count(*) FROM gps_points WHERE user_id='{USER_ID}'").strip())
                    dump = destination / (case_id + ".dump")
                    sandbox.dump(dump)
                    case = {"id": case_id, "dataset": dataset, "point_count": count,
                        "cache": cache, "snapshot": dump.name, "sha256": digest_file(dump)}
                    manifest["cases"].append(case)
                    try:
                        sandbox.start_backend(baseline["Id"], args.prepare_memory, args.prepare_heap)
                        sandbox.ready()
                        await_jobs(sandbox, [begin_job(sandbox, sandbox.login())], args.timeout)
                        golden = destination / (case_id + ".expected.jsonl")
                        sandbox.snapshot(golden)
                        case.update(golden=golden.name, golden_sha256=digest_file(golden))
                    except (RuntimeError, OSError, TimeoutError, ValueError, http.client.HTTPException, SetupFailure) as error:
                        # A baseline that cannot finish is evidence, never an empty golden file.
                        case["golden_error"] = str(error)
                    finally:
                        with open(destination / (case_id + ".baseline.log"), "wb") as output:
                            docker("logs", sandbox.backend, stdout=output, check=False)
                        sandbox.remove(sandbox.backend)
                        sandbox.backend = None
                    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Baseline image, configuration and database snapshots preserved in " + str(destination), flush=True)


def execute_run(args, sandbox, bundle, case, image, variant, memory, folder, scenario, iteration=0, reuse=False):
    folder.mkdir(parents=True)
    row = {"commit": args.candidate_commit if variant == "candidate" else bundle["baseline_commit"],
        "image_digest": image, "variant": variant, "dataset": case["dataset"], "point_count": case["point_count"],
        "cache": case["cache"], "memory_limit": memory, "memory_limit_bytes": memory_bytes(memory),
        "heap_limit": args.heap, "scenario": scenario, "iteration": iteration, "profiled": args.profile,
        "memory_diagnostics": getattr(args, "memory_diagnostics", False),
        "peak_container_bytes": 0, "peak_rss_bytes": 0, "peak_swap_bytes": 0, "elapsed_seconds": 0,
        "backend_cpu_seconds": None, "database_cpu_seconds": None, "sql_calls": None,
        "oom_kills": 0, "output_comparison": "unavailable", "status": "failed", "artifact": str(folder)}
    monitor = load = None
    start = None
    try:
        if not reuse:
            if sandbox.backend:
                sandbox.remove(sandbox.backend)
                sandbox.backend = None
            sandbox.restore(Path(args.bundle) / case["snapshot"])
            if scenario == "concurrency":
                sandbox.sql(f"""INSERT INTO users SELECT (jsonb_populate_record(NULL::users,to_jsonb(u) ||
                    '{{"id":"{SECOND_USER}","email":"second-memory-benchmark@example.invalid"}}'::jsonb)).*
                    FROM users u WHERE id='{USER_ID}';
                    INSERT INTO gps_points (user_id,coordinates,timestamp,accuracy,velocity,altitude,source_type,created_at)
                    SELECT '{SECOND_USER}',coordinates,timestamp,accuracy,velocity,altitude,source_type,created_at
                    FROM gps_points WHERE user_id='{USER_ID}'; ANALYZE gps_points;""", timeout=args.timeout)
            row["environment"] = sandbox.start_backend(image, memory, args.heap, profile=args.profile,
                memory_diagnostics=getattr(args, "memory_diagnostics", False))
        monitor = Monitor(sandbox, folder / "memory.jsonl")
        monitor.thread.start()
        sandbox.ready()
        token = sandbox.login()
        if scenario in ("interrupt", "database-failure"):
            await_jobs(sandbox, [begin_job(sandbox, token)], args.timeout)
            sandbox.snapshot(folder / "before.jsonl")
        if scenario == "load":
            load = ApiLoad(sandbox, token)
        cpu_before = sandbox.cpu(sandbox.backend)
        db_cpu_before = sandbox.cpu(sandbox.db)
        calls_before = sandbox.calls()
        geocoding_before = sandbox.geocoding_calls()
        if load:
            load.thread.start()
        start = time.monotonic()
        jobs = [begin_job(sandbox, token)]
        if scenario == "concurrency":
            jobs.append(begin_job(sandbox, sandbox.login("second-memory-benchmark@example.invalid")))
        def inject():
            if scenario == "interrupt":
                monitor.sample()
                docker("kill", "--signal", "KILL", sandbox.backend)
                return True
            return sandbox.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE "
                "usename='benchmark_app' AND datname='benchmark' AND xact_start IS NOT NULL "
                "AND query ILIKE '%gps_points%' ORDER BY xact_start LIMIT 1").strip() == b"t"
        row.update(await_jobs(sandbox, jobs, args.timeout, inject if scenario in ("interrupt", "database-failure") else None))
        if scenario in ("interrupt", "database-failure"):
            if scenario == "interrupt":
                docker("start", sandbox.backend)
                sandbox.ready()
                token = sandbox.login()
            else:
                # Wait for the failed transaction to release all locks before comparing committed state.
                wait_until(lambda: request(sandbox.base, "/timeline/jobs/" + jobs[0][1], token)["status"] == "FAILED", 30)
            sandbox.snapshot(folder / "rolled-back.jsonl")
            rollback = compare_outputs(folder / "before.jsonl", folder / "rolled-back.jsonl")
            row["rollback_comparison"] = rollback["status"]
            await_jobs(sandbox, [begin_job(sandbox, token)], args.timeout)
            row["status"] = "recovered"
        else:
            row["status"] = "completed"
        row["elapsed_seconds"] = time.monotonic() - start
        row["backend_cpu_seconds"] = None if scenario == "interrupt" else max(0, sandbox.cpu(sandbox.backend) - cpu_before)
        row["database_cpu_seconds"] = max(0, sandbox.cpu(sandbox.db) - db_cpu_before)
        row["sql_calls"] = sandbox.calls() - calls_before
        row["geocoding_calls"] = sandbox.geocoding_calls() - geocoding_before
        if load:
            load.stop()
            row.update(api_p95_seconds=percentile(load.latencies, .95), api_samples=len(load.latencies), api_errors=load.errors)
            load = None
        # Save stage metrics and result outside the timed interval.
        with urllib.request.urlopen(sandbox.base + "/api/v1/system/metrics", timeout=10) as response:
            (folder / "metrics.prom").write_bytes(response.read())
        sandbox.snapshot(folder / "output.jsonl")
        time.sleep(args.idle_seconds)
        row["idle_rss_bytes"] = monitor.sample()["rss"]
    except (RuntimeError, TimeoutError, OSError, ValueError, KeyError, http.client.HTTPException, subprocess.TimeoutExpired, SetupFailure) as error:
        row["error"] = str(error)
        row["status"] = "timeout" if isinstance(error, (TimeoutError, subprocess.TimeoutExpired)) else "failed"
        if start:
            row["elapsed_seconds"] = time.monotonic() - start
    finally:
        if load:
            load.stop()
        if monitor:
            monitor.stop()
            row.update(peak_container_bytes=monitor.peak, peak_rss_bytes=monitor.rss_peak,
                peak_swap_bytes=monitor.swap_peak, oom_kills=monitor.oom, monitor_error=monitor.error)
        if sandbox.backend:
            info = inspect(sandbox.backend)
            (folder / "container-state.json").write_text(json.dumps(info["State"], indent=2) + "\n")
            if info["State"]["OOMKilled"]:
                row["status"] = "oom"
                row["oom_kills"] = max(1, row["oom_kills"])
                row["peak_is_lower_bound"] = True
            with open(folder / "backend.log", "wb") as output:
                docker("logs", sandbox.backend, stdout=output, check=False)
            if args.profile:
                docker("stop", "-t", "30", sandbox.backend, check=False)
                docker("cp", sandbox.backend + ":/work/benchmark.jfr", folder / "benchmark.jfr", check=False)
        if not row["peak_container_bytes"]:
            row["measurement_error"] = "No cgroup-v2 sample was collected"
            row["status"] = "failed"
    return row


def run(args):
    directory = Path(args.out).resolve()
    directory.mkdir(parents=True, exist_ok=False)
    bundle_path = Path(args.bundle).resolve()
    bundle = json.loads((bundle_path / "manifest.json").read_text())
    if bundle["format"] != 1 or bundle["fixture_version"] != digest_file(HERE / "workloads.py"):
        raise ValueError("Fixture version differs from the preserved bundle; capture a new bundle")
    candidate = json.loads(docker("image", "inspect", args.candidate).stdout)[0]["Id"]
    verify_native(candidate)
    # The saved baseline ID is immutable even if its original tag was moved.
    if docker("image", "inspect", bundle["baseline_image"], check=False).returncode:
        docker("image", "load", "-i", bundle_path / "baseline-image.tar", timeout=1800)
    cases = [case for case in bundle["cases"] if not args.datasets or case["dataset"] in args.datasets]
    if not cases:
        raise ValueError("No matching cases in baseline bundle")
    for case in cases:
        if digest_file(bundle_path / case["snapshot"]) != case["sha256"]:
            raise ValueError("Database snapshot checksum mismatch: " + case["id"])
    # Only the disposable benchmark encryption key is copied. No installation keys are used.
    (directory / "key.txt").write_bytes((bundle_path / "key.txt").read_bytes())
    (directory / "run-config.json").write_text(json.dumps({**vars(args), "candidate_image": candidate,
        "baseline_image": bundle["baseline_image"], "cpus": bundle["cpus"]}, indent=2) + "\n")
    rows = []
    references = {}
    failures = set()
    for case in cases:
        if case.get("golden"):
            golden = bundle_path / case["golden"]
            if digest_file(golden) != case["golden_sha256"]:
                raise ValueError("Baseline output checksum mismatch: " + case["id"])
            for scenario in args.scenarios:
                references[(case["id"], scenario)] = golden
    with Sandbox(bundle["postgres_image"], bundle["geocoder_image"], directory, bundle["cpus"]) as sandbox:
        for memory in args.memory:
            for case in cases:
                for scenario in args.scenarios:
                    # Large matrix applies to normal rebuilds. Stress scenarios use the smallest fixture per dataset.
                    sizes = [c["point_count"] for c in cases if c["dataset"] == case["dataset"] and c["cache"] == case["cache"]]
                    if scenario != "normal" and case["point_count"] != min(sizes):
                        continue
                    repetitions = 1 if scenario == "retention" else args.repetitions
                    for iteration in range(repetitions):
                        variants = [("baseline", bundle["baseline_image"]), ("candidate", candidate)]
                        if iteration % 2 and scenario != "retention":
                            variants.reverse()
                        for variant, image in variants:
                            failure_key = (case["id"], memory, scenario, variant)
                            if failure_key in failures:
                                continue
                            # Retention needs ten runs in one process per variant, without restoring between runs.
                            iterations = range(10) if scenario == "retention" else [iteration]
                            for index in iterations:
                                folder = directory / f"{case['id']}-{memory}-{scenario}-{variant}-{index}"
                                print(folder.name, flush=True)
                                row = execute_run(args, sandbox, bundle, case, image, variant, memory, folder, scenario,
                                    index, reuse=scenario == "retention" and index > 0)
                                reference_key = (case["id"], scenario)
                                output = folder / "output.jsonl"
                                if output.exists():
                                    if variant == "baseline":
                                        references.setdefault(reference_key, output)
                                    reference = references.get(reference_key)
                                    if reference:
                                        comparison = compare_outputs(reference, output, args.stat_absolute, args.stat_relative)
                                        row["output_comparison"] = comparison["status"]
                                        (folder / "comparison.json").write_text(json.dumps(comparison, indent=2) + "\n")
                                rows.append(row)
                                if row["status"] not in ("completed", "recovered"):
                                    failures.add(failure_key)
                                with open(directory / "runs.jsonl", "a") as journal:
                                    journal.write(json.dumps(row) + "\n")
                                write_report(rows, directory)
                                if row["status"] not in ("completed", "recovered") and scenario == "retention":
                                    break
    # A larger-memory baseline may have completed after a capacity failure. Use its golden output, explicitly.
    for row in rows:
        if row["output_comparison"] == "unavailable" and Path(row["artifact"], "output.jsonl").exists():
            case = next(c for c in cases if c["dataset"] == row["dataset"] and c["point_count"] == row["point_count"] and c["cache"] == row["cache"])
            reference = references.get((case["id"], row["scenario"]))
            if reference:
                comparison = compare_outputs(reference, Path(row["artifact"], "output.jsonl"), args.stat_absolute, args.stat_relative)
                row["output_comparison"] = comparison["status"]
                (Path(row["artifact"]) / "comparison.json").write_text(json.dumps(comparison, indent=2) + "\n")
    (directory / "runs.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows))
    fields = ("commit", "image_digest", "variant", "dataset", "point_count", "cache", "memory_limit", "heap_limit", "scenario", "iteration",
        "profiled", "memory_diagnostics", "peak_container_bytes", "peak_rss_bytes", "peak_swap_bytes", "elapsed_seconds",
        "backend_cpu_seconds", "database_cpu_seconds", "sql_calls", "geocoding_calls", "api_samples", "api_errors",
        "oom_kills", "status", "output_comparison")
    with open(directory / "runs.csv", "w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    report = write_report(rows, directory)
    print("Report: " + str(directory / "report.md"), flush=True)
    return 1 if report["failed"] else 0


def parser():
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="operation", required=True)
    prepare = commands.add_parser("capture", help="Preserve a native baseline and deterministic database snapshots")
    prepare.add_argument("--baseline", required=True)
    prepare.add_argument("--baseline-commit", default="unknown", help="Application commit; inherited OCI base-image labels are not trusted")
    prepare.add_argument("--out", required=True)
    prepare.add_argument("--postgres-image", default="postgis/postgis:18-3.6")
    prepare.add_argument("--geocoder-image", default="python:3.13-alpine")
    prepare.add_argument("--cpus", type=float, default=2)
    prepare.add_argument("--sizes", nargs="+", type=int, default=[100000, 1000000, 10000000])
    prepare.add_argument("--datasets", nargs="+", choices=DATASETS + RECORDED, default=list(DATASETS + RECORDED))
    prepare.add_argument("--caches", nargs="+", choices=("hit", "miss"), default=["hit", "miss"])
    prepare.add_argument("--prepare-memory", default="2g")
    prepare.add_argument("--prepare-heap", default="768m")
    prepare.add_argument("--timeout", type=int, default=1800)
    measure = commands.add_parser("run", help="Restore snapshots for alternating baseline/candidate measurements")
    measure.add_argument("--bundle", required=True)
    measure.add_argument("--candidate", required=True)
    measure.add_argument("--candidate-commit", default="unknown")
    measure.add_argument("--out", required=True)
    measure.add_argument("--memory", nargs="+", default=["512m"], help="Separate labelled capacity/performance profiles")
    measure.add_argument("--heap", default="192m")
    measure.add_argument("--datasets", nargs="+", choices=DATASETS + RECORDED)
    measure.add_argument("--scenarios", nargs="+", choices=SCENARIOS, default=["normal"])
    measure.add_argument("--repetitions", type=int, default=5)
    measure.add_argument("--timeout", type=int, default=1800)
    measure.add_argument("--idle-seconds", type=float, default=5)
    measure.add_argument("--stat-absolute", type=float, default=0)
    measure.add_argument("--stat-relative", type=float, default=0)
    measure.add_argument("--profile", action="store_true", help="Separate JFR diagnostic run, excluded from speed conclusions")
    measure.add_argument("--memory-diagnostics", action="store_true", help="Enable backend memory checkpoints; excluded from speed conclusions")
    return root


def main():
    args = parser().parse_args()
    for key in ("memory", "prepare_memory", "heap", "prepare_heap"):
        values = getattr(args, key, [])
        for value in values if isinstance(values, list) else [values]:
            memory_bytes(value)
    if args.timeout <= 0 or getattr(args, "repetitions", 1) <= 0 or any(n < 2 for n in getattr(args, "sizes", [])):
        raise ValueError("Sizes, repetitions and timeout must be positive (sizes >= 2)")
    if getattr(args, "cpus", 1) <= 0 or getattr(args, "idle_seconds", 0) < 0 or any(
            getattr(args, key, 0) < 0 for key in ("stat_absolute", "stat_relative")):
        raise ValueError("CPU allocation must be positive; idle time and statistical tolerances must be nonnegative")
    if getattr(args, "profile", False) and "retention" in args.scenarios:
        raise ValueError("Profile separately from retention: saving JFR stops the backend between runs")
    return capture(args) if args.operation == "capture" else run(args)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, KeyError, http.client.HTTPException, subprocess.TimeoutExpired, SetupFailure) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
