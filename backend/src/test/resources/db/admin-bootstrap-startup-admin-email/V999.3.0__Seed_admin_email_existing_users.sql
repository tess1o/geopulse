INSERT INTO users (id, email, emailVerified, full_name, created_at, is_active, role, timezone)
VALUES
    (
        '00000000-0000-0000-0000-000000000301',
        'owner@example.com',
        true,
        'Startup Owner',
        NOW(),
        true,
        'USER',
        'UTC'
    ),
    (
        '00000000-0000-0000-0000-000000000302',
        'startup-admin-email-other@example.com',
        true,
        'Startup Other User',
        NOW(),
        true,
        'USER',
        'UTC'
    );
