export class GpsDataFactory {
  /**
   * Generate GPS test data with various patterns for comprehensive testing
   */
  static generateTestData(userId, deviceId = 'test-device', baseId = 90000) {
    const data = {
      // Data for August 10, 2025 - Mixed stationary and moving
      august10: this.generateAugust10Data(userId, deviceId, baseId),
      
      // Data for August 12, 2025 - Different day for filtering tests
      august12: this.generateAugust12Data(userId, deviceId, baseId + 100),
      
      // Data for August 15, 2025 - Another day for range filtering
      august15: this.generateAugust15Data(userId, deviceId, baseId + 200)
    };

    return {
      allPoints: [...data.august10, ...data.august12, ...data.august15],
      byDate: data,
      insertQueries: this.generateInsertQueries([...data.august10, ...data.august12, ...data.august15])
    };
  }

  /**
   * Generate GPS data for August 10, 2025 - Complex journey with stationary periods
   */
  static generateAugust10Data(userId, deviceId, baseId) {
    const points = [];
    let id = baseId;
    let battery = 100;

    // Morning stationary period (9:00-9:45) - 10 points at location A
    const locationA = { lat: 51.521713, lon: -0.126427 };
    for (let i = 0; i < 10; i++) {
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: locationA.lat + (Math.random() - 0.5) * 0.0001,
        lon: locationA.lon + (Math.random() - 0.5) * 0.0001,
        timestamp: `2025-08-10 09:${String(i * 5).padStart(2, '0')}:00.000000`,
        accuracy: 5 + Math.random() * 5,
        battery: battery--,
        velocity: Math.random() * 0.8,
        altitude: 20 + Math.random() * 10,
        sourceType: 'OWNTRACKS'
      }));
    }

    // Walking period (9:47-10:25) - 20 points from A to B
    const locationB = { lat: 51.520277, lon: -0.126094 };
    for (let i = 0; i < 20; i++) {
      const progress = i / 19;
      // Calculate timestamp properly to avoid invalid times
      const baseMinutes = 47;
      const totalMinutes = baseMinutes + i * 2;
      const hour = 9 + Math.floor(totalMinutes / 60);
      const minute = totalMinutes % 60;
      
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: locationA.lat + (locationB.lat - locationA.lat) * progress + (Math.random() - 0.5) * 0.00005,
        lon: locationA.lon + (locationB.lon - locationA.lon) * progress + (Math.random() - 0.5) * 0.00005,
        timestamp: `2025-08-10 ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00.000000`,
        accuracy: 6 + Math.random() * 2,
        battery: battery--,
        velocity: 4 + Math.random() * 2,
        altitude: 22 + Math.random() * 10,
        sourceType: 'OWNTRACKS'
      }));
    }

    // Stationary at B (10:29-10:57) - 8 points
    for (let i = 0; i < 8; i++) {
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: locationB.lat + (Math.random() - 0.5) * 0.0002,
        lon: locationB.lon + (Math.random() - 0.5) * 0.0002,
        timestamp: `2025-08-10 10:${String(29 + i * 4).padStart(2, '0')}:00.000000`,
        accuracy: 5 + Math.random() * 3,
        battery: battery--,
        velocity: Math.random() * 0.9,
        altitude: 18 + Math.random() * 8,
        sourceType: 'OWNTRACKS'
      }));
    }

    // Driving period (10:59-11:27) - 15 points from B to C (faster movement)
    const locationC = { lat: 51.507078, lon: -0.138365 };
    for (let i = 0; i < 15; i++) {
      const progress = i / 14;
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: locationB.lat + (locationC.lat - locationB.lat) * progress + (Math.random() - 0.5) * 0.0001,
        lon: locationB.lon + (locationC.lon - locationB.lon) * progress + (Math.random() - 0.5) * 0.0001,
        timestamp: `2025-08-10 11:${String(1 + i * 2).padStart(2, '0')}:00.000000`,
        accuracy: 8 + Math.random() * 2,
        battery: battery--,
        velocity: 30 + Math.random() * 20,
        altitude: 12 + Math.random() * 10,
        sourceType: 'OWNTRACKS'
      }));
    }

    // Final stationary at C (11:29-11:53) - 5 points
    for (let i = 0; i < 5; i++) {
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: locationC.lat + (Math.random() - 0.5) * 0.0003,
        lon: locationC.lon + (Math.random() - 0.5) * 0.0003,
        timestamp: `2025-08-10 11:${String(29 + i * 6).padStart(2, '0')}:00.000000`,
        accuracy: 6 + Math.random() * 4,
        battery: battery--,
        velocity: Math.random() * 1.0,
        altitude: 11 + Math.random() * 4,
        sourceType: 'OWNTRACKS'
      }));
    }

    return points;
  }

  /**
   * Generate GPS data for August 12, 2025 - Different day for filtering tests
   */
  static generateAugust12Data(userId, deviceId, baseId) {
    const points = [];
    let id = baseId;
    let battery = 85;

    // Simple journey - 15 points throughout the day
    const startLocation = { lat: 51.515000, lon: -0.130000 };
    const endLocation = { lat: 51.510000, lon: -0.135000 };

    for (let i = 0; i < 15; i++) {
      const progress = i / 14;
      const hour = 8 + Math.floor(i / 2);
      const minute = (i % 2) * 30;
      
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: startLocation.lat + (endLocation.lat - startLocation.lat) * progress + (Math.random() - 0.5) * 0.0002,
        lon: startLocation.lon + (endLocation.lon - startLocation.lon) * progress + (Math.random() - 0.5) * 0.0002,
        timestamp: `2025-08-12 ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00.000000`,
        accuracy: 5 + Math.random() * 5,
        battery: battery--,
        velocity: 2 + Math.random() * 3,
        altitude: 15 + Math.random() * 10,
        sourceType: 'OVERLAND'
      }));
    }

    return points;
  }

  /**
   * Generate GPS data for August 15, 2025 - Another day for range filtering
   */
  static generateAugust15Data(userId, deviceId, baseId) {
    const points = [];
    let id = baseId;
    let battery = 95;

    // Evening data - 10 points
    const location = { lat: 51.520000, lon: -0.125000 };

    for (let i = 0; i < 10; i++) {
      points.push(this.createGpsPoint({
        id: id++,
        userId,
        deviceId,
        lat: location.lat + (Math.random() - 0.5) * 0.001,
        lon: location.lon + (Math.random() - 0.5) * 0.001,
        timestamp: `2025-08-15 18:${String(i * 5).padStart(2, '0')}:00.000000`,
        accuracy: 4 + Math.random() * 3,
        battery: battery--,
        velocity: Math.random() * 1.5,
        altitude: 25 + Math.random() * 5,
        sourceType: 'DAWARICH'
      }));
    }

    return points;
  }

  /**
   * Create a single GPS point with consistent format
   */
  static createGpsPoint({
    id,
    userId,
    deviceId,
    lat,
    lon,
    timestamp,
    accuracy,
    battery,
    velocity,
    altitude,
    sourceType,
    createdAt = null
  }) {
    return {
      id,
      device_id: deviceId,
      user_id: userId,
      coordinates: `POINT (${lon} ${lat})`,
      timestamp,
      accuracy: Math.round(accuracy * 10) / 10,
      battery: Math.max(1, Math.min(100, Math.round(battery))),
      velocity: Math.round(velocity * 100) / 100,
      altitude: Math.round(altitude * 10) / 10,
      source_type: sourceType,
      created_at: createdAt || timestamp
    };
  }

  /**
   * Generate insert queries for database seeding
   */
  static generateInsertQueries(points) {
    return points.map(point => 
      `INSERT INTO public.gps_points (id, device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at) VALUES (${point.id}, '${point.device_id}', '${point.user_id}', '${point.coordinates}', '${point.timestamp}', ${point.accuracy}, ${point.battery}, ${point.velocity}, ${point.altitude}, '${point.source_type}', '${point.created_at}');`
    );
  }

  /**
   * Create GPS data for multiple users (for isolation testing)
   */
  static generateMultiUserData(users) {
    const allData = {};
    
    users.forEach((user, index) => {
      allData[user.id] = this.generateTestData(
        user.id, 
        `${user.email.split('@')[0]}-device`,
        90000 + (index * 1000)
      );
    });

    return allData;
  }

  /**
   * Database helper methods
   */
  static async insertGpsData(dbManager, points) {
    for (const point of points) {
      const query = `
        INSERT INTO gps_points (id, device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at) 
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
      `;
      
      const values = [
        point.id,
        point.device_id,
        point.user_id,
        point.coordinates,
        point.timestamp,
        point.accuracy,
        point.battery,
        point.velocity,
        point.altitude,
        point.source_type,
        point.created_at
      ];

      await dbManager.client.query(query, values);
    }
  }

  /**
   * Get GPS points count for a user within date range
   */
  static async getGpsPointsCount(dbManager, userId, startDate = null, endDate = null) {
    let query = `SELECT COUNT(*) as count FROM gps_points WHERE user_id = $1`;
    const params = [userId];

    if (startDate) {
      params.push(startDate);
      query += ` AND timestamp >= $${params.length}`;
    }

    if (endDate) {
      params.push(endDate);
      query += ` AND timestamp <= $${params.length}`;
    }

    const result = await dbManager.client.query(query, params);
    return parseInt(result.rows[0].count);
  }

  /**
   * Get GPS points for a user within date range
   */
  static async getGpsPoints(dbManager, userId, startDate = null, endDate = null, limit = null) {
    let query = `
      SELECT id, device_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type
      FROM gps_points
      WHERE user_id = $1
    `;
    const params = [userId];

    if (startDate) {
      params.push(startDate);
      query += ` AND timestamp >= $${params.length}`;
    }

    if (endDate) {
      params.push(endDate);
      query += ` AND timestamp <= $${params.length}`;
    }

    query += ` ORDER BY timestamp DESC`;

    if (limit) {
      params.push(limit);
      query += ` LIMIT $${params.length}`;
    }

    const result = await dbManager.client.query(query, params);
    return result.rows;
  }

  /**
   * Simple GPS point creation for share link tests
   * Consolidates methods from SharedLocationPage
   */

  /**
   * Insert a single GPS point (simple version)
   */
  static async insertGpsPoint(dbManager, pointData) {
    const gpsQuery = `
      INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      RETURNING *
    `;

    const gpsValues = [
      pointData.device_id || 'test-device',
      pointData.user_id,
      `POINT(${pointData.longitude} ${pointData.latitude})`, // PostGIS POINT format: lon, lat
      pointData.timestamp,
      pointData.accuracy || 10.0,
      pointData.battery || 100,
      pointData.velocity || 0.0,
      pointData.altitude || 20.0,
      pointData.source_type || 'OWNTRACKS',
      pointData.created_at || pointData.timestamp
    ];

    const result = await dbManager.client.query(gpsQuery, gpsValues);
    return result.rows[0];
  }

  /**
   * Insert multiple GPS points (simple array)
   */
  static async insertMultipleGpsPoints(dbManager, points) {
    const results = [];
    for (const point of points) {
      const result = await this.insertGpsPoint(dbManager, point);
      results.push(result);
    }
    return results;
  }

  /**
   * Create GPS points for a user (common pattern in share link tests)
   * Creates points spread over time around London
   * @deprecated Use createGpsPointsForUser instead
   */
  static async createGpsPointsForUser(dbManager, userId, count = 5) {
    const now = new Date();
    const points = [];

    for (let i = 0; i < count; i++) {
      const timestamp = new Date(now.getTime() - (count - i) * 60000); // 1 minute apart
      const point = {
        user_id: userId,
        device_id: 'test-device',
        latitude: 51.5074 + (Math.random() - 0.5) * 0.01, // London area
        longitude: -0.1278 + (Math.random() - 0.5) * 0.01,
        timestamp: timestamp.toISOString(),
        accuracy: 10.0,
        battery: 100 - i,
        velocity: i === count - 1 ? 0.0 : 5.0, // Last point is stationary
        altitude: 20.0,
        source_type: 'OWNTRACKS',
        created_at: timestamp.toISOString()
      };
      points.push(point);
    }

    return await this.insertMultipleGpsPoints(dbManager, points);
  }
}