-- Rename the "Period Tag" feature to "Timeline Label" across the schema.
--
-- The concept has always been presented to users as a "Timeline Label" (see the
-- user guide and the sidebar), while every internal identifier used the older
-- "period tag" name. This migration aligns the database with the product term.
--
-- This is catalog-only metadata work: no rows are read or rewritten, so all ids,
-- colors, show_as_preset values and trip links are preserved as-is. No datafix
-- is required or performed.
--
-- NOTE: PostgreSQL keeps index, constraint and sequence names when a table is
-- renamed, so each dependent object is renamed explicitly below.

-- 1) Table. The trips FK follows the table OID automatically.
ALTER TABLE period_tags RENAME TO timeline_labels;

-- 2) Columns.
ALTER TABLE timeline_labels RENAME COLUMN tag_name TO name;
ALTER TABLE trips RENAME COLUMN period_tag_id TO timeline_label_id;

-- 3) Identity sequence (ALTER TABLE ... RENAME TO does not rename it).
ALTER SEQUENCE period_tags_id_seq RENAME TO timeline_labels_id_seq;

-- 4) Constraints on timeline_labels.
ALTER TABLE timeline_labels RENAME CONSTRAINT period_tags_pkey TO timeline_labels_pkey;
ALTER TABLE timeline_labels RENAME CONSTRAINT period_tags_user_id_fkey TO timeline_labels_user_id_fkey;
ALTER TABLE timeline_labels RENAME CONSTRAINT chk_period_tags_time_order TO chk_timeline_labels_time_order;

-- 5) Indexes on timeline_labels.
ALTER INDEX idx_period_tags_user_id RENAME TO idx_timeline_labels_user_id;
ALTER INDEX idx_period_tags_time_range RENAME TO idx_timeline_labels_time_range;
ALTER INDEX idx_period_tags_user_active RENAME TO idx_timeline_labels_user_active;
ALTER INDEX idx_period_tags_active RENAME TO idx_timeline_labels_active;

-- 6) trips: FK constraint and indexes.
ALTER TABLE trips RENAME CONSTRAINT trips_period_tag_id_fkey TO trips_timeline_label_id_fkey;
ALTER INDEX idx_trips_period_tag_id RENAME TO idx_trips_timeline_label_id;
ALTER INDEX uk_trips_period_tag_id RENAME TO uk_trips_timeline_label_id;

-- 7) chk_trips_unplanned_integrity is deliberately NOT touched. PostgreSQL stores
--    CHECK constraints over attribute numbers, so it already deparses with the new
--    column name. Dropping and recreating it risks rejecting existing UNPLANNED
--    rows or silently weakening the invariant.
