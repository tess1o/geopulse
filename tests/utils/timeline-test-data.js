// Timeline test data helper functions
import {GeocodingFactory} from './geocoding-factory.js';

// Verifiable test data functions that return expected values for verification
export async function insertVerifiableStaysTestData(dbManager, userId) {
  // Use fixed date for predictable results
  const stayData = [
    { name: 'Home', duration: 7200, lat: 40.7128, lon: -74.0060 }, // 2 hours
    { name: 'Office', duration: 5400, lat: 40.7589, lon: -73.9851 }, // 1.5 hours
    { name: 'Gym', duration: 3600, lat: 40.7484, lon: -73.9857 } // 1 hour
  ];

  const results = [];

  for (let i = 0; i < stayData.length; i++) {
    const stay = stayData[i];
    // Use specific hours: 09:00, 10:00, 11:00 of Sept 21, 2025
    const stayTime = new Date(`2025-09-21T${(9 + i).toString().padStart(2, '0')}:00:00Z`);

    console.log(`Inserting stay ${i + 1} of ${stayData.length}... at ${stayTime}`);

    // Create reverse geocoding location
    const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
      dbManager,
      `POINT(${stay.lon} ${stay.lat})`,
      `${stay.name}, New York, NY`,
      'New York',
      'United States'
    );
    
    // Insert stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      stay.duration,
      stay.lon,
      stay.lat,
      stay.name,
      geocodingId
    ]);

    results.push({
      locationName: stay.name,
      duration: stay.duration,
      timestamp: stayTime
    });
  }

  return results;
}

export async function insertVerifiableTripsTestData(dbManager, userId) {
  // Use fixed date for predictable results
  const tripData = [
    { distance: 5500, duration: 1800, type: 'CAR' }, // 5.5km, 30 min, Car
    { distance: 2000, duration: 1200, type: 'WALK' }, // 2km, 20 min, Walk
    { distance: 12000, duration: 2400, type: 'CAR' } // 12km, 40 min, Car
  ];

  const results = [];

  for (let i = 0; i < tripData.length; i++) {
    const trip = tripData[i];
    // Use specific hours: 12:00, 13:00, 14:00 of Sept 21, 2025
    const tripTime = new Date(`2025-09-21T${(12 + i).toString().padStart(2, '0')}:00:00Z`);

    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      -74.0060 + (i * 0.001),
      40.7128 + (i * 0.001),
      -74.0100 + (i * 0.001),
      40.7200 + (i * 0.001),
      trip.distance,
      trip.type
    ]);

    results.push({
      distanceMeters: trip.distance,
      durationSeconds: trip.duration,
      movementType: trip.type,
      timestamp: tripTime
    });
  }

  return results;
}

export async function insertVerifiableDataGapsTestData(dbManager, userId) {
  // Use fixed date for predictable results
  const gapData = [
    { duration: 3600 } // 1 hour
  ];

  const results = [];

  for (let i = 0; i < gapData.length; i++) {
    const gap = gapData[i];
    // Fixed times: 06:00 and 09:00 Sept 21, 2025 (chronological order)
    const gapStartTime = new Date(`2025-09-21T${(6 + i * 3).toString().padStart(2, '0')}:00:00Z`);
    const gapEndTime = new Date(gapStartTime.getTime() + (gap.duration * 1000));
    
    await dbManager.client.query(`
      INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
      VALUES ($1, $2, $3, $4, NOW())
    `, [
      userId,
      gapStartTime,
      gapEndTime,
      gap.duration
    ]);

    results.push({
      durationSeconds: gap.duration,
      timestamp: gapStartTime
    });
  }

  return results.reverse(); // Reverse to match timeline display order (most recent first)
}

