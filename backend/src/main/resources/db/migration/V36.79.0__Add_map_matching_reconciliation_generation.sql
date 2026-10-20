-- Every enqueue bumps the generation, so a worker that claimed a reconciliation before a new range was queued (an
-- import or recalculation rewinding the cursor) cannot move the cursor forward again or mark that range complete.
ALTER TABLE map_matching_reconciliations
    ADD COLUMN IF NOT EXISTS generation BIGINT NOT NULL DEFAULT 0;
