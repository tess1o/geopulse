import {test, expect} from '../fixtures/database-fixture.js';
import {RegisterPage} from '../pages/RegisterPage.js';
import {DashboardPage} from '../pages/DashboardPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {TestConfig} from '../config/test-config.js';

test.describe('User Registration', () => {

    test('should successfully register a new user', async ({page, dbManager}) => {
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
        await TestHelpers.waitForNavigation(page, '**/app/location-sources', TestConfig.TIMEOUTS.navigation);

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

    test('should prevent registration with existing email', async ({page, dbManager}) => {
        const registerPage = new RegisterPage(page);
        const existingUser = TestData.users.existing;
        await UserFactory.createUser(page, existingUser);

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

        await dbManager.deleteUser(existingUser.email);
    });

    test('should navigate to login page from register page', async ({page}) => {
        const registerPage = new RegisterPage(page);
        const loginPage = new (await import('../pages/LoginPage.js')).LoginPage(page);

        await registerPage.navigate();
        await registerPage.waitForPageLoad();

        // Click login link
        await registerPage.clickLoginLink();

        // Should be redirected to login page
        await TestHelpers.waitForNavigation(page, '**/login');
        expect(await loginPage.isOnLoginPage()).toBe(true);
    });
});