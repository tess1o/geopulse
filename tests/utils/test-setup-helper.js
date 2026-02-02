import { LoginPage } from '../pages/LoginPage.js';
import { ShareLinksPage } from '../pages/ShareLinksPage.js';
import { TestHelpers } from './test-helpers.js';
import { TestData } from '../fixtures/test-data.js';
import { UserFactory } from './user-factory.js';
import { GpsDataFactory } from './gps-data-factory.js';
import {FriendsPage} from "../pages/FriendsPage.js";
import {UserProfilePage} from "../pages/UserProfilePage.js";
import {FavoritesManagementPage} from "../pages/FavoritesManagementPage.js";

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

  static async createAndLoginUserAndNavigateToFriendsPage(page, dbManager, userData = null) {
    const {testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const friendsPage = new FriendsPage(page);
    await friendsPage.navigate();
    await friendsPage.waitForPageLoad();
    return {friendsPage, testUser};
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
   * Login user and navigate to Friends page
   * @param {Page} page - Playwright page object
   * @param {Object} userData - User data with email and password
   * @param {FriendsPage} friendsPage - FriendsPage instance
   * @returns {Promise<void>}
   */
  static async loginAndNavigateToFriendsPage(page, userData, friendsPage) {
    await this.loginExistingUser(page, userData);
    await friendsPage.navigate();
    await friendsPage.waitForPageLoad();
  }

  static async loginAndNavigateToUserProfilePage(page, dbManager, userData) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const profilePage = new UserProfilePage(page);
    await profilePage.navigate();
    await profilePage.waitForPageLoad();
    return {profilePage, user, testUser};
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

  // ==================== FRIENDS TEST HELPERS ====================

  /**
   * Create two users for friends testing (testUser and friendUser)
   * Does NOT login or navigate
   * @returns {Promise<{testUser, friendUser, user, friend, loginPage, friendsPage}>}
   */
  static async setupTwoUserFriendsTest(page, dbManager) {
    const loginPage = new LoginPage(page);
    const friendsPage = new FriendsPage(page);
    const testUser = TestData.users.existing;
    const friendUser = TestData.users.another;

    await UserFactory.createUser(page, testUser);
    await UserFactory.createUser(page, friendUser);

    const user = await dbManager.getUserByEmail(testUser.email);
    const friend = await dbManager.getUserByEmail(friendUser.email);

    return { testUser, friendUser, user, friend, loginPage, friendsPage };
  }

  /**
   * Create two users, login as testUser, and navigate to friends page
   * @returns {Promise<{testUser, friendUser, user, friend, loginPage, friendsPage}>}
   */
  static async setupTwoUserFriendsTestWithLogin(page, dbManager) {
    const { testUser, friendUser, user, friend, loginPage, friendsPage } =
      await this.setupTwoUserFriendsTest(page, dbManager);

    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);
    await TestHelpers.waitForNavigation(page, '**/app/timeline');

    await friendsPage.navigate();
    await friendsPage.waitForPageLoad();

    return { testUser, friendUser, user, friend, loginPage, friendsPage };
  }

  /**
   * Create main user + multiple friends
   * @param {number} friendCount - Number of friends to create (default: 2)
   * @param {boolean} login - Whether to login as main user (default: false)
   * @returns {Promise<{testUser, user, friends, friendsData, loginPage, friendsPage}>}
   *
   * Example:
   *   const {user, friends} = await setupMultipleFriendsTest(page, dbManager, 3, true);
   *   // user is the main user DB object
   *   // friends = [{testData: {email, password, ...}, dbUser: {id, email, ...}}, ...]
   */
  static async setupMultipleFriendsTest(page, dbManager, friendCount = 2, login = false) {
    const loginPage = new LoginPage(page);
    const friendsPage = new FriendsPage(page);
    const testUser = TestData.users.existing;

    // Create main user
    await UserFactory.createUser(page, testUser);

    // Create friends
    const friendsData = [];
    const friends = [];

    for (let i = 0; i < friendCount; i++) {
      const friendData = i === 0
        ? { ...TestData.users.another }
        : TestData.generateUserWithEmail(`friend${i + 1}`);

      await UserFactory.createUser(page, friendData);
      friendsData.push(friendData);

      const dbFriend = await dbManager.getUserByEmail(friendData.email);
      friends.push({ testData: friendData, dbUser: dbFriend });
    }

    const user = await dbManager.getUserByEmail(testUser.email);

    if (login) {
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
    }

    return { testUser, user, friends, friendsData, loginPage, friendsPage };
  }

  /**
   * Set friend permissions between two users
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID who is GRANTING permission
   * @param {string} friendId - Friend ID who is RECEIVING permission
   * @param {Object} permissions - {shareLive: boolean, shareTimeline: boolean}
   */
  static async setFriendPermissions(dbManager, userId, friendId, { shareLive = false, shareTimeline = false }) {
    await dbManager.client.query(`
      INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT (user_id, friend_id)
      DO UPDATE SET
        share_live_location = $3,
        share_timeline = $4
    `, [userId, friendId, shareLive, shareTimeline]);
  }

  /**
   * Create friendship between two users and optionally set permissions
   * @param {Object} dbManager - Database manager
   * @param {string} userId - First user ID
   * @param {string} friendId - Second user ID
   * @param {Object} permissions - Optional: {userToFriend: {shareLive, shareTimeline}, friendToUser: {shareLive, shareTimeline}}
   */
  static async setupFriendship(dbManager, userId, friendId, permissions = null) {
    await FriendsPage.insertFriendship(dbManager, userId, friendId);

    if (permissions) {
      // Set permissions from user to friend
      if (permissions.userToFriend) {
        await this.setFriendPermissions(dbManager, userId, friendId, permissions.userToFriend);
      }
      // Set permissions from friend to user
      if (permissions.friendToUser) {
        await this.setFriendPermissions(dbManager, friendId, userId, permissions.friendToUser);
      }
    }
  }

  /**
   * Create friendship with location data and permissions
   * Useful for Live map tests
   */
  static async setupFriendshipWithLocation(dbManager, userId, friendId, latitude, longitude, friendSharesLive = false) {
    await FriendsPage.insertFriendWithLocation(dbManager, userId, friendId, latitude, longitude);

    if (friendSharesLive) {
      await this.setFriendPermissions(dbManager, friendId, userId, { shareLive: true, shareTimeline: false });
    }
  }

  /**
   * Setup invitation test: creates two users and an invitation
   * @param {boolean} loginAsReceiver - Whether to login as receiver (default: true)
   * @returns {Promise<{sender, receiver, senderData, receiverData, invitationId, loginPage, friendsPage}>}
   */
  static async setupInvitationTest(page, dbManager, loginAsReceiver = true) {
    const { testUser, friendUser, user, friend, loginPage, friendsPage } =
      await this.setupTwoUserFriendsTest(page, dbManager);

    // For invitations: friendUser is sender, testUser is receiver
    const invitationId = await FriendsPage.insertInvitation(dbManager, friend.id, user.id);

    if (loginAsReceiver) {
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
    }

    return {
      sender: friend,
      receiver: user,
      senderData: friendUser,
      receiverData: testUser,
      invitationId,
      loginPage,
      friendsPage
    };
  }

  // ==================== FAVORITES TEST HELPERS ====================

  /**
   * Login user and navigate to Favorites Management page
   * @returns {Promise<{favoritesPage, user, testUser}>}
   */
  static async loginAndNavigateToFavoritesPage(page, dbManager, userData = null) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const favoritesPage = new FavoritesManagementPage(page);
    await favoritesPage.navigate();
    await favoritesPage.waitForPageLoad();
    return {favoritesPage, user, testUser};
  }

  /**
   * Create a favorite point location for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} favorite - Favorite data {name, city, country, latitude, longitude}
   * @returns {Promise<number>} - Favorite ID
   */
  static async createFavoritePoint(dbManager, userId, favorite) {
    const result = await dbManager.client.query(`
      INSERT INTO favorite_locations
      (user_id, name, city, country, type, geometry)
      VALUES ($1, $2, $3, $4, 'POINT', ST_GeomFromText($5, 4326))
      RETURNING id
    `, [
      userId,
      favorite.name,
      favorite.city || null,
      favorite.country || null,
      `POINT(${favorite.longitude} ${favorite.latitude})`
    ]);

    return result.rows[0].id;
  }

  /**
   * Create a favorite area location for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} favorite - Favorite data {name, city, country, southWestLat, southWestLon, northEastLat, northEastLon}
   * @returns {Promise<number>} - Favorite ID
   */
  static async createFavoriteArea(dbManager, userId, favorite) {
    const result = await dbManager.client.query(`
      INSERT INTO favorite_locations
      (user_id, name, city, country, type, geometry)
      VALUES ($1, $2, $3, $4, 'AREA', ST_GeomFromText($5, 4326))
      RETURNING id
    `, [
      userId,
      favorite.name,
      favorite.city || null,
      favorite.country || null,
      `POLYGON((${favorite.southWestLon} ${favorite.southWestLat}, ${favorite.northEastLon} ${favorite.southWestLat}, ${favorite.northEastLon} ${favorite.northEastLat}, ${favorite.southWestLon} ${favorite.northEastLat}, ${favorite.southWestLon} ${favorite.southWestLat}))`
    ]);

    return result.rows[0].id;
  }

  /**
   * Create multiple favorite locations for testing
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {number} pointCount - Number of point favorites to create
   * @param {number} areaCount - Number of area favorites to create
   * @returns {Promise<{points: Array, areas: Array}>}
   */
  static async createMultipleFavorites(dbManager, userId, pointCount = 3, areaCount = 2) {
    const points = [];
    const areas = [];

    // Create point favorites
    for (let i = 0; i < pointCount; i++) {
      const favoriteId = await this.createFavoritePoint(dbManager, userId, {
        name: `Test Point ${i + 1}`,
        city: `City ${i + 1}`,
        country: `Country ${i + 1}`,
        latitude: 40.7128 + (i * 0.01),
        longitude: -74.0060 + (i * 0.01)
      });
      points.push(favoriteId);
    }

    // Create area favorites
    for (let i = 0; i < areaCount; i++) {
      const favoriteId = await this.createFavoriteArea(dbManager, userId, {
        name: `Test Area ${i + 1}`,
        city: `City ${i + 1}`,
        country: `Country ${i + 1}`,
        southWestLat: 40.0 + (i * 0.1),
        southWestLon: -74.0 + (i * 0.1),
        northEastLat: 40.1 + (i * 0.1),
        northEastLon: -73.9 + (i * 0.1)
      });
      areas.push(favoriteId);
    }

    return { points, areas };
  }

  /**
   * Get favorite location by ID
   * @param {Object} dbManager - Database manager
   * @param {number} favoriteId - Favorite ID
   * @returns {Promise<Object>} - Favorite data
   */
  static async getFavoriteById(dbManager, favoriteId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, name, city, country, type,
             ST_X(geometry) as longitude,
             ST_Y(geometry) as latitude
      FROM favorite_locations
      WHERE id = $1
    `, [favoriteId]);

    return result.rows[0] || null;
  }

  /**
   * Count favorites for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {string} type - Optional type filter ('POINT' or 'AREA')
   * @returns {Promise<number>}
   */
  static async countFavorites(dbManager, userId, type = null) {
    let query = 'SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1';
    const params = [userId];

    if (type) {
      query += ' AND type = $2';
      params.push(type);
    }

    const result = await dbManager.client.query(query, params);
    return parseInt(result.rows[0].count);
  }

  /**
   * Delete all favorites for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   */
  static async deleteAllFavorites(dbManager, userId) {
    await dbManager.client.query('DELETE FROM favorite_locations WHERE user_id = $1', [userId]);
  }
}
