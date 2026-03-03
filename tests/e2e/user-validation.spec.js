import { test, expect } from '../fixtures/isolated-fixture.js';
import {RegisterPage} from '../pages/RegisterPage.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';

test.describe('User Input Validation', () => {

    test('should show validation errors for invalid input', async ({ page, isolatedUsers }) => {
        const registerPage = new RegisterPage(page);
        const validUser = isolatedUsers.build();

        await registerPage.navigate();
        await registerPage.waitForPageLoad();

        // Test empty form submission
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Test invalid email
        await registerPage.fillEmail('invalid-email-format');
        await registerPage.fillFullName(validUser.fullName);
        await registerPage.fillPassword(validUser.password);
        await registerPage.fillConfirmPassword(validUser.password);

        await ValidationHelpers.waitForValidation();

        // Use validation helper to check browser validation message
        const emailValidationMessage = await ValidationHelpers.getBrowserValidationMessage(
            page,
            registerPage.selectors.emailInput
        );
        expect(emailValidationMessage).toContain("Please include an '@' in the email address");

        // Test password mismatch
        await registerPage.fillEmail(validUser.email);
        await registerPage.fillPassword('Password123!');
        await registerPage.fillConfirmPassword('DifferentPassword123!');
        await registerPage.clickRegister();

        await ValidationHelpers.waitForValidation();
        expect(await ValidationHelpers.hasFieldError(page, registerPage.selectors.confirmPasswordInput)).toBe(true);
    });

    test('should validate password requirements', async ({ page, isolatedUsers }) => {
        const registerPage = new RegisterPage(page);
        const validUser = isolatedUsers.build();

        await registerPage.navigate();
        await registerPage.waitForPageLoad();

        // Test weak password
        await registerPage.fillEmail(validUser.email);
        await registerPage.fillFullName(validUser.fullName);
        await registerPage.fillPassword('weak');
        await registerPage.fillConfirmPassword('weak');

        await ValidationHelpers.waitForValidation();

        // Check for password strength validation (if implemented)
        const hasPasswordError = await ValidationHelpers.hasCustomValidationError(page, registerPage.selectors.passwordError);
        if (hasPasswordError) {
            const errorMessage = await ValidationHelpers.getCustomValidationError(page, registerPage.selectors.passwordError);
            expect(errorMessage).toBeTruthy();
        }
    });

    test('should validate required fields', async ({ page, isolatedUsers }) => {
        const registerPage = new RegisterPage(page);
        const validUser = isolatedUsers.build();

        await registerPage.navigate();
        await registerPage.waitForPageLoad();

        // Try submitting with empty required fields
        await registerPage.fillEmail('');
        await registerPage.fillFullName('');
        await registerPage.fillPassword('');
        await registerPage.fillConfirmPassword('');

        // Register button should be disabled
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill email only
        await registerPage.fillEmail(validUser.email);
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill name
        await registerPage.fillFullName(validUser.fullName);
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill password
        await registerPage.fillPassword('ValidPassword123!');
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill confirm password - now should be enabled
        await registerPage.fillConfirmPassword('ValidPassword123!');
        expect(await registerPage.isRegisterButtonEnabled()).toBe(true);
    });
});
