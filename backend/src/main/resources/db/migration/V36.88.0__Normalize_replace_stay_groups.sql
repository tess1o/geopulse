-- 2.3.0's "Insert trip into forced stay" superseded the original forced-stay edit and recreated the remaining time
-- with extra FORCE_STAY edits. Corrections now chain: the original edit stays active and the inserted activities are
-- carved out of its result, so no replacement marker and no supersede link are needed. Turn the old groups into
-- ordinary chained groups: reactivate the original edit, then drop the marker only the removed path understood.
--
-- This migration must never fail on data: the app cannot start if a migration fails. Every cast is guarded by CASE
-- (which, unlike AND, guarantees the other branch is not evaluated) and bounded to 18 digits, and every JSON access is
-- guarded by jsonb_typeof. Anything that does not look exactly like the 2.3.0 marker is left untouched.

-- Reactivate the original and, from the rows actually reactivated, request one generation per user. The original lost
-- its application row when it was superseded; the generation applies it again and gives it one back. The timeline
-- content does not change because the group's edits carve the same ranges from the original.
WITH replacements AS (
    SELECT e.user_id, e.group_id,
           CASE WHEN (e.payload ->> 'replacesStaySequence') ~ '^[0-9]{1,18}$'
                THEN (e.payload ->> 'replacesStaySequence')::bigint END AS original_seq
    FROM timeline_edits e
    WHERE e.group_id IS NOT NULL
      AND jsonb_typeof(e.payload) = 'object'
),
reactivated AS (
    UPDATE timeline_edits original
    SET status = 'ACTIVE',
        superseded_by = NULL,
        updated_at = NOW()
    FROM replacements
    WHERE original.user_id = replacements.user_id
      AND original.seq = replacements.original_seq
      AND original.status = 'SUPERSEDED'
      -- Only the group that actually replaced this edit may bring it back; a later, unrelated supersede stands.
      AND original.superseded_by IN (
          SELECT member.id
          FROM timeline_edits member
          WHERE member.user_id = replacements.user_id
            AND member.group_id = replacements.group_id
      )
    RETURNING original.user_id, original.affected_from
)
INSERT INTO timeline_rebuild_requests (user_id, rebuild_from, revision)
SELECT reactivated.user_id, MIN(reactivated.affected_from), 1
FROM reactivated
GROUP BY reactivated.user_id
ON CONFLICT (user_id) DO UPDATE
    SET rebuild_from = LEAST(timeline_rebuild_requests.rebuild_from, EXCLUDED.rebuild_from),
        revision = timeline_rebuild_requests.revision + 1,
        failed_attempts = 0,
        updated_at = NOW();

-- Make the old group depend on the original, as a group created by the current code would: undoing the original then
-- waits for the group, and undoing the group lets the rebuild restore the original stay. Only add the dependency when
-- the member has no prerequisites of its own, so nothing the old flow recorded is overwritten.
WITH replacements AS (
    SELECT e.user_id, e.group_id,
           CASE WHEN (e.payload ->> 'replacesStaySequence') ~ '^[0-9]{1,18}$'
                THEN (e.payload ->> 'replacesStaySequence')::bigint END AS original_seq
    FROM timeline_edits e
    WHERE e.group_id IS NOT NULL
      AND jsonb_typeof(e.payload) = 'object'
)
UPDATE timeline_edits member
SET before_values = COALESCE(member.before_values, '{}'::jsonb)
                    || jsonb_build_object('prerequisiteSequences', jsonb_build_array(replacements.original_seq))
FROM replacements
WHERE member.user_id = replacements.user_id
  AND member.group_id = replacements.group_id
  AND replacements.original_seq IS NOT NULL
  AND member.seq <> replacements.original_seq
  AND (member.before_values IS NULL OR jsonb_typeof(member.before_values) = 'object')
  AND NOT jsonb_exists(COALESCE(member.before_values, '{}'::jsonb), 'prerequisiteSequences')
  AND EXISTS (
      SELECT 1
      FROM timeline_edits original
      WHERE original.user_id = replacements.user_id
        AND original.seq = replacements.original_seq
        AND original.status = 'ACTIVE'
  );

UPDATE timeline_edits
SET payload = payload - 'replacesStaySequence'
WHERE jsonb_typeof(payload) = 'object'
  AND jsonb_exists(payload, 'replacesStaySequence');
