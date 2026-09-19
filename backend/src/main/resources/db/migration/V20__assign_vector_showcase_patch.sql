-- This existing community showcase predates patch selection but uses catalog data
-- that is fully covered by the current 1.4.1 base-stat dataset.
UPDATE builds
SET game_version_id = '50000000-0000-0000-0000-000000000001',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'd2c6c71a-0c3e-40a3-9810-dba1b686f951'
  AND game_version_id IS NULL;
