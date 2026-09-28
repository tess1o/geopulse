-- Allow a timeline share link to be restricted to a single Immich album
-- Null means no restriction (all photos in the date range, existing behavior)

ALTER TABLE shared_link ADD COLUMN immich_album_id VARCHAR(255);

COMMENT ON COLUMN shared_link.immich_album_id IS 'Immich album id to restrict shared photos to. Null means all photos are shown.';
