-- Phase 5B: explicitly reviewed correction to published data; see docs/game-data-phase5b.md.
-- Canonical CSVs mirror every fact here. The ordinary importer remains immutable.
-- No user selections, ownership exclusions, historical stats or disputed part stats are changed.
UPDATE gadgets SET slot_cost = 2 WHERE id = '70000000-0000-4000-8000-000000000003';
UPDATE game_versions SET released_at = DATE '2025-12-18' WHERE id = '50000000-0000-0000-0000-000000000003';

INSERT INTO racers (id,name,racing_type,image_path)
VALUES ('81000000-0000-4000-8000-000000000001','Amigo','HANDLING',NULL);
INSERT INTO machines (id,name,racing_type,image_path)
VALUES ('82000000-0000-4000-8000-000000000001','Locomotive de Amigo','POWER',NULL);
INSERT INTO machine_parts (id,source_machine_id,part_type) VALUES
('83000000-0000-4000-8000-000000000001','82000000-0000-4000-8000-000000000001','FRONT'),
('83000000-0000-4000-8000-000000000002','82000000-0000-4000-8000-000000000001','REAR'),
('83000000-0000-4000-8000-000000000003','82000000-0000-4000-8000-000000000001','TIRE');
-- No Locomotive stat rows: conflicting Boost evidence must remain unavailable.
INSERT INTO racer_stats (racer_id,game_version_id,speed,acceleration,handling,power,boost)
VALUES ('81000000-0000-4000-8000-000000000001','50000000-0000-0000-0000-000000000001',6,15,17,9,13);

