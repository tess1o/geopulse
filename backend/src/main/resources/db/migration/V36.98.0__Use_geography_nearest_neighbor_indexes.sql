-- Geography KNN orders by spherical distance, including at high latitudes and across the date line.
CREATE INDEX IF NOT EXISTS idx_geonames_city_geog_gist
    ON geonames_city USING gist ((ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography));

DROP INDEX IF EXISTS idx_geonames_city_geom_gist;

-- User-scoped KNN and radius searches for the nearest-stay fallback.
CREATE INDEX IF NOT EXISTS idx_timeline_stays_user_location_geog
    ON timeline_stays USING gist (user_id, (location::geography));
