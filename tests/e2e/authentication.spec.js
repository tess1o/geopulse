import { test, expect } from '../fixtures/isolated-fixture.js';
import { LoginPage } from '../pages/LoginPage.js';
import { TimelinePage } from '../pages/TimelinePage.js';
import { AppNavigation } from '../pages/AppNavigation.js';
import { TestHelpers } from '../utils/test-helpers.js';
import { TestConfig } from '../config/test-config.js';

test.describe('Authentication Flow', () => {
  test.describe('Session Management', () => {
    test('should maintain session across page reloads', async ({ page, isolatedUsers, dbManager }) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = await isolatedUsers.create(page);
      const createdUser = await dbManager.getUserByEmail(testUser.email);

      expect(createdUser.notification_preferences.whatsNewEnabled).toBe(false);

      // Login first
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authenticated
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Reload the page
      await page.reload();
      await page.waitForLoadState('networkidle');

      // Should still be authenticated and on timeline page
      expect(await timelinePage.isOnTimelinePage()).toBe(true);
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);
    });

    test('should redirect unauthenticated users to login', async ({ page }) => {
      // Try to access protected page without authentication
      await page.goto('/app/timeline');

      // Should be redirected to login page
      await TestHelpers.waitForNavigation(page, url => url.pathname === '/login', TestConfig.TIMEOUTS.navigation);
      expect(new URL(page.url()).searchParams.get('redirect')).toBe('/app/timeline');

      const loginPage = new LoginPage(page);
      expect(await loginPage.isOnLoginPage()).toBe(true);
    });

    test('should handle logout correctly', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const appNavigation = new AppNavigation(page);
      const testUser = await isolatedUsers.create(page);

      // Login first
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authenticated
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Logout
      await appNavigation.logout();

      // Should be redirected away from authenticated app routes
      await page.waitForURL(url => !url.pathname.startsWith('/app'));
      expect(page.url()).toMatch(/\/($|login)/);

      // Should not be authenticated
      expect(await TestHelpers.isAuthenticated(page)).toBe(false);
    });

    // A rejected credential must read as one. The frontend once let this 401 fall through to the
    // generic "session expired" copy, which the old version of this test never asserted on.
    const INVALID_CREDENTIALS_MESSAGE = 'Invalid email or password. Please check your credentials and try again.';

    const expectInvalidCredentialsRejection = async (page, loginPage, email, password) => {
      const responsePromise = page.waitForResponse(
        response => new URL(response.url()).pathname.endsWith('/auth/sessions') && response.request().method() === 'POST'
      );
      await loginPage.login(email, password);

      const response = await responsePromise;
      expect(response.status()).toBe(401);
      expect((await response.json()).code).toBe('INVALID_CREDENTIALS');

      const error = page.locator(loginPage.getErrorSelector());
      await expect(error).toBeVisible();
      await expect(error).toContainText(INVALID_CREDENTIALS_MESSAGE);
      await expect(error).not.toContainText('Your session has expired');

      expect(await loginPage.isOnLoginPage()).toBe(true);
      expect(await TestHelpers.isAuthenticated(page)).toBe(false);
    };

    test('should show invalid credentials message for a wrong password', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page);

      await loginPage.navigate();
      await expectInvalidCredentialsRejection(page, loginPage, testUser.email, `${testUser.password}-wrong`);
    });

    test('should show invalid credentials message for an unknown email', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const unknownUser = isolatedUsers.build();

      await loginPage.navigate();
      await expectInvalidCredentialsRejection(page, loginPage, unknownUser.email, unknownUser.password);
    });

    test('should handle session timeout', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page);

      // Login first
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authenticated
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Clear cookies to simulate session timeout
      await page.context().clearCookies();

      // Try to navigate to a protected page
      await page.goto('/app/timeline');

      // Should be redirected to login
      await TestHelpers.waitForNavigation(page, url => url.pathname === '/login', TestConfig.TIMEOUTS.navigation);
      expect(new URL(page.url()).searchParams.get('redirect')).toBe('/app/timeline');
      expect(await loginPage.isOnLoginPage()).toBe(true);
    });
  });

  test.describe('Timezone Persistence', () => {
    test('should preserve custom timezone in localStorage after login', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'America/Los_Angeles' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authentication
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Check localStorage contains the user's timezone (not UTC or browser timezone)
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('America/Los_Angeles');
      expect(userInfo.timezone).not.toBe('UTC');
      expect(userInfo.timezone).not.toBe('Europe/Kyiv');
    });

    test('should preserve Europe/London timezone after login', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'Europe/London' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authentication
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Check localStorage contains Europe/London timezone
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Europe/London');
    });

    test('should preserve Asia/Tokyo timezone after login', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'Asia/Tokyo' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authentication
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Check localStorage contains Asia/Tokyo timezone
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Asia/Tokyo');
    });

    test('should handle UTC timezone correctly after login', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authentication
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Check localStorage contains UTC timezone
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('UTC');
    });

    test('should handle normalized timezone (Europe/Kyiv) after login', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'Europe/Kyiv' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify authentication
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Check localStorage contains normalized timezone
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Europe/Kyiv');
    });

    test('should persist timezone across page reloads', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'Australia/Sydney' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify initial timezone in localStorage
      let userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Australia/Sydney');

      // Reload the page
      await page.reload();
      await page.waitForLoadState('networkidle');

      // Should still be authenticated and on timeline page
      expect(await timelinePage.isOnTimelinePage()).toBe(true);
      expect(await TestHelpers.isAuthenticated(page)).toBe(true);

      // Check that timezone persists in localStorage after reload
      userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Australia/Sydney');
    });

    test('should clear timezone from localStorage after logout', async ({ page, isolatedUsers }) => {
      const loginPage = new LoginPage(page);
      const appNavigation = new AppNavigation(page);
      const testUser = await isolatedUsers.create(page, { timezone: 'America/Chicago' });

      // Login
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Verify timezone is set in localStorage
      let userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('America/Chicago');

      // Logout
      await appNavigation.logout();

      // Should be redirected away from authenticated app routes
      await page.waitForURL(url => !url.pathname.startsWith('/app'));
      expect(page.url()).toMatch(/\/($|login)/);
      expect(await TestHelpers.isAuthenticated(page)).toBe(false);

      // Check that userInfo is cleared from localStorage
      userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });

      // userInfo should be null or empty after logout
      expect(userInfo).toBeFalsy();
    });
  });
});
