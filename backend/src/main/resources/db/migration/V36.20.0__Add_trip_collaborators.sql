CREATE TABLE trip_collaborators (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    collaborator_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_trip_collaborators_trip_user UNIQUE (trip_id, collaborator_user_id),
    CONSTRAINT chk_trip_collaborators_role CHECK (access_role IN ('VIEW', 'EDIT'))
);

CREATE INDEX idx_trip_collaborators_collaborator_user_id
    ON trip_collaborators(collaborator_user_id);

CREATE INDEX idx_trip_collaborators_trip_id
    ON trip_collaborators(trip_id);
