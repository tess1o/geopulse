-- A trip's movement type set by hand when the trip was split: both legs keep it, and undoing the split gives it back
-- to the whole trip.
ALTER TABLE timeline_trip_stay_split_overrides
    ADD COLUMN IF NOT EXISTS source_trip_movement_type VARCHAR(50);

-- Whether a split stay's name was typed for it. A stay named after its place takes the place's current name whenever
-- it is recreated, so renaming or deleting the place reaches it too.
ALTER TABLE timeline_trip_stay_split_overrides
    ADD COLUMN IF NOT EXISTS stay_location_name_custom BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE timeline_trip_stay_split_overrides o
SET stay_location_name_custom = FALSE
WHERE (o.favorite_id IS NOT NULL
           AND o.stay_location_name = (SELECT f.name FROM favorite_locations f WHERE f.id = o.favorite_id))
   OR (o.favorite_id IS NULL AND o.geocoding_id IS NOT NULL
           AND o.stay_location_name = (SELECT g.display_name FROM reverse_geocoding_location g WHERE g.id = o.geocoding_id));

-- A data gap turned into a stay at a chosen favorite or geocoded place keeps that place's coordinates, so the stay is
-- still recreated there when the place is deleted.
UPDATE timeline_data_gap_stay_overrides o
SET selected_latitude  = ST_Y(ST_Centroid(f.geometry)),
    selected_longitude = ST_X(ST_Centroid(f.geometry))
FROM favorite_locations f
WHERE o.selected_favorite_id = f.id
  AND o.selected_latitude IS NULL;

UPDATE timeline_data_gap_stay_overrides o
SET selected_latitude  = ST_Y(COALESCE(g.result_coordinates, g.request_coordinates)),
    selected_longitude = ST_X(COALESCE(g.result_coordinates, g.request_coordinates))
FROM reverse_geocoding_location g
WHERE o.selected_geocoding_id = g.id
  AND o.selected_latitude IS NULL;

-- A name the user gave one stay, such as a stay described in a timeline reconstruction. Anchored to the time of the
-- stay: after every regeneration the stay there is given the name again.
CREATE TABLE IF NOT EXISTS timeline_stay_name_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    stay_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    stay_end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    location_name VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_timeline_stay_name_overrides_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_timeline_stay_name_overrides_time_order
        CHECK (stay_end_time > stay_start_time)
);

CREATE INDEX IF NOT EXISTS idx_timeline_stay_name_overrides_user_start
    ON timeline_stay_name_overrides (user_id, stay_start_time);

-- One name per interval: a repeated reconstruction updates the name instead of adding another correction. Keep the
-- newest row if an earlier local run of this unreleased branch already created duplicates.
DELETE FROM timeline_stay_name_overrides older
USING timeline_stay_name_overrides newer
WHERE older.user_id = newer.user_id
  AND older.stay_start_time = newer.stay_start_time
  AND older.stay_end_time = newer.stay_end_time
  AND older.id < newer.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_timeline_stay_name_overrides_user_interval
    ON timeline_stay_name_overrides (user_id, stay_start_time, stay_end_time);
