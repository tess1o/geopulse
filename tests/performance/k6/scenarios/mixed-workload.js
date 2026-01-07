/**
 * GeoPulse k6 Load Test - Mixed Workload (Recommended)
 *
 * Simulates realistic user behavior by distributing load across multiple endpoints:
 * - Timeline (40% of users)
 * - Dashboard (30% of users)
 * - Location Analytics (20% of users)
 * - Journey Insights (10% of users)
 *
 * This is the most realistic test scenario and should be used for
 * comprehensive performance evaluation.
 */

import http from 'k6/http';
import { check } from 'k6';
import { AuthManager } from '../utils/auth.js';
import { config, getAllTestUsers } from '../utils/config.js';
import {
  generateDateRange,
  randomSleep,
  selectUser,
  buildUrl,
  commonChecks,
  logTestStart,
  randomItem,
} from '../utils/helpers.js';

// Load multiplier (can be overridden via environment variable)
// Examples:
//   LOAD_MULTIPLIER=1 k6 run ... (25 VUs - default)
//   LOAD_MULTIPLIER=2 k6 run ... (50 VUs)
//   LOAD_MULTIPLIER=4 k6 run ... (100 VUs)
//   LOAD_MULTIPLIER=10 k6 run ... (250 VUs)
const LOAD_MULTIPLIER = parseFloat(__ENV.LOAD_MULTIPLIER || '1');

// Base VU counts (will be multiplied by LOAD_MULTIPLIER)
const BASE_VUS = {
  timeline: 10,    // 40% of load
  dashboard: 7,    // 30% of load
  location: 5,     // 20% of load
  insights: 3,     // 10% of load
};

// Calculate actual VUs based on multiplier
const VUS = {
  timeline: Math.floor(BASE_VUS.timeline * LOAD_MULTIPLIER),
  dashboard: Math.floor(BASE_VUS.dashboard * LOAD_MULTIPLIER),
  location: Math.floor(BASE_VUS.location * LOAD_MULTIPLIER),
  insights: Math.floor(BASE_VUS.insights * LOAD_MULTIPLIER),
};

// Load test configuration with multiple scenarios
export const options = {
  scenarios: {
    // Timeline users (40% of load)
    timeline_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS.timeline },   // Ramp-up
        { duration: '10m', target: VUS.timeline },  // Sustained
        { duration: '2m', target: 0 },              // Ramp-down
      ],
      exec: 'timelineScenario',
      gracefulStop: '30s',
    },

    // Dashboard users (30% of load)
    dashboard_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS.dashboard },
        { duration: '10m', target: VUS.dashboard },
        { duration: '2m', target: 0 },
      ],
      exec: 'dashboardScenario',
      gracefulStop: '30s',
    },

    // Location Analytics users (20% of load)
    location_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS.location },
        { duration: '10m', target: VUS.location },
        { duration: '2m', target: 0 },
      ],
      exec: 'locationScenario',
      gracefulStop: '30s',
    },

    // Journey Insights users (10% of load)
    insights_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS.insights },
        { duration: '10m', target: VUS.insights },
        { duration: '2m', target: 0 },
      ],
      exec: 'insightsScenario',
      gracefulStop: '30s',
    },
  },

  // Overall thresholds (across all scenarios)
  thresholds: {
    'http_req_duration': ['p(95)<3000', 'p(99)<8000'],
    'http_req_failed': ['rate<0.02'],  // < 2% error rate
    'http_req_duration{endpoint:timeline}': ['p(95)<2000', 'p(99)<5000'],
    'http_req_duration{endpoint:dashboard}': ['p(95)<1500', 'p(99)<3000'],
    'http_req_duration{endpoint:journeyInsights}': ['p(95)<3000', 'p(99)<8000'],
    'http_req_duration{endpoint:locationAnalytics}': ['p(95)<1000', 'p(99)<2000'],
  },

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Global auth managers cache (one per VU)
const authManagers = {};

