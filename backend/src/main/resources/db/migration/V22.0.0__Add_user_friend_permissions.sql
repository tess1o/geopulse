-- Add user_friend_permissions table for granular timeline sharing permissions
-- This allows users to control which friends can view their historical timeline data

CREATE TABLE user_friend_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    friend_id UUID NOT NULL,
    share_timeline BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_user_friend_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_friend_permissions_friend FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_friend_permission UNIQUE (user_id, friend_id)
);

CREATE INDEX idx_user_friend_permissions_user ON user_friend_permissions(user_id);
CREATE INDEX idx_user_friend_permissions_friend ON user_friend_permissions(friend_id);

COMMENT ON TABLE user_friend_permissions IS 'Stores granular permissions for friend timeline sharing';
COMMENT ON COLUMN user_friend_permissions.user_id IS 'The user granting the permission';
COMMENT ON COLUMN user_friend_permissions.friend_id IS 'The friend receiving the permission';
COMMENT ON COLUMN user_friend_permissions.share_timeline IS 'Whether the user allows this friend to view their historical timeline';
