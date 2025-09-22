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
     * @param {string} userData.timezone - User timezone (optional, defaults to empty string)
     * @returns {Promise<Response>}
     */

    static async createUser(page, userData) {
        const requestData = {
            email: userData.email,
            fullName: userData.fullName,
            password: userData.password
        };

        // Add timezone if provided, otherwise send empty string
        if (userData.timezone !== undefined) {
            requestData.timezone = userData.timezone;
        } else {
            requestData.timezone = '';
        }

        const response = await page.request.post(TestConfig.API_ENDPOINTS.register, {
            data: requestData
        });

        console.log(`Creating user via API: ${userData.email} with timezone: ${requestData.timezone || 'empty'}`);
        expect(response.ok()).toBeTruthy();
        return response;
    }
}