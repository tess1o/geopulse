import { test as base } from '@playwright/test';
import { randomUUID } from 'crypto';
import { DatabaseManager } from '../setup/database-manager.js';
import { TestData } from './test-data.js';
import { UserFactory } from '../utils/user-factory.js';

const sanitizeSegment = (value, fallback = 'test', maxLength = 24) => {
  const sanitized = String(value ?? '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, maxLength);

  return sanitized || fallback;
};

export const test = base.extend({
  mapMode: ['RASTER', { option: true }],

  dbManager: [async ({}, use) => {
    const dbManager = new DatabaseManager();
    await dbManager.connect();

    await use(dbManager);

    await dbManager.disconnect();
  }, { scope: 'worker' }],

  testIdentity: async ({}, use, testInfo) => {
    const runId = sanitizeSegment(
      process.env.E2E_RUN_ID || `${Date.now().toString(36)}-${process.pid.toString(36)}`,
      'run',
      20
    );
    const fileName = testInfo.file.split('/').at(-1)?.replace('.spec.js', '') || 'spec';
    const fileKey = sanitizeSegment(fileName, 'spec', 20);
    const titleKey = sanitizeSegment(testInfo.title, 'case', 32);

    await use({
      runId,
      fileKey,
      titleKey,
      workerIndex: testInfo.workerIndex,
      retry: testInfo.retry,
      repeatEachIndex: testInfo.repeatEachIndex,
      uniqueToken: `${runId}-w${testInfo.workerIndex}-${randomUUID().slice(0, 8)}`,
    });
  },

  isolatedUsers: async ({ dbManager, testIdentity }, use) => {
    const createdEmails = new Set();
    let sequence = 0;

    const build = (overrides = {}) => {
      sequence += 1;
      const user = TestData.buildExistingUser(overrides);

      if (!overrides.email) {
        const emailSuffix = `${testIdentity.fileKey}-${testIdentity.workerIndex}-${sequence}-${randomUUID().slice(0, 6)}`;
        user.email = `${testIdentity.runId}-${emailSuffix}@example.com`;
      }

      if (!overrides.fullName) {
        user.fullName = `E2E ${testIdentity.fileKey} ${sequence}`;
      }

      return user;
    };

    const create = async (page, overrides = {}) => {
      const user = build(overrides);
      await UserFactory.createUser(page, user);
      createdEmails.add(user.email);
      return user;
    };

    await use({
      build,
      create,
      createdEmails,
    });

    await dbManager.deleteUsersByEmails([...createdEmails]);
  },

  cleanBrowser: [async ({ context }, use) => {
    await context.clearCookies();
    await context.clearPermissions();
    await use();
  }, { auto: true }],

  mapE2EDebugBootstrap: [async ({ page, mapMode }, use) => {
    await page.addInitScript(({ mode }) => {
      window.__GP_E2E_MAP_DEBUG_ENABLED__ = true;
      window.__GP_E2E_MAP_DEBUG__ = {
        enabled: true,
        mode
      };
      window.__GP_E2E_MAPS = window.__GP_E2E_MAPS || {};
    }, { mode: mapMode || 'RASTER' });

    await use();
  }, { auto: true }],
});

export { expect } from '@playwright/test';
