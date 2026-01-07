-- GeoPulse Load Testing - Test Users Creation Script
--
-- Purpose: Creates 20-50 test users with 200K GPS points each by cloning existing production user's data
-- Usage: psql -h <production-host> -U <user> -d geopulse -f create-test-users.sql
--
-- WARNING: This script will add significant data to your production database.
-- Ensure you have enough storage capacity before running.

-- Configuration variables (EDIT THESE BEFORE RUNNING)
DO $$
DECLARE
    source_user_email TEXT := 'your-user@example.com';  -- **REPLACE** with your actual user email
    source_user_id UUID;
    num_test_users INT := 20;  -- Number of test users to create (recommended: 20-50)
    time_shift_weeks INT := 1;  -- Weeks to shift timestamps per user
    test_user_id UUID;
    test_user_password TEXT := '$2a$12$GBOzw3CPRQvAXWAmo0JYsOfeKvGCVfqzogKxA/MrMjfUX94JhVO5C';  -- hash for default password LoadTest123!
    i INT;
    gps_count INT;
    total_start_time TIMESTAMP;
    user_start_time TIMESTAMP;
    user_end_time TIMESTAMP;
BEGIN
    total_start_time := clock_timestamp();

    RAISE NOTICE '';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE '  GeoPulse Load Testing - Test Users Creation';
    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Configuration:';
    RAISE NOTICE '  Source user email: %', source_user_email;
    RAISE NOTICE '  Number of test users: %', num_test_users;
    RAISE NOTICE '  Time shift per user: % weeks', time_shift_weeks;
    RAISE NOTICE '  Test user password: %', test_user_password;
    RAISE NOTICE '───────────────────────────────────────────────────────────────';
    RAISE NOTICE '';

    -- Get source user ID
    SELECT id INTO source_user_id FROM users WHERE email = source_user_email;

    IF source_user_id IS NULL THEN
        RAISE EXCEPTION 'Source user not found with email: %', source_user_email;
    END IF;

    RAISE NOTICE '✓ Source user found: % (ID: %)', source_user_email, source_user_id;

    -- Get source user GPS count
    SELECT COUNT(*) INTO gps_count FROM gps_points WHERE user_id = source_user_id;
    RAISE NOTICE '✓ Source user has % GPS points', gps_count;
    RAISE NOTICE '';

    IF gps_count < 100000 THEN
        RAISE WARNING 'Source user has less than 100K GPS points (found: %). This may not provide enough data for realistic load testing.', gps_count;
    END IF;

    RAISE NOTICE 'Creating % test users (this may take several minutes)...', num_test_users;
    RAISE NOTICE '';

    -- Create test users and clone GPS data
    FOR i IN 1..num_test_users LOOP
        user_start_time := clock_timestamp();

        -- Generate test user ID
        test_user_id := gen_random_uuid();

        -- Create test user (copying from source user)
        INSERT INTO users (
            id, email, emailVerified, password_hash, full_name,
            created_at, updated_at, is_active, role, timezone,
            avatar, measure_unit, share_location_with_friends
        )
        SELECT
            test_user_id,
            'loadtest-user-' || i || '@example.com',
            true,
            password_hash,  -- Reuses same password hash as source user
            'Load Test User ' || i,
            NOW(),
            NOW(),
            true,
            'USER',
            'UTC',
            NULL,  -- No avatar
            'METRIC',  -- Default measure unit
            false  -- Don't share location
        FROM users WHERE id = source_user_id;

        -- Clone GPS points with time shift
        INSERT INTO gps_points (
            device_id, user_id, coordinates, timestamp,
            accuracy, battery, velocity, altitude, source_type, created_at
        )
        SELECT
            'loadtest-device-' || i,  -- Unique device ID per test user
            test_user_id,  -- New test user ID
            coordinates,
            timestamp + (i - 1) * (time_shift_weeks || ' weeks')::INTERVAL,  -- Time shift
            accuracy,
            battery,
            velocity,
            altitude,
            source_type,
            NOW()
        FROM gps_points
        WHERE user_id = source_user_id;

        -- Get count for verification
        SELECT COUNT(*) INTO gps_count FROM gps_points WHERE user_id = test_user_id;

        user_end_time := clock_timestamp();

        RAISE NOTICE '[%/%] Created loadtest-user-%@example.com with % GPS points (%.2f seconds)',
            i, num_test_users, i, gps_count,
            EXTRACT(EPOCH FROM (user_end_time - user_start_time));

        -- Pre-populate reverse geocoding cache to avoid external API calls during load testing
        -- Sample every 100th GPS point to avoid creating too many cache entries
        IF i = 1 THEN  -- Only need to do this once since cache is shared
            RAISE NOTICE '   └─ Pre-populating reverse geocoding cache...';

            -- Insert cache entries, ignoring duplicates
            DECLARE
                cache_inserted INT := 0;
                coords RECORD;
            BEGIN
                -- Use a loop to insert one at a time and ignore errors
                FOR coords IN (
                    SELECT DISTINCT ON (ST_SnapToGrid(coordinates, 0.001))  -- Group similar coordinates
                        coordinates
                    FROM gps_points
                    WHERE user_id = test_user_id
                    AND MOD(id::INT, 100) = 0  -- Sample every 100th point
                    LIMIT 2000  -- Limit to avoid too many cache entries
                )
                LOOP
                    BEGIN
                        INSERT INTO reverse_geocoding_location (
                            id, request_coordinates, result_coordinates, display_name,
                            provider_name, city, country, created_at, last_accessed_at, user_id
                        ) VALUES (
                            nextval('reverse_geocoding_location_seq'),
                            coords.coordinates,
                            coords.coordinates,
                            'Load Test Location',
                            'test',
                            'Load Test City',
                            'Load Test Country',
                            NOW(),
                            NOW(),
                            NULL  -- NULL user_id makes it shared/original (available to all users)
                        );
                        cache_inserted := cache_inserted + 1;
                    EXCEPTION
                        WHEN unique_violation THEN
                            NULL;  -- Ignore duplicate coordinates
                        WHEN OTHERS THEN
                            NULL;  -- Ignore any other errors
                    END;
                END LOOP;

                RAISE NOTICE '   └─ ✓ Reverse geocoding cache populated (% entries)', cache_inserted;
            END;
        END IF;

    END LOOP;

    RAISE NOTICE '';
    RAISE NOTICE '───────────────────────────────────────────────────────────────';
    RAISE NOTICE '✓ Successfully created % test users!', num_test_users;

    -- Get total statistics
    DECLARE
        total_gps INT;
        total_duration INTERVAL;
    BEGIN
        SELECT COUNT(*) INTO total_gps
        FROM gps_points
        WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-user-%@example.com');

        total_duration := clock_timestamp() - total_start_time;

        RAISE NOTICE '  Total GPS points created: %', total_gps;
        RAISE NOTICE '  Total execution time: %', total_duration;
        RAISE NOTICE '  Average GPS points per user: %', total_gps / num_test_users;
    END;

    RAISE NOTICE '═══════════════════════════════════════════════════════════════';
    RAISE NOTICE '';

