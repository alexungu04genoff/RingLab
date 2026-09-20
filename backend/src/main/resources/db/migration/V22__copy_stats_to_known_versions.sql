-- The currently known base stats also apply to RingLab's three older patch
-- choices. Store a separate snapshot for each version so a future patch can
-- introduce different values without changing historical calculations.
WITH older_versions (id) AS (
    VALUES
        ('50000000-0000-0000-0000-000000000002'::uuid),
        ('50000000-0000-0000-0000-000000000003'::uuid),
        ('50000000-0000-0000-0000-000000000004'::uuid)
)
INSERT INTO racer_stats
    (racer_id, game_version_id, speed, acceleration, handling, power, boost)
SELECT current_stats.racer_id,
       older_versions.id,
       current_stats.speed,
       current_stats.acceleration,
       current_stats.handling,
       current_stats.power,
       current_stats.boost
FROM racer_stats current_stats
CROSS JOIN older_versions
WHERE current_stats.game_version_id = '50000000-0000-0000-0000-000000000001'
ON CONFLICT (racer_id, game_version_id) DO UPDATE
SET speed = EXCLUDED.speed,
    acceleration = EXCLUDED.acceleration,
    handling = EXCLUDED.handling,
    power = EXCLUDED.power,
    boost = EXCLUDED.boost;

WITH older_versions (id) AS (
    VALUES
        ('50000000-0000-0000-0000-000000000002'::uuid),
        ('50000000-0000-0000-0000-000000000003'::uuid),
        ('50000000-0000-0000-0000-000000000004'::uuid)
)
INSERT INTO machine_part_stats
    (machine_part_id, game_version_id, speed, acceleration, handling, power, boost)
SELECT current_stats.machine_part_id,
       older_versions.id,
       current_stats.speed,
       current_stats.acceleration,
       current_stats.handling,
       current_stats.power,
       current_stats.boost
FROM machine_part_stats current_stats
CROSS JOIN older_versions
WHERE current_stats.game_version_id = '50000000-0000-0000-0000-000000000001'
ON CONFLICT (machine_part_id, game_version_id) DO UPDATE
SET speed = EXCLUDED.speed,
    acceleration = EXCLUDED.acceleration,
    handling = EXCLUDED.handling,
    power = EXCLUDED.power,
    boost = EXCLUDED.boost;
