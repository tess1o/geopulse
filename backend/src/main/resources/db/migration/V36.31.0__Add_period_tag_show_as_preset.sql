-- Allow timeline labels to remain visible on timelines while being hidden from DatePicker presets.
ALTER TABLE period_tags
ADD COLUMN show_as_preset BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN period_tags.show_as_preset IS
'Whether this timeline label appears in DatePicker preset dropdowns';