INSERT INTO gadgets (id,name,description,slot_cost,image_path) VALUES
('84000000-0000-4000-8000-000000000001','Air Trick Bounty','Awards rings when performing air tricks.','1',NULL),
('84000000-0000-4000-8000-000000000002','Item Attack Bounty','Awards rings after an item hits an opponent.','1',NULL),
('84000000-0000-4000-8000-000000000003','Runoff Bounty','Awards rings while crossing runoff areas with a speed item.','1',NULL),
('84000000-0000-4000-8000-000000000004','Slipstream Bounty','Awards rings during slipstreams.','1',NULL),
('84000000-0000-4000-8000-000000000005','Travel Ring Bounty','Awards rings when passing through a Travel Ring.','1',NULL),
('84000000-0000-4000-8000-000000000006','Morph Action Bounty','Awards rings for charge actions in water or flight.','1',NULL),
('84000000-0000-4000-8000-000000000007','Perfect Charge Bounty','Awards rings for releasing a drift as its charge level fills.','2',NULL),
('84000000-0000-4000-8000-000000000008','Dash Panel Bounty','Awards rings when using dash panels or gates.','2',NULL),
('84000000-0000-4000-8000-000000000009','Dash Panel Mini Bounty','Awards a smaller ring reward when using dash panels or gates.','1',NULL),
('84000000-0000-4000-8000-000000000010','Dash Panel Combo Bounty','Awards rings for consecutive dash panels.','2',NULL),
('84000000-0000-4000-8000-000000000011','200 Ring Limit','Raises ring capacity to 200.','2',NULL),
('84000000-0000-4000-8000-000000000012','Ring Gain Mini Boost','Briefly increases physical speed when collecting rings.','1',NULL),
('84000000-0000-4000-8000-000000000013','Ring Gain Boost','Grants a larger brief physical speed increase when collecting rings.','2',NULL),
('84000000-0000-4000-8000-000000000014','Ring Range UP','Extends the ring pickup range.','1',NULL),
('84000000-0000-4000-8000-000000000015','Air Drift Mobility UP','Changes air drift movement and release behavior in flight form.','1',NULL),
('84000000-0000-4000-8000-000000000016','Charge Jump Mobility UP','Increases lateral air trick movement after a charge jump in water form.','1',NULL),
('84000000-0000-4000-8000-000000000017','Air Trick Adept','Increases air trick animation speed.','1',NULL),
('84000000-0000-4000-8000-000000000018','Air Trick Expert','Increases air trick animation speed more than Air Trick Adept.','2',NULL),
('84000000-0000-4000-8000-000000000019','Lv1 Quick Charge','Shortens level 1 drift charge time.','2',NULL),
('84000000-0000-4000-8000-000000000020','Lv2 Quick Charge','Shortens level 2 drift charge time.','1',NULL),
('84000000-0000-4000-8000-000000000021','Lv3 Quick Charge','Shortens level 3 drift charge time.','1',NULL),
('84000000-0000-4000-8000-000000000022','Technical Drift','Trades drift traction for faster charge in land form.','2',NULL),
('84000000-0000-4000-8000-000000000023','Counter Quick Charge','Accelerates charging when changing drift direction in land form.','3',NULL),
('84000000-0000-4000-8000-000000000024','Maximum Traction','Reduces slipping on sand and ice.','1',NULL),
('84000000-0000-4000-8000-000000000025','Second Wind','Grants a Boost item after falling off the course.','1',NULL),
('84000000-0000-4000-8000-000000000026','Bumper Guard','Prevents ring loss from wall contact.','1',NULL),
('84000000-0000-4000-8000-000000000027','Boost Starter','Grants a Boost item at race start.','1',NULL),
('84000000-0000-4000-8000-000000000028','Double Boost Specialist','Adds Double Boost to item boxes and grants one at race start.','2',NULL),
('84000000-0000-4000-8000-000000000029','Dark Chao Starter','Grants a Dark Chao item at race start.','1',NULL),
('84000000-0000-4000-8000-000000000030','Giant Spiked Iron Ball','Increases Spiked Iron Ball size and item availability.','1',NULL),
('84000000-0000-4000-8000-000000000031','Short Fuse','Speeds up Bomb charging and increases its item availability.','1',NULL),
('84000000-0000-4000-8000-000000000032','Monster Truck Starter','Grants a Monster Truck item at race start.','3',NULL),
('84000000-0000-4000-8000-000000000033','Item Hoarder Kit','Combines extra item capacity with item rewards at the start of later laps.','3',NULL),
('84000000-0000-4000-8000-000000000034','Damage Support Kit','Combines starting invincibility with a Boost item after falling off the course.','3',NULL),
('84000000-0000-4000-8000-000000000035','Perfect Charge Kit','Released festival kit. Complete effect support is not yet reviewed.','3',NULL),
('84000000-0000-4000-8000-000000000036','Speedy Shortcut Kit','Released festival kit. Complete effect support is not yet reviewed.','3',NULL),
('84000000-0000-4000-8000-000000000037','Air Trick Action Kit','Released festival kit. Complete effect support is not yet reviewed.','3',NULL),
('84000000-0000-4000-8000-000000000038','4th Stage Charge Kit','Released festival kit. Complete effect support is not yet reviewed.','3',NULL);

