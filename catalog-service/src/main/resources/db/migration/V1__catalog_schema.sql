CREATE TABLE categories (
    id            BIGSERIAL PRIMARY KEY,
    slug          VARCHAR(60)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0
);

CREATE TABLE brands (
    id   BIGSERIAL PRIMARY KEY,
    slug VARCHAR(60)  NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE listings (
    id                 UUID         PRIMARY KEY,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    category_id        BIGINT       NOT NULL REFERENCES categories (id),
    brand_id           BIGINT       NOT NULL REFERENCES brands (id),
    model_name         VARCHAR(120) NOT NULL,
    condition_grade    VARCHAR(1)   NOT NULL CHECK (condition_grade IN ('A', 'B', 'C')),
    price_minor        BIGINT       NOT NULL CHECK (price_minor > 0),      -- paise
    mrp_minor          BIGINT       CHECK (mrp_minor IS NULL OR mrp_minor > 0),
    currency           VARCHAR(3)   NOT NULL DEFAULT 'INR',
    battery_health_pct INT          CHECK (battery_health_pct BETWEEN 0 AND 100),
    warranty_months    INT          NOT NULL DEFAULT 0,
    attributes         JSONB        NOT NULL DEFAULT '{}'::jsonb,           -- category-specific specs
    thumbnail_url      VARCHAR(500),
    seller_name        VARCHAR(120) NOT NULL,
    city               VARCHAR(80)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_listings_browse   ON listings (status, category_id, price_minor);
CREATE INDEX idx_listings_brand    ON listings (brand_id);
CREATE INDEX idx_listings_created  ON listings (created_at DESC);
CREATE INDEX idx_listings_attrs    ON listings USING GIN (attributes);

CREATE TABLE listing_images (
    listing_id UUID         NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    position   INT          NOT NULL,
    url        VARCHAR(500) NOT NULL,
    PRIMARY KEY (listing_id, position)
);

-- Stock lives in its own table so it can be extracted into an Inventory Service later.
CREATE TABLE stock (
    listing_id    UUID        PRIMARY KEY REFERENCES listings (id) ON DELETE CASCADE,
    available_qty INT         NOT NULL CHECK (available_qty >= 0),
    reserved_qty  INT         NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservations (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL UNIQUE,          -- makes reserve() idempotent per order
    status     VARCHAR(20) NOT NULL,                 -- HELD | COMMITTED | RELEASED | EXPIRED
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_reservations_expiry ON reservations (status, expires_at);

CREATE TABLE reservation_items (
    reservation_id UUID NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    listing_id     UUID NOT NULL,
    quantity       INT  NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, listing_id)
);
