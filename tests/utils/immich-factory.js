import { TestConfig } from '../config/test-config.js';

/**
 * Seeds users for the fake Immich server from demo-integrations (geopulse-demo-integrations-e2e).
 *
 * The fake server has no data of its own. It resolves a user by the API key stored in
 * users.immich_preferences and builds their photo library from their timeline_stays:
 * - photos are only taken between 07:30 and 23:00 in the user's timezone (users.timezone);
 * - a stay of at least 10 minutes gets photos with a fixed chance per place kind, about 55% for a
 *   place whose name contains "coffee". Which stays get photos depends on a hash of the user ID,
 *   so a test seeds a dozen stays and reads back which photos exist instead of guessing.
 * - the library is built on the first request for that user, so seed every stay before the
 *   first Immich request. GeoPulse also caches search results for five minutes per user.
 *
 * The timeline map only renders when the range has GPS points, so every seeded stay and trip also
 * gets the GPS points it would have been built from.
 *
 * The backend's RealTimeTimelineJob runs every five minutes and, for every idle user, deletes the
 * latest stay (and everything after it) before regenerating from the GPS points after it.
 * insertPhotoStays ends with a photo-less night stay, without GPS points, after the tested range,
 * so the job deletes that stay instead of a photo stay and has nothing to regenerate.
 */

// URL the GeoPulse backend uses to reach the fake server (compose service name, not localhost).
export const IMMICH_E2E_SERVER_URL = process.env.IMMICH_E2E_SERVER_URL || 'http://geopulse-demo-integrations-e2e:2283';

// A port nothing listens on, for the "Immich is down" cases.
export const IMMICH_E2E_UNREACHABLE_URL = process.env.IMMICH_E2E_UNREACHABLE_URL || 'http://geopulse-demo-integrations-e2e:2999';

// Inside the fake Kyiv region, so its Kyiv photos and generic coffee shots are used.
export const KYIV_COFFEE_PLACE = Object.freeze({
  name: 'Kyiv Coffee Corner',
  latitude: 50.4501,
  longitude: 30.5234,
  city: 'Kyiv',
  country: 'Ukraine'
});

// Twelve consecutive days, each a two-hour daytime stay followed by a 30-minute walk. With a 55%
// chance per stay the chance that none of them gets a photo is below 0.01%.
export const PHOTO_DAYS = Object.freeze({
  first: '2025-03-03',
  last: '2025-03-14',
  count: 12
});

// Latest stay of every seeded user: outside every tested range and at night, so it has no photos.
const REALTIME_JOB_BUFFER_STAY = Object.freeze({
  timestamp: '2025-04-07T01:00:00Z',
  durationSeconds: 60 * 60,
  name: 'Night Stay'
});

const DAY_MS = 24 * 60 * 60 * 1000;
const MINUTE_MS = 60 * 1000;
// The walk after each stay ends about 1.4 km north-east of the place.
const WALK_SECONDS = 30 * 60;
const WALK_METERS = 1400;
const WALK_OFFSET = Object.freeze({ latitude: 0.008, longitude: 0.015 });

const immichEndpoint = (path) => `${TestConfig.API_BASE_URL}/api/v1/integrations/immich${path}`;

const unwrap = (payload) => payload?.data ?? payload;

export class ImmichFactory {
  static apiKeyFor(userId) {
    return `e2e-immich-${userId}`;
  }

  /**
   * Store Immich settings straight in the database. Without a serverUrl it only registers the
   * API key, which is how the fake server knows the key before the user saves it in the UI.
   */
  static async configure(dbManager, userId, {
    serverUrl = IMMICH_E2E_SERVER_URL,
    apiKey = this.apiKeyFor(userId),
    enabled = true
  } = {}) {
    const preferences = { apiKey, enabled };
    if (serverUrl) {
      preferences.serverUrl = serverUrl;
    }

    await dbManager.client.query(
      "UPDATE users SET immich_preferences = $2::jsonb, timezone = 'UTC' WHERE id = $1",
      [userId, JSON.stringify(preferences)]
    );
    return preferences;
  }

  static async getPreferences(dbManager, userId) {
    const result = await dbManager.client.query('SELECT immich_preferences FROM users WHERE id = $1', [userId]);
    return result.rows[0]?.immich_preferences || null;
  }

  static async insertStay(dbManager, userId, {
    timestamp,
    durationSeconds,
    place = KYIV_COFFEE_PLACE,
    name = place.name,
    favoriteId = null,
    geocodingId = null
  }) {
    const result = await dbManager.client.query(`
      INSERT INTO timeline_stays (
        user_id, favorite_id, geocoding_id, timestamp, stay_duration, location, location_name,
        location_source, created_at, last_updated
      )
      VALUES ($1, $2, $3, $4, $5, ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
      RETURNING id
    `, [
      userId,
      favoriteId,
      geocodingId,
      timestamp,
      durationSeconds,
      place.longitude,
      place.latitude,
      name,
      favoriteId ? 'FAVORITE' : 'GEOCODING'
    ]);
    return result.rows[0].id;
  }

  static async insertTrip(dbManager, userId, { timestamp, durationSeconds, from, to, distanceMeters, movementType = 'WALK' }) {
    await dbManager.client.query(`
      INSERT INTO timeline_trips (
        user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type,
        created_at, last_updated
      )
      VALUES (
        $1, $2, $3,
        ST_SetSRID(ST_MakePoint($4, $5), 4326),
        ST_SetSRID(ST_MakePoint($6, $7), 4326),
        $8, $9, NOW(), NOW()
      )
    `, [
      userId, timestamp, durationSeconds,
      from.longitude, from.latitude,
      to.longitude, to.latitude,
      distanceMeters, movementType
    ]);
  }

