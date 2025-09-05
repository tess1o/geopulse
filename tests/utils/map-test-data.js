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
    const stayTime = new Date(now.getTime() - ((i + 1) * 3 * 60 * 60 * 1000)); // 3, 6, 9 hours ago

    // FIRST: Insert GPS points (REQUIRED for map to show)
    await dbManager.client.query(`
      INSERT INTO gps_points (user_id, coordinates, timestamp, accuracy, created_at)
      VALUES ($1, ST_Point($2, $3), $4, $5, NOW())
    `, [
      userId,
      location.lon, // longitude first for PostGIS
      location.lat, // latitude second
      stayTime,
      10.0 // 10 meter accuracy
    ]);

    // Create reverse geocoding location
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      location.duration,
      location.lat,
      location.lon,
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

  return results.reverse(); // Most recent first
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
    const tripTime = new Date(now.getTime() - ((i + 1) * 2 * 60 * 60 * 1000)); // 2, 4, 6 hours ago
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      trip.startLat,
      trip.startLon,
      trip.endLat,
      trip.endLon,
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

  return results.reverse(); // Most recent first
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
    const gapStartTime = new Date(now.getTime() - ((i + 1) * 4 * 60 * 60 * 1000)); // 4, 8 hours ago
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

  return results.reverse(); // Most recent first
}

/**
 * Insert location path data with known coordinates for map testing
 * Creates a path that connects the stay locations
 */
export async function insertMapTestPathData(dbManager, userId) {
  const now = new Date();
  
  // Create a path that connects our test locations
  const pathPoints = [
    { lat: 40.7589, lon: -73.9851, timestamp: now.getTime() - (8 * 60 * 60 * 1000) }, // Times Square, 8h ago
    { lat: 40.7650, lon: -73.9800, timestamp: now.getTime() - (7.5 * 60 * 60 * 1000) }, // Intermediate point
    { lat: 40.7720, lon: -73.9750, timestamp: now.getTime() - (7 * 60 * 60 * 1000) }, // Intermediate point
    { lat: 40.7829, lon: -73.9654, timestamp: now.getTime() - (6 * 60 * 60 * 1000) }, // Central Park, 6h ago
    { lat: 40.7750, lon: -73.9800, timestamp: now.getTime() - (4 * 60 * 60 * 1000) }, // Intermediate point
    { lat: 40.7400, lon: -73.9900, timestamp: now.getTime() - (3.5 * 60 * 60 * 1000) }, // Intermediate point
    { lat: 40.7061, lon: -73.9969, timestamp: now.getTime() - (3 * 60 * 60 * 1000) }, // Brooklyn Bridge, 3h ago
  ];

  for (const point of pathPoints) {
    await dbManager.client.query(`
      INSERT INTO gps_points (user_id, coordinates, timestamp, accuracy, created_at)
      VALUES ($1, ST_Point($2, $3), $4, $5, NOW())
    `, [
      userId,
      point.lon, // longitude first for PostGIS
      point.lat, // latitude second
      new Date(point.timestamp),
      10.0 // 10 meter accuracy
    ]);
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

  const stayTime = new Date(now.getTime() - (2 * 60 * 60 * 1000)); // 2 hours ago

  // First insert GPS points (REQUIRED for map to show)
  await dbManager.client.query(`
    INSERT INTO gps_points (user_id, coordinates, timestamp, accuracy, created_at)
    VALUES ($1, ST_Point($2, $3), $4, $5, NOW())
  `, [
    userId,
    location.lon, // longitude first for PostGIS
    location.lat, // latitude second
    stayTime,
    10.0 // 10 meter accuracy
  ]);

  // Create reverse geocoding location
  const result = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
  
  const geocodingId = result.rows[0].id;
  
  // Insert single stay
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
  `, [
    userId,
    stayTime,
    location.duration,
    location.lat,
    location.lon,
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
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
  `, [
    userId,
    yesterday,
    location.duration,
    location.lat,
    location.lon,
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