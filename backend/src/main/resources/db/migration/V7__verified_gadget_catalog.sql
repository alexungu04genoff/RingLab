-- First-party evidence checked through 2026-09-12; see docs/game-data-sources.md.
-- Existing IDs are immutable. No build, ordered selection, or social rows are changed.
-- New IDs use the reserved 70000000-0000-4000-8000 namespace, allocated explicitly below.
-- NULL means unsupported, not zero. Costs are latest verified values, not patch rules.

UPDATE gadgets SET name = 'Inventory Swap',
    description = 'Use the Gadget Button to swap stocked items.'
WHERE id = '174ea0a3-43bb-5001-bbd4-8b598482fe59';
UPDATE gadgets SET name = 'Ultimate Charge',
    description = 'Adds a Level 4 charge boost with 1.5 seconds of invincibility.'
WHERE id = 'f8a1e5bd-b342-5a88-87e1-908adb2e165f';

UPDATE gadgets SET image_path = '/assets/gadgets/item-stock-plus.png' WHERE id = '238359e7-ba2a-582d-bc82-6ab1c14b0284';
UPDATE gadgets SET image_path = '/assets/gadgets/giant-rocket-punch.png' WHERE id = 'c857021b-d746-5d73-b54d-ccd594064c80';
UPDATE gadgets SET image_path = '/assets/gadgets/lucky-pair.png' WHERE id = '84c606c3-0540-5a3e-bb92-27185533a89b';
UPDATE gadgets SET image_path = '/assets/gadgets/double-down.png' WHERE id = 'f6f75d95-16dd-538d-89b5-49c71cc8a346';
UPDATE gadgets SET image_path = '/assets/gadgets/ring-mercy.png' WHERE id = 'b5f42975-23b3-5a1d-9173-c18d2e584244';
UPDATE gadgets SET image_path = '/assets/gadgets/damage-mercy.png' WHERE id = '97147799-cf1d-5f50-b133-3ff6091574d3';
UPDATE gadgets SET image_path = '/assets/gadgets/item-mercy.png' WHERE id = 'f3b3dd31-9912-5a84-a8db-a76d1f93dcbe';
UPDATE gadgets SET image_path = '/assets/gadgets/strong-finish.png' WHERE id = '9735d3ab-d53b-52a1-a80d-ed0cb89429c2';
UPDATE gadgets SET image_path = '/assets/gadgets/champion-bounty.png' WHERE id = 'b3002fe1-32a2-5a06-8c07-64d0511811ad';
UPDATE gadgets SET image_path = '/assets/gadgets/route-planner-bounty.png' WHERE id = '175c2797-0be5-571a-99ac-af4bfdbf58a4';
UPDATE gadgets SET image_path = '/assets/gadgets/ring-doubler.png' WHERE id = '3130026a-75cc-50c9-94ba-4748263acbfd';
UPDATE gadgets SET image_path = '/assets/gadgets/less-is-more.png' WHERE id = '180a09fc-0034-5f56-be95-2938ce9ed61d';
UPDATE gadgets SET image_path = '/assets/gadgets/ring-engine.png' WHERE id = '5a000a58-7d7a-581c-80e3-6ae8661215b7';
UPDATE gadgets SET image_path = '/assets/gadgets/hyper-ring-engine.png' WHERE id = '182bdfa8-44d3-5de8-941b-d525381dd0a3';

