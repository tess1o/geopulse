import {expect} from '@playwright/test';
import {TestConfig} from '../config/test-config.js';

export class UserFactory {
    /**
     * Create a user via API for testing purposes
     * @param {import('@playwright/test').Page} page
     * @param {Object} userData - User data object
     * @param {string} userData.email - User email
     * @param {string} userData.fullName - User full name
     * @param {string} userData.password - User password
     * @returns {Promise<Response>}
     */

    static async createUser(page, userData) {
        const response = await page.request.post(TestConfig.API_ENDPOINTS.register, {
            data: {
                email: userData.email,
                fullName: userData.fullName,
                password: userData.password
            }
        });

        console.log(`Creating user via API: ${userData.email}`);
        expect(response.ok()).toBeTruthy();
        return response;
    }
}