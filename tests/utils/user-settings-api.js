import { expect } from '@playwright/test';
import { TestConfig } from '../config/test-config.js';

const userEndpoint = (path) => `${TestConfig.API_BASE_URL}/api/v1/users${path}`;

// Profile updates are PATCH /api/v1/users/me (the old POST /api/v1/users/update is gone).
const currentUserEndpoint = `${TestConfig.API_BASE_URL}/api/v1/users/me`;

async function getCsrfHeaders(page) {
  const cookies = await page.context().cookies(TestConfig.API_BASE_URL);
  const csrfToken = cookies.find((cookie) => cookie.name === 'csrf-token')?.value;
  return csrfToken ? { 'X-CSRF-Token': csrfToken } : {};
}

function unwrapApiResponse(payload) {
  return payload?.data ?? payload;
}

export class UserSettingsApi {
  static async getCurrentUser(page) {
    const response = await page.request.get(userEndpoint('/me'));
    expect(response.ok()).toBeTruthy();
    return unwrapApiResponse(await response.json());
  }

  static async updateCurrentUserProfile(page, overrides = {}) {
    const currentUser = await this.getCurrentUser(page);
    // Profile fields are top-level; anything else is a UI preference (distanceUnit, dateFormat, ...).
    const {fullName, avatar, timezone, ...uiPreferences} = overrides;
    const profileOverrides = Object.fromEntries(
      Object.entries({fullName, avatar, timezone}).filter(([, value]) => value !== undefined)
    );
    const response = await page.request.patch(currentUserEndpoint, {
      headers: await getCsrfHeaders(page),
      data: {
        fullName: currentUser.fullName || '',
        avatar: currentUser.avatar ?? null,
        timezone: currentUser.timezone || 'UTC',
        ...profileOverrides,
        // Null fields are left unchanged server-side, so only the overridden preferences need sending.
        uiPreferences
      }
    });

    expect(response.ok()).toBeTruthy();
    return unwrapApiResponse(await response.json());
  }
}
