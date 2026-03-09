-- Add user default date range preset preference for Timeline/Dashboard/Reports pages
-- Allowed values: today, yesterday, lastWeek, lastMonth

ALTER TABLE users
ADD COLUMN default_date_range_preset VARCHAR(32);

COMMENT ON COLUMN users.default_date_range_preset IS
'Default date range preset for timeline views (today, yesterday, lastWeek, lastMonth)';
