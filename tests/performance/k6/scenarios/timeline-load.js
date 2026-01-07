/**
 * GeoPulse k6 Load Test - Timeline Endpoint
 *
 * Tests the /api/streaming-timeline endpoint with various date ranges.
 * This endpoint returns trips, stays, and data gaps for a given time period.
 */

import http from 'k6/http';
import { check } from 'k6';
import { AuthManager } from '../utils/auth.js';
import { config, getAllTestUsers, buildThresholds } from '../utils/config.js';
import {
  generateDateRange,
  generateRandomDateRange,
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

  thresholds: buildThresholds('timeline'),

  // Additional global thresholds
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Global auth managers cache (one per VU)
const authManagers = {};

// Setup function - runs once before the test
export function setup() {
  logTestStart('Timeline Endpoint Load Test', options);

  // Return test user credentials for all VUs to use
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

// Main test function - runs for each Virtual User (VU)
export default function (data) {
  const auth = getAuthManager(data.users);

  // Vary the date ranges to test different scenarios
  const rangeTypes = ['week', 'month', 'quarter'];
  const rangeType = randomItem(rangeTypes);
  const dateRange = generateDateRange(rangeType);

  // Build timeline API URL with query parameters
  const timelineUrl = buildUrl(`${config.baseUrl}/api/streaming-timeline`, {
    startTime: dateRange.start,
    endTime: dateRange.end,
  });

  // Make request to timeline endpoint
  const response = http.get(timelineUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'timeline', range: rangeType },
  });

  // Verify response
  check(response, {
    ...commonChecks(200, 'stays'),
    'has trips data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.trips !== undefined;
      } catch (e) {
        return false;
      }
    },
    'has data gaps': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.dataGaps !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 3s': (r) => r.timings.duration < 3000,
  });

  // Log errors for debugging
  if (response.status !== 200) {
    console.error(`Timeline request failed: ${response.status} ${response.body}`);
  }

  // Think time - simulate user reading the timeline
  randomSleep(2, 5);
}

// Teardown function - runs once after the test
export function teardown(data) {
  console.log('✓ Timeline load test completed');
}
