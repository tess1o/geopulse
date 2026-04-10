import { LoginPage } from '../pages/LoginPage.js';
import { ShareLinksPage } from '../pages/ShareLinksPage.js';
import { TestHelpers } from './test-helpers.js';
import { TestData } from '../fixtures/test-data.js';
import { UserFactory } from './user-factory.js';
import { GpsDataFactory } from './gps-data-factory.js';
import { GeocodingFactory } from './geocoding-factory.js';
import {FriendsPage} from "../pages/FriendsPage.js";
import {UserProfilePage} from "../pages/UserProfilePage.js";
import {FavoritesManagementPage} from "../pages/FavoritesManagementPage.js";
import {GeocodingManagementPage} from "../pages/GeocodingManagementPage.js";
import {PeriodTagsManagementPage} from "../pages/PeriodTagsManagementPage.js";
import {TimelineLabelsManagementPage} from "../pages/TimelineLabelsManagementPage.js";
import {TripsManagementPage} from "../pages/TripsManagementPage.js";
import {TripWorkspacePage} from "../pages/TripWorkspacePage.js";
import {DateFormatTestHelper} from './date-format-test-helper.js';
import {GeofencesPage} from "../pages/GeofencesPage.js";

/**
 * Centralized test setup utilities to eliminate duplication
 */
export class TestSetupHelper {
  static async resolveMapModeFromPage(page) {
    try {
      const mode = await page.evaluate(() => window.__GP_E2E_MAP_DEBUG__?.mode || null);
      if (!mode) {
        return null;
      }

      return String(mode).toUpperCase() === 'VECTOR' ? 'VECTOR' : 'RASTER';
    } catch {
      return null;
    }
  }

  static async applyMapRenderModeIfProvided(dbManager, email, mapMode = null) {
    if (!mapMode) {
      return;
    }

    const normalizedMode = String(mapMode).toUpperCase() === 'VECTOR' ? 'VECTOR' : 'RASTER';
    await dbManager.client.query(
      'UPDATE users SET map_render_mode = $2 WHERE email = $1',
      [email, normalizedMode]
    );
  }

  /**
   * Create a user, login, and return necessary objects
   * @returns {Promise<{loginPage, user, testUser}>}
   */
  static async createAndLoginUser(page, dbManager, userData = null, options = {}) {
    const loginPage = new LoginPage(page);
    const testUser = userData || TestData.users.existing;
    const mapMode = options?.mapMode || await this.resolveMapModeFromPage(page);

    await UserFactory.createUser(page, testUser);
    await this.applyMapRenderModeIfProvided(dbManager, testUser.email, mapMode);
    await DateFormatTestHelper.applyDateFormatIfProvided(dbManager, testUser);
    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);
    await TestHelpers.waitForNavigation(page, '**/app/timeline');

    const user = await dbManager.getUserByEmail(testUser.email);

