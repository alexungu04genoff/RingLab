-- Release cutoff: 2026-09-12. Evidence and deliberate gaps: docs/game-data-sources.md.
-- Unknown racing types are nullable rather than invented. Existing types and IDs stay intact.
ALTER TABLE racers ALTER COLUMN racing_type DROP NOT NULL;
ALTER TABLE machines ALTER COLUMN racing_type DROP NOT NULL;

INSERT INTO racers (id, name, image_path) VALUES
('60000000-0000-4000-8000-000000000001', 'Cream & Cheese', '/assets/racers/cream-and-cheese.png'),
('60000000-0000-4000-8000-000000000002', 'Silver the Hedgehog', '/assets/racers/silver.png'),
('60000000-0000-4000-8000-000000000003', 'Blaze the Cat', '/assets/racers/blaze.png'),
('60000000-0000-4000-8000-000000000004', 'Rouge the Bat', '/assets/racers/rouge.png'),
('60000000-0000-4000-8000-000000000005', 'E-123 Omega', '/assets/racers/omega.png'),
('60000000-0000-4000-8000-000000000006', 'Vector the Crocodile', '/assets/racers/vector.png'),
('60000000-0000-4000-8000-000000000007', 'Espio the Chameleon', '/assets/racers/espio.png'),
('60000000-0000-4000-8000-000000000008', 'Charmy Bee', '/assets/racers/charmy.png'),
('60000000-0000-4000-8000-000000000009', 'Zavok', '/assets/racers/zavok.png'),
('60000000-0000-4000-8000-000000000010', 'Zazz', '/assets/racers/zazz.png'),
('60000000-0000-4000-8000-000000000011', 'Dr. Eggman', '/assets/racers/eggman.png'),
('60000000-0000-4000-8000-000000000012', 'Metal Sonic', '/assets/racers/metal-sonic.png'),
('60000000-0000-4000-8000-000000000013', 'Egg Pawn', '/assets/racers/egg-pawn.png'),
('60000000-0000-4000-8000-000000000014', 'Sage', '/assets/racers/sage.png'),
('60000000-0000-4000-8000-000000000015', 'Jet the Hawk', '/assets/racers/jet.png'),
('60000000-0000-4000-8000-000000000016', 'Wave the Swallow', '/assets/racers/wave.png'),
('60000000-0000-4000-8000-000000000017', 'Storm the Albatross', '/assets/racers/storm.png'),
('60000000-0000-4000-8000-000000000018', 'Hatsune Miku', '/assets/racers/hatsune-miku.png'),
('60000000-0000-4000-8000-000000000019', 'Joker', '/assets/racers/joker.png'),
('60000000-0000-4000-8000-000000000020', 'Ichiban Kasuga', '/assets/racers/ichiban-kasuga.png'),
('60000000-0000-4000-8000-000000000021', 'NiGHTS', '/assets/racers/nights.png'),
('60000000-0000-4000-8000-000000000022', 'AiAi', '/assets/racers/aiai.png'),
('60000000-0000-4000-8000-000000000023', 'Tangle', '/assets/racers/tangle.png'),
('60000000-0000-4000-8000-000000000024', 'Whisper', '/assets/racers/whisper.png'),
('60000000-0000-4000-8000-000000000025', 'Red', '/assets/racers/red.png'),
('60000000-0000-4000-8000-000000000026', 'Goro Majima (Captain Majima)', '/assets/racers/captain-majima.png'),
('60000000-0000-4000-8000-000000000027', 'Arle', '/assets/racers/arle.png'),
('60000000-0000-4000-8000-000000000028', 'Classic Sonic', '/assets/racers/classic-sonic.png'),
('60000000-0000-4000-8000-000000000029', 'Axel', '/assets/racers/axel.png'),
('60000000-0000-4000-8000-000000000030', 'Amigo', '/assets/racers/amigo.png'),
('60000000-0000-4000-8000-000000000031', 'Steve', '/assets/racers/steve.png'),
('60000000-0000-4000-8000-000000000032', 'Alex', '/assets/racers/alex.png'),
('60000000-0000-4000-8000-000000000033', 'Creeper', '/assets/racers/creeper.png'),
('60000000-0000-4000-8000-000000000034', 'SpongeBob SquarePants', '/assets/racers/spongebob.png'),
('60000000-0000-4000-8000-000000000035', 'Patrick Star', '/assets/racers/patrick.png'),
('60000000-0000-4000-8000-000000000036', 'PAC-MAN', '/assets/racers/pac-man.png'),
('60000000-0000-4000-8000-000000000037', 'Blinky', NULL),
('60000000-0000-4000-8000-000000000038', 'Mega Man', '/assets/racers/mega-man.png'),
('60000000-0000-4000-8000-000000000039', 'Proto Man', '/assets/racers/proto-man.png'),
('60000000-0000-4000-8000-000000000040', 'Leonardo', '/assets/racers/leonardo.png'),
('60000000-0000-4000-8000-000000000041', 'Raphael', '/assets/racers/raphael.png'),
('60000000-0000-4000-8000-000000000042', 'Donatello', '/assets/racers/donatello.png'),
('60000000-0000-4000-8000-000000000043', 'Michelangelo', '/assets/racers/michelangelo.png'),
('60000000-0000-4000-8000-000000000044', 'Tails Nine', '/assets/racers/tails-nine.png'),
('60000000-0000-4000-8000-000000000045', 'Knuckles the Dread', '/assets/racers/knuckles-the-dread.png'),
('60000000-0000-4000-8000-000000000046', 'Rusty Rose', '/assets/racers/rusty-rose.png'),
('60000000-0000-4000-8000-000000000047', 'Werehog', '/assets/racers/werehog.png');

