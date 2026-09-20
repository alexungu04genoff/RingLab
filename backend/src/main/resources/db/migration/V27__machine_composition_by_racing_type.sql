-- Coordinated application upgrade: older binaries require machines.family.
-- Validate the V26 catalog before removing redundant classification metadata.
DO $$
DECLARE invalid_machines text;
BEGIN
  SELECT string_agg(name || ' [' || coalesce(family, 'NULL') || '/' || coalesce(racing_type, 'NULL') || ']', ', ' ORDER BY name)
  INTO invalid_machines
  FROM machines
  WHERE racing_type IS NULL
     OR racing_type NOT IN ('SPEED', 'ACCELERATION', 'HANDLING', 'POWER', 'BOOST')
     OR family IS NULL
     OR family NOT IN ('STANDARD', 'BOARD')
     OR (family = 'BOARD' AND racing_type <> 'BOOST')
     OR (family = 'STANDARD' AND racing_type = 'BOOST');
  IF invalid_machines IS NOT NULL THEN
    RAISE EXCEPTION 'Machine composition conversion blocked by contradictory/unknown catalog classifications: %', invalid_machines;
  END IF;

  SELECT string_agg(name, ', ' ORDER BY name) INTO invalid_machines
  FROM machines m
  WHERE (SELECT array_agg(p.part_type::text ORDER BY p.part_type) FROM machine_parts p WHERE p.source_machine_id = m.id)
    IS DISTINCT FROM CASE WHEN m.racing_type = 'BOOST' THEN ARRAY['FRONT', 'REAR'] ELSE ARRAY['FRONT', 'REAR', 'TIRE'] END;
  IF invalid_machines IS NOT NULL THEN
    RAISE EXCEPTION 'Machine composition conversion blocked by missing, duplicate or forbidden slots: %', invalid_machines;
  END IF;
END $$;

ALTER TABLE machines DROP CONSTRAINT machines_family_check;
ALTER TABLE machines DROP COLUMN family;
