# Native timeline memory benchmark

This runner measures the production native backend in disposable Docker containers. It never connects to an existing database or uses installation keys. Python 3.11+ and Docker with cgroup v2 are the only host dependencies. Run commands from the repository root.

## Preserve the baseline first

Build or select the **actual pre-change native image**. The application commit must be supplied explicitly: an inherited OCI revision label can identify the UBI base image instead of GeoPulse.

```sh
docker build -f backend/Dockerfile.native -t geopulse-memory-baseline .
docker pull postgis/postgis:18-3.6
docker pull python:3.13-alpine
python3 tests/performance/memory/benchmark.py capture \
  --baseline geopulse-memory-baseline --baseline-commit "$(git rev-parse HEAD)" \
  --out /absolute/path/to/baseline-bundle
```

Capture saves the immutable baseline image as `baseline-image.tar`, source revision/diff, configuration, fixture generator hash, database snapshot/checksum per case, and baseline expected output/checksum. Snapshots contain GPS, settings, favorites/geocoding, edits and notes; they are taken **before** the baseline rebuild so every independent run has the same initial cache state. The recorded issue fixtures also get a durable visit-name correction and an anchored note. Golden generation runs at 2 GiB / 768 MiB heap by default; an unavailable baseline result is recorded, never replaced by an empty passing output.

The default matrix is five synthetic datasets at 100k, 1m and 10m points, both cache hit and miss, plus the three small recorded fixtures from `TimelineReplayHarness`. GPS is generated directly by PostgreSQL's `generate_series`; no large Java or Python input list is built. Capture can take a long time on the unoptimized engine. Start with:

```sh
python3 tests/performance/memory/benchmark.py capture \
  --baseline geopulse-memory-baseline --out /absolute/path/to/smoke-bundle \
  --datasets stay events --sizes 1000 --caches hit miss --timeout 120
```

## Measure baseline and candidate

```sh
docker build -f backend/Dockerfile.native -t geopulse-memory-candidate .
python3 tests/performance/memory/benchmark.py run \
  --bundle /absolute/path/to/baseline-bundle \
  --candidate geopulse-memory-candidate --candidate-commit "$(git rev-parse HEAD)" \
  --out /absolute/path/to/comparison --memory 512m 2g --heap 192m \
  --repetitions 5 --scenarios normal load retention concurrency interrupt database-failure
```

The 512 MiB profile is the capacity test. Other profiles are labelled separately and keep the **same heap** for baseline and candidate. To investigate a larger heap, use another output directory with another `--heap`; do not compare unlike heaps as an algorithm speedup. Results must use unique output paths; the runner will not overwrite a previous bundle/report.

Five alternating A/B pairs (AB, BA, AB, BA, AB) are the default. Every independent run recreates the backend and restores its DB snapshot. A failed variant/case is recorded once rather than repeating the same timeout five times; it cannot produce a speed conclusion. Retention instead runs ten rebuilds in one process per variant; cgroup peak remains the container's lifetime peak and idle RSS is measured after each rebuild. Additional scenarios use only the smallest fixture of each dataset/cache combination to avoid multiplying the full scaling matrix. Increase the smallest size if a fault run finishes before injection can occur.

Every restore runs `ANALYZE` before starting the measured backend. PostgreSQL dumps do not preserve planner statistics; measuring without refreshing them can hide index improvements or time an unrealistic full-table scan. Fixture loading, restoring and statistics collection are outside the measured interval.

Images are pinned by local content ID, PostgreSQL/CPU configuration is shared, swap and automatic restart are disabled, and geocoding uses a deterministic Nominatim substitute on an **internal Docker network**. The mock also provides a loopback-only HTTP ingress proxy; it is outside the backend's memory limit, and its overhead is identical for both images. Backend and PostgreSQL have no external network. Unrelated schedulers, warmup, and GeoNames downloads are disabled equally for both variants. These settings are recorded; this is an isolated regeneration benchmark, not a claim about every production feature running at once.

