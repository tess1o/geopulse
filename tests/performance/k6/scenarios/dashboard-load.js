/**
 * GeoPulse k6 Load Test - Dashboard/Statistics Endpoint
 *
 * Tests the /api/statistics endpoint with various date ranges.
 * This endpoint returns comprehensive statistics including distance, places, routes, etc.
 */

import http from 'k6/http';
import { check } from 'k6';
import { AuthManager } from '../utils/auth.js';
import { config, getAllTestUsers, buildThresholds } from '../utils/config.js';
import {
  generateDateRange,
  randomSleep,
  selectUser,
  buildUrl,
  commonChecks,
  logTestStart,
  randomItem,
} from '../utils/helpers.js';

// Load test configuration
export const options = {
  stages: [
    { duration: '2m', target: 10 },   // Ramp-up to 10 users
    { duration: '5m', target: 20 },   // Ramp-up to 20 users
    { duration: '10m', target: 20 },  // Sustained load at 20 users
    { duration: '2m', target: 0 },    // Ramp-down to 0 users
  ],

  thresholds: buildThresholds('dashboard'),

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Global auth managers cache (one per VU)
const authManagers = {};

// Setup function
export function setup() {
  logTestStart('Dashboard/Statistics Endpoint Load Test', options);

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

// Main test function
export default function (data) {
  const auth = getAuthManager(data.users);

  // Test various date ranges
  const rangeTypes = ['week', 'month', 'quarter'];
  const rangeType = randomItem(rangeTypes);
  const dateRange = generateDateRange(rangeType);

  // Build statistics API URL
  const statsUrl = buildUrl(`${config.baseUrl}/api/statistics`, {
    startTime: dateRange.start,
    endTime: dateRange.end,
  });

  // Make request to statistics endpoint
  const response = http.get(statsUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'dashboard', range: rangeType },
  });

  // Verify response
  check(response, {
    ...commonChecks(200),
    'has totalDistanceMeters': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.totalDistanceMeters !== undefined;
      } catch (e) {
        return false;
      }
    },
    'has timeMoving': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.timeMoving !== undefined;
      } catch (e) {
        return false;
      }
    },
    'has uniqueLocationsCount': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.uniqueLocationsCount !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 2s': (r) => r.timings.duration < 2000,
  });

  if (response.status !== 200) {
    console.error(`Dashboard request failed: ${response.status} ${response.body}`);
  }

  // Think time - simulate user reviewing dashboard
  randomSleep(3, 6);
}

export function teardown(data) {
  console.log('✓ Dashboard load test completed');
}
