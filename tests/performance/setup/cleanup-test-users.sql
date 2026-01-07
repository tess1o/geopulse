-- GeoPulse Load Testing - Test Users Cleanup Script
--
-- Purpose: Removes all load test users and their associated data from the database
-- Usage: psql -h <production-host> -U <user> -d geopulse -f cleanup-test-users.sql
--
-- ⚠️  WARNING: This script will PERMANENTLY DELETE all test users and their data!
-- ⚠️  Only run this if you want to remove load test users from production.
-- ⚠️  This action CANNOT be undone!

-- Safety confirmation
DO $$
DECLARE
    test_user_count INT;
    gps_count INT;
    timeline_trips_count INT;
    timeline_stays_count INT;
    start_time TIMESTAMP;
    end_time TIMESTAMP;
BEGIN
    start_time := clock_timestamp();

    RAISE NOTICE '';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE '  GeoPulse Load Testing - Test Users Cleanup';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE '  ⚠️  WARNING: This will PERMANENTLY DELETE all test users!';
    RAISE NOTICE '───────────────────────────────────────────────────────────────';
    RAISE NOTICE '';

    -- Count existing test users and data
    SELECT COUNT(*) INTO test_user_count
    FROM users
    WHERE email LIKE 'loadtest-user-%@example.com';

    IF test_user_count = 0 THEN
        RAISE NOTICE '✓ No load test users found. Nothing to clean up.';
        RAISE NOTICE '';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO gps_count
    FROM gps_points
    WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');

    SELECT COUNT(*) INTO timeline_trips_count
    FROM timeline_trips
    WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');

    SELECT COUNT(*) INTO timeline_stays_count
    FROM timeline_stays
    WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');

    RAISE NOTICE 'Found % test users', test_user_count;
    RAISE NOTICE '  └─ % GPS points', gps_count;
    RAISE NOTICE '  └─ % timeline trips', timeline_trips_count;
    RAISE NOTICE '  └─ % timeline stays', timeline_stays_count;
    RAISE NOTICE '';
    RAISE NOTICE 'Proceeding with deletion...';
    RAISE NOTICE '';

    -- Delete GPS points
    RAISE NOTICE '→ Deleting GPS points...';
    DELETE FROM gps_points
    WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');
    RAISE NOTICE '  ✓ Deleted % GPS points', gps_count;

    -- Delete timeline trips (if exists)
    IF timeline_trips_count > 0 THEN
        RAISE NOTICE '→ Deleting timeline trips...';
        DELETE FROM timeline_trips
        WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');
        RAISE NOTICE '  ✓ Deleted % timeline trips', timeline_trips_count;
    END IF;

    -- Delete timeline stays (if exists)
    IF timeline_stays_count > 0 THEN
        RAISE NOTICE '→ Deleting timeline stays...';
        DELETE FROM timeline_stays
        WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');
        RAISE NOTICE '  ✓ Deleted % timeline stays', timeline_stays_count;
    END IF;

    -- Delete timeline data gaps (if exists)
    DECLARE
        data_gaps_count INT;
    BEGIN
        SELECT COUNT(*) INTO data_gaps_count
        FROM timeline_data_gaps
        WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');

        IF data_gaps_count > 0 THEN
            RAISE NOTICE '→ Deleting timeline data gaps...';
            DELETE FROM timeline_data_gaps
            WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');
            RAISE NOTICE '  ✓ Deleted % timeline data gaps', data_gaps_count;
        END IF;
    EXCEPTION
        WHEN undefined_table THEN
            NULL;  -- Table doesn't exist, skip
    END;

    -- Delete favorite locations (if any)
    DECLARE
        favorites_count INT;
    BEGIN
        SELECT COUNT(*) INTO favorites_count
        FROM favorite_locations
        WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');

        IF favorites_count > 0 THEN
            RAISE NOTICE '→ Deleting favorite locations...';
            DELETE FROM favorite_locations
            WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');
            RAISE NOTICE '  ✓ Deleted % favorite locations', favorites_count;
        END IF;
    EXCEPTION
        WHEN undefined_table THEN
            NULL;  -- Table doesn't exist, skip
    END;

    -- Delete users (this will CASCADE delete any remaining related data)
    RAISE NOTICE '→ Deleting test users...';
    DELETE FROM users WHERE email LIKE 'loadtest-user-%@example.com';
    RAISE NOTICE '  ✓ Deleted % test users', test_user_count;

    -- Optionally clean up test reverse geocoding cache entries
    DECLARE
        cache_count INT;
    BEGIN
        SELECT COUNT(*) INTO cache_count
        FROM reverse_geocoding_location
        WHERE provider_name = 'test' AND user_id IS NULL;

        IF cache_count > 0 THEN
            RAISE NOTICE '→ Deleting test reverse geocoding cache entries...';
            DELETE FROM reverse_geocoding_location
            WHERE provider_name = 'test' AND user_id IS NULL;
            RAISE NOTICE '  ✓ Deleted % cache entries', cache_count;
        END IF;
    END;

    end_time := clock_timestamp();

    RAISE NOTICE '';
    RAISE NOTICE '───────────────────────────────────────────────────────────────';
    RAISE NOTICE '✓ Cleanup complete!';
    RAISE NOTICE '  Execution time: %', end_time - start_time;
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE '';

    -- Run VACUUM to reclaim disk space
    RAISE NOTICE 'Recommendation: Run VACUUM ANALYZE to reclaim disk space:';
    RAISE NOTICE '  VACUUM ANALYZE gps_points;';
    RAISE NOTICE '  VACUUM ANALYZE users;';
    RAISE NOTICE '';

END $$;

-- Verification: Confirm all test users are deleted
SELECT
    '═══ Verification ═══' as section,
    COUNT(*) as remaining_test_users
FROM users
WHERE email LIKE 'loadtest-user-%@example.com';

-- If count is 0, cleanup was successful
RAISE NOTICE '✓ If "remaining_test_users" = 0, cleanup was successful.';