// Setup function
export function setup() {
  logTestStart('Mixed Workload Load Test', options);

  const totalVUs = VUS.timeline + VUS.dashboard + VUS.location + VUS.insights;

  console.log('');
  console.log('Load Configuration:');
  console.log(`  Load Multiplier:         ${LOAD_MULTIPLIER}x`);
  console.log('  ────────────────────────────────────');
  console.log('Scenario Distribution:');
  console.log(`  Timeline users:          40% (${VUS.timeline} VUs)`);
  console.log(`  Dashboard users:         30% (${VUS.dashboard} VUs)`);
  console.log(`  Location Analytics:      20% (${VUS.location} VUs)`);
  console.log(`  Journey Insights users:  10% (${VUS.insights} VUs)`);
  console.log('  ────────────────────────────────────');
  console.log(`  Total concurrent users:  ${totalVUs} VUs`);
  console.log('═══════════════════════════════════════════════════════');
  console.log('');

  return {
    users: getAllTestUsers(),
  };
}

/**
 * Get or create AuthManager for current VU
 */
function getAuthManager(users) {
  const user = selectUser(users, __VU);

  // Create auth manager if it doesn't exist for this VU
  if (!authManagers[__VU]) {
    authManagers[__VU] = new AuthManager(user.email, user.password);
    authManagers[__VU].login();
  }

  return authManagers[__VU];
}

/**
 * Timeline Scenario - Tests /api/streaming-timeline
 */
export function timelineScenario(data) {
  const auth = getAuthManager(data.users);

  const rangeTypes = ['week', 'month', 'quarter'];
  const rangeType = randomItem(rangeTypes);
  const dateRange = generateDateRange(rangeType);

  const timelineUrl = buildUrl(`${config.baseUrl}/api/streaming-timeline`, {
    startTime: dateRange.start,
    endTime: dateRange.end,
  });

  const response = http.get(timelineUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'timeline', range: rangeType },
  });

  check(response, {
    ...commonChecks(200, 'stays'),
    'has trips': (r) => JSON.parse(r.body).data?.trips !== undefined,
  });

  randomSleep(2, 5);
}

/**
 * Dashboard Scenario - Tests /api/statistics
 */
export function dashboardScenario(data) {
  const auth = getAuthManager(data.users);

  const rangeTypes = ['week', 'month', 'quarter'];
  const rangeType = randomItem(rangeTypes);
  const dateRange = generateDateRange(rangeType);

  const statsUrl = buildUrl(`${config.baseUrl}/api/statistics`, {
    startTime: dateRange.start,
    endTime: dateRange.end,
  });

  const response = http.get(statsUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'dashboard', range: rangeType },
  });

  check(response, {
    ...commonChecks(200),
    'has distance': (r) => JSON.parse(r.body).totalDistanceMeters !== undefined,
  });

  randomSleep(3, 6);
}

/**
 * Location Analytics Scenario - Tests /api/location-analytics/*
 */
export function locationScenario(data) {
  const auth = getAuthManager(data.users);

  const endpoints = ['cities', 'countries'];
  const endpoint = randomItem(endpoints);

  const locationUrl = `${config.baseUrl}/api/location-analytics/${endpoint}`;

  const response = http.get(locationUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'locationAnalytics', subEndpoint: endpoint },
  });

  check(response, {
    ...commonChecks(200),
    'is array': (r) => Array.isArray(JSON.parse(r.body).data),
  });

  randomSleep(2, 4);
}

/**
 * Journey Insights Scenario - Tests /api/journey-insights
 */
export function insightsScenario(data) {
  const auth = getAuthManager(data.users);

  const insightsUrl = `${config.baseUrl}/api/journey-insights`;

  const response = http.get(insightsUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'journeyInsights' },
  });

  check(response, {
    ...commonChecks(200),
    'has geographic': (r) => JSON.parse(r.body).geographic !== undefined,
  });

  randomSleep(4, 8);
}

// Teardown function
export function teardown(data) {
  console.log('');
  console.log('═══════════════════════════════════════════════════════');
  console.log('✓ Mixed workload load test completed');
  console.log('═══════════════════════════════════════════════════════');
}
