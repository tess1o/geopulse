CREATE
EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE timeline_stays
    ADD COLUMN location GEOMETRY(Point, 4326);
UPDATE timeline_stays
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326);
ALTER TABLE timeline_stays
    ALTER COLUMN location SET NOT NULL;
ALTER TABLE timeline_stays DROP COLUMN longitude, DROP COLUMN latitude;
CREATE INDEX IF NOT EXISTS idx_timeline_stays_location ON timeline_stays USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_timeline_stays_user_location ON timeline_stays USING GIST (user_id, location);
ALTER TABLE timeline_trips
    ADD COLUMN start_point GEOMETRY(Point, 4326);
ALTER TABLE timeline_trips
    ADD COLUMN end_point GEOMETRY(Point, 4326);
UPDATE timeline_trips
SET start_point = ST_SetSRID(ST_MakePoint(start_longitude, start_latitude), 4326),
    end_point   = ST_SetSRID(ST_MakePoint(end_longitude, end_latitude), 4326);
ALTER TABLE timeline_trips
    ALTER COLUMN start_point SET NOT NULL;
ALTER TABLE timeline_trips
    ALTER COLUMN end_point SET NOT NULL;
ALTER TABLE timeline_trips
DROP
COLUMN start_longitude,
    DROP
COLUMN start_latitude,
    DROP
COLUMN end_longitude,
    DROP
COLUMN end_latitude;
-- Create spatial indexes on the new start and end point columns
CREATE INDEX IF NOT EXISTS idx_timeline_trips_start_point ON timeline_trips USING GIST (start_point);
CREATE INDEX IF NOT EXISTS idx_timeline_trips_end_point ON timeline_trips USING GIST (end_point);
-- Create a composite index for user-specific queries on trip start points
CREATE INDEX IF NOT EXISTS idx_timeline_trips_user_start_point ON timeline_trips USING GIST (user_id, start_point);

-- GPS points user+spatial composite
CREATE INDEX IF NOT EXISTS idx_gps_points_user_coordinates
    ON gps_points USING GIST (user_id, coordinates);

-- Favorites user+spatial composite
CREATE INDEX IF NOT EXISTS idx_favorite_locations_user_geometry
    ON favorite_locations USING GIST (user_id, geometry);