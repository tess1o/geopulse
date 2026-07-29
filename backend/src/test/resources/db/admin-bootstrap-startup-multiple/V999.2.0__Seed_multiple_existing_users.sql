INSERT INTO users (id, email, emailVerified, full_name, created_at, is_active, role, timezone)
VALUES
    (
        '00000000-0000-0000-0000-000000000201',
        'startup-multiple-user-a@example.com',
        true,
        'Startup Multiple User A',
        NOW(),
        true,
        'USER',
        'UTC'
    ),
    (
        '00000000-0000-0000-0000-000000000202',
        'startup-multiple-user-b@example.com',
        true,
        'Startup Multiple User B',
        NOW(),
        true,
        'USER',
        'UTC'
    );
