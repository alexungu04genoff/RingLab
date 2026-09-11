CREATE TABLE game_versions (
    id UUID PRIMARY KEY,
    version VARCHAR(32) NOT NULL UNIQUE,
    released_at DATE NOT NULL
);

INSERT INTO game_versions (id, version, released_at) VALUES
('50000000-0000-0000-0000-000000000001', '1.4.1', '2026-06-23'),
('50000000-0000-0000-0000-000000000002', '1.3.1', '2026-03-18'),
('50000000-0000-0000-0000-000000000003', '1.2.2', '2025-12-22'),
('50000000-0000-0000-0000-000000000004', '1.2.0', '2025-12-03');

-- No default or backfill: existing builds remain version unspecified.
ALTER TABLE builds ADD COLUMN game_version_id UUID REFERENCES game_versions(id);
