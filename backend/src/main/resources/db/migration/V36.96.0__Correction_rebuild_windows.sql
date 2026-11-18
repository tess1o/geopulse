-- A correction changes no GPS point, so only the period it affects needs rebuilding. The pending request now keeps
-- the two kinds apart:
--   open_from - earliest point of an open-ended change (saved, late or deleted points, settings, imports): the
--               timeline is out of date from there to the end of the recording. NULL when only corrections wait.
--   window_to - latest end of the periods corrections affected; NULL when no correction waits.
-- rebuild_from stays the earliest of everything, so every reader that only needs "from where" is unchanged.
ALTER TABLE timeline_rebuild_requests ADD COLUMN IF NOT EXISTS open_from TIMESTAMPTZ;
ALTER TABLE timeline_rebuild_requests ADD COLUMN IF NOT EXISTS window_to TIMESTAMPTZ;

-- Every request written before this was open-ended.
UPDATE timeline_rebuild_requests SET open_from = rebuild_from WHERE open_from IS NULL AND window_to IS NULL;