-- Non-passive zero vectors are storage placeholders. Kind controls unsupported/conditional semantics.
INSERT INTO passive_gadget_rules (game_version_id,gadget_id,effect_id,position,kind,subject,
required_racing_type,matching_speed,matching_acceleration,matching_handling,matching_power,matching_boost,
nonmatching_speed,nonmatching_acceleration,nonmatching_handling,nonmatching_power,nonmatching_boost,
label,explanation,stacking_group,scenario_stat_potential) VALUES
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000001','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Air Trick Bounty','Awards rings when performing air tricks. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000002','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Item Attack Bounty','Awards rings after an item hits an opponent. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000003','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Runoff Bounty','Awards rings while crossing runoff areas with a speed item. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000004','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Slipstream Bounty','Awards rings during slipstreams. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000005','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Travel Ring Bounty','Awards rings when passing through a Travel Ring. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000006','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Morph Action Bounty','Awards rings for charge actions in water or flight. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000007','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Perfect Charge Bounty','Awards rings for releasing a drift as its charge level fills. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000008','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Dash Panel Bounty','Awards rings when using dash panels or gates. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000009','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Dash Panel Mini Bounty','Awards a smaller ring reward when using dash panels or gates. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000010','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Dash Panel Combo Bounty','Awards rings for consecutive dash panels. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000011','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','200 Ring Limit','Raises ring capacity to 200. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000012','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Ring Gain Mini Boost','Briefly increases physical speed when collecting rings. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000013','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Ring Gain Boost','Grants a larger brief physical speed increase when collecting rings. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000014','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Ring Range UP','Extends the ring pickup range. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000015','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Air Drift Mobility UP','Changes air drift movement and release behavior in flight form. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000016','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Charge Jump Mobility UP','Increases lateral air trick movement after a charge jump in water form. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000017','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Air Trick Adept','Increases air trick animation speed. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000018','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Air Trick Expert','Increases air trick animation speed more than Air Trick Adept. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000019','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Lv1 Quick Charge','Shortens level 1 drift charge time. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000020','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Lv2 Quick Charge','Shortens level 2 drift charge time. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000021','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Lv3 Quick Charge','Shortens level 3 drift charge time. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000022','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Technical Drift','Trades drift traction for faster charge in land form. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000023','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Counter Quick Charge','Accelerates charging when changing drift direction in land form. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000024','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Maximum Traction','Reduces slipping on sand and ice. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000025','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Second Wind','Grants a Boost item after falling off the course. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000026','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Bumper Guard','Prevents ring loss from wall contact. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000027','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Boost Starter','Grants a Boost item at race start. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000028','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Double Boost Specialist','Adds Double Boost to item boxes and grants one at race start. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000029','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Dark Chao Starter','Grants a Dark Chao item at race start. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000030','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Giant Spiked Iron Ball','Increases Spiked Iron Ball size and item availability. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000031','other-0','0','NON_STAT','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Short Fuse','Speeds up Bomb charging and increases its item availability. This utility does not supply passive five-stat points.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000032','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Monster Truck Starter','Grants a Monster Truck item at race start. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000033','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Item Hoarder Kit','Combines extra item capacity with item rewards at the start of later laps. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000034','other-0','0','CONDITIONAL','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Damage Support Kit','Combines starting invincibility with a Boost item after falling off the course. No race event or five-stat adjustment is assumed.',NULL,'false'),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000035','other-0','0','UNSUPPORTED','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Perfect Charge Kit','Identity and cost are reviewed; full kit effects remain unsupported. Unknown is not zero.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000036','other-0','0','UNSUPPORTED','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Speedy Shortcut Kit','Identity and cost are reviewed; full kit effects remain unsupported. Unknown is not zero.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000037','other-0','0','UNSUPPORTED','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','Air Trick Action Kit','Identity and cost are reviewed; full kit effects remain unsupported. Unknown is not zero.',NULL,NULL),
('50000000-0000-0000-0000-000000000001','84000000-0000-4000-8000-000000000038','other-0','0','UNSUPPORTED','ANY',NULL,'0','0','0','0','0','0','0','0','0','0','4th Stage Charge Kit','Identity and cost are reviewed; full kit effects remain unsupported. Unknown is not zero.',NULL,NULL);

