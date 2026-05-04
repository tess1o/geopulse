-- Null oversized geocoding bounding boxes to prevent broad containment matching.
-- Threshold: 5000 km² = 5,000,000,000 m².
UPDATE reverse_geocoding_location
SET bounding_box = NULL
WHERE bounding_box IS NOT NULL
  AND ST_Area(bounding_box::geography) > 5000000000;

ANALYZE reverse_geocoding_location;