// Verifiable overnight test data functions
export async function insertVerifiableOvernightStaysTestData(dbManager, userId) {
  // Use fixed dates for predictable test results
  const yesterday = new Date('2025-09-20T18:00:00Z'); // Sept 20, 2025 at 6 PM UTC

  // Logical sequence: Hotel stay, then after checkout a trip to airport, then airport stay
  const stayData = [
    { name: 'Hotel Downtown', duration: 16 * 60 * 60, startOffset: 0 }, // 18:00 yesterday to 10:00 today
    { name: 'Airport Terminal', duration: 8 * 60 * 60, startOffset: 17 * 60 * 60 } // 11:00 today to 19:00 today (after 1h trip)
  ];

  const results = [];

  for (let i = 0; i < stayData.length; i++) {
    const stay = stayData[i];
    const stayStartTime = new Date(yesterday.getTime() + stay.startOffset * 1000); // Use specific offset
    
    // Create reverse geocoding location
    const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
      dbManager,
      'POINT(-74.0060 40.7128)',
      `${stay.name}, New York, NY`,
      'New York',
      'United States'
    );
    
    // Insert overnight stay (use same structure as working version)
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
    `, [
      userId,
      stayStartTime,
      stay.duration,
      -74.0060,
      40.7128,
      stay.name,
      geocodingId
    ]);

    results.push({
      locationName: stay.name,
      totalDuration: stay.duration,
      startTime: stayStartTime
    });
  }

  // Add a logical trip between hotel and airport (10:00 to 11:00 today)
  const tripStartTime = new Date(yesterday.getTime() + 16 * 60 * 60 * 1000); // After hotel checkout
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
  `, [
    userId,
    tripStartTime,
    3600, // 1 hour trip
    -74.0060, 40.7128, // Hotel location
    -73.7781, 40.6413, // JFK Airport location
    25000, // 25km distance
    'CAR'
  ]);

  return results; // Don't reverse, keep chronological order
}