  /**
   * @param {Array<{timestamp: Date, latitude: number, longitude: number, velocity?: number}>} points
   */
  static async insertGpsPoints(dbManager, userId, points) {
    const values = [];
    const rows = points.map((point, index) => {
      const base = index * 5;
      values.push(point.timestamp, point.longitude, point.latitude, point.velocity ?? 0, userId);
      return `('immich-e2e-device', $${base + 5}, ST_SetSRID(ST_MakePoint($${base + 2}, $${base + 3}), 4326), ` +
        `$${base + 1}, 8.0, 90, $${base + 4}, 20.0, 'OVERLAND', $${base + 1})`;
    });

    await dbManager.client.query(`
      INSERT INTO gps_points (
        device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at
      )
      VALUES ${rows.join(', ')}
    `, values);
  }

  /**
   * Every day of PHOTO_DAYS: a stay at the place from startHourUtc, then a walk away from it, with
   * GPS points for both. Ends with the night stay the real-time timeline job may delete.
   * Pass favoriteId or geocodingId to link the stays to a place (place, city and country pages).
   * @returns {Promise<Array<{id: number, timestamp: Date, durationSeconds: number}>>} the photo stays
   */
  static async insertPhotoStays(dbManager, userId, {
    place = KYIV_COFFEE_PLACE,
    firstDay = PHOTO_DAYS.first,
    days = PHOTO_DAYS.count,
    startHourUtc = 10,
    durationSeconds = 2 * 60 * 60,
    favoriteId = null,
    geocodingId = null
  } = {}) {
    const stays = [];
    const gpsPoints = [];
    const firstStart = new Date(`${firstDay}T${String(startHourUtc).padStart(2, '0')}:00:00Z`);
    const walkEnd = {
      latitude: place.latitude + WALK_OFFSET.latitude,
      longitude: place.longitude + WALK_OFFSET.longitude
    };

    for (let day = 0; day < days; day += 1) {
      const timestamp = new Date(firstStart.getTime() + day * DAY_MS);
      const id = await this.insertStay(dbManager, userId, {
        timestamp, durationSeconds, place, favoriteId, geocodingId
      });
      stays.push({ id, timestamp, durationSeconds });

      // Standing still for the whole stay, one point every 20 minutes.
      for (let minute = 0; minute * 60 < durationSeconds; minute += 20) {
        gpsPoints.push({
          timestamp: new Date(timestamp.getTime() + minute * MINUTE_MS),
          latitude: place.latitude,
          longitude: place.longitude
        });
      }

      const walkStart = new Date(timestamp.getTime() + durationSeconds * 1000);
      await this.insertTrip(dbManager, userId, {
        timestamp: walkStart,
        durationSeconds: WALK_SECONDS,
        from: place,
        to: walkEnd,
        distanceMeters: WALK_METERS
      });

      // Walking pace along a straight line, one point every 5 minutes.
      const steps = WALK_SECONDS / 300;
      for (let step = 0; step <= steps; step += 1) {
        const fraction = step / steps;
        gpsPoints.push({
          timestamp: new Date(walkStart.getTime() + step * 5 * MINUTE_MS),
          latitude: place.latitude + (walkEnd.latitude - place.latitude) * fraction,
          longitude: place.longitude + (walkEnd.longitude - place.longitude) * fraction,
          velocity: WALK_METERS / WALK_SECONDS
        });
      }
    }

    await this.insertGpsPoints(dbManager, userId, gpsPoints);

    await this.insertStay(dbManager, userId, {
      timestamp: new Date(REALTIME_JOB_BUFFER_STAY.timestamp),
      durationSeconds: REALTIME_JOB_BUFFER_STAY.durationSeconds,
      place,
      name: REALTIME_JOB_BUFFER_STAY.name
    });

    return stays;
  }

  /**
   * Search photos through the GeoPulse API as the signed-in user (the page's cookies).
   * Fails with a pointer to the compose service when the fake server is not reachable.
   */
  static async requestPhotoSearch(page, { from, to }) {
    return page.request.get(immichEndpoint('/photos/search'), { params: { from, to } });
  }

  static async searchPhotos(page, { from, to }) {
    const response = await this.requestPhotoSearch(page, { from, to });
    if (!response.ok()) {
      throw new Error(
        `Immich photo search failed with ${response.status()}: ${await response.text()}. ` +
        `Is the geopulse-demo-integrations-e2e service from tests/docker-compose.e2e.yml running?`
      );
    }
    return unwrap(await response.json())?.photos || [];
  }

  /**
   * The photos the fake server placed in the PHOTO_DAYS stays. Throws when there are none, so a
   * test never passes on an empty library.
   */
  static async expectSeededPhotos(page, { firstDay = PHOTO_DAYS.first, lastDay = PHOTO_DAYS.last } = {}) {
    const photos = await this.searchPhotos(page, {
      from: `${firstDay}T00:00:00Z`,
      to: `${lastDay}T23:59:59Z`
    });
    if (photos.length === 0) {
      throw new Error('The fake Immich server placed no photos in the seeded stays');
    }
    return photos;
  }

  static async fetchPhotoBytes(page, photoId, variant = 'thumbnail') {
    return page.request.get(immichEndpoint(`/photos/${encodeURIComponent(photoId)}/${variant}`));
  }
}
