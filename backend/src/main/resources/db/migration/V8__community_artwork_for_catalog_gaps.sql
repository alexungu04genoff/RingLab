-- Community-hosted artwork explicitly approved by the project owner on 2026-09-12.
-- This migration only fills local artwork paths. It does not change catalog names, effects, costs,
-- IDs, build selections, or game rules. Source provenance is in docs/game-data-sources.md.

UPDATE machines SET image_path = '/assets/machines/dark-reaper.png' WHERE id = 'fc5a6823-88b8-5acd-9d9d-0bc2f8b52fb3';
UPDATE machines SET image_path = '/assets/machines/jumble-rage.png' WHERE id = '04c4a02d-2dfd-5c60-b55a-752ace638175';
UPDATE machines SET image_path = '/assets/machines/neo-lightron.png' WHERE id = 'c075c292-e6c2-5b7e-bae2-525039a77013';
UPDATE machines SET image_path = '/assets/machines/road-dragoon.png' WHERE id = '098afc29-eb17-5be7-a04e-75ebba27e3e3';
UPDATE machines SET image_path = '/assets/machines/type-s-stream.png' WHERE id = 'c60a51f3-9d1f-5ee5-9329-34d92829a4e7';

UPDATE gadgets SET image_path = '/assets/gadgets/inventory-swap.png' WHERE id = '174ea0a3-43bb-5001-bbd4-8b598482fe59';
UPDATE gadgets SET image_path = '/assets/gadgets/attack-item-chance-up.png' WHERE id = '82005d0f-575b-5350-934f-fb108d306a21';
UPDATE gadgets SET image_path = '/assets/gadgets/defense-item-chance-up.png' WHERE id = '83284989-3174-5cf8-a5ef-3d443b559b55';
UPDATE gadgets SET image_path = '/assets/gadgets/boost-item-chance-up.png' WHERE id = '966f8792-a6af-576f-885c-697a33064497';
UPDATE gadgets SET image_path = '/assets/gadgets/hazard-item-chance-up.png' WHERE id = 'ae3d98e8-321c-5e19-b442-a2da09463467';
UPDATE gadgets SET image_path = '/assets/gadgets/ultimate-charge.png' WHERE id = 'f8a1e5bd-b342-5a88-87e1-908adb2e165f';
UPDATE gadgets SET image_path = '/assets/gadgets/quick-recovery.png' WHERE id = '70000000-0000-4000-8000-000000000033';
UPDATE gadgets SET image_path = '/assets/gadgets/wisp-chance-up.png' WHERE id = '70000000-0000-4000-8000-000000000035';
UPDATE gadgets SET image_path = '/assets/gadgets/invincible-start.png' WHERE id = '70000000-0000-4000-8000-000000000036';
UPDATE gadgets SET image_path = '/assets/gadgets/handling-machine-kit.png' WHERE id = '70000000-0000-4000-8000-000000000037';
UPDATE gadgets SET image_path = '/assets/gadgets/power-machine-kit.png' WHERE id = '70000000-0000-4000-8000-000000000038';
UPDATE gadgets SET image_path = '/assets/gadgets/spin-drift.png' WHERE id = '70000000-0000-4000-8000-000000000039';
UPDATE gadgets SET image_path = '/assets/gadgets/crash-pads.png' WHERE id = '70000000-0000-4000-8000-000000000040';
UPDATE gadgets SET image_path = '/assets/gadgets/warp-ring-specialist.png' WHERE id = '70000000-0000-4000-8000-000000000041';
UPDATE gadgets SET image_path = '/assets/gadgets/item-keeper.png' WHERE id = '70000000-0000-4000-8000-000000000042';
UPDATE gadgets SET image_path = '/assets/gadgets/perfect-charge-boost.png' WHERE id = '70000000-0000-4000-8000-000000000043';
UPDATE gadgets SET image_path = '/assets/gadgets/friction-drift.png' WHERE id = '70000000-0000-4000-8000-000000000044';
UPDATE gadgets SET image_path = '/assets/gadgets/quick-starter.png' WHERE id = '70000000-0000-4000-8000-000000000045';
UPDATE gadgets SET image_path = '/assets/gadgets/slow-starter.png' WHERE id = '70000000-0000-4000-8000-000000000046';
UPDATE gadgets SET image_path = '/assets/gadgets/super-quick-starter.png' WHERE id = '70000000-0000-4000-8000-000000000047';
UPDATE gadgets SET image_path = '/assets/gadgets/super-slow-starter.png' WHERE id = '70000000-0000-4000-8000-000000000048';
UPDATE gadgets SET image_path = '/assets/gadgets/drift-charge-kit.png' WHERE id = '70000000-0000-4000-8000-000000000050';
