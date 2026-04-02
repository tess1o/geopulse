-- Allow creating and storing unplanned trip plans without schedule dates.

ALTER TABLE trips
    ALTER COLUMN start_time DROP NOT NULL,
    ALTER COLUMN end_time DROP NOT NULL;

ALTER TABLE trips DROP CONSTRAINT IF EXISTS chk_trips_time_order;
ALTER TABLE trips DROP CONSTRAINT IF EXISTS chk_trips_status;
ALTER TABLE trips DROP CONSTRAINT IF EXISTS chk_trips_unplanned_integrity;

ALTER TABLE trips
    ADD CONSTRAINT chk_trips_time_order
        CHECK (
            (start_time IS NULL AND end_time IS NULL)
            OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
        );

ALTER TABLE trips
    ADD CONSTRAINT chk_trips_status
        CHECK (status IN ('UNPLANNED', 'UPCOMING', 'ACTIVE', 'COMPLETED', 'CANCELLED'));

ALTER TABLE trips
    ADD CONSTRAINT chk_trips_unplanned_integrity
        CHECK (
            (status = 'UNPLANNED' AND start_time IS NULL AND end_time IS NULL AND period_tag_id IS NULL)
            OR (status <> 'UNPLANNED' AND start_time IS NOT NULL AND end_time IS NOT NULL)
        );
