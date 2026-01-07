# GeoPulse k6 Load Testing

Comprehensive load testing infrastructure for GeoPulse using [k6](https://k6.io/).

## Overview

This test suite performs load testing on production GeoPulse servers to evaluate performance under realistic user load. Tests simulate concurrent users accessing various endpoints including Timeline, Dashboard, Journey Insights, and Location Analytics.

**Key Features:**
- ✅ Tests against **production server** with **production data**
- ✅ Reusable test users (created once, used multiple times)
- ✅ Read-only tests (safe for production)
- ✅ JWT authentication with automatic token refresh
- ✅ Realistic mixed workload scenarios
- ✅ Configurable load profiles (light, moderate, heavy)

## Prerequisites

- **k6** installed locally ([installation guide](https://k6.io/docs/getting-started/installation/))
- **PostgreSQL client** (psql) for one-time setup
- **Network access** to production server
- **Database credentials** for production database

### Install k6

**macOS:**
```bash
brew install k6
```

**Linux:**
```bash
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Windows:**
```powershell
choco install k6
```

## Setup

### Phase 1: One-Time Test Data Creation (Production Database)

**IMPORTANT:** This creates 20-50 test users with 200K GPS points each in your **production database**. Ensure you have adequate storage (~2-4GB for 4M-10M GPS points).

1. **Edit the SQL setup script:**
```bash
cd setup
vi create-test-users.sql

# Update these variables:
# - source_user_email: Your production user with ~200K GPS points
# - num_test_users: Number of test users (20-50 recommended)
# - time_shift_weeks: 1 is recommended
```

2. **Run the SQL script against production:**
```bash
psql -h <production-host> -p 5432 -U <username> -d geopulse -f create-test-users.sql
```

**Expected output:**
```
NOTICE:  Source user ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
NOTICE:  Creating 20 test users...
NOTICE:  Created user 1: loadtest-user-1@example.com with 200000 GPS points
...
NOTICE:  Successfully created 20 test users with GPS data!
NOTICE:  Total GPS points: 4000000
```

3. **Verify test users created:**
```sql
SELECT u.email, COUNT(g.id) as gps_points_count
FROM users u
LEFT JOIN gps_points g ON u.id = g.user_id
WHERE u.email LIKE 'loadtest-user-%@example.com'
GROUP BY u.email;
```

### Phase 2: Configure k6 Tests

1. **Navigate to k6 directory:**
```bash
cd ../k6
```

2. **Find your test data date range:**

Run this SQL query against your production database to find the min/max GPS timestamps:

```sql
SELECT
  MIN(timestamp) as start_date,
  MAX(timestamp) as end_date
FROM gps_points
WHERE user_id IN (
  SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com'
);
```

Example output:
```
     start_date      |       end_date
---------------------+---------------------
 2025-05-01 00:00:00 | 2026-05-20 23:59:59
```

3. **Set environment variables:**

k6 requires environment variables to be passed either via shell or `-e` flags. Choose one approach:

**Option A: Shell environment variables (Recommended)**

Create a `.env` file for reference (not read by k6, just for your documentation):
```bash
cp .env.example .env
vi .env
```

Set variables in your shell before running tests:
```bash
export BASE_URL=https://your-production-server.com
export NUM_TEST_USERS=20
export TEST_USER_PASSWORD=your-password-here
export TEST_DATA_START_DATE=2025-05-01T00:00:00.000Z
export TEST_DATA_END_DATE=2026-05-20T23:59:59.999Z

# Now run k6 tests
k6 run scenarios/mixed-workload.js
```

**Option B: Inline environment variables**

Pass variables inline (macOS/Linux):
```bash
BASE_URL=https://your-production-server.com \
NUM_TEST_USERS=20 \
TEST_USER_PASSWORD=your-password \
TEST_DATA_START_DATE=2025-05-01T00:00:00.000Z \
TEST_DATA_END_DATE=2026-05-20T23:59:59.999Z \
k6 run scenarios/mixed-workload.js
```

**Option C: k6 -e flags**

Pass variables using k6's `-e` flag:
```bash
k6 run \
  -e BASE_URL=https://your-production-server.com \
  -e NUM_TEST_USERS=20 \
  -e TEST_USER_PASSWORD=your-password \
  -e TEST_DATA_START_DATE=2025-05-01T00:00:00.000Z \
  -e TEST_DATA_END_DATE=2026-05-20T23:59:59.999Z \
  scenarios/mixed-workload.js
```

**Why date ranges are important**: The k6 tests generate date ranges for API queries (e.g., "last month", "last quarter"). The `TEST_DATA_START_DATE` and `TEST_DATA_END_DATE` variables ensure the generated ranges stay within your test data bounds, preventing queries that return empty results.

## Running Tests

### Quick Start (Recommended)

**Important**: Make sure you've set environment variables first (see Phase 2 above).

Run the mixed workload scenario (most realistic):
```bash
# If you exported variables in your shell:
k6 run scenarios/mixed-workload.js

# Or pass variables inline:
BASE_URL=https://your-server.com \
TEST_DATA_START_DATE=2025-05-01T00:00:00.000Z \
TEST_DATA_END_DATE=2026-05-20T23:59:59.999Z \
k6 run scenarios/mixed-workload.js
```

### Individual Endpoint Tests

Test specific endpoints:
```bash
# Timeline endpoint
k6 run scenarios/timeline-load.js

# Dashboard endpoint
k6 run scenarios/dashboard-load.js

# Journey Insights endpoint
k6 run scenarios/journey-insights-load.js

# Location Analytics endpoints
k6 run scenarios/location-analytics-load.js
```

### Load Profiles

Test with different load levels using the `LOAD_MULTIPLIER` environment variable:

```bash
# Light load (25 VUs) - Default
k6 run scenarios/mixed-workload.js
# Or explicitly:
LOAD_MULTIPLIER=1 k6 run scenarios/mixed-workload.js

# Medium load (50 VUs) - 2x
LOAD_MULTIPLIER=2 k6 run scenarios/mixed-workload.js

# Heavy load (100 VUs) - 4x
LOAD_MULTIPLIER=4 k6 run scenarios/mixed-workload.js

# Very heavy load (250 VUs) - 10x
LOAD_MULTIPLIER=10 k6 run scenarios/mixed-workload.js

# Extreme load (500 VUs) - 20x
LOAD_MULTIPLIER=20 k6 run scenarios/mixed-workload.js
```

The multiplier scales all scenarios proportionally while maintaining the distribution:
- **Timeline**: 40% of total VUs
- **Dashboard**: 30% of total VUs
- **Location Analytics**: 20% of total VUs
- **Journey Insights**: 10% of total VUs

**Examples:**
- `LOAD_MULTIPLIER=1` → 25 VUs total (10/7/5/3)
- `LOAD_MULTIPLIER=2` → 50 VUs total (20/14/10/6)
- `LOAD_MULTIPLIER=4` → 100 VUs total (40/28/20/12)
- `LOAD_MULTIPLIER=10` → 250 VUs total (100/70/50/30)

### Save Results

Save detailed results to JSON file:
```bash
k6 run scenarios/mixed-workload.js --out json=../results/test-$(date +%Y%m%d-%H%M%S).json
```

### Environment Variables

Override configuration via command line (inline approach):
```bash
BASE_URL=https://prod.example.com \
NUM_TEST_USERS=30 \
TEST_DATA_START_DATE=2025-05-01T00:00:00.000Z \
TEST_DATA_END_DATE=2026-05-20T23:59:59.999Z \
LOAD_MULTIPLIER=4 \
k6 run scenarios/mixed-workload.js
```

Or using k6's `-e` flag:
```bash
k6 run \
  -e BASE_URL=https://prod.example.com \
  -e NUM_TEST_USERS=30 \
  -e LOAD_MULTIPLIER=4 \
  scenarios/mixed-workload.js
```

**Complete example** with heavy load (100 VUs):
```bash
export BASE_URL=https://your-production-server.com
export NUM_TEST_USERS=20
export TEST_USER_PASSWORD=your-password
export TEST_DATA_START_DATE=2025-05-01T00:00:00.000Z
export TEST_DATA_END_DATE=2026-05-20T23:59:59.999Z
export LOAD_MULTIPLIER=4

k6 run scenarios/mixed-workload.js
```

**Note**: Always include `TEST_DATA_START_DATE` and `TEST_DATA_END_DATE` to keep queries within test data bounds.

## Test Scenarios

### Mixed Workload (Recommended)

**File:** `scenarios/mixed-workload.js`

Simulates realistic user distribution across endpoints:
- **40% Timeline users** (10 VUs) - Browsing timeline with date ranges
- **30% Dashboard users** (7 VUs) - Viewing statistics and analytics
- **20% Location Analytics users** (5 VUs) - Exploring cities/countries
- **10% Journey Insights users** (3 VUs) - Viewing journey insights

**Duration:** 14 minutes (2 min ramp-up, 10 min sustained, 2 min ramp-down)

**Run:**
```bash
k6 run scenarios/mixed-workload.js
```

### Timeline Load Test

Tests `/api/streaming-timeline` with various date ranges (day, week, month, quarter).

**Run:**
```bash
k6 run scenarios/timeline-load.js
```

### Dashboard Load Test

Tests `/api/statistics` with various date ranges.

**Run:**
```bash
k6 run scenarios/dashboard-load.js
```

### Journey Insights Load Test

Tests `/api/journey-insights` endpoint (no date range parameters).

**Run:**
```bash
k6 run scenarios/journey-insights-load.js
```

### Location Analytics Load Test

Tests `/api/location-analytics/*` endpoints (cities, countries, search).

**Run:**
```bash
k6 run scenarios/location-analytics-load.js
```

## Monitoring During Tests

### Terminal 1: Run k6
```bash
k6 run scenarios/mixed-workload.js
```

### Terminal 2: Monitor Production Database

**Active connections:**
```bash
watch -n 5 'psql -h <prod-host> -U <user> -d geopulse -c "SELECT count(*) FROM pg_stat_activity WHERE state = '\''active'\'';"'
```

**Cache hit ratio:**
```bash
psql -h <prod-host> -U <user> -d geopulse -c "
  SELECT blks_hit::float/(blks_read + blks_hit) as cache_hit_ratio
  FROM pg_stat_database
  WHERE datname = 'geopulse';
"
```

**Slow queries:**
```bash
psql -h <prod-host> -U <user> -d geopulse -c "
  SELECT query, calls, mean_time
  FROM pg_stat_statements
  ORDER BY mean_time DESC
  LIMIT 10;
"
```

## Understanding Results

### k6 Output

k6 displays real-time metrics:
```
     ✓ status is 200
     ✓ response is JSON
     ✓ has success status

     checks.........................: 99.80% ✓ 14970     ✗ 30
     data_received..................: 25 MB  41 kB/s
     data_sent......................: 1.8 MB 3.0 kB/s
     http_req_blocked...............: avg=1.12ms   min=1µs    med=4µs    max=89.99ms  p(95)=6µs    p(99)=72.89ms
     http_req_connecting............: avg=559.27µs min=0s     med=0s     max=44.87ms  p(95)=0s     p(99)=36.06ms
     http_req_duration..............: avg=1.21s    min=87.8ms med=1.02s  max=4.98s    p(95)=2.45s  p(99)=3.89s
     http_req_failed................: 0.20%  ✓ 10        ✗ 4990
     http_req_receiving.............: avg=459.59µs min=25µs   med=85µs   max=67.02ms  p(95)=1.13ms p(99)=3.04ms
     http_req_sending...............: avg=29.91µs  min=5µs    med=18µs   max=1.89ms   p(95)=51µs   p(99)=134.89µs
     http_req_tls_handshaking.......: avg=550.86µs min=0s     med=0s     max=44.81ms  p(95)=0s     p(99)=35.98ms
     http_req_waiting...............: avg=1.21s    min=87.66ms med=1.02s  max=4.98s    p(95)=2.45s  p(99)=3.89s
     http_reqs......................: 5000   8.326815/s
     iteration_duration.............: avg=4.32s    min=2.18s  med=4.03s  max=10.02s   p(95)=7.56s  p(99)=9.12s
     iterations.....................: 1250   2.081704/s
     vus............................: 25     min=0       max=25
     vus_max........................: 25     min=25      max=25
```

### Key Metrics

**Response Time:**
- `http_req_duration` p95 - 95% of requests complete within this time
- `http_req_duration` p99 - 99% of requests complete within this time

**Error Rate:**
- `http_req_failed` - Percentage of failed requests (target: < 1%)
- `checks` - Percentage of passed validation checks

**Throughput:**
- `http_reqs` - Requests per second
- `data_received` - Data throughput (MB/s)

### Performance Thresholds

**Timeline:** P95 < 2s, P99 < 5s, Error rate < 1%
**Dashboard:** P95 < 1.5s, P99 < 3s, Error rate < 1%
**Journey Insights:** P95 < 3s, P99 < 8s, Error rate < 1%
**Location Analytics:** P95 < 1s, P99 < 2s, Error rate < 1%

## Production Safety Guidelines

⚠️ **IMPORTANT:** You're testing against production!

1. **Initial Test Run:**
   - Start with **light load** during **low-traffic period**
   - Monitor production metrics (CPU, memory, DB connections)
   - Verify no impact on real users

2. **Read-Only Operations:**
   - All tests only perform GET requests
   - No POST/PUT/DELETE operations
   - No data modification

3. **Test User Identification:**
   - Test users: `loadtest-user-*@example.com`
   - Easy to filter from analytics/metrics
   - Clearly documented

4. **Database Impact:**
   - 20 users × 200K points = 4M GPS points (~2-4GB storage)
   - Monitor database size and performance
   - Consider running `VACUUM ANALYZE` after setup

5. **Gradual Load Increase:**
   - Start: Light load (5 VUs)
   - Then: Moderate load (25 VUs)
   - Finally: Heavy load (50 VUs)
   - Monitor at each step

6. **Rollback Plan:**
   - Keep `cleanup-test-users.sql` ready
   - Can quickly remove test users if issues arise

## Cleanup (Optional)

**⚠️ WARNING:** This permanently deletes all test users and their data!

Only run if you want to remove test users from production:
```bash
cd setup
psql -h <production-host> -U <username> -d geopulse -f cleanup-test-users.sql
```

**Reclaim disk space after cleanup:**
```sql
VACUUM ANALYZE gps_points;
VACUUM ANALYZE users;
```

## Troubleshooting

### Authentication Failures

**Error:** `Authentication failed for loadtest-user-X@example.com`

**Solution:** Verify `TEST_USER_PASSWORD` in `.env` matches the password used in SQL setup.

### Connection Refused

**Error:** `dial: i/o timeout` or `connection refused`

**Solution:** Verify `BASE_URL` in `.env` and check network access to production server.

### Token Expiration

**Error:** `401 Unauthorized` after long test runs

**Solution:** Token auto-refresh is implemented. If still failing, check server logs for JWT issues.

### High Error Rates

**Error:** `http_req_failed` > 5%

**Solution:**
- Check production server health
- Verify database connection pool size
- Review slow query logs
- Reduce load (use light profile)

## Project Structure

```
tests/performance/
├── README.md                              # This file
├── setup/                                 # One-time setup (SQL)
│   ├── create-test-users.sql             # Creates test users with GPS data
│   └── cleanup-test-users.sql            # Removes test users (optional)
├── k6/                                    # k6 test scripts
│   ├── scenarios/
│   │   ├── timeline-load.js              # Timeline endpoint test
│   │   ├── dashboard-load.js             # Dashboard endpoint test
│   │   ├── journey-insights-load.js      # Journey insights test
│   │   ├── location-analytics-load.js    # Location analytics test
│   │   └── mixed-workload.js             # Realistic mixed scenario (recommended)
│   ├── utils/
│   │   ├── auth.js                       # JWT auth & token refresh
│   │   ├── config.js                     # Configuration management
│   │   └── helpers.js                    # Date ranges, random sleep, etc.
│   ├── config/
│   │   ├── light-load.json               # 5 VUs, 5 min
│   │   ├── moderate-load.json            # 25 VUs, 14 min
│   │   └── heavy-load.json               # 50 VUs, 21 min
│   └── .env.example                      # Environment template
├── results/                               # Test results (gitignored)
└── .gitignore                             # Git ignore rules
```

## Best Practices

1. **Test During Low-Traffic Periods:**
   - Run initial tests during off-peak hours
   - Gradually increase load

2. **Monitor Production Metrics:**
   - CPU, memory, disk I/O
   - Database connections and query performance
   - Application logs

3. **Establish Baselines:**
   - Run tests regularly (weekly/monthly)
   - Compare against baseline metrics
   - Detect performance regressions early

4. **Reusable Tests:**
   - Test users are persistent
   - No setup/teardown needed per run
   - Can run multiple times without database changes

5. **Document Results:**
   - Save results to JSON files
   - Track performance trends over time
   - Share findings with team

## Support

For issues or questions:
- Check troubleshooting section above
- Review k6 documentation: https://k6.io/docs/
- Consult GeoPulse backend team

## License

Same as GeoPulse project.
