ALTER TABLE machines ADD COLUMN family VARCHAR(20);

UPDATE machines SET family = 'STANDARD';

ALTER TABLE machines ALTER COLUMN family SET NOT NULL;
ALTER TABLE machines ADD CONSTRAINT machines_family_check
  CHECK (family IN ('STANDARD', 'BOARD'));

ALTER TABLE builds ALTER COLUMN tire_part_id DROP NOT NULL;
