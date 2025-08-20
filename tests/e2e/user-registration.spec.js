import {test, expect} from '../fixtures/database-fixture.js';
import {RegisterPage} from '../pages/RegisterPage.js';
import {LocationSourcesPage} from '../pages/LocationSourcesPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {TestConfig} from '../config/test-config.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';
import {LoginPage} from "../pages/LoginPage.js";

test.describe('User Registration', () => {

    test('should successfully register a new user', async ({page, dbManager}) => {
        const registerPage = new RegisterPage(page);
        const locationSourcesPage = new LocationSourcesPage(page);
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
        expect(await locationSourcesPage.isOnLocationSourcesPage()).toBe(true);

        // Verify user is authenticated
        expect(await TestHelpers.isAuthenticated(page)).toBe(true);

        // Verify user was created in database
        const createdUser = await dbManager.getUserByEmail(newUser.email);
        expect(createdUser).toBeTruthy();
        expect(createdUser.full_name).toBe(newUser.fullName);
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
        await ValidationHelpers.waitForPageErrorMessage(page, registerPage.getErrorSelector());
        const errorMessage = await ValidationHelpers.getPageErrorMessage(page, registerPage.getErrorSelector());
        expect(errorMessage).toContain('User with email ' + existingUser.email + ' already exists');
    });

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