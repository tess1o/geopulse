-- Points the demo personas at the fake Immich and Memos servers.
-- Run it once against the demo database, then take a new snapshot:
--
--   docker compose exec -T geopulse-postgres psql -U "$GEOPULSE_POSTGRES_USERNAME" -d "$GEOPULSE_POSTGRES_DB" \
--     -v immich_url=http://geopulse-demo-integrations:2283 \
--     -v memos_url=https://memos.demo.geopulse.cc \
--     < demo-integrations/sql/configure-demo-users.sql
--   backend/scripts/demo/geopulse-demo-db.sh snapshot
--
-- immich_url only has to be reachable from the GeoPulse backend (photos are proxied).
-- memos_url is also used for "open in Memos" links, so make it public if you want those to work.
-- The API keys are not secrets: the fake servers look them up in this table.

\if :{?immich_url}
\else
  \set immich_url 'http://geopulse-demo-integrations:2283'
\endif
\if :{?memos_url}
\else
  \set memos_url 'http://geopulse-demo-integrations:5230'
\endif

BEGIN;

WITH personas(email, persona) AS (
  VALUES ('kyiv@demo.geopulse.cc', 'kyiv'),
         ('london@demo.geopulse.cc', 'london'),
         ('new-york@demo.geopulse.cc', 'new-york')
)
UPDATE users u
   SET immich_preferences = jsonb_build_object(
         'serverUrl', :'immich_url',
         'apiKey', 'demo-immich-' || p.persona,
         'enabled', true),
       memos_preferences = jsonb_build_object(
         'serverUrl', :'memos_url',
         'apiKey', 'demo-memos-' || p.persona,
         'enabled', true,
         'defaultSaveDestination', 'GEOPULSE',
         'defaultVisibility', 'PRIVATE',
         'searchCacheEnabled', true,
         'includeTags', '[]'::jsonb,
         'excludeTags', '[]'::jsonb)
  FROM personas p
 WHERE u.email = p.email;

SELECT email,
       immich_preferences->>'serverUrl' AS immich_url,
       memos_preferences->>'serverUrl' AS memos_url
  FROM users
 WHERE email LIKE '%@demo.geopulse.cc'
 ORDER BY email;

COMMIT;
