-- The stay velocity threshold was shown in km/h but compared with GPS speeds in m/s, so a stored value of 2.5 meant
-- 9 km/h. It is now a real km/h setting like every other speed setting. Convert stored values so each user's
-- timeline keeps detecting stays exactly as before.
UPDATE users
SET timeline_preferences = jsonb_set(
        timeline_preferences,
        '{staypointVelocityThreshold}',
        to_jsonb(round(((timeline_preferences ->> 'staypointVelocityThreshold')::numeric * 3.6), 1))
    )
WHERE timeline_preferences IS NOT NULL
  AND jsonb_typeof(timeline_preferences -> 'staypointVelocityThreshold') = 'number';
