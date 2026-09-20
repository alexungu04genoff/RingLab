-- Gadgets must have a verified one-to-three-slot Gadget Plate cost before they are available.
-- Remove dependent build rows first to preserve referential integrity for any legacy references.
DELETE FROM build_gadgets
WHERE gadget_id IN (SELECT id FROM gadgets WHERE slot_cost IS NULL);

DELETE FROM gadgets
WHERE slot_cost IS NULL;