    return { loginPage, user, testUser };
  }

  static async createAndLoginUserAndNavigateToFriendsPage(page, dbManager, userData = null, options = {}) {
    const {testUser} = await this.createAndLoginUser(page, dbManager, userData, options);
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
  static async setupShareLinksTest(page, dbManager, userData = null, options = {}) {
    const { loginPage, user, testUser } = await this.createAndLoginUser(page, dbManager, userData, options);
    const shareLinksPage = new ShareLinksPage(page);

    await shareLinksPage.navigate();
    await shareLinksPage.waitForPageLoad();

    return { loginPage, shareLinksPage, user, testUser };
  }

  /**
   * Setup for public share access tests: create user with GPS data, then logout
   * @param {number} gpsPointCount - Number of GPS points to seed before logout
   * @param {Object|null} userData - Optional user object for isolated test user injection
   */
  static async setupPublicShareAccess(page, dbManager, context, gpsPointCount = 5, userData = null, options = {}) {
    const { user, testUser } = await this.createAndLoginUser(page, dbManager, userData, options);

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

  static async loginAndNavigateToUserProfilePage(page, dbManager, userData, options = {}) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData, options);
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
   * @param {Object|null} testUserData - Optional receiver/main user data
   * @param {Object|null} friendUserData - Optional friend/sender user data
   * @returns {Promise<{testUser, friendUser, user, friend, loginPage, friendsPage}>}
   */
  static async setupTwoUserFriendsTest(page, dbManager, testUserData = null, friendUserData = null) {
    const loginPage = new LoginPage(page);
    const friendsPage = new FriendsPage(page);
    const testUser = testUserData || TestData.buildExistingUser();
    const friendUser = friendUserData || TestData.buildAnotherUser();

    await UserFactory.createUser(page, testUser);
    await UserFactory.createUser(page, friendUser);

    const user = await dbManager.getUserByEmail(testUser.email);
    const friend = await dbManager.getUserByEmail(friendUser.email);

    return { testUser, friendUser, user, friend, loginPage, friendsPage };
  }

  /**
   * Create two users, login as testUser, and navigate to friends page
   * @param {Object|null} testUserData - Optional receiver/main user data
   * @param {Object|null} friendUserData - Optional friend/sender user data
   * @returns {Promise<{testUser, friendUser, user, friend, loginPage, friendsPage}>}
   */
  static async setupTwoUserFriendsTestWithLogin(page, dbManager, testUserData = null, friendUserData = null) {
    const { testUser, friendUser, user, friend, loginPage, friendsPage } =
      await this.setupTwoUserFriendsTest(page, dbManager, testUserData, friendUserData);

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
   * @param {Object|null} testUserData - Optional main user data
   * @param {Array<Object>|null} friendUsersData - Optional array of friend user data
   * @returns {Promise<{testUser, user, friends, friendsData, loginPage, friendsPage}>}
   *
   * Example:
   *   const {user, friends} = await setupMultipleFriendsTest(page, dbManager, 3, true);
   *   // user is the main user DB object
   *   // friends = [{testData: {email, password, ...}, dbUser: {id, email, ...}}, ...]
   */
  static async setupMultipleFriendsTest(
    page,
    dbManager,
    friendCount = 2,
    login = false,
    testUserData = null,
    friendUsersData = null
  ) {
    const loginPage = new LoginPage(page);
    const friendsPage = new FriendsPage(page);
    const testUser = testUserData || TestData.buildExistingUser();

    // Create main user
    await UserFactory.createUser(page, testUser);

    // Create friends
    const friendsData = [];
    const friends = [];

    for (let i = 0; i < friendCount; i++) {
      const friendData = friendUsersData?.[i]
        || (i === 0
          ? TestData.buildAnotherUser()
          : TestData.generateUserWithEmail(`friend${i + 1}`));

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
   * @param {Object|null} receiverData - Optional receiver user data
   * @param {Object|null} senderData - Optional sender user data
   * @returns {Promise<{sender, receiver, senderData, receiverData, invitationId, loginPage, friendsPage}>}
   */
  static async setupInvitationTest(
    page,
    dbManager,
    loginAsReceiver = true,
    receiverData = null,
    senderData = null
  ) {
    const { testUser, friendUser, user, friend, loginPage, friendsPage } =
      await this.setupTwoUserFriendsTest(page, dbManager, receiverData, senderData);

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
   * Login user and navigate to Geofences page
   * @returns {Promise<{geofencesPage, user, testUser}>}
   */
  static async loginAndNavigateToGeofencesPage(page, dbManager, userData = null, options = {}) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData, options);
    const geofencesPage = new GeofencesPage(page);
    await geofencesPage.navigate();
    await geofencesPage.waitForPageLoad();
    return {geofencesPage, user, testUser};
  }

  /**
   * Login user and navigate to Favorites Management page
   * @returns {Promise<{favoritesPage, user, testUser}>}
   */
  static async loginAndNavigateToFavoritesPage(page, dbManager, userData = null, options = {}) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData, options);
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

  // ==================== GEOCODING TEST HELPERS ====================

  /**
   * Login user and navigate to Geocoding Management page
   * @returns {Promise<{geocodingPage, user, testUser}>}
   */
  static async loginAndNavigateToGeocodingPage(page, dbManager, userData = null) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const geocodingPage = new GeocodingManagementPage(page);
    await geocodingPage.navigate();
    await geocodingPage.waitForPageLoad();
    return {geocodingPage, user, testUser};
  }

  /**
   * Create a geocoding result for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID (can be null for original locations)
   * @param {Object} geocoding - Geocoding data {coords, displayName, city, country, providerName}
   * @returns {Promise<number>} - Geocoding result ID
   */
  static async createGeocodingResult(dbManager, userId, geocoding) {
    const coords = geocoding.coords || 'POINT(-74.0060 40.7128)';
    const displayName = geocoding.displayName || 'Test Location';
    const city = geocoding.city || 'New York';
    const country = geocoding.country || 'USA';
    const providerName = geocoding.providerName || 'Nominatim';

    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location
      (id, request_coordinates, result_coordinates, display_name, city, country, provider_name, user_id, created_at, last_accessed_at)
      VALUES (
        nextval('reverse_geocoding_location_seq'),
        ST_GeomFromText($1, 4326),
        ST_GeomFromText($1, 4326),
        $2,
        $3,
        $4,
        $5,
        $6,
        NOW(),
        NOW()
      )
      RETURNING id
    `, [coords, displayName, city, country, providerName, userId]);

    return result.rows[0].id;
  }

  /**
   * Create multiple geocoding results for testing
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID (can be null for original locations)
   * @param {number} count - Number of geocoding results to create
   * @param {string} providerName - Provider name (optional)
   * @returns {Promise<Array<number>>} - Array of geocoding result IDs
   */
  static async createMultipleGeocodingResults(dbManager, userId, count = 3, providerName = null) {
    const resultIds = [];

    for (let i = 0; i < count; i++) {
      const resultId = await this.createGeocodingResult(dbManager, userId, {
        coords: `POINT(${-74.0060 + (i * 0.01)} ${40.7128 + (i * 0.01)})`,
        displayName: `Test Location ${i + 1}`,
        city: `City ${i + 1}`,
        country: `Country ${i + 1}`,
        providerName: providerName || 'Nominatim'
      });
      resultIds.push(resultId);
    }

    return resultIds;
  }

  /**
   * Create geocoding results with different providers
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID (can be null for original locations)
   * @returns {Promise<Object>} - Object with provider names as keys and result IDs as values
   */
  static async createGeocodingResultsWithDifferentProviders(dbManager, userId) {
    const providers = ['Nominatim', 'GoogleMaps', 'Mapbox', 'Photon'];
    const results = {};

    for (let i = 0; i < providers.length; i++) {
      const provider = providers[i];
      const resultId = await this.createGeocodingResult(dbManager, userId, {
        coords: `POINT(${-74.0060 + (i * 0.01)} ${40.7128 + (i * 0.01)})`,
        displayName: `${provider} Location`,
        city: 'New York',
        country: 'USA',
        providerName: provider
      });
      results[provider] = resultId;
    }

    return results;
  }

  /**
   * Get geocoding result by ID
   * @param {Object} dbManager - Database manager
   * @param {number} geocodingId - Geocoding result ID
   * @returns {Promise<Object>} - Geocoding result data
   */
  static async getGeocodingResultById(dbManager, geocodingId) {
    const result = await dbManager.client.query(`
      SELECT id, display_name, city, country, provider_name,
             ST_X(request_coordinates) as longitude,
             ST_Y(request_coordinates) as latitude,
             user_id, created_at, last_accessed_at
      FROM reverse_geocoding_location
      WHERE id = $1
    `, [geocodingId]);

    return result.rows[0] || null;
  }

  /**
   * Count geocoding results for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID (can be null for original locations)
   * @param {string} providerName - Optional provider name filter
   * @returns {Promise<number>}
   */
  static async countGeocodingResults(dbManager, userId = null, providerName = null) {
    let query = 'SELECT COUNT(*) as count FROM reverse_geocoding_location WHERE 1=1';
    const params = [];
    let paramIndex = 1;

    if (userId !== undefined) {
      if (userId === null) {
        query += ' AND user_id IS NULL';
      } else {
        query += ` AND user_id = $${paramIndex}`;
        params.push(userId);
        paramIndex++;
      }
    }

    if (providerName) {
      query += ` AND provider_name = $${paramIndex}`;
      params.push(providerName);
    }

    const result = await dbManager.client.query(query, params);
    return parseInt(result.rows[0].count);
  }

  /**
   * Delete all geocoding results for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID (can be null for original locations)
   */
  static async deleteAllGeocodingResults(dbManager, userId = null) {
    if (userId === null) {
      await dbManager.client.query('DELETE FROM reverse_geocoding_location WHERE user_id IS NULL');
    } else {
      await dbManager.client.query('DELETE FROM reverse_geocoding_location WHERE user_id = $1', [userId]);
    }
  }

  /**
   * Update geocoding result last accessed time
   * @param {Object} dbManager - Database manager
   * @param {number} geocodingId - Geocoding result ID
   * @param {Date} lastAccessedAt - Last accessed timestamp
   */
  static async updateGeocodingLastAccessed(dbManager, geocodingId, lastAccessedAt) {
    await dbManager.client.query(`
      UPDATE reverse_geocoding_location
      SET last_accessed_at = $1
      WHERE id = $2
    `, [lastAccessedAt, geocodingId]);
  }

  // ==================== TIMELINE STAY TEST HELPERS ====================

  /**
   * Create a timeline stay for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} options - Timeline stay options
   * @param {number} options.geocodingId - Geocoding result ID (optional)
   * @param {number} options.favoriteId - Favorite location ID (optional)
   * @param {string} options.coords - Point coordinates (e.g., 'POINT(-74.0060 40.7128)')
   * @param {string} options.locationName - Location name
   * @param {number} options.durationSeconds - Duration in seconds (default: 3600 = 1 hour)
   * @param {string} options.timestampOffset - Timestamp offset (e.g., '2 hours', '4 hours')
   * @param {string} options.locationSource - Location source ('FAVORITE', 'GEOCODING', or 'HISTORICAL', default: auto-detect)
   * @returns {Promise<number>} - Timeline stay ID
   */
  static async createTimelineStay(dbManager, userId, options) {
    const {
      geocodingId = null,
      favoriteId = null,
      coords = 'POINT(-74.0060 40.7128)',
      locationName = 'Test Location',
      durationSeconds = 3600,
      timestampOffset = '2 hours',
      locationSource = null
    } = options;

    // Auto-detect location source if not provided
    const source = locationSource || (favoriteId ? 'FAVORITE' : 'GEOCODING');

    const result = await dbManager.client.query(`
      INSERT INTO timeline_stays (
        user_id, geocoding_id, favorite_id, timestamp, stay_duration,
        location, location_name, location_source,
        created_at, last_updated
      )
      VALUES (
        $1, $2, $3, NOW() - INTERVAL '${timestampOffset}', $4,
        ST_GeomFromText($5, 4326), $6, $7,
        NOW(), NOW()
      )
      RETURNING id
    `, [userId, geocodingId, favoriteId, durationSeconds, coords, locationName, source]);

    return result.rows[0].id;
  }

  /**
   * Create multiple timeline stays for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Array<Object>} staysData - Array of timeline stay options
   * @returns {Promise<Array<number>>} - Array of timeline stay IDs
   */
  static async createMultipleTimelineStays(dbManager, userId, staysData) {
    const stayIds = [];
    for (const stayData of staysData) {
      const stayId = await this.createTimelineStay(dbManager, userId, stayData);
      stayIds.push(stayId);
    }
    return stayIds;
  }

  /**
   * Get timeline stay by ID
   * @param {Object} dbManager - Database manager
   * @param {number} stayId - Timeline stay ID
   * @returns {Promise<Object>} - Timeline stay data
   */
  static async getTimelineStayById(dbManager, stayId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, geocoding_id, timestamp, stay_duration,
             location_name, location_source
      FROM timeline_stays
      WHERE id = $1
    `, [stayId]);

    return result.rows[0] || null;
  }

  /**
   * Get timeline stays by user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Array<Object>>} - Array of timeline stays
   */
  static async getTimelineStaysByUser(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, geocoding_id, timestamp, stay_duration,
             location_name, location_source
      FROM timeline_stays
      WHERE user_id = $1
      ORDER BY timestamp DESC
    `, [userId]);

    return result.rows;
  }

  /**
   * Get distinct geocoding IDs used in user's timeline stays
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Array<number>>} - Array of distinct geocoding IDs
   */
  static async getDistinctGeocodingIdsForUser(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT DISTINCT geocoding_id
      FROM timeline_stays
      WHERE user_id = $1 AND geocoding_id IS NOT NULL
    `, [userId]);

    return result.rows.map(row => row.geocoding_id);
  }

  // ==================== USER TEST HELPERS ====================

  /**
   * Create an additional test user (for multi-user scenarios)
   * @param {Object} dbManager - Database manager
   * @param {string} email - User email
   * @returns {Promise<string>} - User ID
   */
  static async createAdditionalUser(dbManager, email) {
    await dbManager.client.query(`
      INSERT INTO users (id, email, password_hash, emailverified, created_at, updated_at)
      VALUES (gen_random_uuid(), $1, 'hash', true, NOW(), NOW())
    `, [email]);

    const result = await dbManager.client.query(
      'SELECT id FROM users WHERE email = $1',
      [email]
    );

    return result.rows[0].id;
  }

  // ==================== PERIOD TAGS TEST HELPERS ====================

  /**
   * Login user and navigate to Period Tags Management page
   * @returns {Promise<{periodTagsPage, user, testUser}>}
   */
  static async loginAndNavigateToPeriodTagsPage(page, dbManager, userData = null) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const periodTagsPage = new PeriodTagsManagementPage(page);
    await periodTagsPage.navigate();
    await periodTagsPage.waitForPageLoad();
    return {periodTagsPage, user, testUser};
  }

  /**
   * Login user and navigate to Timeline Labels page
   * @returns {Promise<{timelineLabelsPage, user, testUser}>}
   */
  static async loginAndNavigateToTimelineLabelsPage(page, dbManager, userData = null) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const timelineLabelsPage = new TimelineLabelsManagementPage(page);
    await timelineLabelsPage.navigate();
    await timelineLabelsPage.waitForPageLoad();
    return {timelineLabelsPage, user, testUser};
  }

  /**
   * Login user and navigate to Trip Plans page
   * @returns {Promise<{tripsPage, user, testUser}>}
   */
  static async loginAndNavigateToTripsPage(page, dbManager, userData = null) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const tripsPage = new TripsManagementPage(page);
    await tripsPage.navigate();
    await tripsPage.waitForPageLoad();
    return {tripsPage, user, testUser};
  }

  /**
   * Login user and navigate to specific Trip Workspace page
   * @returns {Promise<{tripWorkspacePage, user, testUser}>}
   */
  static async loginAndNavigateToTripWorkspacePage(page, dbManager, tripId, userData = null, options = {}) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData, options);
    const tripWorkspacePage = new TripWorkspacePage(page);
    await tripWorkspacePage.navigate(tripId);
    await tripWorkspacePage.waitForPageLoad();
    return {tripWorkspacePage, user, testUser};
  }

  /**
   * Create a period tag for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} options - Period tag options
   * @param {string} options.tagName - Tag name
   * @param {Date} options.startTime - Start time
   * @param {Date} options.endTime - End time (optional, null for active tags)
   * @param {string} options.source - Source ('manual' or 'owntracks')
   * @param {boolean} options.isActive - Whether the tag is active (default: false)
   * @returns {Promise<number>} - Period tag ID
   */
  static async createPeriodTag(dbManager, userId, options) {
    const {
      tagName,
      startTime,
      endTime = null,
      source = 'manual',
      isActive = false,
      color = null
    } = options;

    const result = await dbManager.client.query(`
      INSERT INTO period_tags (user_id, tag_name, start_time, end_time, source, is_active, color, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
      RETURNING id
    `, [userId, tagName, startTime, endTime, source, isActive, color]);

    return result.rows[0].id;
  }

  /**
   * Create multiple period tags for testing
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {number} count - Number of period tags to create
   * @param {string} source - Source ('manual' or 'owntracks')
   * @returns {Promise<Array<number>>} - Array of period tag IDs
   */
  static async createMultiplePeriodTags(dbManager, userId, count = 3, source = 'manual') {
    const tagIds = [];
    const baseDate = new Date('2024-01-01T00:00:00Z');

    for (let i = 0; i < count; i++) {
      const startTime = new Date(baseDate);
      startTime.setDate(baseDate.getDate() + (i * 7)); // 1 week apart

      const endTime = new Date(startTime);
      endTime.setDate(startTime.getDate() + 3); // 3-day duration

      const tagId = await this.createPeriodTag(dbManager, userId, {
        tagName: `Test Tag ${i + 1}`,
        startTime,
        endTime,
        source
      });
      tagIds.push(tagId);
    }

    return tagIds;
  }

  /**
   * Create an active period tag (no end time)
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} options - Period tag options
   * @returns {Promise<number>} - Period tag ID
   */
  static async createActivePeriodTag(dbManager, userId, options = {}) {
    const {
      tagName = 'Active Tag',
      startTime = new Date(Date.now() - 86400000), // 1 day ago
      source = 'owntracks'
    } = options;

    return await this.createPeriodTag(dbManager, userId, {
      tagName,
      startTime,
      endTime: null,
      source,
      isActive: true  // Active tags must have isActive = true
    });
  }

  /**
   * Get period tag by ID
   * @param {Object} dbManager - Database manager
   * @param {number} tagId - Period tag ID
   * @returns {Promise<Object>} - Period tag data
   */
  static async getPeriodTagById(dbManager, tagId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, tag_name, start_time, end_time, source, is_active, color, created_at
      FROM period_tags
      WHERE id = $1
    `, [tagId]);

    return result.rows[0] || null;
  }

  /**
   * Get all period tags for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Array<Object>>} - Array of period tags
   */
  static async getPeriodTagsByUser(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, tag_name, start_time, end_time, source, is_active, color, created_at
      FROM period_tags
      WHERE user_id = $1
      ORDER BY start_time DESC
    `, [userId]);

    return result.rows;
  }

  /**
   * Count period tags for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {string} source - Optional source filter ('manual' or 'owntracks')
   * @returns {Promise<number>}
   */
  static async countPeriodTags(dbManager, userId, source = null) {
    let query = 'SELECT COUNT(*) as count FROM period_tags WHERE user_id = $1';
    const params = [userId];

    if (source) {
      query += ' AND source = $2';
      params.push(source);
    }

    const result = await dbManager.client.query(query, params);
    return parseInt(result.rows[0].count);
  }

  /**
   * Get active period tag for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Object>} - Active period tag or null
   */
  static async getActivePeriodTag(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, tag_name, start_time, end_time, source, created_at
      FROM period_tags
      WHERE user_id = $1 AND end_time IS NULL
      ORDER BY start_time DESC
      LIMIT 1
    `, [userId]);

    return result.rows[0] || null;
  }

  /**
   * Update period tag
   * @param {Object} dbManager - Database manager
   * @param {number} tagId - Period tag ID
   * @param {Object} updates - Fields to update
   * @returns {Promise<void>}
   */
  static async updatePeriodTag(dbManager, tagId, updates) {
    const fields = [];
    const values = [];
    let paramIndex = 1;

    if (updates.tagName !== undefined) {
      fields.push(`tag_name = $${paramIndex++}`);
      values.push(updates.tagName);
    }
    if (updates.startTime !== undefined) {
      fields.push(`start_time = $${paramIndex++}`);
      values.push(updates.startTime);
    }
    if (updates.endTime !== undefined) {
      fields.push(`end_time = $${paramIndex++}`);
      values.push(updates.endTime);
    }

    if (fields.length === 0) return;

    values.push(tagId);
    const query = `UPDATE period_tags SET ${fields.join(', ')} WHERE id = $${paramIndex}`;
    await dbManager.client.query(query, values);
  }

  /**
   * Delete period tag
   * @param {Object} dbManager - Database manager
   * @param {number} tagId - Period tag ID
   * @returns {Promise<void>}
   */
  static async deletePeriodTag(dbManager, tagId) {
    await dbManager.client.query('DELETE FROM period_tags WHERE id = $1', [tagId]);
  }

  /**
   * Delete all period tags for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<void>}
   */
  static async deleteAllPeriodTags(dbManager, userId) {
    await dbManager.client.query('DELETE FROM period_tags WHERE user_id = $1', [userId]);
  }

  /**
   * Setup timeline with period tag: login, create timeline data, create period tag, navigate to date range
   * @param {Object} page - Playwright page object
   * @param {Object} dbManager - Database manager
   * @param {Object} options - Setup options
   * @param {Object} options.userData - User data (optional, uses TestData.users.existing by default)
   * @param {Function} options.timelineDataFn - Function to insert timeline data (e.g., TimelineTestData.insertRegularStaysTestData)
   * @param {Object} options.periodTag - Period tag options {tagName, startTime, endTime, source}
   * @param {Date} options.startDate - Start date for navigation
   * @param {Date} options.endDate - End date for navigation
   * @param {boolean} options.skipNavigation - Skip navigation to timeline (default: false)
   * @returns {Promise<{timelinePage, user, testUser, periodTagId}>}
   */
  static async setupTimelineWithPeriodTag(page, dbManager, options) {
    const TimelinePage = (await import('../pages/TimelinePage.js')).TimelinePage;
    const TestData = (await import('../fixtures/test-data.js')).TestData;

    const {
      userData = TestData.users.existing,
      timelineDataFn = null,
      periodTag,
      startDate,
      endDate,
      skipNavigation = false
    } = options;

    // Login and navigate
    const timelinePage = new TimelinePage(page);
    const { testUser } = await timelinePage.loginAndNavigate(userData);

    // Get user from DB
    const user = await dbManager.getUserByEmail(testUser.email);

    // Insert timeline data if provided
    if (timelineDataFn) {
      await timelineDataFn(dbManager, user.id);
    }

    // Create period tag if provided
    let periodTagId = null;
    if (periodTag) {
      periodTagId = await this.createPeriodTag(dbManager, user.id, periodTag);
    }

    // Navigate to date range if not skipped
    if (!skipNavigation && startDate && endDate) {
      await timelinePage.navigateWithDateRange(startDate, endDate);
      await timelinePage.waitForPageLoad();
      await timelinePage.waitForTimelineContent();
    }

    return { timelinePage, user, testUser, periodTagId };
  }

  /**
   * Check if period tag is visible on timeline
   * @param {Object} page - Playwright page object
   * @param {string} tagName - Tag name to check
   * @returns {Promise<boolean>} - True if visible
   */
  static async isPeriodTagVisible(page, tagName) {
    const periodTagBanner = page.locator(`.gp-period-badge:has-text("${tagName}"), .p-message:has-text("${tagName}")`);
    return await periodTagBanner.isVisible();
  }

  /**
   * Assert period tag visibility on timeline
   * @param {Object} page - Playwright page object
   * @param {string} tagName - Tag name to check
   * @param {boolean} shouldBeVisible - Expected visibility (default: true)
   * @param {Object} expect - Playwright expect object
   * @returns {Promise<void>}
   */
  static async assertPeriodTagVisibility(page, tagName, shouldBeVisible = true, expect) {
    const periodTagBanner = page.locator(`.gp-period-badge:has-text("${tagName}"), .p-message:has-text("${tagName}")`);
    expect(await periodTagBanner.isVisible()).toBe(shouldBeVisible);
  }

  /**
   * Create multiple period tags for timeline testing
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Array<Object>} periodTags - Array of period tag options
   * @returns {Promise<Array<number>>} - Array of period tag IDs
   */
  static async createMultiplePeriodTagsForTimeline(dbManager, userId, periodTags) {
    const tagIds = [];
    for (const periodTag of periodTags) {
      const tagId = await this.createPeriodTag(dbManager, userId, periodTag);
      tagIds.push(tagId);
    }
    return tagIds;
  }

  // ==================== LOCATION ANALYTICS TEST HELPERS ====================

  /**
   * Login user and navigate to Location Analytics page
   * @returns {Promise<{locationAnalyticsPage, user, testUser}>}
   */
  static async loginAndNavigateToLocationAnalyticsPage(page, dbManager, userData = null) {
    const {user, testUser} = await this.createAndLoginUser(page, dbManager, userData);
    const LocationAnalyticsPage = (await import('../pages/LocationAnalyticsPage.js')).LocationAnalyticsPage;
    const locationAnalyticsPage = new LocationAnalyticsPage(page);
    await locationAnalyticsPage.navigateToTab('cities');
    await locationAnalyticsPage.waitForPageLoad();
    return {locationAnalyticsPage, user, testUser};
  }

  /**
   * Create timeline stays for location analytics testing
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Array<Object>} locations - Array of location data
   * @returns {Promise<Array<number>>} - Array of timeline stay IDs
   *
   * Example location object:
   * {
   *   city: 'New York',
   *   country: 'USA',
   *   latitude: 40.7128,
   *   longitude: -74.0060,
   *   visitCount: 3,
   *   locationName: 'Times Square'
   * }
   */
  static async createLocationAnalyticsData(dbManager, userId, locations) {
    const stayIds = [];

    for (const location of locations) {
      const {
        city,
        country,
        latitude,
        longitude,
        visitCount = 1,
        locationName = 'Test Location'
      } = location;

      // First, create or get geocoding result
      const geocodingId = await this.createGeocodingResult(dbManager, userId, {
        coords: `POINT(${longitude} ${latitude})`,
        displayName: locationName,
        city,
        country,
        providerName: 'Nominatim'
      });

      // Create multiple timeline stays for this location
      for (let i = 0; i < visitCount; i++) {
        const hoursAgo = (i + 1) * 24; // Space out visits by days
        const stayId = await this.createTimelineStay(dbManager, userId, {
          geocodingId,
          coords: `POINT(${longitude} ${latitude})`,
          locationName,
          durationSeconds: 3600, // 1 hour
          timestampOffset: `${hoursAgo} hours`
        });
        stayIds.push(stayId);
      }
    }

    return stayIds;
  }

  /**
   * Create diverse location analytics test data with cities and countries
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Object>} - Object with cityCount, countryCount, stayIds
   */
  static async createDiverseLocationAnalyticsData(dbManager, userId) {
    const locations = [
      // USA cities
      {
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060,
        visitCount: 5,
        locationName: 'Times Square'
      },
      {
        city: 'Los Angeles',
        country: 'USA',
        latitude: 34.0522,
        longitude: -118.2437,
        visitCount: 3,
        locationName: 'Hollywood'
      },
      {
        city: 'San Francisco',
        country: 'USA',
        latitude: 37.7749,
        longitude: -122.4194,
        visitCount: 4,
        locationName: 'Golden Gate Bridge'
      },

      // UK cities
      {
        city: 'London',
        country: 'United Kingdom',
        latitude: 51.5074,
        longitude: -0.1278,
        visitCount: 6,
        locationName: 'Big Ben'
      },
      {
        city: 'Manchester',
        country: 'United Kingdom',
        latitude: 53.4808,
        longitude: -2.2426,
        visitCount: 2,
        locationName: 'Old Trafford'
      },

      // France cities
      {
        city: 'Paris',
        country: 'France',
        latitude: 48.8566,
        longitude: 2.3522,
        visitCount: 7,
        locationName: 'Eiffel Tower'
      },

      // Japan cities
      {
        city: 'Tokyo',
        country: 'Japan',
        latitude: 35.6762,
        longitude: 139.6503,
        visitCount: 4,
        locationName: 'Shibuya Crossing'
      }
    ];

    const stayIds = await this.createLocationAnalyticsData(dbManager, userId, locations);

    return {
      cityCount: locations.length,
      countryCount: 4, // USA, UK, France, Japan
      stayIds
    };
  }

  /**
   * Create location analytics data for a single city with multiple places
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} options - City options
   * @returns {Promise<Object>} - Object with cityData and stayIds
   */
  static async createSingleCityAnalyticsData(dbManager, userId, options = {}) {
    const {
      city = 'New York',
      country = 'USA',
      placeCount = 3,
      visitsPerPlace = 2
    } = options;

    const locations = [];
    const baseLatitude = 40.7128;
    const baseLongitude = -74.0060;

    for (let i = 0; i < placeCount; i++) {
      locations.push({
        city,
        country,
        latitude: baseLatitude + (i * 0.01),
        longitude: baseLongitude + (i * 0.01),
        visitCount: visitsPerPlace,
        locationName: `Place ${i + 1} in ${city}`
      });
    }

    const stayIds = await this.createLocationAnalyticsData(dbManager, userId, locations);

    return {
      cityData: { city, country, placeCount, totalVisits: placeCount * visitsPerPlace },
      stayIds
    };
  }

  /**
   * Create location analytics data for a single country with multiple cities
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {Object} options - Country options
   * @returns {Promise<Object>} - Object with countryData and stayIds
   */
  static async createSingleCountryAnalyticsData(dbManager, userId, options = {}) {
    const {
      country = 'USA',
      cityCount = 3,
      visitsPerCity = 2
    } = options;

    const cities = [
      { name: 'New York', lat: 40.7128, lon: -74.0060 },
      { name: 'Los Angeles', lat: 34.0522, lon: -118.2437 },
      { name: 'Chicago', lat: 41.8781, lon: -87.6298 },
      { name: 'Houston', lat: 29.7604, lon: -95.3698 },
      { name: 'Phoenix', lat: 33.4484, lon: -112.0740 }
    ];

    const locations = [];
    for (let i = 0; i < Math.min(cityCount, cities.length); i++) {
      const city = cities[i];
      locations.push({
        city: city.name,
        country,
        latitude: city.lat,
        longitude: city.lon,
        visitCount: visitsPerCity,
        locationName: `Location in ${city.name}`
      });
    }

    const stayIds = await this.createLocationAnalyticsData(dbManager, userId, locations);

    return {
      countryData: { country, cityCount, totalVisits: cityCount * visitsPerCity },
      stayIds
    };
  }

  /**
   * Get city statistics from database
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {string} cityName - City name
   * @returns {Promise<Object>} - City statistics
   */
  static async getCityStatistics(dbManager, userId, cityName) {
    const result = await dbManager.client.query(`
      SELECT
        COUNT(*) as visit_count,
        COUNT(DISTINCT rgl.id) as unique_places
      FROM timeline_stays ts
      JOIN reverse_geocoding_location rgl ON ts.geocoding_id = rgl.id
      WHERE ts.user_id = $1 AND rgl.city = $2
    `, [userId, cityName]);

    return result.rows[0] || null;
  }

  /**
   * Get country statistics from database
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @param {string} countryName - Country name
   * @returns {Promise<Object>} - Country statistics
   */
  static async getCountryStatistics(dbManager, userId, countryName) {
    const result = await dbManager.client.query(`
      SELECT
        COUNT(*) as visit_count,
        COUNT(DISTINCT rgl.city) as city_count,
        COUNT(DISTINCT rgl.id) as unique_places
      FROM timeline_stays ts
      JOIN reverse_geocoding_location rgl ON ts.geocoding_id = rgl.id
      WHERE ts.user_id = $1 AND rgl.country = $2
    `, [userId, countryName]);

    return result.rows[0] || null;
  }

  /**
   * Get all cities with visit counts for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Array>} - Array of cities with visit counts
   */
  static async getAllCitiesForUser(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT
        rgl.city,
        rgl.country,
        COUNT(*) as visit_count,
        COUNT(DISTINCT rgl.id) as unique_places
      FROM timeline_stays ts
      JOIN reverse_geocoding_location rgl ON ts.geocoding_id = rgl.id
      WHERE ts.user_id = $1 AND rgl.city IS NOT NULL
      GROUP BY rgl.city, rgl.country
      ORDER BY visit_count DESC
    `, [userId]);

    return result.rows;
  }

  /**
   * Get all countries with visit counts for a user
   * @param {Object} dbManager - Database manager
   * @param {string} userId - User ID
   * @returns {Promise<Array>} - Array of countries with visit counts
   */
  static async getAllCountriesForUser(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT
        rgl.country,
        COUNT(*) as visit_count,
        COUNT(DISTINCT rgl.city) as city_count,
        COUNT(DISTINCT rgl.id) as unique_places
      FROM timeline_stays ts
      JOIN reverse_geocoding_location rgl ON ts.geocoding_id = rgl.id
      WHERE ts.user_id = $1 AND rgl.country IS NOT NULL
      GROUP BY rgl.country
      ORDER BY visit_count DESC
    `, [userId]);

    return result.rows;
  }

  // ==================== TRIP PLANNER TEST HELPERS ====================

  /**
   * Create trip for user
   * @returns {Promise<number>} trip ID
   */
  static async createTrip(dbManager, userId, options) {
    const {
      name = 'Test Trip',
      startTime,
      endTime,
      status = 'UPCOMING',
      color = '#3B82F6',
      notes = null,
      periodTagId = null
    } = options;

    const result = await dbManager.client.query(`
      INSERT INTO trips (
        user_id, period_tag_id, name, start_time, end_time, status, color, notes, created_at, updated_at
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW(), NOW())
      RETURNING id
    `, [userId, periodTagId, name, startTime, endTime, status, color, notes]);

    return result.rows[0].id;
  }

  /**
   * Get trip by ID
   */
  static async getTripById(dbManager, tripId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, period_tag_id, name, start_time, end_time, status, color, notes
      FROM trips
      WHERE id = $1
    `, [tripId]);

    return result.rows[0] || null;
  }

  /**
   * Get trip by name
   */
  static async getTripByName(dbManager, userId, name) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, period_tag_id, name, start_time, end_time, status, color, notes
      FROM trips
      WHERE user_id = $1 AND name = $2
      ORDER BY id DESC
      LIMIT 1
    `, [userId, name]);

    return result.rows[0] || null;
  }

  /**
   * Count trips for user
   */
  static async countTrips(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT COUNT(*)::int AS count
      FROM trips
      WHERE user_id = $1
    `, [userId]);

    return result.rows[0].count;
  }

  /**
   * Get trip by linked period tag id
   */
  static async getTripByPeriodTagId(dbManager, periodTagId) {
    const result = await dbManager.client.query(`
      SELECT id, user_id, period_tag_id, name, start_time, end_time, status, color, notes
      FROM trips
      WHERE period_tag_id = $1
      ORDER BY id DESC
      LIMIT 1
    `, [periodTagId]);

    return result.rows[0] || null;
  }

  /**
   * Create a trip plan item
   * @returns {Promise<number>} plan item ID
   */
  static async createTripPlanItem(dbManager, tripId, options = {}) {
    const {
      title = 'Planned stop',
      notes = null,
      latitude = null,
      longitude = null,
      plannedDay = null,
      priority = 'OPTIONAL',
      orderIndex = 0,
      isVisited = false,
      visitConfidence = null,
      visitSource = null,
      visitedAt = null,
      manualOverrideState = null
    } = options;

    const result = await dbManager.client.query(`
      INSERT INTO trip_plan_items (
        trip_id, title, notes, latitude, longitude, planned_day, priority, order_index, is_visited,
        visit_confidence, visit_source, visited_at, manual_override_state, created_at, updated_at
      )
      VALUES (
        $1, $2, $3, $4, $5, $6, $7, $8, $9,
        $10, $11, $12, $13, NOW(), NOW()
      )
      RETURNING id
    `, [
      tripId,
      title,
      notes,
      latitude,
      longitude,
      plannedDay,
      priority,
      orderIndex,
      isVisited,
      visitConfidence,
      visitSource,
      visitedAt,
      manualOverrideState
    ]);

    return result.rows[0].id;
  }

  /**
   * Get trip plan item by ID
   */
  static async getTripPlanItemById(dbManager, planItemId) {
    const result = await dbManager.client.query(`
      SELECT id, trip_id, title, notes, latitude, longitude, planned_day, priority, order_index,
             is_visited, visit_confidence, visit_source, visited_at, manual_override_state
      FROM trip_plan_items
      WHERE id = $1
    `, [planItemId]);

    return result.rows[0] || null;
  }

  /**
   * Get trip plan item by title for a trip
   */
  static async getTripPlanItemByTitle(dbManager, tripId, title) {
    const result = await dbManager.client.query(`
      SELECT id, trip_id, title, notes, latitude, longitude, planned_day, priority, order_index,
             is_visited, visit_confidence, visit_source, visited_at, manual_override_state
      FROM trip_plan_items
      WHERE trip_id = $1 AND title = $2
      ORDER BY id DESC
      LIMIT 1
    `, [tripId, title]);

    return result.rows[0] || null;
  }

  /**
   * Count trip plan items for trip
   */
  static async countTripPlanItems(dbManager, tripId) {
    const result = await dbManager.client.query(`
      SELECT COUNT(*)::int AS count
      FROM trip_plan_items
      WHERE trip_id = $1
    `, [tripId]);

    return result.rows[0].count;
  }

  /**
   * Create precomputed trip visit match
   */
  static async createTripVisitMatch(dbManager, options) {
    const {
      tripId,
      planItemId,
      stayId = null,
      distanceMeters = 0,
      dwellSeconds = 0,
      confidence = 0.9,
      decision = 'AUTO_MATCHED'
    } = options;

    const result = await dbManager.client.query(`
      INSERT INTO trip_place_visit_match (
        trip_id, plan_item_id, stay_id, distance_meters, dwell_seconds, confidence, decision, created_at
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
      RETURNING id
    `, [tripId, planItemId, stayId, distanceMeters, dwellSeconds, confidence, decision]);

    return result.rows[0].id;
  }

  /**
   * Create timeline stay at specific timestamp
   */
  static async createTimelineStayAt(dbManager, userId, options = {}) {
    const {
      timestamp,
      durationSeconds = 1800,
      latitude = 51.5007,
      longitude = -0.1246,
      locationName = 'Matched Stay',
      locationSource = 'GEOCODING'
    } = options;

    const result = await dbManager.client.query(`
      INSERT INTO timeline_stays (
        user_id, timestamp, stay_duration, location, location_name, location_source, created_at, last_updated
      )
      VALUES (
        $1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW()
      )
      RETURNING id
    `, [userId, timestamp, durationSeconds, longitude, latitude, locationName, locationSource]);

    return result.rows[0].id;
  }

  /**
   * Create linked label + trip seed
   */
  static async createLinkedLabelAndTrip(dbManager, userId, options = {}) {
    const now = new Date();
    const startTime = options.startTime || new Date(now.getTime() - 5 * 24 * 60 * 60 * 1000);
    const endTime = options.endTime || new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000);
    const name = options.name || 'Linked Trip';
    const color = options.color || '#7C3AED';

    const periodTagId = await this.createPeriodTag(dbManager, userId, {
      tagName: name,
      startTime,
      endTime,
      source: options.periodTagSource || 'manual'
    });

    await dbManager.client.query('UPDATE period_tags SET color = $1 WHERE id = $2', [color, periodTagId]);

    const tripId = await this.createTrip(dbManager, userId, {
      name,
      startTime,
      endTime,
      status: options.tripStatus || 'COMPLETED',
      color,
      notes: options.notes || null,
      periodTagId
    });

    return { periodTagId, tripId };
  }
}
