  import { test, expect } from '@playwright/test';
import {TestConfig} from "../config/test-config.js";
import {LoginPage} from "../pages/LoginPage.js";

test.describe('Health Check', () => {
  
  test('should load frontend application', async ({ page }) => {
    // Go to the main page
    await page.goto('/');
    
    // Wait for the page to load
    await page.waitForLoadState('networkidle');
    
    const homePage = page.locator('.landing-page');
    await expect(homePage).toBeVisible();

    // Should show the updated home hero content
    const logo = page.getByRole('img', { name: 'GeoPulse logo' });
    await expect(logo).toBeVisible();

    const heroContent = page.locator('.hero-content');
    await expect(heroContent).toBeVisible();
    await expect(heroContent).toContainText('Self-hosted location timeline');
    await expect(heroContent).toContainText('Self-Host Anywhere');
    await expect(heroContent).toContainText('Start Your Journey');
    await expect(heroContent).toContainText('Sign In');
  });

  test('should access backend health endpoint', async ({ page }) => {
    // Make a request to the backend health endpoint
    const response = await page.request.get(TestConfig.API_BASE_URL + '/api/health');
    
    // Should return 200 OK
    expect(response.status()).toBe(200);
    
    // Should contain health status
    const body = await response.json();
    expect(body.status).toBe('success');
    expect(body.data.status).toBe('UP');
  });

  test('should show login form', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    const loginPage = new LoginPage(page);
    
    // Check for login form elements
    const emailInput = page.locator(loginPage.selectors.emailInput);
    const passwordInput = page.locator(loginPage.selectors.passwordInput);
    const loginButton = page.locator(loginPage.selectors.loginButton);
    
    await expect(emailInput).toBeVisible();
    await expect(passwordInput).toBeVisible();
    await expect(loginButton).toBeVisible();
    
    // Check login button text
    await expect(loginButton).toHaveText('Sign In');
  });
});
