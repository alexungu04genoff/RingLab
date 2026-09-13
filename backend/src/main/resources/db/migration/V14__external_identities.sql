ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE external_identities (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT external_identities_pkey PRIMARY KEY (provider, provider_subject)
);
CREATE INDEX external_identities_user_id_idx ON external_identities(user_id);
