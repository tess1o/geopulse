DELETE FROM user_friends kept
USING user_friends duplicate
WHERE kept.user_id = duplicate.user_id
  AND kept.friend_id = duplicate.friend_id
  AND (
      kept.created_at > duplicate.created_at
      OR (kept.created_at = duplicate.created_at AND kept.id > duplicate.id)
      OR (kept.created_at IS NULL AND duplicate.created_at IS NOT NULL)
      OR (kept.created_at IS NULL AND duplicate.created_at IS NULL AND kept.id > duplicate.id)
  );

CREATE UNIQUE INDEX uk_user_friends_user_friend
    ON user_friends (user_id, friend_id);
