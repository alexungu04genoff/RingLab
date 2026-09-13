ALTER TABLE builds
  ADD COLUMN remixed_from_build_id UUID
  REFERENCES builds(id) ON DELETE SET NULL;

CREATE INDEX builds_remixed_from ON builds(remixed_from_build_id);
