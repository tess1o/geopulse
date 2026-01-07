/**
 * GeoPulse k6 Load Testing - Configuration
 *
 * Centralized configuration for k6 load tests.
 * Uses environment variables for flexibility.
 */

/**
 * Load testing configuration
 *
 * Configuration can be overridden via environment variables:
 * - BASE_URL: Production server URL
 * - NUM_TEST_USERS: Number of test users (must match setup)
 * - TEST_USER_PASSWORD: Password for test users
 */
export const config = {
  // Production server URL
  baseUrl: __ENV.BASE_URL || 'http://localhost:8080',

  // Test user configuration
  users: {
    prefix: 'loadtest-user-',  // Test user email prefix
    count: parseInt(__ENV.NUM_TEST_USERS || '20'),  // Number of test users (20-50)
    password: __ENV.TEST_USER_PASSWORD || 'LoadTest123!',  // Password for all test users
  },

  // Performance thresholds
  // These define what constitutes acceptable performance
  thresholds: {
    // Response time thresholds (milliseconds)
    http_req_duration_p50: 1000,   // 50% of requests < 1s
    http_req_duration_p95: 2000,   // 95% of requests < 2s
    http_req_duration_p99: 5000,   // 99% of requests < 5s

    // Error rate threshold
    http_req_failed: 0.01,  // < 1% failure rate
  },

  // Endpoint-specific thresholds
  endpoints: {
    timeline: {
      p95: 2000,  // 95% < 2s
      p99: 5000,  // 99% < 5s
      errorRate: 0.01,
    },
    dashboard: {
      p95: 1500,  // 95% < 1.5s
      p99: 3000,  // 99% < 3s
      errorRate: 0.01,
    },
    journeyInsights: {
      p95: 3000,  // 95% < 3s (complex aggregations)
      p99: 8000,  // 99% < 8s
      errorRate: 0.01,
    },
    locationAnalytics: {
      p95: 1000,  // 95% < 1s
      p99: 2000,  // 99% < 2s
      errorRate: 0.01,
    },
  },

  // Think time configuration (seconds)
  // Simulates realistic user behavior with pauses between requests
  thinkTime: {
    min: 2,
    max: 5,
  },

  // Date range presets for testing
  // These define different time periods to test
  dateRanges: {
    day: { days: 1 },
    week: { days: 7 },
    month: { days: 30 },
    quarter: { days: 90 },
    year: { days: 365 },
  },

  // Test data date ranges (loaded from environment variables)
  // When set, date range queries will be constrained to these bounds
  testDataRanges: {
    startDate: __ENV.TEST_DATA_START_DATE || null,
    endDate: __ENV.TEST_DATA_END_DATE || null,
  },
};

/**
 * Get user email by index
 *
 * @param {number} index - User index (0-based)
 * @returns {string} User email
 */
export function getUserEmail(index) {
  const userNumber = (index % config.users.count) + 1;  // 1-based user number
  return `${config.users.prefix}${userNumber}@example.com`;
}

/**
 * Get user credentials by index
 *
 * @param {number} index - User index (0-based)
 * @returns {Object} { email, password }
 */
export function getUserCredentials(index) {
  return {
    email: getUserEmail(index),
    password: config.users.password,
  };
}

/**
 * Generate array of all test user credentials
 *
 * @returns {Array} Array of { email, password } objects
 */
export function getAllTestUsers() {
  const users = [];
  for (let i = 0; i < config.users.count; i++) {
    users.push(getUserCredentials(i));
  }
  return users;
}

/**
 * Get threshold configuration for a specific endpoint
 *
 * @param {string} endpoint - Endpoint name (timeline, dashboard, etc.)
 * @returns {Object} Threshold configuration
 */
export function getEndpointThresholds(endpoint) {
  return config.endpoints[endpoint] || config.thresholds;
}

/**
 * Build k6 threshold object for options
 *
 * @param {string} endpoint - Endpoint name for tagging
 * @returns {Object} k6 thresholds object
 */
export function buildThresholds(endpoint) {
  const thresholds = getEndpointThresholds(endpoint);

  return {
    [`http_req_duration{endpoint:${endpoint}}`]: [
      `p(95)<${thresholds.p95}`,
      `p(99)<${thresholds.p99}`,
    ],
    [`http_req_failed{endpoint:${endpoint}}`]: [
      `rate<${thresholds.errorRate}`,
    ],
  };
}

/**
 * Validate configuration
 *
 * Checks that all required configuration is present and valid.
 * Call this at the start of your test.
 */
export function validateConfig() {
  const errors = [];

  if (!config.baseUrl) {
    errors.push('BASE_URL environment variable is required');
  }

  if (config.users.count < 1 || config.users.count > 100) {
    errors.push('NUM_TEST_USERS must be between 1 and 100');
  }

  if (!config.users.password || config.users.password.length < 8) {
    errors.push('TEST_USER_PASSWORD must be at least 8 characters');
  }

  if (errors.length > 0) {
    throw new Error(`Configuration validation failed:\n${errors.join('\n')}`);
  }

  console.log('✓ Configuration validated successfully');
  console.log(`  Base URL: ${config.baseUrl}`);
  console.log(`  Test users: ${config.users.count}`);
}
