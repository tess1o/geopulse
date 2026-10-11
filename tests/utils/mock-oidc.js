import { expect } from '@playwright/test';

/**
 * Client for the fake OpenID Connect provider (demo-integrations/cmd/fake-oidc, served on port 9400 of
 * the geopulse-demo-integrations-e2e service). GeoPulse and the browser reach it by its service name;
 * the test process drives its control API, which a host run reaches on the published port.
 */
export const MOCK_OIDC_PUBLIC_URL = 'http://geopulse-demo-integrations-e2e:9400';
const CONTROL_URL = (process.env.MOCK_OIDC_CONTROL_URL || 'http://localhost:9400').replace(/\/+$/, '');

async function control(method, realm, body) {
  const response = await fetch(`${CONTROL_URL}/__control/realms/${realm}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`Mock OIDC ${method} ${realm} failed: ${response.status} ${await response.text()}`);
  }
  return response.status === 204 ? null : response.json();
}

export class MockOidc {
  /**
   * @param {string} realm - Realm (and GeoPulse provider) name
   * @param {Object} options - clientId, clientSecret, endSession (advertise end_session_endpoint),
   *   authorizeError (OAuth error the authorize endpoint returns), tokenError (token endpoint fails)
   */
  static createRealm(realm, options = {}) {
    return control('PUT', realm, options);
  }

  static updateRealm(realm, patch) {
    return control('PATCH', realm, patch);
  }

  static async stats(realm) {
    return (await control('GET', realm)).stats;
  }

  static async deleteRealm(realm) {
    try {
      await control('DELETE', realm);
    } catch {
      // Already gone
    }
  }

  static discoveryUrl(realm) {
    return `${MOCK_OIDC_PUBLIC_URL}/realms/${realm}/.well-known/openid-configuration`;
  }
}

/** Sign in on the mock provider's login form. */
export async function signInAtMockIdp(page, email) {
  await expect(page.locator('#mock-oidc-login')).toBeVisible();
  await page.fill('#mock-oidc-email', email);
  await page.click('#mock-oidc-submit');
}

/**
 * Counts the login page's requests to start an OIDC sign-in, so a test can prove the page did not
 * launch one on its own.
 */
export function trackOidcLaunches(page) {
  const tracker = { count: 0 };
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().includes('/auth/oidc/login-authorizations/')) {
      tracker.count += 1;
    }
  });
  return tracker;
}
