-- Remove unused replacement-item branch from trip planner model.

-- Normalize legacy value before tightening constraint.
UPDATE trip_plan_items
SET manual_override_state = NULL
WHERE manual_override_state = 'REPLACED';

ALTER TABLE trip_plan_items
    DROP COLUMN IF EXISTS replacement_item_id;

ALTER TABLE trip_plan_items
    DROP CONSTRAINT IF EXISTS chk_trip_plan_items_override_state;

ALTER TABLE trip_plan_items
    ADD CONSTRAINT chk_trip_plan_items_override_state
        CHECK (manual_override_state IS NULL OR manual_override_state IN ('CONFIRMED', 'REJECTED'));
