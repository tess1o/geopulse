import {test, expect} from '@playwright/test';
import {LoginPage} from '../pages/LoginPage.js';
import {RegisterPage} from '../pages/RegisterPage.js';
import {DashboardPage} from '../pages/DashboardPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {DatabaseManager} from '../setup/database-manager.js';

async function createUser(page, existingUser) {
    const response = await page.request.post('http://localhost:8081/api/users/register', {
        data: {
            email: existingUser.email,
            fullName: existingUser.fullName,
            password: existingUser.password
        }
    });

    expect(response.ok()).toBeTruthy();
}

test.describe('Authentication Flow', () => {
    let dbManager;

    test.beforeAll(async () => {
        // Initialize database manager
        dbManager = new DatabaseManager();
        await dbManager.connect();
    });

    test.afterAll(async () => {
        if (dbManager) {
            await dbManager.disconnect();
        }
    });

    test.beforeEach(async ({page, context}) => {
        // Clear all cookies and storage before each test
        await context.clearCookies();
        await context.clearPermissions();
        // await page.evaluate(() => {
        //     localStorage.clear();
        //     sessionStorage.clear();
        // });
    });

    test.describe('User Registration', () => {

        test('should successfully register a new user', async ({page}) => {
            const registerPage = new RegisterPage(page);
            const dashboardPage = new DashboardPage(page);
            const newUser = TestData.generateNewUser();

            // Navigate to register page
            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Verify we're on the register page
            expect(await registerPage.isOnRegisterPage()).toBe(true);
            expect(await registerPage.getPageTitle()).toBe('Create Account');

            // Fill and submit registration form
            await registerPage.register(
                newUser.email,
                newUser.fullName,
                newUser.password
            );

            // Wait for successful registration and redirect
            await TestHelpers.waitForNavigation(page, '**/app/location-sources', 15000);

            // Verify we're redirected to location sources page (onboarding)
            expect(await dashboardPage.isOnLocationSourcesPage()).toBe(true);

            // Verify user is authenticated
            expect(await TestHelpers.isAuthenticated(page)).toBe(true);

            // Verify user was created in database
            const createdUser = await dbManager.getUserByEmail(newUser.email);
            expect(createdUser).toBeTruthy();
            expect(createdUser.full_name).toBe(newUser.fullName);

            // Cleanup: Delete the test user
            await dbManager.deleteUser(newUser.email);
        });

        test('should show validation errors for invalid input', async ({page}) => {
            const registerPage = new RegisterPage(page);

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Test empty form submission
            expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

            // Test invalid email
            await registerPage.fillEmail(TestData.invalid.email.invalid);
            await registerPage.fillFullName('Valid Name');
            await registerPage.fillPassword('ValidPassword123!');
            await registerPage.fillConfirmPassword('ValidPassword123!');

            await registerPage.waitForValidationErrors();

            const email = page.locator(registerPage.selectors.emailInput);
            const emailErrorMessage = await email.evaluate(el => el.validationMessage);
            expect(emailErrorMessage).toContain("Please include an '@' in the email address");

            // Test password mismatch
            await registerPage.fillEmail('valid@example.com');
            await registerPage.fillPassword('Password123!');
            await registerPage.fillConfirmPassword('DifferentPassword123!');
            await registerPage.clickRegister();

            await registerPage.waitForValidationErrors();
            expect(await registerPage.hasFieldError('#confirmPassword')).toBe(true);
        });

        test('should prevent registration with existing email', async ({page}) => {
            const registerPage = new RegisterPage(page);
            const existingUser = TestData.users.existing;
            await createUser(page, existingUser);

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Try to register with existing email
            await registerPage.register(
                existingUser.email,
                'New Full Name',
                'NewPassword123!'
            );

            // Should show error message
            await registerPage.waitForErrorMessage();
            const errorMessage = await registerPage.getErrorMessage();
            expect(errorMessage).toContain('User with email ' + existingUser.email + ' already exists');

            dbManager.deleteUser(existingUser.email);
        })

        test('should navigate to login page from register page', async ({page}) => {
            const registerPage = new RegisterPage(page);
            const loginPage = new LoginPage(page);

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Click login link
            await registerPage.clickLoginLink();

            // Should be redirected to login page
            await TestHelpers.waitForNavigation(page, '**/login');
            expect(await loginPage.isOnLoginPage()).toBe(true);
        });
    });

    test.describe('Authentication State Management', () => {

        test('should maintain session across page reloads', async ({page}) => {
            const loginPage = new LoginPage(page);
            const dashboardPage = new DashboardPage(page);
            const testUser = TestData.users.existing;

            await createUser(page, testUser);

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
            expect(await dashboardPage.isOnTimelinePage()).toBe(true);
            expect(await TestHelpers.isAuthenticated(page)).toBe(true);
        });

        test('should redirect unauthenticated users to login', async ({page}) => {
            // Try to access protected page without authentication
            await page.goto('/app/timeline');

            // Should be redirected to login page
            await TestHelpers.waitForNavigation(page, '**/login', 10000);

            const loginPage = new LoginPage(page);
            expect(await loginPage.isOnLoginPage()).toBe(true);
        });

        test('should handle logout correctly', async ({page}) => {
            const loginPage = new LoginPage(page);
            const dashboardPage = new DashboardPage(page);
            const testUser = TestData.users.existing;

            createUser(page, testUser);

            // Login first
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Verify authenticated
            expect(await TestHelpers.isAuthenticated(page)).toBe(true);

            // Logout
            await dashboardPage.logout();

            // Should be redirected to login page
            expect(await TestHelpers.isHomePage(page)).toBe(true);

            // Should not be authenticated
            expect(await TestHelpers.isAuthenticated(page)).toBe(false);
        });
    });
});