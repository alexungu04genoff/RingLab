CREATE TABLE collection_racer_exclusions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES racers(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, item_id)
);
CREATE TABLE collection_machine_exclusions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, item_id)
);
CREATE TABLE collection_gadget_exclusions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES gadgets(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, item_id)
);
