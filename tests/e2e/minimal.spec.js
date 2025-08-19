import { test, expect } from '@playwright/test';

test.describe('Minimal E2E Test', () => {
  
  test('should load and interact with login page', async ({ page }) => {
    // Navigate to login page
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    
    // Check basic elements are present
    const emailInput = page.locator('#email');
    const passwordInput = page.locator('#password input');
    const submitButton = page.locator('button[type="submit"]');
    
    await expect(emailInput).toBeVisible();
    await expect(passwordInput).toBeVisible();
    await expect(submitButton).toBeVisible();
    
    // Try to fill out the form
    await emailInput.fill('test@example.com');
    await passwordInput.fill('password123');
    
    // Verify form was filled
    await expect(emailInput).toHaveValue('test@example.com');
    
    console.log('✅ Login page test passed!');
  });

  test('should load register page', async ({ page }) => {
    // Navigate to register page
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    
    // Check basic elements are present
    const emailInput = page.locator('#email');
    const nameInput = page.locator('#fullName');
    const submitButton = page.locator('button[type="submit"]');
    
    await expect(emailInput).toBeVisible();
    await expect(nameInput).toBeVisible();
    await expect(submitButton).toBeVisible();
    
    console.log('✅ Register page test passed!');
  });
});