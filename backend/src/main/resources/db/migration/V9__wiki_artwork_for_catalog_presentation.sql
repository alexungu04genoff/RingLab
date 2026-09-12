-- The project owner selected Sonic Wiki artwork as the single visual source for catalog cards.
-- Existing local asset paths remain stable; only Blinky previously lacked an image path.
-- This does not change catalog identity, effects, costs, game rules, or community data.

UPDATE racers
SET image_path = '/assets/racers/blinky.png'
WHERE id = '60000000-0000-4000-8000-000000000037';
