/**
 * GeoPulse k6 Load Testing - Authentication Manager
 *
 * Handles JWT token authentication with automatic token refresh
 * for GeoPulse API load testing.
 */

import http from 'k6/http';
import { check, fail } from 'k6';
import { config } from './config.js';

/**
 * Authentication Manager Class
 *
 * Manages JWT access and refresh tokens for a single user session.
 * Automatically refreshes access tokens before expiry to maintain
 * uninterrupted test execution.
 */
export class AuthManager {
  constructor(email, password) {
    this.email = email;
    this.password = password;
    this.accessToken = null;
    this.refreshToken = null;
    this.tokenExpiry = null;
  }

  /**
   * Login and obtain JWT tokens
   *
   * Uses /api/auth/api-login endpoint which returns tokens in response body
   * (not cookies), making it suitable for API testing.
   *
   * @returns {boolean} true if login successful, false otherwise
   */
  login() {
    const loginUrl = `${config.baseUrl}/api/auth/api-login`;

    const payload = JSON.stringify({
      email: this.email,
      password: this.password,
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
      },
      tags: { endpoint: 'auth-login' },
    };

    const response = http.post(loginUrl, payload, params);

    const success = check(response, {
      'login status is 200': (r) => r.status === 200,
      'login response has data': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.data && body.data.accessToken && body.data.refreshToken;
        } catch (e) {
          return false;
        }
      },
    });

    if (!success) {
      console.error(`Login failed for ${this.email}: ${response.status} ${response.body}`);
      fail(`Authentication failed for ${this.email}`);
      return false;
    }

    const body = JSON.parse(response.body);
    console.log(`Login successful for ${this.email}`);
    this.accessToken = body.data.accessToken;
    this.refreshToken = body.data.refreshToken;

    // expiresIn is in seconds (default: 1800 = 30 minutes)
    const expiresInMs = (body.data.expiresIn || 1800) * 1000;
    this.tokenExpiry = Date.now() + expiresInMs;

    return true;
  }

  /**
   * Get authorization header
   *
   * Returns Authorization header with Bearer token.
   * Automatically refreshes token if it will expire within 2 minutes.
   *
   * @returns {Object} Headers object with Authorization
   */
  getAuthHeader() {
    // Check if we need to refresh the token (less than 2 minutes remaining)
    if (this.needsRefresh()) {
      this.refreshAccessToken();
    }

    return {
      'Authorization': `Bearer ${this.accessToken}`,
      'Content-Type': 'application/json',
    };
  }

  /**
   * Check if token needs refresh
   *
   * @returns {boolean} true if token expires in less than 2 minutes
   */
  needsRefresh() {
    if (!this.tokenExpiry || !this.refreshToken) {
      return false;
    }

    const twoMinutesInMs = 2 * 60 * 1000;
    return (this.tokenExpiry - Date.now()) < twoMinutesInMs;
  }

  /**
   * Refresh access token using refresh token
   *
   * Exchanges the refresh token for a new access token and refresh token.
   * Updates internal state with new tokens and expiry time.
   */
  refreshAccessToken() {
    const refreshUrl = `${config.baseUrl}/api/auth/refresh`;

    const payload = JSON.stringify({
      refreshToken: this.refreshToken,
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
      },
      tags: { endpoint: 'auth-refresh' },
    };

    const response = http.post(refreshUrl, payload, params);

    const success = check(response, {
      'token refresh status is 200': (r) => r.status === 200,
      'token refresh response has accessToken': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.accessToken !== undefined;
        } catch (e) {
          return false;
        }
      },
    });

    if (!success) {
      console.error(`Token refresh failed for ${this.email}: ${response.status} ${response.body}`);
      // Try to re-login if refresh fails
      console.log(`Attempting to re-login for ${this.email}...`);
      return this.login();
    }

    const body = JSON.parse(response.body);
    this.accessToken = body.accessToken;
    this.refreshToken = body.refreshToken || this.refreshToken; // Keep old refresh token if not returned

    const expiresInMs = (body.expiresIn || 1800) * 1000;
    this.tokenExpiry = Date.now() + expiresInMs;

    console.log(`Token refreshed for ${this.email}`);
  }

  /**
   * Get user email for logging/debugging
   *
   * @returns {string} User email
   */
  getEmail() {
    return this.email;
  }

  /**
   * Check if user is authenticated
   *
   * @returns {boolean} true if user has valid access token
   */
  isAuthenticated() {
    return this.accessToken !== null;
  }
}

/**
 * Create an AuthManager instance and perform login
 *
 * Helper function to create and login in one step.
 *
 * @param {string} email - User email
 * @param {string} password - User password
 * @returns {AuthManager|null} AuthManager instance or null if login failed
 */
export function createAndLogin(email, password) {
  const auth = new AuthManager(email, password);
  const success = auth.login();

  if (!success) {
    return null;
  }

  return auth;
}
