/**
 * GeoPulse k6 Load Test - Location Analytics Endpoints
 *
 * Tests the /api/location-analytics/* endpoints:
 * - /api/location-analytics/cities
 * - /api/location-analytics/countries
 * - /api/location-analytics/search
 */

import http from 'k6/http';
import { check } from 'k6';
import { AuthManager } from '../utils/auth.js';
import { config, getAllTestUsers, buildThresholds } from '../utils/config.js';
import {
  randomSleep,
  selectUser,
  buildUrl,
  commonChecks,
  logTestStart,
  randomItem,
  weightedRandom,
} from '../utils/helpers.js';

// Load test configuration
export const options = {
  stages: [
    { duration: '2m', target: 10 },   // Ramp-up to 10 users
    { duration: '10m', target: 10 },  // Sustained load at 10 users
    { duration: '2m', target: 0 },    // Ramp-down to 0 users
  ],

  thresholds: buildThresholds('locationAnalytics'),

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Global auth managers cache (one per VU)
const authManagers = {};

// Setup function
export function setup() {
  logTestStart('Location Analytics Endpoints Load Test', options);

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

  // Vary the endpoint being tested
  // Weighted selection: cities and countries are accessed more frequently than search
  const endpoints = ['cities', 'countries', 'search'];
  const weights = [40, 40, 20];  // 40% cities, 40% countries, 20% search
  const endpoint = weightedRandom(endpoints, weights);

  let response;

  switch (endpoint) {
    case 'cities':
      response = testGetCities(auth);
      break;

    case 'countries':
      response = testGetCountries(auth);
      break;

    case 'search':
      response = testSearch(auth);
      break;
  }

  // Verify common response characteristics
  if (response && response.status !== 200) {
    console.error(`Location Analytics request failed: ${response.status} ${response.body}`);
  }

  // Think time - simulate user browsing locations
  randomSleep(2, 4);
}

/**
 * Test GET /api/location-analytics/cities
 */
function testGetCities(auth) {
  const citiesUrl = `${config.baseUrl}/api/location-analytics/cities`;

  const response = http.get(citiesUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'locationAnalytics', subEndpoint: 'cities' },
  });

  check(response, {
    ...commonChecks(200),
    'cities is array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.data);
      } catch (e) {
        return false;
      }
    },
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  return response;
}

/**
 * Test GET /api/location-analytics/countries
 */
function testGetCountries(auth) {
  const countriesUrl = `${config.baseUrl}/api/location-analytics/countries`;

  const response = http.get(countriesUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'locationAnalytics', subEndpoint: 'countries' },
  });

  check(response, {
    ...commonChecks(200),
    'countries is array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.data);
      } catch (e) {
        return false;
      }
    },
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  return response;
}

/**
 * Test GET /api/location-analytics/search
 */
function testSearch(auth) {
  // Sample search queries
  const searchQueries = ['New', 'San', 'Los', 'London', 'Paris', 'Test'];
  const query = randomItem(searchQueries);

  const searchUrl = buildUrl(`${config.baseUrl}/api/location-analytics/search`, {
    q: query,
  });

  const response = http.get(searchUrl, {
    headers: auth.getAuthHeader(),
    tags: { endpoint: 'locationAnalytics', subEndpoint: 'search' },
  });

  check(response, {
    ...commonChecks(200),
    'search results is array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.data);
      } catch (e) {
        return false;
      }
    },
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  return response;
}

export function teardown(data) {
  console.log('✓ Location Analytics load test completed');
}