INSERT INTO gadgets (id, name, description, slot_cost, image_path) VALUES
('70000000-0000-4000-8000-000000000001', 'Ultimate Air Trick', NULL, NULL, '/assets/gadgets/ultimate-air-trick.png'),
('70000000-0000-4000-8000-000000000002', 'Perfect Landing', NULL, NULL, '/assets/gadgets/perfect-landing.png'),
('70000000-0000-4000-8000-000000000003', 'Substitute Item', NULL, NULL, '/assets/gadgets/substitute-item.png'),
('70000000-0000-4000-8000-000000000004', 'Invincible Finish', NULL, NULL, '/assets/gadgets/invincible-finish.png'),
('70000000-0000-4000-8000-000000000005', 'Collision Evolution', NULL, NULL, '/assets/gadgets/collision-evolution.png'),
('70000000-0000-4000-8000-000000000006', 'Ring Evolution', NULL, NULL, '/assets/gadgets/ring-evolution.png'),
('70000000-0000-4000-8000-000000000007', 'Damage Evolution', NULL, NULL, '/assets/gadgets/damage-evolution.png'),
('70000000-0000-4000-8000-000000000008', 'Item Hit Evolution', NULL, NULL, '/assets/gadgets/item-hit-evolution.png'),
('70000000-0000-4000-8000-000000000009', 'Summon Item Box', NULL, NULL, '/assets/gadgets/summon-item-box.png'),
('70000000-0000-4000-8000-000000000010', 'Go Go Omochao', NULL, NULL, '/assets/gadgets/go-go-omochao.png'),
('70000000-0000-4000-8000-000000000011', 'Boost Character Kit', NULL, 3, '/assets/gadgets/boost-character-kit.png'),
('70000000-0000-4000-8000-000000000012', 'Power Character Kit', NULL, 3, '/assets/gadgets/power-character-kit.png'),
('70000000-0000-4000-8000-000000000013', 'Handling Character Kit', 'Supports collisions and Ring theft, with stat adjustments for Handling characters.', 3, '/assets/gadgets/handling-character-kit.png'),
('70000000-0000-4000-8000-000000000014', 'Comeback Kit', NULL, 3, '/assets/gadgets/comeback-kit.png'),
('70000000-0000-4000-8000-000000000015', 'Sea Dog Kit', NULL, 3, '/assets/gadgets/sea-dog-kit.png'),
('70000000-0000-4000-8000-000000000016', 'Acceleration Character Kit', NULL, NULL, '/assets/gadgets/acceleration-character-kit.png'),
('70000000-0000-4000-8000-000000000017', 'Ace Pilot Kit', NULL, NULL, '/assets/gadgets/ace-pilot-kit.png'),
('70000000-0000-4000-8000-000000000018', 'All-Rounder Kit', NULL, NULL, '/assets/gadgets/all-rounder-kit.png'),
('70000000-0000-4000-8000-000000000019', 'Wisp Hoarder Kit', NULL, NULL, '/assets/gadgets/wisp-hoarder-kit.png'),
('70000000-0000-4000-8000-000000000020', 'Speed Character Kit', NULL, NULL, '/assets/gadgets/speed-character-kit.png'),
('70000000-0000-4000-8000-000000000021', 'Item Buster Kit', NULL, NULL, '/assets/gadgets/item-buster-kit.png'),
('70000000-0000-4000-8000-000000000022', 'Drift Spinner Kit', NULL, NULL, '/assets/gadgets/drift-spinner-kit.png'),
('70000000-0000-4000-8000-000000000023', 'Panel Combo Kit', NULL, NULL, '/assets/gadgets/panel-combo-kit.png'),
('70000000-0000-4000-8000-000000000024', '4th Stage Charge Kit', NULL, NULL, '/assets/gadgets/4th-stage-charge-kit.png'),
('70000000-0000-4000-8000-000000000025', 'Perfect Charge Kit', NULL, NULL, '/assets/gadgets/perfect-charge-kit.png'),
('70000000-0000-4000-8000-000000000026', 'Air Trick Action Kit', NULL, NULL, '/assets/gadgets/air-trick-action-kit.png'),
('70000000-0000-4000-8000-000000000027', 'Starting Boost Bounty', 'Awards Rings for a successful Starting Boost.', NULL, '/assets/gadgets/starting-boost-bounty.png'),
('70000000-0000-4000-8000-000000000028', '130 Ring Limit', 'Raises the Ring holding limit to 130.', NULL, '/assets/gadgets/130-ring-limit.png'),
('70000000-0000-4000-8000-000000000029', 'Collision Boost', 'Grants a boost when colliding with a Machine.', NULL, '/assets/gadgets/collision-boost.png'),
('70000000-0000-4000-8000-000000000030', 'Extended Slipstream', 'Makes slipstreams occur more often.', NULL, '/assets/gadgets/extended-slipstream.png'),
('70000000-0000-4000-8000-000000000031', 'Ring Thief', 'Steals 5 Rings when colliding with a Machine.', NULL, '/assets/gadgets/ring-thief.png'),
('70000000-0000-4000-8000-000000000032', 'Mini Ring Thief', 'Steals 2 Rings when colliding with a Machine.', NULL, '/assets/gadgets/mini-ring-thief.png'),
('70000000-0000-4000-8000-000000000033', 'Quick Recovery', 'Reduces recovery time after an attack.', NULL, NULL),
('70000000-0000-4000-8000-000000000034', 'Acceleration Machine Kit', NULL, 3, '/assets/gadgets/acceleration-machine-kit.png'),
('70000000-0000-4000-8000-000000000035', 'Wisp Chance UP', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000036', 'Invincible Start', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000037', 'Handling Machine Kit', NULL, 3, NULL),
('70000000-0000-4000-8000-000000000038', 'Power Machine Kit', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000039', 'Spin Drift', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000040', 'Crash Pads', NULL, 1, NULL),
('70000000-0000-4000-8000-000000000041', 'Warp Ring Specialist', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000042', 'Item Keeper', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000043', 'Perfect Charge Boost', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000044', 'Friction Drift', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000045', 'Quick Starter', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000046', 'Slow Starter', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000047', 'Super Quick Starter', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000048', 'Super Slow Starter', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000049', 'Speed Machine Kit', NULL, 3, '/assets/gadgets/speed-machine-kit.png'),
('70000000-0000-4000-8000-000000000050', 'Drift Charge Kit', NULL, NULL, NULL),
('70000000-0000-4000-8000-000000000051', 'Spin Dash Kit', NULL, NULL, '/assets/gadgets/spin-dash-kit.png');
