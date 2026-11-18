-- The application projection becomes an explicit effects projection: these rows record which materialized event a
-- correction produced or changed. They are rebuilt by every generation pass and are never a correctness input.
ALTER TABLE timeline_edit_applications RENAME TO timeline_correction_effects;
ALTER TABLE timeline_correction_effects RENAME COLUMN edit_id TO correction_id;
ALTER TABLE timeline_correction_effects RENAME CONSTRAINT timeline_edit_applications_pkey
    TO timeline_correction_effects_pkey;
ALTER TABLE timeline_correction_effects RENAME CONSTRAINT chk_timeline_edit_applications_type
    TO chk_timeline_correction_effects_type;
ALTER INDEX idx_timeline_edit_applications_target RENAME TO idx_timeline_correction_effects_target;

COMMENT ON TABLE timeline_correction_effects IS
    'Projection: the materialized event a correction produced or changed. Rebuilt every pass; history display only.';
