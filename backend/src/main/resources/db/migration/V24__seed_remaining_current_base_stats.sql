-- Ver. 1.4.1 values from SRCW Gadget Builder and its linked
-- "SRCW Machine & Character Stats - Best to Worst (Ver. 1.4.1)" sheet.
-- Machine values are per-part contributions, matching the existing RingLab model.
-- Store an independent copy for every currently selectable version so later
-- patch-specific corrections can update one snapshot without changing another.

WITH sourced_racers (name, speed, acceleration, handling, power, boost) AS (
    VALUES
        ('AiAi', 5, 16, 17, 8, 14),
        ('Arle', 6, 15, 16, 9, 14),
        ('Axel', 14, 17, 10, 12, 7),
        ('Blinky', 18, 6, 12, 15, 9),
        ('Donatello', 7, 14, 19, 9, 11),
        ('Goro Majima (Captain Majima)', 13, 16, 11, 12, 8),
        ('Leonardo', 17, 5, 14, 16, 8),
        ('Mega Man', 12, 8, 6, 19, 15),
        ('Michelangelo', 14, 18, 10, 11, 7),
        ('NiGHTS', 17, 6, 13, 15, 9),
        ('PAC-MAN', 15, 18, 9, 12, 6),
        ('Proto Man', 17, 7, 12, 14, 10),
        ('Raphael', 11, 9, 7, 20, 13),
        ('Red', 17, 7, 12, 14, 10),
        ('Tangle', 15, 16, 9, 14, 6)
)
INSERT INTO racer_stats
    (racer_id, game_version_id, speed, acceleration, handling, power, boost)
SELECT racer.id, version.id, sourced.speed, sourced.acceleration,
       sourced.handling, sourced.power, sourced.boost
FROM sourced_racers sourced
JOIN racers racer ON racer.name = sourced.name
CROSS JOIN game_versions version
ON CONFLICT (racer_id, game_version_id) DO UPDATE
SET speed = EXCLUDED.speed,
    acceleration = EXCLUDED.acceleration,
    handling = EXCLUDED.handling,
    power = EXCLUDED.power,
    boost = EXCLUDED.boost;

WITH sourced_machines (name, speed, acceleration, handling, power, boost) AS (
    VALUES
        ('Banana Cruiser', 17, 7, 12, 14, 10),
        ('Devolada Yellow Jack', 16, 6, 14, 15, 9),
        ('Dream Sleeper', 13, 18, 23, 9, 27),
        ('Goromaru', 12, 10, 7, 17, 14),
        ('PAC-MAN Mobile', 6, 15, 17, 9, 13),
        ('Pizzafire Van', 14, 9, 6, 16, 14),
        ('Rush Roadstar', 15, 17, 9, 13, 6),
        ('Super Roaster', 14, 17, 10, 12, 7),
        ('Twinkle Bayoen', 15, 16, 9, 14, 6)
)
INSERT INTO machine_part_stats
    (machine_part_id, game_version_id, speed, acceleration, handling, power, boost)
SELECT part.id, version.id, sourced.speed, sourced.acceleration,
       sourced.handling, sourced.power, sourced.boost
FROM sourced_machines sourced
JOIN machines machine ON machine.name = sourced.name
JOIN machine_parts part ON part.source_machine_id = machine.id
CROSS JOIN game_versions version
ON CONFLICT (machine_part_id, game_version_id) DO UPDATE
SET speed = EXCLUDED.speed,
    acceleration = EXCLUDED.acceleration,
    handling = EXCLUDED.handling,
    power = EXCLUDED.power,
    boost = EXCLUDED.boost;
