-- The legacy override layer was migrated to timeline_edits by V36.81.0 and repaired by V36.89.0. It was kept as a
-- read-only archive for one release; its re-apply services, creation paths, repositories and entities are gone.
DROP TABLE IF EXISTS timeline_trip_stay_split_overrides;
DROP TABLE IF EXISTS timeline_trip_movement_overrides;
DROP TABLE IF EXISTS timeline_data_gap_stay_overrides;
DROP TABLE IF EXISTS timeline_stay_name_overrides;
