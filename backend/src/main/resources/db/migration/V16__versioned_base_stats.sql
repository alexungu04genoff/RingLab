-- Historical values are deliberately unseeded: see docs/stats-1.3.1-audit.md.
-- NULL is unknown, never an implicit zero. Catalog identities remain independent.
CREATE TABLE racer_stats (
    racer_id UUID NOT NULL REFERENCES racers(id),
    game_version_id UUID NOT NULL REFERENCES game_versions(id),
    speed NUMERIC,
    acceleration NUMERIC,
    handling NUMERIC,
    power NUMERIC,
    boost NUMERIC,
    PRIMARY KEY (racer_id, game_version_id)
);

CREATE TABLE machine_part_stats (
    machine_part_id UUID NOT NULL REFERENCES machine_parts(id),
    game_version_id UUID NOT NULL REFERENCES game_versions(id),
    speed NUMERIC,
    acceleration NUMERIC,
    handling NUMERIC,
    power NUMERIC,
    boost NUMERIC,
    PRIMARY KEY (machine_part_id, game_version_id)
);
