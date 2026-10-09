import { randomUUID } from 'crypto';
import { test, expect } from '../fixtures/isolated-fixture.js';
import {RegisterPage} from '../pages/RegisterPage.js';
import {LocationSourcesPage} from '../pages/LocationSourcesPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestConfig} from '../config/test-config.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';
import {LoginPage} from "../pages/LoginPage.js";
import {buildManagedUser as buildRegistrableUser} from '../utils/isolated-user-helper.js';

test.describe('User Registration', () => {
    test('should successfully register a new user', async ({ page, isolatedUsers, dbManager}) => {
        const registerPage = new RegisterPage(page);
        const locationSourcesPage = new LocationSourcesPage(page);
        const newUser = buildRegistrableUser(isolatedUsers);

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

    test('should prevent registration with existing email', async ({ page, isolatedUsers }) => {
        const registerPage = new RegisterPage(page);
        const existingUser = await isolatedUsers.create(page);

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
        expect(errorMessage).toContain('An account with this email already exists');
        expect(errorMessage).not.toContain('Request failed');
    });

    test('should explain a validation failure only the backend catches', async ({ page, isolatedUsers, dbManager }) => {
        const registerPage = new RegisterPage(page);
        const newUser = buildRegistrableUser(isolatedUsers);

        await registerPage.navigate();
        await registerPage.waitForPageLoad();

        // The form only checks a minimum length; the backend caps a full name at 100 characters.
        await registerPage.register(newUser.email, 'N'.repeat(101), newUser.password);

        const error = page.locator(registerPage.getErrorSelector());
        await expect(error).toContainText('Full name must be between 1 and 100 characters');
        await expect(error).not.toContainText('check your information');
        expect(await registerPage.isOnRegisterPage()).toBe(true);
        expect(await dbManager.getUserByEmail(newUser.email)).toBeFalsy();
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
        test('should auto-detect timezone during registration (America/New_York)', async ({ page, isolatedUsers, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = buildRegistrableUser(isolatedUsers);

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

        test('should auto-detect timezone during registration (Europe/London)', async ({ page, isolatedUsers, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = buildRegistrableUser(isolatedUsers);

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

        test('should handle timezone normalization during registration (Europe/Kiev -> Europe/Kyiv)', async ({ page, isolatedUsers, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = buildRegistrableUser(isolatedUsers);

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

        test('should fallback to UTC when timezone detection fails', async ({ page, isolatedUsers, dbManager}) => {
            const registerPage = new RegisterPage(page);
            const locationSourcesPage = new LocationSourcesPage(page);
            const newUser = buildRegistrableUser(isolatedUsers);

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

    // Registration through an admin invitation link. Each test seeds its own invitation, so nothing here
    // touches the global registration settings other specs depend on.
    test.describe('Invitation Registration', () => {
        const selectors = {
            email: '#email',
            fullName: '#fullName',
            password: '#password input',
            confirmPassword: '#confirmPassword input',
            submit: 'button[type="submit"]',
            error: '.register-form .p-message'
        };

        const seedInvitation = async (page, isolatedUsers, dbManager, { used = false } = {}) => {
            const inviter = await isolatedUsers.create(page);
            const inviterDbUser = await dbManager.getUserByEmail(inviter.email);
            const token = `e2e-invite-${randomUUID().replace(/-/g, '')}`;
            await dbManager.client.query(
                `INSERT INTO user_invitations (id, token, created_by, created_at, expires_at, used, used_at, revoked)
                 VALUES ($1, $2, $3, NOW(), NOW() + interval '7 days', $4, CASE WHEN $4 THEN NOW() END, false)`,
                [randomUUID(), token, inviterDbUser.id, used]
            );
            return { token, inviter };
        };

        const openInvitation = async (page, token) => {
            await page.goto(`/register/invite/${token}`);
            await page.waitForLoadState('networkidle');
        };

        const submitInvitation = async (page, email, password) => {
            await page.fill(selectors.email, email);
            await page.fill(selectors.fullName, 'Invited User');
            await page.fill(selectors.password, password);
            await page.fill(selectors.confirmPassword, password);
            await page.click(selectors.submit);
        };

        test('should report an email that is already registered', async ({ page, isolatedUsers, dbManager }) => {
            const { token, inviter } = await seedInvitation(page, isolatedUsers, dbManager);

            await openInvitation(page, token);
            const responsePromise = page.waitForResponse(
                response => response.url().includes(`/registration-invitations/${token}/registrations`)
            );
            await submitInvitation(page, inviter.email, 'Password123!');

            const response = await responsePromise;
            expect(response.status()).toBe(409);
            expect((await response.json()).code).toBe('USER_REGISTRATION_CONFLICT');

            const error = page.locator(selectors.error);
            await expect(error).toContainText('An account with this email already exists');
            await expect(error).not.toContainText('Bad Request');
        });

        test('should explain an invitation revoked while the form was open', async ({ page, isolatedUsers, dbManager }) => {
            const { token } = await seedInvitation(page, isolatedUsers, dbManager);
            const newUser = buildRegistrableUser(isolatedUsers);

            await openInvitation(page, token);
            await expect(page.locator(selectors.email)).toBeVisible();

            await dbManager.client.query(
                'UPDATE user_invitations SET revoked = true, revoked_at = NOW() WHERE token = $1',
                [token]
            );
            await submitInvitation(page, newUser.email, newUser.password);

            await expect(page.locator(selectors.error)).toContainText('This invitation has been revoked');
            expect(await dbManager.getUserByEmail(newUser.email)).toBeFalsy();
        });

        test('should explain an invitation that was already used', async ({ page, isolatedUsers, dbManager }) => {
            const { token } = await seedInvitation(page, isolatedUsers, dbManager, { used: true });

            await openInvitation(page, token);

            await expect(page.getByText('This invitation has already been used')).toBeVisible();
            await expect(page.locator(selectors.email)).toHaveCount(0);
        });

        test('should explain an invitation link that does not exist', async ({ page }) => {
            await openInvitation(page, `e2e-missing-${randomUUID().replace(/-/g, '')}`);

            await expect(page.getByText('This invitation link is not valid. Please ask your administrator for a new one.'))
                .toBeVisible();
            await expect(page.getByText('Failed to validate invitation')).toHaveCount(0);
        });
    });
});
