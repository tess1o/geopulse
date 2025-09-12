// Map-specific test data helpers for e2e testing
// These helpers create timeline data with predictable coordinates for map testing

/**
 * Insert timeline data with known coordinates for map testing
 * Creates stays at specific, testable locations + location history for map display
 */
export async function insertMapTestStaysData(dbManager, userId) {
  const now = new Date();
  
  // Known coordinates for major cities that are easy to identify on map
  const testLocations = [
    { 
      name: 'NYC Times Square', 
      lat: 40.7589, 
      lon: -73.9851, 
      duration: 7200 // 2 hours
    },
    { 
      name: 'NYC Central Park', 
      lat: 40.7829, 
      lon: -73.9654, 
      duration: 5400 // 1.5 hours
    },
    { 
      name: 'NYC Brooklyn Bridge', 
      lat: 40.7061, 
      lon: -73.9969, 
      duration: 3600 // 1 hour
    }
  ];

  const results = [];

  for (let i = 0; i < testLocations.length; i++) {
    const location = testLocations[i];
    // Use specific hours: 8:00, 9:00, 10:00 of current day
    const stayTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 8 + i, 0, 0);

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
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      location.duration,
      location.lon,
      location.lat,
      location.name,
      geocodingId
    ]);

    results.push({
      locationName: location.name,
      duration: location.duration,
      latitude: location.lat,
      longitude: location.lon,
      timestamp: stayTime
    });
  }

  return results;
}

/**
 * Insert trip data with known coordinates for map testing
 * Creates trips between predictable locations
 */
export async function insertMapTestTripsData(dbManager, userId) {
  const now = new Date();
  
  // Known trip routes in NYC area
  const testTrips = [
    {
      startLat: 40.7589, startLon: -73.9851, // Times Square
      endLat: 40.7829, endLon: -73.9654,   // Central Park
      distance: 2500, // 2.5km
      duration: 1800, // 30 min
      type: 'CAR'
    },
    {
      startLat: 40.7829, startLon: -73.9654, // Central Park
      endLat: 40.7061, endLon: -73.9969,    // Brooklyn Bridge
      distance: 8000, // 8km
      duration: 2400, // 40 min
      type: 'CAR'
    },
    {
      startLat: 40.7061, startLon: -73.9969, // Brooklyn Bridge
      endLat: 40.7589, endLon: -73.9851,    // Back to Times Square
      distance: 3200, // 3.2km
      duration: 1200, // 20 min
      type: 'WALK'
    }
  ];

  const results = [];

  for (let i = 0; i < testTrips.length; i++) {
    const trip = testTrips[i];
    // Use specific hours: 11:00, 12:00, 13:00 of current day
    const tripTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 11 + i, 0, 0);
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      trip.startLon,
      trip.startLat,
      trip.endLon,
      trip.endLat,
      trip.distance,
      trip.type
    ]);

    results.push({
      distanceMeters: trip.distance,
      durationSeconds: trip.duration,
      movementType: trip.type,
      startLatitude: trip.startLat,
      startLongitude: trip.startLon,
      endLatitude: trip.endLat,
      endLongitude: trip.endLon,
      timestamp: tripTime
    });
  }

  return results;
}

/**
 * Insert data gaps with specific timing for map testing
 */
export async function insertMapTestDataGapsData(dbManager, userId) {
  const now = new Date();
  
  const gapData = [
    { duration: 3600 }, // 1 hour gap
    { duration: 1800 }  // 30 minute gap
  ];

  const results = [];

  for (let i = 0; i < gapData.length; i++) {
    const gap = gapData[i];
    // Use specific hours: 14:00, 15:00 of current day
    const gapStartTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 14 + i, 0, 0);
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
      startTime: gapStartTime,
      endTime: gapEndTime
    });
  }

  return results;
}

/**
 * Insert location path data with known coordinates for map testing
 * Creates a path that connects the stay locations
 */
