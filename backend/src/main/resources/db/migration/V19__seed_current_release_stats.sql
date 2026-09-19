-- Ver. 1.4.1 values for racers and a machine released after the original
-- stat-sheet audit. Values are sourced from the current Sonic Wiki Zone
-- character and machine tables documented in docs/game-data-sources.md.

INSERT INTO racer_stats (racer_id, game_version_id, speed, acceleration, handling, power, boost)
SELECT r.id,
       '50000000-0000-0000-0000-000000000001',
       v.speed,
       v.acceleration,
       v.handling,
       v.power,
       v.boost
FROM (VALUES
    ('Whisper', 7, 13, 16, 5, 19),
    ('Classic Sonic', 20, 5, 13, 15, 7)
) AS v(name, speed, acceleration, handling, power, boost)
JOIN racers r ON r.name = v.name
ON CONFLICT (racer_id, game_version_id) DO UPDATE
SET speed = EXCLUDED.speed,
    acceleration = EXCLUDED.acceleration,
    handling = EXCLUDED.handling,
    power = EXCLUDED.power,
    boost = EXCLUDED.boost;

-- Mach Cyclone's published stock totals are 48/21/39/42/30. Each of its
-- three selectable parts contributes one third of that stock setup.
INSERT INTO machine_part_stats
    (machine_part_id, game_version_id, speed, acceleration, handling, power, boost)
SELECT p.id,
       '50000000-0000-0000-0000-000000000001',
       16,
       7,
       13,
       14,
       10
FROM machines m
JOIN machine_parts p ON p.source_machine_id = m.id
WHERE m.name = 'Mach Cyclone'
ON CONFLICT (machine_part_id, game_version_id) DO UPDATE
SET speed = EXCLUDED.speed,
    acceleration = EXCLUDED.acceleration,
    handling = EXCLUDED.handling,
    power = EXCLUDED.power,
    boost = EXCLUDED.boost;
