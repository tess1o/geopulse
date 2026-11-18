-- A correction group is the unit of undo, restore and replacement, even for a single correction.
UPDATE timeline_edits SET group_id = gen_random_uuid() WHERE group_id IS NULL;
ALTER TABLE timeline_edits ALTER COLUMN group_id SET NOT NULL;
