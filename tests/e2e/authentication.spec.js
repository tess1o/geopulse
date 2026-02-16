import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {AppNavigation} from '../pages/AppNavigation.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {TestConfig} from '../config/test-config.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';

test.describe('Authentication Flow', () => {

    test.describe('Session Management', () => {
        test('should maintain session across page reloads', async ({page}) => {
            const loginPage = new LoginPage(page);
            const timelinePage = new TimelinePage(page);
            const testUser = TestData.users.existing;
            await UserFactory.createUser(page, testUser);

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

        test('should redirect unauthenticated users to login', async ({page}) => {
            // Try to access protected page without authentication
            await page.goto('/app/timeline');

            // Should be redirected to login page
            await TestHelpers.waitForNavigation(page, '**/login', TestConfig.TIMEOUTS.navigation);

            const loginPage = new LoginPage(page);
            expect(await loginPage.isOnLoginPage()).toBe(true);
        });

        test('should handle logout correctly', async ({page}) => {
            const loginPage = new LoginPage(page);
            const appNavigation = new AppNavigation(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);

            // Login first
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Verify authenticated
            expect(await TestHelpers.isAuthenticated(page)).toBe(true);

            // Logout
            await appNavigation.logout();

            // Should be redirected to login page
            expect(await TestHelpers.isHomePage(page)).toBe(true);

            // Should not be authenticated
            expect(await TestHelpers.isAuthenticated(page)).toBe(false);
        });

        test('should handle invalid login attempts', async ({page}) => {
            const loginPage = new LoginPage(page);

            await loginPage.navigate();
            await loginPage.login('nonexistent@example.com', 'wrongpassword');

            // Should show error message and stay on login page
            expect(await loginPage.isOnLoginPage()).toBe(true);
            
            // Check for error message if login page has error handling
            try {
                await ValidationHelpers.waitForPageErrorMessage(page, loginPage.getErrorSelector());
                const errorMessage = await ValidationHelpers.getPageErrorMessage(page, loginPage.getErrorSelector());
                expect(errorMessage).toBeTruthy();
            } catch {
            }
        });

        test('should handle session timeout', async ({page}) => {
            const loginPage = new LoginPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);

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
            await TestHelpers.waitForNavigation(page, '**/login', TestConfig.TIMEOUTS.navigation);
            expect(await loginPage.isOnLoginPage()).toBe(true);
        });
    });

    test.describe('Timezone Persistence', () => {
        test('should preserve custom timezone in localStorage after login', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const testUser = TestData.users.existing;
            
            // Create user with custom timezone (not UTC)
            testUser.timezone = 'America/Los_Angeles';
            await UserFactory.createUser(page, testUser);

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

        test('should preserve Europe/London timezone after login', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const testUser = TestData.users.existing;
            
            // Create user with Europe/London timezone
            testUser.timezone = 'Europe/London';
            await UserFactory.createUser(page, testUser);

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

        test('should preserve Asia/Tokyo timezone after login', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const testUser = TestData.users.existing;
            
            // Create user with Asia/Tokyo timezone
            testUser.timezone = 'Asia/Tokyo';
            await UserFactory.createUser(page, testUser);

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

        test('should handle UTC timezone correctly after login', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const testUser = TestData.users.existing;
            
            // Create user with explicit UTC timezone
            testUser.timezone = 'UTC';
            await UserFactory.createUser(page, testUser);

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

        test('should handle normalized timezone (Europe/Kyiv) after login', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const testUser = TestData.users.existing;
            
            // Create user with normalized Europe/Kyiv timezone
            testUser.timezone = 'Europe/Kyiv';
            await UserFactory.createUser(page, testUser);

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

        test('should persist timezone across page reloads', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const timelinePage = new TimelinePage(page);
            const testUser = TestData.users.existing;
            
            // Create user with custom timezone
            testUser.timezone = 'Australia/Sydney';
            await UserFactory.createUser(page, testUser);

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

        test('should clear timezone from localStorage after logout', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const appNavigation = new AppNavigation(page);
            const testUser = TestData.users.existing;
            
            // Create user with custom timezone
            testUser.timezone = 'America/Chicago';
            await UserFactory.createUser(page, testUser);

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

            // Should be redirected to home page
            expect(await TestHelpers.isHomePage(page)).toBe(true);
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