/**
 * GeoPulse k6 Load Test - Journey Insights Endpoint
 *
 * Tests the /api/journey-insights endpoint.
 * This endpoint returns comprehensive journey insights including geographic patterns,
 * time patterns, achievements, and distance traveled.
 */

import http from 'k6/http';
import { check } from 'k6';
import { AuthManager } from '../utils/auth.js';
import { config, getAllTestUsers, buildThresholds } from '../utils/config.js';
import {
  randomSleep,
  selectUser,
  commonChecks,
  logTestStart,
} from '../utils/helpers.js';

// Load test configuration
export const options = {
  stages: [
    { duration: '2m', target: 5 },    // Ramp-up to 5 users
    { duration: '5m', target: 10 },   // Ramp-up to 10 users
    { duration: '10m', target: 10 },  // Sustained load at 10 users
    { duration: '2m', target: 0 },    // Ramp-down to 0 users
  ],

  thresholds: buildThresholds('journeyInsights'),

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Global auth managers cache (one per VU)
const authManagers = {};

// Setup function
export function setup() {
  logTestStart('Journey Insights Endpoint Load Test', options);

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

  // Journey insights endpoint (no date range parameters)
  const insightsUrl = `${config.baseUrl}/api/journey-insights`;

  // Make request to journey insights endpoint
  const response = http.get(insightsUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'journeyInsights' },
  });

  // Verify response
  check(response, {
    ...commonChecks(200),
    'has geographic data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.geographic !== undefined;
      } catch (e) {
        return false;
      }
    },
    'has timePatterns data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.timePatterns !== undefined;
      } catch (e) {
        return false;
      }
    },
    'has achievements data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.achievements !== undefined;
      } catch (e) {
        return false;
      }
    },
    'has distanceTraveled data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.distanceTraveled !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 5s': (r) => r.timings.duration < 5000,
  });

  if (response.status !== 200) {
    console.error(`Journey Insights request failed: ${response.status} ${response.body}`);
  }

  // Think time - simulate user exploring insights
  randomSleep(4, 8);
}

export function teardown(data) {
  console.log('✓ Journey Insights load test completed');
}
