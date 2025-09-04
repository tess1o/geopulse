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
    const stayTime = new Date(now.getTime() - ((i + 1) * 2 * 60 * 60 * 1000)); // 2, 4, 6 hours ago

    // Create reverse geocoding location
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${stay.lon} ${stay.lat})`, `${stay.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      stay.duration,
      stay.lat,
      stay.lon,
      stay.name,
      geocodingId
    ]);

    results.push({
      locationName: stay.name,
      duration: stay.duration,
      timestamp: stayTime
    });
  }

  return results.reverse(); // Reverse to match timeline display order (most recent first)
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
    const tripTime = new Date(now.getTime() - ((i + 1) * 60 * 60 * 1000)); // 1, 2, 3 hours ago
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      40.7128 + (i * 0.001),
      -74.0060 + (i * 0.001),
      40.7200 + (i * 0.001),
      -74.0100 + (i * 0.001),
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

  return results.reverse(); // Reverse to match timeline display order (most recent first)
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
  yesterday.setHours(18, 0, 0, 0); // Use same time as working insertOvernightStaysTestData

  const stayData = [
    { name: 'Hotel Downtown', duration: 16 * 60 * 60 }, // 16 hours in seconds (like the working version)
    { name: 'Airport Terminal', duration: 14 * 60 * 60 }  // 14 hours in seconds
  ];

  const results = [];

  for (let i = 0; i < stayData.length; i++) {
    const stay = stayData[i];
    const stayStartTime = new Date(yesterday.getTime() + (i * 60 * 60 * 1000)); // Start 1 hour apart
    
    // Create reverse geocoding location
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', $1, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`${stay.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert overnight stay (use same structure as working version)
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayStartTime,
      stay.duration,
      40.7128,
      -74.0060,
      stay.name,
      geocodingId
    ]);

    results.push({
      locationName: stay.name,
      totalDuration: stay.duration,
      startTime: stayStartTime
    });
  }

  return results; // Don't reverse, keep chronological order
}

export async function insertVerifiableOvernightTripsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0); // Use same time as working insertOvernightTripsTestData

  const tripData = [
    { distance: 150000, duration: 16*60*60, type: 'CAR' }, // 150km, 16 hours in seconds (like working version)
    { distance: 100000, duration: 14*60*60, type: 'CAR' }  // 100km, 14 hours in seconds
  ];

  const results = [];

  for (let i = 0; i < tripData.length; i++) {
    const trip = tripData[i];
    const tripStartTime = new Date(yesterday.getTime() + (i * 60 * 60 * 1000)); // Start 1 hour apart
    
    // Insert overnight trip (use same structure as working version)
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripStartTime,
      trip.duration,
      40.7128,
      -74.0060,
      41.0000,
      -74.5000,
      trip.distance,
      trip.type
    ]);

    results.push({
      distanceMeters: trip.distance,
      totalDuration: trip.duration,
      movementType: trip.type,
      startTime: tripStartTime
    });
  }

  return results; // Don't reverse, keep chronological order
}

export async function insertVerifiableOvernightDataGapsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0); // Use same time as working insertOvernightDataGapsTestData
  
  const today = new Date(now);
  today.setHours(6, 0, 0, 0); // 6 AM today (like working version)

  const gapData = [
    { startOffset: 0, endOffset: 0 }, // 18:00 yesterday to 06:00 today = 12 hours
    { startOffset: 1, endOffset: 1 }  // 19:00 yesterday to 07:00 today = 12 hours
  ];

  const results = [];

  for (let i = 0; i < gapData.length; i++) {
    const gap = gapData[i];
    const gapStartTime = new Date(yesterday.getTime() + (gap.startOffset * 60 * 60 * 1000));
    const gapEndTime = new Date(today.getTime() + (gap.endOffset * 60 * 60 * 1000));
    
    // Calculate actual duration in seconds
    const durationSeconds = Math.floor((gapEndTime.getTime() - gapStartTime.getTime()) / 1000);
    
    // Insert overnight data gap (use same structure as working version)
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
  }

  return results; // Don't reverse, keep chronological order
}

// Basic test data functions (for simple tests)
export async function insertRegularStaysTestData(dbManager, userId) {
  const now = new Date();
  const locations = [
    { name: 'Home', lat: 40.7128, lon: -74.0060 },
    { name: 'Office', lat: 40.7589, lon: -73.9851 },
    { name: 'Gym', lat: 40.7484, lon: -73.9857 }
  ];

  for (const location of locations) {
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    const stayTime = new Date(now.getTime() - (2 * 60 * 60 * 1000)); // 2 hours ago
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      3600, // 1 hour stay
      location.lat,
      location.lon,
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
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      40.7128 + (i * 0.001),
      -74.0060 + (i * 0.001),
      40.7200 + (i * 0.001),
      -74.0100 + (i * 0.001),
      trip.distance,
      trip.type
    ]);
  }
}