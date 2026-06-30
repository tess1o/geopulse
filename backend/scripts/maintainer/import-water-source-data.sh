#!/usr/bin/env bash
set -euo pipefail

WORK_DIR="${TMPDIR:-/tmp}/geopulse-water-source-data"
HYDROLAKES_URL="${HYDROLAKES_URL:-https://data.hydrosheds.org/file/hydrolakes/HydroLAKES_polys_v10_shp.zip}"
NATURAL_EARTH_OCEAN_URL="${NATURAL_EARTH_OCEAN_URL:-https://naciscdn.org/naturalearth/10m/physical/ne_10m_ocean.zip}"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-geopulse}"
PGUSER="${PGUSER:-postgres}"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v unzip >/dev/null || { echo "unzip is required" >&2; exit 1; }
command -v shp2pgsql >/dev/null || { echo "shp2pgsql is required from PostGIS client tools" >&2; exit 1; }
command -v psql >/dev/null || { echo "psql is required" >&2; exit 1; }

PSQL=(psql -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE")

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

echo "Downloading HydroLAKES polygons..."
curl -fL "$HYDROLAKES_URL" -o "$WORK_DIR/hydrolakes.zip"
unzip -q "$WORK_DIR/hydrolakes.zip" -d "$WORK_DIR/hydrolakes"

HYDROLAKES_SHP="$(find "$WORK_DIR/hydrolakes" -name 'HydroLAKES_polys_v10.shp' -print -quit)"
if [[ -z "$HYDROLAKES_SHP" || ! -f "$HYDROLAKES_SHP" ]]; then
  echo "HydroLAKES shapefile not found in downloaded archive" >&2
  exit 1
fi

echo "Downloading Natural Earth ocean polygons..."
curl -fL "$NATURAL_EARTH_OCEAN_URL" -o "$WORK_DIR/ne_10m_ocean.zip"
unzip -q "$WORK_DIR/ne_10m_ocean.zip" -d "$WORK_DIR/ocean"

OCEAN_SHP="$(find "$WORK_DIR/ocean" -name 'ne_10m_ocean.shp' -print -quit)"
if [[ -z "$OCEAN_SHP" || ! -f "$OCEAN_SHP" ]]; then
  echo "Natural Earth ocean shapefile not found in downloaded archive" >&2
  exit 1
fi

echo "Resetting water surface tables..."
"${PSQL[@]}" <<'SQL'
TRUNCATE TABLE gps_point_environment;
TRUNCATE TABLE water_surface_polygons;
DELETE FROM geo_dataset_metadata WHERE dataset_name LIKE 'water_surface_polygons:%';
DROP TABLE IF EXISTS hydrolakes_raw;
DROP TABLE IF EXISTS natural_earth_ocean_raw;
SQL

echo "Loading HydroLAKES raw polygons..."
shp2pgsql -c -s 4326 "$HYDROLAKES_SHP" hydrolakes_raw | "${PSQL[@]}"

echo "Loading Natural Earth ocean raw polygons..."
shp2pgsql -c -s 4326 "$OCEAN_SHP" natural_earth_ocean_raw | "${PSQL[@]}"

echo "Importing normalized water surfaces..."
"${PSQL[@]}" <<'SQL'
INSERT INTO water_surface_polygons (source, source_id, name, water_type, geom)
SELECT
    'hydrolakes_v10',
    hylak_id::text,
    NULLIF(lake_name, ''),
    CASE lake_type
        WHEN 1 THEN 'lake'
        WHEN 2 THEN 'reservoir'
        ELSE 'water'
    END,
    ST_Multi((ST_Dump(ST_Subdivide(ST_MakeValid(geom), 256))).geom)::geometry(MultiPolygon, 4326)
FROM hydrolakes_raw
WHERE geom IS NOT NULL;

INSERT INTO water_surface_polygons (source, source_id, name, water_type, geom)
SELECT
    'naturalearth_10m_ocean',
    gid::text,
    NULL,
    'ocean',
    ST_Multi((ST_Dump(ST_Subdivide(ST_MakeValid(geom), 256))).geom)::geometry(MultiPolygon, 4326)
FROM natural_earth_ocean_raw
WHERE geom IS NOT NULL;

INSERT INTO geo_dataset_metadata (
    dataset_name,
    source_url,
    source_version,
    license,
    attribution,
    feature_count,
    imported_at
)
VALUES (
    'water_surface_polygons:hydrolakes_v10',
    'https://www.hydrosheds.org/products/hydrolakes',
    'hydrolakes_v10',
    'CC-BY 4.0',
    'HydroLAKES, HydroSHEDS',
    (SELECT COUNT(*) FROM water_surface_polygons WHERE source = 'hydrolakes_v10'),
    NOW()
);

INSERT INTO geo_dataset_metadata (
    dataset_name,
    source_url,
    source_version,
    license,
    attribution,
    feature_count,
    imported_at
)
VALUES (
    'water_surface_polygons:naturalearth_10m_ocean',
    'https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-ocean/',
    'naturalearth_10m_ocean',
    'Public domain',
    'Natural Earth',
    (SELECT COUNT(*) FROM water_surface_polygons WHERE source = 'naturalearth_10m_ocean'),
    NOW()
);

DROP TABLE hydrolakes_raw;
DROP TABLE natural_earth_ocean_raw;

ANALYZE water_surface_polygons;
ANALYZE gps_point_environment;
SQL

echo "Imported water surface polygons:"
"${PSQL[@]}" -c "SELECT source, COUNT(*) FROM water_surface_polygons GROUP BY source ORDER BY source;"
