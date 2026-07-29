INSERT INTO users (id, email, emailVerified, full_name, created_at, is_active, role, timezone)
VALUES (
    '00000000-0000-0000-0000-000000000101',
    'startup-single-user@example.com',
    true,
    'Startup Single User',
    NOW(),
    true,
    'USER',
    'UTC'
);
