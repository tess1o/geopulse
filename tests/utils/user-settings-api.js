import { expect } from '@playwright/test';
import { TestConfig } from '../config/test-config.js';

const userEndpoint = (path) => `${TestConfig.API_BASE_URL}/api/users${path}`;

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
    const response = await page.request.post(userEndpoint('/update'), {
      headers: await getCsrfHeaders(page),
      data: {
        fullName: currentUser.fullName || '',
        avatar: currentUser.avatar ?? null,
        timezone: currentUser.timezone || 'UTC',
        measureUnit: currentUser.measureUnit || 'METRIC',
        defaultRedirectUrl: currentUser.defaultRedirectUrl || '',
        dateFormat: currentUser.dateFormat || 'MDY',
        timeFormat: currentUser.timeFormat || '24h',
        ...overrides
      }
    });

    expect(response.ok()).toBeTruthy();
    return unwrapApiResponse(await response.json());
  }
}