END $$;

-- Verification Queries
RAISE NOTICE 'Running verification queries...';
RAISE NOTICE '';

-- Count of test users created
SELECT
    '═══ Test Users Summary ═══' as section,
    COUNT(*) as total_users_created
FROM users
WHERE email LIKE 'loadtest-user-%@example.com';

-- Detailed breakdown per user
SELECT
    u.email,
    COUNT(g.id) as gps_points_count,
    MIN(g.timestamp)::DATE as earliest_point_date,
    MAX(g.timestamp)::DATE as latest_point_date,
    EXTRACT(DAY FROM (MAX(g.timestamp) - MIN(g.timestamp)))::INT as date_range_days
FROM users u
LEFT JOIN gps_points g ON u.id = g.user_id
WHERE u.email LIKE 'loadtest-user-%@example.com'
GROUP BY u.email
ORDER BY u.email;

-- Reverse geocoding cache coverage
SELECT
    '═══ Reverse Geocoding Cache ═══' as section,
    COUNT(*) as cache_entries,
    COUNT(DISTINCT request_coordinates) as unique_coordinates
FROM reverse_geocoding_location
WHERE provider_name = 'test' OR user_id IS NULL;

RAISE NOTICE '';
RAISE NOTICE '✓ Setup complete! You can now run k6 load tests against these users.';
RAISE NOTICE '';
RAISE NOTICE 'Next steps:';
RAISE NOTICE '  1. Update k6 configuration with your production server URL';
RAISE NOTICE '  2. Set TEST_USER_PASSWORD to match the source user password';
RAISE NOTICE '  3. Run: k6 run k6/scenarios/mixed-workload.js';
RAISE NOTICE '';
