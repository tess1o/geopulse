/**
 * GeoPulse k6 Load Testing - Helper Functions
 *
 * Common utility functions for load testing scenarios.
 */

import { sleep } from 'k6';
import { config } from './config.js';

/**
 * Generate ISO-8601 date range for API queries
 *
 * Creates start and end timestamps based on the range type.
 * Ranges are calculated backwards from test data end date (if configured)
 * or current date/time.
 *
 * @param {string} rangeType - Range type (day, week, month, quarter, year)
 * @param {Date} endDate - Optional end date (defaults to test data end or now)
 * @returns {Object} { start: ISO-8601 string, end: ISO-8601 string }
 */
export function generateDateRange(rangeType = 'month', endDate = null) {
  // Use test data end date if available, otherwise current date or provided endDate
  let end;
  if (config.testDataRanges.endDate) {
    end = new Date(config.testDataRanges.endDate);
  } else {
    end = endDate || new Date();
  }

  // Get days for this range type
  const rangeDays = config.dateRanges[rangeType]?.days || config.dateRanges.month.days;

  // Calculate start date
  const start = new Date(end);
  start.setDate(start.getDate() - rangeDays);

  // Ensure start doesn't go before test data start date
  if (config.testDataRanges.startDate) {
    const minDate = new Date(config.testDataRanges.startDate);
    if (start < minDate) {
      start.setTime(minDate.getTime());
    }
  }

  return {
    start: start.toISOString(),
    end: end.toISOString(),
  };
}

/**
 * Generate date range for a specific date
 *
 * Useful for testing queries on specific dates (e.g., today, specific day).
 *
 * @param {Date} date - The date to generate range for
 * @returns {Object} { start: ISO-8601 string (midnight), end: ISO-8601 string (23:59:59) }
 */
export function generateSingleDayRange(date) {
  const start = new Date(date);
  start.setHours(0, 0, 0, 0);

  const end = new Date(date);
  end.setHours(23, 59, 59, 999);

  return {
    start: start.toISOString(),
    end: end.toISOString(),
  };
}

/**
 * Generate today's date range
 *
 * @returns {Object} { start: ISO-8601 string, end: ISO-8601 string }
 */
export function generateTodayRange() {
  return generateSingleDayRange(new Date());
}

/**
 * Generate random date range within the past year (or test data bounds)
 *
 * Useful for varied testing scenarios.
 * If test data ranges are configured, generates within those bounds.
 *
 * @param {number} minDays - Minimum days in range (default: 7)
 * @param {number} maxDays - Maximum days in range (default: 90)
 * @returns {Object} { start: ISO-8601 string, end: ISO-8601 string }
 */
export function generateRandomDateRange(minDays = 7, maxDays = 90) {
  // If test data ranges configured, generate within those bounds
  if (config.testDataRanges.startDate && config.testDataRanges.endDate) {
    const minDate = new Date(config.testDataRanges.startDate);
    const maxDate = new Date(config.testDataRanges.endDate);
    const availableRangeDays = (maxDate - minDate) / (1000 * 60 * 60 * 24);

    // Adjust maxDays if it exceeds available range
    const actualMaxDays = Math.min(maxDays, availableRangeDays);
    const rangeDays = Math.floor(Math.random() * (actualMaxDays - minDays + 1)) + minDays;

    // Random end date between (minDate + rangeDays) and maxDate
    const earliestEnd = new Date(minDate.getTime() + rangeDays * 24 * 60 * 60 * 1000);
    const latestEnd = maxDate;
    const endTime = earliestEnd.getTime() + Math.random() * (latestEnd.getTime() - earliestEnd.getTime());

    const end = new Date(endTime);
    const start = new Date(end.getTime() - rangeDays * 24 * 60 * 60 * 1000);

    return {
      start: start.toISOString(),
      end: end.toISOString(),
    };
  }

  // Original behavior if no test data ranges configured
  const rangeDays = Math.floor(Math.random() * (maxDays - minDays + 1)) + minDays;

  // Random end date within past year
  const maxDaysBack = 365;
  const daysBack = Math.floor(Math.random() * (maxDaysBack - rangeDays));

  const end = new Date();
  end.setDate(end.getDate() - daysBack);

  const start = new Date(end);
  start.setDate(start.getDate() - rangeDays);

  return {
    start: start.toISOString(),
    end: end.toISOString(),
  };
}

