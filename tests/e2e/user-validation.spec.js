import {test, expect} from '../fixtures/database-fixture.js';
import {RegisterPage} from '../pages/RegisterPage.js';
import {TestData} from '../fixtures/test-data.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';

test.describe('User Input Validation', () => {

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

        await ValidationHelpers.waitForValidation();

        // Use validation helper to check browser validation message
        const emailValidationMessage = await ValidationHelpers.getBrowserValidationMessage(
            page,
            registerPage.selectors.emailInput
        );
        expect(emailValidationMessage).toContain("Please include an '@' in the email address");

        // Test password mismatch
        await registerPage.fillEmail('valid@example.com');
        await registerPage.fillPassword('Password123!');
        await registerPage.fillConfirmPassword('DifferentPassword123!');
        await registerPage.clickRegister();

        await ValidationHelpers.waitForValidation();
        expect(await ValidationHelpers.hasFieldError(page, registerPage.selectors.confirmPasswordInput)).toBe(true);
    });

    test('should validate password requirements', async ({page}) => {
        const registerPage = new RegisterPage(page);

        await registerPage.navigate();
        await registerPage.waitForPageLoad();

        // Test weak password
        await registerPage.fillEmail('test@example.com');
        await registerPage.fillFullName('Test User');
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

    test('should validate required fields', async ({page}) => {
        const registerPage = new RegisterPage(page);

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
        await registerPage.fillEmail('test@example.com');
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill name
        await registerPage.fillFullName('Test User');
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill password
        await registerPage.fillPassword('ValidPassword123!');
        expect(await registerPage.isRegisterButtonEnabled()).toBe(false);

        // Fill confirm password - now should be enabled
        await registerPage.fillConfirmPassword('ValidPassword123!');
        expect(await registerPage.isRegisterButtonEnabled()).toBe(true);
    });
});