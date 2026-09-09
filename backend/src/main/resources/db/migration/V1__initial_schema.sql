CREATE TABLE users (
 id UUID PRIMARY KEY, username VARCHAR(30) NOT NULL UNIQUE, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE racers (id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, racing_type VARCHAR(20) NOT NULL CHECK (racing_type IN ('SPEED','ACCELERATION','HANDLING','POWER','BOOST')), image_path VARCHAR(255));
CREATE TABLE machines (id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, racing_type VARCHAR(20) NOT NULL CHECK (racing_type IN ('SPEED','ACCELERATION','HANDLING','POWER','BOOST')), image_path VARCHAR(255));
CREATE TABLE gadgets (id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, description TEXT, slot_cost INTEGER, image_path VARCHAR(255));
CREATE TABLE builds (
 id UUID PRIMARY KEY, title VARCHAR(120) NOT NULL, description TEXT NOT NULL,
 author_id UUID NOT NULL REFERENCES users(id), racer_id UUID NOT NULL REFERENCES racers(id),
 machine_id UUID NOT NULL REFERENCES machines(id), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE build_gadgets (
 build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE, gadget_id UUID NOT NULL REFERENCES gadgets(id),
 position INTEGER NOT NULL CHECK (position >= 0), PRIMARY KEY(build_id, position)
);
CREATE TABLE votes (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
 value SMALLINT NOT NULL CHECK (value IN (-1,1)), CONSTRAINT votes_user_build_unique UNIQUE(user_id,build_id)
);
CREATE TABLE comments (
 id UUID PRIMARY KEY, build_id UUID NOT NULL REFERENCES builds(id) ON DELETE CASCADE,
 author_id UUID NOT NULL REFERENCES users(id), text VARCHAR(2000) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX builds_created ON builds(created_at DESC,id);
CREATE INDEX builds_author ON builds(author_id);
CREATE INDEX builds_racer ON builds(racer_id);
CREATE INDEX builds_machine ON builds(machine_id);
CREATE INDEX votes_build ON votes(build_id);
CREATE INDEX comments_build_created ON comments(build_id,created_at,id);
