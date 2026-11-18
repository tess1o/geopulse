-- A carved correction can contribute several surviving fragments.
ALTER TABLE timeline_correction_effects DROP CONSTRAINT timeline_correction_effects_pkey;
ALTER TABLE timeline_correction_effects ADD COLUMN id BIGSERIAL PRIMARY KEY;
ALTER TABLE timeline_correction_effects ADD CONSTRAINT ux_timeline_correction_effects_target UNIQUE (correction_id, target_type, target_id);
