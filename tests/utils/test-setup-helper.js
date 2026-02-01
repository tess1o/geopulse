import { LoginPage } from '../pages/LoginPage.js';
import { ShareLinksPage } from '../pages/ShareLinksPage.js';
import { TestHelpers } from './test-helpers.js';
import { TestData } from '../fixtures/test-data.js';
import { UserFactory } from './user-factory.js';
import { GpsDataFactory } from './gps-data-factory.js';

/**
 * Centralized test setup utilities to eliminate duplication
 */
export class TestSetupHelper {
  /**
   * Create a user, login, and return necessary objects
   * @returns {Promise<{loginPage, user, testUser}>}
   */
  static async createAndLoginUser(page, dbManager, userData = null) {
    const loginPage = new LoginPage(page);
    const testUser = userData || TestData.users.existing;

    await UserFactory.createUser(page, testUser);
    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);
    await TestHelpers.waitForNavigation(page, '**/app/timeline');

    const user = await dbManager.getUserByEmail(testUser.email);

    return { loginPage, user, testUser };
  }

  /**
   * Create two users for multi-user tests (owner and viewer pattern)
   */
  static async createTwoUsers(page, dbManager, ownerEmail = 'owner@test.com', viewerEmail = 'viewer@test.com') {
    const ownerData = { ...TestData.users.existing, email: ownerEmail };
    const viewerData = { ...TestData.users.existing, email: viewerEmail };

    await UserFactory.createUser(page, ownerData);
    await UserFactory.createUser(page, viewerData);

    const owner = await dbManager.getUserByEmail(ownerEmail);
    const viewer = await dbManager.getUserByEmail(viewerEmail);

    return { ownerData, viewerData, owner, viewer };
  }

  /**
   * Setup for share links tests: create user, login, navigate to share links page
   */
  static async setupShareLinksTest(page, dbManager, userData = null) {
    const { loginPage, user, testUser } = await this.createAndLoginUser(page, dbManager, userData);
    const shareLinksPage = new ShareLinksPage(page);

    await shareLinksPage.navigate();
    await shareLinksPage.waitForPageLoad();

    return { loginPage, shareLinksPage, user, testUser };
  }

  /**
   * Setup for public share access tests: create user with GPS data, then logout
   */
  static async setupPublicShareAccess(page, dbManager, context, gpsPointCount = 5) {
    const { user, testUser } = await this.createAndLoginUser(page, dbManager);

    if (gpsPointCount > 0) {
      await GpsDataFactory.createGpsPointsForUser(dbManager, user.id, gpsPointCount);
    }

    // Logout to simulate guest access
    await context.clearCookies();

    return { user, testUser };
  }

  /**
   * Login an existing user (user must already be created)
   * @param {Page} page - Playwright page object
   * @param {Object} userData - User data with email and password
   * @returns {Promise<void>}
   */
  static async loginExistingUser(page, userData) {
    const loginPage = new LoginPage(page);
    await loginPage.navigate();
    await loginPage.login(userData.email, userData.password);
    await TestHelpers.waitForNavigation(page, '**/app/timeline');
  }

  /**
   * Logout current user and login as different user
   * @param {Page} page - Playwright page object
   * @param {AppNavigation} appNav - App navigation page object
   * @param {Object} userData - User data with email and password
   * @returns {Promise<void>}
   */
  static async switchUser(page, appNav, userData) {
    await appNav.logout();
    await this.loginExistingUser(page, userData);
  }

  /**
   * Setup multi-user test: create owner and viewer, login as owner
   * Use switchUser() to switch to viewer later
   */
  static async setupMultiUserShareTest(page, dbManager, ownerEmail = 'owner@test.com', viewerEmail = 'viewer@test.com') {
    const { ownerData, viewerData, owner, viewer } = await this.createTwoUsers(page, dbManager, ownerEmail, viewerEmail);

    // Login as owner
    await this.loginExistingUser(page, ownerData);

    return { ownerData, viewerData, owner, viewer };
  }
}