/**
 * Random sleep (think time) between requests
 *
 * Simulates realistic user behavior by adding random pauses between requests.
 *
 * @param {number} min - Minimum sleep time in seconds (default: from config)
 * @param {number} max - Maximum sleep time in seconds (default: from config)
 */
export function randomSleep(min = null, max = null) {
  const minSleep = min !== null ? min : config.thinkTime.min;
  const maxSleep = max !== null ? max : config.thinkTime.max;

  const sleepTime = minSleep + Math.random() * (maxSleep - minSleep);
  sleep(sleepTime);
}

/**
 * Select user for Virtual User (VU)
 *
 * Maps VU ID to a test user in round-robin fashion.
 *
 * @param {Array} users - Array of user objects
 * @param {number} vuId - Virtual User ID (from __VU)
 * @returns {Object} User object
 */
export function selectUser(users, vuId) {
  // Use modulo to cycle through users
  const index = (vuId - 1) % users.length;  // VU IDs start at 1
  return users[index];
}

/**
 * Select random user
 *
 * @param {Array} users - Array of user objects
 * @returns {Object} User object
 */
export function selectRandomUser(users) {
  const index = Math.floor(Math.random() * users.length);
  return users[index];
}

/**
 * Format URL with query parameters
 *
 * Helper to build URLs with query parameters.
 *
 * @param {string} baseUrl - Base URL (without query string)
 * @param {Object} params - Query parameters as key-value pairs
 * @returns {string} Full URL with query string
 */
export function buildUrl(baseUrl, params = {}) {
  const queryParams = Object.entries(params)
    .filter(([_, value]) => value !== null && value !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');

  return queryParams ? `${baseUrl}?${queryParams}` : baseUrl;
}

/**
 * Common response checks
 *
 * Returns a set of common checks for HTTP responses.
 *
 * @param {number} expectedStatus - Expected HTTP status code (default: 200)
 * @param {string} dataField - Optional field name in response.data to check
 * @returns {Object} k6 checks object
 */
export function commonChecks(expectedStatus = 200, dataField = null) {
  const checks = {
    [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
    'response is JSON': (r) => {
      try {
        JSON.parse(r.body);
        return true;
      } catch (e) {
        return false;
      }
    },
    'has success status': (r) => {
      try {
        const body = r.json();
        return body?.status === undefined || body.status === 'success';
      } catch (e) {
        return false;
      }
    },
  };

  if (dataField) {
    checks[`has ${dataField} data`] = (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data[dataField] !== undefined;
      } catch (e) {
        return false;
      }
    };
  }

  return checks;
}

/**
 * Log test start information
 *
 * Logs helpful information at the start of a test.
 *
 * @param {string} scenarioName - Name of the scenario
 * @param {Object} options - Test options
 */
export function logTestStart(scenarioName, options) {
  console.log('═══════════════════════════════════════════════════════');
  console.log(`  ${scenarioName}`);
  console.log('═══════════════════════════════════════════════════════');
  console.log(`  Base URL: ${config.baseUrl}`);
  console.log(`  Test users: ${config.users.count}`);
  if (options.stages) {
    console.log('  Load pattern:');
    options.stages.forEach((stage, i) => {
      console.log(`    Stage ${i + 1}: ${stage.target} VUs for ${stage.duration}`);
    });
  }
  console.log('═══════════════════════════════════════════════════════');
}

/**
 * Get random item from array
 *
 * @param {Array} array - Array to select from
 * @returns {*} Random item from array
 */
export function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

/**
 * Generate random integer between min and max (inclusive)
 *
 * @param {number} min - Minimum value
 * @param {number} max - Maximum value
 * @returns {number} Random integer
 */
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Weighted random selection
 *
 * Select an item based on weights. Higher weight = higher probability.
 *
 * @param {Array} items - Array of items
 * @param {Array} weights - Array of weights (same length as items)
 * @returns {*} Selected item
 */
export function weightedRandom(items, weights) {
  const totalWeight = weights.reduce((sum, weight) => sum + weight, 0);
  let random = Math.random() * totalWeight;

  for (let i = 0; i < items.length; i++) {
    random -= weights[i];
    if (random <= 0) {
      return items[i];
    }
  }

  return items[items.length - 1];
}
