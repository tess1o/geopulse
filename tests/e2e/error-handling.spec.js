import { test, expect } from '@playwright/test';

test.describe('Error Handling', () => {
  test('should display the error page with details from query parameters', async ({ page }) => {
    const error = {
      type: 'server',
      title: 'Test Server Error',
      message: 'This is a test error message.',
      details: JSON.stringify({ message: 'Something went wrong on the server.' })
    };

    const url = `/error?type=${error.type}&title=${encodeURIComponent(error.title)}&message=${encodeURIComponent(
      error.message
    )}&details=${encodeURIComponent(error.details)}`;

    await page.goto(url);

    await expect(page.locator('h1:has-text("Test Server Error")')).toBeVisible();
    await expect(page.locator('p:has-text("This is a test error message.")')).toBeVisible();

    // Check for details
    const detailsSummary = page.locator('summary:has-text("Technical Details")');
    await detailsSummary.click();
    await expect(page.locator('div .error-detail-section').first()).toBeVisible();
  });

  test('should display error page correctly with long details passed via sessionStorage', async ({ page }) => {
    // 1. Go to the base URL to establish an origin and a document context
    await page.goto('/');

    // 2. Now that we are on the correct origin, set the sessionStorage item
    const longHtml =
      '<!DOCTYPE html><html><head><title>502: Bad gateway</title></head><body><h1>502: Bad gateway</h1><p>There is an issue with the upstream server.</p></body></html>'.repeat(300);
    const errorDetails = {
      message: 'Request failed with status code 502',
      status: 502,
      data: longHtml
    };

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
    await expect(page.locator('div.error-details-formatted')).toBeVisible();

    // Check for a piece of the long details to confirm it was loaded from sessionStorage
    await expect(page.locator('pre.response-data')).toContainText('502: Bad gateway');
  });
});
