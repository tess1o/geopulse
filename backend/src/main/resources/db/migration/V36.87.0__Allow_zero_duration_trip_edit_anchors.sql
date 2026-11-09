ALTER TABLE timeline_edits DROP CONSTRAINT chk_timeline_edits_anchor;
ALTER TABLE timeline_edits ADD CONSTRAINT chk_timeline_edits_anchor CHECK (
    anchor_end > anchor_start OR (
        anchor_end = anchor_start AND kind IN ('SET_MOVEMENT_TYPE', 'SET_TIMES', 'REMOVE_EVENT')
    )
);
