package org.github.tess1o.geopulse.shared.exportimport;

public final class NativeSqlImportTemplates {
    
    private NativeSqlImportTemplates() {
        // Utility class
    }
    
    public static final String REVERSE_GEOCODING_LOCATION_UPSERT = """
        INSERT INTO reverse_geocoding_location 
        (id, request_coordinates, result_coordinates, bounding_box, display_name, 
         provider_name, created_at, last_accessed_at, city, country) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            request_coordinates = EXCLUDED.request_coordinates,
            result_coordinates = EXCLUDED.result_coordinates,
            bounding_box = EXCLUDED.bounding_box,
            display_name = EXCLUDED.display_name,
            provider_name = EXCLUDED.provider_name,
            created_at = EXCLUDED.created_at,
            last_accessed_at = EXCLUDED.last_accessed_at,
            city = EXCLUDED.city,
            country = EXCLUDED.country
        """;
    
    public static final String FAVORITES_UPSERT = """
        INSERT INTO favorite_locations 
        (id, user_id, name, city, country, type, geometry) 
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            city = EXCLUDED.city,
            country = EXCLUDED.country,
            type = EXCLUDED.type,
            geometry = EXCLUDED.geometry
        """;
    
    public static final String TIMELINE_STAYS_UPSERT = """
        INSERT INTO timeline_stays 
        (id, user_id, timestamp, location, stay_duration, 
         location_name, location_source, favorite_id, geocoding_id, 
         created_at, last_updated) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET
            timestamp = EXCLUDED.timestamp,
            location = EXCLUDED.location,
            stay_duration = EXCLUDED.stay_duration,
            location_name = EXCLUDED.location_name,
            location_source = EXCLUDED.location_source,
            favorite_id = EXCLUDED.favorite_id,
            geocoding_id = EXCLUDED.geocoding_id,
            last_updated = NOW()
        """;
    
    public static final String TIMELINE_TRIPS_UPSERT = """
        INSERT INTO timeline_trips 
        (id, user_id, timestamp, start_point, end_point,
         distance_meters, trip_duration, movement_type, path, created_at, last_updated) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET
            timestamp = EXCLUDED.timestamp,
            start_point = EXCLUDED.start_point,
            end_point = EXCLUDED.end_point,
            distance_meters = EXCLUDED.distance_meters,
            trip_duration = EXCLUDED.trip_duration,
            movement_type = EXCLUDED.movement_type,
            path = EXCLUDED.path,
            last_updated = NOW()
        """;
    
    public static final String GPS_POINTS_UPSERT = """
        INSERT INTO gps_points
        (id, user_id, timestamp, coordinates, accuracy, altitude, velocity, battery,
         device_id, source_type, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            timestamp = EXCLUDED.timestamp,
            coordinates = EXCLUDED.coordinates,
            accuracy = EXCLUDED.accuracy,
            altitude = EXCLUDED.altitude,
            velocity = EXCLUDED.velocity,
            battery = EXCLUDED.battery,
            device_id = EXCLUDED.device_id,
            source_type = EXCLUDED.source_type,
            created_at = EXCLUDED.created_at
        """;

    /**
     * GPS points insert with automatic duplicate skipping.
     * Uses ON CONFLICT DO NOTHING to skip duplicates based on unique index.
     * Duplicates are defined as same user_id, timestamp, and coordinates.
     *
     * This is used for both CLEAR and MERGE import modes.
     */
    public static final String GPS_POINTS_INSERT_OR_UPDATE = """
        INSERT INTO gps_points
        (user_id, device_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
        VALUES (?::uuid, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, timestamp, coordinates) DO NOTHING
        """;
    
    public static final String GPS_SOURCE_CONFIG_UPSERT = """
        INSERT INTO gps_source_config 
        (id, user_id, username, source_type, active, connection_type) 
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            username = EXCLUDED.username,
            source_type = EXCLUDED.source_type,
            active = EXCLUDED.active,
            connection_type = EXCLUDED.connection_type
        """;
    
    public static final String TIMELINE_DATA_GAPS_UPSERT = """
        INSERT INTO timeline_data_gaps 
        (id, user_id, start_time, end_time, duration_seconds, created_at) 
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            start_time = EXCLUDED.start_time,
            end_time = EXCLUDED.end_time,
            duration_seconds = EXCLUDED.duration_seconds,
            created_at = EXCLUDED.created_at
        """;
}