-- V36.26.0 measured bounding-box size with ST_Area(bounding_box::geography).
-- That measure collapses to 0 for polygons spanning the full longitude range,
-- because -180 and +180 are the same meridian on the globe, while cache
-- containment matching uses planar ST_Contains on the lon/lat rectangle.
-- Provider country-level bboxes (for example Nominatim's United States bbox)
-- therefore bypassed the guard and matched every point in their latitude band.
--
-- This function measures the same lon/lat rectangle that containment matching
-- uses, with the exact spherical area of a meridian/parallel rectangle:
--   R^2 * dLon(rad) * (sin(latMax(rad)) - sin(latMin(rad)))
-- It handles full-longitude and antimeridian-spanning boxes and agrees with
-- ST_Contains semantics. Values are in square kilometres.
CREATE OR REPLACE FUNCTION geopulse_geocoding_bbox_area_km2(bbox geometry)
RETURNS double precision
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT 6371.0088 * 6371.0088
           * radians(ST_XMax(bbox) - ST_XMin(bbox))
           * (sin(radians(ST_YMax(bbox))) - sin(radians(ST_YMin(bbox))));
$$;

-- Re-run the oversized-bbox cleanup with the planar-consistent measure so rows
-- that V36.26.0 missed (geography area reported as 0) are dropped for all users.
UPDATE reverse_geocoding_location
SET bounding_box = NULL
WHERE bounding_box IS NOT NULL
  AND geopulse_geocoding_bbox_area_km2(bounding_box) > 5000;

ANALYZE reverse_geocoding_location;
