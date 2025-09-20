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

    test.describe('Timezone Auto-Detection', () => {
        test('should auto-detect timezone during registration (America/New_York)', async ({page, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = TestData.generateNewUser();

            // Mock browser timezone to America/New_York
            await page.addInitScript(() => {
                const originalDateTimeFormat = Intl.DateTimeFormat;
                Intl.DateTimeFormat = function(...args) {
                    const formatter = new originalDateTimeFormat(...args);
                    const originalResolvedOptions = formatter.resolvedOptions;
                    formatter.resolvedOptions = function() {
                        const options = originalResolvedOptions.call(this);
                        options.timeZone = 'America/New_York';
                        return options;
                    };
                    return formatter;
                };
                Object.setPrototypeOf(Intl.DateTimeFormat, originalDateTimeFormat);
                Object.getOwnPropertyNames(originalDateTimeFormat).forEach(name => {
                    if (typeof originalDateTimeFormat[name] === 'function') {
                        Intl.DateTimeFormat[name] = originalDateTimeFormat[name];
                    }
                });
            });

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Register new user
            await registerPage.register(
                newUser.email,
                newUser.fullName,
                newUser.password
            );

            // Wait for successful registration
            await TestHelpers.waitForNavigation(page, '**/app/location-sources', TestConfig.TIMEOUTS.navigation);
            expect(await locationSourcesPage.isOnLocationSourcesPage()).toBe(true);

            // Check that timezone was auto-detected and sent to backend
            const userInfo = await page.evaluate(() => {
                const userInfoStr = localStorage.getItem('userInfo');
                return userInfoStr ? JSON.parse(userInfoStr) : null;
            });

            // Verify localStorage contains the detected timezone
            expect(userInfo).toBeTruthy();
            expect(userInfo.timezone).toBe('America/New_York');

            // Verify database was updated with the correct timezone
            const createdUser = await dbManager.getUserByEmail(newUser.email);
            expect(createdUser).toBeTruthy();
            expect(createdUser.timezone).toBe('America/New_York');
        });

        test('should auto-detect timezone during registration (Europe/London)', async ({page, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = TestData.generateNewUser();

            // Mock browser timezone to Europe/London
            await page.addInitScript(() => {
                const originalDateTimeFormat = Intl.DateTimeFormat;
                Intl.DateTimeFormat = function(...args) {
                    const formatter = new originalDateTimeFormat(...args);
                    const originalResolvedOptions = formatter.resolvedOptions;
                    formatter.resolvedOptions = function() {
                        const options = originalResolvedOptions.call(this);
                        options.timeZone = 'Europe/London';
                        return options;
                    };
                    return formatter;
                };
                Object.setPrototypeOf(Intl.DateTimeFormat, originalDateTimeFormat);
                Object.getOwnPropertyNames(originalDateTimeFormat).forEach(name => {
                    if (typeof originalDateTimeFormat[name] === 'function') {
                        Intl.DateTimeFormat[name] = originalDateTimeFormat[name];
                    }
                });
            });

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Register new user
            await registerPage.register(
                newUser.email,
                newUser.fullName,
                newUser.password
            );

            // Wait for successful registration
            await TestHelpers.waitForNavigation(page, '**/app/location-sources', TestConfig.TIMEOUTS.navigation);
            expect(await locationSourcesPage.isOnLocationSourcesPage()).toBe(true);

            // Check that timezone was auto-detected and sent to backend
            const userInfo = await page.evaluate(() => {
                const userInfoStr = localStorage.getItem('userInfo');
                return userInfoStr ? JSON.parse(userInfoStr) : null;
            });

            // Verify localStorage contains the detected timezone
            expect(userInfo).toBeTruthy();
            expect(userInfo.timezone).toBe('Europe/London');

            // Verify database was updated with the correct timezone
            const createdUser = await dbManager.getUserByEmail(newUser.email);
            expect(createdUser).toBeTruthy();
            expect(createdUser.timezone).toBe('Europe/London');
        });

        test('should handle timezone normalization during registration (Europe/Kiev -> Europe/Kyiv)', async ({page, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = TestData.generateNewUser();

            // Mock browser timezone to Europe/Kiev (old spelling)
            await page.addInitScript(() => {
                const originalDateTimeFormat = Intl.DateTimeFormat;
                Intl.DateTimeFormat = function(...args) {
                    const formatter = new originalDateTimeFormat(...args);
                    const originalResolvedOptions = formatter.resolvedOptions;
                    formatter.resolvedOptions = function() {
                        const options = originalResolvedOptions.call(this);
                        options.timeZone = 'Europe/Kiev';
                        return options;
                    };
                    return formatter;
                };
                Object.setPrototypeOf(Intl.DateTimeFormat, originalDateTimeFormat);
                Object.getOwnPropertyNames(originalDateTimeFormat).forEach(name => {
                    if (typeof originalDateTimeFormat[name] === 'function') {
                        Intl.DateTimeFormat[name] = originalDateTimeFormat[name];
                    }
                });
            });

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Register new user
            await registerPage.register(
                newUser.email,
                newUser.fullName,
                newUser.password
            );

            // Wait for successful registration
            await TestHelpers.waitForNavigation(page, '**/app/location-sources', TestConfig.TIMEOUTS.navigation);
            expect(await locationSourcesPage.isOnLocationSourcesPage()).toBe(true);

            // Check that timezone was normalized and sent to backend
            const userInfo = await page.evaluate(() => {
                const userInfoStr = localStorage.getItem('userInfo');
                return userInfoStr ? JSON.parse(userInfoStr) : null;
            });

            // Verify localStorage contains the normalized timezone
            expect(userInfo).toBeTruthy();
            expect(userInfo.timezone).toBe('Europe/Kyiv'); // Should be normalized to Kyiv

            // Verify database was updated with the normalized timezone
            const createdUser = await dbManager.getUserByEmail(newUser.email);
            expect(createdUser).toBeTruthy();
            expect(createdUser.timezone).toBe('Europe/Kyiv'); // Should be normalized
        });

        test('should fallback to UTC when timezone detection fails', async ({page, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = TestData.generateNewUser();

            // Break timezone detection
            await page.addInitScript(() => {
                Object.defineProperty(Intl, 'DateTimeFormat', {
                    value: function() {
                        throw new Error('Timezone detection failed');
                    }
                });
            });

            await registerPage.navigate();
            await registerPage.waitForPageLoad();

            // Register new user
            await registerPage.register(
                newUser.email,
                newUser.fullName,
                newUser.password
            );

            // Wait for successful registration
            await TestHelpers.waitForNavigation(page, '**/app/location-sources', TestConfig.TIMEOUTS.navigation);
            expect(await locationSourcesPage.isOnLocationSourcesPage()).toBe(true);

            // Check that timezone falls back to UTC
            const userInfo = await page.evaluate(() => {
                const userInfoStr = localStorage.getItem('userInfo');
                return userInfoStr ? JSON.parse(userInfoStr) : null;
            });

            // Verify localStorage contains UTC as fallback
            expect(userInfo).toBeTruthy();
            expect(userInfo.timezone).toBe('UTC');

            // Verify database was updated with UTC fallback
            const createdUser = await dbManager.getUserByEmail(newUser.email);
            expect(createdUser).toBeTruthy();
            expect(createdUser.timezone).toBe('UTC');
        });
    });
});