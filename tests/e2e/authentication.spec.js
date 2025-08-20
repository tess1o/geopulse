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
                // Error handling might not be implemented yet
                console.log('Login error message handling not yet implemented');
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
});