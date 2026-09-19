-- Keep bundled demo builds ready for full build-card and detail previews.
-- Only demo accounts are touched; user-created builds remain unchanged.
WITH demo_builds AS (
    SELECT b.id
    FROM builds b
    JOIN users u ON u.id = b.author_id
    WHERE u.username LIKE 'ringlab_demo_%'
)
UPDATE builds
SET game_version_id = '50000000-0000-0000-0000-000000000001'
WHERE id IN (SELECT id FROM demo_builds);

WITH demo_builds AS (
    SELECT b.id
    FROM builds b
    JOIN users u ON u.id = b.author_id
    WHERE u.username LIKE 'ringlab_demo_%'
)
DELETE FROM build_gadgets
WHERE build_id IN (SELECT id FROM demo_builds);

WITH demo_builds AS (
    SELECT b.id
    FROM builds b
    JOIN users u ON u.id = b.author_id
    WHERE u.username LIKE 'ringlab_demo_%'
),
selected_gadgets AS (
    SELECT id, name
    FROM gadgets
    WHERE slot_cost = 1
    ORDER BY name
    LIMIT 6
)
INSERT INTO build_gadgets (build_id, gadget_id, position)
SELECT demo_builds.id,
       selected_gadgets.id,
       ROW_NUMBER() OVER (PARTITION BY demo_builds.id ORDER BY selected_gadgets.name)::integer - 1
FROM demo_builds
CROSS JOIN selected_gadgets;
