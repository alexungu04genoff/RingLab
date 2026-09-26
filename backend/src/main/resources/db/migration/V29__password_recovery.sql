ALTER TABLE users ADD COLUMN auth_version integer NOT NULL DEFAULT 0 CHECK (auth_version >= 0);

CREATE TABLE password_reset_tokens (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at)
);
