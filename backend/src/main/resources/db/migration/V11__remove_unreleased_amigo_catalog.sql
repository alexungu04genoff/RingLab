-- Amigo and Locomotive de Amigo are unreleased at the project owner's 2026-09-12 cutoff.
-- Delete parts first. The machine/racer deletes intentionally fail if a real build references them,
-- preventing this catalog correction from silently discarding community data.
DELETE FROM machine_parts
WHERE source_machine_id = (SELECT id FROM machines WHERE name = 'Locomotive de Amigo');

DELETE FROM machines WHERE name = 'Locomotive de Amigo';
DELETE FROM racers WHERE name = 'Amigo';
DELETE FROM gadgets WHERE name = '4th Stage Charge Kit';
