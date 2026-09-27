-- Identity/cost evidence: docs/passive-gadget-sources.md. Existing costs stay unchanged.
-- Numerical rules are versioned local Java data, not derived totals stored on builds.
INSERT INTO gadgets (id, name, description, slot_cost, image_path) VALUES
('70000000-0000-4000-8000-000000000052','Speed Tuner 1','Adjust Speed; matching Speed machines also receive an Acceleration penalty.',1,NULL),
('70000000-0000-4000-8000-000000000053','Speed Tuner 2','Adjust Speed; matching Speed machines also receive Acceleration and Boost penalties.',1,NULL),
('70000000-0000-4000-8000-000000000054','Acceleration Tuner 1','Adjust Acceleration; matching Acceleration machines also receive a Handling penalty.',1,NULL),
('70000000-0000-4000-8000-000000000055','Acceleration Tuner 2','Adjust Acceleration; matching Acceleration machines also receive Handling and Boost penalties.',1,NULL),
('70000000-0000-4000-8000-000000000056','Handling Tuner 1','Adjust Handling; matching Handling machines also receive a Power penalty.',1,NULL),
('70000000-0000-4000-8000-000000000057','Handling Tuner 2','Adjust Handling; matching Handling machines also receive Speed and Power penalties.',1,NULL),
('70000000-0000-4000-8000-000000000058','Power Tuner 1','Adjust Power; matching Power machines also receive an Acceleration penalty.',1,NULL),
('70000000-0000-4000-8000-000000000059','Power Tuner 2','Adjust Power; matching Power machines also receive Acceleration and Handling penalties.',1,NULL),
('70000000-0000-4000-8000-000000000060','Boost Tuner 1','Adjust Boost; matching Boost machines also receive a Speed penalty.',1,NULL),
('70000000-0000-4000-8000-000000000061','Boost Tuner 2','Adjust Boost; matching Boost machines also receive Speed and Power penalties.',1,NULL),
('70000000-0000-4000-8000-000000000062','Boost Machine Kit','Matching Boost machines gain Boost stat points; drift boosts can grant rings.',3,NULL);

-- Correct old prose that conflated a physical speed unit or an event with passive points.
UPDATE gadgets SET description='Top-speed boost while holding Rings; drains Rings over time. Physical speed is not a Speed-point bonus.'
WHERE id IN ('5a000a58-7d7a-581c-80e3-6ae8661215b7','182bdfa8-44d3-5de8-941b-d525381dd0a3');
UPDATE gadgets SET description='Passive Handling and Power bonuses; consecutive Dash Panels separately grant Rings.'
WHERE id='70000000-0000-4000-8000-000000000023';