`load` offers one authenticated timeline request per second from the host for the first seven days of the fixture, with errors counted and no replay of missed requests. Its endpoint, parameters and expected fields are shared with the existing k6 timeline scenario in `k6/utils/timeline-read.json`. `concurrency` submits different users and records observed queued/processing jobs; progress polling is evidence, not an exact internal admission counter. `interrupt` kills only the owned backend, verifies rollback and restart recovery, and rebuilds again. `database-failure` terminates the owned backend's active GPS transaction and checks rollback/retry. There is no cancellation API in GeoPulse: process interruption is tested instead. Nothing is injected into another Docker project.

## Read the results

- `runs.csv` and `runs.jsonl`: one row per run, including immutable image ID, declared commit, dataset, limits, memory peaks, CPU, DB query count, elapsed time, OOM and correctness status.
- `report.md` / `report.json`: grouped medians/ranges, capacity, headroom, correctness, API p95, retention, scaling and speed gates.
- Each run directory: cgroup-v2 samples, container exit/OOM state, native logs, stage metrics, streamed semantic output, and comparison/error details.

Elapsed time covers the external job request through completion/finalization and release of the user's transaction lock. SQL calls come from `pg_stat_statements` for the application's dedicated DB role, including authenticated polling/read traffic, so JDBC cannot hide work from the counter. CPU is read from each container's cgroup. Backend CPU across process-interruption/restart is reported unavailable rather than as an incorrect delta. Startup memory is included. An OOM can happen between samples; the final Docker OOM state is also checked. Current/peak cgroup use is authoritative, not Docker's cache-subtracted memory display.

Gates: no OOM/swap, peak below the selected container limit, 450 MiB headroom target, exact semantic output (with explicit statistical tolerance if selected), and no statistically clear >5% slowdown. Overlapping five-run timing ranges produce an inconclusive regression result. API p95 allows 10% regression and needs at least 20 successful API samples per run; faster smoke jobs are inconclusive. Retention flags sustained >16 MiB RSS growth over the last five of ten runs; reserved GC memory means this is an alarm, not proof of a leak. Scaling requires three input sizes spanning 100x and flags >32 MiB extra median peak at the last step. Headroom/scaling conservatively use the larger of cgroup peak and RSS high-water mark: shared native-image pages can be charged to another cgroup, so lower cgroup charges alone are not memory savings. These thresholds are explicit heuristics; fixed internal buffer caps still need implementation-level assertions/instrumentation when buffers are introduced.

Failures return exit code 1; setup/infrastructure failures return 2. Inconclusive evidence is displayed, never promoted to a claimed improvement. A one-pair smoke run checks wiring, not speed or scaling. A failed baseline cannot produce a speedup percentage. A baseline golden saved at capture is used even when the measured baseline OOMs; if no baseline can complete, correctness is unavailable and cannot pass. Streamed comparison runs on the host with server-side cursor fetching, not through a whole-history backend export.

Output compares ordered event contents, places, movement types, explanations, manual edits/applications and note attachments, ignoring generated IDs, volatile timestamps and the moving end of an ongoing gap. Counts/sample counts remain exact. Statistical fields are strict by default. Opt into measured error budgets with `--stat-absolute` and/or `--stat-relative`; they apply only to statistics, never classification/boundaries/manual edits. `comparison.json` reports maximum absolute/relative errors. Keep the existing real-day/replay/classification regression tests as an independent expected-result check; comparing two paths in the candidate alone is insufficient.

## Profiling, CI and attribution

Timeline persistence uses multi-row SQL inserts capped at 500 rows on the existing generation transaction's JDBC connection. Each chunk is one SQL execution, including set-based stay/place joins. It does not register inserted events as Hibernate-managed entities or commit/clear the caller's transaction. The writer's integration tests compare stored columns with ORM output across multiple chunks and verify rollback after a later chunk fails. The generation path also avoids deep copies when there are no manual edits, and note reattachment queries only eligible timestamp windows. These changes bound the insert buffer; the generated-event collection and longest active GPS segment still need separate scaling checks before claiming constant working memory for all histories. The 450 MiB headroom gate applies to the 512 MiB capacity profile; larger, separately labelled performance profiles have no 450 MiB gate.

Read-only active-point inspections reuse a view instead of copying the segment on each incoming point. Owned snapshots remain where points must survive clearing the state. Stay centroids accumulate coordinates in the original addition order, and altitude smoothing reuses a five-element scratch array. GPS pagination uses the ordered `(timestamp, id)` row comparison so PostgreSQL can seek to the cursor; duplicate timestamps and page-size invariance have regression tests. None of these changes caps the number of points in an unfinished stay/trip.

