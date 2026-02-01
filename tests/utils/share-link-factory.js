import { DateFactory } from './date-factory.js';
import { TestConstants } from '../fixtures/test-constants.js';

/**
 * Factory for creating share links in database
 * Consolidates database operations from ShareLinksPage, SharedLocationPage, SharedTimelinePage
 */
export class ShareLinkFactory {
  /**
   * Generic share link creation
   * @private
   */
  static async _insertShareLink(dbManager, linkData) {
    const id = linkData.id || TestConstants.generateTestUUID();
    const shareType = linkData.share_type || 'LIVE_LOCATION';

    const result = await dbManager.client.query(`
      INSERT INTO shared_link (
        id, name, expires_at, password, show_history, user_id, created_at, view_count,
        share_type, start_date, end_date, show_current_location, show_photos,
        history_hours, custom_map_tile_url
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
      RETURNING *
    `, [
      id,
      linkData.name || 'Test Link',
      linkData.expires_at,
      linkData.password || null,
      linkData.show_history || false,
      linkData.user_id,
      linkData.created_at || new Date().toISOString(),
      linkData.view_count || 0,
      shareType,
      linkData.start_date || null,
      linkData.end_date || null,
      linkData.show_current_location !== undefined ? linkData.show_current_location : null,
      linkData.show_photos || false,
      linkData.history_hours || 24,
      linkData.custom_map_tile_url || null
    ]);
    return result.rows[0];
  }

  /**
   * Create a basic live location share link
   */
  static async createLiveLocation(dbManager, userId, options = {}) {
    const defaults = {
      user_id: userId,
      name: 'Live Location Share',
      expires_at: DateFactory.futureDate(7).toISOString(),
      share_type: 'LIVE_LOCATION',
      show_history: false,
      password: options.password || null
    };

    return await this._insertShareLink(dbManager, { ...defaults, ...options });
  }

  /**
   * Create a live location share with history
   */
  static async createLiveLocationWithHistory(dbManager, userId, historyHours = 24, options = {}) {
    return await this.createLiveLocation(dbManager, userId, {
      show_history: true,
      history_hours: historyHours,
      name: `Live Location with ${historyHours}h History`,
      ...options
    });
  }

  /**
   * Create a password-protected live location share
   */
  static async createProtectedLiveLocation(dbManager, userId, options = {}) {
    return await this.createLiveLocation(dbManager, userId, {
      password: options.password || TestConstants.BCRYPT_HASHES.testpass123,
      name: 'Protected Live Location',
      ...options
    });
  }

  /**
   * Create an expired live location share
   */
  static async createExpiredLiveLocation(dbManager, userId, options = {}) {
    return await this.createLiveLocation(dbManager, userId, {
      expires_at: DateFactory.pastDate(7).toISOString(),
      name: 'Expired Live Location',
      ...options
    });
  }

  /**
   * Create a timeline share link
   */
  static async createTimeline(dbManager, userId, options = {}) {
    const dateRange = options.dateRange || DateFactory.ranges.active();

    const defaults = {
      user_id: userId,
      name: 'Timeline Share',
      share_type: 'TIMELINE',
      show_history: true,
      start_date: dateRange.startDate.toISOString(),
      end_date: dateRange.endDate.toISOString(),
      expires_at: dateRange.expiresAt.toISOString(),
      show_current_location: true,
      show_photos: false,
      password: options.password || null
    };

    return await this._insertShareLink(dbManager, { ...defaults, ...options });
  }

  /**
   * Create an active timeline share
   */
  static async createActiveTimeline(dbManager, userId, options = {}) {
    return await this.createTimeline(dbManager, userId, {
      dateRange: DateFactory.ranges.active(),
      name: 'Active Timeline',
      ...options
    });
  }

  /**
   * Create an upcoming timeline share
   */
  static async createUpcomingTimeline(dbManager, userId, options = {}) {
    return await this.createTimeline(dbManager, userId, {
      dateRange: DateFactory.ranges.upcoming(),
      name: 'Upcoming Timeline',
      ...options
    });
  }

  /**
   * Create an expired timeline share
   */
  static async createExpiredTimeline(dbManager, userId, options = {}) {
    return await this.createTimeline(dbManager, userId, {
      dateRange: DateFactory.ranges.completed(),
      name: 'Expired Timeline',
      ...options
    });
  }

  /**
   * Create a password-protected timeline share
   */
  static async createProtectedTimeline(dbManager, userId, options = {}) {
    return await this.createTimeline(dbManager, userId, {
      password: options.password || TestConstants.BCRYPT_HASHES.timelinepass,
      name: 'Protected Timeline',
      ...options
    });
  }

  /**
   * Create timeline with photos enabled
   */
  static async createTimelineWithPhotos(dbManager, userId, options = {}) {
    return await this.createTimeline(dbManager, userId, {
      show_photos: true,
      name: 'Timeline With Photos',
      ...options
    });
  }

  /**
   * Database query methods
   */
  static async getById(dbManager, linkId) {
    const result = await dbManager.client.query(
      'SELECT * FROM shared_link WHERE id = $1',
      [linkId]
    );
    return result.rows[0] || null;
  }

  static async getByUserId(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT * FROM shared_link WHERE user_id = $1 ORDER BY created_at DESC',
      [userId]
    );
    return result.rows;
  }

  static async countByUserId(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT COUNT(*) as count FROM shared_link WHERE user_id = $1',
      [userId]
    );
    return parseInt(result.rows[0].count);
  }

  static async getViewCount(dbManager, linkId) {
    const result = await dbManager.client.query(
      'SELECT view_count FROM shared_link WHERE id = $1',
      [linkId]
    );
    return result.rows[0] ? parseInt(result.rows[0].view_count) : 0;
  }

  static async deleteAll(dbManager, userId) {
    await dbManager.client.query(
      'DELETE FROM shared_link WHERE user_id = $1',
      [userId]
    );
  }

  /**
   * Get timeline status based on dates
   */
  static getTimelineStatus(startDate, endDate) {
    const now = new Date();
    const start = new Date(startDate);
    const end = new Date(endDate);

    if (now < start) return 'upcoming';
    if (now > end) return 'completed';
    return 'active';
  }
}
