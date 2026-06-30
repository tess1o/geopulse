#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${OUT_DIR:-dist/water-dataset}"
ARTIFACT_NAME="${ARTIFACT_NAME:-geopulse-water-surfaces-v1.copy.gz}"
MANIFEST_NAME="${MANIFEST_NAME:-geopulse-water-surfaces-v1.manifest.json}"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-geopulse}"
PGUSER="${PGUSER:-postgres}"

command -v gzip >/dev/null || { echo "gzip is required" >&2; exit 1; }
command -v psql >/dev/null || { echo "psql is required" >&2; exit 1; }
command -v sha256sum >/dev/null || command -v shasum >/dev/null || {
  echo "sha256sum or shasum is required" >&2
  exit 1
}

mkdir -p "$OUT_DIR"
ARTIFACT_PATH="$OUT_DIR/$ARTIFACT_NAME"
MANIFEST_PATH="$OUT_DIR/$MANIFEST_NAME"

PSQL=(psql -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE")

echo "Exporting water surface artifact to $ARTIFACT_PATH..."
"${PSQL[@]}" -qAt -c "
COPY (
    SELECT
        source,
        source_id,
        COALESCE(name, ''),
        water_type,
        ST_AsEWKB(geom)
    FROM water_surface_polygons
    ORDER BY source, source_id, id
) TO STDOUT WITH (FORMAT binary)
" | gzip -c > "$ARTIFACT_PATH"

if command -v sha256sum >/dev/null; then
  SHA256="$(sha256sum "$ARTIFACT_PATH" | awk '{print $1}')"
else
  SHA256="$(shasum -a 256 "$ARTIFACT_PATH" | awk '{print $1}')"
fi

FEATURE_COUNT="$("${PSQL[@]}" -qAt -c "SELECT COUNT(*) FROM water_surface_polygons")"
SOURCE_COUNTS="$("${PSQL[@]}" -qAt -c "SELECT jsonb_object_agg(source, count ORDER BY source) FROM (SELECT source, COUNT(*) AS count FROM water_surface_polygons GROUP BY source) s")"
SIZE_BYTES="$(wc -c < "$ARTIFACT_PATH" | tr -d ' ')"

cat > "$MANIFEST_PATH" <<JSON
{
  "datasetVersion": "water_surfaces_v1",
  "artifact": "$ARTIFACT_NAME",
  "format": "postgres-binary-copy-gzip",
  "sha256": "$SHA256",
  "sizeBytes": $SIZE_BYTES,
  "featureCount": $FEATURE_COUNT,
  "sourceCounts": $SOURCE_COUNTS,
  "columns": ["source", "source_id", "name", "water_type", "geom_ewkb"],
  "attribution": "HydroLAKES, HydroSHEDS; Natural Earth",
  "license": "Mixed: HydroLAKES CC-BY 4.0; Natural Earth public domain"
}
JSON

echo "Wrote $ARTIFACT_PATH"
echo "Wrote $MANIFEST_PATH"
echo "SHA-256: $SHA256"
