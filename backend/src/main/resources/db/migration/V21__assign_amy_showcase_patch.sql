-- This community build was created while the new-build editor still defaulted
-- to an unspecified patch. Its selections use the current 1.4.1 catalog.
UPDATE builds
SET game_version_id = '50000000-0000-0000-0000-000000000001',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '358270d0-f658-4de1-8d3b-4c057e3cf7a0'
  AND game_version_id IS NULL;