INSERT INTO passive_rule_sources (game_version_id,gadget_id,effect_id,position,url)
SELECT '50000000-0000-0000-0000-000000000001'::uuid, reviewed.id::uuid, 'other-0', source.position, source.url
FROM (VALUES
('84000000-0000-4000-8000-000000000001', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000002', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000003', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000004', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000005', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000006', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000007', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000008', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000009', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000010', 'https://w.atwiki.jp/sonicracingcw/pages/41.html'),
('84000000-0000-4000-8000-000000000011', 'https://w.atwiki.jp/sonicracingcw/pages/42.html'),
('84000000-0000-4000-8000-000000000012', 'https://w.atwiki.jp/sonicracingcw/pages/42.html'),
('84000000-0000-4000-8000-000000000013', 'https://w.atwiki.jp/sonicracingcw/pages/42.html'),
('84000000-0000-4000-8000-000000000014', 'https://w.atwiki.jp/sonicracingcw/pages/42.html'),
('84000000-0000-4000-8000-000000000015', 'https://w.atwiki.jp/sonicracingcw/pages/43.html'),
('84000000-0000-4000-8000-000000000016', 'https://w.atwiki.jp/sonicracingcw/pages/43.html'),
('84000000-0000-4000-8000-000000000017', 'https://w.atwiki.jp/sonicracingcw/pages/43.html'),
('84000000-0000-4000-8000-000000000018', 'https://w.atwiki.jp/sonicracingcw/pages/43.html'),
('84000000-0000-4000-8000-000000000019', 'https://w.atwiki.jp/sonicracingcw/pages/44.html'),
('84000000-0000-4000-8000-000000000020', 'https://w.atwiki.jp/sonicracingcw/pages/44.html'),
('84000000-0000-4000-8000-000000000021', 'https://w.atwiki.jp/sonicracingcw/pages/44.html'),
('84000000-0000-4000-8000-000000000022', 'https://w.atwiki.jp/sonicracingcw/pages/44.html'),
('84000000-0000-4000-8000-000000000023', 'https://w.atwiki.jp/sonicracingcw/pages/44.html'),
('84000000-0000-4000-8000-000000000024', 'https://w.atwiki.jp/sonicracingcw/pages/46.html'),
('84000000-0000-4000-8000-000000000025', 'https://w.atwiki.jp/sonicracingcw/pages/46.html'),
('84000000-0000-4000-8000-000000000026', 'https://w.atwiki.jp/sonicracingcw/pages/46.html'),
('84000000-0000-4000-8000-000000000027', 'https://w.atwiki.jp/sonicracingcw/pages/48.html'),
('84000000-0000-4000-8000-000000000028', 'https://w.atwiki.jp/sonicracingcw/pages/48.html'),
('84000000-0000-4000-8000-000000000029', 'https://w.atwiki.jp/sonicracingcw/pages/48.html'),
('84000000-0000-4000-8000-000000000030', 'https://w.atwiki.jp/sonicracingcw/pages/48.html'),
('84000000-0000-4000-8000-000000000031', 'https://w.atwiki.jp/sonicracingcw/pages/48.html'),
('84000000-0000-4000-8000-000000000032', 'https://w.atwiki.jp/sonicracingcw/pages/48.html'),
('84000000-0000-4000-8000-000000000033', 'https://w.atwiki.jp/sonicracingcw/pages/49.html'),
('84000000-0000-4000-8000-000000000034', 'https://w.atwiki.jp/sonicracingcw/pages/49.html'),
('84000000-0000-4000-8000-000000000035', 'https://w.atwiki.jp/sonicracingcw/pages/88.html'),
('84000000-0000-4000-8000-000000000036', 'https://w.atwiki.jp/sonicracingcw/pages/88.html'),
('84000000-0000-4000-8000-000000000037', 'https://w.atwiki.jp/sonicracingcw/pages/88.html'),
('84000000-0000-4000-8000-000000000038', 'https://w.atwiki.jp/sonicracingcw/pages/88.html')
) AS reviewed(id,wiki)
CROSS JOIN LATERAL (VALUES
(0, 'https://www.srcgadgetbuilder.com/build'),
(1, 'https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0'),
(2, reviewed.wiki)
) AS source(position,url);

UPDATE gadget_rule_sets SET passive_ruleset = 'crossworlds-1.4.1-passive-2026-10-03.1'
WHERE game_version_id = '50000000-0000-0000-0000-000000000001';
