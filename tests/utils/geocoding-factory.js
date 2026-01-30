/**
 * GeocodingFactory - Utility functions for managing reverse geocoding test data
 *
 * This utility handles the insertion of reverse_geocoding_location records while
 * properly handling the unique constraint idx_reverse_geocoding_unique_original_coords
 * which prevents duplicate "original" records (where user_id IS NULL) at the same coordinates.
 */

export class GeocodingFactory {
  /**
   * Insert or get existing geocoding location
   *
   * This function handles the unique constraint on reverse_geocoding_location by:
   * 1. First checking if a location with the same coordinates already exists (for user_id IS NULL)
   * 2. If it exists, return the existing ID
   * 3. If it doesn't exist, insert a new record
   *
   * @param {Object} dbManager - Database manager instance
   * @param {string} coords - Geometry string in WKT format (e.g., 'POINT(-74.0060 40.7128)')
   * @param {string} name - Display name for the location
   * @param {string} city - City name
   * @param {string} country - Country name
   * @param {number|null} userId - User ID (null for "original" locations)
   * @returns {Promise<number>} The geocoding location ID
   */
  static async insertOrGetGeocodingLocation(dbManager, coords, name, city, country, userId = null) {
    // First check if location exists (for originals where user_id IS NULL)
    if (userId === null) {
      const existingResult = await dbManager.client.query(`
        SELECT id FROM reverse_geocoding_location
        WHERE ST_Equals(request_coordinates, ST_GeomFromText($1, 4326)) AND user_id IS NULL
        LIMIT 1
      `, [coords]);

      if (existingResult.rows.length > 0) {
        // Location already exists, reuse it
        return existingResult.rows[0].id;
      }
    }

    // Insert new location
    const insertResult = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (
        id,
        request_coordinates,
        result_coordinates,
        display_name,
        provider_name,
        city,
        country,
        user_id,
        created_at,
        last_accessed_at
      )
      VALUES (
        nextval('reverse_geocoding_location_seq'),
        $1,
        $1,
        $2,
        'test',
        $3,
        $4,
        $5,
        NOW(),
        NOW()
      )
      RETURNING id
    `, [coords, name, city, country, userId]);

    return insertResult.rows[0].id;
  }

  /**
   * Insert multiple geocoding locations in batch
   *
   * @param {Object} dbManager - Database manager instance
   * @param {Array<Object>} locations - Array of location objects with {coords, name, city, country}
   * @param {number|null} userId - User ID (null for "original" locations)
   * @returns {Promise<Array<number>>} Array of geocoding location IDs
   */
  static async insertOrGetGeocodingLocations(dbManager, locations, userId = null) {
    const geocodingIds = [];

    for (const location of locations) {
      const id = await GeocodingFactory.insertOrGetGeocodingLocation(
        dbManager,
        location.coords,
        location.name,
        location.city,
        location.country,
        userId
      );
      geocodingIds.push(id);
    }

    return geocodingIds;
  }

  /**
   * Common test locations for use across tests
   */
  static get commonLocations() {
    return {
      nyHome: {
        coords: 'POINT(-74.0060 40.7128)',
        name: 'Home, New York, NY',
        city: 'New York',
        country: 'United States'
      },
      nyOffice: {
        coords: 'POINT(-73.9851 40.7589)',
        name: 'Office, New York, NY',
        city: 'New York',
        country: 'United States'
      },
      parisCoffeeShop: {
        coords: 'POINT(2.3522 48.8566)',
        name: 'Coffee Shop, Paris, France',
        city: 'Paris',
        country: 'France'
      },
      nyGym: {
        coords: 'POINT(-73.9857 40.7484)',
        name: 'Gym, New York, NY',
        city: 'New York',
        country: 'United States'
      },
      nyRestaurant: {
        coords: 'POINT(-73.9776 40.7614)',
        name: 'Restaurant, New York, NY',
        city: 'New York',
        country: 'United States'
      },
      nyPark: {
        coords: 'POINT(-73.9442 40.8006)',
        name: 'Park, New York, NY',
        city: 'New York',
        country: 'United States'
      },
      airportTerminal: {
        coords: 'POINT(-73.7781 40.6413)',
        name: 'Airport Terminal',
        city: 'New York',
        country: 'United States'
      }
    };
  }

  /**
   * Get an array of common locations for testing
   *
   * @param {Array<string>} locationKeys - Array of location keys from commonLocations
   * @returns {Array<Object>} Array of location objects
   */
  static getCommonLocations(locationKeys) {
    return locationKeys.map(key => GeocodingFactory.commonLocations[key]);
  }
}
