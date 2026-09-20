-- Extreme Gear machines use FRONT and REAR parts only. V4 predated machine
-- families and generated a TIRE row for every catalog machine.
UPDATE machines
SET family = 'BOARD'
WHERE name IN (
    'Blue Star',
    'Diva Macchina',
    'Dream Sleeper',
    'Jaws Rocket',
    'Pop Float',
    'Quad Copter',
    'Sakura Board',
    'Triple Fan',
    'TYPE-J Iota',
    'TYPE-S Stream',
    'TYPE-W Windy',
    'Wispon Booster'
);

-- Existing builds involving a Board cannot retain a tire. This also releases
-- references to the generated Board tire rows before those rows are removed.
UPDATE builds build
SET tire_part_id = NULL
WHERE EXISTS (
    SELECT 1
    FROM machine_parts part
    JOIN machines machine ON machine.id = part.source_machine_id
    WHERE part.id IN (build.front_part_id, build.rear_part_id, build.tire_part_id)
      AND machine.family = 'BOARD'
);

DELETE FROM machine_part_stats stats
USING machine_parts part, machines machine
WHERE stats.machine_part_id = part.id
  AND part.source_machine_id = machine.id
  AND machine.family = 'BOARD'
  AND part.part_type = 'TIRE';

DELETE FROM machine_parts part
USING machines machine
WHERE part.source_machine_id = machine.id
  AND machine.family = 'BOARD'
  AND part.part_type = 'TIRE';
