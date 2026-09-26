CREATE TABLE saved_builds (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (user_id, build_id)
);
CREATE INDEX saved_builds_user_chronology ON saved_builds(user_id, saved_at DESC, build_id ASC);
CREATE INDEX saved_builds_source ON saved_builds(build_id);
