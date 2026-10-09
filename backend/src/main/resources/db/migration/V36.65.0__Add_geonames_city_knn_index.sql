-- Spatial index for nearest-city lookups on geonames_city.
--
-- Used to resolve the local timezone of stays, trips and GPS points (the "Location local time" display mode),
-- via KNN ordering: ORDER BY ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) <-> <point>.
-- Queries must use exactly this expression for the index to apply.

CREATE INDEX IF NOT EXISTS idx_geonames_city_geom_gist
    ON geonames_city USING gist ((ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)));
