import { randomUUID } from 'crypto';
import { test as base, expect } from './isolated-fixture.js';
import { TestConfig } from '../config/test-config.js';
import { MockOidc } from '../utils/mock-oidc.js';

const PROVIDER_PREFIX = 'e2e-oidc-';
const CLIENT_ID = 'geopulse-e2e';

/**
 * Global auth settings every OIDC test starts from: password login off, auto-launch on, and a callback
 * base URL the browser can reach. Settings are read from the database on every request, so writing the
 * rows is enough; the original rows are restored afterwards.
 */
const baseSettings = (baseURL) => ({
  'auth.login.enabled': ['true', 'BOOLEAN'],
  'auth.password-login.enabled': ['false', 'BOOLEAN'],
  'auth.oidc.login.enabled': ['true', 'BOOLEAN'],
  'auth.oidc.auto-launch.enabled': ['true', 'BOOLEAN'],
  // Signs the test's password user in through the provider without the linking dialog.
  'auth.oidc.auto-link-accounts': ['true', 'BOOLEAN'],
  'auth.oidc.rp-logout.enabled': ['true', 'BOOLEAN'],
  'auth.admin-login-bypass.enabled': ['true', 'BOOLEAN'],
  'auth.guest-root-redirect-to-login.enabled': ['false', 'BOOLEAN'],
  'auth.oidc.callback-base-url': [baseURL.replace(/\/+$/, ''), 'STRING'],
});

class OidcEnvironment {
  constructor({ page, playwright, baseURL, isolatedUsers, dbManager }) {
    this.page = page;
    this.playwright = playwright;
    this.baseURL = baseURL;
    this.isolatedUsers = isolatedUsers;
    this.db = dbManager.client;
    this.providers = [];
    this.settingsSnapshot = null;
    this.adminApi = null;
  }

  /** The provider the test signs in with (the first one created). */
  get provider() {
    return this.providers[0];
  }

  async setUp({ endSession = true } = {}) {
    // Users are registered while password registration still works; the admin manages providers.
    this.admin = await this.isolatedUsers.create(this.page);
    await this.db.query("UPDATE users SET role = 'ADMIN' WHERE email = $1", [this.admin.email]);
    this.adminApi = await this.playwright.request.newContext();
    const login = await this.adminApi.post(TestConfig.API_ENDPOINTS.login, {
      data: { email: this.admin.email, password: this.admin.password },
    });
    expect(login.ok(), 'admin API sign-in').toBe(true);

    // A run that crashed may have left providers behind; auto-launch needs exactly one.
    await this.db.query('DELETE FROM oidc_providers WHERE name LIKE $1', [`${PROVIDER_PREFIX}%`]);

    const settings = baseSettings(this.baseURL);
    const snapshot = await this.db.query(
      'SELECT key, value, value_type, category, description FROM system_settings WHERE key = ANY($1::text[])',
      [Object.keys(settings)]
    );
    this.settingsSnapshot = { keys: Object.keys(settings), rows: snapshot.rows };
    for (const [key, [value, type]] of Object.entries(settings)) {
      await this.setSetting(key, value, type);
    }

    await this.addProvider({ endSession });
  }

  async setSetting(key, value, type = 'BOOLEAN') {
    await this.db.query(
      `INSERT INTO system_settings (key, value, value_type, category, description, updated_at)
       VALUES ($1, $2, $3, 'auth', 'e2e override', NOW())
       ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
      [key, value, type]
    );
  }

  /**
   * Register a mock provider realm and the matching GeoPulse provider.
   * @param {Object} options - endSession: whether the provider supports RP-initiated logout
   */
  async addProvider({ endSession = true, displayName = 'Mock IdP' } = {}) {
    const name = `${PROVIDER_PREFIX}${randomUUID().slice(0, 8)}`;
    const clientSecret = `secret-${randomUUID()}`;
    await MockOidc.createRealm(name, { clientId: CLIENT_ID, clientSecret, endSession });
    const provider = { name, displayName: `${displayName} ${this.providers.length + 1}` };
    this.providers.push(provider);

    const response = await this.adminApi.post(`${TestConfig.API_BASE_URL}/api/v1/admin/oidc-providers`, {
      data: {
        name,
        displayName: provider.displayName,
        enabled: true,
        clientId: CLIENT_ID,
        clientSecret,
        discoveryUrl: MockOidc.discoveryUrl(name),
        scopes: 'openid profile email',
      },
    });
    expect(response.status(), `create OIDC provider ${name}: ${await response.text()}`).toBe(201);
    return provider;
  }

  configureProvider(patch) {
    return MockOidc.updateRealm(this.provider.name, patch);
  }

  stats() {
    return MockOidc.stats(this.provider.name);
  }

  /** A password user the provider signs in (linked by email). */
  createUser() {
    return this.isolatedUsers.create(this.page);
  }

  async tearDown() {
    for (const provider of this.providers) {
      await MockOidc.deleteRealm(provider.name);
    }
    if (this.providers.length > 0) {
      await this.db.query('DELETE FROM user_oidc_connections WHERE provider_name = ANY($1::text[])',
        [this.providers.map((provider) => provider.name)]);
      await this.db.query('DELETE FROM oidc_providers WHERE name = ANY($1::text[])',
        [this.providers.map((provider) => provider.name)]);
    }
    if (this.settingsSnapshot) {
      await this.db.query('DELETE FROM system_settings WHERE key = ANY($1::text[])', [this.settingsSnapshot.keys]);
      for (const row of this.settingsSnapshot.rows) {
        await this.db.query(
          `INSERT INTO system_settings (key, value, value_type, category, description, updated_at)
           VALUES ($1, $2, $3, $4, $5, NOW())`,
          [row.key, row.value, row.value_type, row.category, row.description]
        );
      }
    }
    await this.adminApi?.dispose();
  }
}

/**
 * Extends the isolated fixture with `oidc`: global auth settings for OIDC sign-in, an admin API session,
 * and one mock provider, all undone after the test. Only for the chromium-oidc project, which runs these
 * tests one at a time after every other spec.
 */
export const test = base.extend({
  oidcOptions: [{ endSession: true }, { option: true }],

  oidc: async ({ page, playwright, baseURL, isolatedUsers, dbManager, oidcOptions }, use) => {
    const environment = new OidcEnvironment({ page, playwright, baseURL, isolatedUsers, dbManager });
    try {
      await environment.setUp(oidcOptions);
      await use(environment);
    } finally {
      await environment.tearDown();
    }
  },
});

export { expect };