export async function insertVerifiableOvernightTripsTestData(dbManager, userId) {
  // Use fixed dates for predictable test results  
  const yesterday = new Date('2025-09-20T18:00:00Z'); // Sept 20, 2025 at 6 PM UTC

  const results = [];

  // Realistic sequence: stay -> overnight trip -> stay
  
  // 1. Initial stay (6 PM yesterday for 2 hours)
  const initialStayTime = new Date(yesterday.getTime());
  const initialStayGeocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(-74.0060 40.7128)',
    'Starting Location, New York, NY',
    'New York',
    'United States'
  );

  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    initialStayTime.toISOString(),
    2 * 60 * 60, // 2 hours
    -74.0060,
    40.7128,
    'Starting Location',
    initialStayGeocodingId
  ]);

  // 2. Overnight trip (starts at 20:00 UTC on Sept 20 = 23:00 Kyiv, duration 10 hours, ends 06:00 UTC on Sept 21 = 09:00 Kyiv)  
  const tripStartTime = new Date('2025-09-20T20:00:00Z'); // 20:00 UTC on Sept 20 (23:00 Kyiv)
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
  `, [
    userId,
    tripStartTime.toISOString(),
    10 * 60 * 60, // 10 hours (spans midnight)
    -74.0060, 40.7128, // Starting in NYC
    -74.5000, 41.0000, // Ending in another location
    150000, // 150km distance
    'CAR'
  ]);

  results.push({
    distanceMeters: 150000,
    totalDuration: 10 * 60 * 60,
    movementType: 'CAR',
    startTime: tripStartTime
  });

  // 3. Final stay (08:00 UTC today for 4 hours)
  const finalStayTime = new Date(tripStartTime.getTime() + (10 * 60 * 60 * 1000)); // After trip ends
  const finalStayGeocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(-74.5000 41.0000)',
    'Destination Location, NY',
    'New York',
    'United States'
  );

  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    finalStayTime.toISOString(),
    4 * 60 * 60, // 4 hours
    -74.5000,
    41.0000,
    'Destination Location',
    finalStayGeocodingId
  ]);

  return results; // Return only the trip data for verification
}

export async function insertVerifiableOvernightDataGapsTestData(dbManager, userId) {
  // Use fixed dates for predictable test results
  const yesterday = new Date('2025-09-20T18:00:00Z'); // Sept 20, 2025 at 6 PM UTC

  const results = [];

  // Realistic sequence: stay -> overnight data gap -> stay
  
  // 1. Initial stay (6 PM yesterday for 3 hours)
  const initialStayTime = new Date(yesterday.getTime());
  const initialStayGeocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(-74.0060 40.7128)',
    'Home Location, New York, NY',
    'New York',
    'United States'
  );

  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    initialStayTime.toISOString(),
    3 * 60 * 60, // 3 hours (6 PM to 9 PM)
    -74.0060,
    40.7128,
    'Home Location',
    initialStayGeocodingId
  ]);

  // 2. Overnight data gap (9 PM yesterday to 8 AM today = 11 hours)
  // In Europe/Kyiv: 9 PM UTC = 12 AM Kyiv (Sept 21), 8 AM UTC = 11 AM Kyiv (Sept 21)
  const gapStartTime = new Date(yesterday.getTime() + (2 * 60 * 60 * 1000)); // 3 hours after 6 PM = 9 PM yesterday UTC
  const gapEndTime = new Date('2025-09-21T08:00:00Z'); // 8 AM next day UTC
  const durationSeconds = Math.floor((gapEndTime.getTime() - gapStartTime.getTime()) / 1000);
  
  await dbManager.client.query(`
    INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
    VALUES ($1, $2, $3, $4, NOW())
  `, [
    userId,
    gapStartTime.toISOString(),
    gapEndTime.toISOString(),
    durationSeconds
  ]);

  results.push({
    totalDuration: durationSeconds,
    startTime: gapStartTime,
    endTime: gapEndTime
  });

  // 3. Final stay (10 AM today for 4 hours)
  const finalStayTime = new Date('2025-09-21T10:00:00Z'); // 10 AM today UTC (2 hours after gap ends)
  const finalStayGeocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(-74.0100 40.7200)',
    'Work Location, New York, NY',
    'New York',
    'United States'
  );

  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    finalStayTime,
    4 * 60 * 60, // 4 hours
    -74.0100,
    40.7200,
    'Work Location',
    finalStayGeocodingId
  ]);

  return results; // Return only the data gap for verification
}

// Basic test data functions (for simple tests)
export async function insertRegularStaysTestData(dbManager, userId) {
  // Use fixed date for predictable results
  const locations = [
    { name: 'Home', lat: 40.7128, lon: -74.0060 },
    { name: 'Office', lat: 40.7589, lon: -73.9851 },
    { name: 'Gym', lat: 40.7484, lon: -73.9857 }
  ];

  for (let i = 0; i < locations.length; i++) {
    const location = locations[i];
    // Use specific hours: 15:00, 16:00, 17:00 of Sept 21, 2025
    const stayTime = new Date(`2025-09-21T${(15 + i).toString().padStart(2, '0')}:00:00Z`);
    
    // FIRST: Insert GPS points (REQUIRED for map to show)
    const gpsQuery = `
      INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at) 
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `;
    
    const gpsValues = [
      'test-device',
      userId,
      `POINT(${location.lon} ${location.lat})`,
      stayTime,
      10.0,
      100,
      0.0,
      20.0,
      'OVERLAND',
      stayTime
    ];

    await dbManager.client.query(gpsQuery, gpsValues);

    // Create reverse geocoding location
    const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
      dbManager,
      `POINT(${location.lon} ${location.lat})`,
      `${location.name}, New York, NY`,
      'New York',
      'United States'
    );
    
    // Insert timeline stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      3600, // 1 hour stay
      location.lon,
      location.lat,
      location.name,
      geocodingId
    ]);
  }
}

export async function insertRegularTripsTestData(dbManager, userId) {
  // Use fixed date for predictable results
  const trips = [
    { distance: 5000, duration: 1800, type: 'CAR' }, // 5km, 30 min
    { distance: 2000, duration: 1200, type: 'WALK' }, // 2km, 20 min
    { distance: 8000, duration: 2400, type: 'CAR' } // 8km, 40 min
  ];

  for (let i = 0; i < trips.length; i++) {
    const trip = trips[i];
    // Fixed times: 1, 2, 3 hours before midnight Sept 22, 2025 (23:00, 22:00, 21:00 Sept 21)
    const tripTime = new Date(`2025-09-21T${(23 - i).toString().padStart(2, '0')}:00:00Z`);
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      -74.0060 + (i * 0.001),
      40.7128 + (i * 0.001),
      -74.0100 + (i * 0.001),
      40.7200 + (i * 0.001),
      trip.distance,
      trip.type
    ]);
  }
}