export async function insertMapTestPathData(dbManager, userId) {
  const now = new Date();
  
  // Create a path that connects our test locations with specific times
  const pathPoints = [
    { lat: 40.7589, lon: -73.9851, hour: 7, minute: 0 }, // Times Square, 7:00
    { lat: 40.7650, lon: -73.9800, hour: 7, minute: 30 }, // Intermediate point, 7:30
    { lat: 40.7720, lon: -73.9750, hour: 8, minute: 0 }, // Intermediate point, 8:00
    { lat: 40.7829, lon: -73.9654, hour: 9, minute: 0 }, // Central Park, 9:00
    { lat: 40.7750, lon: -73.9800, hour: 10, minute: 0 }, // Intermediate point, 10:00
    { lat: 40.7400, lon: -73.9900, hour: 10, minute: 30 }, // Intermediate point, 10:30
    { lat: 40.7061, lon: -73.9969, hour: 11, minute: 0 }, // Brooklyn Bridge, 11:00
  ].map(point => ({
    ...point,
    timestamp: new Date(now.getFullYear(), now.getMonth(), now.getDate(), point.hour, point.minute, 0).getTime()
  }));

  for (const point of pathPoints) {
    const gpsQuery = `
      INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at) 
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `;
    
    const gpsValues = [
      'test-device',
      userId,
      `POINT(${point.lon} ${point.lat})`,
      new Date(point.timestamp),
      10.0,
      100,
      5.0,
      20.0,
      'TEST',
      new Date(point.timestamp)
    ];

    await dbManager.client.query(gpsQuery, gpsValues);
  }

  return pathPoints;
}

/**
 * Insert comprehensive map test data (stays + trips + gaps + path)
 * This creates BOTH timeline data AND location path data so map is visible
 */
export async function insertComprehensiveMapTestData(dbManager, userId) {
  // First insert location path data (required for map to show)
  const path = await insertMapTestPathData(dbManager, userId);
  
  // Then insert timeline data
  const stays = await insertMapTestStaysData(dbManager, userId);
  const trips = await insertMapTestTripsData(dbManager, userId);
  const gaps = await insertMapTestDataGapsData(dbManager, userId);

  return {
    stays,
    trips,
    gaps,
    path,
    totalItems: stays.length + trips.length + gaps.length
  };
}

/**
 * Insert map test data for a single location (for single-point map tests)
 * Includes both timeline data AND location path data
 */
export async function insertSingleLocationMapTestData(dbManager, userId) {
  const now = new Date();
  const location = {
    name: 'NYC Empire State Building',
    lat: 40.7484,
    lon: -73.9857,
    duration: 3600 // 1 hour
  };

  // Use specific hour: 16:00 of current day
  const stayTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 16, 0, 0);

  // First insert GPS points (REQUIRED for map to show)
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
    'TEST',
    stayTime
  ];

  await dbManager.client.query(gpsQuery, gpsValues);

  // Create reverse geocoding location
  const result = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
  
  const geocodingId = result.rows[0].id;
  
  // Insert single stay
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    stayTime,
    location.duration,
    location.lon,
    location.lat,
    location.name,
    geocodingId
  ]);

  return {
    locationName: location.name,
    duration: location.duration,
    latitude: location.lat,
    longitude: location.lon,
    timestamp: stayTime
  };
}

/**
 * Insert overnight map test data spanning multiple days
 */
export async function insertOvernightMapTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(20, 0, 0, 0); // 8 PM yesterday

  const location = {
    name: 'NYC Hotel Manhattan',
    lat: 40.7505,
    lon: -73.9934,
    duration: 12 * 60 * 60 // 12 hours
  };

  // Create reverse geocoding location
  const result = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
  
  const geocodingId = result.rows[0].id;
  
  // Insert overnight stay
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    yesterday,
    location.duration,
    location.lon,
    location.lat,
    location.name,
    geocodingId
  ]);

  return {
    locationName: location.name,
    totalDuration: location.duration,
    latitude: location.lat,
    longitude: location.lon,
    startTime: yesterday
  };
}

/**
 * Get expected map bounds for test data
 * Returns the expected bounds that the map should fit to
 */
export function getExpectedMapBounds() {
  return {
    north: 40.7829, // Central Park (northernmost)
    south: 40.7061, // Brooklyn Bridge (southernmost)  
    east: -73.9654, // Central Park (easternmost)
    west: -73.9969, // Brooklyn Bridge (westernmost)
    center: {
      lat: 40.7445, // Approximate center
      lon: -73.9811
    }
  };
}

/**
 * Get expected marker positions for test data
 */
export function getExpectedMarkerPositions() {
  return [
    { name: 'NYC Times Square', lat: 40.7589, lon: -73.9851 },
    { name: 'NYC Central Park', lat: 40.7829, lon: -73.9654 },
    { name: 'NYC Brooklyn Bridge', lat: 40.7061, lon: -73.9969 }
  ];
}