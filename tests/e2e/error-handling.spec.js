import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage.js';
import { TestHelpers } from '../utils/test-helpers.js';
import { ValidationHelpers } from '../utils/validation-helpers.js';

test.describe('Error Handling', () => {
  test('should render title and message from query parameters without a details section', async ({ page }) => {
    const error = {
      type: 'server',
      title: 'Test Server Error',
      message: 'This is a test error message.'
    };

    const url = `/error?type=${error.type}&title=${encodeURIComponent(error.title)}&message=${encodeURIComponent(
      error.message
    )}`;

    await page.goto(url);

    await expect(page.locator('h1:has-text("Test Server Error")')).toBeVisible();
    await expect(page.locator('p:has-text("This is a test error message.")')).toBeVisible();

    // Error details are read from sessionStorage only. They are deliberately never carried in the URL:
    // apiService stores them in sessionStorage so a long payload cannot exceed the URL length limit (414),
    // so a `details` query parameter renders nothing.
    await expect(page.locator('summary:has-text("Technical Details")')).toHaveCount(0);
  });

  test('should display error page correctly with long details passed via sessionStorage', async ({ page }) => {
    // 1. Go to the base URL to establish an origin and a document context
    await page.goto('/');

    // A large payload, kept to prove sessionStorage carries it without a 414. The page no longer renders it,
    // so assertions below target the fields ErrorPage does display.
    const longHtml =
      '<!DOCTYPE html><html><head><title>502: Bad gateway</title></head><body><h1>502: Bad gateway</h1><p>There is an issue with the upstream server.</p></body></html>'.repeat(300);
    const errorDetails = {
      timestamp: '2026-01-01T00:00:00.000Z',
      method: 'GET',
      url: '/api/v1/trips/42?include=stays',
      status: 502,
      requestId: 'req-123',
      errorId: 'err-456',
      data: longHtml
    };

    // 2. Now that we are on the correct origin, set the sessionStorage item
    await page.evaluate((details) => {
      sessionStorage.setItem('errorDetails', JSON.stringify(details));
    }, errorDetails);

    // 3. Navigate to the error page with a short URL
    const error = {
      type: 'connection',
      title: 'Backend Unavailable',
      message: 'GeoPulse servers are currently unavailable. Please try again later.'
    };
    const url = `/error?type=${error.type}&title=${encodeURIComponent(error.title)}&message=${encodeURIComponent(
      error.message
    )}`;

    const response = await page.goto(url);

    // 4. Assert that the page loads correctly and displays the details from sessionStorage
    expect(response.status()).toBe(200);
    await expect(page.locator('h1:has-text("Backend Unavailable")')).toBeVisible();

    const detailsSummary = page.locator('summary:has-text("Technical Details")');
    await detailsSummary.click();

    const details = page.locator('div.error-details-formatted');
    await expect(details).toBeVisible();

    // Each field ErrorPage reads out of the stored payload is rendered.
    await expect(details).toContainText('GET');
    await expect(details).toContainText('/api/v1/trips/42'); // query string is stripped before display
    await expect(details).toContainText('502');
    await expect(details).toContainText('req-123');
    await expect(details).toContainText('err-456');
  });

  // Regression: a 500 the backend itself produced -- a well-formed problem+json -- is a failure of a
  // live backend. isBackendDown must not classify it as an outage, or the login form is replaced by
  // the error page, which then polls /system/health, finds it healthy, and bounces straight back.
  test('should keep the login form and show an inline error on a backend 500', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const email = 'e2e-login-500@example.com';
    const password = 'correct-horse-battery';

    // Anchored to the login POST. Playwright anchors globs with `$`, so this never matches
    // GET /api/v1/auth/sessions/current, which LoginPage's onMounted issues via getAuthStatus().
    // A trailing `**` would swallow that call and break the page.
    let intercepted = false;
    await page.route('**/api/v1/auth/sessions', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }

      intercepted = true;
      await route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        headers: { 'X-Error-Id': 'e2e-login-500' },
        body: JSON.stringify({
          type: 'urn:geopulse:error:INTERNAL_ERROR',
          status: 500,
          title: 'Internal Server Error',
          instance: '/auth/sessions',
          code: 'INTERNAL_ERROR',
          errorId: 'e2e-login-500',
          requestId: 'req-e2e-login-500'
        })
      });
    });

    await loginPage.navigate();
    await loginPage.login(email, password);

    // The inline error can only render if handleError did not classify the 500 as an outage:
    // redirectToErrorPage would have replaced the document 100ms later.
    await ValidationHelpers.waitForPageErrorMessage(page, loginPage.getErrorSelector());
    const errorMessage = await ValidationHelpers.getPageErrorMessage(page, loginPage.getErrorSelector());
    expect(errorMessage).toContain('Internal Server Error');
    expect(errorMessage).toContain('Check backend logs for ID: e2e-login-500');

    // Nothing the user typed was destroyed.
    await expect(page.locator('#email')).toHaveValue(email);
    await expect(page.locator('#password input')).toHaveValue(password);

    // Outlive redirectToErrorPage's 100ms setTimeout before asserting we never went to /error.
    await page.waitForTimeout(1000);
    expect(TestHelpers.isBackendUnavailableUrl(page.url())).toBe(false);
    expect(await loginPage.isOnLoginPage()).toBe(true);
    expect(intercepted).toBe(true);
  });
});