Boat transition detection updates the current environment run incrementally and retains only the latest eligible completed land and water runs. Completed-run distance is summed backward once, preserving the original threshold behavior; the current run keeps the original forward summation order. Tracker state is discarded when active points are cleared, and it is never created when boat detection is disabled. Prefix-by-prefix oracle tests cover unknown flags, shoreline blips, duplicate timestamps and floating-point boundaries; a point-read counter checks linear work across long runs.

Routine classification, short-trip and gap-inference decisions log at debug level. Per-point stop-rejection logging and eager numeric formatting have been removed; INFO stage summaries and warnings/errors for failures remain. `TimelineLoggingIntegrationTest` checks a complete synthetic rebuild for bounded INFO volume and no routine warnings. Compare the saved `backend.log` byte sizes and JSON level counts between fresh-container runs to measure logging reductions; retention logs cover the container lifetime and are cumulative.

To find which stage retains memory, rebuild the native image and use `run --memory-diagnostics --repetitions 1`. This sets `GEOPULSE_TIMELINE_MEMORY_DIAGNOSTICS_ENABLED=true`; it is off by default and independent of Prometheus. Diagnostic timings are excluded from speed conclusions.

Search `backend.log` for `Timeline memory diagnostics`. Checkpoints include user/job IDs, the stage, used/committed/max heap bytes, current/peak RSS, anonymous/file-backed RSS, cgroup current/peak/limit and anonymous/file/kernel bytes, and Hibernate's managed entity/collection counts. The processor adds active-point/event counts every 100k GPS points; persistence adds row counts every 2,000 rows. `before_commit` and `after_transaction` cover transaction completion (`transactionStatus=3` is committed; `4` is rolled back). Counters unavailable on a platform or after session closure are `-1`.

These checkpoints do not copy buffers, walk entity graphs, issue SQL, flush/clear the session or force GC. Rising managed-entity counts alongside persistence heap growth identify session retention; rising active-point counts identify unfinished event retention. Heap/RSS/container readings are shared totals when jobs run concurrently. Used heap includes garbage awaiting collection, and RSS/cgroup categories overlap: they are not additive object-size measurements. Use a separate JFR run when allocation stacks are needed. A container OOM kills the process before Java can reliably write a final log or heap dump, so use the preceding checkpoints and the runner's external OOM record.

Use a fresh output directory with `run --profile --repetitions 1` for JFR diagnostics. Native JFR is already included by `backend/Dockerfile.native`. The runner stops the backend gracefully and copies the recording. Profiled timings are excluded from speed conclusions. Do not run profiling concurrently with acceptance timing.

Repeat `run` for each intermediate image against the same baseline bundle, then the combined image. Preserve image digests and all reports; this attributes changes without relying on source-level query counts. `TimelineBatchInvarianceIntegrationTest` already compares all raw event fields/statistics/explanations at GPS page sizes 1, 7, 64 and 10,000 using the existing replay fixtures. Extend it as optimized pipeline stages introduce more batch boundaries; this harness does not invent a production batch-size setting. `TimelineTripClassificationTest` also checks altitude classification immediately below, at, and above both flight thresholds.

```sh
python3 -m unittest discover -s tests/performance/memory -p 'test_*.py' -v
```

PR CI runs these bounded-fixture, comparator, parser, ownership and gate checks. The scheduled/manual native benchmark workflow builds the candidate once, captures a configured baseline, and runs each dataset in a separate job. Image archives and per-dataset evidence are retained as GitHub Actions artifacts for three days, including failures; download the baseline bundle and shared image archive for permanent retention. Set repository variable `MEMORY_BENCHMARK_BASELINE_IMAGE` to an immutable native baseline image reference to enable nightly runs, and `MEMORY_BENCHMARK_BASELINE_COMMIT` when its application commit is known. Workflow dispatch also accepts these directly. The full 10m-point baseline can legitimately time out until optimization lands; that is retained as a failed measurement.

The runner removes its labelled containers, anonymous database volumes and internal network on exit. Bundles, logs, snapshots and reports remain at the explicit output paths, including failure evidence. They contain fixture location history; use synthetic/checked-in fixture data, and do not upload private production snapshots to public CI.