-- Reaffirm the existing paths: the six temporary portraits are replaced with official assets.
UPDATE racers SET image_path = '/assets/racers/' || artwork.slug || '.png'
FROM (VALUES
    ('Sonic the Hedgehog', 'sonic'), ('Miles "Tails" Prower', 'tails'),
    ('Knuckles the Echidna', 'knuckles'), ('Amy Rose', 'amy'),
    ('Shadow the Hedgehog', 'shadow'), ('Big the Cat', 'big')
) AS artwork(name, slug)
WHERE racers.name = artwork.name;

INSERT INTO machines (id, name, image_path) VALUES
('70000000-0000-4000-8000-000000000001', 'Diva Macchina', '/assets/machines/diva-macchina.png'),
('70000000-0000-4000-8000-000000000002', 'Arsene Wing', '/assets/machines/arsene-wing.png'),
('70000000-0000-4000-8000-000000000003', 'Dragon Brave', '/assets/machines/dragon-brave.png'),
('70000000-0000-4000-8000-000000000004', 'Dream Sleeper', '/assets/machines/dream-sleeper.png'),
('70000000-0000-4000-8000-000000000005', 'Banana Cruiser', '/assets/machines/banana-cruiser.png'),
('70000000-0000-4000-8000-000000000006', 'Super Roaster', '/assets/machines/super-roaster.png'),
('70000000-0000-4000-8000-000000000007', 'Goromaru', '/assets/machines/goromaru.png'),
('70000000-0000-4000-8000-000000000008', 'Twinkle Bayoen', '/assets/machines/twinkle-bayoen.png'),
('70000000-0000-4000-8000-000000000009', 'Mach Cyclone', '/assets/machines/mach-cyclone.png'),
('70000000-0000-4000-8000-000000000010', 'Devolada Yellow Jack', '/assets/machines/devolada-yellow-jack.png'),
('70000000-0000-4000-8000-000000000011', 'Locomotive de Amigo', '/assets/machines/locomotive-de-amigo.png'),
('70000000-0000-4000-8000-000000000012', 'Minecart', '/assets/machines/minecart.png'),
('70000000-0000-4000-8000-000000000013', 'Patty Wagon', '/assets/machines/patty-wagon.png'),
('70000000-0000-4000-8000-000000000014', 'PAC-MAN Mobile', '/assets/machines/pac-man-mobile.png'),
('70000000-0000-4000-8000-000000000015', 'Rush Roadstar', '/assets/machines/rush-roadstar.png'),
('70000000-0000-4000-8000-000000000016', 'Pizzafire Van', '/assets/machines/pizzafire-van.png'),
('70000000-0000-4000-8000-000000000017', 'Blue Star', '/assets/machines/blue-star.png');

UPDATE machines SET image_path = '/assets/machines/' || artwork.slug || '.png'
FROM (VALUES
    ('Speedster Lightning', 'speedster-lightning'), ('Whirlwind Sport', 'whirlwind-sport'),
    ('Pink Cabriolet', 'pink-cabriolet'), ('Land Smasher', 'land-smasher'),
    ('TYPE-J Iota', 'type-j-iota')
) AS artwork(name, slug)
WHERE machines.name = artwork.name;

-- Exactly one of each part for each added machine. Preserve V4 part IDs and all build references.
-- Deterministic UUIDs need no PostgreSQL extension; they identify catalog parts, not game statistics.
INSERT INTO machine_parts (id, source_machine_id, part_type)
SELECT md5('ringlab:catalog-part:' || m.id::text || ':' || p.part_type)::uuid, m.id, p.part_type
FROM machines m CROSS JOIN (VALUES ('FRONT'), ('REAR'), ('TIRE')) AS p(part_type)
WHERE NOT EXISTS (
    SELECT 1 FROM machine_parts existing
    WHERE existing.source_machine_id = m.id AND existing.part_type = p.part_type
);
