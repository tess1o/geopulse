import {GeocodingFactory} from './geocoding-factory.js';

// Test data for the "Local time at each place" (location timezone) display mode.
//
// The backend resolves a location's timezone from the nearest row in geonames_city. The e2e stack runs with the
// GeoNames import disabled, so the table is empty and the feature reports itself unavailable. These helpers seed a
// handful of real cities under a reserved geonameid range, which global teardown removes again.

// Real GeoNames ids stay well below this range, so seeded rows never collide with an imported dataset.
export const E2E_GEONAMES_ID_MIN = 990000000;
export const E2E_GEONAMES_ID_MAX = 990999999;

export const LocationTimezoneCities = Object.freeze({
  NEW_YORK: { geonameId: 990000001, name: 'New York', countryCode: 'US', timezone: 'America/New_York', lat: 40.7128, lon: -74.0060 },
  TOKYO: { geonameId: 990000002, name: 'Tokyo', countryCode: 'JP', timezone: 'Asia/Tokyo', lat: 35.6895, lon: 139.6917 },
  HELSINKI: { geonameId: 990000003, name: 'Helsinki', countryCode: 'FI', timezone: 'Europe/Helsinki', lat: 60.1695, lon: 24.9354 },
  // Gives Europe/Kyiv a home country (UA), so "abroad in the same UTC offset" can be detected.
  LVIV: { geonameId: 990000004, name: 'Lviv', countryCode: 'UA', timezone: 'Europe/Kyiv', lat: 49.8397, lon: 24.0297 },
});

// Places near the seeded cities, plus one that is far from every city (beyond the 200 km default).
export const LocationTimezonePlaces = Object.freeze({
  NEW_YORK_OFFICE: { lat: 40.7589, lon: -73.9851, name: 'Midtown Office', city: 'New York', country: 'United States' },
  TOKYO_HOTEL: { lat: 35.6900, lon: 139.7000, name: 'Shinjuku Hotel', city: 'Tokyo', country: 'Japan' },
  TOKYO_HANEDA: { lat: 35.5494, lon: 139.7798 },
  HELSINKI_CAFE: { lat: 60.1699, lon: 24.9384, name: 'Esplanadi Cafe', city: 'Helsinki', country: 'Finland' },
  MID_ATLANTIC: { lat: 35.0, lon: -45.0, name: 'Atlantic Crossing', city: 'North Atlantic Ocean', country: 'International Waters' },
});

/** Idempotent and safe to run from several workers at once. */
export async function seedLocationTimezoneCities(dbManager) {
  for (const city of Object.values(LocationTimezoneCities)) {
    await dbManager.client.query(`
      INSERT INTO geonames_city (geonameid, name, asciiname, latitude, longitude, feature_class, feature_code,
                                 country_code, population, timezone)
      VALUES ($1, $2, $2, $3, $4, 'P', 'PPLA', $5, 1000000, $6)
      ON CONFLICT (geonameid) DO NOTHING
    `, [city.geonameId, city.name, city.lat, city.lon, city.countryCode, city.timezone]);
  }
}

export async function removeLocationTimezoneCities(dbManager) {
  await dbManager.client.query(
    'DELETE FROM geonames_city WHERE geonameid BETWEEN $1 AND $2',
    [E2E_GEONAMES_ID_MIN, E2E_GEONAMES_ID_MAX]
  );
}

/** 'profile' (default) or 'location'. */
export async function setTimeDisplayMode(dbManager, userId, mode) {
  await dbManager.client.query(
    "UPDATE users SET ui_preferences = ui_preferences || jsonb_build_object('timeDisplayMode', $1::text), updated_at = NOW() WHERE id = $2",
    [mode, userId]
  );
}

export async function insertStay(dbManager, userId, place, { timestamp, durationSeconds }) {
  const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    `POINT(${place.lon} ${place.lat})`,
    `${place.name}, ${place.city}`,
    place.city,
    place.country
  );

  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [userId, timestamp, durationSeconds, place.lon, place.lat, place.name, geocodingId]);
}

export async function insertTrip(dbManager, userId, from, to, { timestamp, durationSeconds, distanceMeters = 10000, movementType = 'CAR' }) {
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
  `, [userId, timestamp, durationSeconds, from.lon, from.lat, to.lon, to.lat, distanceMeters, movementType]);
}

export async function insertDataGap(dbManager, userId, { startTime, endTime }) {
  const durationSeconds = Math.round((new Date(endTime) - new Date(startTime)) / 1000);
  await dbManager.client.query(`
    INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
    VALUES ($1, $2, $3, $4, NOW())
  `, [userId, startTime, endTime, durationSeconds]);
}
