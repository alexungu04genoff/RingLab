-- Presentation metadata is populated through the explicit catalog importer.
ALTER TABLE gadgets
    ADD COLUMN acquisition_kind varchar(30) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN acquisition_label varchar(255),
    ADD CONSTRAINT gadgets_acquisition_kind_check
        CHECK (acquisition_kind IN ('STANDARD_UNLOCK', 'FESTIVAL_REWARD', 'UNKNOWN')),
    ADD CONSTRAINT gadgets_acquisition_label_check
        CHECK ((acquisition_kind <> 'FESTIVAL_REWARD' OR
                (acquisition_label IS NOT NULL AND length(trim(acquisition_label)) > 0))
               AND (acquisition_kind <> 'UNKNOWN' OR acquisition_label IS NULL));
