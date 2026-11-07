-- One cart per user. The cart row doubles as a lock target so concurrent edits are serialised.
CREATE TABLE carts (
    user_id    UUID        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Deliberately stores NO live price/title: those always come from Catalog.
-- unit_price_at_add_minor only lets us tell the buyer "price changed since you added this".
CREATE TABLE cart_items (
    id                     UUID        PRIMARY KEY,
    user_id                UUID        NOT NULL REFERENCES carts (user_id) ON DELETE CASCADE,
    listing_id             UUID        NOT NULL,
    quantity               INT         NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    unit_price_at_add_minor BIGINT     NOT NULL CHECK (unit_price_at_add_minor > 0),
    added_at               TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cart_item UNIQUE (user_id, listing_id)
);

CREATE INDEX idx_carts_updated_at ON carts (updated_at);
