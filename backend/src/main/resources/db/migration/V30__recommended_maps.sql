-- Zero rows means All maps. Existing builds and their metadata are untouched.
CREATE TABLE race_maps (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    category VARCHAR(24) NOT NULL CHECK (category IN ('MAIN_COURSE', 'CROSSWORLD')),
    content_pack VARCHAR(120),
    image_path VARCHAR(255),
    catalog_order INTEGER NOT NULL UNIQUE
);

CREATE TABLE build_recommended_maps (
    build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
    map_id UUID NOT NULL REFERENCES race_maps(id) ON DELETE RESTRICT,
    PRIMARY KEY (build_id, map_id)
);
CREATE INDEX idx_build_recommended_maps_map ON build_recommended_maps(map_id, build_id);
