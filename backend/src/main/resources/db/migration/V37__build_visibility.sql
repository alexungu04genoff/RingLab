ALTER TABLE builds ADD COLUMN visibility VARCHAR(7) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE builds ADD COLUMN first_published_at TIMESTAMPTZ;

UPDATE builds SET first_published_at = created_at;

ALTER TABLE builds ADD CONSTRAINT builds_visibility_check
    CHECK (visibility IN ('PRIVATE', 'PUBLIC'));

-- Candidate queries filter publication before application ranking. Owner queries also filter author.
CREATE INDEX builds_visibility_idx ON builds (visibility);
CREATE INDEX builds_author_visibility_idx ON builds (author_id, visibility);
