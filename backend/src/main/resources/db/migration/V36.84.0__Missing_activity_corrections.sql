ALTER TABLE timeline_edits ADD COLUMN group_id UUID;
CREATE INDEX idx_timeline_edits_group ON timeline_edits(user_id, group_id);
ALTER TABLE timeline_trips ADD COLUMN manual_route JSONB;
