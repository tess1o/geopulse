import { test as base } from '@playwright/test';
import { DatabaseManager } from '../setup/database-manager.js';

// Extend base test with database fixture
export const test = base.extend({
  dbManager: [async ({}, use) => {
    // Setup: create and connect database manager
    const dbManager = new DatabaseManager();
    await dbManager.connect();
    
    // Provide the database manager to the test
    await use(dbManager);
    
    // Teardown: disconnect database manager
    await dbManager.disconnect();
  }, { scope: 'worker' }], // Share across all tests in same worker

  // Fresh database state for each test
  cleanDatabase: [async ({ dbManager }, use) => {
    // Reset database before each test
    await dbManager.resetDatabase();
    await use();
    // Database cleanup happens in dbManager fixture teardown
  }, { auto: true }], // Automatically runs for every test

  // Clean browser state for each test
  cleanBrowser: [async ({ context }, use) => {
    // Clear cookies and permissions before each test
    await context.clearCookies();
    await context.clearPermissions();
    await use();
  }, { auto: true }], // Automatically runs for every test
});

export { expect } from '@playwright/test';