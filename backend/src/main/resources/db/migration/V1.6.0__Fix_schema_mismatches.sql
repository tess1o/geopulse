-- Migration: Fix schema type mismatches
-- Version: V1.6.0
-- Description: Fixes data type mismatches between database schema and Hibernate entity definitions

-- Fix timeline_data_gaps.duration_seconds to support large durations (weeks/months)
-- Change from INTEGER to BIGINT to match Java long type and prevent overflow
ALTER TABLE timeline_data_gaps 
    ALTER COLUMN duration_seconds SET DATA TYPE BIGINT;

-- Add comments for clarity
COMMENT ON COLUMN timeline_data_gaps.duration_seconds IS 'Duration of the gap in seconds. BIGINT to support very long gaps (weeks/months).';