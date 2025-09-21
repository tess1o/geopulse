// Timeline test data helper functions

// Verifiable test data functions that return expected values for verification
export async function insertVerifiableStaysTestData(dbManager, userId) {
  const now = new Date();
  const stayData = [
    { name: 'Home', duration: 7200, lat: 40.7128, lon: -74.0060 }, // 2 hours
    { name: 'Office', duration: 5400, lat: 40.7589, lon: -73.9851 }, // 1.5 hours
    { name: 'Gym', duration: 3600, lat: 40.7484, lon: -73.9857 } // 1 hour
  ];

  const results = [];

  for (let i = 0; i < stayData.length; i++) {
    const stay = stayData[i];
    // Use specific hours: 9:00, 10:00, 11:00 of current day
    const stayTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 9 + i, 0, 0);

    // Create reverse geocoding location
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${stay.lon} ${stay.lat})`, `${stay.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
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
  const now = new Date();
  const tripData = [
    { distance: 5500, duration: 1800, type: 'CAR' }, // 5.5km, 30 min, Car
    { distance: 2000, duration: 1200, type: 'WALK' }, // 2km, 20 min, Walk
    { distance: 12000, duration: 2400, type: 'CAR' } // 12km, 40 min, Car
  ];

  const results = [];

  for (let i = 0; i < tripData.length; i++) {
    const trip = tripData[i];
    // Use specific hours: 12:00, 13:00, 14:00 of current day
    const tripTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12 + i, 0, 0);

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
  const now = new Date();
  const gapData = [
    { duration: 1800 }, // 30 minutes
    { duration: 3600 } // 1 hour
  ];

  const results = [];

  for (let i = 0; i < gapData.length; i++) {
    const gap = gapData[i];
    const gapStartTime = new Date(now.getTime() - ((i + 1) * 3 * 60 * 60 * 1000)); // 3, 6 hours ago
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
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setUTCHours(18, 0, 0, 0); // Hotel check-in at 6 PM UTC yesterday

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
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', $1, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`${stay.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
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
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setUTCHours(18, 0, 0, 0); // Start at 6 PM UTC yesterday

  const results = [];

  // Realistic sequence: stay -> overnight trip -> stay
  
  // 1. Initial stay (6 PM yesterday for 2 hours) 
  const initialStayTime = new Date(yesterday.getTime());
  const initialStayResult = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', 'Starting Location, New York, NY', 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `);
  
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    initialStayTime,
    2 * 60 * 60, // 2 hours
    -74.0060,
    40.7128,
    'Starting Location',
    initialStayResult.rows[0].id
  ]);

  // 2. Overnight trip (8 PM yesterday for 16 hours, ending at 12 PM today)
  const tripStartTime = new Date(yesterday.getTime() + (2 * 60 * 60 * 1000)); // 2 hours after initial stay
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
  `, [
    userId,
    tripStartTime,
    16 * 60 * 60, // 16 hours
    -74.0060, 40.7128, // Starting in NYC
    -74.5000, 41.0000, // Ending in another location
    150000, // 150km distance
    'CAR'
  ]);

  results.push({
    distanceMeters: 150000,
    totalDuration: 16 * 60 * 60,
    movementType: 'CAR',
    startTime: tripStartTime
  });

  // 3. Final stay (12 PM today for 4 hours)
  const finalStayTime = new Date(tripStartTime.getTime() + (16 * 60 * 60 * 1000)); // After trip ends
  const finalStayResult = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.5000 41.0000)', 'POINT(-74.5000 41.0000)', 'Destination Location, NY', 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `);
  
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    finalStayTime,
    4 * 60 * 60, // 4 hours
    -74.5000,
    41.0000,
    'Destination Location',
    finalStayResult.rows[0].id
  ]);

  return results; // Return only the trip data for verification
}

export async function insertVerifiableOvernightDataGapsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setUTCHours(18, 0, 0, 0); // Start at 6 PM UTC yesterday

  const results = [];

  // Realistic sequence: stay -> overnight data gap -> stay
  
  // 1. Initial stay (6 PM yesterday for 3 hours) 
  const initialStayTime = new Date(yesterday.getTime());
  const initialStayResult = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', 'Home Location, New York, NY', 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `);
  
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
  `, [
    userId,
    initialStayTime,
    3 * 60 * 60, // 3 hours (6 PM to 9 PM)
    -74.0060,
    40.7128,
    'Home Location',
    initialStayResult.rows[0].id
  ]);

  // 2. Overnight data gap (8 PM yesterday to 12 PM today = 16 hours)
  // In Europe/Kyiv: 8 PM UTC = 11 PM Kyiv (Sept 20), 12 PM UTC = 3 PM Kyiv (Sept 21)
  const gapStartTime = new Date(yesterday.getTime() + (2 * 60 * 60 * 1000)); // 2 hours after 6 PM = 8 PM yesterday UTC
  const gapEndTime = new Date(gapStartTime.getTime() + (16 * 60 * 60 * 1000)); // 16 hours later = 12 PM today UTC
  const durationSeconds = Math.floor((gapEndTime.getTime() - gapStartTime.getTime()) / 1000);
  
  await dbManager.client.query(`
    INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
    VALUES ($1, $2, $3, $4, NOW())
  `, [
    userId,
    gapStartTime,
    gapEndTime,
    durationSeconds
  ]);

  results.push({
    totalDuration: durationSeconds,
    startTime: gapStartTime,
    endTime: gapEndTime
  });

  // 3. Final stay (11 AM today for 4 hours)
  const finalStayTime = new Date(gapEndTime.getTime());
  const finalStayResult = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0100 40.7200)', 'POINT(-74.0100 40.7200)', 'Work Location, New York, NY', 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `);
  
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
    finalStayResult.rows[0].id
  ]);

  return results; // Return only the data gap for verification
}

// Basic test data functions (for simple tests)
export async function insertRegularStaysTestData(dbManager, userId) {
  const now = new Date();
  const locations = [
    { name: 'Home', lat: 40.7128, lon: -74.0060 },
    { name: 'Office', lat: 40.7589, lon: -73.9851 },
    { name: 'Gym', lat: 40.7484, lon: -73.9857 }
  ];

  for (let i = 0; i < locations.length; i++) {
    const location = locations[i];
    // Use specific hours: 15:00, 16:00, 17:00 of current day
    const stayTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 15 + i, 0, 0);
    
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
  const now = new Date();
  const trips = [
    { distance: 5000, duration: 1800, type: 'CAR' }, // 5km, 30 min
    { distance: 2000, duration: 1200, type: 'WALK' }, // 2km, 20 min
    { distance: 8000, duration: 2400, type: 'CAR' } // 8km, 40 min
  ];

  for (let i = 0; i < trips.length; i++) {
    const trip = trips[i];
    const tripTime = new Date(now.getTime() - ((i + 1) * 60 * 60 * 1000)); // Hours ago
    
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